import { describe, expect, it } from '@ohos/hypium'
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
  ItemCounts,
  ItemFields,
  ItemSortBy,
  RecommendationDto,
  SortOrder,
  UserItemDataDto
} from 'sdk/src/main/ets/generated-client/models'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'
import { MediaCatalogError, MediaCatalogErrorCode } from '../main/ets/media/catalog/MediaCatalogError'
import { MediaLibraryKind } from '../main/ets/media/catalog/MediaCatalogModels'
import {
  MediaCatalogSortDirection,
  MediaCatalogSortField,
  MediaEpisodeQueryProfile,
  MediaRecommendationsRequest
} from '../main/ets/media/catalog/MediaCatalogProvider'
import {
  JellyfinCatalogErrorMapper,
  JellyfinCatalogProvider,
  JellyfinCatalogRequestSession,
  JellyfinCatalogService
} from '../main/ets/media/jellyfin/JellyfinCatalogProvider'

class TestCatalogHttpResponse {
  status: number = 0
}

class TestCatalogHttpError {
  name: string = 'Error'
  message: string = 'request failed'
  isAxiosError?: boolean
  response?: TestCatalogHttpResponse
}

class FakeJellyfinCatalogService implements JellyfinCatalogService {
  recommendationsResult: RecommendationDto[] = []
  viewsResult: BaseItemDtoQueryResult = { Items: [] }
  itemsResult: BaseItemDtoQueryResult = { Items: [], StartIndex: 0, TotalRecordCount: 0 }
  resumeResult: BaseItemDtoQueryResult = { Items: [], StartIndex: 0, TotalRecordCount: 0 }
  nextUpResult: BaseItemDtoQueryResult = { Items: [], StartIndex: 0, TotalRecordCount: 0 }
  seasonsResult: BaseItemDtoQueryResult = { Items: [] }
  episodesResult: BaseItemDtoQueryResult = { Items: [], StartIndex: 0, TotalRecordCount: 0 }
  itemResult: BaseItemDto = {}
  countsResult: ItemCounts = {}
  favoriteResult: UserItemDataDto = {}
  watchedResult: UserItemDataDto = {}
  itemsByParent: Map<string, BaseItemDtoQueryResult> = new Map<string, BaseItemDtoQueryResult>()
  latestItems: Map<string, BaseItemDto[]> = new Map<string, BaseItemDto[]>()
  latestFailureParentId: string = ''

  lastRecommendationsRequest?: MediaRecommendationsRequest
  lastItemsRequest?: ItemsApiGetItemsRequest
  itemRequests: ItemsApiGetItemsRequest[] = []
  lastResumeRequest?: ItemsApiGetResumeItemsRequest
  nextUpRequests: TvShowsApiGetNextUpRequest[] = []
  episodeRequests: TvShowsApiGetEpisodesRequest[] = []
  getItemCount: number = 0

  createSession(owner: MediaProviderSession): JellyfinCatalogRequestSession {
    return new JellyfinCatalogRequestSession(owner, {} as Api, 'MediaBrowser Token="test-token"')
  }

  async getCounts(_session: JellyfinCatalogRequestSession): Promise<ItemCounts> {
    return this.countsResult
  }

  async getUserViews(_session: JellyfinCatalogRequestSession): Promise<BaseItemDtoQueryResult> {
    return this.viewsResult
  }

  async getRecommendations(_session: JellyfinCatalogRequestSession,
    request: MediaRecommendationsRequest): Promise<RecommendationDto[]> {
    this.lastRecommendationsRequest = request
    return this.recommendationsResult
  }

  async getItems(_session: JellyfinCatalogRequestSession,
    request: ItemsApiGetItemsRequest): Promise<BaseItemDtoQueryResult> {
    this.lastItemsRequest = request
    this.itemRequests.push(request)
    return this.itemsByParent.get(request.parentId || '') || this.itemsResult
  }

  async getResumeItems(_session: JellyfinCatalogRequestSession,
    request: ItemsApiGetResumeItemsRequest): Promise<BaseItemDtoQueryResult> {
    this.lastResumeRequest = request
    return this.resumeResult
  }

