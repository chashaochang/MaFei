import { AxiosInstance, AxiosResponse } from '@ohos/axios'
import {
  getItemsApi,
  getLibraryApi,
  getPlaystateApi,
  getTvShowsApi,
  getUserLibraryApi,
  getUserViewsApi
} from 'sdk'
import { Api } from 'sdk/src/main/ets/api'
import { ItemsApiGetItemsRequest, ItemsApiGetResumeItemsRequest } from
  'sdk/src/main/ets/generated-client/api/items-api'
import {
  TvShowsApiGetEpisodesRequest,
  TvShowsApiGetNextUpRequest,
  TvShowsApiGetSeasonsRequest
} from 'sdk/src/main/ets/generated-client/api/tv-shows-api'
import { UserLibraryApiGetLatestMediaRequest } from
  'sdk/src/main/ets/generated-client/api/user-library-api'
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  CollectionType,
  ImageType,
  ItemCounts,
  ItemFields,
  ItemSortBy,
  RecommendationDto,
  SortOrder,
  UserItemDataDto
} from 'sdk/src/main/ets/generated-client/models'
import { ClientInfo } from 'sdk/src/main/ets/models/client-info'
import { DeviceInfo } from 'sdk/src/main/ets/models/device-info'
import { ApiClient } from '../../network/ApiClient'
import { MediaProviderKind } from '../MediaProviderKind'
import { MediaProviderSession } from '../MediaProviderSession'
import { MediaRef, MediaRefKind } from '../MediaRef'
import { MediaCatalogError, MediaCatalogErrorCode } from '../catalog/MediaCatalogError'
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
} from '../catalog/MediaCatalogModels'
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
} from '../catalog/MediaCatalogProvider'
import { JellyfinCatalogMapper } from './JellyfinCatalogMapper'

const HOME_RECENT_LIBRARY_LIMIT: number = 16

interface JellyfinLatestLibraryPage {
  readonly items: BaseItemDto[]
}

interface JellyfinHttpResponse {
  readonly status: number
}

interface JellyfinRequestError extends Error {
  readonly isAxiosError?: boolean
  readonly response?: JellyfinHttpResponse
}

function normalizeBasePath(value: string): string {
  let normalized = value.trim()
  while (normalized.endsWith('/')) {
    normalized = normalized.slice(0, -1)
  }
  return normalized
}

export class JellyfinCatalogRequestSession {
  readonly owner: MediaProviderSession
  readonly accountScope: string
  readonly basePath: string
  readonly userId: string
  readonly authorizationHeader: string
  readonly api: Api

  constructor(owner: MediaProviderSession, api: Api, authorizationHeader: string = '') {
    this.owner = owner
    this.accountScope = owner.accountScope
    this.basePath = normalizeBasePath(owner.baseUrl)
    this.userId = owner.userId
    this.authorizationHeader = authorizationHeader
    this.api = api
  }
}

export interface JellyfinCatalogService {
  createSession(owner: MediaProviderSession): JellyfinCatalogRequestSession
  getCounts(session: JellyfinCatalogRequestSession): Promise<ItemCounts>
  getUserViews(session: JellyfinCatalogRequestSession): Promise<BaseItemDtoQueryResult>
  getRecommendations(session: JellyfinCatalogRequestSession,
    request: MediaRecommendationsRequest): Promise<RecommendationDto[]>
  getItems(session: JellyfinCatalogRequestSession,
    request: ItemsApiGetItemsRequest): Promise<BaseItemDtoQueryResult>
  getResumeItems(session: JellyfinCatalogRequestSession,
    request: ItemsApiGetResumeItemsRequest): Promise<BaseItemDtoQueryResult>
  getLatestMedia(session: JellyfinCatalogRequestSession,
    request: UserLibraryApiGetLatestMediaRequest): Promise<BaseItemDto[]>
  getNextUp(session: JellyfinCatalogRequestSession,
    request: TvShowsApiGetNextUpRequest): Promise<BaseItemDtoQueryResult>
  getItem(session: JellyfinCatalogRequestSession, itemId: string): Promise<BaseItemDto>
  getSeasons(session: JellyfinCatalogRequestSession,
    request: TvShowsApiGetSeasonsRequest): Promise<BaseItemDtoQueryResult>
  getEpisodes(session: JellyfinCatalogRequestSession,
    request: TvShowsApiGetEpisodesRequest): Promise<BaseItemDtoQueryResult>
  setFavorite(session: JellyfinCatalogRequestSession, itemId: string,
    favorite: boolean): Promise<UserItemDataDto>
  setWatched(session: JellyfinCatalogRequestSession, itemId: string,
    watched: boolean): Promise<UserItemDataDto>
}

