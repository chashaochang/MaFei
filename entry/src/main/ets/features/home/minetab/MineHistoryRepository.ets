import { AxiosResponse } from '@ohos/axios'
import { getItemsApi, ImageUrlsApi } from 'sdk'
import { ItemsApi } from 'sdk/src/main/ets/generated-client/api/items-api'
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  ImageType,
  ItemFields,
  ItemSortBy,
  MediaType,
  SortOrder
} from 'sdk/src/main/ets/generated-client/models'
import { ApiClient } from '../../../network/ApiClient'
import {
  MineHistoryCandidate,
  MineHistoryMediaType,
  MineHistoryPlanner,
  MineHistorySeriesEpisodeCandidate,
  MineHistorySeriesPosition
} from './MineHistoryPlanner'

export interface MineHistoryLoadRequest {
  resumeStartIndex: number
  completedStartIndex: number
  limit: number
  loadResume: boolean
  loadCompleted: boolean
}

export class MineHistoryRequestSession {
  scope: string
  basePath: string
  userId: string
  itemsApi: ItemsApi

  constructor(scope: string, basePath: string, userId: string, itemsApi: ItemsApi) {
    this.scope = scope
    this.basePath = basePath
    this.userId = userId
    this.itemsApi = itemsApi
  }
}

export class MineHistorySourceResult {
  items: MineHistoryCandidate[] = []
  requested: boolean = false
  failed: boolean = false
  hasMore: boolean = false
  nextStartIndex: number = 0
}

export class MineHistoryLoadResult {
  scope: string = ''
  session?: MineHistoryRequestSession
  resume: MineHistorySourceResult = new MineHistorySourceResult()
  completed: MineHistorySourceResult = new MineHistorySourceResult()
}

class MineHistoryFetchResult {
  items: MineHistoryCandidate[] = []
  rawCount: number = 0
}

class MineHistorySeriesCacheEntry {
  position?: MineHistorySeriesPosition
  expiresAt: number

  constructor(position: MineHistorySeriesPosition | undefined, expiresAt: number) {
    this.position = position
    this.expiresAt = expiresAt
  }
}

export class MineHistoryRepository {
  private readonly imageApi: ImageUrlsApi = new ImageUrlsApi()
  private readonly planner: MineHistoryPlanner = new MineHistoryPlanner()
  private readonly latestSeriesCache: Map<string, MineHistorySeriesCacheEntry> =
    new Map<string, MineHistorySeriesCacheEntry>()
  private readonly latestSeriesPending: Map<string, Promise<MineHistorySeriesPosition | undefined>> =
    new Map<string, Promise<MineHistorySeriesPosition | undefined>>()
  private readonly latestSeriesCacheTtlMs: number = 5 * 60 * 1000
  private readonly enrichmentConcurrency: number = 4
  private readonly latestSeriesQueryLimit: number = 12
  private readonly latestSeriesTimeoutMs: number = 8000

  currentScope(): string {
    const api = ApiClient.Instance()
    return this.scopeFor(String(api.basePath || ''), api.userId)
  }

  async load(request: MineHistoryLoadRequest): Promise<MineHistoryLoadResult> {
    const session = this.createSession()
    this.pruneExpiredSeriesCache()
    const resumePromise: Promise<MineHistorySourceResult> = request.loadResume ?
      this.fetchResume(session, request.resumeStartIndex, request.limit)
        .then((fetchResult: MineHistoryFetchResult): MineHistorySourceResult =>
          this.success(fetchResult, request.resumeStartIndex, request.limit))
        .catch((error: Error): MineHistorySourceResult => {
          console.warn(`[MineHistory] resume request failed: ${error.message}`)
          return this.failure(request.resumeStartIndex)
        }) : Promise.resolve(this.skipped(request.resumeStartIndex))
    const completedPromise: Promise<MineHistorySourceResult> = request.loadCompleted ?
      this.fetchCompleted(session, request.completedStartIndex, request.limit)
        .then((fetchResult: MineHistoryFetchResult): MineHistorySourceResult =>
          this.success(fetchResult, request.completedStartIndex, request.limit))
        .catch((error: Error): MineHistorySourceResult => {
          console.warn(`[MineHistory] completed request failed: ${error.message}`)
          return this.failure(request.completedStartIndex)
        }) : Promise.resolve(this.skipped(request.completedStartIndex))

    const sources = await Promise.all([resumePromise, completedPromise])
    const result = new MineHistoryLoadResult()
    result.scope = session.scope
    result.session = session
    result.resume = sources[0]
    result.completed = sources[1]
    return result
  }

