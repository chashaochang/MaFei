import { PageState } from '../../common/base/BaseUIState'
import { BaseViewModel } from '../../common/base/BaseViewModel'
import {
  PhotoBrowseErrorCode,
  PhotoExifInfo,
  PhotoItem,
  PhotoViewerRouteParam
} from './PhotoModels'
import {
  PhotoBrowseRepositoryPort,
  PhotoRepository
} from './PhotoRepository'
import {
  photoViewerSourceStore,
  PhotoViewerSourceSnapshot,
  PhotoViewerSourceStorePort
} from './PhotoViewerSourceStore'
import { PhotoViewerUIState } from './PhotoViewerUIState'

const DEFAULT_SLIDESHOW_INTERVAL_MS: number = 5000

export interface PhotoViewerSchedulerPort {
  schedule(callback: () => void, intervalMs: number): number
  cancel(timerId: number): void
}

class SystemPhotoViewerScheduler implements PhotoViewerSchedulerPort {
  schedule(callback: () => void, intervalMs: number): number {
    return setInterval(callback, intervalMs)
  }

  cancel(timerId: number): void {
    clearInterval(timerId)
  }
}

export class PhotoViewerViewModel extends BaseViewModel<PhotoViewerUIState> {
  private repository: PhotoBrowseRepositoryPort
  private sourceStore: PhotoViewerSourceStorePort
  private scheduler: PhotoViewerSchedulerPort
  private activeScope: string = ''
  private activeSourceKey: string = ''
  private lifecycleEpoch: number = 0
  private infoEpoch: number = 0
  private favoriteEpoch: number = 0
  private slideshowTimer: number = -1
  private disposed: boolean = false
  private detailCache: Map<string, PhotoItem> = new Map<string, PhotoItem>()

  constructor(ui: PhotoViewerUIState, repository: PhotoBrowseRepositoryPort = new PhotoRepository(),
    sourceStore: PhotoViewerSourceStorePort = photoViewerSourceStore,
    scheduler: PhotoViewerSchedulerPort = new SystemPhotoViewerScheduler()) {
    super(ui)
    this.repository = repository
    this.sourceStore = sourceStore
    this.scheduler = scheduler
  }

  init(param: PhotoViewerRouteParam): boolean {
    const previousSourceKey = this.activeSourceKey
    const previousScope = this.activeScope
    this.stopSlideshow()
    this.lifecycleEpoch += 1
    this.infoEpoch += 1
    this.favoriteEpoch += 1
    this.disposed = false
    this.resetUI()

    const sourceKey = param.sourceKey.trim()
    const currentScope = this.repository.currentScope()
    if (previousSourceKey.length > 0 &&
      (previousSourceKey !== sourceKey || previousScope !== currentScope)) {
      this.sourceStore.remove(previousSourceKey, previousScope)
    }
    if (currentScope.length === 0) {
      this.failSource(PhotoBrowseErrorCode.AuthenticationExpired)
      return false
    }
    const source = this.sourceStore.resolve(sourceKey, currentScope)
    if (!source || source.items.length === 0) {
      this.failSource(PhotoBrowseErrorCode.NotFound)
      return false
    }
    this.applySource(source, param.initialPhotoId)
    return true
  }

  currentPhoto(): PhotoItem | undefined {
    if (this.ui.currentIndex < 0 || this.ui.currentIndex >= this.ui.photos.length) {
      return undefined
    }
    return this.ui.photos[this.ui.currentIndex]
  }

  canGoPrevious(): boolean {
    return this.ui.currentIndex > 0
  }

  canGoNext(): boolean {
    return this.ui.currentIndex >= 0 && this.ui.currentIndex < this.ui.photos.length - 1
  }

  goToIndex(index: number, manual: boolean = true): boolean {
    if (manual) {
      this.notifyManualInteraction()
    }
    if (!this.ensureCurrentScope() || index < 0 || index >= this.ui.photos.length) {
      return false
    }
    if (index === this.ui.currentIndex) {
      return true
    }
    this.ui.currentIndex = index
    this.onCurrentPhotoChanged()
    return true
  }

  goPrevious(): boolean {
    return this.goToIndex(this.ui.currentIndex - 1)
  }

  goNext(): boolean {
    return this.goToIndex(this.ui.currentIndex + 1)
  }

  toggleControls(): void {
    this.notifyManualInteraction()
    this.ui.controlsVisible = !this.ui.controlsVisible
  }

  setControlsVisible(visible: boolean): void {
    this.notifyManualInteraction()
    this.ui.controlsVisible = visible
  }

