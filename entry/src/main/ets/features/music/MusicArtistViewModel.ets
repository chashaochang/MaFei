import { BaseViewModel } from '../../common/base/BaseViewModel'
import { MusicDetailState } from './MusicAlbumUIState'
import { MusicArtistUIState } from './MusicArtistUIState'
import { MusicBrowseErrorCode } from './MusicModels'
import { MusicQueryPlanner } from './MusicQueryPlanner'
import { MusicBrowseRepositoryPort, MusicRepository } from './MusicRepository'

export class MusicArtistViewModel extends BaseViewModel<MusicArtistUIState> {
  private repository: MusicBrowseRepositoryPort
  private readonly albumPageSize: number = 40
  private artistId: string = ''
  private requestEpoch: number = 0
  private activeScope: string = ''

  constructor(ui: MusicArtistUIState,
    repository: MusicBrowseRepositoryPort = new MusicRepository()) {
    super(ui)
    this.repository = repository || new MusicRepository()
  }

  async load(artistId: string): Promise<void> {
    const normalizedId = artistId.trim()
    this.artistId = normalizedId
    const epoch = ++this.requestEpoch
    const requestScope = this.repository.currentScope()
    this.activeScope = requestScope
    this.ui.artist = null
    this.ui.popularTracks = []
    this.ui.albums = []
    this.ui.albumsHasMore = false
    this.ui.albumsNextStartIndex = 0
    this.ui.albumsLoadingMore = false
    this.ui.albumsLoadMoreFailed = false
    this.ui.albumsLoadMoreErrorCode = MusicBrowseErrorCode.None
    this.ui.errorCode = MusicBrowseErrorCode.None
    if (normalizedId.length === 0) {
      this.ui.state = MusicDetailState.Error
      this.ui.errorCode = MusicBrowseErrorCode.NotFound
      return
    }
    this.ui.state = MusicDetailState.Loading
    try {
      const values = await Promise.all([
        this.repository.getArtist(normalizedId),
        this.repository.getArtistTracks(normalizedId, 12),
        this.repository.getArtistAlbums(normalizedId, 0, this.albumPageSize)
      ])
      if (!this.isCurrent(epoch, requestScope)) {
        return
      }
      this.ui.artist = values[0]
      this.ui.popularTracks = values[1]
      this.ui.albums = MusicQueryPlanner.mergeAlbumsUnique([], values[2].items)
      this.ui.albumsNextStartIndex = values[2].startIndex + values[2].items.length
      this.ui.albumsHasMore = values[2].hasMore
      this.ui.albumsLoadMoreFailed = false
      this.ui.state = MusicDetailState.Content
      this.ui.errorCode = MusicBrowseErrorCode.None
    } catch (error) {
      if (!this.isCurrent(epoch, requestScope)) {
        return
      }
      console.error('[MusicArtist] load failed')
      this.ui.errorCode = this.repository.classifyError(error as Object)
      this.ui.state = MusicDetailState.Error
    }
  }

  async loadMoreAlbums(): Promise<void> {
    if (this.artistId.length === 0 || this.ui.albumsLoadingMore || !this.ui.albumsHasMore ||
      this.ui.albumsLoadMoreFailed || this.activeScope !== this.repository.currentScope()) {
      return
    }
    const epoch = this.requestEpoch
    const requestScope = this.activeScope
    this.ui.albumsLoadingMore = true
    this.ui.albumsLoadMoreFailed = false
    try {
      const result = await this.repository.getArtistAlbums(
        this.artistId, this.ui.albumsNextStartIndex, this.albumPageSize)
      if (!this.isCurrent(epoch, requestScope)) {
        return
      }
      this.ui.albums = MusicQueryPlanner.mergeAlbumsUnique(this.ui.albums, result.items)
      this.ui.albumsNextStartIndex = result.startIndex + result.items.length
      this.ui.albumsHasMore = result.hasMore
      this.ui.albumsLoadMoreErrorCode = MusicBrowseErrorCode.None
    } catch (error) {
      if (this.isCurrent(epoch, requestScope)) {
        this.ui.albumsLoadMoreErrorCode = this.repository.classifyError(error as Object)
        this.ui.albumsLoadMoreFailed = true
      }
    } finally {
      if (this.isCurrent(epoch, requestScope)) {
        this.ui.albumsLoadingMore = false
      }
    }
  }

  async retryLoadMoreAlbums(): Promise<void> {
    this.ui.albumsLoadMoreFailed = false
    await this.loadMoreAlbums()
  }

  private isCurrent(epoch: number, requestScope: string): boolean {
    return epoch === this.requestEpoch && requestScope === this.activeScope &&
      requestScope === this.repository.currentScope()
  }
}
