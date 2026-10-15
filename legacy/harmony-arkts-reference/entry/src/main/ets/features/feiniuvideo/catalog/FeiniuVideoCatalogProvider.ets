import CryptoJS from '@ohos/crypto-js'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaConnectionRoute, MediaProviderSession } from '../../../media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../../../media/MediaRef'
import { MediaCatalogError, MediaCatalogErrorCode } from '../../../media/catalog/MediaCatalogError'
import {
  MediaCatalogCounts,
  MediaDetail,
  MediaEpisode,
  MediaFavoriteState,
  MediaImageRef,
  MediaLibrary,
  MediaPage,
  MediaPerson,
  MediaSeason,
  MediaSummary,
  MediaWatchedState
} from '../../../media/catalog/MediaCatalogModels'
import {
  MediaCatalogProvider,
  MediaCatalogSortDirection,
  MediaCatalogSortField,
  MediaEpisodeQueryProfile,
  MediaEpisodeRequest,
  MediaLatestRequest,
  MediaListRequest,
  MediaNextUpRequest,
  MediaRecommendationsRequest,
  MediaResumeRequest,
  MediaSearchRequest
} from '../../../media/catalog/MediaCatalogProvider'
import { FeiniuVideoApiClient } from '../network/FeiniuVideoApiClient'
import { FeiniuVideoSessionSnapshotSync } from '../account/FeiniuVideoSessionService'
import {
  FeiniuVideoApiRequest,
  FeiniuVideoAuthenticationError,
  FeiniuVideoHttpMethod,
  FeiniuVideoQueryParameter,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  FnMediaUserInfoResp
} from '../network/FeiniuVideoApiModels'
import {
  FnMediaCountResp,
  FnMediaEpisodeItem,
  FnMediaGenreItem,
  FnMediaGuidReq,
  FnMediaItem,
  FnMediaItemDetailResp,
  FnMediaItemFile,
  FnMediaItemListReq,
  FnMediaItemListResp,
  FnMediaItemListTags,
  FnMediaItemStream,
  FnMediaLibraryItem,
  FnMediaListResp,
  FnMediaPersonItemListItem,
  FnMediaPersonItemListReq,
  FnMediaPersonItemListResp,
  FnMediaPersonListResp,
  FnMediaPersonResp,
  FnMediaPlayItem,
  FnMediaSeasonItem
} from './FeiniuVideoDtos'
import {
  FeiniuVideoCatalogImageAccess,
  FeiniuVideoCatalogItemContext,
  FeiniuVideoCatalogMapper
} from './FeiniuVideoCatalogMapper'
import {
  FnMediaOfficialStreamData,
  FnMediaOfficialStreamReq,
  FnMediaOfficialStreamResp,
  FnMediaPlayInfoReq,
  FnMediaPlayInfoResp,
  FnMediaStreamAudioInfo,
  FnMediaStreamFileInfo,
  FnMediaStreamListData,
  FnMediaStreamListResp,
  FnMediaStreamSubtitleInfo,
  FnMediaStreamVideoInfo
} from '../playback/FeiniuVideoPlaybackDtos'
import { FeiniuVideoRecommendationPlanner } from './FeiniuVideoRecommendationPlanner'

const FEINIU_HOME_LIBRARY_LIMIT: number = 16
const FEINIU_PERSON_JOBS: string[] = ['Actor', 'Director', 'Screenplay']
const FEINIU_RECOMMENDATION_CACHE_TTL_MS: number = 6 * 60 * 60 * 1000
const FEINIU_RECOMMENDATION_MIN_CANDIDATES: number = 24
const FEINIU_RECOMMENDATION_MAX_CANDIDATES: number = 60
const FEINIU_RECOMMENDATION_DETAIL_LIMIT: number = 6
const FEINIU_RECOMMENDATION_SIGNAL_DETAIL_LIMIT: number = 8
const FEINIU_OFFICIAL_STREAM_USER_AGENT: string =
  '1.32.2 (com.trim.app; build:1322007; Android 36) okHttp/4.9.0'

interface FeiniuVideoOfficialStreamEntry {
  readonly mediaGuid: string
  readonly data?: FnMediaOfficialStreamData
}

interface FeiniuVideoEnrichedCatalogItem {
  readonly item: FnMediaItem
  readonly context: FeiniuVideoCatalogItemContext
}

export interface FeiniuVideoCatalogService {
  request<T>(request: FeiniuVideoApiRequest): Promise<T>
  imageAccess(): FeiniuVideoCatalogImageAccess
}

export class FeiniuVideoCatalogApiService implements FeiniuVideoCatalogService {
  private readonly client: FeiniuVideoApiClient
  private readonly snapshotSync?: FeiniuVideoSessionSnapshotSync

  constructor(client: FeiniuVideoApiClient, snapshotSync?: FeiniuVideoSessionSnapshotSync) {
    this.client = client
    this.snapshotSync = snapshotSync
  }

  async request<T>(request: FeiniuVideoApiRequest): Promise<T> {
    const result = await this.client.request<T>(request)
    this.snapshotSync?.syncSnapshot()
    return result
  }

  imageAccess(): FeiniuVideoCatalogImageAccess {
    const activeCandidate = this.client.getActiveCandidate()
    const credentials = this.client.getCredentials()
    return new FeiniuVideoCatalogImageAccess(
      activeCandidate.baseUrl,
      credentials.token,
      credentials.cookie,
      activeCandidate.route === MediaConnectionRoute.Relay
    )
  }
}

export class FeiniuVideoCatalogErrorMapper {
  static from(error: Object, session: MediaProviderSession, operation: string): MediaCatalogError {
    if (error instanceof MediaCatalogError && error.accountScope === session.accountScope) {
      return error
    }
    if (error instanceof FeiniuVideoAuthenticationError) {
      return FeiniuVideoCatalogErrorMapper.error(
        MediaCatalogErrorCode.AuthenticationExpired,
        error,
        session,
        operation,
        'Feiniu Video authorization expired'
      )
    }
    if (error instanceof FeiniuVideoRequestError) {
      if (error.kind === FeiniuVideoRequestFailureKind.Connection) {
        return FeiniuVideoCatalogErrorMapper.error(
          MediaCatalogErrorCode.Offline, error, session, operation, 'Feiniu Video is offline')
      }
      if (error.kind === FeiniuVideoRequestFailureKind.Authentication) {
        return FeiniuVideoCatalogErrorMapper.error(
          MediaCatalogErrorCode.AuthenticationExpired,
          error,
          session,
          operation,
          'Feiniu Video authorization expired'
        )
      }
      if (error.kind === FeiniuVideoRequestFailureKind.InvalidRequest) {
        return FeiniuVideoCatalogErrorMapper.error(
          MediaCatalogErrorCode.InvalidRequest, error, session, operation, 'Feiniu Video request is invalid')
      }
      if (error.kind === FeiniuVideoRequestFailureKind.InvalidResponse) {
        return FeiniuVideoCatalogErrorMapper.error(
          MediaCatalogErrorCode.IncompatibleService,
          error,
          session,
          operation,
          'Feiniu Video returned an incompatible response'
        )
      }
      if (error.kind === FeiniuVideoRequestFailureKind.Business) {
        return FeiniuVideoCatalogErrorMapper.error(
          MediaCatalogErrorCode.RequestFailed,
          error,
          session,
          operation,
          'Feiniu Video rejected the catalog operation',
          false
        )
      }
    }
    return FeiniuVideoCatalogErrorMapper.error(
      MediaCatalogErrorCode.RequestFailed, error, session, operation, 'Feiniu Video request failed')
  }

  private static error(code: MediaCatalogErrorCode, source: Object, session: MediaProviderSession,
    operation: string, fallback: string, retryable?: boolean): MediaCatalogError {
    const value = source as Error
    return new MediaCatalogError({
      code: code,
      message: value.message && value.message.length > 0 ? value.message : fallback,
      accountScope: session.accountScope,
      operation: operation,
      retryable: retryable
    })
  }
}

interface FeiniuVideoLatestLibraryPage {
  readonly items: FnMediaItem[]
  readonly total: number
}

interface FeiniuVideoItemPage {
  readonly items: FnMediaItem[]
  readonly total: number
}

interface FeiniuVideoPersonItemPage {
  readonly items: FnMediaPersonItemListItem[]
  readonly total: number
}

class FeiniuVideoRecommendationSignals {
  readonly unfinished: FnMediaItem[]
  readonly completed: FnMediaItem[]

  constructor(unfinished: FnMediaItem[], completed: FnMediaItem[]) {
    this.unfinished = unfinished
    this.completed = completed
  }
}

export class FeiniuVideoCatalogProvider implements MediaCatalogProvider {
  readonly session: MediaProviderSession
  private readonly service: FeiniuVideoCatalogService
  private readonly genreNames: Map<number, string> = new Map<number, string>()
  private genresLoaded: boolean = false
  private recommendationCacheItems: FnMediaItem[] = []
  private recommendationCacheLimit: number = 0
  private recommendationCacheExpiresAt: number = 0
  private readonly itemDetailCache: Map<string, Promise<FnMediaItemDetailResp>> =
    new Map<string, Promise<FnMediaItemDetailResp>>()

  constructor(session: MediaProviderSession, service: FeiniuVideoCatalogService) {
    if (session.provider !== MediaProviderKind.FeiniuVideo) {
      throw new MediaCatalogError({
        code: MediaCatalogErrorCode.Unsupported,
        message: 'Feiniu Video catalog cannot serve a different provider',
        accountScope: session.accountScope,
        operation: 'constructor'
      })
    }
    if (!session.capabilities.videoCatalog) {
      throw new MediaCatalogError({
        code: MediaCatalogErrorCode.Unsupported,
        message: 'Feiniu Video catalog is disabled for this account',
        accountScope: session.accountScope,
        operation: 'constructor'
      })
    }
    this.session = session
    this.service = service
  }

  static fromClient(session: MediaProviderSession, client: FeiniuVideoApiClient,
    snapshotSync?: FeiniuVideoSessionSnapshotSync): FeiniuVideoCatalogProvider {
    return new FeiniuVideoCatalogProvider(
      session,
      new FeiniuVideoCatalogApiService(client, snapshotSync)
    )
  }