  updateZoom(scale: number, offsetX: number, offsetY: number, manual: boolean = true): void {
    if (manual) {
      this.notifyManualInteraction()
    }
    const normalizedScale = Number.isFinite(scale) ? Math.max(1, scale) : 1
    this.ui.zoomScale = normalizedScale
    this.ui.zoomOffsetX = normalizedScale > 1 && Number.isFinite(offsetX) ? offsetX : 0
    this.ui.zoomOffsetY = normalizedScale > 1 && Number.isFinite(offsetY) ? offsetY : 0
  }

  resetZoom(): void {
    this.notifyManualInteraction()
    this.resetZoomState()
  }

  notifyManualInteraction(): void {
    this.stopSlideshow()
  }

  startSlideshow(intervalMs: number = DEFAULT_SLIDESHOW_INTERVAL_MS): boolean {
    this.stopSlideshow()
    if (!this.ensureCurrentScope() || this.ui.photos.length < 2 || !this.canGoNext()) {
      return false
    }
    const normalizedInterval = Number.isFinite(intervalMs) && intervalMs > 0 ?
      Math.floor(intervalMs) : DEFAULT_SLIDESHOW_INTERVAL_MS
    this.ui.slideshowActive = true
    this.ui.controlsVisible = false
    this.slideshowTimer = this.scheduler.schedule((): void => this.advanceSlideshow(), normalizedInterval)
    return true
  }

  stopSlideshow(): void {
    if (this.slideshowTimer >= 0) {
      this.scheduler.cancel(this.slideshowTimer)
      this.slideshowTimer = -1
    }
    this.ui.slideshowActive = false
  }

  toggleSlideshow(intervalMs: number = DEFAULT_SLIDESHOW_INTERVAL_MS): boolean {
    if (this.ui.slideshowActive) {
      this.stopSlideshow()
      return false
    }
    return this.startSlideshow(intervalMs)
  }

  async showInfo(forceReload: boolean = false): Promise<boolean> {
    this.notifyManualInteraction()
    if (!this.ensureCurrentScope() || !this.currentPhoto()) {
      this.ui.infoVisible = false
      return false
    }
    this.ui.infoVisible = true
    return this.loadCurrentInfo(forceReload)
  }

  hideInfo(): void {
    this.ui.infoVisible = false
    this.infoEpoch += 1
    this.ui.infoLoading = false
  }

  async loadCurrentInfo(forceReload: boolean = false): Promise<boolean> {
    if (!this.ensureCurrentScope()) {
      return false
    }
    const photo = this.currentPhoto()
    if (!photo) {
      return false
    }
    const cached = this.detailCache.get(photo.id)
    if (!forceReload && cached) {
      this.ui.infoPhoto = this.copyPhoto(cached)
      this.ui.infoErrorCode = PhotoBrowseErrorCode.None
      return true
    }
    const requestEpoch = ++this.infoEpoch
    const lifecycleEpoch = this.lifecycleEpoch
    const requestScope = this.activeScope
    const requestSourceKey = this.activeSourceKey
    const photoId = photo.id
    this.ui.infoPhoto = this.copyPhoto(photo)
    this.ui.infoLoading = true
    this.ui.infoErrorCode = PhotoBrowseErrorCode.None
    try {
      const detail = await this.repository.getPhoto(photoId)
      if (!this.isRequestCurrent(lifecycleEpoch, requestScope, requestSourceKey) ||
        requestEpoch !== this.infoEpoch || !this.isCurrentPhoto(photoId) || detail.id !== photoId) {
        return false
      }
      const copiedDetail = this.copyPhoto(detail)
      this.detailCache.set(photoId, copiedDetail)
      this.replacePhoto(copiedDetail)
      this.ui.infoPhoto = this.copyPhoto(copiedDetail)
      return true
    } catch (error) {
      if (this.isRequestCurrent(lifecycleEpoch, requestScope, requestSourceKey) &&
        requestEpoch === this.infoEpoch && this.isCurrentPhoto(photoId)) {
        this.ui.infoErrorCode = this.repository.classifyError(error as Object)
      }
      return false
    } finally {
      if (this.isRequestCurrent(lifecycleEpoch, requestScope, requestSourceKey) &&
        requestEpoch === this.infoEpoch && this.isCurrentPhoto(photoId)) {
        this.ui.infoLoading = false
      }
    }
  }

  async toggleCurrentFavorite(): Promise<boolean> {
    const photo = this.currentPhoto()
    if (!photo) {
      return false
    }
    return this.setCurrentFavorite(!photo.favorite)
  }

