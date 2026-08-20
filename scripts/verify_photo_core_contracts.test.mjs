import assert from 'node:assert/strict'
import test from 'node:test'
import {
  REQUIRED_PHOTO_STRING_KEYS,
  validatePhotoCoreContracts,
  validateWorkspacePhotoCoreContracts
} from './verify_photo_core_contracts.mjs'

const routerPath = 'entry/src/main/ets/common/RouterConsts.ets'
const mediaTabPath = 'entry/src/main/ets/features/home/mediatab/MediaTab.ets'
const homePath = 'entry/src/main/ets/features/home/HomeScreen.ets'
const modulePath = 'entry/src/main/module.json5'
const modelsPath = 'entry/src/main/ets/features/photo/PhotoModels.ets'
const mapperPath = 'entry/src/main/ets/features/photo/PhotoMapper.ets'
const plannerPath = 'entry/src/main/ets/features/photo/PhotoQueryPlanner.ets'
const repositoryPath = 'entry/src/main/ets/features/photo/PhotoRepository.ets'
const libraryPath = 'entry/src/main/ets/features/photo/PhotoLibraryPage.ets'
const albumPath = 'entry/src/main/ets/features/photo/PhotoAlbumPage.ets'
const viewerPath = 'entry/src/main/ets/features/photo/PhotoViewerPage.ets'
const zoomPath = 'entry/src/main/ets/features/photo/PhotoZoomableImage.ets'
const infoPath = 'entry/src/main/ets/features/photo/PhotoInfoSheet.ets'
const savePath = 'entry/src/main/ets/features/photo/PhotoSaveService.ets'
const storePath = 'entry/src/main/ets/features/photo/PhotoViewerSourceStore.ets'
const resourcePaths = [
  'entry/src/main/resources/base/element/string.json',
  'entry/src/main/resources/zh_CN/element/string.json',
  'entry/src/main/resources/en_US/element/string.json'
]

function resourceSource(keys = REQUIRED_PHOTO_STRING_KEYS) {
  return JSON.stringify({
    string: keys.map((name) => ({ name, value: name }))
  })
}

function photoPage(route, openPhoto = '') {
  return `
    @HMRouter({ pageUrl: RouterConsts.${route}, useNavDst: true })
    struct ${route} {
      ${openPhoto}
    }
  `
}

function openPhotoSource() {
  return `
    private openPhoto(photo: PhotoItem, index: number): void {
      const sourceKey = photoViewerSourceStore.save(this.vm.viewerScope(), 'Photos', this.ui.photos)
      const params: PhotoViewerRouteParam = {
        sourceKey: sourceKey,
        initialPhotoId: photo.id
      }
      HMRouterMgr.to(RouterConsts.PhotoViewerPage).withParam(params).push()
    }
  `
}