  rehydrateImage(image: MediaImageRef): MediaImageRef {
    return this.mapper().rehydrateImage(image)
  }

  getCounts(): Promise<MediaCatalogCounts> {
    return this.run('getCounts', async (): Promise<MediaCatalogCounts> => {
      const response = await this.request<FnMediaCountResp>(
        '/v/api/v1/mediadb/sum', FeiniuVideoHttpMethod.Get)
      return this.mapper().counts(response.data)
    })
  }

  getLibraries(): Promise<MediaLibrary[]> {
    return this.run('getLibraries', async (): Promise<MediaLibrary[]> => {
      const listResponse = await this.request<FnMediaListResp<FnMediaLibraryItem[]>>(
        '/v/api/v1/mediadb/list', FeiniuVideoHttpMethod.Get)
      const countResponse = await this.request<FnMediaCountResp>(
        '/v/api/v1/mediadb/sum', FeiniuVideoHttpMethod.Get)
      const mapper = this.mapper()
      return (listResponse.data || [])
        .filter((item: FnMediaLibraryItem): boolean => mapper.isVisibleLibrary(item))
        .map((item: FnMediaLibraryItem): MediaLibrary => mapper.library(item, countResponse.data))
    })
  }

  recommendations(request: MediaRecommendationsRequest): Promise<MediaSummary[]> {
    return this.run('recommendations', async (): Promise<MediaSummary[]> => {
      const resultLimit = Math.max(0, Math.floor(request.resultLimit))
      if (resultLimit === 0) {
        return []
      }
      const now = Date.now()
      if (now < this.recommendationCacheExpiresAt && this.recommendationCacheLimit >= resultLimit) {
        const mapper = this.mapper()
        return this.recommendationCacheItems.slice(0, resultLimit)
          .map((item: FnMediaItem): MediaSummary => mapper.summary(item))
      }

      const candidateLimit = Math.min(
        FEINIU_RECOMMENDATION_MAX_CANDIDATES,
        Math.max(FEINIU_RECOMMENDATION_MIN_CANDIDATES, resultLimit * 4)
      )
      const kinds = [MediaRefKind.Movie, MediaRefKind.Series]
      const playListPromise = this.optionalRecommendationPlayList()
      const groups = await Promise.all([
        this.optionalRecommendationPage(
          '/v/api/v1/item/list',
          this.itemListPayload('', kinds, 0, candidateLimit,
            [MediaCatalogSortField.CommunityRating], [MediaCatalogSortDirection.Descending])
        ),
        this.optionalRecommendationPage(
          '/v/api/v1/item/list',
          this.itemListPayload('', kinds, 0, candidateLimit,
            [MediaCatalogSortField.DateCreated], [MediaCatalogSortDirection.Descending])
        ),
        this.optionalRecommendationPage(
          '/v/api/v1/favorite/list',
          this.itemListPayload('', kinds, 0, candidateLimit,
            [MediaCatalogSortField.DateCreated], [MediaCatalogSortDirection.Descending])
        )
      ])
      const highestRated = groups[0]
      const latest = groups[1]
      const favorites = groups[2]
      const playItems = await playListPromise
      const signals = await this.recommendationSignals(
        playItems,
        latest.concat(highestRated).concat(favorites)
      )

      const selected = new FeiniuVideoRecommendationPlanner().rank(
        latest,
        highestRated,
        signals.unfinished,
        signals.completed,
        favorites,
        resultLimit,
        this.recommendationSeed(now)
      )
      const enriched = await Promise.all(selected.map((item: FnMediaItem, index: number): Promise<FnMediaItem> => {
        return index < FEINIU_RECOMMENDATION_DETAIL_LIMIT ? this.recommendationDetail(item) : Promise.resolve(item)
      }))
      await this.ensureGenres()
      this.recommendationCacheItems = enriched
      this.recommendationCacheLimit = resultLimit
      this.recommendationCacheExpiresAt = now + FEINIU_RECOMMENDATION_CACHE_TTL_MS
      const mapper = this.mapper()
      return enriched.map((item: FnMediaItem): MediaSummary => mapper.summary(item))
    })
  }

  resume(request: MediaResumeRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('resume', async (): Promise<MediaPage<MediaSummary>> => {
      const limit = this.limit(request.limit)
      const response = await this.request<FnMediaListResp<FnMediaPlayItem[]>>(
        '/v/api/v1/play/list', FeiniuVideoHttpMethod.Get)
      const mapper = this.mapper()
      const source = (response.data || []).filter((item: FnMediaPlayItem): boolean => {
        const kind = mapper.playItemKind(item)
        if (kind === MediaRefKind.LiveChannel && !this.session.capabilities.liveTv) {
          return false
        }
        return item.guid.trim().length > 0 &&
          (request.kinds.length === 0 || request.kinds.indexOf(kind) >= 0)
      })
      const enriched = await Promise.all(source.map(
        async (item: FnMediaPlayItem): Promise<MediaSummary> => {
          const catalogItem = await this.enrichCatalogItem(
            this.playCatalogItem(item), mapper.playItemKind(item))
          return mapper.resume(item, catalogItem.context)
        }))
      const items = this.uniqueResumeSummaries(enriched).slice(0, limit)
      return mapper.page(items, 0, limit, source.length, Math.min(source.length, limit))
    })
  }

  nextUp(request: MediaNextUpRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('nextUp', async (): Promise<MediaPage<MediaSummary>> => {
      this.requireOwnedOptional(request.seriesRef, 'nextUp')
      const limit = this.limit(request.limit)
      if (!request.seriesRef) {
        // Feiniu has no global next-up endpoint. Keep the home section empty instead of issuing N+1 series requests.
        return this.mapper().page([], 0, limit, 0, 0)
      }
      const episodePromise = this.episodeItemsForSeries(request.seriesRef.id)
      const playPromise = this.request<FnMediaListResp<FnMediaPlayItem[]>>(
        '/v/api/v1/play/list', FeiniuVideoHttpMethod.Get)
      const episodes = await episodePromise
      const playResponse = await playPromise
      const selected = this.selectNextUpEpisode(episodes, playResponse.data || [], request)
      if (!selected) {
        return this.mapper().page([], 0, limit, 0, 0)
      }
      await this.ensureGenres()
      const enriched = await this.enrichCatalogItem(selected, MediaRefKind.Episode)
      const item = this.mapper().summary(selected, enriched.context)
      return this.mapper().page([item], 0, limit, 1, 1)
    })
  }

  latest(request: MediaLatestRequest): Promise<MediaPage<MediaSummary>> {
    if (request.libraryRef) {
      return this.latestForLibrary(request)
    }
    return this.latestAcrossLibraries(request)
  }

  getItems(request: MediaListRequest): Promise<MediaPage<MediaSummary>> {
    if (request.personRef) {
      return this.personItems(request)
    }
    return this.run('getItems', async (): Promise<MediaPage<MediaSummary>> => {
      this.requireOwnedOptional(request.libraryRef, 'getItems')
      this.requireOwnedOptional(request.parentRef, 'getItems')
      if (this.onlyUnsupportedLive(request.kinds)) {
        return this.mapper().page([], this.startIndex(request.startIndex), this.limit(request.limit), 0, 0)
      }
      const startIndex = this.startIndex(request.startIndex)
      const limit = this.limit(request.limit)
      const payload = this.itemListPayload(
        request.parentRef?.id || request.libraryRef?.id || '',
        request.kinds,
        startIndex,
        limit,
        request.sortFields,
        request.sortDirections,
        request.watched
      )
      const path = request.favoriteOnly ? '/v/api/v1/favorite/list' : '/v/api/v1/item/list'
      const page = await this.itemPage(path, payload, startIndex, limit)
      const source = page.items
      await this.ensureGenres()
      const mapper = this.mapper()
      const mapped = source
        .filter((item: FnMediaItem): boolean => mapper.isVisibleItem(item) && mapper.matchesKinds(item, request.kinds))
        .map((item: FnMediaItem): MediaSummary => mapper.summary(item))
      const items = this.uniqueSummaries(mapped)
      return mapper.page(items, startIndex, limit, page.total, source.length)
    })
  }

  search(request: MediaSearchRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('search', async (): Promise<MediaPage<MediaSummary>> => {
      this.requireOwnedOptional(request.libraryRef, 'search')
      const query = request.query.trim()
      const startIndex = this.startIndex(request.startIndex)
      const limit = this.limit(request.limit)
      if (query.length === 0 || this.onlyUnsupportedLive(request.kinds)) {
        return this.mapper().page([], startIndex, limit, 0, 0)
      }
      const response = await this.request<FnMediaListResp<FnMediaItem[]>>(
        '/v/api/v1/search/list',
        FeiniuVideoHttpMethod.Get,
        '',
        [new FeiniuVideoQueryParameter('q', query)]
      )
      await this.ensureGenres()
      const mapper = this.mapper()
      const filtered = (response.data || []).filter((item: FnMediaItem): boolean => {
        if (!mapper.isVisibleItem(item) || !mapper.matchesKinds(item, request.kinds)) {
          return false
        }
        const libraryId = request.libraryRef?.id || ''
        if (libraryId.length === 0) {
          return true
        }
        return `${item.ancestor_guid || ''}`.trim() === libraryId ||
          `${item.parent_guid || ''}`.trim() === libraryId
      })
      const selected = filtered.slice(startIndex, startIndex + limit)
      const items = selected.map((item: FnMediaItem): MediaSummary => mapper.summary(item))
      return mapper.page(items, startIndex, limit, filtered.length, selected.length)
    })
  }

