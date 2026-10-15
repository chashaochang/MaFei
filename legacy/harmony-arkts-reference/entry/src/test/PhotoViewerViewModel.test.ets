import { describe, expect, it } from '@ohos/hypium'
import { PageState } from '../main/ets/common/base/BaseUIState'
import {
  PhotoAlbumItem,
  PhotoAlbumKind,
  PhotoBrowseErrorCode,
  PhotoItem,
  PhotoOrientation,
  PhotoPageResult
} from '../main/ets/features/photo/PhotoModels'
import { PhotoBrowseRepositoryPort } from '../main/ets/features/photo/PhotoRepository'
import {
  PhotoViewerSourceSnapshot,
  PhotoViewerSourceStore,
  PhotoViewerSourceStorePort
} from '../main/ets/features/photo/PhotoViewerSourceStore'
import { PhotoViewerUIState } from '../main/ets/features/photo/PhotoViewerUIState'
import {
  PhotoViewerSchedulerPort,
  PhotoViewerViewModel
} from '../main/ets/features/photo/PhotoViewerViewModel'

function photo(id: string, name: string = id, favorite: boolean = false): PhotoItem {
  return {
    id: id,
    name: name,
    fileName: id + '.jpg',
    parentId: 'album-1',
    path: '/photos/' + id + '.jpg',
    format: 'jpg',
    dateTaken: '2026-08-11T10:00:00Z',
    dateKey: '2026-08-11',
    dateTimestamp: 20260811,
    width: 4000,
    height: 3000,
    thumbnailUrl: 'https://photos/' + id + '/thumbnail',
    fullImageUrl: 'https://photos/' + id + '/full',
    favorite: favorite,
    canDownload: true,
    exif: {
      cameraMake: 'Huawei',
      cameraModel: 'Camera',
      software: 'MaFei',
      orientation: PhotoOrientation.Normal
    }
  }
}

function emptyPage<T>(): PhotoPageResult<T> {
  return {
    items: [],
    startIndex: 0,
    nextStartIndex: 0,
    totalCount: 0,
    hasMore: false
  }
}

class FakePhotoViewerRepository implements PhotoBrowseRepositoryPort {
  scope: string = 'server-a|user-a'
  photoCalls: string[] = []
  favoriteCalls: string[] = []
  deferPhoto: boolean = false
  deferFavorite: boolean = false
  private photoResolver?: (value: PhotoItem) => void
  private favoriteResolver?: (value: boolean) => void

  currentScope(): string {
    return this.scope
  }

  classifyError(_error: Object): PhotoBrowseErrorCode {
    return PhotoBrowseErrorCode.RequestFailed
  }

  async getPhotos(_parentId: string, _startIndex?: number,
    _limit?: number): Promise<PhotoPageResult<PhotoItem>> {
    return emptyPage<PhotoItem>()
  }

  async getAlbums(_parentId: string, _startIndex?: number,
    _limit?: number): Promise<PhotoPageResult<PhotoAlbumItem>> {
    return emptyPage<PhotoAlbumItem>()
  }

  getPhoto(photoId: string): Promise<PhotoItem> {
    this.photoCalls.push(photoId)
    if (this.deferPhoto) {
      return new Promise<PhotoItem>((resolve: (value: PhotoItem) => void) => {
        this.photoResolver = resolve
      })
    }
    return Promise.resolve(photo(photoId, 'Detail ' + photoId))
  }

  async getAlbum(albumId: string): Promise<PhotoAlbumItem> {
    return {
      id: albumId,
      name: albumId,
      parentId: '',
      kind: PhotoAlbumKind.PhotoAlbum,
      thumbnailUrl: '',
      itemCount: 0
    }
  }

  setFavorite(photoId: string, favorite: boolean): Promise<boolean> {
    this.favoriteCalls.push(photoId + ':' + favorite)
    if (this.deferFavorite) {
      return new Promise<boolean>((resolve: (value: boolean) => void) => {
        this.favoriteResolver = resolve
      })
    }
    return Promise.resolve(favorite)
  }

