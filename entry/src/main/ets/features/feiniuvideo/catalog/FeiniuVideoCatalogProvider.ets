import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaConnectionRoute, MediaProviderSession } from '../../../media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../../../media/MediaRef'
import { MediaCatalogError, MediaCatalogErrorCode } from '../../../media/catalog/MediaCatalogError'
import {
  MediaCatalogCounts,
  MediaDetail,
  MediaEpisode,
  MediaFavoriteState,
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
  FeiniuVideoRequestFailureKind
} from '../network/FeiniuVideoApiModels'
import {
  FnMediaCountResp,
  FnMediaEpisodeItem,
  FnMediaGenreItem,
  FnMediaGuidReq,
  FnMediaItem,
  FnMediaItemDetailResp,
  FnMediaItemListReq,
  FnMediaItemListResp,
  FnMediaItemListTags,
  FnMediaItemStream,
  FnMediaLibraryItem,
  FnMediaListResp,
  FnMediaPersonItemListItem,
  FnMediaPersonItemListReq,
  FnMediaPersonItemListResp,
  FnMediaPersonListItem,
  FnMediaPersonListResp,
  FnMediaPersonResp,
  FnMediaPlayItem,
  FnMediaSeasonItem
} from './FeiniuVideoDtos'
import {
  FeiniuVideoCatalogImageAccess,
  FeiniuVideoCatalogMapper
} from './FeiniuVideoCatalogMapper'
import { FeiniuVideoRecommendationPlanner } from './FeiniuVideoRecommendationPlanner'

