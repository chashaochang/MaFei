import { describe, expect, it } from '@ohos/hypium'
import { MusicAlbumUIState, MusicDetailState } from '../main/ets/features/music/MusicAlbumUIState'
import { MusicAlbumViewModel } from '../main/ets/features/music/MusicAlbumViewModel'
import { MusicArtistUIState } from '../main/ets/features/music/MusicArtistUIState'
import { MusicArtistViewModel } from '../main/ets/features/music/MusicArtistViewModel'
import {
  MusicAlbum,
  MusicArtist,
  MusicBrowseErrorCode,
  MusicPageResult,
  MusicTrack
} from '../main/ets/features/music/MusicModels'
import { MusicRepository } from '../main/ets/features/music/MusicRepository'

function album(id: string, favorite: boolean = false): MusicAlbum {
  return {
    id: id,
    title: id,
    artist: 'Artist',
    artistId: 'artist-1',
    year: 2026,
    imageUrl: '',
    trackCount: 1,
    durationMs: 1000,
    favorite: favorite,
    overview: ''
  }
}

function artist(id: string): MusicArtist {
  return {
    id: id,
    name: id,
    imageUrl: '',
    albumCount: 1,
    trackCount: 1,
    overview: ''
  }
}

function track(id: string): MusicTrack {
  return {
    id: id,
    title: id,
    artist: 'Artist',
    artistId: 'artist-1',
    album: 'Album',
    albumId: 'album-1',
    discNumber: 1,
    trackNumber: 1,
    durationMs: 1000,
    imageUrl: '',
    favorite: false,
    playbackPositionMs: 0
  }
}

function page<T>(items: T[], hasMore: boolean = false, startIndex: number = 0): MusicPageResult<T> {
  return {
    items: items,
    startIndex: startIndex,
    totalCount: startIndex + (hasMore ? items.length + 1 : items.length),
    hasMore: hasMore
  }
}

class FakeMusicDetailRepository extends MusicRepository {
  scope: string = 'server-a|user-a'
  errorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.RequestFailed
  failAlbumLoad: boolean = false
  failArtistLoad: boolean = false
  failTrackPage: boolean = false
  failArtistAlbumPage: boolean = false
  failFavorite: boolean = false
  deferAlbumCall: number = -1
  deferAlbumTrackCall: number = -1
  deferArtistAlbumCall: number = -1
  deferFavorite: boolean = false
  albumRequestCount: number = 0
  artistAlbumStarts: number[] = []
  albumTrackStarts: number[] = []
  currentAlbum: MusicAlbum = album('album-a')
  currentArtist: MusicArtist = artist('artist-a')
  albumTracks: MusicPageResult<MusicTrack> = page([track('track-a')])
  artistAlbums: MusicPageResult<MusicAlbum> = page([album('album-a')], true)
  private resolveDeferredAlbum?: (value: MusicAlbum) => void
  private resolveDeferredAlbumTracks?: (value: MusicPageResult<MusicTrack>) => void
  private resolveDeferredArtistAlbums?: (value: MusicPageResult<MusicAlbum>) => void
  private resolveDeferredFavorite?: (value: boolean) => void

  currentScope(): string {
    return this.scope
  }

  classifyError(_error: Object): MusicBrowseErrorCode {
    return this.errorCode
  }

  async getAlbum(_albumId: string): Promise<MusicAlbum> {
    this.albumRequestCount += 1
    if (this.albumRequestCount === this.deferAlbumCall) {
      return new Promise<MusicAlbum>((resolve: (value: MusicAlbum) => void) => {
        this.resolveDeferredAlbum = resolve
      })
    }
    if (this.failAlbumLoad) {
      throw new Error('album failed')
    }
    return this.currentAlbum
  }

  async getAlbumTracks(_albumId: string, startIndex: number = 0,
    _limit: number = 50): Promise<MusicPageResult<MusicTrack>> {
    this.albumTrackStarts.push(startIndex)
    if (this.albumTrackStarts.length === this.deferAlbumTrackCall) {
      return new Promise<MusicPageResult<MusicTrack>>(
        (resolve: (value: MusicPageResult<MusicTrack>) => void) => {
          this.resolveDeferredAlbumTracks = resolve
        })
    }
    if (this.failTrackPage) {
      this.failTrackPage = false
      throw new Error('tracks failed')
    }
    return this.albumTracks
  }

  async getArtist(_artistId: string): Promise<MusicArtist> {
    if (this.failArtistLoad) {
      throw new Error('artist failed')
    }
    return this.currentArtist
  }

  async getArtistTracks(_artistId: string, _limit: number = 12): Promise<MusicTrack[]> {
    return [track('popular-a')]
  }

