import { describe, expect, it } from '@ohos/hypium'
import { MediaCatalogError, MediaCatalogErrorCode } from '../main/ets/media/catalog/MediaCatalogError'
import {
  createMediaUserState,
  MediaDetail,
  MediaEpisode,
  MediaHomeSection,
  MediaHomeSectionKind,
  MediaImageKind,
  MediaImageRef,
  MediaLibrary,
  MediaLibraryKind,
  MediaFileInfo,
  MediaPage,
  MediaPerson,
  MediaPersonCredit,
  MediaPersonKind,
  MediaSeason,
  MediaStreamInfo,
  MediaStreamKind,
  MediaSummary
} from '../main/ets/media/catalog/MediaCatalogModels'
import {
  MediaCatalogSortDirection,
  MediaCatalogSortField,
  MediaListRequest
} from '../main/ets/media/catalog/MediaCatalogProvider'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'

function session(provider: MediaProviderKind, providerAccountId: string): MediaProviderSession {
  return new MediaProviderSession({
    provider: provider,
    providerAccountId: providerAccountId,
    userId: 'user-1',
    originalAddress: 'media.example',
    baseUrl: 'https://media.example'
  })
}

function ref(owner: MediaProviderSession, id: string, kind: MediaRefKind): MediaRef {
  return new MediaRef(owner.provider, owner.accountScope, id, kind)
}

function summary(owner: MediaProviderSession, id: string): MediaSummary {
  return {
    ref: ref(owner, id, MediaRefKind.Movie),
    title: 'Movie',
    subtitle: '2026',
    overview: 'Overview',
    durationMs: 7200000,
    genres: ['Drama'],
    tags: ['4K'],
    userState: createMediaUserState(false, false, 120000, 10)
  }
}