  getDetail(ref: MediaRef): Promise<MediaDetail> {
    return this.run('getDetail', async (): Promise<MediaDetail> => {
      this.requireOwned(ref, 'getDetail')
      const detail = await this.itemDetail(ref.id)
      const mapperBeforeSeasons = this.mapper()
      const detailKind = ref.kind !== MediaRefKind.Unknown ? ref.kind : mapperBeforeSeasons.refKind(detail.type)
      const peoplePromise = this.detailPeople(ref.id)
      const seasonPromise: Promise<MediaSeason[]> = detailKind === MediaRefKind.Series
        ? this.seasonItems(ref.id).then((items: FnMediaSeasonItem[]): MediaSeason[] => {
          const seriesRef = new MediaRef(this.session.provider, this.session.accountScope, ref.id, MediaRefKind.Series)
          const mapper = this.mapper()
          return items.map((item: FnMediaSeasonItem): MediaSeason => mapper.season(item, seriesRef))
        }).catch((error: Error): MediaSeason[] => {
          if (this.isAuthenticationError(error)) throw error
          console.warn(`[FeiniuVideoCatalog] season list failed item=${ref.id} error=${error.message || 'unknown'}`)
          return []
        }) : Promise.resolve([])
      const playInfoPromise: Promise<FnMediaPlayInfoResp | undefined> = this.supportsPlayInfo(detailKind)
        ? this.optionalPlayInfo(ref.id) : Promise.resolve(undefined)
      const streamPromise: Promise<FnMediaStreamListData | undefined> = this.supportsDetailStreams(detailKind)
        ? playInfoPromise.then((playInfo: FnMediaPlayInfoResp | undefined): Promise<FnMediaStreamListData> =>
          this.detailStreams(detail, playInfo))
        : Promise.resolve(undefined)
      const genrePromise = this.ensureGenres()
      const contextPromise = this.itemContext(detail, detailKind)
      const peopleResponse = await peoplePromise
      const seasons = await seasonPromise
      const streamData = await streamPromise
      const playInfo = await playInfoPromise
      await genrePromise
      const context = await contextPromise
      return this.mapper().detail(detail, peopleResponse.data?.list || [], seasons, streamData, context, playInfo)
    })
  }

  private async itemDetail(itemId: string): Promise<FnMediaItemDetailResp> {
    const normalizedId = itemId.trim()
    const cached = this.itemDetailCache.get(normalizedId)
    if (cached) return await cached
    const pending = this.request<FnMediaListResp<FnMediaItemDetailResp>>(
      `/v/api/v1/item/${encodeURIComponent(normalizedId)}`,
      FeiniuVideoHttpMethod.Get
    ).then((response: FnMediaListResp<FnMediaItemDetailResp>): FnMediaItemDetailResp => {
      const detail = response.data
      if (!detail || `${detail.guid || ''}`.trim() !== normalizedId) {
        throw new FeiniuVideoRequestError(
          FeiniuVideoRequestFailureKind.InvalidResponse,
          'Feiniu Video detail response does not match the requested item'
        )
      }
      return detail
    })
    this.itemDetailCache.set(normalizedId, pending)
    try {
      return await pending
    } finally {
      if (this.itemDetailCache.get(normalizedId) === pending) {
        this.itemDetailCache.delete(normalizedId)
      }
    }
  }

  private async optionalItemDetail(itemId: string): Promise<FnMediaItemDetailResp | undefined> {
    const normalizedId = itemId.trim()
    if (normalizedId.length === 0) return undefined
    try {
      return await this.itemDetail(normalizedId)
    } catch (error) {
      if (this.isAuthenticationError(error as Object)) throw error as Error
      console.warn(`[FeiniuVideoCatalog] item detail failed item=${normalizedId} ` +
        `error=${(error as Error).message || 'unknown'}`)
      return undefined
    }
  }

  private async detailPeople(itemId: string): Promise<FnMediaPersonListResp> {
    try {
      return await this.request<FnMediaPersonListResp>(
        `/v/api/v1/person/list/${encodeURIComponent(itemId)}`,
        FeiniuVideoHttpMethod.Post
      )
    } catch (error) {
      if (this.isAuthenticationError(error as Object)) throw error as Error
      console.warn(`[FeiniuVideoCatalog] people failed item=${itemId} ` +
        `error=${(error as Error).message || 'unknown'}`)
      return { code: 0, msg: '', data: { list: [], total: 0 } }
    }
  }

  private async enrichCatalogItem(item: FnMediaItem,
    kindOverride?: MediaRefKind): Promise<FeiniuVideoEnrichedCatalogItem> {
    const mapper = this.mapper()
    const resolvedKind = kindOverride !== undefined && kindOverride !== MediaRefKind.Unknown
      ? kindOverride : mapper.refKind(item.type)
    let enriched: FnMediaItem = item
    const hierarchyNeedsDetail = (resolvedKind === MediaRefKind.Episode || resolvedKind === MediaRefKind.Season) &&
      `${item.parent_guid || ''}`.trim().length === 0
    const detail = hierarchyNeedsDetail || this.itemPrimaryImagePath(item).length === 0
      ? await this.optionalItemDetail(item.guid) : undefined
    if (detail) {
      this.applyItemFallbacks(detail, item)
      enriched = detail
    }
    return {
      item: enriched,
      context: await this.itemContext(enriched, resolvedKind)
    }
  }

  private playCatalogItem(item: FnMediaPlayItem): FnMediaItem {
    const result = {} as FnMediaItem
    result.guid = item.guid
    result.title = item.title
    result.type = item.type
    result.poster = item.poster
    result.still_path = item.still_path
    result.parent_guid = item.parent_guid
    result.parent_title = item.parent_title
    result.ancestor_guid = item.ancestor_guid
    result.ancestor_name = item.ancestor_name
    result.ancestor_category = item.ancestor_category
    result.tv_title = item.tv_title
    result.watched = item.watched
    result.watched_ts = item.watched_ts
    result.is_favorite = item.is_favorite
    result.status = item.status
    result.ts = item.ts
    result.duration = item.duration
    result.season_number = item.season_number
    result.episode_number = item.episode_number
    result.media_guid = item.media_guid
    result.video_guid = item.video_guid
    result.audio_guid = item.audio_guid
    result.subtitle_guid = item.subtitle_guid
    result.file_name = item.file_name
    result.single_child_guid = item.single_child_guid
    result.play_item_guid = item.play_item_guid
    result.can_play = item.can_play
    if (item.media_stream) {
      const stream = {} as FnMediaItemStream
      stream.progress = item.media_stream.progress
      stream.current_time = item.media_stream.current_time
      result.media_stream = stream
    }
    return result
  }

  private applyItemFallbacks(detail: FnMediaItemDetailResp, fallback: FnMediaItem): void {
    detail.title = detail.title || fallback.title
    detail.type = detail.type || fallback.type
    detail.poster = detail.poster || fallback.poster
    detail.still_path = detail.still_path || fallback.still_path
    detail.parent_guid = detail.parent_guid || fallback.parent_guid
    detail.parent_title = detail.parent_title || fallback.parent_title
    detail.ancestor_guid = detail.ancestor_guid || fallback.ancestor_guid
    detail.ancestor_name = detail.ancestor_name || fallback.ancestor_name
    detail.ancestor_category = detail.ancestor_category || fallback.ancestor_category
    detail.tv_title = detail.tv_title || fallback.tv_title
    detail.overview = detail.overview || fallback.overview
    detail.vote_average = detail.vote_average || fallback.vote_average
    detail.release_date = detail.release_date || fallback.release_date
    detail.air_date = detail.air_date || fallback.air_date
    detail.first_air_date = detail.first_air_date || fallback.first_air_date
    detail.last_air_date = detail.last_air_date || fallback.last_air_date
    detail.duration = detail.duration || fallback.duration
    detail.runtime = detail.runtime || fallback.runtime
    detail.season_number = detail.season_number || fallback.season_number
    detail.episode_number = detail.episode_number || fallback.episode_number
    detail.genres = detail.genres || fallback.genres
    detail.media_stream = detail.media_stream || fallback.media_stream
    detail.media_guid = detail.media_guid || fallback.media_guid
    detail.video_guid = detail.video_guid || fallback.video_guid
    detail.audio_guid = detail.audio_guid || fallback.audio_guid
    detail.subtitle_guid = detail.subtitle_guid || fallback.subtitle_guid
    detail.file_name = detail.file_name || fallback.file_name
    detail.single_child_guid = detail.single_child_guid || fallback.single_child_guid
    detail.play_item_guid = detail.play_item_guid || fallback.play_item_guid
    detail.can_play = detail.can_play === undefined ? fallback.can_play : detail.can_play
    detail.watched = detail.watched === undefined ? fallback.watched : detail.watched
    detail.watched_ts = detail.watched_ts === undefined ? fallback.watched_ts : detail.watched_ts
    detail.is_favorite = detail.is_favorite === undefined ? fallback.is_favorite : detail.is_favorite
    detail.ts = detail.ts === undefined ? fallback.ts : detail.ts
  }

  private async itemContext(item: FnMediaItem,
    kindOverride?: MediaRefKind): Promise<FeiniuVideoCatalogItemContext> {
    const mapper = this.mapper()
    const kind = kindOverride !== undefined && kindOverride !== MediaRefKind.Unknown
      ? kindOverride : mapper.refKind(item.type)
    const detail = item as FnMediaItemDetailResp
    let seriesId = ''
    let seasonId = ''
    let seriesTitle = `${item.tv_title || ''}`.trim()
    let seasonTitle = ''
    let primaryImagePath = this.itemPrimaryImagePath(item)
    let backdropImagePath = `${detail.backdrops || ''}`.trim()
    let logoImagePath = `${detail.logos || ''}`.trim()
    let seasonDetail: FnMediaItemDetailResp | undefined
    let seriesDetail: FnMediaItemDetailResp | undefined

    if (kind === MediaRefKind.Series) {
      seriesId = item.guid.trim()
      seriesTitle = seriesTitle || item.title
    } else if (kind === MediaRefKind.Season) {
      const parentId = `${item.parent_guid || ''}`.trim()
      const libraryId = `${item.ancestor_guid || ''}`.trim()
      seriesId = parentId.length > 0 && parentId !== libraryId ? parentId : ''
      seasonTitle = item.title || ''
    } else if (kind === MediaRefKind.Episode) {
      const parentId = `${item.parent_guid || ''}`.trim()
      const parentDetail = await this.optionalItemDetail(parentId)
      const parentKind = parentDetail ? mapper.refKind(parentDetail.type) : MediaRefKind.Unknown
      if (parentDetail && parentKind === MediaRefKind.Series) {
        seriesId = parentDetail.guid.trim() || parentId
        seriesTitle = seriesTitle || parentDetail.title || `${item.parent_title || ''}`.trim()
        seriesDetail = parentDetail
      } else {
        seasonId = parentId
        seasonDetail = parentDetail
        if (seasonDetail) {
          seasonTitle = seasonDetail.title || ''
          const seriesParentId = `${seasonDetail.parent_guid || ''}`.trim()
          const libraryId = `${seasonDetail.ancestor_guid || ''}`.trim()
          seriesId = seriesParentId.length > 0 && seriesParentId !== libraryId ? seriesParentId : ''
          seriesTitle = seriesTitle || `${seasonDetail.parent_title || ''}`.trim()
          primaryImagePath = primaryImagePath || this.itemPrimaryImagePath(seasonDetail)
          backdropImagePath = backdropImagePath || `${seasonDetail.backdrops || ''}`.trim()
          logoImagePath = logoImagePath || `${seasonDetail.logos || ''}`.trim()
        }
      }
    }

    if (!seriesDetail && seriesId.length > 0 && seriesId !== item.guid) {
      seriesDetail = await this.optionalItemDetail(seriesId)
    }
    if (seriesDetail) {
      seriesTitle = seriesTitle || seriesDetail.title || ''
      primaryImagePath = primaryImagePath || this.itemPrimaryImagePath(seriesDetail)
      backdropImagePath = backdropImagePath || `${seriesDetail.backdrops || ''}`.trim()
      logoImagePath = logoImagePath || `${seriesDetail.logos || ''}`.trim()
    }

    primaryImagePath = primaryImagePath || backdropImagePath
    backdropImagePath = backdropImagePath || primaryImagePath
    return {
      kind: kind,
      seriesId: seriesId,
      seasonId: seasonId,
      seriesTitle: seriesTitle,
      seasonTitle: seasonTitle,
      primaryImagePath: primaryImagePath,
      backdropImagePath: backdropImagePath,
      logoImagePath: logoImagePath
    }
  }

