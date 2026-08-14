import { BaseViewModel } from '../../common/base/BaseViewModel'
import {
  PhotoContentState,
  PhotoLibraryUIState
} from './PhotoLibraryUIState'
import {
  PhotoBrowseErrorCode,
  PhotoItem,
  PhotoLibrarySection
} from './PhotoModels'
import {
  PhotoBrowseRepositoryPort,
  PhotoRepository
} from './PhotoRepository'
import { PhotoQueryPlanner } from './PhotoQueryPlanner'

enum PhotoLoadMode {
  Initial,
  Refresh,
  More
}

export class PhotoLibraryViewModel extends BaseViewModel<PhotoLibraryUIState> {
  private repository: PhotoBrowseRepositoryPort
  private readonly pageSize: number = 60
  private photosEpoch: number = 0
  private albumsEpoch: number = 0
  private favoriteEpoch: number = 0
  private photosInitialized: boolean = false
  private albumsInitialized: boolean = false
  private photosInitialLoading: boolean = false
  private albumsInitialLoading: boolean = false
  private activeScope: string = ''

  constructor(ui: PhotoLibraryUIState, repository: PhotoBrowseRepositoryPort = new PhotoRepository()) {
    super(ui)
    this.repository = repository
  }

  async init(libraryId: string, libraryName: string): Promise<void> {
    const normalizedId = libraryId.trim()
    const requestScope = this.repository.currentScope()
    if (normalizedId.length === 0) {
      this.resetAll()
      this.ui.libraryId = ''
      this.ui.libraryName = libraryName
      this.ui.photosState = PhotoContentState.Error
      this.ui.photosErrorCode = PhotoBrowseErrorCode.RequestFailed
      return
    }
    if (this.ui.libraryId !== normalizedId || this.activeScope !== requestScope) {
      this.resetAll()
    }
    this.activeScope = requestScope
    this.ui.libraryId = normalizedId
    this.ui.libraryName = libraryName
    await this.loadCurrent(PhotoLoadMode.Initial)
  }

  async selectSection(index: number): Promise<void> {
    this.ui.selectedSection = index === 1 ? PhotoLibrarySection.Albums : PhotoLibrarySection.Photos
    await this.loadCurrent(PhotoLoadMode.Initial)
  }

  async refreshCurrent(): Promise<void> {
    await this.loadCurrent(PhotoLoadMode.Refresh)
  }

  async loadMoreCurrent(): Promise<void> {
    await this.loadCurrent(PhotoLoadMode.More)
  }

  async retryCurrent(): Promise<void> {
    await this.loadCurrent(PhotoLoadMode.Initial)
  }

  async retryLoadMoreCurrent(): Promise<void> {
    if (this.ui.selectedSection === PhotoLibrarySection.Albums) {
      this.ui.albumsLoadMoreFailed = false
    } else {
      this.ui.photosLoadMoreFailed = false
    }
    await this.loadCurrent(PhotoLoadMode.More)
  }

  async setPhotoFavorite(photoId: string, favorite: boolean): Promise<boolean> {
    if (this.isFavoritePending(photoId) ||
      this.ui.photos.findIndex((item: PhotoItem): boolean => item.id === photoId) < 0) {
      return false
    }
    const requestScope = this.activeScope
    const requestLibraryId = this.ui.libraryId
    const requestFavoriteEpoch = this.favoriteEpoch
    if (requestScope.length === 0 || requestScope !== this.repository.currentScope()) {
      return false
    }
    this.ui.favoritePendingIds = this.ui.favoritePendingIds.concat([photoId])
    try {
      const confirmedFavorite = await this.repository.setFavorite(photoId, favorite)
      if (!this.isFavoriteCurrent(requestFavoriteEpoch, requestScope, requestLibraryId)) {
        return false
      }
      this.updateFavorite(photoId, confirmedFavorite)
      return true
    } catch (_error) {
      return false
    } finally {
      if (requestFavoriteEpoch === this.favoriteEpoch) {
        this.ui.favoritePendingIds = this.ui.favoritePendingIds.filter(
          (pendingId: string): boolean => pendingId !== photoId)
      }
    }
  }

  async refreshPhoto(photoId: string): Promise<boolean> {
    const requestScope = this.activeScope
    const requestLibraryId = this.ui.libraryId
    try {
      const photo = await this.repository.getPhoto(photoId)
      if (requestScope !== this.activeScope || requestScope !== this.repository.currentScope() ||
        requestLibraryId !== this.ui.libraryId) {
        return false
      }
      this.replacePhoto(photo)
      return true
    } catch (_error) {
      return false
    }
  }