  async getArtistAlbums(_artistId: string, startIndex: number = 0,
    _limit: number = 40): Promise<MusicPageResult<MusicAlbum>> {
    this.artistAlbumStarts.push(startIndex)
    if (this.artistAlbumStarts.length === this.deferArtistAlbumCall) {
      return new Promise<MusicPageResult<MusicAlbum>>(
        (resolve: (value: MusicPageResult<MusicAlbum>) => void) => {
          this.resolveDeferredArtistAlbums = resolve
        })
    }
    if (this.failArtistAlbumPage) {
      this.failArtistAlbumPage = false
      throw new Error('artist albums failed')
    }
    return this.artistAlbums
  }

  async setFavorite(_itemId: string, favorite: boolean): Promise<boolean> {
    if (this.deferFavorite) {
      return new Promise<boolean>((resolve: (value: boolean) => void) => {
        this.resolveDeferredFavorite = resolve
      })
    }
    if (this.failFavorite) {
      throw new Error('favorite failed')
    }
    return favorite
  }

  resolveAlbum(value: MusicAlbum): void {
    this.resolveDeferredAlbum?.(value)
  }

  resolveAlbumTracks(value: MusicPageResult<MusicTrack>): void {
    this.resolveDeferredAlbumTracks?.(value)
  }

  resolveArtistAlbums(value: MusicPageResult<MusicAlbum>): void {
    this.resolveDeferredArtistAlbums?.(value)
  }

  resolveFavorite(value: boolean): void {
    this.resolveDeferredFavorite?.(value)
  }
}