  private itemPrimaryImagePath(item: FnMediaItem): string {
    const detail = item as FnMediaItemDetailResp
    return `${item.still_path || item.poster || detail.posters || ''}`.trim()
  }

  private uniqueResumeSummaries(items: MediaSummary[]): MediaSummary[] {
    const result: MediaSummary[] = []
    const seen = new Set<string>()
    items.forEach((item: MediaSummary): void => {
      const key = item.ref.kind === MediaRefKind.Episode && item.seriesRef
        ? `series:${item.seriesRef.identity}` : `item:${item.ref.identity}`
      if (!seen.has(key)) {
        seen.add(key)
        result.push(item)
      }
    })
    return result
  }

  private supportsDetailStreams(kind: MediaRefKind): boolean {
    return kind === MediaRefKind.Movie || kind === MediaRefKind.Episode || kind === MediaRefKind.Video
  }

  private supportsPlayInfo(kind: MediaRefKind): boolean {
    return kind === MediaRefKind.Movie || kind === MediaRefKind.Series || kind === MediaRefKind.Season ||
      kind === MediaRefKind.Episode || kind === MediaRefKind.Video
  }

  private async streamList(itemId: string): Promise<FnMediaStreamListData> {
    const response = await this.request<FnMediaStreamListResp>(
      `/v/api/v1/stream/list/${encodeURIComponent(itemId)}`,
      FeiniuVideoHttpMethod.Get,
      '',
      [new FeiniuVideoQueryParameter('before_play', 1)]
    )
    return response.data || {}
  }

  private async detailStreams(detail: FnMediaItemDetailResp,
    playInfo?: FnMediaPlayInfoResp): Promise<FnMediaStreamListData> {
    const supplemental = await Promise.all([
      this.optionalStreamList(detail.guid),
      this.optionalItemFiles(detail.guid)
    ])
    const listed = this.mergeItemFiles(
      supplemental[0] as FnMediaStreamListData,
      supplemental[1] as FnMediaItemFile[]
    )

    const preferredMediaGuid = `${playInfo?.data?.media_guid || detail.media_guid || ''}`.trim()
    const mediaGuids = this.detailMediaGuids(detail, listed, preferredMediaGuid)
    if (mediaGuids.length === 0) {
      return this.preferMediaFile(listed, preferredMediaGuid)
    }

    let ip = ''
    try {
      ip = await this.officialStreamIp()
    } catch (error) {
      console.warn(`[FeiniuVideoCatalog] official stream identity failed item=${detail.guid} error=${(error as Error).message || 'unknown'}`)
      return this.preferMediaFile(listed, preferredMediaGuid)
    }

    const requests: Promise<FeiniuVideoOfficialStreamEntry>[] = mediaGuids.map(
      (mediaGuid: string): Promise<FeiniuVideoOfficialStreamEntry> => {
        return this.officialStreamInfo(mediaGuid, ip)
          .then((data: FnMediaOfficialStreamData): FeiniuVideoOfficialStreamEntry => {
            return { mediaGuid: mediaGuid, data: data }
          })
          .catch((error: Error): FeiniuVideoOfficialStreamEntry => {
            console.warn(`[FeiniuVideoCatalog] official stream failed media=${mediaGuid} error=${error.message || 'unknown'}`)
            return { mediaGuid: mediaGuid }
          })
      })
    const officialEntries = await Promise.all(requests)
    const merged = this.mergeDetailStreams(detail, listed, officialEntries)
    console.info(`[FeiniuVideoCatalog] detail streams item=${detail.guid} files=${merged.files?.length || 0} ` +
      `videos=${merged.video_streams?.length || 0} audios=${merged.audio_streams?.length || 0} ` +
      `subtitles=${merged.subtitle_streams?.length || 0}`)
    return this.preferMediaFile(merged, preferredMediaGuid)
  }

  private async optionalStreamList(itemGuid: string): Promise<FnMediaStreamListData> {
    try {
      return await this.streamList(itemGuid)
    } catch (error) {
      if (this.isAuthenticationError(error as Object)) throw error as Error
      console.warn(`[FeiniuVideoCatalog] stream list failed item=${itemGuid} ` +
        `error=${(error as Error).message || 'unknown'}`)
      return {}
    }
  }

  private async optionalItemFiles(itemGuid: string): Promise<FnMediaItemFile[]> {
    try {
      const response = await this.request<FnMediaListResp<FnMediaItemFile[]>>(
        `/v/api/v1/media/itemfile/${encodeURIComponent(itemGuid)}`,
        FeiniuVideoHttpMethod.Get
      )
      return response.data || []
    } catch (error) {
      if (this.isAuthenticationError(error as Object)) throw error as Error
      console.warn(`[FeiniuVideoCatalog] item files failed item=${itemGuid} ` +
        `error=${(error as Error).message || 'unknown'}`)
      return []
    }
  }

  private async optionalPlayInfo(itemGuid: string): Promise<FnMediaPlayInfoResp | undefined> {
    try {
      const payload: FnMediaPlayInfoReq = { item_guid: itemGuid }
      return await this.request<FnMediaPlayInfoResp>(
        '/v/api/v1/play/info',
        FeiniuVideoHttpMethod.Post,
        JSON.stringify(payload)
      )
    } catch (error) {
      if (this.isAuthenticationError(error as Object)) throw error as Error
      console.warn(`[FeiniuVideoCatalog] play info failed item=${itemGuid} ` +
        `error=${(error as Error).message || 'unknown'}`)
      return undefined
    }
  }

  private mergeItemFiles(listed: FnMediaStreamListData, itemFiles: FnMediaItemFile[]): FnMediaStreamListData {
    const files = (listed.files || []).slice()
    itemFiles.forEach((item: FnMediaItemFile): void => {
      const mediaGuid = `${item.media_guid || item.item_guid || ''}`.trim()
      if (mediaGuid.length === 0) return
      const existingIndex = files.findIndex((file: FnMediaStreamFileInfo): boolean =>
        `${file.guid || ''}`.trim() === mediaGuid)
      if (existingIndex >= 0) {
        const existing = files[existingIndex]
        existing.file_name = existing.file_name || item.filename
        existing.path = existing.path || item.file_path
        existing.size = existing.size || item.size || item.file_size
        existing.create_time = existing.create_time || item.create_time
        existing.update_time = existing.update_time || item.update_time
        existing.file_birth_time = existing.file_birth_time || item.file_birth_time
        existing.timestamp = existing.timestamp || item.timestamp
        existing.duration = existing.duration || item.duration
        return
      }
      files.push({
        guid: mediaGuid,
        file_name: item.filename,
        path: item.file_path,
        size: item.size || item.file_size,
        create_time: item.create_time,
        update_time: item.update_time,
        file_birth_time: item.file_birth_time,
        timestamp: item.timestamp,
        duration: item.duration
      })
    })
    return {
      files: files,
      video_streams: (listed.video_streams || []).slice(),
      audio_streams: (listed.audio_streams || []).slice(),
      subtitle_streams: (listed.subtitle_streams || []).slice()
    }
  }

  private preferMediaFile(data: FnMediaStreamListData, preferredMediaGuid: string): FnMediaStreamListData {
    const preferred = preferredMediaGuid.trim()
    const files = (data.files || []).slice().sort((left: FnMediaStreamFileInfo,
      right: FnMediaStreamFileInfo): number => {
      const leftSort = left.sort_num
      const rightSort = right.sort_num
      if (leftSort !== undefined && rightSort !== undefined && leftSort !== rightSort) return leftSort - rightSort
      if (leftSort !== undefined && rightSort === undefined) return -1
      if (leftSort === undefined && rightSort !== undefined) return 1
      return `${left.file_name || left.path || left.guid || ''}`.localeCompare(
        `${right.file_name || right.path || right.guid || ''}`)
    })
    if (preferred.length > 0) {
      const index = files.findIndex((file: FnMediaStreamFileInfo): boolean =>
        `${file.guid || ''}`.trim() === preferred)
      if (index > 0) {
        const selected = files[index]
        files.splice(index, 1)
        files.unshift(selected)
      }
    }
    return {
      files: files,
      video_streams: (data.video_streams || []).slice(),
      audio_streams: (data.audio_streams || []).slice(),
      subtitle_streams: (data.subtitle_streams || []).slice()
    }
  }

