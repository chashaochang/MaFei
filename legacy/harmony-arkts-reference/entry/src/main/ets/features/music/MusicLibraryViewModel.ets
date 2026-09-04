import { BaseViewModel } from '../../common/base/BaseViewModel'
import {
  MusicContentState,
  MusicLibraryUIState
} from './MusicLibraryUIState'
import { MusicBrowseErrorCode, MusicLibrarySection } from './MusicModels'
import { MusicQueryPlanner } from './MusicQueryPlanner'
import { MusicBrowseRepositoryPort, MusicRepository } from './MusicRepository'

enum MusicLoadMode {
  Initial,
  Refresh,
  More
}

export class MusicLibraryViewModel extends BaseViewModel<MusicLibraryUIState> {
  private repository: MusicBrowseRepositoryPort
  private readonly pageSize: number = 40
  private albumsEpoch: number = 0
  private artistsEpoch: number = 0
  private tracksEpoch: number = 0
  private albumsInitialized: boolean = false
  private artistsInitialized: boolean = false
  private tracksInitialized: boolean = false
  private activeScope: string = ''

  constructor(ui: MusicLibraryUIState,
    repository: MusicBrowseRepositoryPort = new MusicRepository()) {
    super(ui)
    this.repository = repository || new MusicRepository()
  }

  init(libraryId: string, libraryName: string): void {
    const normalizedId = libraryId.trim()
    const requestScope = this.repository.currentScope()
    if (normalizedId.length === 0) {
      this.resetAll()
      this.activeScope = requestScope
      this.ui.libraryId = ''
      this.ui.libraryName = libraryName
      this.ui.albumsState = MusicContentState.Error
      this.ui.albumsErrorCode = MusicBrowseErrorCode.NotFound
      return
    }
    if (this.ui.libraryId !== normalizedId || this.activeScope !== requestScope) {
      this.resetAll()
    }
    this.activeScope = requestScope
    this.ui.libraryId = normalizedId
    this.ui.libraryName = libraryName
    this.loadCurrent(MusicLoadMode.Initial)
  }

  selectSection(index: number): void {
    this.ui.selectedSection = this.sectionForIndex(index)
    this.loadCurrent(MusicLoadMode.Initial)
  }

  refreshCurrent(): void {
    this.loadCurrent(MusicLoadMode.Refresh)
  }

  loadMoreCurrent(): void {
    this.loadCurrent(MusicLoadMode.More)
  }

  retryLoadMoreCurrent(): void {
    switch (this.ui.selectedSection) {
      case MusicLibrarySection.Artists:
        this.ui.artistsLoadMoreFailed = false
        break
      case MusicLibrarySection.Tracks:
        this.ui.tracksLoadMoreFailed = false
        break
      case MusicLibrarySection.Albums:
      default:
        this.ui.albumsLoadMoreFailed = false
        break
    }
    this.loadCurrent(MusicLoadMode.More)
  }

  retryCurrent(): void {
    this.loadCurrent(MusicLoadMode.Initial)
  }

  saveScrollOffset(section: MusicLibrarySection, offset: number): void {
    const normalizedOffset = Number.isFinite(offset) ? Math.max(0, offset) : 0
    switch (section) {
      case MusicLibrarySection.Artists:
        this.ui.artistsScrollOffset = normalizedOffset
        break
      case MusicLibrarySection.Tracks:
        this.ui.tracksScrollOffset = normalizedOffset
        break
      case MusicLibrarySection.Albums:
      default:
        this.ui.albumsScrollOffset = normalizedOffset
        break
    }
  }

  private loadCurrent(mode: MusicLoadMode): void {
    if (this.ui.libraryId.length === 0) {
      return
    }
    const currentScope = this.repository.currentScope()
    if (this.activeScope !== currentScope) {
      this.resetAll()
      this.activeScope = currentScope
      mode = MusicLoadMode.Initial
    }
    switch (this.ui.selectedSection) {
      case MusicLibrarySection.Artists:
        this.loadArtists(mode)
        break
      case MusicLibrarySection.Tracks:
        this.loadTracks(mode)
        break
      case MusicLibrarySection.Albums:
      default:
        this.loadAlbums(mode)
        break
    }
  }