const FEINIU_HOME_LIBRARY_LIMIT: number = 16
const FEINIU_PERSON_JOBS: string[] = ['Actor', 'Director', 'Screenplay']
const FEINIU_RECOMMENDATION_CACHE_TTL_MS: number = 6 * 60 * 60 * 1000
const FEINIU_RECOMMENDATION_MIN_CANDIDATES: number = 24
const FEINIU_RECOMMENDATION_MAX_CANDIDATES: number = 60
const FEINIU_RECOMMENDATION_DETAIL_LIMIT: number = 6
const FEINIU_RECOMMENDATION_SIGNAL_DETAIL_LIMIT: number = 8

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
      const selected = source.slice(0, limit)
      const items = selected.map((item: FnMediaPlayItem): MediaSummary => mapper.resume(item))
      return mapper.page(items, 0, limit, source.length, selected.length)
    })
  }

  nextUp(request: MediaNextUpRequest): Promise<MediaPage<MediaSummary>> {
    this.requireOwnedOptional(request.seriesRef, 'nextUp')
    // There is no proven global next-up contract in Feiniu Video. Hide the section instead of inventing data.
    return Promise.resolve(this.mapper().page([], 0, this.limit(request.limit), 0, 0))
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
      const detailResponse = await this.request<FnMediaListResp<FnMediaItemDetailResp>>(
        `/v/api/v1/item/${encodeURIComponent(ref.id)}`, FeiniuVideoHttpMethod.Get)
      const detail = detailResponse.data
      if (!detail || `${detail.guid || ''}`.trim().length === 0) {
        throw new FeiniuVideoRequestError(
          FeiniuVideoRequestFailureKind.InvalidResponse,
          'Feiniu Video detail response is empty'
        )
      }
      const peopleResponse = await this.request<FnMediaPersonListResp>(
        `/v/api/v1/person/list/${encodeURIComponent(ref.id)}`, FeiniuVideoHttpMethod.Post)
      const mapperBeforeSeasons = this.mapper()
      let seasons: MediaSeason[] = []
      if (mapperBeforeSeasons.refKind(detail.type) === MediaRefKind.Series) {
        const seasonItems = await this.seasonItems(ref.id)
        const seriesRef = new MediaRef(this.session.provider, this.session.accountScope, ref.id, MediaRefKind.Series)
        const mapper = this.mapper()
        seasons = seasonItems.map((item: FnMediaSeasonItem): MediaSeason => mapper.season(item, seriesRef))
      }
      await this.ensureGenres()
      return this.mapper().detail(detail, peopleResponse.data?.list || [], seasons)
    })
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
      const source: FnMediaEpisodeItem[] = []
      if (request.seasonRef) {
        const episodes = await this.episodeItems(request.seasonRef.id)
        episodes.forEach((item: FnMediaEpisodeItem): void => {
          source.push(item)
        })
      } else {
        let seasons: FnMediaSeasonItem[] = []
        let usedSeriesEpisodeFallback = false
        try {
          seasons = await this.seasonItems(request.seriesRef.id)
        } catch (_error) {
          usedSeriesEpisodeFallback = true
          const episodes = await this.episodeItems(request.seriesRef.id)
          episodes.forEach((item: FnMediaEpisodeItem): void => {
            source.push(item)
          })
        }
        if (!usedSeriesEpisodeFallback && seasons.length === 0) {
          const episodes = await this.episodeItems(request.seriesRef.id)
          episodes.forEach((item: FnMediaEpisodeItem): void => {
            source.push(item)
          })
        } else if (seasons.length > 0) {
          for (let index = 0; index < seasons.length; index += 1) {
            const seasonId = seasons[index].guid.trim()
            if (seasonId.length > 0) {
              const episodes = await this.episodeItems(seasonId)
              episodes.forEach((item: FnMediaEpisodeItem): void => {
                source.push(item)
              })
            }
          }
        }
      }
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
      const mapped = source
        .filter((item: FnMediaItem): boolean => mapper.isVisibleItem(item) && mapper.matchesKinds(item, request.kinds))
        .map((item: FnMediaItem): MediaSummary => mapper.latest(item))
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
      const pages: FeiniuVideoLatestLibraryPage[] = []
      let totalCount = 0
      for (let index = 0; index < libraries.length; index += 1) {
        const library = libraries[index]
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
          pages.push({ items: page.items, total: page.total })
          totalCount += page.total
        } catch (error) {
          if (error instanceof FeiniuVideoAuthenticationError) {
            throw error
          }
          pages.push({ items: [], total: 0 })
        }
      }
      await this.ensureGenres()
      const source = this.interleave(pages, limit)
      const mapper = this.mapper()
      const mapped = source
        .filter((item: FnMediaItem): boolean => mapper.isVisibleItem(item) && mapper.matchesKinds(item, request.kinds))
        .map((item: FnMediaItem): MediaSummary => mapper.latest(item))
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
    return response.data || []
  }

  private async episodeItems(seasonId: string): Promise<FnMediaEpisodeItem[]> {
    const response = await this.request<FnMediaListResp<FnMediaEpisodeItem[]>>(
      `/v/api/v1/episode/list/${encodeURIComponent(seasonId)}`, FeiniuVideoHttpMethod.Get)
    return response.data || []
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
    sortedPlayItems.forEach((playItem: FnMediaPlayItem): void => {
      const item = this.recommendationSignalItem(playItem)
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

  private recommendationSignalItem(item: FnMediaPlayItem): FnMediaItem {
    const type = `${item.type || ''}`.trim().toLowerCase()
    const isEpisode = type === 'episode'
    const guid = `${isEpisode ? (item.ancestor_guid || item.parent_guid || item.guid) : item.guid}`.trim()
    const result = {} as FnMediaItem
    result.guid = guid
    result.title = `${isEpisode ?
      (item.tv_title || item.ancestor_name || item.parent_title || item.title) : item.title}`.trim()
    result.type = isEpisode ? 'TV' : item.type
    result.poster = item.poster
    result.watched = item.watched
    result.watched_ts = item.watched_ts
    result.is_favorite = item.is_favorite
    if (item.media_stream) {
      const stream = {} as FnMediaItemStream
      stream.progress = item.media_stream.progress
      stream.current_time = item.media_stream.current_time
      result.media_stream = stream
    }
    return result
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
      const response = await this.request<FnMediaListResp<FnMediaItemDetailResp>>(
        `/v/api/v1/item/${encodeURIComponent(item.guid)}`, FeiniuVideoHttpMethod.Get)
      const detail = response.data
      if (!detail || `${detail.guid || ''}`.trim().length === 0) {
        return item
      }
      detail.poster = detail.poster || item.poster
      detail.vote_average = detail.vote_average || item.vote_average
      detail.release_date = detail.release_date || item.release_date
      detail.first_air_date = detail.first_air_date || item.first_air_date
      detail.genres = detail.genres || item.genres
      detail.watched = detail.watched === undefined ? item.watched : detail.watched
      detail.is_favorite = detail.is_favorite === undefined ? item.is_favorite : detail.is_favorite
      return detail
    } catch (error) {
      if (this.isAuthenticationError(error as Object)) {
        throw error as Error
      }
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