  private detailMediaGuids(detail: FnMediaItemDetailResp, data: FnMediaStreamListData,
    preferredMediaGuid: string): string[] {
    const result: string[] = []
    const append = (value?: string): void => {
      const normalized = `${value || ''}`.trim()
      if (normalized.length > 0 && result.indexOf(normalized) < 0) {
        result.push(normalized)
      }
    }
    const files = data.files || []
    const videos = data.video_streams || []
    const audios = data.audio_streams || []
    const subtitles = data.subtitle_streams || []
    files.forEach((file: FnMediaStreamFileInfo): void => append(file.guid))
    videos.forEach((stream: FnMediaStreamVideoInfo): void => append(stream.media_guid))
    audios.forEach((stream: FnMediaStreamAudioInfo): void => append(stream.media_guid))
    subtitles.forEach((stream: FnMediaStreamSubtitleInfo): void => append(stream.media_guid))
    append(preferredMediaGuid)
    append(detail.media_guid)
    return result
  }

  private async officialStreamIp(): Promise<string> {
    const userInfo = await this.request<FnMediaUserInfoResp>(
      '/v/api/v1/user/info', FeiniuVideoHttpMethod.Get)
    const sources = userInfo.data?.sources || []
    const sourceName = sources.length > 0 ? `${sources[0].source_name || ''}`.trim() : ''
    return CryptoJS.MD5(sourceName).toString()
  }

  private async officialStreamInfo(mediaGuid: string, ip: string): Promise<FnMediaOfficialStreamData> {
    const payload: FnMediaOfficialStreamReq = {
      media_guid: mediaGuid,
      ip: ip,
      level: 1,
      header: {
        'User-Agent': [FEINIU_OFFICIAL_STREAM_USER_AGENT]
      }
    }
    const response = await this.service.request<FnMediaOfficialStreamResp>(new FeiniuVideoApiRequest(
      '/v/api/v1/stream',
      FeiniuVideoHttpMethod.Post,
      JSON.stringify(payload),
      [],
      true,
      60000
    ))
    return response.data || {}
  }

  private mergeDetailStreams(detail: FnMediaItemDetailResp, listed: FnMediaStreamListData,
    officialEntries: FeiniuVideoOfficialStreamEntry[]): FnMediaStreamListData {
    const files = (listed.files || []).slice()
    let videos = (listed.video_streams || []).slice()
    let audios = (listed.audio_streams || []).slice()
    let subtitles = (listed.subtitle_streams || []).slice()

    officialEntries.forEach((entry: FeiniuVideoOfficialStreamEntry): void => {
      const data = entry.data
      if (!data) {
        return
      }
      const mediaGuid = entry.mediaGuid
      const file = data.file_stream
      if (file) {
        if (`${file.guid || ''}`.trim().length === 0) file.guid = mediaGuid
        file.direct_link_qualities = data.direct_link_qualities || data.qualities || file.direct_link_qualities
        const fileIndex = files.findIndex((value: FnMediaStreamFileInfo): boolean =>
          `${value.guid || ''}`.trim() === mediaGuid)
        if (fileIndex >= 0) {
          const listedFile = files[fileIndex]
          file.path = file.path || listedFile.path
          file.file_name = file.file_name || listedFile.file_name
          file.size = file.size || listedFile.size
          file.timestamp = file.timestamp || listedFile.timestamp
          file.type = file.type ?? listedFile.type
          file.sort_num = file.sort_num ?? listedFile.sort_num
          file.can_play = file.can_play ?? listedFile.can_play
          file.play_error = file.play_error || listedFile.play_error
          file.create_time = file.create_time || listedFile.create_time
          file.update_time = file.update_time || listedFile.update_time
          file.file_birth_time = file.file_birth_time || listedFile.file_birth_time
          file.progress_thumb_hash_dir = file.progress_thumb_hash_dir || listedFile.progress_thumb_hash_dir
          file.duration = file.duration || listedFile.duration
          file.direct_link_qualities = file.direct_link_qualities || listedFile.direct_link_qualities
          files[fileIndex] = file
        } else files.push(file)
      } else if (!files.some((value: FnMediaStreamFileInfo): boolean =>
        `${value.guid || ''}`.trim() === mediaGuid)) {
        files.push({
          guid: mediaGuid,
          file_name: detail.file_name,
          path: '',
          size: 0,
          direct_link_qualities: data.direct_link_qualities || data.qualities
        })
      }

      if (data.video_stream) {
        if (`${data.video_stream.media_guid || ''}`.trim().length === 0) {
          data.video_stream.media_guid = mediaGuid
        }
        videos = this.mergeVideoStream(videos, data.video_stream)
      }
      const officialAudios = data.audio_streams || []
      const officialSubtitles = data.subtitle_streams || []
      officialAudios.forEach((stream: FnMediaStreamAudioInfo): void => {
        if (`${stream.media_guid || ''}`.trim().length === 0) stream.media_guid = mediaGuid
        audios = this.mergeAudioStream(audios, stream)
      })
      officialSubtitles.forEach((stream: FnMediaStreamSubtitleInfo): void => {
        if (`${stream.media_guid || ''}`.trim().length === 0) stream.media_guid = mediaGuid
        subtitles = this.mergeSubtitleStream(subtitles, stream)
      })
    })

    return {
      files: files,
      video_streams: videos,
      audio_streams: audios,
      subtitle_streams: subtitles
    }
  }

  private mergeVideoStream(values: FnMediaStreamVideoInfo[], replacement: FnMediaStreamVideoInfo):
    FnMediaStreamVideoInfo[] {
    const result = values.slice()
    const index = result.findIndex((value: FnMediaStreamVideoInfo): boolean => {
      const replacementGuid = `${replacement.guid || ''}`.trim()
      if (replacementGuid.length > 0 && `${value.guid || ''}`.trim() === replacementGuid) return true
      return `${value.media_guid || ''}`.trim() === `${replacement.media_guid || ''}`.trim() &&
        (value.index ?? 0) === (replacement.index ?? 0)
    })
    if (index >= 0) {
      const current = result[index]
      result[index] = {
        media_guid: replacement.media_guid || current.media_guid,
        guid: replacement.guid || current.guid,
        title: replacement.title || current.title,
        index: replacement.index ?? current.index,
        resolution_type: replacement.resolution_type || current.resolution_type,
        color_range_type: replacement.color_range_type || current.color_range_type,
        color_range: replacement.color_range || current.color_range,
        codec_name: replacement.codec_name || current.codec_name,
        codec_type: replacement.codec_type || current.codec_type,
        profile: replacement.profile || current.profile,
        width: replacement.width ?? current.width,
        height: replacement.height ?? current.height,
        coded_width: replacement.coded_width ?? current.coded_width,
        coded_height: replacement.coded_height ?? current.coded_height,
        display_aspect_ratio: replacement.display_aspect_ratio || current.display_aspect_ratio,
        pix_fmt: replacement.pix_fmt || current.pix_fmt,
        level: replacement.level || current.level,
        color_space: replacement.color_space || current.color_space,
        color_transfer: replacement.color_transfer || current.color_transfer,
        color_primaries: replacement.color_primaries || current.color_primaries,
        dv_profile: replacement.dv_profile ?? current.dv_profile,
        bits_per_raw_sample: replacement.bits_per_raw_sample || current.bits_per_raw_sample,
        wrapper: replacement.wrapper || current.wrapper,
        duration: replacement.duration ?? current.duration,
        bps: replacement.bps ?? current.bps,
        fps: replacement.fps ?? current.fps,
        frame_rate: replacement.frame_rate ?? current.frame_rate,
        avg_frame_rate: replacement.avg_frame_rate || current.avg_frame_rate,
        r_frame_rate: replacement.r_frame_rate || current.r_frame_rate,
        progressive: replacement.progressive ?? current.progressive,
        bit_depth: replacement.bit_depth ?? current.bit_depth,
        refs: replacement.refs ?? current.refs,
        reference_frames: replacement.reference_frames ?? current.reference_frames,
        rotation: replacement.rotation ?? current.rotation,
        is_bluray: replacement.is_bluray ?? current.is_bluray
      }
    }
    else result.push(replacement)
    return result
  }

  private mergeAudioStream(values: FnMediaStreamAudioInfo[], replacement: FnMediaStreamAudioInfo):
    FnMediaStreamAudioInfo[] {
    const result = values.slice()
    const index = result.findIndex((value: FnMediaStreamAudioInfo): boolean => {
      const replacementGuid = `${replacement.guid || ''}`.trim()
      if (replacementGuid.length > 0 && `${value.guid || ''}`.trim() === replacementGuid) return true
      return `${value.media_guid || ''}`.trim() === `${replacement.media_guid || ''}`.trim() &&
        (value.index ?? 0) === (replacement.index ?? 0)
    })
    if (index >= 0) {
      const current = result[index]
      result[index] = {
        media_guid: replacement.media_guid || current.media_guid,
        guid: replacement.guid || current.guid,
        index: replacement.index ?? current.index,
        title: replacement.title || current.title,
        audio_type: replacement.audio_type || current.audio_type,
        codec_name: replacement.codec_name || current.codec_name,
        codec_type: replacement.codec_type || current.codec_type,
        language: replacement.language || current.language,
        channels: replacement.channels ?? current.channels,
        profile: replacement.profile || current.profile,
        sample_rate: replacement.sample_rate || current.sample_rate,
        is_default: replacement.is_default ?? current.is_default,
        channel_layout: replacement.channel_layout || current.channel_layout,
        duration: replacement.duration ?? current.duration,
        bps: replacement.bps ?? current.bps,
        is_fake: replacement.is_fake ?? current.is_fake
      }
    }
    else result.push(replacement)
    return result
  }