  resolvePhoto(value: PhotoItem): void {
    this.photoResolver?.(value)
    this.photoResolver = undefined
  }

  resolveFavorite(value: boolean): void {
    this.favoriteResolver?.(value)
    this.favoriteResolver = undefined
  }
}

class RecordingPhotoViewerSourceStore implements PhotoViewerSourceStorePort {
  private store: PhotoViewerSourceStore = new PhotoViewerSourceStore()
  removeCalls: number = 0

  save(scope: string, title: string, items: PhotoItem[]): string {
    return this.store.save(scope, title, items)
  }

  resolve(sourceKey: string, scope: string): PhotoViewerSourceSnapshot | undefined {
    return this.store.resolve(sourceKey, scope)
  }

  remove(sourceKey: string, scope?: string): boolean {
    this.removeCalls += 1
    return this.store.remove(sourceKey, scope)
  }

  clearScope(scope: string): number {
    return this.store.clearScope(scope)
  }
}

class FakePhotoViewerScheduler implements PhotoViewerSchedulerPort {
  callbacks: Map<number, () => void> = new Map<number, () => void>()
  intervals: number[] = []
  cancelled: number[] = []
  private sequence: number = 0

  schedule(callback: () => void, intervalMs: number): number {
    this.sequence += 1
    this.callbacks.set(this.sequence, callback)
    this.intervals.push(intervalMs)
    return this.sequence
  }

  cancel(timerId: number): void {
    this.cancelled.push(timerId)
    this.callbacks.delete(timerId)
  }

  fire(timerId: number): void {
    this.callbacks.get(timerId)?.()
  }

  latestTimerId(): number {
    return this.sequence
  }
}

interface ViewerHarness {
  repository: FakePhotoViewerRepository
  store: RecordingPhotoViewerSourceStore
  scheduler: FakePhotoViewerScheduler
  ui: PhotoViewerUIState
  vm: PhotoViewerViewModel
  sourceKey: string
}

function harness(items: PhotoItem[], initialPhotoId: string = ''): ViewerHarness {
  const repository = new FakePhotoViewerRepository()
  const store = new RecordingPhotoViewerSourceStore()
  const scheduler = new FakePhotoViewerScheduler()
  const ui = new PhotoViewerUIState({} as UIContext)
  const vm = new PhotoViewerViewModel(ui, repository, store, scheduler)
  const sourceKey = store.save(repository.scope, 'Photos', items)
  vm.init({ sourceKey: sourceKey, initialPhotoId: initialPhotoId })
  return {
    repository: repository,
    store: store,
    scheduler: scheduler,
    ui: ui,
    vm: vm,
    sourceKey: sourceKey
  }
}