export class JellyfinCatalogApiService implements JellyfinCatalogService {
  createSession(owner: MediaProviderSession): JellyfinCatalogRequestSession {
    if (owner.provider !== MediaProviderKind.Jellyfin) {
      throw new MediaCatalogError({
        code: MediaCatalogErrorCode.Unsupported,
        message: 'Jellyfin catalog cannot serve a different provider',
        accountScope: owner.accountScope,
        operation: 'createSession'
      })
    }
    if (!ApiClient.isInitialized()) {
      throw new MediaCatalogError({
        code: MediaCatalogErrorCode.AuthenticationExpired,
        message: 'Jellyfin requires an initialized account',
        accountScope: owner.accountScope,
        operation: 'createSession'
      })
    }
    const activeApi = ApiClient.Instance()
    const activeBasePath = normalizeBasePath(String(activeApi.basePath || ''))
    const activeUserId = String(activeApi.userId || '')
    if (activeBasePath !== normalizeBasePath(owner.baseUrl) || activeUserId !== owner.userId) {
      throw new MediaCatalogError({
        code: MediaCatalogErrorCode.AuthenticationExpired,
        message: 'Jellyfin account changed before the catalog session was created',
        accountScope: owner.accountScope,
        operation: 'createSession'
      })
    }
    const clientInfo: ClientInfo = {
      name: String(activeApi.clientInfo.name || ''),
      version: String(activeApi.clientInfo.version || '')
    }
    const deviceInfo: DeviceInfo = {
      id: String(activeApi.deviceInfo.id || ''),
      name: String(activeApi.deviceInfo.name || '')
    }
    const snapshotApi = new Api(
      activeBasePath,
      clientInfo,
      deviceInfo,
      String(activeApi.accessToken || ''),
      activeApi.axiosInstance
    )
    snapshotApi.userId = owner.userId
    return new JellyfinCatalogRequestSession(owner, snapshotApi, snapshotApi.authorizationHeader)
  }

  async getCounts(session: JellyfinCatalogRequestSession): Promise<ItemCounts> {
    const response: AxiosResponse<ItemCounts> = await getLibraryApi(session.api).getItemCounts({
      userId: session.userId
    })
    return response.data
  }

  async getUserViews(session: JellyfinCatalogRequestSession): Promise<BaseItemDtoQueryResult> {
    const response: AxiosResponse<BaseItemDtoQueryResult> = await getUserViewsApi(session.api).getUserViews({
      userId: session.userId
    })
    return response.data
  }

  async getRecommendations(session: JellyfinCatalogRequestSession,
    request: MediaRecommendationsRequest): Promise<RecommendationDto[]> {
    const axiosInstance = session.api.axiosInstance as AxiosInstance
    const requestUrl = `${session.basePath}/Movies/Recommendations?userId=${encodeURIComponent(session.userId)}` +
      `&categoryLimit=${request.categoryLimit}&itemLimit=${request.itemLimit}` +
      '&fields=Overview%2CPrimaryImageAspectRatio'
    const response: AxiosResponse<RecommendationDto[]> = await axiosInstance.get<RecommendationDto[]>(requestUrl, {
      headers: { Authorization: session.authorizationHeader }
    })
    return response.data || []
  }

  async getItems(session: JellyfinCatalogRequestSession,
    request: ItemsApiGetItemsRequest): Promise<BaseItemDtoQueryResult> {
    const response: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(session.api).getItems(request)
    return response.data
  }

  async getResumeItems(session: JellyfinCatalogRequestSession,
    request: ItemsApiGetResumeItemsRequest): Promise<BaseItemDtoQueryResult> {
    const response: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(session.api).getResumeItems(request)
    return response.data
  }

  async getLatestMedia(session: JellyfinCatalogRequestSession,
    request: UserLibraryApiGetLatestMediaRequest): Promise<BaseItemDto[]> {
    const response: AxiosResponse<BaseItemDto[]> = await getUserLibraryApi(session.api).getLatestMedia(request)
    return response.data || []
  }

