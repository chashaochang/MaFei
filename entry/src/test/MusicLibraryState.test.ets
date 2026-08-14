import { describe, expect, it } from '@ohos/hypium'
import { MusicLibraryUIState, MusicContentState } from '../main/ets/features/music/MusicLibraryUIState'
import { MusicLibraryViewModel } from '../main/ets/features/music/MusicLibraryViewModel'
import {
  MusicAlbum,
  MusicArtist,
  MusicBrowseErrorCode,
  MusicLibrarySection,
  MusicPageResult,
  MusicTrack
} from '../main/ets/features/music/MusicModels'
import { MusicRepository } from '../main/ets/features/music/MusicRepository'

function album(id: string): MusicAlbum {
  return {
    id: id,
    title: id,
    artist: 'Artist',
    artistId: 'artist-1',
    year: 2026,
    imageUrl: '',
    trackCount: 1,
    durationMs: 1000,
    favorite: false,
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

async function settle(): Promise<void> {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}

class FakeMusicLibraryRepository extends MusicRepository {
  scope: string = 'server-a|user-a'
  errorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.RequestFailed
  failAlbums: boolean = false
  failArtists: boolean = false
  failTracks: boolean = false
  albums: MusicPageResult<MusicAlbum> = page([album('album-a')])
  artists: MusicPageResult<MusicArtist> = page([artist('artist-a')])
  tracks: MusicPageResult<MusicTrack> = page([track('track-a')])
  albumStarts: number[] = []
  artistStarts: number[] = []
  trackStarts: number[] = []
  deferFirstAlbums: boolean = false
  deferAlbumCall: number = -1
  private albumCallCount: number = 0
  private resolveFirstAlbums?: (result: MusicPageResult<MusicAlbum>) => void

  currentScope(): string {
    return this.scope
  }

  classifyError(_error: Object): MusicBrowseErrorCode {
    return this.errorCode
  }

  async getAlbums(_libraryId: string, startIndex: number = 0,
    _limit: number = 40): Promise<MusicPageResult<MusicAlbum>> {
    this.albumStarts.push(startIndex)
    this.albumCallCount += 1
    if ((this.deferFirstAlbums && this.albumCallCount === 1) ||
      this.albumCallCount === this.deferAlbumCall) {
      return new Promise<MusicPageResult<MusicAlbum>>(
        (resolve: (result: MusicPageResult<MusicAlbum>) => void) => {
          this.resolveFirstAlbums = resolve
        })
    }
    if (this.failAlbums) {
      this.failAlbums = false
      throw new Error('albums failed')
    }
    return this.albums
  }

  async getArtists(_libraryId: string, startIndex: number = 0,
    _limit: number = 40): Promise<MusicPageResult<MusicArtist>> {
    this.artistStarts.push(startIndex)
    if (this.failArtists) {
      this.failArtists = false
      throw new Error('artists failed')
    }
    return this.artists
  }

  async getTracks(_libraryId: string, startIndex: number = 0,
    _limit: number = 40): Promise<MusicPageResult<MusicTrack>> {
    this.trackStarts.push(startIndex)
    if (this.failTracks) {
      this.failTracks = false
      throw new Error('tracks failed')
    }
    return this.tracks
  }

  resolveDeferredAlbums(result: MusicPageResult<MusicAlbum>): void {
    this.resolveFirstAlbums?.(result)
  }
}

export default function musicLibraryStateTest() {
  describe('MusicLibraryState', () => {
    it('rejectsAlbumsFromThePreviousAccountScope', 0, async () => {
      const repository = new FakeMusicLibraryRepository()
      repository.deferFirstAlbums = true
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.init('library-a', 'Music A')
      repository.scope = 'server-b|user-b'
      repository.albums = page([album('new')])
      vm.init('library-a', 'Music B')
      await settle()
      repository.resolveDeferredAlbums(page([album('old')]))
      await settle()

      expect(ui.albums.length).assertEqual(1)
      expect(ui.albums[0].id).assertEqual('new')
    })

    it('invalidatesAnInFlightRequestWhenTheLibraryIdBecomesEmpty', 0, async () => {
      const repository = new FakeMusicLibraryRepository()
      repository.deferFirstAlbums = true
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.init('library-a', 'Music')
      vm.init('   ', 'Missing')
      repository.resolveDeferredAlbums(page([album('old')]))
      await settle()

      expect(ui.libraryId).assertEqual('')
      expect(ui.albums.length).assertEqual(0)
      expect(ui.albumsState).assertEqual(MusicContentState.Error)
      expect(ui.albumsErrorCode).assertEqual(MusicBrowseErrorCode.NotFound)
    })

    it('loadsANewLibraryWhileThePreviousRefreshIsStillPending', 0, async () => {
      const repository = new FakeMusicLibraryRepository()
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.init('library-a', 'Music A')
      await settle()
      repository.deferAlbumCall = 2
      vm.refreshCurrent()
      await settle()
      expect(ui.albumsRefreshing).assertTrue()

      repository.albums = page([album('new')])
      vm.init('library-b', 'Music B')
      await settle()
      repository.resolveDeferredAlbums(page([album('old-refresh')]))
      await settle()

      expect(ui.albumsRefreshing).assertFalse()
      expect(ui.albums.length).assertEqual(1)
      expect(ui.albums[0].id).assertEqual('new')
    })

    it('keepsLoadedAlbumsWhenTheArtistTabLoads', 0, async () => {
      const repository = new FakeMusicLibraryRepository()
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.init('library-a', 'Music')
      await settle()
      vm.selectSection(1)
      await settle()

      expect(ui.selectedSection).assertEqual(MusicLibrarySection.Artists)
      expect(ui.albums[0].id).assertEqual('album-a')
      expect(ui.artists[0].id).assertEqual('artist-a')
    })

    it('storesIndependentScrollOffsetsForEveryTab', 0, () => {
      const repository = new FakeMusicLibraryRepository()
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.saveScrollOffset(MusicLibrarySection.Albums, 120)
      vm.saveScrollOffset(MusicLibrarySection.Artists, 240)
      vm.saveScrollOffset(MusicLibrarySection.Tracks, 360)

      expect(ui.albumsScrollOffset).assertEqual(120)
      expect(ui.artistsScrollOffset).assertEqual(240)
      expect(ui.tracksScrollOffset).assertEqual(360)
    })

    it('clampsInvalidScrollOffsets', 0, () => {
      const repository = new FakeMusicLibraryRepository()
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.saveScrollOffset(MusicLibrarySection.Albums, -120)
      vm.saveScrollOffset(MusicLibrarySection.Artists, Number.NaN)

      expect(ui.albumsScrollOffset).assertEqual(0)
      expect(ui.artistsScrollOffset).assertEqual(0)
    })

    it('clearsAllScrollOffsetsWhenTheLibraryChanges', 0, async () => {
      const repository = new FakeMusicLibraryRepository()
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.init('library-a', 'Music A')
      await settle()
      vm.saveScrollOffset(MusicLibrarySection.Albums, 120)
      vm.saveScrollOffset(MusicLibrarySection.Artists, 240)
      vm.saveScrollOffset(MusicLibrarySection.Tracks, 360)
      vm.init('library-b', 'Music B')

      expect(ui.albumsScrollOffset).assertEqual(0)
      expect(ui.artistsScrollOffset).assertEqual(0)
      expect(ui.tracksScrollOffset).assertEqual(0)
    })

    it('clearsAllScrollOffsetsWhenTheAccountScopeChanges', 0, async () => {
      const repository = new FakeMusicLibraryRepository()
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.init('library-a', 'Music')
      await settle()
      vm.saveScrollOffset(MusicLibrarySection.Albums, 120)
      vm.saveScrollOffset(MusicLibrarySection.Artists, 240)
      vm.saveScrollOffset(MusicLibrarySection.Tracks, 360)
      repository.scope = 'server-b|user-b'
      vm.init('library-a', 'Music')

      expect(ui.albumsScrollOffset).assertEqual(0)
      expect(ui.artistsScrollOffset).assertEqual(0)
      expect(ui.tracksScrollOffset).assertEqual(0)
    })

    it('storesTypedErrorsForEachLibrarySection', 0, async () => {
      const repository = new FakeMusicLibraryRepository()
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      repository.failAlbums = true
      repository.errorCode = MusicBrowseErrorCode.PermissionDenied
      vm.init('library-a', 'Music')
      await settle()
      expect(ui.albumsState).assertEqual(MusicContentState.Error)
      expect(ui.albumsErrorCode).assertEqual(MusicBrowseErrorCode.PermissionDenied)

      repository.failArtists = true
      repository.errorCode = MusicBrowseErrorCode.NotFound
      vm.selectSection(1)
      await settle()
      expect(ui.artistsErrorCode).assertEqual(MusicBrowseErrorCode.NotFound)

      repository.failTracks = true
      repository.errorCode = MusicBrowseErrorCode.Offline
      vm.selectSection(2)
      await settle()
      expect(ui.tracksErrorCode).assertEqual(MusicBrowseErrorCode.Offline)
    })

    it('deduplicatesAlbumPagesWhileFollowingServerCursors', 0, async () => {
      const repository = new FakeMusicLibraryRepository()
      repository.albums = page([album('album-a'), album('album-a')], true, 5)
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.init('library-a', 'Music')
      await settle()
      repository.albums = page([album('album-a'), album('album-b')], false, 9)
      vm.loadMoreCurrent()
      await settle()

      expect(repository.albumStarts.join(',')).assertEqual('0,7')
      expect(ui.albums.length).assertEqual(2)
      expect(ui.albums[1].id).assertEqual('album-b')
      expect(ui.albumsNextStartIndex).assertEqual(11)
    })

    it('refreshesFromZeroAndUsesTheReturnedPageCursor', 0, async () => {
      const repository = new FakeMusicLibraryRepository()
      repository.albums = page([album('old')], true, 4)
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.init('library-a', 'Music')
      await settle()
      repository.albums = page([album('fresh'), album('fresh')], true, 20)
      vm.refreshCurrent()
      await settle()

      expect(repository.albumStarts.join(',')).assertEqual('0,0')
      expect(ui.albums.length).assertEqual(1)
      expect(ui.albums[0].id).assertEqual('fresh')
      expect(ui.albumsNextStartIndex).assertEqual(22)
    })

    it('keepsIndependentServerCursorsForArtistsAndTracks', 0, async () => {
      const repository = new FakeMusicLibraryRepository()
      repository.artists = page([artist('artist-a'), artist('artist-a')], true, 12)
      repository.tracks = page([track('track-a')], true, 30)
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.init('library-a', 'Music')
      await settle()
      vm.selectSection(1)
      await settle()
      repository.artists = page([artist('artist-a'), artist('artist-b')], false, 18)
      vm.loadMoreCurrent()
      await settle()
      vm.selectSection(2)
      await settle()
      repository.tracks = page([track('track-a'), track('track-b')], false, 40)
      vm.loadMoreCurrent()
      await settle()

      expect(repository.artistStarts.join(',')).assertEqual('0,14')
      expect(repository.trackStarts.join(',')).assertEqual('0,31')
      expect(ui.artists.length).assertEqual(2)
      expect(ui.artistsNextStartIndex).assertEqual(20)
      expect(ui.tracks.length).assertEqual(2)
      expect(ui.tracksNextStartIndex).assertEqual(42)
    })

    it('retriesAlbumPaginationFromTheFailedOffset', 0, async () => {
      const repository = new FakeMusicLibraryRepository()
      repository.albums = page([album('album-a')], true, 5)
      const ui = new MusicLibraryUIState({} as UIContext)
      const vm = new MusicLibraryViewModel(ui, repository)

      vm.init('library-a', 'Music')
      await settle()
      repository.failAlbums = true
      vm.loadMoreCurrent()
      await settle()
      repository.albums = page([album('album-b')], false, 9)
      vm.retryLoadMoreCurrent()
      await settle()

      expect(repository.albumStarts.join(',')).assertEqual('0,6,6')
      expect(ui.albums.length).assertEqual(2)
      expect(ui.albums[1].id).assertEqual('album-b')
      expect(ui.albumsNextStartIndex).assertEqual(10)
    })
  })
}