  private mergeSubtitleStream(values: FnMediaStreamSubtitleInfo[], replacement: FnMediaStreamSubtitleInfo):
    FnMediaStreamSubtitleInfo[] {
    const result = values.slice()
    const index = result.findIndex((value: FnMediaStreamSubtitleInfo): boolean => {
      const replacementGuid = `${replacement.guid || ''}`.trim()
      if (replacementGuid.length > 0 && `${value.guid || ''}`.trim() === replacementGuid) return true
      return `${value.media_guid || ''}`.trim() === `${replacement.media_guid || ''}`.trim() &&
        (value.index ?? 0) === (replacement.index ?? 0)
    })
    if (index >= 0) {
      const current = result[index]
      result[index] = {
        media_guid: replacement.media_guid || current.media_guid,
        guid: replacement.guid || current.guid,
        index: replacement.index ?? current.index,
        title: replacement.title || current.title,
        codec_name: replacement.codec_name || current.codec_name,
        codec_type: replacement.codec_type || current.codec_type,
        format: replacement.format || current.format,
        language: replacement.language || current.language,
        forced: replacement.forced ?? current.forced,
        is_default: replacement.is_default ?? current.is_default,
        is_external: replacement.is_external ?? current.is_external,
        trim_id: replacement.trim_id || current.trim_id,
        source_id: replacement.source_id || current.source_id,
        Source: replacement.Source || current.Source,
        create_time: replacement.create_time ?? current.create_time,
        update_time: replacement.update_time ?? current.update_time,
        extra_file: replacement.extra_file ?? current.extra_file,
        is_bitmap: replacement.is_bitmap ?? current.is_bitmap,
        file_size: replacement.file_size ?? current.file_size
      }
    }
    else result.push(replacement)
    return result
  }

  getSeasons(seriesRef: MediaRef): Promise<MediaSeason[]> {
    return this.run('getSeasons', async (): Promise<MediaSeason[]> => {
      this.requireOwned(seriesRef, 'getSeasons')
      const source = await this.seasonItems(seriesRef.id)
      await this.ensureGenres()
      const mapper = this.mapper()
      return source.map((item: FnMediaSeasonItem): MediaSeason => mapper.season(item, seriesRef))
    })
  }

  getEpisodes(request: MediaEpisodeRequest): Promise<MediaPage<MediaEpisode>> {
    return this.run('getEpisodes', async (): Promise<MediaPage<MediaEpisode>> => {
      this.requireOwned(request.seriesRef, 'getEpisodes')
      this.requireOwnedOptional(request.seasonRef, 'getEpisodes')
      const source = request.seasonRef
        ? await this.episodeItems(request.seasonRef.id)
        : await this.episodeItemsForSeries(request.seriesRef.id)
      await this.ensureGenres()
      const mapper = this.mapper()
      const visible = source.filter((item: FnMediaEpisodeItem): boolean => mapper.isVisibleItem(item))
      if (request.profile === MediaEpisodeQueryProfile.PlaybackQueue) {
        const items = visible.map((item: FnMediaEpisodeItem): MediaEpisode => {
          return mapper.episode(item, request.seriesRef, request.seasonRef)
        })
        return mapper.page(items, 0, Math.max(1, visible.length), visible.length, visible.length)
      }
      const startIndex = this.startIndex(request.startIndex)
      const limit = this.limit(request.limit)
      const selected = visible.slice(startIndex, startIndex + limit)
      const items = selected.map((item: FnMediaEpisodeItem): MediaEpisode => {
        return mapper.episode(item, request.seriesRef, request.seasonRef)
      })
      return mapper.page(items, startIndex, limit, visible.length, selected.length)
    })
  }

  getPerson(ref: MediaRef): Promise<MediaPerson> {
    return this.run('getPerson', async (): Promise<MediaPerson> => {
      this.requireOwned(ref, 'getPerson')
      const response = await this.request<FnMediaPersonResp>(
        `/v/api/v1/person/${encodeURIComponent(ref.id)}`, FeiniuVideoHttpMethod.Get)
      return this.mapper().person(response.data)
    })
  }

  setFavorite(ref: MediaRef, favorite: boolean): Promise<MediaFavoriteState> {
    return this.run('setFavorite', async (): Promise<MediaFavoriteState> => {
      this.requireOwned(ref, 'setFavorite')
      const payload: FnMediaGuidReq = { item_guid: ref.id }
      await this.request<FnMediaListResp<Object>>(
        '/v/api/v1/item/favorite',
        favorite ? FeiniuVideoHttpMethod.Put : FeiniuVideoHttpMethod.Delete,
        JSON.stringify(payload)
      )
      return this.mapper().favoriteState(favorite)
    })
  }

  setWatched(ref: MediaRef, watched: boolean): Promise<MediaWatchedState> {
    return this.run('setWatched', async (): Promise<MediaWatchedState> => {
      this.requireOwned(ref, 'setWatched')
      const payload: FnMediaGuidReq = { item_guid: ref.id }
      await this.request<FnMediaListResp<Object>>(
        '/v/api/v1/item/watched',
        watched ? FeiniuVideoHttpMethod.Post : FeiniuVideoHttpMethod.Delete,
        JSON.stringify(payload)
      )
      return this.mapper().watchedState(watched)
    })
  }

  private latestForLibrary(request: MediaLatestRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('latest', async (): Promise<MediaPage<MediaSummary>> => {
      this.requireOwned(request.libraryRef!, 'latest')
      if (this.onlyUnsupportedLive(request.kinds)) {
        return this.mapper().page([], this.startIndex(request.startIndex), this.limit(request.limit), 0, 0)
      }
      const startIndex = this.startIndex(request.startIndex)
      const limit = this.limit(request.limit)
      const payload = this.itemListPayload(
        request.libraryRef?.id || '',
        request.kinds,
        startIndex,
        limit,
        [MediaCatalogSortField.DateCreated],
        [MediaCatalogSortDirection.Descending]
      )
      const page = await this.itemPage('/v/api/v1/item/list', payload, startIndex, limit)
      const source = page.items
      await this.ensureGenres()
      const mapper = this.mapper()
      const visible = source.filter((item: FnMediaItem): boolean =>
        mapper.isVisibleItem(item) && mapper.matchesKinds(item, request.kinds))
      const enriched = await Promise.all(visible.map((item: FnMediaItem): Promise<FeiniuVideoEnrichedCatalogItem> =>
        this.enrichCatalogItem(item)))
      const mapped = enriched.map((entry: FeiniuVideoEnrichedCatalogItem): MediaSummary =>
        mapper.latest(entry.item, entry.context))
      const items = this.uniqueSummaries(mapped)
      return mapper.page(items, startIndex, limit, page.total, source.length)
    })
  }

  private latestAcrossLibraries(request: MediaLatestRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('latest', async (): Promise<MediaPage<MediaSummary>> => {
      if (this.onlyUnsupportedLive(request.kinds)) {
        return this.mapper().page([], this.startIndex(request.startIndex), this.limit(request.limit), 0, 0)
      }
      const startIndex = this.startIndex(request.startIndex)
      const limit = this.limit(request.limit)
      const libraryResponse = await this.request<FnMediaListResp<FnMediaLibraryItem[]>>(
        '/v/api/v1/mediadb/list', FeiniuVideoHttpMethod.Get)
      const libraryMapper = this.mapper()
      const libraries = (libraryResponse.data || []).filter((item: FnMediaLibraryItem): boolean => {
        return libraryMapper.isVisibleLibrary(item)
      })
      const pages = await Promise.all(libraries.map(
        async (library: FnMediaLibraryItem): Promise<FeiniuVideoLatestLibraryPage> => {
          try {
            const payload = this.itemListPayload(
              library.guid,
              request.kinds,
              startIndex,
              Math.max(limit, FEINIU_HOME_LIBRARY_LIMIT),
              [MediaCatalogSortField.DateCreated],
              [MediaCatalogSortDirection.Descending]
            )
            const page = await this.itemPage(
              '/v/api/v1/item/list', payload, startIndex, Math.max(limit, FEINIU_HOME_LIBRARY_LIMIT))
            return { items: page.items, total: page.total }
          } catch (error) {
            if (this.isAuthenticationError(error as Object)) throw error as Error
            console.warn(`[FeiniuVideoCatalog] latest library failed library=${library.guid} ` +
              `error=${(error as Error).message || 'unknown'}`)
            return { items: [], total: 0 }
          }
        }))
      let totalCount = 0
      pages.forEach((page: FeiniuVideoLatestLibraryPage): void => {
        totalCount += page.total
      })
      await this.ensureGenres()
      const source = this.interleave(pages, limit)
      const mapper = this.mapper()
      const visible = source.filter((item: FnMediaItem): boolean =>
        mapper.isVisibleItem(item) && mapper.matchesKinds(item, request.kinds))
      const enriched = await Promise.all(visible.map((item: FnMediaItem): Promise<FeiniuVideoEnrichedCatalogItem> =>
        this.enrichCatalogItem(item)))
      const mapped = enriched.map((entry: FeiniuVideoEnrichedCatalogItem): MediaSummary =>
        mapper.latest(entry.item, entry.context))
      const items = this.uniqueSummaries(mapped).slice(0, limit)
      return mapper.page(items, startIndex, limit, totalCount, source.length)
    })
  }

  private personItems(request: MediaListRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('getItems', async (): Promise<MediaPage<MediaSummary>> => {
      this.requireOwned(request.personRef!, 'getItems')
      const startIndex = this.startIndex(request.startIndex)
      const limit = this.limit(request.limit)
      const source: FnMediaPersonItemListItem[] = []
      let totalCount = 0
      for (let index = 0; index < FEINIU_PERSON_JOBS.length; index += 1) {
        const payload: FnMediaPersonItemListReq = {
          person_guid: request.personRef?.id || '',
          page: this.pageNumber(startIndex, limit),
          page_size: limit,
          job: FEINIU_PERSON_JOBS[index],
          sort_column: this.sortColumn(request.sortFields),
          sort_type: this.sortDirection(request.sortDirections)
        }
        const page = await this.personItemPage(payload, startIndex, limit)
        page.items.forEach((item: FnMediaPersonItemListItem): void => {
          source.push(item)
        })
        totalCount += page.total
      }
      await this.ensureGenres()
      const mapper = this.mapper()
      const mapped = source
        .map((item: FnMediaPersonItemListItem): MediaSummary => mapper.personWorkSummary(item))
        .filter((item: MediaSummary): boolean => item.ref.id.length > 0 &&
          (request.kinds.length === 0 || request.kinds.indexOf(item.ref.kind) >= 0))
      const items = this.uniqueSummaries(mapped).slice(0, limit)
      return mapper.page(items, startIndex, limit, totalCount, Math.min(limit, source.length))
    })
  }