export default function photoViewerViewModelTest() {
  describe('PhotoViewerViewModel', () => {
    it('selectsTheRequestedInitialPhotoAndFallsBackToTheFirstPhoto', 0, () => {
      const selected = harness([photo('photo-1'), photo('photo-2')], ' photo-2 ')
      expect(selected.ui.pageState).assertEqual(PageState.Content)
      expect(selected.ui.currentIndex).assertEqual(1)
      expect(selected.vm.currentPhoto()?.id).assertEqual('photo-2')

      const fallback = harness([photo('photo-1'), photo('photo-2')], 'missing')
      expect(fallback.ui.currentIndex).assertEqual(0)
      expect(fallback.vm.currentPhoto()?.id).assertEqual('photo-1')
    })

    it('classifiesMissingScopeAndMissingSourceErrors', 0, () => {
      const repository = new FakePhotoViewerRepository()
      const store = new RecordingPhotoViewerSourceStore()
      const scheduler = new FakePhotoViewerScheduler()
      const sourceKey = store.save(repository.scope, 'Photos', [photo('photo-1')])
      repository.scope = ''
      const authUI = new PhotoViewerUIState({} as UIContext)
      const authVM = new PhotoViewerViewModel(authUI, repository, store, scheduler)

      expect(authVM.init({ sourceKey: sourceKey, initialPhotoId: '' })).assertFalse()
      expect(authUI.loadErrorCode).assertEqual(PhotoBrowseErrorCode.AuthenticationExpired)

      repository.scope = 'server-a|user-a'
      const missingUI = new PhotoViewerUIState({} as UIContext)
      const missingVM = new PhotoViewerViewModel(missingUI, repository, store, scheduler)
      expect(missingVM.init({ sourceKey: 'missing', initialPhotoId: '' })).assertFalse()
      expect(missingUI.loadErrorCode).assertEqual(PhotoBrowseErrorCode.NotFound)
    })

    it('enforcesNavigationBoundariesAndResetsPhotoSpecificState', 0, async () => {
      const value = harness([photo('photo-1'), photo('photo-2'), photo('photo-3')], 'photo-2')
      await value.vm.showInfo()
      value.vm.updateZoom(2, 20, 30)
      const revision = value.ui.zoomRevision

      expect(value.vm.goPrevious()).assertTrue()
      expect(value.ui.currentIndex).assertEqual(0)
      expect(value.ui.zoomScale).assertEqual(1)
      expect(value.ui.zoomOffsetX).assertEqual(0)
      expect(value.ui.zoomOffsetY).assertEqual(0)
      expect(value.ui.zoomRevision > revision).assertTrue()
      expect(value.ui.infoVisible).assertFalse()
      expect(value.ui.infoPhoto === null).assertTrue()
      expect(value.vm.goPrevious()).assertFalse()

      expect(value.vm.goNext()).assertTrue()
      expect(value.vm.goNext()).assertTrue()
      expect(value.ui.currentIndex).assertEqual(2)
      expect(value.vm.goNext()).assertFalse()
      expect(value.vm.goToIndex(99)).assertFalse()
    })

    it('stopsTheSlideshowWhenTheUserInteracts', 0, () => {
      const value = harness([photo('photo-1'), photo('photo-2')])

      expect(value.vm.startSlideshow(1200)).assertTrue()
      expect(value.ui.slideshowActive).assertTrue()
      expect(value.ui.controlsVisible).assertFalse()
      expect(value.scheduler.intervals[0]).assertEqual(1200)

      value.vm.updateZoom(2, 10, 10)
      expect(value.ui.slideshowActive).assertFalse()
      expect(value.scheduler.cancelled[0]).assertEqual(1)
    })

    it('keepsTheSlideshowRunningForPassiveZoomSynchronization', 0, () => {
      const value = harness([photo('photo-1'), photo('photo-2'), photo('photo-3')])
      expect(value.vm.startSlideshow(1200)).assertTrue()
      const timerId = value.scheduler.latestTimerId()

      value.scheduler.fire(timerId)
      expect(value.ui.currentIndex).assertEqual(1)
      value.vm.updateZoom(1, 0, 0, false)

      expect(value.ui.slideshowActive).assertTrue()
      expect(value.scheduler.cancelled.length).assertEqual(0)
    })

    it('advancesTheSlideshowAndStopsAtTheLastPhoto', 0, () => {
      const value = harness([photo('photo-1'), photo('photo-2'), photo('photo-3')])
      expect(value.vm.startSlideshow()).assertTrue()
      const timerId = value.scheduler.latestTimerId()

      value.scheduler.fire(timerId)
      expect(value.ui.currentIndex).assertEqual(1)
      expect(value.ui.slideshowActive).assertTrue()

      value.scheduler.fire(timerId)
      expect(value.ui.currentIndex).assertEqual(2)
      expect(value.ui.slideshowActive).assertFalse()
      expect(value.scheduler.cancelled.length).assertEqual(1)
      expect(value.vm.startSlideshow()).assertFalse()
    })

    it('loadsPhotoInformationLazilyAndReusesTheDetailCache', 0, async () => {
      const value = harness([photo('photo-1')])

      expect(await value.vm.showInfo()).assertTrue()
      expect(value.repository.photoCalls.length).assertEqual(1)
      expect(value.ui.infoPhoto?.name).assertEqual('Detail photo-1')
      value.vm.hideInfo()

      expect(await value.vm.showInfo()).assertTrue()
      expect(value.repository.photoCalls.length).assertEqual(1)
      expect(value.ui.infoPhoto?.name).assertEqual('Detail photo-1')
    })

    it('ignoresStaleInformationAfterTheAccountScopeChanges', 0, async () => {
      const value = harness([photo('photo-1'), photo('photo-2')])
      value.repository.deferPhoto = true
      const request = value.vm.showInfo()
      expect(value.ui.infoLoading).assertTrue()

      value.repository.scope = 'server-b|user-b'
      expect(value.vm.goNext()).assertFalse()
      value.repository.resolvePhoto(photo('photo-1', 'Stale detail'))

      expect(await request).assertFalse()
      expect(value.ui.pageState).assertEqual(PageState.Error)
      expect(value.ui.loadErrorCode).assertEqual(PhotoBrowseErrorCode.AuthenticationExpired)
      expect(value.ui.infoPhoto === null).assertTrue()
    })

    it('updatesFavoritesOnlyAfterTheServerConfirmsTheValue', 0, async () => {
      const value = harness([photo('photo-1')])
      value.repository.deferFavorite = true

      const first = value.vm.setCurrentFavorite(true)
      expect(value.ui.photos[0].favorite).assertFalse()
      expect(value.ui.favoritePendingPhotoId).assertEqual('photo-1')
      value.repository.resolveFavorite(false)
      expect(await first).assertTrue()
      expect(value.ui.photos[0].favorite).assertFalse()

      const second = value.vm.setCurrentFavorite(true)
      expect(value.ui.photos[0].favorite).assertFalse()
      value.repository.resolveFavorite(true)
      expect(await second).assertTrue()
      expect(value.ui.photos[0].favorite).assertTrue()
      expect(value.ui.favoritePendingPhotoId).assertEqual('')
    })

    it('ignoresStaleFavoriteResultsAfterTheAccountScopeChanges', 0, async () => {
      const value = harness([photo('photo-1'), photo('photo-2')])
      value.repository.deferFavorite = true
      const originalPhoto = value.ui.photos[0]
      const request = value.vm.setCurrentFavorite(true)

      value.repository.scope = 'server-b|user-b'
      expect(value.vm.goNext()).assertFalse()
      value.repository.resolveFavorite(true)

      expect(await request).assertFalse()
      expect(originalPhoto.favorite).assertFalse()
      expect(value.ui.favoritePendingPhotoId).assertEqual('')
      expect(value.ui.loadErrorCode).assertEqual(PhotoBrowseErrorCode.AuthenticationExpired)
    })

    it('cancelsTimersRemovesTheSourceAndIgnoresPendingWorkOnDispose', 0, async () => {
      const value = harness([photo('photo-1'), photo('photo-2')])
      value.repository.deferPhoto = true
      expect(value.vm.startSlideshow()).assertTrue()
      const request = value.vm.showInfo()

      value.vm.dispose()
      expect(value.scheduler.cancelled.length).assertEqual(1)
      expect(value.store.removeCalls).assertEqual(1)
      expect(value.store.resolve(value.sourceKey, value.repository.scope) === undefined).assertTrue()
      expect(value.ui.infoVisible).assertFalse()

      value.repository.resolvePhoto(photo('photo-1', 'Disposed detail'))
      expect(await request).assertFalse()
      expect(value.ui.infoPhoto === null).assertTrue()
      value.vm.dispose()
      expect(value.store.removeCalls).assertEqual(1)
    })
  })
}