export default function mediaCatalogModelsTest() {
  describe('MediaCatalogModels', () => {
    it('keepsImageRequestsScopedAndCacheKeysIndependentFromResolvedUrls', 0, () => {
      const owner = session(MediaProviderKind.Jellyfin, 'jellyfin-account-1')
      const itemRef = ref(owner, 'movie-1', MediaRefKind.Movie)
      const first = new MediaImageRef({
        owner: itemRef,
        kind: MediaImageKind.Primary,
        url: 'https://lan.example/items/movie-1/image',
        variant: 'poster-600',
        revision: 'tag-1',
        headers: [{ name: 'Authorization', value: 'first-token' }]
      })
      const fallback = new MediaImageRef({
        owner: itemRef,
        kind: MediaImageKind.Primary,
        url: 'https://relay.example/items/movie-1/image',
        variant: 'poster-600',
        revision: 'tag-1',
        headers: [{ name: 'Authorization', value: 'second-token' }]
      })

      expect(first.provider).assertEqual(owner.provider)
      expect(first.accountScope).assertEqual(owner.accountScope)
      expect(first.belongsTo(owner)).assertTrue()
      expect(first.cacheKey).assertEqual(fallback.cacheKey)
      expect(first.url === fallback.url).assertFalse()
      expect(first.headers[0].value === fallback.headers[0].value).assertFalse()
    })

    it('preventsImageCacheCollisionsAcrossProviderAccounts', 0, () => {
      const jellyfin = session(MediaProviderKind.Jellyfin, 'jellyfin-account-1')
      const feiniu = session(MediaProviderKind.FeiniuVideo, 'feiniu-account-2')
      const jellyfinImage = new MediaImageRef({
        owner: ref(jellyfin, 'shared-id', MediaRefKind.Movie),
        kind: MediaImageKind.Backdrop,
        url: 'https://media.example/image',
        variant: 'backdrop-1280'
      })
      const feiniuImage = new MediaImageRef({
        owner: ref(feiniu, 'shared-id', MediaRefKind.Movie),
        kind: MediaImageKind.Backdrop,
        url: 'https://media.example/image',
        variant: 'backdrop-1280'
      })

      expect(jellyfinImage.cacheKey === feiniuImage.cacheKey).assertFalse()
      expect(jellyfinImage.belongsTo(feiniu)).assertFalse()
    })

    it('keepsLibraryHomeAndPageEntriesAsScopedMediaRefs', 0, () => {
      const owner = session(MediaProviderKind.Jellyfin, 'jellyfin-account-1')
      const item = summary(owner, 'movie-1')
      const library: MediaLibrary = {
        ref: ref(owner, 'library-1', MediaRefKind.Library),
        title: 'Movies',
        kind: MediaLibraryKind.Movies,
        itemCount: 10
      }
      const section: MediaHomeSection = {
        id: 'continue',
        title: 'Continue watching',
        kind: MediaHomeSectionKind.ContinueWatching,
        libraryRef: library.ref,
        items: [item]
      }
      const page: MediaPage<MediaSummary> = {
        items: section.items,
        startIndex: 0,
        nextStartIndex: 1,
        totalCount: 10,
        hasMore: true
      }

      expect(library.ref.belongsTo(owner)).assertTrue()
      expect(section.items[0].ref.belongsTo(owner)).assertTrue()
      expect(page.nextStartIndex).assertEqual(1)
      expect(page.hasMore).assertTrue()
    })

    it('keepsSeriesSeasonAndThumbnailReferencesOnSummaries', 0, () => {
      const owner = session(MediaProviderKind.Jellyfin, 'jellyfin-account-1')
      const seriesRef = ref(owner, 'series-1', MediaRefKind.Series)
      const seasonRef = ref(owner, 'season-1', MediaRefKind.Season)
      const episodeRef = ref(owner, 'episode-1', MediaRefKind.Episode)
      const thumbnail = new MediaImageRef({
        owner: episodeRef,
        kind: MediaImageKind.Thumbnail,
        url: 'https://media.example/episode-1/thumb',
        variant: 'thumb-600'
      })
      const episode: MediaSummary = {
        ref: episodeRef,
        seriesRef: seriesRef,
        seasonRef: seasonRef,
        title: 'Episode 1',
        subtitle: 'Series S01E01',
        overview: '',
        durationMs: 2700000,
        seriesTitle: 'Series',
        seasonNumber: 1,
        episodeNumber: 1,
        genres: [],
        tags: [],
        thumbnailImage: thumbnail,
        userState: createMediaUserState()
      }

      expect(episode.seriesRef?.equals(seriesRef)).assertTrue()
      expect(episode.seasonRef?.equals(seasonRef)).assertTrue()
      expect(episode.thumbnailImage?.belongsTo(owner)).assertTrue()
    })

    it('expressesAllSelectableSortsAndExistingListFlags', 0, () => {
      const owner = session(MediaProviderKind.Jellyfin, 'jellyfin-account-1')
      const selectableSorts: MediaCatalogSortField[] = [
        MediaCatalogSortField.Name,
        MediaCatalogSortField.DateCreated,
        MediaCatalogSortField.PremiereDate,
        MediaCatalogSortField.DatePlayed,
        MediaCatalogSortField.CommunityRating,
        MediaCatalogSortField.CriticRating,
        MediaCatalogSortField.ProductionYear,
        MediaCatalogSortField.PlayCount,
        MediaCatalogSortField.Runtime,
        MediaCatalogSortField.OfficialRating,
        MediaCatalogSortField.Random,
        MediaCatalogSortField.Album,
        MediaCatalogSortField.Artist,
        MediaCatalogSortField.AlbumArtist,
        MediaCatalogSortField.AiredEpisodeOrder,
        MediaCatalogSortField.SeriesSortName,
        MediaCatalogSortField.Studio,
        MediaCatalogSortField.IsFavoriteOrLiked,
        MediaCatalogSortField.DateLastContentAdded,
        MediaCatalogSortField.IsUnplayed,
        MediaCatalogSortField.IsPlayed
      ]
      const request: MediaListRequest = {
        libraryRef: ref(owner, 'library-1', MediaRefKind.Library),
        personRef: ref(owner, 'person-1', MediaRefKind.Person),
        kinds: [MediaRefKind.Movie, MediaRefKind.Series],
        startIndex: 0,
        limit: 100,
        sortFields: selectableSorts,
        sortDirections: [MediaCatalogSortDirection.Descending],
        recursive: true,
        collapseBoxSetItems: false,
        favoriteOnly: false
      }

      expect(selectableSorts.length).assertEqual(21)
      expect(request.personRef?.belongsTo(owner)).assertTrue()
      expect(request.recursive).assertTrue()
      expect(request.collapseBoxSetItems).assertFalse()
    })

    it('representsDetailSeasonEpisodeAndPersonWithoutProviderDtos', 0, () => {
      const owner = session(MediaProviderKind.Jellyfin, 'jellyfin-account-1')
      const seriesRef = ref(owner, 'series-1', MediaRefKind.Series)
      const seasonRef = ref(owner, 'season-1', MediaRefKind.Season)
      const person: MediaPerson = {
        ref: ref(owner, 'person-1', MediaRefKind.Person),
        name: 'Actor',
        sortName: 'Actor',
        overview: '',
        kind: MediaPersonKind.Actor,
        role: 'Lead',
        userState: createMediaUserState()
      }
      const credit: MediaPersonCredit = {
        personRef: person.ref,
        name: person.name,
        kind: person.kind,
        role: person.role
      }
      const videoStream: MediaStreamInfo = {
        index: 0,
        kind: MediaStreamKind.Video,
        title: 'Main video',
        displayTitle: '1080p H264',
        codec: 'h264',
        language: '',
        bitrate: 7000000,
        bitDepth: 8,
        width: 1920,
        height: 1080,
        external: false,
        default: true,
        forced: false
      }
      const mediaFile: MediaFileInfo = {
        ownerRef: seriesRef,
        id: 'source-1',
        name: 'Series S01E01',
        path: '/media/series/s01e01.mkv',
        container: 'mkv',
        protocol: 'file',
        sizeBytes: 4294967296,
        bitrate: 7000000,
        durationMs: 2700000,
        streams: [videoStream]
      }
      const season: MediaSeason = {
        ref: seasonRef,
        seriesRef: seriesRef,
        title: 'Season 1',
        overview: '',
        seasonNumber: 1,
        episodeCount: 8,
        userState: createMediaUserState()
      }
      const episode: MediaEpisode = {
        ref: ref(owner, 'episode-1', MediaRefKind.Episode),
        seriesRef: seriesRef,
        seasonRef: seasonRef,
        title: 'Episode 1',
        seriesTitle: 'Series',
        overview: '',
        seasonNumber: 1,
        episodeNumber: 1,
        durationMs: 2700000,
        tags: [],
        userState: createMediaUserState(false, true)
      }
      const detail: MediaDetail = {
        ref: seriesRef,
        title: 'Series',
        originalTitle: 'Series',
        tagline: '',
        overview: 'Overview',
        productionYear: 2026,
        durationMs: 2700000,
        officialRating: 'PG-13',
        genres: ['Drama'],
        tags: [],
        studios: [],
        personCredits: [credit],
        seasons: [season],
        mediaFiles: [mediaFile],
        externalLinks: [],
        userState: createMediaUserState(true, false)
      }

      expect(detail.ref.belongsTo(owner)).assertTrue()
      expect(detail.personCredits[0].personRef.belongsTo(owner)).assertTrue()
      expect(detail.seasons[0].ref.belongsTo(owner)).assertTrue()
      expect(detail.mediaFiles[0].ownerRef.belongsTo(owner)).assertTrue()
      expect(detail.mediaFiles[0].streams[0].bitDepth).assertEqual(8)
      expect(episode.ref.belongsTo(owner)).assertTrue()
      expect(episode.userState.watched.watched).assertTrue()
      expect(detail.userState.favorite.favorite).assertTrue()
    })

    it('keepsCatalogErrorsInsideTheRequestAccountScope', 0, () => {
      const owner = session(MediaProviderKind.Jellyfin, 'jellyfin-account-1')
      const offline = new MediaCatalogError({
        code: MediaCatalogErrorCode.Offline,
        message: 'Server is offline',
        accountScope: owner.accountScope,
        operation: 'getLibraries'
      })
      const expired = new MediaCatalogError({
        code: MediaCatalogErrorCode.AuthenticationExpired,
        message: 'Session expired',
        accountScope: owner.accountScope,
        operation: 'getDetail'
      })

      expect(offline.accountScope).assertEqual(owner.accountScope)
      expect(offline.retryable).assertTrue()
      expect(offline.isAuthenticationExpired()).assertFalse()
      expect(expired.retryable).assertFalse()
      expect(expired.isAuthenticationExpired()).assertTrue()
    })
  })
}