  viewerScope(): string {
    return this.activeScope.length > 0 && this.activeScope === this.repository.currentScope() ?
      this.activeScope : ''
  }

  private async loadCurrent(mode: PhotoLoadMode): Promise<void> {
    if (this.ui.libraryId.length === 0) {
      return
    }
    let requestMode = mode
    const currentScope = this.repository.currentScope()
    if (this.activeScope !== currentScope) {
      this.resetAll()
      this.activeScope = currentScope
      requestMode = PhotoLoadMode.Initial
    }
    if (this.ui.selectedSection === PhotoLibrarySection.Albums) {
      await this.loadAlbums(requestMode)
    } else {
      await this.loadPhotos(requestMode)
    }
  }

  private async loadPhotos(mode: PhotoLoadMode): Promise<void> {
    if (mode === PhotoLoadMode.Initial && (this.photosInitialized || this.photosInitialLoading)) {
      return
    }
    if (this.photosInitialLoading || this.ui.photosRefreshing || this.ui.photosLoadingMore ||
      (mode === PhotoLoadMode.More && (!this.ui.photosHasMore || this.ui.photosLoadMoreFailed))) {
      return
    }
    const epoch = ++this.photosEpoch
    const requestScope = this.activeScope
    const startIndex = mode === PhotoLoadMode.More ? this.ui.photosNextIndex : 0
    this.beginPhotos(mode)
    try {
      const result = await this.repository.getPhotos(this.ui.libraryId, startIndex, this.pageSize)
      if (!this.isCurrent(epoch, this.photosEpoch, requestScope)) {
        return
      }
      this.ui.photos = mode === PhotoLoadMode.More ?
        PhotoQueryPlanner.mergeUnique(this.ui.photos, result.items) :
        PhotoQueryPlanner.mergeUnique([], result.items)
      this.ui.photoGroups = PhotoQueryPlanner.groupByDate(this.ui.photos)
      this.ui.photosNextIndex = result.nextStartIndex
      this.ui.photosHasMore = result.hasMore
      this.ui.photosLoadMoreFailed = false
      this.ui.photosErrorCode = PhotoBrowseErrorCode.None
      this.ui.photosState = this.ui.photos.length === 0 ? PhotoContentState.Empty : PhotoContentState.Content
      this.photosInitialized = true
    } catch (error) {
      if (!this.isCurrent(epoch, this.photosEpoch, requestScope)) {
        return
      }
      this.ui.photosErrorCode = this.repository.classifyError(error as Object)
      if (mode === PhotoLoadMode.More && this.ui.photos.length > 0) {
        this.ui.photosLoadMoreFailed = true
      } else if (this.ui.photos.length === 0) {
        this.ui.photosState = PhotoContentState.Error
      }
    } finally {
      if (this.isCurrent(epoch, this.photosEpoch, requestScope)) {
        this.finishPhotos(mode)
      }
    }
  }

  private async loadAlbums(mode: PhotoLoadMode): Promise<void> {
    if (mode === PhotoLoadMode.Initial && (this.albumsInitialized || this.albumsInitialLoading)) {
      return
    }
    if (this.albumsInitialLoading || this.ui.albumsRefreshing || this.ui.albumsLoadingMore ||
      (mode === PhotoLoadMode.More && (!this.ui.albumsHasMore || this.ui.albumsLoadMoreFailed))) {
      return
    }
    const epoch = ++this.albumsEpoch
    const requestScope = this.activeScope
    const startIndex = mode === PhotoLoadMode.More ? this.ui.albumsNextIndex : 0
    this.beginAlbums(mode)
    try {
      const result = await this.repository.getAlbums(this.ui.libraryId, startIndex, this.pageSize)
      if (!this.isCurrent(epoch, this.albumsEpoch, requestScope)) {
        return
      }
      this.ui.albums = mode === PhotoLoadMode.More ?
        PhotoQueryPlanner.mergeAlbumsUnique(this.ui.albums, result.items) :
        PhotoQueryPlanner.mergeAlbumsUnique([], result.items)
      this.ui.albumsNextIndex = result.nextStartIndex
      this.ui.albumsHasMore = result.hasMore
      this.ui.albumsLoadMoreFailed = false
      this.ui.albumsErrorCode = PhotoBrowseErrorCode.None
      this.ui.albumsState = this.ui.albums.length === 0 ? PhotoContentState.Empty : PhotoContentState.Content
      this.albumsInitialized = true
    } catch (error) {
      if (!this.isCurrent(epoch, this.albumsEpoch, requestScope)) {
        return
      }
      this.ui.albumsErrorCode = this.repository.classifyError(error as Object)
      if (mode === PhotoLoadMode.More && this.ui.albums.length > 0) {
        this.ui.albumsLoadMoreFailed = true
      } else if (this.ui.albums.length === 0) {
        this.ui.albumsState = PhotoContentState.Error
      }
    } finally {
      if (this.isCurrent(epoch, this.albumsEpoch, requestScope)) {
        this.finishAlbums(mode)
      }
    }
  }