  async enrichSeriesPositions(items: MineHistoryCandidate[], session: MineHistoryRequestSession): Promise<void> {
    const seriesIds: string[] = []
    const seen: Map<string, boolean> = new Map<string, boolean>()
    items.forEach((item: MineHistoryCandidate) => {
      if (item.mediaType === MineHistoryMediaType.Episode && item.seriesId.length > 0 &&
        !seen.has(item.seriesId)) {
        seen.set(item.seriesId, true)
        seriesIds.push(item.seriesId)
      }
    })
    if (seriesIds.length === 0) {
      return
    }

    const bySeries: Map<string, MineHistorySeriesPosition> = new Map<string, MineHistorySeriesPosition>()
    for (let batchStart = 0; batchStart < seriesIds.length; batchStart += this.enrichmentConcurrency) {
      const batchIds = seriesIds.slice(batchStart, batchStart + this.enrichmentConcurrency)
      const requests: Promise<MineHistorySeriesPosition | undefined>[] = []
      batchIds.forEach((seriesId: string) => {
        requests.push(this.latestSeriesPosition(session, seriesId))
      })
      const positions = await Promise.all(requests)
      positions.forEach((position: MineHistorySeriesPosition | undefined, index: number) => {
        if (position) {
          bySeries.set(batchIds[index], position)
        }
      })
    }

    items.forEach((item: MineHistoryCandidate) => {
      const position = bySeries.get(item.seriesId)
      if (position) {
        item.latestSeasonNumber = position.seasonNumber
        item.latestEpisodeNumber = position.episodeNumber
        item.seriesHasMultipleSeasons = item.seriesHasMultipleSeasons || position.hasMultipleSeasons
      }
    })
  }

  private createSession(): MineHistoryRequestSession {
    const api = ApiClient.Instance()
    const basePath = this.normalizedBasePath(String(api.basePath || ''))
    const userId = api.userId
    return new MineHistoryRequestSession(
      this.scopeFor(basePath, userId), basePath, userId, getItemsApi(api))
  }

  private fetchResume(
    session: MineHistoryRequestSession,
    startIndex: number,
    limit: number
  ): Promise<MineHistoryFetchResult> {
    try {
      return session.itemsApi.getResumeItems({
        userId: session.userId,
        startIndex,
        limit,
        fields: [ItemFields.PrimaryImageAspectRatio],
        mediaTypes: [MediaType.Video],
        includeItemTypes: [BaseItemKind.Movie, BaseItemKind.Episode],
        enableUserData: true,
        enableImages: true,
        imageTypeLimit: 1,
        enableImageTypes: [ImageType.Primary, ImageType.Thumb],
        enableTotalRecordCount: false
      }).then((response: AxiosResponse<BaseItemDtoQueryResult>): MineHistoryFetchResult =>
        this.mapResponse(response.data?.Items || [], session.basePath))
    } catch (_) {
      return Promise.reject(new Error('Unable to request resumable history'))
    }
  }

  private fetchCompleted(
    session: MineHistoryRequestSession,
    startIndex: number,
    limit: number
  ): Promise<MineHistoryFetchResult> {
    try {
      return session.itemsApi.getItems({
        userId: session.userId,
        startIndex,
        limit,
        recursive: true,
        includeItemTypes: [BaseItemKind.Movie, BaseItemKind.Episode],
        mediaTypes: [MediaType.Video],
        isPlayed: true,
        sortBy: [ItemSortBy.DatePlayed],
        sortOrder: [SortOrder.Descending],
        fields: [ItemFields.PrimaryImageAspectRatio],
        enableUserData: true,
        enableImages: true,
        imageTypeLimit: 1,
        enableImageTypes: [ImageType.Primary, ImageType.Thumb],
        enableTotalRecordCount: false
      }).then((response: AxiosResponse<BaseItemDtoQueryResult>): MineHistoryFetchResult =>
        this.mapResponse(response.data?.Items || [], session.basePath))
    } catch (_) {
      return Promise.reject(new Error('Unable to request completed history'))
    }
  }

  private mapResponse(items: BaseItemDto[], basePath: string): MineHistoryFetchResult {
    const result = new MineHistoryFetchResult()
    result.rawCount = items.length
    result.items = this.mapItems(items, basePath)
    return result
  }

  private mapItems(items: BaseItemDto[], basePath: string): MineHistoryCandidate[] {
    const result: MineHistoryCandidate[] = []
    items.forEach((item: BaseItemDto) => {
      const candidate = this.mapItem(item, basePath)
      if (candidate) {
        result.push(candidate)
      }
    })
    return result
  }

  private mapItem(item: BaseItemDto, basePath: string): MineHistoryCandidate | undefined {
    const id = item.Id || ''
    const lastPlayedDate = item.UserData?.LastPlayedDate || ''
    if (id.length === 0 || lastPlayedDate.length === 0) {
      return undefined
    }
    const isEpisode = item.Type === BaseItemKind.Episode
    const seasonNumber = item.ParentIndexNumber === null ? undefined : item.ParentIndexNumber
    return {
      id,
      name: isEpisode ? (item.SeriesName || item.Name || '') : (item.Name || ''),
      image: this.resolvePoster(item, basePath),
      lastPlayedDate,
      played: item.UserData?.Played === true,
      percent: item.UserData?.PlayedPercentage || 0,
      mediaType: isEpisode ? MineHistoryMediaType.Episode : MineHistoryMediaType.Movie,
      communityRating: item.CommunityRating === null ? undefined : item.CommunityRating,
      seriesId: item.SeriesId || '',
      seasonNumber,
      episodeNumber: item.IndexNumber === null ? undefined : item.IndexNumber,
      latestSeasonNumber: undefined,
      latestEpisodeNumber: undefined,
      seriesHasMultipleSeasons: seasonNumber !== undefined && Number.isFinite(seasonNumber) && seasonNumber > 1
    }
  }

