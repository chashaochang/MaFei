import { describe, expect, it } from '@ohos/hypium'
import {
  BaseItemDto,
  BaseItemKind,
  MediaProtocol,
  MediaStreamType,
  PersonKind
} from 'sdk/src/main/ets/generated-client/models'
import {
  MediaPersonKind,
  MediaStreamKind,
  MediaSummary
} from '../main/ets/media/catalog/MediaCatalogModels'
import { JellyfinCatalogMapper } from '../main/ets/media/jellyfin/JellyfinCatalogMapper'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import { MediaRefKind } from '../main/ets/media/MediaRef'

function session(providerAccountId: string = 'jellyfin-account-1'): MediaProviderSession {
  return new MediaProviderSession({
    provider: MediaProviderKind.Jellyfin,
    providerAccountId: providerAccountId,
    userId: 'user-1',
    originalAddress: 'media.example',
    baseUrl: 'https://media.example'
  })
}

export default function jellyfinCatalogMapperTest() {
  describe('JellyfinCatalogMapper', () => {
    it('keepsGenericVideoKindAndMapsSummaryState', 0, () => {
      const owner = session()
      const mapper = new JellyfinCatalogMapper(owner)
      const item: BaseItemDto = {
        Id: 'video-1',
        Type: BaseItemKind.Video,
        Name: 'Home Video',
        Overview: 'Overview',
        ProductionYear: 2026,
        PremiereDate: '2026-08-20T10:00:00Z',
        RunTimeTicks: 72000000,
        CanDelete: true,
        Path: '/media/home-video.mkv',
        ChildCount: 3,
        Genres: ['Family'],
        MediaStreams: [{
          Type: MediaStreamType.Video,
          Width: 3840,
          Height: 2160
        }],
        UserData: {
          IsFavorite: true,
          Played: true,
          PlaybackPositionTicks: 30000000,
          PlayedPercentage: 42.5
        }
      }

      const summary = mapper.summary(item)

      expect(summary.ref.kind).assertEqual(MediaRefKind.Video)
      expect(summary.ref.belongsTo(owner)).assertTrue()
      expect(summary.title).assertEqual('Home Video')
      expect(summary.durationMs).assertEqual(7200)
      expect(summary.canDelete).assertTrue()
      expect(summary.path).assertEqual('/media/home-video.mkv')
      expect(summary.childCount).assertEqual(3)
      expect(summary.tags[0]).assertEqual('4K')
      expect(summary.userState.favorite.favorite).assertTrue()
      expect(summary.userState.watched.watched).assertTrue()
      expect(summary.userState.watched.playbackPositionMs).assertEqual(3000)
      expect(summary.userState.watched.playedPercentage).assertEqual(42.5)
    })

    it('scopesImageUrlHeadersAndCacheKeyToTheAccount', 0, () => {
      const owner = session()
      const item: BaseItemDto = {
        Id: 'movie-1',
        Type: BaseItemKind.Movie,
        Name: 'Movie',
        ImageTags: { Primary: 'primary-tag' }
      }
      const directMapper = new JellyfinCatalogMapper(
        owner, 'https://direct.example/', 'MediaBrowser Token="token-1"')
      const relayMapper = new JellyfinCatalogMapper(
        owner, 'https://relay.example', 'MediaBrowser Token="token-2"')

      const directImage = directMapper.summary(item).primaryImage
      const relayImage = relayMapper.summary(item).primaryImage

      expect(directImage !== undefined).assertTrue()
      expect(relayImage !== undefined).assertTrue()
      expect(directImage?.url.startsWith('https://direct.example/Items/movie-1/Images/Primary')).assertTrue()
      expect(relayImage?.url.startsWith('https://relay.example/Items/movie-1/Images/Primary')).assertTrue()
      expect(directImage?.headers[0].name).assertEqual('Authorization')
      expect(directImage?.headers[0].value).assertEqual('MediaBrowser Token="token-1"')
      expect(directImage?.accountScope).assertEqual(owner.accountScope)
      expect(directImage?.belongsTo(owner)).assertTrue()
      expect(directImage?.cacheKey).assertEqual(relayImage?.cacheKey)
      expect(directImage?.url === relayImage?.url).assertFalse()
    })

    it('foldsLatestEpisodeIntoItsSeries', 0, () => {
      const owner = session()
      const mapper = new JellyfinCatalogMapper(owner)
      const item: BaseItemDto = {
        Id: 'episode-4',
        Type: BaseItemKind.Episode,
        Name: 'Episode 4',
        SeriesId: 'series-1',
        SeasonId: 'season-1',
        SeriesName: 'Series',
        ParentIndexNumber: 2,
        IndexNumber: 4,
        SeriesPrimaryImageTag: 'series-tag',
        UserData: { Played: false }
      }

      const latest = mapper.latest(item)

      expect(latest.ref.id).assertEqual('series-1')
      expect(latest.ref.kind).assertEqual(MediaRefKind.Series)
      expect(latest.seriesRef?.equals(latest.ref)).assertTrue()
      expect(latest.seasonRef?.id).assertEqual('season-1')
      expect(latest.title).assertEqual('Series')
      expect(latest.subtitle).assertEqual('S2:E4 - Episode 4')
      expect(latest.primaryImage?.owner.equals(latest.ref)).assertTrue()
      expect(latest.primaryImage?.url.includes('/Items/series-1/Images/Primary')).assertTrue()
    })

    it('mapsDetailPeopleMediaStreamsAndExternalLinks', 0, () => {
      const owner = session()
      const mapper = new JellyfinCatalogMapper(
        owner, 'https://media.example', 'MediaBrowser Token="token-1"')
      const item: BaseItemDto = {
        Id: 'movie-1',
        Type: BaseItemKind.Movie,
        Name: 'Movie',
        OriginalTitle: 'Original Movie',
        Taglines: ['Tagline'],
        Overview: 'Overview',
        ProductionYear: 2026,
        RunTimeTicks: 72000000,
        OfficialRating: 'PG-13',
        CommunityRating: 8.2,
        CriticRating: 91,
        Genres: ['Drama'],
        Tags: ['Award Winner'],
        Studios: [{ Name: 'Studio', Id: 'studio-1' }],
        People: [{
          Id: 'person-1',
          Name: 'Actor',
          Type: PersonKind.Actor,
          Role: 'Lead',
          PrimaryImageTag: 'person-tag'
        }, {
          Id: '',
          Name: 'Missing Id',
          Type: PersonKind.Director
        }],
        MediaSources: [{
          Id: 'source-1',
          Name: 'Main',
          Path: '/media/movie.mkv',
          Container: 'mkv',
          Protocol: MediaProtocol.File,
          Size: 4294967296,
          Bitrate: 7000000,
          RunTimeTicks: 72000000,
          MediaStreams: [{
            Index: 0,
            Type: MediaStreamType.Video,
            Title: 'Main video',
            DisplayTitle: '4K HEVC',
            Codec: 'hevc',
            BitRate: 6500000,
            BitDepth: 10,
            Width: 3840,
            Height: 2160,
            AverageFrameRate: 23.976,
            IsExternal: false,
            IsDefault: true,
            IsForced: false
          }, {
            Index: 1,
            Type: MediaStreamType.Audio,
            DisplayTitle: 'Chinese AAC',
            Codec: 'aac',
            Language: 'chi',
            SampleRate: 48000,
            Channels: 2,
            IsExternal: false,
            IsDefault: true,
            IsForced: false
          }, {
            Index: 2,
            Type: MediaStreamType.Subtitle,
            DisplayTitle: 'Chinese ASS',
            Codec: 'ass',
            Language: 'chi',
            IsExternal: true,
            IsDefault: false,
            IsForced: false
          }]
        }],
        ExternalUrls: [{ Name: 'IMDb', Url: 'https://www.imdb.com/title/movie-1' }, {
          Name: 'Empty',
          Url: ''
        }],
        UserData: { IsFavorite: true }
      }

      const detail = mapper.detail(item)

      expect(detail.originalTitle).assertEqual('Original Movie')
      expect(detail.tagline).assertEqual('Tagline')
      expect(detail.durationMs).assertEqual(7200)
      expect(detail.studios[0]).assertEqual('Studio')
      expect(detail.personCredits.length).assertEqual(1)
      expect(detail.personCredits[0].kind).assertEqual(MediaPersonKind.Actor)
      expect(detail.personCredits[0].role).assertEqual('Lead')
      expect(detail.personCredits[0].personRef.belongsTo(owner)).assertTrue()
      expect(detail.personCredits[0].primaryImage?.headers[0].value)
        .assertEqual('MediaBrowser Token="token-1"')
      expect(detail.mediaFiles.length).assertEqual(1)
      expect(detail.mediaFiles[0].durationMs).assertEqual(7200)
      expect(detail.mediaFiles[0].protocol).assertEqual(MediaProtocol.File)
      expect(detail.mediaFiles[0].streams.length).assertEqual(3)
      expect(detail.mediaFiles[0].streams[0].kind).assertEqual(MediaStreamKind.Video)
      expect(detail.mediaFiles[0].streams[0].bitDepth).assertEqual(10)
      expect(detail.mediaFiles[0].streams[1].kind).assertEqual(MediaStreamKind.Audio)
      expect(detail.mediaFiles[0].streams[1].sampleRate).assertEqual(48000)
      expect(detail.mediaFiles[0].streams[2].kind).assertEqual(MediaStreamKind.Subtitle)
      expect(detail.mediaFiles[0].streams[2].external).assertTrue()
      expect(detail.externalLinks.length).assertEqual(1)
      expect(detail.externalLinks[0].title).assertEqual('IMDb')
      expect(detail.userState.favorite.favorite).assertTrue()
    })

    it('mapsPersonWorkCounts', 0, () => {
      const mapper = new JellyfinCatalogMapper(session())
      const person = mapper.person({
        Id: 'person-1',
        Type: BaseItemKind.Person,
        Name: 'Actor',
        SortName: 'Actor, The',
        MovieCount: 3,
        EpisodeCount: 12,
        SeriesCount: 2
      })

      expect(person.ref.kind).assertEqual(MediaRefKind.Person)
      expect(person.movieCount).assertEqual(3)
      expect(person.episodeCount).assertEqual(12)
      expect(person.seriesCount).assertEqual(2)
    })

    it('keepsDetailParentRefsAndDeleteMetadata', 0, () => {
      const owner = session()
      const detail = new JellyfinCatalogMapper(owner).detail({
        Id: 'episode-1',
        Type: BaseItemKind.Episode,
        Name: 'Episode',
        SeriesId: 'series-1',
        SeasonId: 'season-1',
        CanDelete: true,
        Path: '/media/series/episode-1.mkv'
      })

      expect(detail.ref.kind).assertEqual(MediaRefKind.Episode)
      expect(detail.seriesRef?.id).assertEqual('series-1')
      expect(detail.seasonRef?.id).assertEqual('season-1')
      expect(detail.canDelete).assertTrue()
      expect(detail.path).assertEqual('/media/series/episode-1.mkv')
    })

    it('advancesPageByRawSourceItemCount', 0, () => {
      const mapper = new JellyfinCatalogMapper(session())
      const mappedItems: MediaSummary[] = [mapper.summary({
        Id: 'series-1',
        Type: BaseItemKind.Series,
        Name: 'Series'
      })]

      const page = mapper.page(mappedItems, 30, 30, 120, 30)

      expect(page.items.length).assertEqual(1)
      expect(page.startIndex).assertEqual(30)
      expect(page.nextStartIndex).assertEqual(60)
      expect(page.totalCount).assertEqual(120)
      expect(page.hasMore).assertTrue()
    })
  })
}