  async getLatestMedia(_session: JellyfinCatalogRequestSession,
    request: UserLibraryApiGetLatestMediaRequest): Promise<BaseItemDto[]> {
    const parentId = request.parentId || ''
    if (parentId === this.latestFailureParentId) {
      throw new Error('library unavailable')
    }
    return this.latestItems.get(parentId) || []
  }

  async getNextUp(_session: JellyfinCatalogRequestSession,
    request: TvShowsApiGetNextUpRequest): Promise<BaseItemDtoQueryResult> {
    this.nextUpRequests.push(request)
    return this.nextUpResult
  }

  async getItem(_session: JellyfinCatalogRequestSession, _itemId: string): Promise<BaseItemDto> {
    this.getItemCount += 1
    return this.itemResult
  }

  async getSeasons(_session: JellyfinCatalogRequestSession,
    _request: TvShowsApiGetSeasonsRequest): Promise<BaseItemDtoQueryResult> {
    return this.seasonsResult
  }

  async getEpisodes(_session: JellyfinCatalogRequestSession,
    request: TvShowsApiGetEpisodesRequest): Promise<BaseItemDtoQueryResult> {
    this.episodeRequests.push(request)
    return this.episodesResult
  }

  async setFavorite(_session: JellyfinCatalogRequestSession, _itemId: string,
    _favorite: boolean): Promise<UserItemDataDto> {
    return this.favoriteResult
  }

  async setWatched(_session: JellyfinCatalogRequestSession, _itemId: string,
    _watched: boolean): Promise<UserItemDataDto> {
    return this.watchedResult
  }
}

function session(providerAccountId: string = 'jellyfin-account-1'): MediaProviderSession {
  return new MediaProviderSession({
    provider: MediaProviderKind.Jellyfin,
    providerAccountId: providerAccountId,
    userId: 'user-1',
    originalAddress: 'jellyfin.example',
    baseUrl: 'https://jellyfin.example/'
  })
}

function ref(owner: MediaProviderSession, id: string, kind: MediaRefKind): MediaRef {
  return new MediaRef(owner.provider, owner.accountScope, id, kind)
}

function item(id: string, kind: BaseItemKind, name: string = id): BaseItemDto {
  return { Id: id, Type: kind, Name: name }
}

function httpError(status?: number, offline: boolean = false, name: string = 'Error'): TestCatalogHttpError {
  const error = new TestCatalogHttpError()
  error.name = name
  error.isAxiosError = offline
  if (status !== undefined) {
    error.response = new TestCatalogHttpResponse()
    error.response.status = status
  }
  return error
}