export default function musicDetailStateTest() {
  describe('MusicDetailState', () => {
    it('doesNotRequestAnEmptyAlbumId', 0, async () => {
      const repository = new FakeMusicDetailRepository()
      const ui = new MusicAlbumUIState({} as UIContext)
      const vm = new MusicAlbumViewModel(ui, repository)

      await vm.load('   ')

      expect(repository.albumRequestCount).assertEqual(0)
      expect(ui.state).assertEqual(MusicDetailState.Error)
      expect(ui.errorCode).assertEqual(MusicBrowseErrorCode.NotFound)
    })

    it('invalidatesAnInFlightAlbumLoadWhenTheIdBecomesEmpty', 0, async () => {
      const repository = new FakeMusicDetailRepository()
      repository.deferAlbumCall = 1
      const ui = new MusicAlbumUIState({} as UIContext)
      const vm = new MusicAlbumViewModel(ui, repository)

      const oldLoad = vm.load('album-a')
      await Promise.resolve()
      await vm.load('   ')
      repository.resolveAlbum(album('album-a'))
      await oldLoad

      expect(ui.album).assertNull()
      expect(ui.state).assertEqual(MusicDetailState.Error)
      expect(ui.errorCode).assertEqual(MusicBrowseErrorCode.NotFound)
    })

    it('storesTypedAlbumAndArtistLoadErrors', 0, async () => {
      const repository = new FakeMusicDetailRepository()
      repository.failAlbumLoad = true
      repository.errorCode = MusicBrowseErrorCode.PermissionDenied
      const albumUi = new MusicAlbumUIState({} as UIContext)
      await new MusicAlbumViewModel(albumUi, repository).load('album-a')
      expect(albumUi.errorCode).assertEqual(MusicBrowseErrorCode.PermissionDenied)

      repository.failArtistLoad = true
      repository.errorCode = MusicBrowseErrorCode.Offline
      const artistUi = new MusicArtistUIState({} as UIContext)
      await new MusicArtistViewModel(artistUi, repository).load('artist-a')
      expect(artistUi.errorCode).assertEqual(MusicBrowseErrorCode.Offline)
    })

    it('retriesAlbumTrackPaginationWithoutDroppingLoadedTracks', 0, async () => {
      const repository = new FakeMusicDetailRepository()
      repository.albumTracks = page([track('track-a'), track('track-a')], true, 5)
      const ui = new MusicAlbumUIState({} as UIContext)
      const vm = new MusicAlbumViewModel(ui, repository)

      await vm.load('album-a')
      repository.failTrackPage = true
      await vm.loadMoreTracks()
      repository.albumTracks = page([track('track-a'), track('track-b')], false, 9)
      await vm.retryLoadMoreTracks()

      expect(repository.albumTrackStarts.join(',')).assertEqual('0,7,7')
      expect(ui.tracks.length).assertEqual(2)
      expect(ui.tracks[1].id).assertEqual('track-b')
      expect(ui.tracksNextStartIndex).assertEqual(11)
    })

    it('deduplicatesArtistAlbumsWhileFollowingServerCursors', 0, async () => {
      const repository = new FakeMusicDetailRepository()
      repository.artistAlbums = page([album('album-a'), album('album-a')], true, 4)
      const ui = new MusicArtistUIState({} as UIContext)
      const vm = new MusicArtistViewModel(ui, repository)

      await vm.load('artist-a')
      repository.artistAlbums = page([album('album-a'), album('album-b')], false, 8)
      await vm.loadMoreAlbums()

      expect(repository.artistAlbumStarts.join(',')).assertEqual('0,6')
      expect(ui.albums.length).assertEqual(2)
      expect(ui.albums[1].id).assertEqual('album-b')
      expect(ui.albumsNextStartIndex).assertEqual(10)
    })

    it('retriesArtistAlbumPaginationFromTheFailedServerCursor', 0, async () => {
      const repository = new FakeMusicDetailRepository()
      repository.artistAlbums = page([album('album-a')], true, 6)
      const ui = new MusicArtistUIState({} as UIContext)
      const vm = new MusicArtistViewModel(ui, repository)

      await vm.load('artist-a')
      repository.failArtistAlbumPage = true
      await vm.loadMoreAlbums()
      repository.artistAlbums = page([album('album-b')], false, 10)
      await vm.retryLoadMoreAlbums()

      expect(repository.artistAlbumStarts.join(',')).assertEqual('0,7,7')
      expect(ui.albums.length).assertEqual(2)
      expect(ui.albums[1].id).assertEqual('album-b')
      expect(ui.albumsNextStartIndex).assertEqual(11)
    })

    it('loadsANewAlbumWhileThePreviousPageIsStillPending', 0, async () => {
      const repository = new FakeMusicDetailRepository()
      repository.albumTracks = page([track('track-a')], true)
      const ui = new MusicAlbumUIState({} as UIContext)
      const vm = new MusicAlbumViewModel(ui, repository)

      await vm.load('album-a')
      repository.deferAlbumTrackCall = 2
      const oldPage = vm.loadMoreTracks()
      await Promise.resolve()
      repository.currentAlbum = album('album-b')
      repository.albumTracks = page([track('track-b')])
      await vm.load('album-b')
      repository.resolveAlbumTracks(page([track('old-page')]))
      await oldPage

      expect(ui.album?.id).assertEqual('album-b')
      expect(ui.tracksLoadingMore).assertFalse()
      expect(ui.tracks.length).assertEqual(1)
      expect(ui.tracks[0].id).assertEqual('track-b')
    })

    it('keepsTypedErrorsForDetailPagination', 0, async () => {
      const repository = new FakeMusicDetailRepository()
      repository.albumTracks = page([track('track-a')], true)
      const albumUi = new MusicAlbumUIState({} as UIContext)
      const albumVm = new MusicAlbumViewModel(albumUi, repository)
      await albumVm.load('album-a')
      repository.failTrackPage = true
      repository.errorCode = MusicBrowseErrorCode.PermissionDenied
      await albumVm.loadMoreTracks()
      expect(albumUi.tracksLoadMoreErrorCode).assertEqual(MusicBrowseErrorCode.PermissionDenied)

      repository.errorCode = MusicBrowseErrorCode.NotFound
      const artistUi = new MusicArtistUIState({} as UIContext)
      const artistVm = new MusicArtistViewModel(artistUi, repository)
      await artistVm.load('artist-a')
      repository.failArtistAlbumPage = true
      await artistVm.loadMoreAlbums()
      expect(artistUi.albumsLoadMoreErrorCode).assertEqual(MusicBrowseErrorCode.NotFound)
    })

    it('doesNotLoadMoreArtistAlbumsAfterTheAccountChanges', 0, async () => {
      const repository = new FakeMusicDetailRepository()
      const ui = new MusicArtistUIState({} as UIContext)
      const vm = new MusicArtistViewModel(ui, repository)

      await vm.load('artist-a')
      repository.scope = 'server-b|user-b'
      await vm.loadMoreAlbums()

      expect(repository.artistAlbumStarts.join(',')).assertEqual('0')
      expect(ui.albums[0].id).assertEqual('album-a')
    })

    it('keepsTheServerFavoriteWhenTheMutationFails', 0, async () => {
      const repository = new FakeMusicDetailRepository()
      repository.failFavorite = true
      const ui = new MusicAlbumUIState({} as UIContext)
      const vm = new MusicAlbumViewModel(ui, repository)

      await vm.load('album-a')
      expect(await vm.toggleFavorite()).assertFalse()

      expect(ui.album?.favorite).assertFalse()
      expect(ui.favoritePending).assertFalse()
    })

    it('ignoresAnOldFavoriteResponseAfterOpeningAnotherAlbum', 0, async () => {
      const repository = new FakeMusicDetailRepository()
      const ui = new MusicAlbumUIState({} as UIContext)
      const vm = new MusicAlbumViewModel(ui, repository)

      await vm.load('album-a')
      repository.deferFavorite = true
      const favoriteRequest = vm.toggleFavorite()
      await Promise.resolve()
      repository.currentAlbum = album('album-b')
      repository.albumTracks = page([track('track-b')])
      await vm.load('album-b')
      repository.resolveFavorite(true)
      await favoriteRequest

      expect(ui.album?.id).assertEqual('album-b')
      expect(ui.album?.favorite).assertFalse()
      expect(ui.favoritePending).assertFalse()
    })
  })
}
