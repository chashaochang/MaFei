import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { RouterConsts } from '../../common/RouterConsts'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination'
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
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

interface PhotoAlbumRouteParams {
  albumId: string
  title: string
}

@ComponentV2
struct PhotoAlbumContent {
  @Require @Param ui: PhotoAlbumUIState
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param horizontalPadding: number
  @Require @Param scroller: Scroller
  @Require @Param safeBottom: number
  @Event onRefresh: () => void = () => {}
  @Event onRetry: () => void = () => {}
  @Event onLoadMore: () => void = () => {}
  @Event onRetryLoadMore: () => void = () => {}
  @Event onPhotoPressed: (photo: PhotoItem, index: number) => void =
    (_photo: PhotoItem, _index: number) => {}

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  build() {
    Column() {
      if (this.ui.state === PhotoAlbumContentState.Loading) {
        PhotoSkeletonGrid({
          currentBreakpoint: this.currentBreakpoint,
          nativeSurface: this.nativeSurface
        })
      } else if (this.ui.state === PhotoAlbumContentState.Empty) {
        Column({ space: 12 }) {
          Image($r('app.media.ic_photo'))
            .width(46)
            .height(46)
            .fillColor(this.secondaryTextColor())
          Text($r('app.string.photo_empty_photos'))
            .fontSize(14)
            .fontColor(this.secondaryTextColor())
          Button($r('app.string.photo_refresh'), { type: ButtonType.Normal })
            .height(44)
            .borderRadius(22)
            .onClick(() => this.onRefresh())
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
      } else if (this.ui.state === PhotoAlbumContentState.Error) {
        Column({ space: 12 }) {
          SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
            .fontSize(42)
            .fontColor([this.secondaryTextColor()])
          Text(photoBrowseErrorText(this.ui.errorCode))
            .fontSize(14)
            .fontColor(this.secondaryTextColor())
          Button($r('app.string.photo_retry'), { type: ButtonType.Normal })
            .height(44)
            .borderRadius(22)
            .onClick(() => this.onRetry())
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
      } else {
        Refresh({ refreshing: this.ui.refreshing }) {
          PhotoGrid({
            groups: this.ui.photoGroups,
            currentBreakpoint: this.currentBreakpoint,
            nativeSurface: this.nativeSurface,
            scroller: this.scroller,
            loadingMore: this.ui.loadingMore,
            loadMoreFailed: this.ui.loadMoreFailed,
            onPhotoClick: (photo: PhotoItem, index: number) => this.onPhotoPressed(photo, index),
            onReachEnd: () => this.onLoadMore(),
            onRetryLoadMore: () => this.onRetryLoadMore()
          })
        }
        .onRefreshing(() => this.onRefresh())
      }
    }
    .width(`calc(100% - ${this.horizontalPadding * 2}vp)`)
    .height('100%')
    .padding({ bottom: Math.max(0, this.safeBottom) })
  }
}

@HMRouter({
  pageUrl: RouterConsts.PhotoAlbumPage,
  useNavDst: true
})
@ComponentV2
export struct PhotoAlbumPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
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

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: this.title(),
        menus: [{
                value: $r('app.string.photo_refresh'),
                icon: $r('sys.symbol.arrow_clockwise'),
                action: () => this.vm.refresh()
              }],
        scrollControllers: [this.scroller]
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              PhotoAlbumContent({
                ui: this.ui,
                currentBreakpoint: this.vm.appUIState.currentBreakpoint,
                nativeSurface: true,
                horizontalPadding: this.horizontalPadding(),
                scroller: this.scroller,
                safeBottom: this.vm.appUIState.safeBottom,
                onRefresh: () => this.vm.refresh(),
                onRetry: () => this.vm.retry(),
                onLoadMore: () => this.vm.loadMore(),
                onRetryLoadMore: () => this.vm.retryLoadMore(),
                onPhotoPressed: (photo: PhotoItem, index: number) => this.openPhoto(photo, index)
              })
            } else {
              PhotoAlbumContent({
                ui: this.ui,
                currentBreakpoint: this.vm.appUIState.currentBreakpoint,
                nativeSurface: false,
                horizontalPadding: this.horizontalPadding(),
                scroller: this.scroller,
                safeBottom: this.vm.appUIState.safeBottom,
                onRefresh: () => this.vm.refresh(),
                onRetry: () => this.vm.retry(),
                onLoadMore: () => this.vm.loadMore(),
                onRetryLoadMore: () => this.vm.retryLoadMore(),
                onPhotoPressed: (photo: PhotoItem, index: number) => this.openPhoto(photo, index)
              })
            }
          }
          .width('100%')
          .layoutWeight(1)
        }
        .width('100%')
        .height('100%')
      }
      .backgroundColor(this.routeDestination.hdsBackgroundColor())
      .titleMode(HdsNavDestinationTitleMode.MINI)
      .titleBar(this.routeDestination.nativeTitleBarOptions(this.routeOptions()))
      .hideTitleBar(this.routeDestination.hdsTitleBarHidden(this.routeOptions()))
      .hideBackButton(!this.routeDestination.backButtonVisible(this.routeOptions()))
      .hideToolBar(true)
      .bindToScrollable(this.routeDestination.hdsBoundScrollers(this.routeOptions()))
      .onWillAppear(() => this.routeDestination.helper.onWillAppear())
      .onAppear(() => this.routeDestination.helper.onAppear())
      .onWillShow(() => this.routeDestination.helper.onWillShow())
      .onShown(() => this.routeDestination.helper.onShown())
      .onWillHide(() => this.routeDestination.helper.onWillHide())
      .onHidden(() => this.routeDestination.helper.onHidden())
      .onWillDisappear(() => this.routeDestination.helper.onWillDisappear())
      .onDisAppear(() => this.routeDestination.helper.onDisAppear())
      .onReady((ctx: NavDestinationContext) => this.routeDestination.helper.onReady(ctx))
      .onBackPressed(() => this.routeDestination.handleBackPressed(this.routeOptions()))
      .onResult((result: ESObject) => this.routeDestination.helper.onResult(result))
      .onActive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onActive(reason))
      .onInactive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onInactive(reason))
      .ignoreLayoutSafeArea(
        this.routeDestination.nativeSafeAreaTypes(),
        this.routeDestination.nativeSafeAreaEdges()
      )
      .width('100%')
      .height('100%')
    } else {
      NavDestination() {
        PhotoAlbumContent({
          ui: this.ui,
          currentBreakpoint: this.vm.appUIState.currentBreakpoint,
          nativeSurface: false,
          horizontalPadding: this.horizontalPadding(),
          scroller: this.scroller,
          safeBottom: this.vm.appUIState.safeBottom,
          onRefresh: () => this.vm.refresh(),
          onRetry: () => this.vm.retry(),
          onLoadMore: () => this.vm.loadMore(),
          onRetryLoadMore: () => this.vm.retryLoadMore(),
          onPhotoPressed: (photo: PhotoItem, index: number) => this.openPhoto(photo, index)
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