  async setCurrentFavorite(favorite: boolean): Promise<boolean> {
    this.notifyManualInteraction()
    const photo = this.currentPhoto()
    if (!photo || !this.ensureCurrentScope() || this.ui.favoritePendingPhotoId.length > 0) {
      return false
    }
    const requestEpoch = ++this.favoriteEpoch
    const lifecycleEpoch = this.lifecycleEpoch
    const requestScope = this.activeScope
    const requestSourceKey = this.activeSourceKey
    const photoId = photo.id
    this.ui.favoritePendingPhotoId = photoId
    this.ui.favoriteFailedPhotoId = ''
    this.ui.favoriteErrorCode = PhotoBrowseErrorCode.None
    try {
      const confirmedFavorite = await this.repository.setFavorite(photoId, favorite)
      if (!this.isRequestCurrent(lifecycleEpoch, requestScope, requestSourceKey) ||
        requestEpoch !== this.favoriteEpoch) {
        return false
      }
      this.updateFavorite(photoId, confirmedFavorite)
      return true
    } catch (error) {
      if (this.isRequestCurrent(lifecycleEpoch, requestScope, requestSourceKey) &&
        requestEpoch === this.favoriteEpoch) {
        this.ui.favoriteFailedPhotoId = photoId
        this.ui.favoriteErrorCode = this.repository.classifyError(error as Object)
      }
      return false
    } finally {
      if (this.isRequestCurrent(lifecycleEpoch, requestScope, requestSourceKey) &&
        requestEpoch === this.favoriteEpoch && this.ui.favoritePendingPhotoId === photoId) {
        this.ui.favoritePendingPhotoId = ''
      }
    }
  }

  dispose(): void {
    if (this.disposed) {
      return
    }
    this.stopSlideshow()
    this.disposed = true
    this.lifecycleEpoch += 1
    this.infoEpoch += 1
    this.favoriteEpoch += 1
    this.sourceStore.remove(this.activeSourceKey, this.activeScope)
    this.detailCache.clear()
    this.activeScope = ''
    this.activeSourceKey = ''
    this.ui.infoVisible = false
    this.ui.infoLoading = false
    this.ui.infoPhoto = null
    this.ui.favoritePendingPhotoId = ''
  }

  private applySource(source: PhotoViewerSourceSnapshot, initialPhotoId: string): void {
    this.activeScope = source.scope
    this.activeSourceKey = source.sourceKey
    this.ui.sourceKey = source.sourceKey
    this.ui.sourceScope = source.scope
    this.ui.sourceTitle = source.title
    this.ui.photos = source.items.map((item: PhotoItem): PhotoItem => this.copyPhoto(item))
    const normalizedInitialId = initialPhotoId.trim()
    const requestedIndex = this.ui.photos.findIndex((item: PhotoItem): boolean => item.id === normalizedInitialId)
    this.ui.currentIndex = requestedIndex >= 0 ? requestedIndex : 0
    this.ui.pageState = PageState.Content
    this.ui.loadErrorCode = PhotoBrowseErrorCode.None
    this.onCurrentPhotoChanged()
  }

  private advanceSlideshow(): void {
    if (!this.ui.slideshowActive || !this.ensureCurrentScope()) {
      this.stopSlideshow()
      return
    }
    if (!this.canGoNext()) {
      this.stopSlideshow()
      return
    }
    if (!this.goToIndex(this.ui.currentIndex + 1, false) || !this.canGoNext()) {
      this.stopSlideshow()
    }
  }

  private onCurrentPhotoChanged(): void {
    this.infoEpoch += 1
    this.ui.infoVisible = false
    this.ui.infoLoading = false
    this.ui.infoPhoto = null
    this.ui.infoErrorCode = PhotoBrowseErrorCode.None
    this.ui.favoriteFailedPhotoId = ''
    this.ui.favoriteErrorCode = PhotoBrowseErrorCode.None
    this.resetZoomState()
  }

  private resetZoomState(): void {
    this.ui.zoomScale = 1
    this.ui.zoomOffsetX = 0
    this.ui.zoomOffsetY = 0
    this.ui.zoomRevision += 1
  }

  private replacePhoto(photo: PhotoItem): void {
    const index = this.ui.photos.findIndex((item: PhotoItem): boolean => item.id === photo.id)
    if (index < 0) {
      return
    }
    const photos = this.ui.photos.slice()
    photos[index] = this.copyPhoto(photo)
    this.ui.photos = photos
  }

  private updateFavorite(photoId: string, favorite: boolean): void {
    const index = this.ui.photos.findIndex((item: PhotoItem): boolean => item.id === photoId)
    if (index < 0) {
      return
    }
    const photos = this.ui.photos.slice()
    photos[index] = this.copyPhotoWithFavorite(photos[index], favorite)
    this.ui.photos = photos
    const cached = this.detailCache.get(photoId)
    if (cached) {
      const updatedCache = this.copyPhotoWithFavorite(cached, favorite)
      this.detailCache.set(photoId, updatedCache)
      if (this.isInfoPhoto(photoId)) {
        this.ui.infoPhoto = this.copyPhoto(updatedCache)
      }
    } else if (this.isInfoPhoto(photoId)) {
      this.ui.infoPhoto = this.copyPhoto(photos[index])
    }
  }