  private resolvePoster(item: BaseItemDto, basePath: string): string {
    let imageItemId = item.Id || ''
    if (item.Type === BaseItemKind.Episode) {
      imageItemId = item.SeriesId || item.ParentThumbItemId || imageItemId
    }
    return basePath + this.imageApi.getItemImageUrlById(imageItemId, ImageType.Primary)
  }

  private success(
    fetchResult: MineHistoryFetchResult,
    startIndex: number,
    limit: number
  ): MineHistorySourceResult {
    const result = new MineHistorySourceResult()
    result.items = fetchResult.items
    result.requested = true
    result.nextStartIndex = startIndex + fetchResult.rawCount
    result.hasMore = fetchResult.rawCount === limit
    return result
  }

  private failure(startIndex: number): MineHistorySourceResult {
    const result = new MineHistorySourceResult()
    result.requested = true
    result.failed = true
    result.hasMore = true
    result.nextStartIndex = startIndex
    return result
  }

  private skipped(startIndex: number): MineHistorySourceResult {
    const result = new MineHistorySourceResult()
    result.nextStartIndex = startIndex
    return result
  }

  private latestSeriesPosition(
    session: MineHistoryRequestSession,
    seriesId: string
  ): Promise<MineHistorySeriesPosition | undefined> {
    const cacheKey = `${session.scope}|${seriesId}`
    const now = Date.now()
    const cached = this.latestSeriesCache.get(cacheKey)
    if (cached && cached.expiresAt > now) {
      return Promise.resolve(cached.position)
    }
    if (cached) {
      this.latestSeriesCache.delete(cacheKey)
    }
    const pending = this.latestSeriesPending.get(cacheKey)
    if (pending) {
      return pending
    }

    const request = this.fetchLatestSeriesPosition(session, seriesId)
      .then((position: MineHistorySeriesPosition | undefined): MineHistorySeriesPosition | undefined => {
        this.latestSeriesCache.set(cacheKey,
          new MineHistorySeriesCacheEntry(position, Date.now() + this.latestSeriesCacheTtlMs))
        return position
      })
      .catch((error: Error): MineHistorySeriesPosition | undefined => {
        console.warn(`[MineHistory] latest series request failed: ${seriesId}, ${error.message}`)
        return undefined
      })
      .then((position: MineHistorySeriesPosition | undefined): MineHistorySeriesPosition | undefined => {
        this.latestSeriesPending.delete(cacheKey)
        return position
      })
    this.latestSeriesPending.set(cacheKey, request)
    return request
  }

  private fetchLatestSeriesPosition(
    session: MineHistoryRequestSession,
    seriesId: string
  ): Promise<MineHistorySeriesPosition | undefined> {
    return session.itemsApi.getItems({
      userId: session.userId,
      parentId: seriesId,
      recursive: true,
      includeItemTypes: [BaseItemKind.Episode],
      mediaTypes: [MediaType.Video],
      isMissing: false,
      isPlaceHolder: false,
      sortBy: [ItemSortBy.ParentIndexNumber, ItemSortBy.IndexNumber],
      sortOrder: [SortOrder.Descending, SortOrder.Descending],
      limit: this.latestSeriesQueryLimit,
      enableImages: false,
      enableUserData: false,
      enableTotalRecordCount: false
    }, {
      timeout: this.latestSeriesTimeoutMs
    }).then((response: AxiosResponse<BaseItemDtoQueryResult>): MineHistorySeriesPosition | undefined => {
      const episodeCandidates: MineHistorySeriesEpisodeCandidate[] = []
      ;(response.data?.Items || []).forEach((episode: BaseItemDto) => {
        episodeCandidates.push({
          seasonNumber: episode.ParentIndexNumber === null ? undefined : episode.ParentIndexNumber,
          episodeNumber: episode.IndexNumber === null ? undefined : episode.IndexNumber
        })
      })
      return this.planner.latestSeriesPosition(episodeCandidates)
    })
  }

  private pruneExpiredSeriesCache(): void {
    const now = Date.now()
    this.latestSeriesCache.forEach((entry: MineHistorySeriesCacheEntry, key: string) => {
      if (entry.expiresAt <= now) {
        this.latestSeriesCache.delete(key)
      }
    })
  }

  private scopeFor(basePath: string, userId: string): string {
    return `${this.normalizedBasePath(basePath)}|${userId}`
  }

  private normalizedBasePath(basePath: string): string {
    let normalized = basePath.trim()
    while (normalized.endsWith('/')) {
      normalized = normalized.slice(0, -1)
    }
    return normalized
  }
}