function validPhotoFixture() {
  const sources = new Map()
  sources.set(routerPath, `
    static readonly PhotoLibraryPage = 'PhotoLibraryPage'
    static readonly PhotoAlbumPage = 'PhotoAlbumPage'
    static readonly PhotoViewerPage = 'PhotoViewerPage'
  `)
  sources.set(mediaTabPath, `
    private openLibrary(item: MediaItem): void {
      if (item.libraryKind === MediaLibraryKind.Photos) {
        HMRouterMgr.to(RouterConsts.PhotoLibraryPage).withParam(item).push()
        return
      }
      if (item.libraryKind === MediaLibraryKind.Music) {
        HMRouterMgr.to(RouterConsts.MusicLibraryPage).withParam(item).push()
        return
      }
      if (item.libraryKind === MediaLibraryKind.Movies || item.libraryKind === MediaLibraryKind.Series) {
        HMRouterMgr.to(RouterConsts.VideoListPage).withParam(item).push()
        return
      }
      showToast({ message: $r('app.string.media_library_type_unsupported') })
    }
  `)
  sources.set(homePath, `
    if (item.libraryKind === MediaLibraryKind.Photos) {
      PhotoLibraryPage({
        fromHome: true,
        mediaItem: item
      })
    }
  `)
  sources.set(modulePath, `{ requestPermissions: [{ name: 'ohos.permission.INTERNET' }] }`)
  sources.set(modelsPath, `
    export interface PhotoViewerRouteParam {
      sourceKey: string
      initialPhotoId: string
    }
  `)
  sources.set(mapperPath, `
    const PHOTO_THUMBNAIL_SIZE: number = 720
    const PHOTO_FULL_IMAGE_SIZE: number = 4096
    class PhotoMapper {
      thumbnailUrl = this.imageUrl(id, tag, PHOTO_THUMBNAIL_SIZE, ImageType.Primary)
      fullImageUrl = this.imageUrl(id, tag, PHOTO_FULL_IMAGE_SIZE, ImageType.Primary)
      itemCount = PhotoMapper.nonNegativeNumber(item.RecursiveItemCount ?? item.ChildCount)
    }
  `)
  sources.set(plannerPath, `
    class PhotoQueryPlanner {
      static sortBy() {
        return [ItemSortBy.PremiereDate, ItemSortBy.DateCreated, ItemSortBy.SortName]
      }
      static sortOrder() {
        return [SortOrder.Descending, SortOrder.Descending, SortOrder.Ascending]
      }
    }
  `)
  sources.set(repositoryPath, `
    class PhotoRepository {
      getPhotos() {
        return mapper.page(items, resultStartIndex, sourceItems.length, totalCount)
      }
      getAlbums() {
        return mapper.page(items, resultStartIndex, sourceItems.length, totalCount)
      }
    }
  `)
  sources.set(libraryPath, photoPage('PhotoLibraryPage', openPhotoSource()))
  sources.set(albumPath, photoPage('PhotoAlbumPage', openPhotoSource()))
  sources.set(viewerPath, `
    @HMRouter({ pageUrl: RouterConsts.PhotoViewerPage, useNavDst: true })
    struct PhotoViewerPage {
      private active: boolean = true
      private favoriteEpoch: number = 0
      private floatingButton(): void {
        if (this.useNativeSurface()) {
          Button()
            .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.InteractiveFloating))
            .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
        } else {
          Button()
            .backgroundColor('#66000000')
            .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
        }
      }
      private saveButton(): void {
        Row() {
          SaveButton()
        }
        .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
      }
      private controls(): void {
        Column()
          .hitTestBehavior(HitTestMode.None)
      }
      private toggleFavorite(): void {
        const requestEpoch = ++this.favoriteEpoch
        this.vm.toggleCurrentFavorite().then(() => {
          if (!this.active || requestEpoch !== this.favoriteEpoch) {
            return
          }
        })
      }
      aboutToDisappear(): void {
        this.active = false
        this.favoriteEpoch += 1
      }
      build() {
        const param = HMRouterMgr.getCurrentParam() as PhotoViewerRouteParam
        Stack() {
          Swiper() {
            PhotoZoomableImage()
          }
          this.saveButton()
          if (this.useNativeSurface()) {
            this.floatingButton()
          } else {
            this.floatingButton()
          }
        }
        .backgroundColor(Color.Black)
        .bindSheet($$this.ui.infoVisible, {
          builder: () => {
            PhotoInfoSheet()
          }
        })
        AppRouteDestination({
          contentBuilder: () => {
            this.viewerSurface(true)
          },
          legacyContentBuilder: () => {
            this.viewerSurface(false)
          }
        })
      }
    }
  `)
  sources.set(zoomPath, `
    const PHOTO_VIEWER_MIN_SCALE: number = 1
    const PHOTO_VIEWER_PAN_GESTURE_TAG: string = 'photo-viewer-image-pan'
    struct PhotoZoomableImage {
      zoomScale: number = PHOTO_VIEWER_MIN_SCALE
      build() {
        Stack()
          .gesture(PanGesture({ fingers: 1 })
            .tag(PHOTO_VIEWER_PAN_GESTURE_TAG))
          .onGestureRecognizerJudgeBegin((_event, current) => {
            if (current.getTag() === PHOTO_VIEWER_PAN_GESTURE_TAG &&
              this.zoomScale <= PHOTO_VIEWER_MIN_SCALE) {
              return GestureJudgeResult.REJECT
            }
            return GestureJudgeResult.CONTINUE
          })
      }
    }
  `)
  sources.set(infoPath, 'struct PhotoInfoSheet {}')
  sources.set(savePath, `
    export enum PhotoSaveErrorCode {
      None,
      DownloadNotAllowed,
      DownloadFailed
    }
    class PhotoSaveService {
      importPhoto() {
        const request = photoAccessHelper.MediaAssetChangeRequest.createImageAssetRequest(context, uri)
        helper.applyChanges(request)
      }
      async save(photo: PhotoItem): Promise<void> {
        try {
          await this.downloadPort.download(photo.id, filePath)
        } finally {
          this.tempFilePort.remove(filePath)
        }
      }
    }
  `)
  sources.set(storePath, `
    class PhotoViewerSourceStore {
      resolve(sourceKey: string, scope: string): PhotoViewerSourceSnapshot | undefined {
        const normalizedScope = scope.trim()
        const source = this.sources.get(sourceKey)
        if (!source || source.scope !== normalizedScope) {
          return undefined
        }
        return source
      }
    }
  `)
  for (const path of resourcePaths) {
    sources.set(path, resourceSource())
  }
  return { sources }
}

test('workspace photo contracts are complete', () => {
  assert.doesNotThrow(() => validateWorkspacePhotoCoreContracts())
})