  private ensureCurrentScope(): boolean {
    if (!this.disposed && this.activeScope.length > 0 && this.activeSourceKey.length > 0 &&
      this.repository.currentScope() === this.activeScope) {
      return true
    }
    if (!this.disposed && this.activeSourceKey.length > 0) {
      this.invalidateScope()
    }
    return false
  }

  private invalidateScope(): void {
    const sourceKey = this.activeSourceKey
    const sourceScope = this.activeScope
    this.stopSlideshow()
    this.lifecycleEpoch += 1
    this.infoEpoch += 1
    this.favoriteEpoch += 1
    this.sourceStore.remove(sourceKey, sourceScope)
    this.detailCache.clear()
    this.activeSourceKey = ''
    this.activeScope = ''
    this.ui.sourceKey = ''
    this.ui.sourceScope = ''
    this.ui.sourceTitle = ''
    this.ui.photos = []
    this.ui.currentIndex = -1
    this.ui.controlsVisible = true
    this.ui.infoVisible = false
    this.ui.infoLoading = false
    this.ui.infoPhoto = null
    this.ui.infoErrorCode = PhotoBrowseErrorCode.None
    this.ui.favoritePendingPhotoId = ''
    this.ui.favoriteFailedPhotoId = ''
    this.ui.favoriteErrorCode = PhotoBrowseErrorCode.None
    this.resetZoomState()
    this.ui.pageState = PageState.Error
    this.ui.loadErrorCode = PhotoBrowseErrorCode.AuthenticationExpired
  }

  private isRequestCurrent(epoch: number, scope: string, sourceKey: string): boolean {
    return !this.disposed && epoch === this.lifecycleEpoch && scope === this.activeScope &&
      sourceKey === this.activeSourceKey && scope === this.repository.currentScope()
  }

  private failSource(errorCode: PhotoBrowseErrorCode): void {
    this.ui.pageState = PageState.Error
    this.ui.loadErrorCode = errorCode
  }

  private resetUI(): void {
    this.detailCache.clear()
    this.activeScope = ''
    this.activeSourceKey = ''
    this.ui.sourceKey = ''
    this.ui.sourceScope = ''
    this.ui.sourceTitle = ''
    this.ui.photos = []
    this.ui.currentIndex = -1
    this.ui.controlsVisible = true
    this.ui.slideshowActive = false
    this.ui.infoVisible = false
    this.ui.infoLoading = false
    this.ui.infoPhoto = null
    this.ui.infoErrorCode = PhotoBrowseErrorCode.None
    this.ui.favoritePendingPhotoId = ''
    this.ui.favoriteFailedPhotoId = ''
    this.ui.favoriteErrorCode = PhotoBrowseErrorCode.None
    this.ui.loadErrorCode = PhotoBrowseErrorCode.None
    this.ui.pageState = PageState.Loading
    this.resetZoomState()
  }

  private isCurrentPhoto(photoId: string): boolean {
    const photo = this.currentPhoto()
    return photo !== undefined && photo.id === photoId
  }

  private isInfoPhoto(photoId: string): boolean {
    return this.ui.infoPhoto !== null && this.ui.infoPhoto.id === photoId
  }

  private copyPhotoWithFavorite(photo: PhotoItem, favorite: boolean): PhotoItem {
    const copy = this.copyPhoto(photo)
    copy.favorite = favorite
    return copy
  }

  private copyPhoto(photo: PhotoItem): PhotoItem {
    return {
      id: photo.id,
      name: photo.name,
      fileName: photo.fileName,
      parentId: photo.parentId,
      path: photo.path,
      format: photo.format,
      dateTaken: photo.dateTaken,
      dateKey: photo.dateKey,
      dateTimestamp: photo.dateTimestamp,
      width: photo.width,
      height: photo.height,
      thumbnailUrl: photo.thumbnailUrl,
      fullImageUrl: photo.fullImageUrl,
      favorite: photo.favorite,
      canDownload: photo.canDownload,
      exif: this.copyExif(photo.exif)
    }
  }

  private copyExif(exif: PhotoExifInfo): PhotoExifInfo {
    return {
      cameraMake: exif.cameraMake,
      cameraModel: exif.cameraModel,
      software: exif.software,
      exposureTime: exif.exposureTime,
      focalLength: exif.focalLength,
      orientation: exif.orientation,
      aperture: exif.aperture,
      shutterSpeed: exif.shutterSpeed,
      latitude: exif.latitude,
      longitude: exif.longitude,
      altitude: exif.altitude,
      isoSpeedRating: exif.isoSpeedRating
    }
  }
}