export default function jellyfinCatalogProviderTest() {
  describe('JellyfinCatalogProvider', () => {
    it('filtersAndDeduplicatesRecommendationsInServerGroupOrder', 0, async () => {
      const service = new FakeJellyfinCatalogService()
      service.recommendationsResult = [
        { Items: [item('movie-a', BaseItemKind.Movie), item('series-a', BaseItemKind.Series),
          item('movie-a', BaseItemKind.Movie)] },
        { Items: [item('movie-b', BaseItemKind.Movie), item('movie-c', BaseItemKind.Movie)] }
      ]
      const provider = new JellyfinCatalogProvider(session(), service)

      const result = await provider.recommendations({ categoryLimit: 4, itemLimit: 4, resultLimit: 2 })

      expect(result.length).assertEqual(2)
      expect(result[0].ref.id).assertEqual('movie-a')
      expect(result[1].ref.id).assertEqual('movie-b')
      expect(service.lastRecommendationsRequest?.categoryLimit).assertEqual(4)
      expect(service.lastRecommendationsRequest?.itemLimit).assertEqual(4)
    })

    it('keepsAllLibraryKindsAndLoadsRealCountsForNonLiveViews', 0, async () => {
      const service = new FakeJellyfinCatalogService()
      service.viewsResult = {
        Items: [
          { Id: 'movies', Name: 'Movies', CollectionType: CollectionType.Movies },
          { Id: 'music', Name: 'Music', CollectionType: CollectionType.Music },
          { Id: 'photos', Name: 'Photos', CollectionType: CollectionType.Photos },
          { Id: 'live', Name: 'Live TV', CollectionType: CollectionType.Livetv, ChildCount: 7 }
        ]
      }
      service.itemsByParent.set('movies', { TotalRecordCount: 30 })
      service.itemsByParent.set('music', { TotalRecordCount: 40 })
      service.itemsByParent.set('photos', { TotalRecordCount: 50 })
      const provider = new JellyfinCatalogProvider(session(), service)

      const libraries = await provider.getLibraries()

      expect(libraries.length).assertEqual(4)
      expect(libraries[0].kind).assertEqual(MediaLibraryKind.Movies)
      expect(libraries[0].itemCount).assertEqual(30)
      expect(libraries[1].kind).assertEqual(MediaLibraryKind.Music)
      expect(libraries[1].itemCount).assertEqual(40)
      expect(libraries[2].kind).assertEqual(MediaLibraryKind.Photos)
      expect(libraries[2].itemCount).assertEqual(50)
      expect(libraries[3].kind).assertEqual(MediaLibraryKind.LiveTv)
      expect(libraries[3].itemCount).assertEqual(7)
      expect(service.itemRequests.length).assertEqual(3)
    })

    it('countsAllVideoAsMoviesPlusSeriesInsteadOfAllServerItems', 0, async () => {
      const service = new FakeJellyfinCatalogService()
      service.countsResult = {
        ItemCount: 999,
        MovieCount: 12,
        SeriesCount: 8,
        EpisodeCount: 120
      }
      const provider = new JellyfinCatalogProvider(session(), service)

      const counts = await provider.getCounts()

      expect(counts.totalCount).assertEqual(20)
      expect(counts.movieCount).assertEqual(12)
      expect(counts.seriesCount).assertEqual(8)
      expect(counts.episodeCount).assertEqual(120)
    })

    it('interleavesRecentLibrariesToleratesOneFailureAndDeduplicatesSeries', 0, async () => {
      const service = new FakeJellyfinCatalogService()
      service.viewsResult = {
        Items: [
          { Id: 'movies', Name: 'Movies', CollectionType: CollectionType.Movies },
          { Id: 'failed', Name: 'Failed', CollectionType: CollectionType.Movies },
          { Id: 'shows', Name: 'Shows', CollectionType: CollectionType.Tvshows },
          { Id: 'music', Name: 'Music', CollectionType: CollectionType.Music }
        ]
      }
      service.latestFailureParentId = 'failed'
      service.latestItems.set('movies', [
        { Id: 'episode-1', Type: BaseItemKind.Episode, SeriesId: 'series-1', SeriesName: 'Series' },
        item('movie-1', BaseItemKind.Movie)
      ])
      service.latestItems.set('shows', [
        { Id: 'episode-2', Type: BaseItemKind.Episode, SeriesId: 'series-1', SeriesName: 'Series' },
        item('movie-2', BaseItemKind.Movie)
      ])
      const provider = new JellyfinCatalogProvider(session(), service)

      const page = await provider.latest({ kinds: [], startIndex: 0, limit: 4, recursive: true })

      expect(page.items.length).assertEqual(3)
      expect(page.items[0].ref.id).assertEqual('series-1')
      expect(page.items[0].ref.kind).assertEqual(MediaRefKind.Series)
      expect(page.items[1].ref.id).assertEqual('movie-1')
      expect(page.items[2].ref.id).assertEqual('movie-2')
      expect(page.nextStartIndex).assertEqual(4)
      expect(page.hasMore).assertFalse()
      expect(service.latestItems.has('music')).assertFalse()
    })

    it('advancesLibraryLatestPagingByRawServerItemsBeforeSeriesDeduplication', 0, async () => {
      const owner = session()
      const service = new FakeJellyfinCatalogService()
      service.itemsResult = {
        Items: [
          { Id: 'episode-1', Type: BaseItemKind.Episode, SeriesId: 'series-1', SeriesName: 'Series' },
          { Id: 'episode-2', Type: BaseItemKind.Episode, SeriesId: 'series-1', SeriesName: 'Series' }
        ],
        StartIndex: 7,
        TotalRecordCount: 0
      }
      const provider = new JellyfinCatalogProvider(owner, service)

      const page = await provider.latest({
        libraryRef: ref(owner, 'library-1', MediaRefKind.Library),
        kinds: [MediaRefKind.Episode],
        startIndex: 7,
        limit: 2,
        recursive: true
      })

      expect(page.items.length).assertEqual(1)
      expect(page.nextStartIndex).assertEqual(9)
      expect(page.hasMore).assertTrue()
      expect(service.lastItemsRequest?.sortBy?.[0]).assertEqual(ItemSortBy.DateCreated)
      expect(service.lastItemsRequest?.sortOrder?.[0]).assertEqual(SortOrder.Descending)
    })

    it('keepsResumeMediaTypeOnlyAndUsesSeriesSpecificNextUpShape', 0, async () => {
      const owner = session()
      const service = new FakeJellyfinCatalogService()
      const provider = new JellyfinCatalogProvider(owner, service)

      await provider.resume({ kinds: [MediaRefKind.Movie], limit: 12 })
      await provider.nextUp({
        seriesRef: ref(owner, 'series-1', MediaRefKind.Series),
        limit: 24,
        disableFirstEpisode: false,
        enableResumable: false,
        enableRewatching: false
      })
      await provider.nextUp({
        limit: 24,
        dateCutoff: '2025-01-01T00:00:00.000Z',
        disableFirstEpisode: false,
        enableResumable: false,
        enableRewatching: false
      })

      expect(service.lastResumeRequest?.mediaTypes?.[0]).assertEqual('Video')
      expect(service.lastResumeRequest?.includeItemTypes === undefined).assertTrue()
      expect(service.nextUpRequests[0].seriesId).assertEqual('series-1')
      expect(service.nextUpRequests[0].fields?.length).assertEqual(1)
      expect(service.nextUpRequests[0].fields?.[0]).assertEqual(ItemFields.MediaSourceCount)
      expect(service.nextUpRequests[0].limit === undefined).assertTrue()
      expect(service.nextUpRequests[0].imageTypeLimit === undefined).assertTrue()
      expect(service.nextUpRequests[1].limit).assertEqual(24)
      expect(service.nextUpRequests[1].nextUpDateCutoff).assertEqual('2025-01-01T00:00:00.000Z')
    })

    it('mapsSearchKindsAndSeparatesDisplayFromPlaybackEpisodeQueries', 0, async () => {
      const owner = session()
      const service = new FakeJellyfinCatalogService()
      const provider = new JellyfinCatalogProvider(owner, service)
      const seriesRef = ref(owner, 'series-1', MediaRefKind.Series)
      const seasonRef = ref(owner, 'season-1', MediaRefKind.Season)

      await provider.search({
        query: ' query ',
        kinds: [MediaRefKind.Episode, MediaRefKind.Movie, MediaRefKind.Video, MediaRefKind.Series],
        startIndex: 0,
        limit: 800,
        recursive: true
      })
      await provider.getEpisodes({
        seriesRef: seriesRef,
        seasonRef: seasonRef,
        startIndex: 5,
        limit: 20,
        profile: MediaEpisodeQueryProfile.Display
      })
      await provider.getEpisodes({
        seriesRef: seriesRef,
        seasonRef: seasonRef,
        startIndex: 5,
        limit: 20,
        profile: MediaEpisodeQueryProfile.PlaybackQueue
      })

      expect(service.lastItemsRequest?.searchTerm).assertEqual('query')
      expect(service.lastItemsRequest?.includeItemTypes?.join(','))
        .assertEqual('Episode,Movie,Video,Series')
      expect(service.episodeRequests[0].startIndex).assertEqual(5)
      expect(service.episodeRequests[0].limit).assertEqual(20)
      expect(service.episodeRequests[0].fields?.includes(ItemFields.Overview)).assertTrue()
      expect(service.episodeRequests[1].startIndex === undefined).assertTrue()
      expect(service.episodeRequests[1].limit === undefined).assertTrue()
      expect(service.episodeRequests[1].fields?.includes(ItemFields.Chapters)).assertTrue()
      expect(service.episodeRequests[1].fields?.includes(ItemFields.Trickplay)).assertTrue()
    })

    it('keepsMixedLibraryRowsLightweightAndTypedListsImageReady', 0, async () => {
      const owner = session()
      const service = new FakeJellyfinCatalogService()
      const provider = new JellyfinCatalogProvider(owner, service)

      await provider.getItems({
        libraryRef: ref(owner, 'library-1', MediaRefKind.Library),
        kinds: [],
        startIndex: 0,
        limit: 100,
        sortFields: [MediaCatalogSortField.Name],
        sortDirections: [MediaCatalogSortDirection.Ascending],
        recursive: false,
        collapseBoxSetItems: false,
        favoriteOnly: false
      })
      await provider.getItems({
        kinds: [MediaRefKind.Movie],
        startIndex: 0,
        limit: 100,
        sortFields: [MediaCatalogSortField.Name],
        sortDirections: [MediaCatalogSortDirection.Ascending],
        recursive: true,
        collapseBoxSetItems: false,
        favoriteOnly: false
      })

      expect(service.itemRequests[0].fields?.includes(ItemFields.SortName)).assertTrue()
      expect(service.itemRequests[0].fields?.includes(ItemFields.ChildCount)).assertTrue()
      expect(service.itemRequests[0].enableImages === undefined).assertTrue()
      expect(service.itemRequests[1].includeItemTypes?.[0]).assertEqual(BaseItemKind.Movie)
      expect(service.itemRequests[1].fields?.includes(ItemFields.MediaStreams)).assertTrue()
      expect(service.itemRequests[1].enableImages).assertTrue()
    })

    it('rejectsForeignRefsAndReturnsServerConfirmedFavoriteAndWatchedState', 0, async () => {
      const owner = session('jellyfin-account-1')
      const other = session('jellyfin-account-2')
      const service = new FakeJellyfinCatalogService()
      service.favoriteResult = { IsFavorite: false }
      service.watchedResult = { Played: false, PlaybackPositionTicks: 20000000, PlayedPercentage: 25 }
      const provider = new JellyfinCatalogProvider(owner, service)
      let errorCode = MediaCatalogErrorCode.RequestFailed

      try {
        await provider.getDetail(ref(other, 'movie-1', MediaRefKind.Movie))
      } catch (error) {
        errorCode = (error as MediaCatalogError).code
      }
      const favorite = await provider.setFavorite(ref(owner, 'movie-1', MediaRefKind.Movie), true)
      const watched = await provider.setWatched(ref(owner, 'movie-1', MediaRefKind.Movie), true)

      expect(errorCode).assertEqual(MediaCatalogErrorCode.InvalidRequest)
      expect(service.getItemCount).assertEqual(0)
      expect(favorite.favorite).assertFalse()
      expect(watched.watched).assertFalse()
      expect(watched.playbackPositionMs).assertEqual(2000)
    })

    it('classifiesAuthenticationOfflineServiceAndInvalidRequestErrors', 0, () => {
      const owner = session()
      const unauthorized = JellyfinCatalogErrorMapper.from(httpError(401), owner, 'load')
      const offline = JellyfinCatalogErrorMapper.from(httpError(undefined, true), owner, 'load')
      const unavailable = JellyfinCatalogErrorMapper.from(httpError(503), owner, 'load')
      const invalid = JellyfinCatalogErrorMapper.from(httpError(undefined, false, 'RequiredError'), owner, 'load')

      expect(unauthorized.code).assertEqual(MediaCatalogErrorCode.AuthenticationExpired)
      expect(offline.code).assertEqual(MediaCatalogErrorCode.Offline)
      expect(unavailable.code).assertEqual(MediaCatalogErrorCode.ServiceUnavailable)
      expect(invalid.code).assertEqual(MediaCatalogErrorCode.InvalidRequest)
      expect(unauthorized.accountScope).assertEqual(owner.accountScope)
      expect(unauthorized.operation).assertEqual('load')
    })
  })
}