  async getNextUp(session: JellyfinCatalogRequestSession,
    request: TvShowsApiGetNextUpRequest): Promise<BaseItemDtoQueryResult> {
    const response: AxiosResponse<BaseItemDtoQueryResult> = await getTvShowsApi(session.api).getNextUp(request)
    return response.data
  }

  async getItem(session: JellyfinCatalogRequestSession, itemId: string): Promise<BaseItemDto> {
    const response: AxiosResponse<BaseItemDto> = await getUserLibraryApi(session.api).getItem({
      userId: session.userId,
      itemId: itemId
    })
    return response.data
  }

  async getSeasons(session: JellyfinCatalogRequestSession,
    request: TvShowsApiGetSeasonsRequest): Promise<BaseItemDtoQueryResult> {
    const response: AxiosResponse<BaseItemDtoQueryResult> = await getTvShowsApi(session.api).getSeasons(request)
    return response.data
  }

  async getEpisodes(session: JellyfinCatalogRequestSession,
    request: TvShowsApiGetEpisodesRequest): Promise<BaseItemDtoQueryResult> {
    const response: AxiosResponse<BaseItemDtoQueryResult> = await getTvShowsApi(session.api).getEpisodes(request)
    return response.data
  }

  async setFavorite(session: JellyfinCatalogRequestSession, itemId: string,
    favorite: boolean): Promise<UserItemDataDto> {
    let response: AxiosResponse<UserItemDataDto>
    if (favorite) {
      response = await getUserLibraryApi(session.api).markFavoriteItem({
        userId: session.userId,
        itemId: itemId
      })
    } else {
      response = await getUserLibraryApi(session.api).unmarkFavoriteItem({
        userId: session.userId,
        itemId: itemId
      })
    }
    return response.data
  }

  async setWatched(session: JellyfinCatalogRequestSession, itemId: string,
    watched: boolean): Promise<UserItemDataDto> {
    let response: AxiosResponse<UserItemDataDto>
    if (watched) {
      response = await getPlaystateApi(session.api).markPlayedItem({
        userId: session.userId,
        itemId: itemId
      })
    } else {
      response = await getPlaystateApi(session.api).markUnplayedItem({
        userId: session.userId,
        itemId: itemId
      })
    }
    return response.data
  }
}

export class JellyfinCatalogErrorMapper {
  static from(error: Object, session: MediaProviderSession, operation: string): MediaCatalogError {
    if (error instanceof MediaCatalogError && error.accountScope === session.accountScope) {
      return error
    }
    const requestError = error as JellyfinRequestError
    const status = requestError.response?.status
    if (status === 401) {
      return JellyfinCatalogErrorMapper.error(MediaCatalogErrorCode.AuthenticationExpired,
        error, session, operation, 'Jellyfin authentication expired')
    }
    if (status === 403) {
      return JellyfinCatalogErrorMapper.error(MediaCatalogErrorCode.PermissionDenied,
        error, session, operation, 'Jellyfin access is not allowed')
    }
    if (status === 404) {
      return JellyfinCatalogErrorMapper.error(MediaCatalogErrorCode.NotFound,
        error, session, operation, 'Jellyfin content was not found')
    }
    if (status === 400 || status === 422) {
      return JellyfinCatalogErrorMapper.error(MediaCatalogErrorCode.InvalidRequest,
        error, session, operation, 'Jellyfin request is invalid')
    }
    if (status === 405 || status === 501) {
      return JellyfinCatalogErrorMapper.error(MediaCatalogErrorCode.Unsupported,
        error, session, operation, 'Jellyfin operation is not supported')
    }
    if (status === 426) {
      return JellyfinCatalogErrorMapper.error(MediaCatalogErrorCode.IncompatibleService,
        error, session, operation, 'Jellyfin server version is incompatible')
    }
    if (status === 408 || status === 429 || (status !== undefined && status >= 500)) {
      return JellyfinCatalogErrorMapper.error(MediaCatalogErrorCode.ServiceUnavailable,
        error, session, operation, 'Jellyfin service is unavailable')
    }
    if (requestError.isAxiosError === true && requestError.response === undefined) {
      return JellyfinCatalogErrorMapper.error(MediaCatalogErrorCode.Offline,
        error, session, operation, 'Jellyfin network request failed')
    }
    if (requestError.name === 'RequiredError' || requestError.name === 'TypeError') {
      return JellyfinCatalogErrorMapper.error(MediaCatalogErrorCode.InvalidRequest,
        error, session, operation, 'Jellyfin request is invalid')
    }
    return JellyfinCatalogErrorMapper.error(MediaCatalogErrorCode.RequestFailed,
      error, session, operation, 'Jellyfin request failed')
  }

