import { describe, expect, it } from '@ohos/hypium'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'
import { MediaImageKind, MediaLibraryKind, MediaPersonKind } from
  '../main/ets/media/catalog/MediaCatalogModels'
import {
  FeiniuVideoCatalogImageAccess,
  FeiniuVideoCatalogMapper
} from '../main/ets/features/feiniuvideo/catalog/FeiniuVideoCatalogMapper'
import {
  FnMediaEpisodeItem,
  FnMediaItem,
  FnMediaItemDetailResp,
  FnMediaPersonListItem,
  FnMediaPlayItem,
  FnMediaSeasonItem
} from '../main/ets/features/feiniuvideo/catalog/FeiniuVideoDtos'

interface CountFixture extends Record<string, number> {
  total: number
  movie: number
  tv: number
  episode: number
  'library-movies': number
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

function mapper(owner: MediaProviderSession = session()): FeiniuVideoCatalogMapper {
  const genres = new Map<number, string>()
  genres.set(10, '剧情')
  genres.set(20, '科幻')
  return new FeiniuVideoCatalogMapper(
    owner,
    new FeiniuVideoCatalogImageAccess(
      'https://relay.example/',
      'media-token',
      'session=abc; Trim-MC-token=media-token',
      true
    ),
    genres
  )
}

export default function feiniuVideoCatalogMapperTest() {
  describe('FeiniuVideoCatalogMapper', () => {
    it('mapsCountsLibrariesAndHidesUnsupportedLiveLibraries', 0, () => {
      const catalogMapper = mapper()
      const countData: CountFixture = {
        total: 30,
        movie: 12,
        tv: 8,
        episode: 80,
        'library-movies': 21
      }
      const counts = catalogMapper.counts(countData)
      const library = catalogMapper.library({
        guid: 'library-movies',
        title: '电影库',
        category: 'movie',
        posters: ['/poster/library.jpg']
      }, countData)

      expect(counts.totalCount).assertEqual(30)
      expect(counts.movieCount).assertEqual(12)
      expect(counts.seriesCount).assertEqual(8)
      expect(counts.episodeCount).assertEqual(80)
      expect(library.ref.provider).assertEqual(MediaProviderKind.FeiniuVideo)
      expect(library.ref.accountScope).assertEqual(session().accountScope)
      expect(library.kind).assertEqual(MediaLibraryKind.Movies)
      expect(library.itemCount).assertEqual(21)
      expect(catalogMapper.isVisibleLibrary({ guid: 'live', title: '直播', category: 'live' })).assertFalse()
    })

    it('keepsImageRouteCredentialsAndRelayCookieInsideMediaImageRef', 0, () => {
      const summary = mapper().summary({
        guid: 'movie-1',
        title: 'Movie',
        type: 'Movie',
        poster: '/posters/movie-1.jpg'
      })
      const image = summary.primaryImage!

      expect(image.kind).assertEqual(MediaImageKind.Primary)
      expect(image.url).assertEqual(
        'https://relay.example/v/api/v1/sys/img/posters/movie-1.jpg?w=640')
      expect(image.headers.length).assertEqual(2)
      expect(image.headers[0].name).assertEqual('Authorization')
      expect(image.headers[0].value).assertEqual('media-token')
      expect(image.headers[1].name).assertEqual('Cookie')
      expect(image.headers[1].value.indexOf('Trim-MC-token=media-token') >= 0).assertTrue()
      expect(image.headers[1].value.indexOf('mode=relay') >= 0).assertTrue()
      expect(image.cacheKey.indexOf('media-token') >= 0).assertFalse()
    })

    it('mapsSummaryRatingDurationProgressGenresAndSeriesReferences', 0, () => {
      const item: FnMediaItem = {
        guid: 'episode-3',
        title: 'Episode Three',
        type: 'Episode',
        ancestor_guid: 'series-1',
        ancestor_name: 'Series One',
        parent_guid: 'season-1',
        season_number: 1,
        episode_number: 3,
        vote_average: '8.6',
        duration: 3600,
        ts: 900,
        genres: [10, 99],
        media_stream: {
          progress: 25,
          resolutions: ['2160p'],
          audio_type: 'DTS'
        }
      }
      const summary = mapper().summary(item)
      const latest = mapper().latest(item)

      expect(summary.ref.kind).assertEqual(MediaRefKind.Episode)
      expect(summary.seriesRef?.id).assertEqual('series-1')
      expect(summary.seasonRef?.id).assertEqual('season-1')
      expect(summary.durationMs).assertEqual(3600000)
      expect(summary.communityRating).assertEqual(8.6)
      expect(summary.genres.join(',')).assertEqual('剧情')
      expect(summary.tags.join(',')).assertEqual('2160P,DTS')
      expect(summary.userState.watched.playbackPositionMs).assertEqual(900000)
      expect(summary.userState.watched.playedPercentage).assertEqual(25)
      expect(latest.ref.kind).assertEqual(MediaRefKind.Series)
      expect(latest.ref.id).assertEqual('series-1')
      expect(latest.title).assertEqual('Series One')
    })

    it('mapsDetailSeasonEpisodeAndPersonCreditsWithoutLeakingGenreIds', 0, () => {
      const owner = session()
      const catalogMapper = mapper(owner)
      const seriesRef = new MediaRef(owner.provider, owner.accountScope, 'series-1', MediaRefKind.Series)
      const seasonItem: FnMediaSeasonItem = {
        guid: 'season-1',
        title: 'Season 1',
        type: 'Season',
        season_number: 1,
        number_of_episodes: 10,
        first_air_date: '2026-03-18',
        vote_average: '8.4',
        poster: '/season.jpg'
      }
      const season = catalogMapper.season(seasonItem, seriesRef)
      const detail: FnMediaItemDetailResp = {
        guid: 'series-1',
        title: 'Series One',
        original_title: 'Original Series',
        type: 'TV',
        overview: 'Overview',
        vote_average: '9.1',
        genres: [20, 999],
        backdrops: '/backdrop.jpg',
        posters: '/poster.jpg',
        logos: '/logo.png',
        imdb_id: 'tt1234567',
        is_favorite: 1
      }
      const people: FnMediaPersonListItem[] = [{
        person_guid: 'person-1',
        name: 'Actor One',
        job: 'Actor',
        role: 'Hero',
        profile_path: '/actor.jpg'
      }]
      const mappedDetail = catalogMapper.detail(detail, people, [season])
      const episodeItem: FnMediaEpisodeItem = {
        guid: 'episode-1',
        title: 'Pilot',
        type: 'Episode',
        season_number: 1,
        episode_number: 1,
        runtime: 45
      }
      const episode = catalogMapper.episode(episodeItem, seriesRef, season.ref)

      expect(mappedDetail.originalTitle).assertEqual('Original Series')
      expect(mappedDetail.genres.join(',')).assertEqual('科幻')
      expect(mappedDetail.genres.indexOf('999') >= 0).assertFalse()
      expect(mappedDetail.personCredits[0].kind).assertEqual(MediaPersonKind.Actor)
      expect(mappedDetail.personCredits[0].role).assertEqual('Hero')
      expect(mappedDetail.seasons[0].episodeCount).assertEqual(10)
      expect(mappedDetail.seasons[0].productionYear).assertEqual(2026)
      expect(mappedDetail.seasons[0].communityRating).assertEqual(8.4)
      expect(mappedDetail.externalLinks[0].url.indexOf('tt1234567') >= 0).assertTrue()
      expect(episode.durationMs).assertEqual(2700000)
      expect(episode.seriesRef.id).assertEqual('series-1')
    })

    it('recognizesEpisodePlayRecordsByEpisodeNumber', 0, () => {
      const playItem: FnMediaPlayItem = {
        guid: 'episode-8',
        title: 'Episode 8',
        type: 'Video',
        ancestor_guid: 'series-1',
        parent_guid: 'season-1',
        episode_number: 8
      }
      const summary = mapper().resume(playItem)

      expect(summary.ref.kind).assertEqual(MediaRefKind.Episode)
      expect(summary.seriesRef?.id).assertEqual('series-1')
      expect(summary.seasonRef?.id).assertEqual('season-1')
    })

    it('mapsPersonAndAdvancesPagesByRawServerItemCount', 0, () => {
      const catalogMapper = mapper()
      const person = catalogMapper.person({
        guid: 'person-1',
        name: 'Person',
        original_name: 'Original',
        biography: 'Biography',
        profile: '/person.jpg',
        is_favorite: 1
      })
      const page = catalogMapper.page([catalogMapper.summary({
        guid: 'movie-1',
        title: 'Movie',
        type: 'Movie'
      })], 30, 30, 120, 30)

      expect(person.ref.kind).assertEqual(MediaRefKind.Person)
      expect(person.sortName).assertEqual('Original')
      expect(person.userState.favorite.favorite).assertTrue()
      expect(page.items.length).assertEqual(1)
      expect(page.nextStartIndex).assertEqual(60)
      expect(page.totalCount).assertEqual(120)
      expect(page.hasMore).assertTrue()
    })
  })
}