test('accepts the complete photo fixture', () => {
  assert.doesNotThrow(() => validatePhotoCoreContracts(validPhotoFixture()))
})

test('rejects a missing photo route', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(routerPath, fixture.sources.get(routerPath).replace(
    "static readonly PhotoAlbumPage = 'PhotoAlbumPage'", ''))
  assert.throws(() => validatePhotoCoreContracts(fixture), /photo route constant/)
})

test('rejects routing photo libraries through the video list', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(mediaTabPath, fixture.sources.get(mediaTabPath).replace(
    'item.libraryKind === MediaLibraryKind.Movies',
    'item.libraryKind === MediaLibraryKind.Photos || item.libraryKind === MediaLibraryKind.Movies'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /photo libraries must not reuse video routing/)
})

test('rejects serializing photos into the viewer route payload', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(modelsPath, fixture.sources.get(modelsPath).replace(
    'initialPhotoId: string', 'initialPhotoId: string\n      photos: PhotoItem[]'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /payload must contain only/)
})

test('rejects changing the thumbnail and full-image limits', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(mapperPath, fixture.sources.get(mapperPath).replace('= 4096', '= 8192'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /720px thumbnails and 4096px/)
})

test('rejects preferring direct child counts over recursive album counts', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(mapperPath, fixture.sources.get(mapperPath).replace(
    'item.RecursiveItemCount ?? item.ChildCount', 'item.ChildCount ?? item.RecursiveItemCount'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /prefer RecursiveItemCount/)
})

test('rejects unstable photo sorting', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(plannerPath, fixture.sources.get(plannerPath).replace(
    'ItemSortBy.PremiereDate, ItemSortBy.DateCreated, ItemSortBy.SortName',
    'ItemSortBy.SortName'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /sorting must remain stable/)
})

test('rejects pagination based on filtered items', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(repositoryPath, fixture.sources.get(repositoryPath).replaceAll(
    'sourceItems.length', 'items.length'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /raw server item count/)
})

test('rejects a second viewer Swiper', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(viewerPath, fixture.sources.get(viewerPath).replace(
    'PhotoZoomableImage()', 'PhotoZoomableImage()\n            Swiper() {}'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /exactly one Swiper/)
})

test('rejects removing the system SaveButton', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(viewerPath, fixture.sources.get(viewerPath).replace('SaveButton()', 'Button()'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /system SaveButton/)
})

test('rejects an unzoomed image Pan that can compete with the viewer Swiper', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(zoomPath, fixture.sources.get(zoomPath).replace(
    '.tag(PHOTO_VIEWER_PAN_GESTURE_TAG)', '.tag(\'other-pan\')'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /unzoomed image Pan/)
})

test('rejects viewer controls that can click through to the photo', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(viewerPath, fixture.sources.get(viewerPath).replace(
    '.hitTestBehavior(HitTestMode.None)', '.hitTestBehavior(HitTestMode.Transparent)'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /control overlay/)
})

test('rejects favorite callbacks that remain live after leaving the viewer', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(viewerPath, fixture.sources.get(viewerPath).replace(
    'this.favoriteEpoch += 1', 'this.onFavoriteEpochUnchanged()'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /favorite callbacks/)
})

test('rejects save flows that do not clean up temporary files', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(savePath, fixture.sources.get(savePath).replace(
    'this.tempFilePort.remove(filePath)', 'this.onCleanupSkipped()'))
  assert.throws(() => validatePhotoCoreContracts(fixture), /always clean up/)
})

test('rejects viewer references to undeclared save error codes', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(viewerPath,
    fixture.sources.get(viewerPath) + '\nPhotoSaveErrorCode.NotDeclared')
  assert.throws(() => validatePhotoCoreContracts(fixture), /undeclared photo save error code/)
})

test('rejects long-term image library permissions', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(modulePath,
    `{ requestPermissions: [{ name: 'ohos.permission.WRITE_IMAGEVIDEO' }] }`)
  assert.throws(() => validatePhotoCoreContracts(fixture), /must not request long-term/)
})

test('rejects API 26 material construction in a legacy viewer branch', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(viewerPath, fixture.sources.get(viewerPath).replace(
    'private saveButton(): void {', `private legacyControls(): void {
        Button().systemMaterial(
          AppThemeSurfaceResolver.material(AppThemeMaterialRole.InteractiveFloating))
      }
      private saveButton(): void {`))
  assert.throws(() => validatePhotoCoreContracts(fixture), /legacy viewer branch/)
})

test('rejects photo resource locale drift', () => {
  const fixture = validPhotoFixture()
  fixture.sources.set(resourcePaths[2], resourceSource(REQUIRED_PHOTO_STRING_KEYS.slice(1)))
  assert.throws(() => validatePhotoCoreContracts(fixture), /equal key sets/)
})
