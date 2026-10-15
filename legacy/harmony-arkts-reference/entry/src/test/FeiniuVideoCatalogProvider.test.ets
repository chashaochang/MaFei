import { describe, expect, it } from '@ohos/hypium'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'
import { MediaCatalogError, MediaCatalogErrorCode } from '../main/ets/media/catalog/MediaCatalogError'
import { MediaSummary } from '../main/ets/media/catalog/MediaCatalogModels'
import {
  MediaCatalogSortDirection,
  MediaCatalogSortField,
  MediaEpisodeQueryProfile
} from '../main/ets/media/catalog/MediaCatalogProvider'
import {
  FeiniuVideoCatalogImageAccess
} from '../main/ets/features/feiniuvideo/catalog/FeiniuVideoCatalogMapper'
import {
  FeiniuVideoCatalogErrorMapper,
  FeiniuVideoCatalogProvider,
  FeiniuVideoCatalogService
} from '../main/ets/features/feiniuvideo/catalog/FeiniuVideoCatalogProvider'
import {
  FnMediaCountResp,
  FnMediaEpisodeItem,
  FnMediaGenreItem,
  FnMediaItem,
  FnMediaItemDetailResp,
  FnMediaItemFile,
  FnMediaItemListResp,
  FnMediaLibraryItem,
  FnMediaListResp,
  FnMediaPersonItemListResp,
  FnMediaPersonListResp,
  FnMediaPersonResp,
  FnMediaPlayItem,
  FnMediaSeasonItem
} from '../main/ets/features/feiniuvideo/catalog/FeiniuVideoDtos'
import {
  FnMediaOfficialStreamReq,
  FnMediaOfficialStreamResp,
  FnMediaPlayInfoResp,
  FnMediaStreamListResp
} from
  '../main/ets/features/feiniuvideo/playback/FeiniuVideoPlaybackDtos'
import {
  FeiniuVideoApiRequest,
  FeiniuVideoAuthenticationError,
  FeiniuVideoHttpMethod,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  FnMediaUserInfoResp
} from '../main/ets/features/feiniuvideo/network/FeiniuVideoApiModels'

interface CountFixture extends Record<string, number> {
  total: number
  movie: number
  tv: number
  episode: number
  movies: number
  shows: number
  live: number
}

interface MutationFixture {
  success?: boolean
}

class FakeFeiniuVideoCatalogService implements FeiniuVideoCatalogService {
  readonly requests: FeiniuVideoApiRequest[] = []
  accessValue: FeiniuVideoCatalogImageAccess = new FeiniuVideoCatalogImageAccess(
    'http://lan:5666', 'token-1', 'session=old; Trim-MC-token=token-1')
  promotedAccess?: FeiniuVideoCatalogImageAccess
  promoteOnPath: string = ''
  errorPath: string = ''
  errorValue?: FeiniuVideoRequestError

  countResponse: FnMediaCountResp = { code: 0, msg: '', data: {} as CountFixture }
  libraryResponse: FnMediaListResp<FnMediaLibraryItem[]> = { code: 0, msg: '', data: [] }
  playResponse: FnMediaListResp<FnMediaPlayItem[]> = { code: 0, msg: '', data: [] }
  itemListResponses: FnMediaItemListResp[] = []
  searchResponse: FnMediaListResp<FnMediaItem[]> = { code: 0, msg: '', data: [] }
  detailResponse: FnMediaListResp<FnMediaItemDetailResp> = {
    code: 0,
    msg: '',
    data: { guid: 'movie-1', title: 'Movie', type: 'Movie' }
  }
  detailResponses: Map<string, FnMediaListResp<FnMediaItemDetailResp>> =
    new Map<string, FnMediaListResp<FnMediaItemDetailResp>>()
  personListResponse: FnMediaPersonListResp = { code: 0, msg: '', data: { list: [], total: 0 } }
  streamListResponse: FnMediaStreamListResp = { code: 0, msg: '', data: {} }
  itemFileResponses: Map<string, FnMediaListResp<FnMediaItemFile[]>> =
    new Map<string, FnMediaListResp<FnMediaItemFile[]>>()
  playInfoResponse: FnMediaPlayInfoResp = { code: 0, msg: '', data: {} }
  userInfoResponse: FnMediaUserInfoResp = {
    code: 0,
    msg: '',
    data: { username: 'tester', sources: [{ source_name: 'tester' }] }
  }
  officialStreamResponses: Map<string, FnMediaOfficialStreamResp> =
    new Map<string, FnMediaOfficialStreamResp>()
  seasonResponses: Map<string, FnMediaListResp<FnMediaSeasonItem[]>> =
    new Map<string, FnMediaListResp<FnMediaSeasonItem[]>>()
  episodeResponses: Map<string, FnMediaListResp<FnMediaEpisodeItem[]>> =
    new Map<string, FnMediaListResp<FnMediaEpisodeItem[]>>()
  personResponse: FnMediaPersonResp = {
    code: 0,
    msg: '',
    data: { guid: 'person-1', name: 'Person' }
  }
  personItemResponses: FnMediaPersonItemListResp[] = []
  genreResponse: FnMediaListResp<FnMediaGenreItem[]> = { code: 0, msg: '', data: [] }
  mutationResponse: FnMediaListResp<MutationFixture> = { code: 0, msg: '', data: {} }

  imageAccess(): FeiniuVideoCatalogImageAccess {
    return this.accessValue
  }