  private static error(code: MediaCatalogErrorCode, source: Object, session: MediaProviderSession,
    operation: string, fallback: string): MediaCatalogError {
    const value = source as Error
    return new MediaCatalogError({
      code: code,
      message: value.message && value.message.length > 0 ? value.message : fallback,
      accountScope: session.accountScope,
      operation: operation
    })
  }
}

export class JellyfinCatalogProvider implements MediaCatalogProvider {
  readonly session: MediaProviderSession
  private readonly service: JellyfinCatalogService
  private readonly requestSession: JellyfinCatalogRequestSession
  private readonly mapper: JellyfinCatalogMapper

  constructor(session: MediaProviderSession, service: JellyfinCatalogService = new JellyfinCatalogApiService()) {
    if (session.provider !== MediaProviderKind.Jellyfin) {
      throw new MediaCatalogError({
        code: MediaCatalogErrorCode.Unsupported,
        message: 'Jellyfin catalog cannot serve a different provider',
        accountScope: session.accountScope,
        operation: 'constructor'
      })
    }
    this.session = session
    this.service = service
    this.requestSession = service.createSession(session)
    if (this.requestSession.accountScope !== session.accountScope) {
      throw new MediaCatalogError({
        code: MediaCatalogErrorCode.InvalidRequest,
        message: 'Jellyfin request session does not match the provider account',
        accountScope: session.accountScope,
        operation: 'constructor'
      })
    }
    this.mapper = new JellyfinCatalogMapper(
      session, this.requestSession.basePath, this.requestSession.authorizationHeader)
  }

  getCounts(): Promise<MediaCatalogCounts> {
    return this.run('getCounts', async (): Promise<MediaCatalogCounts> => {
      const counts = await this.service.getCounts(this.requestSession)
      const movieCount = Math.max(0, counts.MovieCount || 0)
      const seriesCount = Math.max(0, counts.SeriesCount || 0)
      const episodeCount = Math.max(0, counts.EpisodeCount || 0)
      return {
        totalCount: movieCount + seriesCount,
        movieCount: movieCount,
        seriesCount: seriesCount,
        episodeCount: episodeCount
      }
    })
  }

  getLibraries(): Promise<MediaLibrary[]> {
    return this.run('getLibraries', async (): Promise<MediaLibrary[]> => {
      const response = await this.service.getUserViews(this.requestSession)
      const views = (response.Items || []).filter((item: BaseItemDto): boolean => {
        return (item.Id || '').length > 0 && this.mapper.isVisibleLibrary(item)
      })
      return Promise.all(views.map(async (item: BaseItemDto): Promise<MediaLibrary> => {
        if (item.CollectionType === CollectionType.Livetv) {
          return this.mapper.library(item)
        }
        const items = await this.service.getItems(this.requestSession, {
          userId: this.requestSession.userId,
          parentId: item.Id
        })
        return this.mapper.library(item, items.TotalRecordCount || 0)
      }))
    })
  }

  recommendations(request: MediaRecommendationsRequest): Promise<MediaSummary[]> {
    return this.run('recommendations', async (): Promise<MediaSummary[]> => {
      const resultLimit = Math.max(0, Math.floor(request.resultLimit))
      if (resultLimit === 0) {
        return []
      }
      const normalizedRequest: MediaRecommendationsRequest = {
        categoryLimit: Math.max(1, Math.floor(request.categoryLimit)),
        itemLimit: Math.max(1, Math.floor(request.itemLimit)),
        resultLimit: resultLimit
      }
      const groups = await this.service.getRecommendations(this.requestSession, normalizedRequest)
      const summaries: MediaSummary[] = []
      const seenIds: Set<string> = new Set<string>()
      for (let groupIndex = 0; groupIndex < groups.length && summaries.length < resultLimit; groupIndex++) {
        const items = groups[groupIndex].Items || []
        for (let itemIndex = 0; itemIndex < items.length && summaries.length < resultLimit; itemIndex++) {
          const summary = this.mapper.recommendation(items[itemIndex])
          if (summary && !seenIds.has(summary.ref.identity)) {
            seenIds.add(summary.ref.identity)
            summaries.push(summary)
          }
        }
      }
      return summaries
    })
  }