  private beginPhotos(mode: PhotoLoadMode): void {
    if (mode === PhotoLoadMode.Initial) {
      this.photosInitialLoading = true
      this.ui.photosState = PhotoContentState.Loading
    } else if (mode === PhotoLoadMode.Refresh) {
      this.ui.photosRefreshing = true
    } else {
      this.ui.photosLoadingMore = true
      this.ui.photosLoadMoreFailed = false
    }
  }

  private finishPhotos(mode: PhotoLoadMode): void {
    if (mode === PhotoLoadMode.Initial) {
      this.photosInitialLoading = false
    } else if (mode === PhotoLoadMode.Refresh) {
      this.ui.photosRefreshing = false
    } else {
      this.ui.photosLoadingMore = false
    }
  }

  private beginAlbums(mode: PhotoLoadMode): void {
    if (mode === PhotoLoadMode.Initial) {
      this.albumsInitialLoading = true
      this.ui.albumsState = PhotoContentState.Loading
    } else if (mode === PhotoLoadMode.Refresh) {
      this.ui.albumsRefreshing = true
    } else {
      this.ui.albumsLoadingMore = true
      this.ui.albumsLoadMoreFailed = false
    }
  }

  private finishAlbums(mode: PhotoLoadMode): void {
    if (mode === PhotoLoadMode.Initial) {
      this.albumsInitialLoading = false
    } else if (mode === PhotoLoadMode.Refresh) {
      this.ui.albumsRefreshing = false
    } else {
      this.ui.albumsLoadingMore = false
    }
  }

  private updateFavorite(photoId: string, favorite: boolean): void {
    const index = this.ui.photos.findIndex((item: PhotoItem): boolean => item.id === photoId)
    if (index < 0) {
      return
    }
    const photos = this.ui.photos.slice()
    photos[index].favorite = favorite
    this.ui.photos = photos
    this.ui.photoGroups = PhotoQueryPlanner.groupByDate(photos)
  }

  private replacePhoto(photo: PhotoItem): void {
    const index = this.ui.photos.findIndex((item: PhotoItem): boolean => item.id === photo.id)
    if (index < 0) {
      return
    }
    const photos = this.ui.photos.slice()
    photos[index] = photo
    this.ui.photos = photos
    this.ui.photoGroups = PhotoQueryPlanner.groupByDate(photos)
  }

  private isFavoritePending(photoId: string): boolean {
    return this.ui.favoritePendingIds.includes(photoId)
  }

  private isFavoriteCurrent(epoch: number, requestScope: string, libraryId: string): boolean {
    return epoch === this.favoriteEpoch && requestScope === this.activeScope &&
      requestScope === this.repository.currentScope() && libraryId === this.ui.libraryId
  }

  private isCurrent(epoch: number, currentEpoch: number, requestScope: string): boolean {
    return epoch === currentEpoch && requestScope === this.activeScope &&
      requestScope === this.repository.currentScope()
  }

  private resetAll(): void {
    this.photosEpoch++
    this.albumsEpoch++
    this.favoriteEpoch++
    this.photosInitialized = false
    this.albumsInitialized = false
    this.photosInitialLoading = false
    this.albumsInitialLoading = false
    this.ui.selectedSection = PhotoLibrarySection.Photos
    this.ui.photos = []
    this.ui.photoGroups = []
    this.ui.albums = []
    this.ui.photosState = PhotoContentState.Loading
    this.ui.albumsState = PhotoContentState.Loading
    this.ui.photosRefreshing = false
    this.ui.photosLoadingMore = false
    this.ui.photosLoadMoreFailed = false
    this.ui.photosHasMore = true
    this.ui.photosNextIndex = 0
    this.ui.photosErrorCode = PhotoBrowseErrorCode.None
    this.ui.albumsRefreshing = false
    this.ui.albumsLoadingMore = false
    this.ui.albumsLoadMoreFailed = false
    this.ui.albumsHasMore = true
    this.ui.albumsNextIndex = 0
    this.ui.albumsErrorCode = PhotoBrowseErrorCode.None
    this.ui.favoritePendingIds = []
  }
}