  private async loadAlbums(mode: MusicLoadMode): Promise<void> {
    if (mode === MusicLoadMode.Initial && this.albumsInitialized) {
      return
    }
    if (this.ui.albumsRefreshing || this.ui.albumsLoadingMore ||
      (mode === MusicLoadMode.More && (!this.ui.albumsHasMore || this.ui.albumsLoadMoreFailed))) {
      return
    }
    const epoch = ++this.albumsEpoch
    const requestScope = this.activeScope
    const startIndex = mode === MusicLoadMode.More ? this.ui.albumsNextStartIndex : 0
    this.beginAlbums(mode)
    try {
      const result = await this.repository.getAlbums(this.ui.libraryId, startIndex, this.pageSize)
      if (!this.isCurrent(epoch, this.albumsEpoch, requestScope)) {
        return
      }
      this.ui.albums = mode === MusicLoadMode.More ?
        MusicQueryPlanner.mergeAlbumsUnique(this.ui.albums, result.items) :
        MusicQueryPlanner.mergeAlbumsUnique([], result.items)
      this.ui.albumsNextStartIndex = result.startIndex + result.items.length
      this.ui.albumsHasMore = result.hasMore
      this.ui.albumsLoadMoreFailed = false
      this.ui.albumsErrorCode = MusicBrowseErrorCode.None
      this.ui.albumsState = this.ui.albums.length === 0 ? MusicContentState.Empty : MusicContentState.Content
      this.albumsInitialized = true
    } catch (error) {
      if (!this.isCurrent(epoch, this.albumsEpoch, requestScope)) {
        return
      }
      console.error('[MusicLibrary] load albums failed')
      this.ui.albumsErrorCode = this.repository.classifyError(error as Object)
      if (mode === MusicLoadMode.More && this.ui.albums.length > 0) {
        this.ui.albumsLoadMoreFailed = true
      } else if (this.ui.albums.length === 0) {
        this.ui.albumsState = MusicContentState.Error
      }
    } finally {
      if (this.isCurrent(epoch, this.albumsEpoch, requestScope)) {
        this.ui.albumsRefreshing = false
        this.ui.albumsLoadingMore = false
      }
    }
  }

  private async loadArtists(mode: MusicLoadMode): Promise<void> {
    if (mode === MusicLoadMode.Initial && this.artistsInitialized) {
      return
    }
    if (this.ui.artistsRefreshing || this.ui.artistsLoadingMore ||
      (mode === MusicLoadMode.More && (!this.ui.artistsHasMore || this.ui.artistsLoadMoreFailed))) {
      return
    }
    const epoch = ++this.artistsEpoch
    const requestScope = this.activeScope
    const startIndex = mode === MusicLoadMode.More ? this.ui.artistsNextStartIndex : 0
    this.beginArtists(mode)
    try {
      const result = await this.repository.getArtists(this.ui.libraryId, startIndex, this.pageSize)
      if (!this.isCurrent(epoch, this.artistsEpoch, requestScope)) {
        return
      }
      this.ui.artists = mode === MusicLoadMode.More ?
        MusicQueryPlanner.mergeArtistsUnique(this.ui.artists, result.items) :
        MusicQueryPlanner.mergeArtistsUnique([], result.items)
      this.ui.artistsNextStartIndex = result.startIndex + result.items.length
      this.ui.artistsHasMore = result.hasMore
      this.ui.artistsLoadMoreFailed = false
      this.ui.artistsErrorCode = MusicBrowseErrorCode.None
      this.ui.artistsState = this.ui.artists.length === 0 ? MusicContentState.Empty : MusicContentState.Content
      this.artistsInitialized = true
    } catch (error) {
      if (!this.isCurrent(epoch, this.artistsEpoch, requestScope)) {
        return
      }
      console.error('[MusicLibrary] load artists failed')
      this.ui.artistsErrorCode = this.repository.classifyError(error as Object)
      if (mode === MusicLoadMode.More && this.ui.artists.length > 0) {
        this.ui.artistsLoadMoreFailed = true
      } else if (this.ui.artists.length === 0) {
        this.ui.artistsState = MusicContentState.Error
      }
    } finally {
      if (this.isCurrent(epoch, this.artistsEpoch, requestScope)) {
        this.ui.artistsRefreshing = false
        this.ui.artistsLoadingMore = false
      }
    }
  }