  private async seasonItems(seriesId: string): Promise<FnMediaSeasonItem[]> {
    const response = await this.request<FnMediaListResp<FnMediaSeasonItem[]>>(
      `/v/api/v1/season/list/${encodeURIComponent(seriesId)}`, FeiniuVideoHttpMethod.Get)
    return (response.data || []).slice()
  }

  private async episodeItems(seasonId: string): Promise<FnMediaEpisodeItem[]> {
    const response = await this.request<FnMediaListResp<FnMediaEpisodeItem[]>>(
      `/v/api/v1/episode/list/${encodeURIComponent(seasonId)}`, FeiniuVideoHttpMethod.Get)
    return (response.data || []).slice().sort((left: FnMediaEpisodeItem, right: FnMediaEpisodeItem): number =>
      (left.episode_number || 0) - (right.episode_number || 0))
  }

  private async episodeItemsForSeries(seriesId: string): Promise<FnMediaEpisodeItem[]> {
    let seasons: FnMediaSeasonItem[] = []
    try {
      seasons = await this.seasonItems(seriesId)
    } catch (error) {
      if (this.isAuthenticationError(error as Object)) throw error as Error
      return await this.episodeItems(seriesId)
    }
    if (seasons.length === 0) {
      return await this.episodeItems(seriesId)
    }
    const episodeGroups = await Promise.all(seasons
      .map((season: FnMediaSeasonItem): string => season.guid.trim())
      .filter((seasonId: string): boolean => seasonId.length > 0)
      .map((seasonId: string): Promise<FnMediaEpisodeItem[]> => this.episodeItems(seasonId)))
    const result: FnMediaEpisodeItem[] = []
    episodeGroups.forEach((episodes: FnMediaEpisodeItem[]): void => {
      episodes.forEach((item: FnMediaEpisodeItem): void => {
        result.push(item)
      })
    })
    return result
  }

  private selectNextUpEpisode(episodes: FnMediaEpisodeItem[], playItems: FnMediaPlayItem[],
    request: MediaNextUpRequest): FnMediaEpisodeItem | undefined {
    if (episodes.length === 0) return undefined
    const playByGuid = new Map<string, FnMediaPlayItem>()
    const playOrder = new Map<string, number>()
    playItems.forEach((item: FnMediaPlayItem, index: number): void => {
      const guid = item.guid.trim()
      if (guid.length > 0 && !playByGuid.has(guid)) {
        playByGuid.set(guid, item)
        playOrder.set(guid, index)
      }
    })

    let resumableIndex = -1
    let resumableTimestamp = -1
    let resumableOrder = Number.MAX_VALUE
    let latestPlayedIndex = -1
    let latestPlayedTimestamp = -1
    let latestPlayedOrder = Number.MAX_VALUE
    let hasMatchedHistory = false
    let latestLocalEvidenceIndex = -1

    episodes.forEach((episode: FnMediaEpisodeItem, index: number): void => {
      const history = playByGuid.get(episode.guid.trim())
      const watched = this.episodeWatched(episode, history)
      const position = this.episodePosition(episode, history)
      const timestamp = Math.max(0, history?.watched_ts || 0)
      const order = playOrder.get(episode.guid.trim()) ?? Number.MAX_VALUE
      if (history) {
        hasMatchedHistory = true
        if (timestamp > latestPlayedTimestamp ||
          (timestamp === latestPlayedTimestamp && order < latestPlayedOrder)) {
          latestPlayedIndex = index
          latestPlayedTimestamp = timestamp
          latestPlayedOrder = order
        }
      } else if (watched || position > 0) {
        latestLocalEvidenceIndex = index
      }
      if (request.enableResumable && !watched && position > 0 &&
        (timestamp > resumableTimestamp ||
          (timestamp === resumableTimestamp && order < resumableOrder) ||
          (timestamp === resumableTimestamp && order === resumableOrder && index > resumableIndex))) {
        resumableIndex = index
        resumableTimestamp = timestamp
        resumableOrder = order
      }
    })

    if (resumableIndex >= 0) {
      return this.applyEpisodePlayState(episodes[resumableIndex], playByGuid.get(episodes[resumableIndex].guid.trim()))
    }
    const evidenceIndex = hasMatchedHistory ? latestPlayedIndex : latestLocalEvidenceIndex
    if (evidenceIndex >= 0) {
      for (let index = evidenceIndex + 1; index < episodes.length; index += 1) {
        const history = playByGuid.get(episodes[index].guid.trim())
        if (!this.episodeWatched(episodes[index], history)) {
          return this.applyEpisodePlayState(episodes[index], history)
        }
      }
      return request.enableRewatching
        ? this.applyEpisodePlayState(episodes[0], playByGuid.get(episodes[0].guid.trim())) : undefined
    }

    const firstUnwatchedIndex = episodes.findIndex((episode: FnMediaEpisodeItem): boolean =>
      !this.episodeWatched(episode, playByGuid.get(episode.guid.trim())))
    if (firstUnwatchedIndex < 0) {
      return request.enableRewatching
        ? this.applyEpisodePlayState(episodes[0], playByGuid.get(episodes[0].guid.trim())) : undefined
    }
    if (firstUnwatchedIndex === 0 && request.disableFirstEpisode) return undefined
    return this.applyEpisodePlayState(
      episodes[firstUnwatchedIndex], playByGuid.get(episodes[firstUnwatchedIndex].guid.trim()))
  }

  private episodeWatched(item: FnMediaEpisodeItem, history?: FnMediaPlayItem): boolean {
    const watched = Math.max(history?.watched || 0, item.watched || 0)
    const progress = history?.media_stream?.progress !== undefined
      ? history.media_stream.progress : item.media_stream?.progress
    return (watched || 0) > 0 || (progress || 0) >= 100
  }

  private episodePosition(item: FnMediaEpisodeItem, history?: FnMediaPlayItem): number {
    const historyPosition = Math.max(0, history?.ts || 0, history?.media_stream?.current_time || 0)
    const itemPosition = Math.max(0, item.ts || 0, item.media_stream?.current_time || 0)
    const progress = Math.max(0, history?.media_stream?.progress || item.media_stream?.progress || 0)
    return Math.max(historyPosition, itemPosition, progress)
  }

  private applyEpisodePlayState(item: FnMediaEpisodeItem, history?: FnMediaPlayItem): FnMediaEpisodeItem {
    if (!history) return item
    item.watched = history.watched !== undefined ? history.watched : item.watched
    item.watched_ts = history.watched_ts !== undefined ? history.watched_ts : item.watched_ts
    item.ts = history.ts !== undefined ? history.ts : item.ts
    item.duration = history.duration !== undefined ? history.duration : item.duration
    if (history.media_stream) {
      const stream = item.media_stream || {}
      stream.progress = history.media_stream.progress !== undefined
        ? history.media_stream.progress : stream.progress
      stream.current_time = history.media_stream.current_time !== undefined
        ? history.media_stream.current_time : stream.current_time
      item.media_stream = stream
    }
    return item
  }

  private itemListPayload(ancestorGuid: string, kinds: MediaRefKind[], startIndex: number,
    limit: number, sortFields: MediaCatalogSortField[], sortDirections: MediaCatalogSortDirection[],
    watched?: boolean): FnMediaItemListReq {
    const tags: FnMediaItemListTags = {
      type: this.itemTypes(kinds)
    }
    if (watched !== undefined) {
      tags.watched = watched ? '1' : '0'
    }
    const payload: FnMediaItemListReq = {
      exclude_grouped_video: 1,
      sort_type: this.sortDirection(sortDirections),
      sort_column: this.sortColumn(sortFields),
      page_size: limit,
      page: this.pageNumber(startIndex, limit),
      tags: tags
    }
    if (ancestorGuid.trim().length > 0) {
      payload.ancestor_guid = ancestorGuid.trim()
    }
    return payload
  }

  private async itemPage(path: string, payload: FnMediaItemListReq,
    startIndex: number, limit: number): Promise<FeiniuVideoItemPage> {
    const pageSize = this.limit(limit)
    const pageNumber = this.pageNumber(startIndex, pageSize)
    const offset = this.startIndex(startIndex) % pageSize
    payload.page_size = pageSize
    payload.page = pageNumber
    const firstResponse = await this.request<FnMediaItemListResp>(
      path, FeiniuVideoHttpMethod.Post, JSON.stringify(payload))
    const firstItems = firstResponse.data?.list || []
    const total = firstResponse.data?.total || firstItems.length
    const selected: FnMediaItem[] = []
    for (let index = offset; index < firstItems.length && selected.length < pageSize; index += 1) {
      selected.push(firstItems[index])
    }
    if (selected.length < pageSize && startIndex + selected.length < total) {
      payload.page = pageNumber + 1
      const nextResponse = await this.request<FnMediaItemListResp>(
        path, FeiniuVideoHttpMethod.Post, JSON.stringify(payload))
      const nextItems = nextResponse.data?.list || []
      for (let index = 0; index < nextItems.length && selected.length < pageSize; index += 1) {
        selected.push(nextItems[index])
      }
    }
    return { items: selected, total: total }
  }

  private async optionalRecommendationPage(path: string,
    payload: FnMediaItemListReq): Promise<FnMediaItem[]> {
    try {
      const response = await this.request<FnMediaItemListResp>(
        path, FeiniuVideoHttpMethod.Post, JSON.stringify(payload))
      return response.data?.list || []
    } catch (error) {
      if (this.isAuthenticationError(error as Object)) {
        throw error as Error
      }
      return []
    }
  }

  private async optionalRecommendationPlayList(): Promise<FnMediaPlayItem[]> {
    try {
      const response = await this.request<FnMediaListResp<FnMediaPlayItem[]>>(
        '/v/api/v1/play/list', FeiniuVideoHttpMethod.Get)
      return response.data || []
    } catch (error) {
      if (this.isAuthenticationError(error as Object)) {
        throw error as Error
      }
      return []
    }
  }