  resume(request: MediaResumeRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('resume', async (): Promise<MediaPage<MediaSummary>> => {
      const limit = this.limit(request.limit)
      const result = await this.service.getResumeItems(this.requestSession, {
        limit: limit,
        fields: [ItemFields.PrimaryImageAspectRatio],
        imageTypeLimit: 1,
        enableImages: true,
        enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb],
        enableTotalRecordCount: false,
        mediaTypes: ['Video'],
        userId: this.requestSession.userId
      })
      const items = (result.Items || []).map((item: BaseItemDto): MediaSummary => this.mapper.resume(item))
      const sourceItemCount = (result.Items || []).length
      return this.mapper.page(items, result.StartIndex || 0, limit,
        result.TotalRecordCount ?? items.length, sourceItemCount)
    })
  }

  nextUp(request: MediaNextUpRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('nextUp', async (): Promise<MediaPage<MediaSummary>> => {
      this.requireOwnedOptional(request.seriesRef, 'nextUp')
      const limit = this.limit(request.limit)
      let serviceRequest: TvShowsApiGetNextUpRequest
      if (request.seriesRef) {
        serviceRequest = {
          userId: this.requestSession.userId,
          seriesId: request.seriesRef.id,
          fields: [ItemFields.MediaSourceCount]
        }
      } else {
        serviceRequest = {
          limit: limit,
          fields: [ItemFields.PrimaryImageAspectRatio, ItemFields.DateCreated,
            ItemFields.Path, ItemFields.MediaSourceCount],
          userId: this.requestSession.userId,
          imageTypeLimit: 1,
          enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Banner, ImageType.Thumb],
          enableTotalRecordCount: false,
          disableFirstEpisode: request.disableFirstEpisode,
          nextUpDateCutoff: request.dateCutoff,
          enableResumable: request.enableResumable,
          enableRewatching: request.enableRewatching
        }
      }
      const result = await this.service.getNextUp(this.requestSession, serviceRequest)
      const startIndex = result.StartIndex || 0
      const items = (result.Items || []).map((item: BaseItemDto): MediaSummary => this.mapper.nextUp(item))
      const sourceItemCount = (result.Items || []).length
      return this.mapper.page(items, startIndex, limit,
        result.TotalRecordCount ?? items.length, sourceItemCount)
    })
  }

  latest(request: MediaLatestRequest): Promise<MediaPage<MediaSummary>> {
    if (request.libraryRef) {
      return this.latestForLibrary(request)
    }
    return this.latestAcrossLibraries(request)
  }

  getItems(request: MediaListRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('getItems', async (): Promise<MediaPage<MediaSummary>> => {
      this.requireOwnedOptional(request.libraryRef, 'getItems')
      this.requireOwnedOptional(request.parentRef, 'getItems')
      this.requireOwnedOptional(request.personRef, 'getItems')
      const startIndex = this.startIndex(request.startIndex)
      const limit = this.limit(request.limit)
      const mixedLibraryRequest = request.kinds.length === 0 && !request.recursive
      const result = await this.service.getItems(this.requestSession, {
        userId: this.requestSession.userId,
        parentId: request.parentRef?.id || request.libraryRef?.id,
        personIds: request.personRef ? [request.personRef.id] : undefined,
        recursive: request.recursive,
        startIndex: startIndex,
        limit: limit,
        includeItemTypes: this.itemKinds(request.kinds),
        fields: request.personRef ?
          [ItemFields.ParentId, ItemFields.PrimaryImageAspectRatio] :
          (mixedLibraryRequest ?
            [ItemFields.PrimaryImageAspectRatio, ItemFields.SortName, ItemFields.CanDelete,
              ItemFields.Path, ItemFields.ChildCount, ItemFields.MediaSourceCount] :
            [ItemFields.PrimaryImageAspectRatio, ItemFields.CanDelete, ItemFields.Path,
              ItemFields.MediaSourceCount, ItemFields.MediaStreams]),
        imageTypeLimit: mixedLibraryRequest ? undefined : 1,
        enableImages: mixedLibraryRequest ? undefined : true,
        enableImageTypes: mixedLibraryRequest ? undefined :
          [ImageType.Primary, ImageType.Backdrop, ImageType.Banner, ImageType.Thumb],
        enableTotalRecordCount: true,
        sortBy: this.sortFields(request.sortFields),
        sortOrder: this.sortDirections(request.sortDirections),
        collapseBoxSetItems: request.collapseBoxSetItems,
        isFavorite: request.favoriteOnly ? true : undefined,
        isPlayed: request.watched
      })
      const items = (result.Items || []).map((item: BaseItemDto): MediaSummary => this.mapper.summary(item))
      const sourceItemCount = (result.Items || []).length
      return this.mapper.page(items, result.StartIndex ?? startIndex, limit,
        result.TotalRecordCount, sourceItemCount, false)
    })
  }

  search(request: MediaSearchRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('search', async (): Promise<MediaPage<MediaSummary>> => {
      this.requireOwnedOptional(request.libraryRef, 'search')
      const query = request.query.trim()
      const startIndex = this.startIndex(request.startIndex)
      const limit = this.limit(request.limit)
      if (query.length === 0) {
        return this.mapper.page([], startIndex, limit, 0)
      }
      const result = await this.service.getItems(this.requestSession, {
        userId: this.requestSession.userId,
        parentId: request.libraryRef?.id,
        searchTerm: query,
        fields: [ItemFields.PrimaryImageAspectRatio, ItemFields.CanDelete, ItemFields.MediaSourceCount],
        includeItemTypes: this.itemKinds(request.kinds),
        startIndex: startIndex,
        limit: limit,
        imageTypeLimit: 1,
        enableTotalRecordCount: false,
        recursive: request.recursive
      })
      const items = (result.Items || []).map((item: BaseItemDto): MediaSummary => this.mapper.summary(item))
      const sourceItemCount = (result.Items || []).length
      return this.mapper.page(items, result.StartIndex ?? startIndex, limit,
        result.TotalRecordCount ?? startIndex + sourceItemCount, sourceItemCount)
    })
  }

  getDetail(ref: MediaRef): Promise<MediaDetail> {
    return this.run('getDetail', async (): Promise<MediaDetail> => {
      this.requireOwned(ref, 'getDetail')
      return this.mapper.detail(await this.service.getItem(this.requestSession, ref.id))
    })
  }

  getSeasons(seriesRef: MediaRef): Promise<MediaSeason[]> {
    return this.run('getSeasons', async (): Promise<MediaSeason[]> => {
      this.requireOwned(seriesRef, 'getSeasons')
      const result = await this.service.getSeasons(this.requestSession, {
        userId: this.requestSession.userId,
        seriesId: seriesRef.id,
        fields: [ItemFields.ItemCounts, ItemFields.PrimaryImageAspectRatio, ItemFields.CanDelete,
          ItemFields.MediaSourceCount, ItemFields.ChildCount]
      })
      return (result.Items || []).map((item: BaseItemDto): MediaSeason => {
        return this.mapper.season(item, seriesRef)
      })
    })
  }

  getEpisodes(request: MediaEpisodeRequest): Promise<MediaPage<MediaEpisode>> {
    return this.run('getEpisodes', async (): Promise<MediaPage<MediaEpisode>> => {
      this.requireOwned(request.seriesRef, 'getEpisodes')
      this.requireOwnedOptional(request.seasonRef, 'getEpisodes')
      const startIndex = this.startIndex(request.startIndex)
      const limit = this.limit(request.limit)
      const playbackQueue = request.profile === MediaEpisodeQueryProfile.PlaybackQueue
      const result = await this.service.getEpisodes(this.requestSession, {
        userId: this.requestSession.userId,
        seriesId: request.seriesRef.id,
        seasonId: request.seasonRef?.id,
        fields: playbackQueue ? [ItemFields.Chapters, ItemFields.Trickplay] :
          [ItemFields.ItemCounts, ItemFields.PrimaryImageAspectRatio, ItemFields.CanDelete,
            ItemFields.MediaSourceCount, ItemFields.Overview],
        isMissing: false,
        startIndex: playbackQueue ? undefined : startIndex,
        limit: playbackQueue ? undefined : limit,
        enableImages: playbackQueue ? undefined : true,
        imageTypeLimit: playbackQueue ? undefined : 1,
        enableImageTypes: playbackQueue ? undefined :
          [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb],
        enableUserData: playbackQueue ? undefined : true
      })
      const items = (result.Items || []).map((item: BaseItemDto): MediaEpisode => {
        return this.mapper.episode(item, request.seriesRef, request.seasonRef)
      })
      const sourceItemCount = (result.Items || []).length
      return this.mapper.page(items, result.StartIndex ?? startIndex, limit,
        result.TotalRecordCount, sourceItemCount)
    })
  }

  getPerson(ref: MediaRef): Promise<MediaPerson> {
    return this.run('getPerson', async (): Promise<MediaPerson> => {
      this.requireOwned(ref, 'getPerson')
      return this.mapper.person(await this.service.getItem(this.requestSession, ref.id))
    })
  }

  setFavorite(ref: MediaRef, favorite: boolean): Promise<MediaFavoriteState> {
    return this.run('setFavorite', async (): Promise<MediaFavoriteState> => {
      this.requireOwned(ref, 'setFavorite')
      const result = await this.service.setFavorite(this.requestSession, ref.id, favorite)
      return this.mapper.favoriteState(result, favorite)
    })
  }

  setWatched(ref: MediaRef, watched: boolean): Promise<MediaWatchedState> {
    return this.run('setWatched', async (): Promise<MediaWatchedState> => {
      this.requireOwned(ref, 'setWatched')
      const result = await this.service.setWatched(this.requestSession, ref.id, watched)
      return this.mapper.watchedState(result, watched)
    })
  }

  private latestForLibrary(request: MediaLatestRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('latest', async (): Promise<MediaPage<MediaSummary>> => {
      this.requireOwned(request.libraryRef!, 'latest')
      const startIndex = this.startIndex(request.startIndex)
      const limit = this.limit(request.limit)
      const result = await this.service.getItems(this.requestSession, {
        userId: this.requestSession.userId,
        parentId: request.libraryRef?.id,
        recursive: request.recursive,
        startIndex: startIndex,
        limit: limit,
        includeItemTypes: this.itemKinds(request.kinds),
        sortBy: [ItemSortBy.DateCreated],
        sortOrder: [SortOrder.Descending],
        fields: [ItemFields.PrimaryImageAspectRatio, ItemFields.DateCreated,
          ItemFields.Path, ItemFields.MediaSourceCount, ItemFields.MediaStreams],
        imageTypeLimit: 1,
        enableImages: true,
        enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb]
      })
      const mapped = (result.Items || []).map((item: BaseItemDto): MediaSummary => this.mapper.latest(item))
      const items = this.uniqueSummaries(mapped)
      const sourceItemCount = (result.Items || []).length
      const knownTotalCount = (result.TotalRecordCount || 0) > 0 ? result.TotalRecordCount : undefined
      return this.mapper.page(items, result.StartIndex ?? startIndex, limit,
        knownTotalCount, sourceItemCount)
    })
  }

  private latestAcrossLibraries(request: MediaLatestRequest): Promise<MediaPage<MediaSummary>> {
    return this.run('latest', async (): Promise<MediaPage<MediaSummary>> => {
      const limit = this.limit(request.limit)
      const response = await this.service.getUserViews(this.requestSession)
      const views = (response.Items || []).filter((item: BaseItemDto): boolean => {
        return (item.Id || '').length > 0 && this.mapper.isLatestLibrary(item)
      })
      const pages: JellyfinLatestLibraryPage[] = await Promise.all(
        views.map(async (view: BaseItemDto): Promise<JellyfinLatestLibraryPage> => {
          try {
            return {
              items: await this.service.getLatestMedia(this.requestSession, {
                userId: this.requestSession.userId,
                parentId: view.Id,
                limit: HOME_RECENT_LIBRARY_LIMIT,
                fields: [ItemFields.PrimaryImageAspectRatio, ItemFields.Path, ItemFields.MediaStreams],
                imageTypeLimit: 1,
                enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb]
              })
            }
          } catch (_error) {
            return { items: [] }
          }
        })
      )
      const interleaved = this.interleave(pages, limit)
      const mapped = interleaved.map((item: BaseItemDto): MediaSummary => this.mapper.latest(item))
      const items = this.uniqueSummaries(mapped).slice(0, limit)
      return this.mapper.page(items, 0, limit, interleaved.length, interleaved.length)
    })
  }

  private interleave(pages: JellyfinLatestLibraryPage[], limit: number): BaseItemDto[] {
    const result: BaseItemDto[] = []
    let itemIndex = 0
    let appended = true
    while (result.length < limit && appended) {
      appended = false
      for (let pageIndex = 0; pageIndex < pages.length && result.length < limit; pageIndex++) {
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

  private itemKinds(kinds: MediaRefKind[]): BaseItemKind[] | undefined {
    const result: BaseItemKind[] = []
    kinds.forEach((kind: MediaRefKind): void => {
      const itemKind = this.itemKind(kind)
      if (itemKind && !result.includes(itemKind)) {
        result.push(itemKind)
      }
    })
    return result.length > 0 ? result : undefined
  }

  private itemKind(kind: MediaRefKind): BaseItemKind | undefined {
    if (kind === MediaRefKind.Movie) {
      return BaseItemKind.Movie
    }
    if (kind === MediaRefKind.Video) {
      return BaseItemKind.Video
    }
    if (kind === MediaRefKind.Series) {
      return BaseItemKind.Series
    }
    if (kind === MediaRefKind.Season) {
      return BaseItemKind.Season
    }
    if (kind === MediaRefKind.Episode) {
      return BaseItemKind.Episode
    }
    if (kind === MediaRefKind.Person) {
      return BaseItemKind.Person
    }
    if (kind === MediaRefKind.LiveChannel) {
      return BaseItemKind.LiveTvChannel
    }
    return undefined
  }

  private sortFields(fields: MediaCatalogSortField[]): ItemSortBy[] | undefined {
    const result = fields.map((field: MediaCatalogSortField): ItemSortBy => this.sortField(field))
    return result.length > 0 ? result : undefined
  }

  private sortField(field: MediaCatalogSortField): ItemSortBy {
    if (field === MediaCatalogSortField.Name) return ItemSortBy.Name
    if (field === MediaCatalogSortField.DateCreated) return ItemSortBy.DateCreated
    if (field === MediaCatalogSortField.PremiereDate) return ItemSortBy.PremiereDate
    if (field === MediaCatalogSortField.DatePlayed) return ItemSortBy.DatePlayed
    if (field === MediaCatalogSortField.CommunityRating) return ItemSortBy.CommunityRating
    if (field === MediaCatalogSortField.CriticRating) return ItemSortBy.CriticRating
    if (field === MediaCatalogSortField.ProductionYear) return ItemSortBy.ProductionYear
    if (field === MediaCatalogSortField.PlayCount) return ItemSortBy.PlayCount
    if (field === MediaCatalogSortField.Runtime) return ItemSortBy.Runtime
    if (field === MediaCatalogSortField.OfficialRating) return ItemSortBy.OfficialRating
    if (field === MediaCatalogSortField.Random) return ItemSortBy.Random
    if (field === MediaCatalogSortField.Album) return ItemSortBy.Album
    if (field === MediaCatalogSortField.Artist) return ItemSortBy.Artist
    if (field === MediaCatalogSortField.AlbumArtist) return ItemSortBy.AlbumArtist
    if (field === MediaCatalogSortField.AiredEpisodeOrder) return ItemSortBy.AiredEpisodeOrder
    if (field === MediaCatalogSortField.SeriesSortName) return ItemSortBy.SeriesSortName
    if (field === MediaCatalogSortField.Studio) return ItemSortBy.Studio
    if (field === MediaCatalogSortField.IsFavoriteOrLiked) return ItemSortBy.IsFavoriteOrLiked
    if (field === MediaCatalogSortField.DateLastContentAdded) return ItemSortBy.DateLastContentAdded
    if (field === MediaCatalogSortField.IsUnplayed) return ItemSortBy.IsUnplayed
    if (field === MediaCatalogSortField.IsPlayed) return ItemSortBy.IsPlayed
    return ItemSortBy.Default
  }

  private sortDirections(directions: MediaCatalogSortDirection[]): SortOrder[] | undefined {
    const result = directions.map((direction: MediaCatalogSortDirection): SortOrder => {
      return direction === MediaCatalogSortDirection.Descending ? SortOrder.Descending : SortOrder.Ascending
    })
    return result.length > 0 ? result : undefined
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
        message: 'Media reference does not belong to the Jellyfin account',
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

  private async run<T>(operation: string, request: () => Promise<T>): Promise<T> {
    try {
      return await request()
    } catch (error) {
      throw JellyfinCatalogErrorMapper.from(error as Object, this.session, operation)
    }
  }
}
