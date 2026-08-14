import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { RouterConsts } from '../../common/RouterConsts'
import { AppRouteDestination } from '../../component/AppRouteDestination'
import { PhotoAlbumContentState, PhotoAlbumUIState } from './PhotoAlbumUIState'
import { PhotoAlbumViewModel } from './PhotoAlbumViewModel'
import {
  PhotoItem,
  PhotoViewerRouteParam
} from './PhotoModels'
import { PhotoGrid } from './PhotoGrid'
import { photoBrowseErrorText } from './PhotoErrorText'
import { PhotoSkeletonGrid } from './PhotoSkeletonGrid'
import { photoViewerSourceStore } from './PhotoViewerSourceStore'

interface PhotoAlbumRouteParams {
  albumId: string
  title: string
}

@HMRouter({
  pageUrl: RouterConsts.PhotoAlbumPage,
  useNavDst: true
})
@ComponentV2
export struct PhotoAlbumPage {
  @Local ui: PhotoAlbumUIState = new PhotoAlbumUIState(this.getUIContext())
  @Local vm: PhotoAlbumViewModel = new PhotoAlbumViewModel(this.ui)
  private readonly scroller: Scroller = new Scroller()
  private initialized: boolean = false

  aboutToAppear(): void {
    if (this.initialized) {
      this.vm.refresh()
      return
    }
    this.initialized = true
    const param = HMRouterMgr.getCurrentParam() as PhotoAlbumRouteParams | undefined
    this.vm.init(param?.albumId || '', param?.title || '')
  }

  private title(): ResourceStr {
    return this.ui.albumName.length > 0 ? this.ui.albumName : $r('app.string.photo_tab_albums')
  }

  private horizontalPadding(): number {
    return this.vm.appUIState.currentBreakpoint.includes('s') ? 12 : 24
  }

  private openPhoto(photo: PhotoItem, _index: number): void {
    const sourceKey = photoViewerSourceStore.save(
      this.vm.viewerScope(), this.ui.albumName, this.ui.photos)
    if (sourceKey.length === 0) {
      return
    }
    const params: PhotoViewerRouteParam = {
      sourceKey: sourceKey,
      initialPhotoId: photo.id
    }
    HMRouterMgr.to(RouterConsts.PhotoViewerPage).withParam(params).push()
  }

  @Builder
  private loading(nativeSurface: boolean) {
    PhotoSkeletonGrid({
      currentBreakpoint: this.vm.appUIState.currentBreakpoint,
      nativeSurface: nativeSurface
    })
  }

  @Builder
  private empty(nativeSurface: boolean) {
    Column({ space: 12 }) {
      Image($r('app.media.ic_photo'))
        .width(46)
        .height(46)
        .fillColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
      Text($r('app.string.photo_empty_photos'))
        .fontSize(14)
        .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
      Button($r('app.string.photo_refresh'), { type: ButtonType.Normal })
        .height(44)
        .borderRadius(22)
        .onClick(() => this.vm.refresh())
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private error(nativeSurface: boolean) {
    Column({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
        .fontSize(42)
        .fontColor([nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')])
      Text(photoBrowseErrorText(this.ui.errorCode))
        .fontSize(14)
        .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
      Button($r('app.string.photo_retry'), { type: ButtonType.Normal })
        .height(44)
        .borderRadius(22)
        .onClick(() => this.vm.retry())
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private pageContent(nativeSurface: boolean) {
    Column() {
      if (this.ui.state === PhotoAlbumContentState.Loading) {
        this.loading(nativeSurface)
      } else if (this.ui.state === PhotoAlbumContentState.Empty) {
        this.empty(nativeSurface)
      } else if (this.ui.state === PhotoAlbumContentState.Error) {
        this.error(nativeSurface)
      } else {
        Refresh({ refreshing: $$this.ui.refreshing }) {
          PhotoGrid({
            groups: this.ui.photoGroups,
            currentBreakpoint: this.vm.appUIState.currentBreakpoint,
            nativeSurface: nativeSurface,
            scroller: this.scroller,
            loadingMore: this.ui.loadingMore,
            loadMoreFailed: this.ui.loadMoreFailed,
            onPhotoClick: (photo: PhotoItem, index: number) => this.openPhoto(photo, index),
            onReachEnd: () => this.vm.loadMore(),
            onRetryLoadMore: () => this.vm.retryLoadMore()
          })
        }
        .onRefreshing(() => this.vm.refresh())
      }
    }
    .width(`calc(100% - ${this.horizontalPadding() * 2}vp)`)
    .height('100%')
    .padding({ bottom: Math.max(0, this.vm.appUIState.safeBottom) })
  }

  build() {
    AppRouteDestination({
      title: this.title(),
      menus: [{
        value: $r('app.string.photo_refresh'),
        icon: $r('sys.symbol.arrow_clockwise'),
        action: () => this.vm.refresh()
      }],
      scrollControllers: [this.scroller],
      contentBuilder: () => {
        this.pageContent(true)
      },
      legacyContentBuilder: () => {
        this.pageContent(false)
      }
    })
  }
}