  async request<T>(request: FeiniuVideoApiRequest): Promise<T> {
    this.requests.push(request)
    if (request.path === this.errorPath && this.errorValue) {
      throw this.errorValue
    }
    if (request.path === this.promoteOnPath && this.promotedAccess) {
      this.accessValue = this.promotedAccess
    }
    if (request.path === '/v/api/v1/mediadb/sum') return this.countResponse as T
    if (request.path === '/v/api/v1/mediadb/list') return this.libraryResponse as T
    if (request.path === '/v/api/v1/play/list') return this.playResponse as T
    if (request.path === '/v/api/v1/item/list' || request.path === '/v/api/v1/favorite/list') {
      return this.nextItemListResponse() as T
    }
    if (request.path === '/v/api/v1/search/list') return this.searchResponse as T
    if (request.path === '/v/api/v1/tag/genres') return this.genreResponse as T
    if (request.path === '/v/api/v1/play/info') return this.playInfoResponse as T
    if (request.path === '/v/api/v1/user/info') return this.userInfoResponse as T
    if (request.path === '/v/api/v1/stream') {
      const payload = JSON.parse(request.bodyText) as FnMediaOfficialStreamReq
      return (this.officialStreamResponses.get(payload.media_guid) || this.emptyOfficialStreamResponse()) as T
    }
    if (request.path.startsWith('/v/api/v1/media/itemfile/')) {
      return (this.itemFileResponses.get(this.pathId(request.path)) ||
        this.emptyItemFileResponse()) as T
    }
    if (request.path.startsWith('/v/api/v1/item/')) {
      return (this.detailResponses.get(this.pathId(request.path)) || this.detailResponse) as T
    }
    if (request.path.startsWith('/v/api/v1/stream/list/')) return this.streamListResponse as T
    if (request.path.startsWith('/v/api/v1/person/list/')) return this.personListResponse as T
    if (request.path.startsWith('/v/api/v1/season/list/')) {
      return (this.seasonResponses.get(this.pathId(request.path)) || this.emptySeasonResponse()) as T
    }
    if (request.path.startsWith('/v/api/v1/episode/list/')) {
      return (this.episodeResponses.get(this.pathId(request.path)) || this.emptyEpisodeResponse()) as T
    }
    if (request.path === '/v/api/v1/person/item/list') return this.nextPersonItemResponse() as T
    if (request.path.startsWith('/v/api/v1/person/')) return this.personResponse as T
    if (request.path === '/v/api/v1/item/favorite' || request.path === '/v/api/v1/item/watched') {
      return this.mutationResponse as T
    }
    throw new FeiniuVideoRequestError(
      FeiniuVideoRequestFailureKind.InvalidRequest,
      `Unexpected fixture request ${request.path}`
    )
  }

  private nextItemListResponse(): FnMediaItemListResp {
    if (this.itemListResponses.length === 0) {
      return { code: 0, msg: '', data: { list: [], total: 0 } }
    }
    return this.itemListResponses.shift() || { code: 0, msg: '', data: { list: [], total: 0 } }
  }

  private nextPersonItemResponse(): FnMediaPersonItemListResp {
    if (this.personItemResponses.length === 0) {
      return { code: 0, msg: '', data: { list: [], total: 0 } }
    }
    return this.personItemResponses.shift() || { code: 0, msg: '', data: { list: [], total: 0 } }
  }

  private emptySeasonResponse(): FnMediaListResp<FnMediaSeasonItem[]> {
    return { code: 0, msg: '', data: [] }
  }

  private emptyEpisodeResponse(): FnMediaListResp<FnMediaEpisodeItem[]> {
    return { code: 0, msg: '', data: [] }
  }

  private emptyOfficialStreamResponse(): FnMediaOfficialStreamResp {
    return { code: 0, msg: '', data: {} }
  }

  private emptyItemFileResponse(): FnMediaListResp<FnMediaItemFile[]> {
    return { code: 0, msg: '', data: [] }
  }

  private pathId(path: string): string {
    const index = path.lastIndexOf('/')
    return index >= 0 ? decodeURIComponent(path.substring(index + 1)) : ''
  }
}

function session(providerAccountId: string = 'feiniu-account-1'): MediaProviderSession {
  return new MediaProviderSession({
    provider: MediaProviderKind.FeiniuVideo,
    providerAccountId: providerAccountId,
    userId: 'user-1',
    originalAddress: 'FN123456',
    baseUrl: 'http://lan:5666',
    displayName: 'Feiniu User'
  })
}

function ref(owner: MediaProviderSession, id: string, kind: MediaRefKind): MediaRef {
  return new MediaRef(owner.provider, owner.accountScope, id, kind)
}

function item(id: string, type: string = 'Movie', poster: string = ''): FnMediaItem {
  return {
    guid: id,
    title: id,
    type: type,
    poster: poster
  }
}

function requestsFor(service: FakeFeiniuVideoCatalogService, path: string): FeiniuVideoApiRequest[] {
  return service.requests.filter((request: FeiniuVideoApiRequest): boolean => request.path === path)
}

