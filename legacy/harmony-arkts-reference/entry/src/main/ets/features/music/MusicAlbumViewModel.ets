import { BaseViewModel } from '../../common/base/BaseViewModel'
import { MusicAlbumUIState, MusicDetailState } from './MusicAlbumUIState'
import { MusicBrowseErrorCode } from './MusicModels'
import { MusicQueryPlanner } from './MusicQueryPlanner'
import { MusicBrowseRepositoryPort, MusicRepository } from './MusicRepository'

export class MusicAlbumViewModel extends BaseViewModel<MusicAlbumUIState> {
  private repository: MusicBrowseRepositoryPort
  private readonly pageSize: number = 50
  private requestEpoch: number = 0
  private albumId: string = ''
  private activeScope: string = ''

  constructor(ui: MusicAlbumUIState,
    repository: MusicBrowseRepositoryPort = new MusicRepository()) {
    super(ui)
    this.repository = repository || new MusicRepository()
  }

  async load(albumId: string): Promise<void> {
    const normalizedId = albumId.trim()
    const epoch = ++this.requestEpoch
    const requestScope = this.repository.currentScope()
    this.albumId = normalizedId
    this.activeScope = requestScope
    this.ui.album = null
    this.ui.tracks = []
    this.ui.tracksHasMore = false
    this.ui.tracksNextStartIndex = 0
    this.ui.tracksLoadingMore = false
    this.ui.tracksLoadMoreFailed = false
    this.ui.tracksLoadMoreErrorCode = MusicBrowseErrorCode.None
    this.ui.favoritePending = false
    this.ui.errorCode = MusicBrowseErrorCode.None
    if (normalizedId.length === 0) {
      this.ui.state = MusicDetailState.Error
      this.ui.errorCode = MusicBrowseErrorCode.NotFound
      return
    }
    this.ui.state = MusicDetailState.Loading
    try {
      const values = await Promise.all([
        this.repository.getAlbum(normalizedId),
        this.repository.getAlbumTracks(normalizedId, 0, this.pageSize)
      ])
      if (!this.isCurrent(epoch, requestScope)) {
        return
      }
      this.ui.album = values[0]
      this.ui.tracks = MusicQueryPlanner.mergeTracksUnique([], values[1].items)
      this.ui.tracksNextStartIndex = values[1].startIndex + values[1].items.length
      this.ui.tracksHasMore = values[1].hasMore
      this.ui.state = MusicDetailState.Content
      this.ui.errorCode = MusicBrowseErrorCode.None
    } catch (error) {
      if (!this.isCurrent(epoch, requestScope)) {
        return
      }
      console.error('[MusicAlbum] load failed')
      this.ui.errorCode = this.repository.classifyError(error as Object)
      this.ui.state = MusicDetailState.Error
    }
  }

  async loadMoreTracks(): Promise<void> {
    if (this.albumId.length === 0 || this.ui.tracksLoadingMore || !this.ui.tracksHasMore ||
      this.ui.tracksLoadMoreFailed || this.activeScope !== this.repository.currentScope()) {
      return
    }
    const epoch = this.requestEpoch
    const requestScope = this.activeScope
    this.ui.tracksLoadingMore = true
    this.ui.tracksLoadMoreFailed = false
    try {
      const result = await this.repository.getAlbumTracks(
        this.albumId, this.ui.tracksNextStartIndex, this.pageSize)
      if (!this.isCurrent(epoch, requestScope)) {
        return
      }
      this.ui.tracks = MusicQueryPlanner.mergeTracksUnique(this.ui.tracks, result.items)
      this.ui.tracksNextStartIndex = result.startIndex + result.items.length
      this.ui.tracksHasMore = result.hasMore
      this.ui.tracksLoadMoreErrorCode = MusicBrowseErrorCode.None
    } catch (error) {
      if (this.isCurrent(epoch, requestScope)) {
        this.ui.tracksLoadMoreErrorCode = this.repository.classifyError(error as Object)
        this.ui.tracksLoadMoreFailed = true
      }
    } finally {
      if (this.isCurrent(epoch, requestScope)) {
        this.ui.tracksLoadingMore = false
      }
    }
  }

  async retryLoadMoreTracks(): Promise<void> {
    this.ui.tracksLoadMoreFailed = false
    await this.loadMoreTracks()
  }

  async toggleFavorite(): Promise<boolean> {
    const album = this.ui.album
    if (!album || this.ui.favoritePending) {
      return false
    }
    this.ui.favoritePending = true
    const epoch = this.requestEpoch
    const requestScope = this.activeScope
    const albumId = album.id
    try {
      const favorite = await this.repository.setFavorite(album.id, !album.favorite)
      if (!this.isCurrent(epoch, requestScope) || this.albumId !== albumId ||
        this.ui.album?.id !== albumId) {
        return true
      }
      this.ui.album = {
        id: album.id,
        title: album.title,
        artist: album.artist,
        artistId: album.artistId,
        year: album.year,
        imageUrl: album.imageUrl,
        trackCount: album.trackCount,
        durationMs: album.durationMs,
        favorite: favorite,
        overview: album.overview
      }
      return true
    } catch (error) {
      if (!this.isCurrent(epoch, requestScope) || this.albumId !== albumId ||
        this.ui.album?.id !== albumId) {
        return true
      }
      console.error('[MusicAlbum] favorite failed')
      return false
    } finally {
      if (this.isCurrent(epoch, requestScope) && this.albumId === albumId &&
        this.ui.album?.id === albumId) {
        this.ui.favoritePending = false
      }
    }
  }

  private isCurrent(epoch: number, requestScope: string): boolean {
    return epoch === this.requestEpoch && requestScope === this.activeScope &&
      requestScope === this.repository.currentScope()
  }
}
