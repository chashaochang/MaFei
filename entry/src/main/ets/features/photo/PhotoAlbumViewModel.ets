import { BaseViewModel } from '../../common/base/BaseViewModel'
import { PhotoAlbumContentState, PhotoAlbumUIState } from './PhotoAlbumUIState'
import {
  PhotoBrowseErrorCode,
  PhotoItem
} from './PhotoModels'
import { PhotoQueryPlanner } from './PhotoQueryPlanner'
import { PhotoBrowseRepositoryPort, PhotoRepository } from './PhotoRepository'

enum PhotoAlbumLoadMode {
  Initial,
  Refresh,
  More
}

export class PhotoAlbumViewModel extends BaseViewModel<PhotoAlbumUIState> {
  private repository: PhotoBrowseRepositoryPort
  private readonly pageSize: number = 60
  private epoch: number = 0
  private initialized: boolean = false
  private initialLoading: boolean = false
  private activeScope: string = ''

  constructor(ui: PhotoAlbumUIState, repository: PhotoBrowseRepositoryPort = new PhotoRepository()) {
    super(ui)
    this.repository = repository || new PhotoRepository()
  }

  async init(albumId: string, albumName: string): Promise<void> {
    const normalizedId = albumId.trim()
    const scope = this.repository.currentScope()
    if (normalizedId.length === 0) {
      this.ui.albumId = ''
      this.ui.albumName = albumName
      this.ui.state = PhotoAlbumContentState.Error
      this.ui.errorCode = PhotoBrowseErrorCode.NotFound
      return
    }
    if (this.ui.albumId !== normalizedId || this.activeScope !== scope) {
      this.reset()
    }
    this.activeScope = scope
    this.ui.albumId = normalizedId
    this.ui.albumName = albumName
    await this.load(PhotoAlbumLoadMode.Initial)
  }

  async refresh(): Promise<void> {
    await this.load(PhotoAlbumLoadMode.Refresh)
  }

  async loadMore(): Promise<void> {
    await this.load(PhotoAlbumLoadMode.More)
  }

  async retry(): Promise<void> {
    await this.load(PhotoAlbumLoadMode.Initial)
  }

  async retryLoadMore(): Promise<void> {
    this.ui.loadMoreFailed = false
    await this.load(PhotoAlbumLoadMode.More)
  }

  applyFavorite(itemId: string, favorite: boolean): void {
    const index = this.ui.photos.findIndex((photo: PhotoItem): boolean => photo.id === itemId)
    if (index < 0) {
      return
    }
    const photos = this.ui.photos.slice()
    photos[index].favorite = favorite
    this.ui.photos = photos
    this.ui.photoGroups = PhotoQueryPlanner.groupByDate(photos)
  }

  viewerScope(): string {
    return this.activeScope.length > 0 && this.activeScope === this.repository.currentScope() ?
      this.activeScope : ''
  }

  private async load(mode: PhotoAlbumLoadMode): Promise<void> {
    if (this.ui.albumId.length === 0 ||
      (mode === PhotoAlbumLoadMode.Initial && (this.initialized || this.initialLoading))) {
      return
    }
    if (this.initialLoading || this.ui.refreshing || this.ui.loadingMore ||
      (mode === PhotoAlbumLoadMode.More && (!this.ui.hasMore || this.ui.loadMoreFailed))) {
      return
    }
    const currentScope = this.repository.currentScope()
    if (this.activeScope !== currentScope) {
      this.reset()
      this.activeScope = currentScope
      mode = PhotoAlbumLoadMode.Initial
    }
    const epoch = ++this.epoch
    const requestScope = this.activeScope
    const startIndex = mode === PhotoAlbumLoadMode.More ? this.ui.nextIndex : 0
    this.begin(mode)
    try {
      if (mode !== PhotoAlbumLoadMode.More) {
        this.ui.album = await this.repository.getAlbum(this.ui.albumId)
        if (!this.isCurrent(epoch, requestScope)) {
          return
        }
        if (this.ui.album.name.length > 0) {
          this.ui.albumName = this.ui.album.name
        }
      }
      const result = await this.repository.getPhotos(this.ui.albumId, startIndex, this.pageSize)
      if (!this.isCurrent(epoch, requestScope)) {
        return
      }
      this.ui.photos = mode === PhotoAlbumLoadMode.More ?
        PhotoQueryPlanner.mergeUnique(this.ui.photos, result.items) : PhotoQueryPlanner.mergeUnique([], result.items)
      this.ui.photoGroups = PhotoQueryPlanner.groupByDate(this.ui.photos)
      this.ui.nextIndex = result.nextStartIndex
      this.ui.hasMore = result.hasMore
      this.ui.loadMoreFailed = false
      this.ui.errorCode = PhotoBrowseErrorCode.None
      this.ui.state = this.ui.photos.length === 0 ?
        PhotoAlbumContentState.Empty : PhotoAlbumContentState.Content
      this.initialized = true
    } catch (error) {
      if (!this.isCurrent(epoch, requestScope)) {
        return
      }
      this.ui.errorCode = this.repository.classifyError(error as Object)
      if (mode === PhotoAlbumLoadMode.More && this.ui.photos.length > 0) {
        this.ui.loadMoreFailed = true
      } else if (this.ui.photos.length === 0) {
        this.ui.state = PhotoAlbumContentState.Error
      }
      console.error(`[PhotoAlbum] load failed: ${JSON.stringify(error)}`)
    } finally {
      if (this.isCurrent(epoch, requestScope)) {
        this.finish(mode)
      }
    }
  }

  private begin(mode: PhotoAlbumLoadMode): void {
    if (mode === PhotoAlbumLoadMode.Initial && !this.initialized) {
      this.initialLoading = true
      this.ui.state = PhotoAlbumContentState.Loading
    } else if (mode === PhotoAlbumLoadMode.Refresh) {
      this.ui.refreshing = true
    } else if (mode === PhotoAlbumLoadMode.More) {
      this.ui.loadingMore = true
      this.ui.loadMoreFailed = false
    }
  }

  private finish(mode: PhotoAlbumLoadMode): void {
    if (mode === PhotoAlbumLoadMode.Initial) {
      this.initialLoading = false
    } else if (mode === PhotoAlbumLoadMode.Refresh) {
      this.ui.refreshing = false
    } else {
      this.ui.loadingMore = false
    }
  }

  private isCurrent(epoch: number, requestScope: string): boolean {
    return epoch === this.epoch && requestScope === this.activeScope &&
      requestScope === this.repository.currentScope()
  }

  private reset(): void {
    this.epoch++
    this.initialized = false
    this.initialLoading = false
    this.ui.album = null
    this.ui.photos = []
    this.ui.photoGroups = []
    this.ui.state = PhotoAlbumContentState.Loading
    this.ui.refreshing = false
    this.ui.loadingMore = false
    this.ui.loadMoreFailed = false
    this.ui.hasMore = true
    this.ui.nextIndex = 0
    this.ui.errorCode = PhotoBrowseErrorCode.None
  }
}