export default function feiniuVideoCatalogProviderTest() {
  describe('FeiniuVideoCatalogProvider', () => {
    it('buildsLocalRecommendationsFromCatalogSignalsAndCachesResults', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.detailResponse = { code: 0, msg: '', data: {} as FnMediaItemDetailResp }
      service.playResponse = {
        code: 0,
        msg: '',
        data: [
          {
            guid: 'movie-watched', title: 'movie-watched', type: 'Movie', poster: '/watched.jpg',
            watched: 1, media_stream: { progress: 100 }
          },
          {
            guid: 'unfinished', title: 'unfinished', type: 'Movie', poster: '/unfinished.jpg',
            watched: 0, media_stream: { progress: 35 }
          }
        ]
      }
      service.itemListResponses = [
        { code: 0, msg: '', data: { list: [
          {
            guid: 'movie-rated', title: 'movie-rated', type: 'Movie', poster: '/rated.jpg',
            vote_average: '9.2', genres: [20]
          },
          {
            guid: 'series-match', title: 'series-match', type: 'TV', poster: '/series.jpg',
            vote_average: '8.4', genres: [10]
          }
        ], total: 2 } },
        { code: 0, msg: '', data: { list: [
          {
            guid: 'movie-latest', title: 'movie-latest', type: 'Movie', poster: '/latest.jpg',
            vote_average: '8.6', genres: [30]
          },
          {
            guid: 'series-match', title: 'series-match', type: 'TV', poster: '/series.jpg',
            vote_average: '8.4', genres: [10]
          }
        ], total: 2 } },
        { code: 0, msg: '', data: { list: [
          {
            guid: 'favorite', title: 'favorite', type: 'Movie', poster: '/favorite.jpg',
            is_favorite: 1, genres: [10]
          }
        ], total: 1 } }
      ]
      service.detailResponses.set('series-match', {
        code: 0,
        msg: '',
        data: {
          guid: 'series-match',
          title: 'series-match',
          type: 'TV',
          poster: '/series.jpg',
          backdrops: '/series-backdrop.jpg',
          logos: '/series-logo.png'
        }
      })
      const provider = new FeiniuVideoCatalogProvider(owner, service)

      const recommendations = await provider.recommendations({ categoryLimit: 4, itemLimit: 4, resultLimit: 3 })
      const requestCount = service.requests.length
      const cached = await provider.recommendations({ categoryLimit: 4, itemLimit: 4, resultLimit: 3 })

      const itemRequests = requestsFor(service, '/v/api/v1/item/list')
      const favoriteRequests = requestsFor(service, '/v/api/v1/favorite/list')
      const playRequests = requestsFor(service, '/v/api/v1/play/list')
      expect(recommendations.length).assertEqual(3)
      expect(cached.map((entry: MediaSummary): string => entry.ref.id).join(','))
        .assertEqual(recommendations.map((entry: MediaSummary): string => entry.ref.id).join(','))
      expect(service.requests.length).assertEqual(requestCount)
      expect(itemRequests.length).assertEqual(2)
      expect(favoriteRequests.length).assertEqual(1)
      expect(playRequests.length).assertEqual(1)
      expect(itemRequests[0].bodyText.indexOf('"sort_column":"vote_average"') >= 0).assertTrue()
      expect(itemRequests[1].bodyText.indexOf('"sort_column":"create_time"') >= 0).assertTrue()
      const series = recommendations.find((entry: MediaSummary): boolean => entry.ref.id === 'series-match')
      expect((series?.backdropImage?.url || '').indexOf('/series-backdrop.jpg') >= 0).assertTrue()
      expect((series?.logoImage?.url || '').indexOf('/series-logo.png') >= 0).assertTrue()
    })

    it('loadsRealLibrariesAndCountsWhileHidingLiveCapability', 0, async () => {
      const service = new FakeFeiniuVideoCatalogService()
      service.libraryResponse = {
        code: 0,
        msg: '',
        data: [
          { guid: 'movies', title: 'Movies', category: 'movie' },
          { guid: 'shows', title: 'Shows', category: 'tv' },
          { guid: 'live', title: 'Live', category: 'live' }
        ]
      }
      const countData: CountFixture = {
        total: 30,
        movie: 12,
        tv: 8,
        episode: 90,
        movies: 20,
        shows: 10,
        live: 5
      }
      service.countResponse = {
        code: 0,
        msg: '',
        data: countData
      }
      const provider = new FeiniuVideoCatalogProvider(session(), service)

      const libraries = await provider.getLibraries()
      const counts = await provider.getCounts()

      expect(libraries.length).assertEqual(2)
      expect(libraries[0].itemCount).assertEqual(20)
      expect(libraries[1].itemCount).assertEqual(10)
      expect(counts.totalCount).assertEqual(30)
      expect(counts.movieCount).assertEqual(12)
      expect(counts.seriesCount).assertEqual(8)
      expect(counts.episodeCount).assertEqual(90)
      expect(service.requests[0].method).assertEqual(FeiniuVideoHttpMethod.Get)
      expect(service.requests[0].requireMediaToken).assertTrue()
    })

    it('mapsContinueWatchingFromPlayListOnly', 0, async () => {
      const service = new FakeFeiniuVideoCatalogService()
      service.playResponse = {
        code: 0,
        msg: '',
        data: [{
          guid: 'episode-2',
          title: 'Episode 2',
          type: 'Episode',
          parent_guid: 'season-1',
          ancestor_guid: 'series-1',
          parent_title: 'Series One',
          season_number: 1,
          episode_number: 2,
          ts: 600,
          duration: 2400,
          media_stream: { progress: 25 }
        }]
      }
      const provider = new FeiniuVideoCatalogProvider(session(), service)

      const page = await provider.resume({ kinds: [MediaRefKind.Episode], limit: 8 })

      expect(page.items.length).assertEqual(1)
      expect(page.items[0].title).assertEqual('Series One')
      expect(page.items[0].seasonNumber).assertEqual(1)
      expect(page.items[0].episodeNumber).assertEqual(2)
      expect(page.items[0].userState.watched.playedPercentage).assertEqual(25)
      expect(service.requests[0].path).assertEqual('/v/api/v1/play/list')
    })

    it('keepsContinueWatchingInServerOrder', 0, async () => {
      const service = new FakeFeiniuVideoCatalogService()
      service.playResponse = {
        code: 0,
        msg: '',
        data: [
          { guid: 'server-first', title: 'First', type: 'Movie', watched_ts: 100 },
          { guid: 'server-second', title: 'Second', type: 'Movie', watched_ts: 200 }
        ]
      }
      const provider = new FeiniuVideoCatalogProvider(session(), service)

      const page = await provider.resume({ kinds: [MediaRefKind.Movie], limit: 8 })

      expect(page.items.length).assertEqual(2)
      expect(page.items[0].ref.id).assertEqual('server-first')
      expect(page.items[1].ref.id).assertEqual('server-second')
    })

    it('selectsResumableThenNextEpisodeForASeries', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.seasonResponses.set('series-1', {
        code: 0,
        msg: '',
        data: [{
          guid: 'season-1', title: 'Season 1', type: 'Season', parent_guid: 'series-1',
          ancestor_guid: 'library-tv', season_number: 1
        }]
      })
      service.episodeResponses.set('season-1', {
        code: 0,
        msg: '',
        data: [
          { guid: 'episode-1', title: 'Episode 1', type: 'Episode', parent_guid: 'season-1', episode_number: 1 },
          { guid: 'episode-2', title: 'Episode 2', type: 'Episode', parent_guid: 'season-1', episode_number: 2 },
          { guid: 'episode-3', title: 'Episode 3', type: 'Episode', parent_guid: 'season-1', episode_number: 3 }
        ]
      })
      service.detailResponses.set('season-1', {
        code: 0,
        msg: '',
        data: {
          guid: 'season-1', title: 'Season 1', type: 'Season', parent_guid: 'series-1',
          ancestor_guid: 'library-tv', parent_title: 'Series One'
        }
      })
      service.detailResponses.set('series-1', {
        code: 0,
        msg: '',
        data: {
          guid: 'series-1', title: 'Series One', type: 'TV', poster: '/series.jpg',
          backdrops: '/series-backdrop.jpg', logos: '/series-logo.png'
        }
      })
      service.detailResponses.set('episode-2', {
        code: 0,
        msg: '',
        data: {
          guid: 'episode-2', title: 'Episode 2', type: 'Episode', parent_guid: 'season-1',
          ancestor_guid: 'library-tv', season_number: 1, episode_number: 2
        }
      })
      service.detailResponses.set('episode-3', {
        code: 0,
        msg: '',
        data: {
          guid: 'episode-3', title: 'Episode 3', type: 'Episode', parent_guid: 'season-1',
          ancestor_guid: 'library-tv', season_number: 1, episode_number: 3
        }
      })
      service.playResponse = {
        code: 0,
        msg: '',
        data: [{
          guid: 'episode-2', title: 'Episode 2', type: 'Episode', parent_guid: 'season-1',
          season_number: 1, episode_number: 2, watched_ts: 200, watched: 0, duration: 2400,
          media_stream: { progress: 25, current_time: 600 }
        }]
      }

      const resumable = await new FeiniuVideoCatalogProvider(owner, service).nextUp({
        seriesRef: ref(owner, 'series-1', MediaRefKind.Series),
        limit: 1,
        disableFirstEpisode: false,
        enableResumable: true,
        enableRewatching: false
      })

      expect(resumable.items.length).assertEqual(1)
      expect(resumable.items[0].ref.id).assertEqual('episode-2')
      expect(resumable.items[0].seriesRef?.id).assertEqual('series-1')
      expect(resumable.items[0].seasonRef?.id).assertEqual('season-1')
      expect(resumable.items[0].userState.watched.playedPercentage).assertEqual(25)
      expect((resumable.items[0].backdropImage?.url || '').indexOf('/series-backdrop.jpg') >= 0).assertTrue()

      service.playResponse = {
        code: 0,
        msg: '',
        data: [{
          guid: 'episode-2', title: 'Episode 2', type: 'Episode', parent_guid: 'season-1',
          season_number: 1, episode_number: 2, watched_ts: 300, watched: 1,
          media_stream: { progress: 100 }
        }]
      }
      const next = await new FeiniuVideoCatalogProvider(owner, service).nextUp({
        seriesRef: ref(owner, 'series-1', MediaRefKind.Series),
        limit: 1,
        disableFirstEpisode: false,
        enableResumable: true,
        enableRewatching: false
      })

      expect(next.items.length).assertEqual(1)
      expect(next.items[0].ref.id).assertEqual('episode-3')

      service.episodeResponses.set('season-1', {
        code: 0,
        msg: '',
        data: [
          {
            guid: 'episode-6', title: 'Episode 6', type: 'Episode', parent_guid: 'season-1',
            episode_number: 6, watched: 1
          },
          {
            guid: 'episode-7', title: 'Episode 7', type: 'Episode', parent_guid: 'season-1',
            episode_number: 7, watched: 0
          }
        ]
      })
      service.playResponse = {
        code: 0,
        msg: '',
        data: [{
          guid: 'episode-6', title: 'Episode 6', type: 'Episode', parent_guid: 'season-1',
          episode_number: 6, watched_ts: 400, watched: 0, duration: 2400,
          media_stream: { progress: 25, current_time: 600 }
        }]
      }
      const nextFromCatalogWatchedState = await new FeiniuVideoCatalogProvider(owner, service).nextUp({
        seriesRef: ref(owner, 'series-1', MediaRefKind.Series),
        limit: 1,
        disableFirstEpisode: false,
        enableResumable: true,
        enableRewatching: false
      })

      expect(nextFromCatalogWatchedState.items.length).assertEqual(1)
      expect(nextFromCatalogWatchedState.items[0].ref.id).assertEqual('episode-7')
    })

    it('keepsEpisodesMountedDirectlyUnderSeriesOutOfFakeSeasonHierarchy', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.seasonResponses.set('series-direct', { code: 0, msg: '', data: [] })
      service.episodeResponses.set('series-direct', {
        code: 0,
        msg: '',
        data: [
          {
            guid: 'episode-direct-1', title: 'Episode 1', type: 'Episode', parent_guid: 'series-direct',
            ancestor_guid: 'library-tv', episode_number: 1, watched: 1
          },
          {
            guid: 'episode-direct-2', title: 'Episode 2', type: 'Episode', parent_guid: 'series-direct',
            ancestor_guid: 'library-tv', episode_number: 2
          }
        ]
      })
      service.detailResponses.set('series-direct', {
        code: 0,
        msg: '',
        data: {
          guid: 'series-direct', title: 'Direct Series', type: 'TV', poster: '/direct.jpg',
          backdrops: '/direct-backdrop.jpg', logos: '/direct-logo.png'
        }
      })
      service.detailResponses.set('episode-direct-2', {
        code: 0,
        msg: '',
        data: {
          guid: 'episode-direct-2', title: 'Episode 2', type: 'Episode', parent_guid: 'series-direct',
          ancestor_guid: 'library-tv', episode_number: 2
        }
      })
      service.playResponse = {
        code: 0,
        msg: '',
        data: [{
          guid: 'episode-direct-1', title: 'Episode 1', type: 'Episode', parent_guid: 'series-direct',
          episode_number: 1, watched: 1, watched_ts: 100, media_stream: { progress: 100 }
        }]
      }
      const provider = new FeiniuVideoCatalogProvider(owner, service)

      const next = await provider.nextUp({
        seriesRef: ref(owner, 'series-direct', MediaRefKind.Series),
        limit: 1,
        disableFirstEpisode: false,
        enableResumable: true,
        enableRewatching: false
      })
      const episodes = await provider.getEpisodes({
        seriesRef: ref(owner, 'series-direct', MediaRefKind.Series),
        startIndex: 0,
        limit: 20,
        profile: MediaEpisodeQueryProfile.Display
      })

      expect(next.items[0].ref.id).assertEqual('episode-direct-2')
      expect(next.items[0].seriesRef?.id).assertEqual('series-direct')
      expect(next.items[0].seasonRef === undefined).assertTrue()
      expect((next.items[0].backdropImage?.url || '').indexOf('/direct-backdrop.jpg') >= 0).assertTrue()
      expect(episodes.items[1].seriesRef.id).assertEqual('series-direct')
      expect(episodes.items[1].seasonRef === undefined).assertTrue()
    })

    it('handlesNonAlignedServerPagesAndUsesPromotedRouteForImages', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.itemListResponses = [
        { code: 0, msg: '', data: { list: [item('m0'), item('m1'), item('m2'), item('m3')], total: 10 } },
        { code: 0, msg: '', data: {
          list: [
            item('m4', 'Movie', '/m4.jpg'),
            { guid: 'm5', title: 'm5', type: 'Movie', genres: [1] },
            item('m6'),
            item('m7')
          ],
          total: 10
        } }
      ]
      service.genreResponse = { code: 0, msg: '', data: [{ id: 1, value: '剧情' }] }
      service.promoteOnPath = '/v/api/v1/item/list'
      service.promotedAccess = new FeiniuVideoCatalogImageAccess(
        'https://relay.example', 'token-new', 'session=new; Trim-MC-token=token-new', true)
      const provider = new FeiniuVideoCatalogProvider(owner, service)

      const page = await provider.latest({
        libraryRef: ref(owner, 'library-1', MediaRefKind.Library),
        kinds: [MediaRefKind.Movie],
        startIndex: 3,
        limit: 4,
        recursive: true
      })

      const itemRequests = requestsFor(service, '/v/api/v1/item/list')
      expect(page.items.map((entry): string => entry.ref.id).join(',')).assertEqual('m3,m4,m5,m6')
      expect(page.nextStartIndex).assertEqual(7)
      expect(page.totalCount).assertEqual(10)
      expect(page.hasMore).assertTrue()
      expect(itemRequests.length).assertEqual(2)
      expect(itemRequests[0].bodyText.indexOf('"page":1') >= 0).assertTrue()
      expect(itemRequests[1].bodyText.indexOf('"page":2') >= 0).assertTrue()
      expect(page.items[1].primaryImage?.url).assertEqual(
        'https://relay.example/v/api/v1/sys/img/m4.jpg?w=640')
      expect((page.items[1].primaryImage?.headers[1].value || '').indexOf('mode=relay') >= 0).assertTrue()
      expect(page.items[2].genres.join(',')).assertEqual('剧情')
    })

    it('usesFavoriteListWatchedTagsSortAndMutationMethods', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.itemListResponses = [{
        code: 0,
        msg: '',
        data: { list: [item('movie-1')], total: 1 }
      }]
      const provider = new FeiniuVideoCatalogProvider(owner, service)

      await provider.getItems({
        libraryRef: ref(owner, 'library-1', MediaRefKind.Library),
        kinds: [MediaRefKind.Movie],
        startIndex: 0,
        limit: 20,
        sortFields: [MediaCatalogSortField.CommunityRating],
        sortDirections: [MediaCatalogSortDirection.Ascending],
        recursive: true,
        collapseBoxSetItems: false,
        favoriteOnly: true,
        watched: false
      })
      const favorite = await provider.setFavorite(ref(owner, 'movie-1', MediaRefKind.Movie), true)
      const watched = await provider.setWatched(ref(owner, 'movie-1', MediaRefKind.Movie), false)

      const listRequest = requestsFor(service, '/v/api/v1/favorite/list')[0]
      const favoriteRequest = requestsFor(service, '/v/api/v1/item/favorite')[0]
      const watchedRequest = requestsFor(service, '/v/api/v1/item/watched')[0]
      expect(listRequest.bodyText.indexOf('"watched":"0"') >= 0).assertTrue()
      expect(listRequest.bodyText.indexOf('"type":["Movie"]') >= 0).assertTrue()
      expect(listRequest.bodyText.indexOf('"sort_column":"vote_average"') >= 0).assertTrue()
      expect(listRequest.bodyText.indexOf('"sort_type":"ASC"') >= 0).assertTrue()
      expect(favoriteRequest.method).assertEqual(FeiniuVideoHttpMethod.Put)
      expect(watchedRequest.method).assertEqual(FeiniuVideoHttpMethod.Delete)
      expect(favorite.favorite).assertTrue()
      expect(watched.watched).assertFalse()
    })

    it('scopesSearchToRequestedLibraryAndSlicesClientSideResults', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.searchResponse = {
        code: 0,
        msg: '',
        data: [
          { guid: 'movie-1', title: 'One', type: 'Movie', ancestor_guid: 'library-1' },
          { guid: 'movie-2', title: 'Two', type: 'Movie', ancestor_guid: 'library-2' },
          { guid: 'series-1', title: 'Series', type: 'TV', ancestor_guid: 'library-1' }
        ]
      }
      const provider = new FeiniuVideoCatalogProvider(owner, service)

      const page = await provider.search({
        query: ' movie ',
        libraryRef: ref(owner, 'library-1', MediaRefKind.Library),
        kinds: [MediaRefKind.Movie],
        startIndex: 0,
        limit: 1,
        recursive: true
      })

      const searchRequest = requestsFor(service, '/v/api/v1/search/list')[0]
      expect(page.items.length).assertEqual(1)
      expect(page.items[0].ref.id).assertEqual('movie-1')
      expect(page.totalCount).assertEqual(1)
      expect(searchRequest.parameters[0].name).assertEqual('q')
      expect(searchRequest.parameters[0].value).assertEqual('movie')
    })

    it('loadsDetailPeopleSeasonsAndFallsBackEpisodesToSeriesGuid', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.detailResponse = {
        code: 0,
        msg: '',
        data: { guid: 'series-1', title: 'Series One', type: 'TV', genres: [1] }
      }
      service.personListResponse = {
        code: 0,
        msg: '',
        data: { list: [{ person_guid: 'person-1', name: 'Actor', job: 'Actor' }], total: 1 }
      }
      service.seasonResponses.set('series-1', { code: 0, msg: '', data: [] })
      service.episodeResponses.set('series-1', {
        code: 0,
        msg: '',
        data: [{ guid: 'episode-1', title: 'Pilot', type: 'Episode', episode_number: 1 }]
      })
      service.genreResponse = { code: 0, msg: '', data: [{ id: 1, value: '剧情' }] }
      const provider = new FeiniuVideoCatalogProvider(owner, service)

      const detail = await provider.getDetail(ref(owner, 'series-1', MediaRefKind.Series))
      const episodes = await provider.getEpisodes({
        seriesRef: ref(owner, 'series-1', MediaRefKind.Series),
        startIndex: 0,
        limit: 20,
        profile: MediaEpisodeQueryProfile.Display
      })

      expect(detail.personCredits.length).assertEqual(1)
      expect(detail.genres.join(',')).assertEqual('剧情')
      expect(episodes.items.length).assertEqual(1)
      expect(episodes.items[0].ref.id).assertEqual('episode-1')
      expect(requestsFor(service, '/v/api/v1/episode/list/series-1').length).assertEqual(1)
      expect(requestsFor(service, '/v/api/v1/play/info').length).assertEqual(1)
    })

    it('mapsSeriesPlaybackTargetFromPlayInfo', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.detailResponse = {
        code: 0,
        msg: '',
        data: { guid: 'series-1', title: '奔跑吧', type: 'TV' }
      }
      service.seasonResponses.set('series-1', {
        code: 0,
        msg: '',
        data: [
          { guid: 'season-7', title: '奔跑吧 第7季', type: 'Season', season_number: 7 },
          { guid: 'season-1', title: '奔跑吧 第1季', type: 'Season', season_number: 1 }
        ]
      })
      service.playInfoResponse = {
        code: 0,
        msg: '',
        data: {
          guid: 'episode-7-1',
          item: {
            title: '城市垃圾',
            type: 'Episode',
            tv_title: '奔跑吧',
            parent_title: '奔跑吧 第3季',
            season_number: 7,
            episode_number: 1
          }
        }
      }
      const provider = new FeiniuVideoCatalogProvider(owner, service)

      const detail = await provider.getDetail(ref(owner, 'series-1', MediaRefKind.Series))

      expect(detail.playbackTarget?.ref?.id).assertEqual('episode-7-1')
      expect(detail.playbackTarget?.seasonNumber).assertEqual(7)
      expect(detail.playbackTarget?.episodeNumber).assertEqual(1)
      expect(requestsFor(service, '/v/api/v1/play/info').length).assertEqual(1)
      expect(requestsFor(service, '/v/api/v1/play/list').length).assertEqual(0)
    })

    it('loadsOptionalStreamListForPlayableDetailAndMapsEachFile', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.detailResponse = {
        code: 0,
        msg: '',
        data: { guid: 'movie-1', title: 'Movie', type: 'Movie', duration: 7200 }
      }
      service.streamListResponse = {
        code: 0,
        msg: '',
        data: {
          files: [
            { guid: 'media-4k', file_name: 'Movie.4K.mkv', size: 4294967296 },
            { guid: 'media-1080', file_name: 'Movie.1080p.mp4', size: 2147483648 }
          ],
          video_streams: [
            { media_guid: 'media-4k', resolution_type: '4K', codec_name: 'hevc', wrapper: 'mkv' },
            { media_guid: 'media-1080', resolution_type: '1080P', codec_name: 'h264', wrapper: 'mp4' }
          ]
        }
      }
      const provider = new FeiniuVideoCatalogProvider(owner, service)

      const detail = await provider.getDetail(ref(owner, 'movie-1', MediaRefKind.Movie))

      expect(detail.mediaFiles.length).assertEqual(2)
      expect(detail.mediaFiles[0].id).assertEqual('media-4k')
      expect(detail.mediaFiles[1].id).assertEqual('media-1080')
      expect(requestsFor(service, '/v/api/v1/stream/list/movie-1').length).assertEqual(1)
    })

    it('refreshesResolvedItemDetailsInsteadOfKeepingStaleCatalogState', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.detailResponses.set('movie-1', {
        code: 0,
        msg: '',
        data: { guid: 'movie-1', title: 'Movie Before', type: 'Movie', is_favorite: 0 }
      })
      const provider = new FeiniuVideoCatalogProvider(owner, service)

      const before = await provider.getDetail(ref(owner, 'movie-1', MediaRefKind.Movie))
      service.detailResponses.set('movie-1', {
        code: 0,
        msg: '',
        data: { guid: 'movie-1', title: 'Movie After', type: 'Movie', is_favorite: 1 }
      })
      const after = await provider.getDetail(ref(owner, 'movie-1', MediaRefKind.Movie))

      expect(before.title).assertEqual('Movie Before')
      expect(before.userState.favorite.favorite).assertFalse()
      expect(after.title).assertEqual('Movie After')
      expect(after.userState.favorite.favorite).assertTrue()
      expect(requestsFor(service, '/v/api/v1/item/movie-1').length).assertEqual(2)
    })

    it('mergesItemFilesOfficialStreamsAndPreferredMediaVersion', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.detailResponse = {
        code: 0,
        msg: '',
        data: { guid: 'movie-1', title: 'Movie', type: 'Movie', duration: 7200, media_guid: 'media-4k' }
      }
      service.streamListResponse = {
        code: 0,
        msg: '',
        data: {
          files: [{ guid: 'media-4k', file_name: 'Movie.4K.mkv' }],
          video_streams: [{ media_guid: 'media-4k', index: 0, codec_name: 'hevc' }]
        }
      }
      service.itemFileResponses.set('movie-1', {
        code: 0,
        msg: '',
        data: [
          { media_guid: 'media-4k', filename: 'Movie.4K.mkv', file_path: '/media/Movie.4K.mkv', file_size: 4000 },
          { media_guid: 'media-1080', filename: 'Movie.1080p.mp4', file_path: '/media/Movie.1080p.mp4', size: 2000 }
        ]
      })
      service.playInfoResponse = { code: 0, msg: '', data: { media_guid: 'media-1080' } }
      service.officialStreamResponses.set('media-4k', {
        code: 0,
        msg: '',
        data: {
          file_stream: { guid: 'media-4k', file_name: 'Movie.4K.mkv' },
          video_stream: {
            media_guid: 'media-4k', index: 0, codec_name: 'hevc', resolution_type: '4K',
            wrapper: 'mkv', width: 3840, height: 2160, avg_frame_rate: '24000/1001',
            color_range_type: 'Dolby Vision'
          },
          audio_streams: [{ media_guid: 'media-4k', index: 1, codec_name: 'eac3', channels: 6 }],
          subtitle_streams: [{ media_guid: 'media-4k', index: 2, codec_name: 'ass', language: 'zh' }]
        }
      })
      service.officialStreamResponses.set('media-1080', {
        code: 0,
        msg: '',
        data: {
          file_stream: { guid: 'media-1080', file_name: 'Movie.1080p.mp4' },
          video_stream: {
            media_guid: 'media-1080', index: 0, codec_name: 'h264', resolution_type: '1080P',
            wrapper: 'mp4', width: 1920, height: 1080, avg_frame_rate: '25/1'
          },
          audio_streams: [{ media_guid: 'media-1080', index: 1, codec_name: 'aac', channels: 2 }]
        }
      })

      const detail = await new FeiniuVideoCatalogProvider(owner, service)
        .getDetail(ref(owner, 'movie-1', MediaRefKind.Movie))

      expect(detail.mediaFiles.length).assertEqual(2)
      expect(detail.mediaFiles[0].id).assertEqual('media-1080')
      expect(detail.mediaFiles[0].path).assertEqual('/media/Movie.1080p.mp4')
      expect(detail.mediaFiles[0].sizeBytes).assertEqual(2000)
      expect(detail.mediaFiles[0].streams[0].width).assertEqual(1920)
      expect(detail.mediaFiles[1].id).assertEqual('media-4k')
      expect(detail.mediaFiles[1].path).assertEqual('/media/Movie.4K.mkv')
      expect(detail.mediaFiles[1].streams[0].frameRate).assertEqual(24000 / 1001)
      expect(detail.mediaFiles[1].streams.length).assertEqual(3)
      expect(requestsFor(service, '/v/api/v1/play/info').length).assertEqual(1)
      expect(requestsFor(service, '/v/api/v1/user/info').length).assertEqual(1)
      expect(requestsFor(service, '/v/api/v1/stream').length).assertEqual(2)
    })

    it('usesPlayInfoMediaGuidWhenOtherDetailEndpointsDoNotExposeTheSource', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.detailResponse = {
        code: 0,
        msg: '',
        data: { guid: 'movie-1', title: 'Movie', type: 'Movie', duration: 7200 }
      }
      service.playInfoResponse = { code: 0, msg: '', data: { media_guid: 'media-only' } }
      service.officialStreamResponses.set('media-only', {
        code: 0,
        msg: '',
        data: {
          file_stream: { guid: 'media-only', file_name: 'Movie.mkv', size: 4000 },
          video_stream: {
            media_guid: 'media-only', index: 0, codec_name: 'hevc', resolution_type: '4K',
            wrapper: 'mkv', width: 3840, height: 2160
          }
        }
      })

      const detail = await new FeiniuVideoCatalogProvider(owner, service)
        .getDetail(ref(owner, 'movie-1', MediaRefKind.Movie))

      expect(detail.mediaFiles.length).assertEqual(1)
      expect(detail.mediaFiles[0].id).assertEqual('media-only')
      expect(detail.mediaFiles[0].streams[0].width).assertEqual(3840)
      expect(requestsFor(service, '/v/api/v1/stream').length).assertEqual(1)
    })

    it('fallsBackToEpisodeListWhenSeasonLookupRejectsHistoryGuid', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.errorPath = '/v/api/v1/season/list/season-1'
      service.errorValue = new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.Business, 'not a series guid')
      service.episodeResponses.set('season-1', {
        code: 0,
        msg: '',
        data: [
          { guid: 'episode-1', title: 'Episode 1', type: 'Episode', episode_number: 1 },
          { guid: 'episode-2', title: 'Episode 2', type: 'Episode', episode_number: 2 }
        ]
      })
      const provider = new FeiniuVideoCatalogProvider(owner, service)

      const episodes = await provider.getEpisodes({
        seriesRef: ref(owner, 'season-1', MediaRefKind.Series),
        startIndex: 0,
        limit: 20,
        profile: MediaEpisodeQueryProfile.Display
      })

      expect(episodes.items.length).assertEqual(2)
      expect(episodes.items[1].episodeNumber).assertEqual(2)
      expect(requestsFor(service, '/v/api/v1/episode/list/season-1').length).assertEqual(1)
    })

    it('queriesActorDirectorAndScreenplayWorksThroughPersonEndpoint', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuVideoCatalogService()
      service.personItemResponses = [
        { code: 0, msg: '', data: { list: [{ item_guid: 'movie-1', title: 'Movie', type: 'Movie' }], total: 1 } },
        { code: 0, msg: '', data: { list: [{ item_guid: 'movie-2', title: 'Movie 2', type: 'Movie' }], total: 1 } },
        { code: 0, msg: '', data: { list: [], total: 0 } }
      ]
      const provider = new FeiniuVideoCatalogProvider(owner, service)

      const page = await provider.getItems({
        personRef: ref(owner, 'person-1', MediaRefKind.Person),
        kinds: [MediaRefKind.Movie],
        startIndex: 0,
        limit: 20,
        sortFields: [MediaCatalogSortField.DateCreated],
        sortDirections: [MediaCatalogSortDirection.Descending],
        recursive: true,
        collapseBoxSetItems: false,
        favoriteOnly: false
      })

      const requests = requestsFor(service, '/v/api/v1/person/item/list')
      expect(requests.length).assertEqual(3)
      expect(requests[0].bodyText.indexOf('"job":"Actor"') >= 0).assertTrue()
      expect(requests[1].bodyText.indexOf('"job":"Director"') >= 0).assertTrue()
      expect(requests[2].bodyText.indexOf('"job":"Screenplay"') >= 0).assertTrue()
      expect(page.items.length).assertEqual(2)
    })

    it('rejectsForeignRefsAndClassifiesAuthBusinessAndEmptyDetailErrors', 0, async () => {
      const owner = session('feiniu-account-1')
      const other = session('feiniu-account-2')
      const foreignService = new FakeFeiniuVideoCatalogService()
      const foreignProvider = new FeiniuVideoCatalogProvider(owner, foreignService)
      let foreignCode = MediaCatalogErrorCode.RequestFailed
      try {
        await foreignProvider.getDetail(ref(other, 'movie-1', MediaRefKind.Movie))
      } catch (error) {
        foreignCode = (error as MediaCatalogError).code
      }

      const authService = new FakeFeiniuVideoCatalogService()
      authService.errorPath = '/v/api/v1/mediadb/sum'
      authService.errorValue = new FeiniuVideoAuthenticationError('expired', true)
      let authCode = MediaCatalogErrorCode.RequestFailed
      try {
        await new FeiniuVideoCatalogProvider(owner, authService).getCounts()
      } catch (error) {
        authCode = (error as MediaCatalogError).code
      }

      const business = FeiniuVideoCatalogErrorMapper.from(
        new FeiniuVideoRequestError(FeiniuVideoRequestFailureKind.Business, 'rejected'), owner, 'load')

      const emptyService = new FakeFeiniuVideoCatalogService()
      emptyService.detailResponse = { code: 0, msg: '', data: {} as FnMediaItemDetailResp }
      let emptyCode = MediaCatalogErrorCode.RequestFailed
      try {
        await new FeiniuVideoCatalogProvider(owner, emptyService)
          .getDetail(ref(owner, 'movie-1', MediaRefKind.Movie))
      } catch (error) {
        emptyCode = (error as MediaCatalogError).code
      }

      expect(foreignCode).assertEqual(MediaCatalogErrorCode.InvalidRequest)
      expect(foreignService.requests.length).assertEqual(0)
      expect(authCode).assertEqual(MediaCatalogErrorCode.AuthenticationExpired)
      expect(business.code).assertEqual(MediaCatalogErrorCode.RequestFailed)
      expect(business.retryable).assertFalse()
      expect(emptyCode).assertEqual(MediaCatalogErrorCode.IncompatibleService)
    })
  })
}