  private async loadTracks(mode: MusicLoadMode): Promise<void> {
    if (mode === MusicLoadMode.Initial && this.tracksInitialized) {
      return
    }
    if (this.ui.tracksRefreshing || this.ui.tracksLoadingMore ||
      (mode === MusicLoadMode.More && (!this.ui.tracksHasMore || this.ui.tracksLoadMoreFailed))) {
      return
    }
    const epoch = ++this.tracksEpoch
    const requestScope = this.activeScope
    const startIndex = mode === MusicLoadMode.More ? this.ui.tracksNextStartIndex : 0
    this.beginTracks(mode)
    try {
      const result = await this.repository.getTracks(this.ui.libraryId, startIndex, this.pageSize)
      if (!this.isCurrent(epoch, this.tracksEpoch, requestScope)) {
        return
      }
      this.ui.tracks = mode === MusicLoadMode.More ?
        MusicQueryPlanner.mergeTracksUnique(this.ui.tracks, result.items) :
        MusicQueryPlanner.mergeTracksUnique([], result.items)
      this.ui.tracksNextStartIndex = result.startIndex + result.items.length
      this.ui.tracksHasMore = result.hasMore
      this.ui.tracksLoadMoreFailed = false
      this.ui.tracksErrorCode = MusicBrowseErrorCode.None
      this.ui.tracksState = this.ui.tracks.length === 0 ? MusicContentState.Empty : MusicContentState.Content
      this.tracksInitialized = true
    } catch (error) {
      if (!this.isCurrent(epoch, this.tracksEpoch, requestScope)) {
        return
      }
      console.error('[MusicLibrary] load tracks failed')
      this.ui.tracksErrorCode = this.repository.classifyError(error as Object)
      if (mode === MusicLoadMode.More && this.ui.tracks.length > 0) {
        this.ui.tracksLoadMoreFailed = true
      } else if (this.ui.tracks.length === 0) {
        this.ui.tracksState = MusicContentState.Error
      }
    } finally {
      if (this.isCurrent(epoch, this.tracksEpoch, requestScope)) {
        this.ui.tracksRefreshing = false
        this.ui.tracksLoadingMore = false
      }
    }
  }

  private beginAlbums(mode: MusicLoadMode): void {
    if (mode === MusicLoadMode.Initial && !this.albumsInitialized) {
      this.ui.albumsState = MusicContentState.Loading
    } else if (mode === MusicLoadMode.Refresh) {
      this.ui.albumsRefreshing = true
    } else if (mode === MusicLoadMode.More) {
      this.ui.albumsLoadingMore = true
      this.ui.albumsLoadMoreFailed = false
    }
  }

  private beginArtists(mode: MusicLoadMode): void {
    if (mode === MusicLoadMode.Initial && !this.artistsInitialized) {
      this.ui.artistsState = MusicContentState.Loading
    } else if (mode === MusicLoadMode.Refresh) {
      this.ui.artistsRefreshing = true
    } else if (mode === MusicLoadMode.More) {
      this.ui.artistsLoadingMore = true
      this.ui.artistsLoadMoreFailed = false
    }
  }

  private beginTracks(mode: MusicLoadMode): void {
    if (mode === MusicLoadMode.Initial && !this.tracksInitialized) {
      this.ui.tracksState = MusicContentState.Loading
    } else if (mode === MusicLoadMode.Refresh) {
      this.ui.tracksRefreshing = true
    } else if (mode === MusicLoadMode.More) {
      this.ui.tracksLoadingMore = true
      this.ui.tracksLoadMoreFailed = false
    }
  }

  private sectionForIndex(index: number): MusicLibrarySection {
    if (index === 1) {
      return MusicLibrarySection.Artists
    }
    if (index === 2) {
      return MusicLibrarySection.Tracks
    }
    return MusicLibrarySection.Albums
  }

  private isCurrent(epoch: number, currentEpoch: number, requestScope: string): boolean {
    return epoch === currentEpoch && requestScope === this.activeScope &&
      requestScope === this.repository.currentScope()
  }

  private resetAll(): void {
    this.albumsEpoch++
    this.artistsEpoch++
    this.tracksEpoch++
    this.albumsInitialized = false
    this.artistsInitialized = false
    this.tracksInitialized = false
    this.ui.selectedSection = MusicLibrarySection.Albums
    this.ui.albums = []
    this.ui.artists = []
    this.ui.tracks = []
    this.ui.albumsState = MusicContentState.Loading
    this.ui.artistsState = MusicContentState.Loading
    this.ui.tracksState = MusicContentState.Loading
    this.ui.albumsHasMore = true
    this.ui.artistsHasMore = true
    this.ui.tracksHasMore = true
    this.ui.albumsNextStartIndex = 0
    this.ui.artistsNextStartIndex = 0
    this.ui.tracksNextStartIndex = 0
    this.ui.albumsRefreshing = false
    this.ui.artistsRefreshing = false
    this.ui.tracksRefreshing = false
    this.ui.albumsLoadingMore = false
    this.ui.artistsLoadingMore = false
    this.ui.tracksLoadingMore = false
    this.ui.albumsLoadMoreFailed = false
    this.ui.artistsLoadMoreFailed = false
    this.ui.tracksLoadMoreFailed = false
    this.ui.albumsErrorCode = MusicBrowseErrorCode.None
    this.ui.artistsErrorCode = MusicBrowseErrorCode.None
    this.ui.tracksErrorCode = MusicBrowseErrorCode.None
    this.ui.albumsScrollOffset = 0
    this.ui.artistsScrollOffset = 0
    this.ui.tracksScrollOffset = 0
  }
}