  private async recommendationSignals(playItems: FnMediaPlayItem[],
    knownItems: FnMediaItem[]): Promise<FeiniuVideoRecommendationSignals> {
    const unfinishedBases: FnMediaItem[] = []
    const completed: FnMediaItem[] = []
    const seen = new Set<string>()
    const sortedPlayItems = playItems.slice().sort((left: FnMediaPlayItem, right: FnMediaPlayItem): number => {
      return this.recommendationTimestamp(right) - this.recommendationTimestamp(left)
    })
    const signalItems = await Promise.all(sortedPlayItems.map(
      (playItem: FnMediaPlayItem): Promise<FnMediaItem> => this.recommendationSignalItem(playItem)))
    sortedPlayItems.forEach((playItem: FnMediaPlayItem, index: number): void => {
      const item = signalItems[index]
      if (item.guid.length === 0 || seen.has(item.guid)) return
      const progress = this.recommendationProgress(playItem)
      if ((playItem.watched || 0) > 0 || progress >= 95) {
        completed.push(item)
        seen.add(item.guid)
      } else if (progress > 0) {
        unfinishedBases.push(item)
        seen.add(item.guid)
      }
    })

    const knownById = new Map<string, FnMediaItem>()
    knownItems.forEach((item: FnMediaItem): void => {
      if (item.guid.trim().length > 0) knownById.set(item.guid, item)
    })
    const unfinished = await Promise.all(unfinishedBases.slice(0, FEINIU_RECOMMENDATION_SIGNAL_DETAIL_LIMIT)
      .map((item: FnMediaItem): Promise<FnMediaItem> => {
        const known = knownById.get(item.guid)
        return known ? Promise.resolve(known) : this.recommendationDetail(item)
      }))
    return new FeiniuVideoRecommendationSignals(unfinished, completed)
  }

  private async recommendationSignalItem(item: FnMediaPlayItem): Promise<FnMediaItem> {
    const type = `${item.type || ''}`.trim().toLowerCase()
    const isEpisode = type === 'episode'
    if (isEpisode) {
      const enriched = await this.enrichCatalogItem(this.playCatalogItem(item), MediaRefKind.Episode)
      const seriesId = `${enriched.context.seriesId || ''}`.trim()
      if (seriesId.length > 0) {
        const series = await this.optionalItemDetail(seriesId)
        if (series) return series
      }
    }
    return this.playCatalogItem(item)
  }

  private recommendationProgress(item: FnMediaPlayItem): number {
    const value = Number(item.media_stream?.progress || 0)
    return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0
  }

  private recommendationTimestamp(item: FnMediaPlayItem): number {
    return Math.max(0, item.watched_ts || 0, item.ts || 0)
  }

  private async recommendationDetail(item: FnMediaItem): Promise<FnMediaItem> {
    try {
      const detail = await this.itemDetail(item.guid)
      this.applyItemFallbacks(detail, item)
      return detail
    } catch (error) {
      if (this.isAuthenticationError(error as Object)) {
        throw error as Error
      }
      console.warn(`[FeiniuVideoCatalog] recommendation detail failed item=${item.guid} ` +
        `error=${(error as Error).message || 'unknown'}`)
      return item
    }
  }

  private recommendationSeed(now: number): string {
    const date = new Date(now)
    return `${this.session.accountScope}|${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`
  }

  private isAuthenticationError(error: Object): boolean {
    return error instanceof FeiniuVideoAuthenticationError ||
      (error instanceof FeiniuVideoRequestError && error.kind === FeiniuVideoRequestFailureKind.Authentication)
  }

  private async personItemPage(payload: FnMediaPersonItemListReq,
    startIndex: number, limit: number): Promise<FeiniuVideoPersonItemPage> {
    const pageSize = this.limit(limit)
    const pageNumber = this.pageNumber(startIndex, pageSize)
    const offset = this.startIndex(startIndex) % pageSize
    payload.page_size = pageSize
    payload.page = pageNumber
    const firstResponse = await this.request<FnMediaPersonItemListResp>(
      '/v/api/v1/person/item/list', FeiniuVideoHttpMethod.Post, JSON.stringify(payload))
    const firstItems = firstResponse.data?.list || []
    const total = firstResponse.data?.total || firstItems.length
    const selected: FnMediaPersonItemListItem[] = []
    for (let index = offset; index < firstItems.length && selected.length < pageSize; index += 1) {
      selected.push(firstItems[index])
    }
    if (selected.length < pageSize && startIndex + selected.length < total) {
      payload.page = pageNumber + 1
      const nextResponse = await this.request<FnMediaPersonItemListResp>(
        '/v/api/v1/person/item/list', FeiniuVideoHttpMethod.Post, JSON.stringify(payload))
      const nextItems = nextResponse.data?.list || []
      for (let index = 0; index < nextItems.length && selected.length < pageSize; index += 1) {
        selected.push(nextItems[index])
      }
    }
    return { items: selected, total: total }
  }

  private itemTypes(kinds: MediaRefKind[]): string[] {
    const result: string[] = []
    const source = kinds.length > 0 ? kinds : [
      MediaRefKind.Movie,
      MediaRefKind.Series,
      MediaRefKind.Library,
      MediaRefKind.Video
    ]
    source.forEach((kind: MediaRefKind): void => {
      const value = this.itemType(kind)
      if (value.length > 0 && result.indexOf(value) < 0 &&
        (value !== 'LiveChannel' || this.session.capabilities.liveTv)) {
        result.push(value)
      }
    })
    return result
  }

  private itemType(kind: MediaRefKind): string {
    if (kind === MediaRefKind.Movie) return 'Movie'
    if (kind === MediaRefKind.Series) return 'TV'
    if (kind === MediaRefKind.Season) return 'Season'
    if (kind === MediaRefKind.Episode) return 'Episode'
    if (kind === MediaRefKind.Library) return 'Directory'
    if (kind === MediaRefKind.Video) return 'Video'
    if (kind === MediaRefKind.LiveChannel) return 'LiveChannel'
    return ''
  }

  private sortColumn(fields: MediaCatalogSortField[]): string {
    const field = fields.length > 0 ? fields[0] : MediaCatalogSortField.DateCreated
    if (field === MediaCatalogSortField.Name || field === MediaCatalogSortField.SeriesSortName) {
      return 'sort_title'
    }
    if (field === MediaCatalogSortField.PremiereDate || field === MediaCatalogSortField.ProductionYear) {
      return 'release_date'
    }
    if (field === MediaCatalogSortField.CommunityRating || field === MediaCatalogSortField.CriticRating) {
      return 'vote_average'
    }
    if (field === MediaCatalogSortField.DatePlayed || field === MediaCatalogSortField.PlayCount) {
      return 'watched_ts'
    }
    if (field === MediaCatalogSortField.Runtime) {
      return 'runtime'
    }
    if (field === MediaCatalogSortField.Default) {
      return 'create_time'
    }
    return 'create_time'
  }

  private sortDirection(directions: MediaCatalogSortDirection[]): string {
    return directions.length > 0 && directions[0] === MediaCatalogSortDirection.Ascending ? 'ASC' : 'DESC'
  }

  private async ensureGenres(): Promise<void> {
    if (this.genresLoaded) {
      return
    }
    this.genresLoaded = true
    try {
      const response = await this.request<FnMediaListResp<FnMediaGenreItem[]>>(
        '/v/api/v1/tag/genres',
        FeiniuVideoHttpMethod.Get,
        '',
        [new FeiniuVideoQueryParameter('lan', 'zh')]
      )
      const items = response.data || []
      items.forEach((item: FnMediaGenreItem): void => {
        const id = item.id || 0
        const value = `${item.value || ''}`.trim()
        if (id > 0 && value.length > 0) {
          this.genreNames.set(id, value)
        }
      })
    } catch (_) {
      // Genre names are optional metadata. Unmapped numeric IDs must never leak into shared UI.
    }
  }

  private mapper(): FeiniuVideoCatalogMapper {
    return new FeiniuVideoCatalogMapper(this.session, this.service.imageAccess(), this.genreNames)
  }

  private request<T>(path: string, method: FeiniuVideoHttpMethod, bodyText: string = '',
    parameters: FeiniuVideoQueryParameter[] = []): Promise<T> {
    return this.service.request<T>(new FeiniuVideoApiRequest(
      path,
      method,
      bodyText,
      parameters,
      true
    ))
  }

  private interleave(pages: FeiniuVideoLatestLibraryPage[], limit: number): FnMediaItem[] {
    const result: FnMediaItem[] = []
    let itemIndex = 0
    let appended = true
    while (result.length < limit && appended) {
      appended = false
      for (let pageIndex = 0; pageIndex < pages.length && result.length < limit; pageIndex += 1) {
        const item = pages[pageIndex].items[itemIndex]
        if (item) {
          result.push(item)
          appended = true
        }
      }
      itemIndex += 1
    }
    return result
  }

  private uniqueSummaries(items: MediaSummary[]): MediaSummary[] {
    const result: MediaSummary[] = []
    const identities: Set<string> = new Set<string>()
    items.forEach((item: MediaSummary): void => {
      if (!identities.has(item.ref.identity)) {
        identities.add(item.ref.identity)
        result.push(item)
      }
    })
    return result
  }

  private onlyUnsupportedLive(kinds: MediaRefKind[]): boolean {
    return !this.session.capabilities.liveTv && kinds.length > 0 &&
      kinds.every((kind: MediaRefKind): boolean => kind === MediaRefKind.LiveChannel)
  }

  private requireOwnedOptional(ref: MediaRef | undefined, operation: string): void {
    if (ref) {
      this.requireOwned(ref, operation)
    }
  }

  private requireOwned(ref: MediaRef, operation: string): void {
    if (ref.id.length === 0 || !ref.belongsTo(this.session)) {
      throw new MediaCatalogError({
        code: MediaCatalogErrorCode.InvalidRequest,
        message: 'Media reference does not belong to the Feiniu Video account',
        accountScope: this.session.accountScope,
        operation: operation
      })
    }
  }

  private startIndex(value: number): number {
    return Math.max(0, Math.floor(value))
  }

  private limit(value: number): number {
    return Math.max(1, Math.floor(value))
  }

  private pageNumber(startIndex: number, limit: number): number {
    return Math.floor(this.startIndex(startIndex) / this.limit(limit)) + 1
  }

  private async run<T>(operation: string, request: () => Promise<T>): Promise<T> {
    try {
      return await request()
    } catch (error) {
      throw FeiniuVideoCatalogErrorMapper.from(error as Object, this.session, operation)
    }
  }
}
