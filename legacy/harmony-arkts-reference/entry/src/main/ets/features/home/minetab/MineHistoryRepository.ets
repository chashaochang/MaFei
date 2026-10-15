import { currentMediaCatalogProvider } from '../../../media/MediaCatalogAccess'
import { MediaEpisode, MediaSummary } from '../../../media/catalog/MediaCatalogModels'
import {
  MediaCatalogProvider,
  MediaCatalogSortDirection,
  MediaCatalogSortField,
  MediaEpisodeQueryProfile
} from '../../../media/catalog/MediaCatalogProvider'
import { MediaRef, MediaRefKind } from '../../../media/MediaRef'
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
  catalog: MediaCatalogProvider

  constructor(catalog: MediaCatalogProvider) {
    this.scope = catalog.session.accountScope
    this.catalog = catalog
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
  hasMore: boolean = false
  nextStartIndex: number = 0
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
  private readonly planner: MineHistoryPlanner = new MineHistoryPlanner()
  private readonly latestSeriesCache: Map<string, MineHistorySeriesCacheEntry> =
    new Map<string, MineHistorySeriesCacheEntry>()
  private readonly latestSeriesPending: Map<string, Promise<MineHistorySeriesPosition | undefined>> =
    new Map<string, Promise<MineHistorySeriesPosition | undefined>>()
  private readonly latestSeriesCacheTtlMs: number = 5 * 60 * 1000
  private readonly enrichmentConcurrency: number = 4

  currentScope(): string {
    try {
      return currentMediaCatalogProvider().session.accountScope
    } catch (_error) {
      return ''
    }
  }

  async load(request: MineHistoryLoadRequest): Promise<MineHistoryLoadResult> {
    const session = this.createSession()
    this.pruneExpiredSeriesCache()
    const resumePromise: Promise<MineHistorySourceResult> = request.loadResume ?
      this.fetchResume(session, request.resumeStartIndex, request.limit)
        .then((fetchResult: MineHistoryFetchResult): MineHistorySourceResult => this.success(fetchResult))
        .catch((error: Error): MineHistorySourceResult => {
          console.warn(`[MineHistory] resume request failed: ${error.message}`)
          return this.failure(request.resumeStartIndex)
        }) : Promise.resolve(this.skipped(request.resumeStartIndex))
    const completedPromise: Promise<MineHistorySourceResult> = request.loadCompleted ?
      this.fetchCompleted(session, request.completedStartIndex, request.limit)
        .then((fetchResult: MineHistoryFetchResult): MineHistorySourceResult => this.success(fetchResult))
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
    const seriesRefs: MediaRef[] = []
    const seen: Set<string> = new Set<string>()
    items.forEach((item: MineHistoryCandidate): void => {
      const seriesRef = item.seriesRef
      if (item.mediaType === MineHistoryMediaType.Episode && seriesRef && !seen.has(seriesRef.identity)) {
        seen.add(seriesRef.identity)
        seriesRefs.push(seriesRef)
      }
    })
    if (seriesRefs.length === 0) return

    const bySeries: Map<string, MineHistorySeriesPosition> = new Map<string, MineHistorySeriesPosition>()
    for (let batchStart = 0; batchStart < seriesRefs.length; batchStart += this.enrichmentConcurrency) {
      const batchRefs = seriesRefs.slice(batchStart, batchStart + this.enrichmentConcurrency)
      const requests: Promise<MineHistorySeriesPosition | undefined>[] = []
      batchRefs.forEach((seriesRef: MediaRef): void => {
        requests.push(this.latestSeriesPosition(session, seriesRef))
      })
      const positions = await Promise.all(requests)
      positions.forEach((position: MineHistorySeriesPosition | undefined, index: number): void => {
        if (position) bySeries.set(batchRefs[index].identity, position)
      })
    }

    items.forEach((item: MineHistoryCandidate): void => {
      const position = item.seriesRef ? bySeries.get(item.seriesRef.identity) : undefined
      if (position) {
        item.latestSeasonNumber = position.seasonNumber
        item.latestEpisodeNumber = position.episodeNumber
        item.seriesHasMultipleSeasons = item.seriesHasMultipleSeasons || position.hasMultipleSeasons
      }
    })
  }

  private createSession(): MineHistoryRequestSession {
    return new MineHistoryRequestSession(currentMediaCatalogProvider())
  }

  private async fetchResume(session: MineHistoryRequestSession, startIndex: number,
    limit: number): Promise<MineHistoryFetchResult> {
    const page = await session.catalog.resume({
      kinds: [MediaRefKind.Movie, MediaRefKind.Episode],
      limit: Math.max(1, startIndex + limit)
    })
    const selected = page.items.slice(startIndex, startIndex + limit)
    return this.mapPage(selected, startIndex,
      page.hasMore || page.items.length >= startIndex + limit)
  }

  private async fetchCompleted(session: MineHistoryRequestSession, startIndex: number,
    limit: number): Promise<MineHistoryFetchResult> {
    const page = await session.catalog.getItems({
      kinds: [MediaRefKind.Movie, MediaRefKind.Episode],
      startIndex: startIndex,
      limit: limit,
      sortFields: [MediaCatalogSortField.DatePlayed],
      sortDirections: [MediaCatalogSortDirection.Descending],
      recursive: true,
      collapseBoxSetItems: false,
      favoriteOnly: false,
      watched: true
    })
    const result = this.mapPage(page.items, page.startIndex, page.hasMore || page.items.length >= limit)
    result.nextStartIndex = page.nextStartIndex
    return result
  }

  private mapPage(items: MediaSummary[], startIndex: number, hasMore: boolean): MineHistoryFetchResult {
    const result = new MineHistoryFetchResult()
    result.rawCount = items.length
    result.hasMore = hasMore
    result.nextStartIndex = startIndex + items.length
    result.items = this.mapItems(items)
    return result
  }

  private mapItems(items: MediaSummary[]): MineHistoryCandidate[] {
    const result: MineHistoryCandidate[] = []
    items.forEach((item: MediaSummary): void => {
      const candidate = this.mapItem(item)
      if (candidate) result.push(candidate)
    })
    return result
  }

  private mapItem(item: MediaSummary): MineHistoryCandidate | undefined {
    const lastPlayedDate = item.lastPlayedDate || ''
    if (item.ref.id.length === 0 || lastPlayedDate.length === 0) return undefined
    const isEpisode = item.ref.kind === MediaRefKind.Episode
    const imageRef = item.primaryImage || item.thumbnailImage || item.backdropImage
    return {
      id: item.ref.id,
      mediaRef: item.ref,
      name: isEpisode ? (item.seriesTitle || item.title) : item.title,
      image: imageRef?.url || '',
      imageRef: imageRef,
      lastPlayedDate: lastPlayedDate,
      played: item.userState.watched.watched,
      percent: item.userState.watched.playedPercentage,
      mediaType: isEpisode ? MineHistoryMediaType.Episode : MineHistoryMediaType.Movie,
      communityRating: item.communityRating,
      seriesId: item.seriesRef?.id || '',
      seriesRef: item.seriesRef,
      seasonNumber: item.seasonNumber,
      episodeNumber: item.episodeNumber,
      latestSeasonNumber: undefined,
      latestEpisodeNumber: undefined,
      seriesHasMultipleSeasons: item.seasonNumber !== undefined && item.seasonNumber > 1
    }
  }

  private success(fetchResult: MineHistoryFetchResult): MineHistorySourceResult {
    const result = new MineHistorySourceResult()
    result.items = fetchResult.items
    result.requested = true
    result.nextStartIndex = fetchResult.nextStartIndex
    result.hasMore = fetchResult.hasMore
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

  private latestSeriesPosition(session: MineHistoryRequestSession,
    seriesRef: MediaRef): Promise<MineHistorySeriesPosition | undefined> {
    const cacheKey = `${session.scope}|${seriesRef.identity}`
    const now = Date.now()
    const cached = this.latestSeriesCache.get(cacheKey)
    if (cached && cached.expiresAt > now) return Promise.resolve(cached.position)
    if (cached) this.latestSeriesCache.delete(cacheKey)
    const pending = this.latestSeriesPending.get(cacheKey)
    if (pending) return pending

    const request = this.fetchLatestSeriesPosition(session, seriesRef)
      .then((position: MineHistorySeriesPosition | undefined): MineHistorySeriesPosition | undefined => {
        this.latestSeriesCache.set(cacheKey,
          new MineHistorySeriesCacheEntry(position, Date.now() + this.latestSeriesCacheTtlMs))
        return position
      })
      .catch((error: Error): MineHistorySeriesPosition | undefined => {
        console.warn(`[MineHistory] latest series request failed: ${seriesRef.id}, ${error.message}`)
        return undefined
      })
      .then((position: MineHistorySeriesPosition | undefined): MineHistorySeriesPosition | undefined => {
        this.latestSeriesPending.delete(cacheKey)
        return position
      })
    this.latestSeriesPending.set(cacheKey, request)
    return request
  }

  private async fetchLatestSeriesPosition(session: MineHistoryRequestSession,
    seriesRef: MediaRef): Promise<MineHistorySeriesPosition | undefined> {
    if (!seriesRef.belongsTo(session.catalog.session)) return undefined
    const page = await session.catalog.getEpisodes({
      seriesRef: seriesRef,
      startIndex: 0,
      limit: 1000,
      profile: MediaEpisodeQueryProfile.Display
    })
    const episodeCandidates: MineHistorySeriesEpisodeCandidate[] = []
    page.items.forEach((episode: MediaEpisode): void => {
      episodeCandidates.push({
        seasonNumber: episode.seasonNumber,
        episodeNumber: episode.episodeNumber
      })
    })
    return this.planner.latestSeriesPosition(episodeCandidates)
  }

  private pruneExpiredSeriesCache(): void {
    const now = Date.now()
    this.latestSeriesCache.forEach((entry: MineHistorySeriesCacheEntry, key: string): void => {
      if (entry.expiresAt <= now) this.latestSeriesCache.delete(key)
    })
  }
}
