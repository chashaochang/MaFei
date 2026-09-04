import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { RouterConsts } from '../../common/RouterConsts'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination'
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { MediaItem } from '../home/hometab/HomeUIState'
import { PhotoAlbumGrid } from './PhotoAlbumGrid'
import { PhotoContentState, PhotoLibraryUIState } from './PhotoLibraryUIState'
import { PhotoLibraryViewModel } from './PhotoLibraryViewModel'
import {
  PhotoAlbumItem,
  PhotoItem,
  PhotoLibrarySection,
  PhotoViewerRouteParam
} from './PhotoModels'
import { PhotoGrid } from './PhotoGrid'
import { photoBrowseErrorText } from './PhotoErrorText'
import { PhotoSkeletonGrid } from './PhotoSkeletonGrid'
import { photoViewerSourceStore } from './PhotoViewerSourceStore'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'
import { Constants } from '../../utils/Constants'
import { HomeContentLayoutPolicy } from '../home/HomeContentLayoutPolicy'

@ComponentV2
struct PhotoLibrarySectionContent {
  @Require @Param ui: PhotoLibraryUIState
  @Require @Param section: PhotoLibrarySection
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param scroller: Scroller
  @Require @Param errorText: ResourceStr
  @Event onRefresh: () => void = () => {}
  @Event onRetry: () => void = () => {}
  @Event onLoadMore: () => void = () => {}
  @Event onRetryLoadMore: () => void = () => {}
  @Event onPhotoPressed: (photo: PhotoItem, index: number) => void =
    (_photo: PhotoItem, _index: number) => {}
  @Event onAlbumPressed: (album: PhotoAlbumItem) => void = (_album: PhotoAlbumItem) => {}

  private state(): PhotoContentState {
    return this.section === PhotoLibrarySection.Photos ? this.ui.photosState : this.ui.albumsState
  }

  private refreshing(): boolean {
    return this.section === PhotoLibrarySection.Photos ? this.ui.photosRefreshing : this.ui.albumsRefreshing
  }

  private stateTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  build() {
    Column() {
      if (this.state() === PhotoContentState.Loading) {
        PhotoSkeletonGrid({
          currentBreakpoint: this.currentBreakpoint,
          nativeSurface: this.nativeSurface,
          albumMode: this.section === PhotoLibrarySection.Albums
        })
      } else if (this.state() === PhotoContentState.Empty) {
        Column({ space: 12 }) {
          Image(this.section === PhotoLibrarySection.Albums ?
            $r('app.media.placeholder_folder') : $r('app.media.ic_photo'))
            .width(46)
            .height(46)
            .fillColor(this.stateTextColor())
          Text(this.section === PhotoLibrarySection.Albums ?
            $r('app.string.photo_empty_albums') : $r('app.string.photo_empty_photos'))
            .fontSize(14)
            .fontColor(this.stateTextColor())
          Button($r('app.string.photo_refresh'), { type: ButtonType.Normal })
            .height(44)
            .borderRadius(22)
            .onClick(() => this.onRefresh())
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
      } else if (this.state() === PhotoContentState.Error) {
        Column({ space: 12 }) {
          SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
            .fontSize(42)
            .fontColor([this.stateTextColor()])
          Text(this.errorText)
            .fontSize(14)
            .fontColor(this.stateTextColor())
          Button($r('app.string.photo_retry'), { type: ButtonType.Normal })
            .height(44)
            .borderRadius(22)
            .onClick(() => this.onRetry())
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
      } else {
        Refresh({ refreshing: this.refreshing() }) {
          if (this.section === PhotoLibrarySection.Photos) {
            PhotoGrid({
              groups: this.ui.photoGroups,
              currentBreakpoint: this.currentBreakpoint,
              nativeSurface: this.nativeSurface,
              scroller: this.scroller,
              loadingMore: this.ui.photosLoadingMore,
              loadMoreFailed: this.ui.photosLoadMoreFailed,
              onPhotoClick: (photo: PhotoItem, index: number) => this.onPhotoPressed(photo, index),
              onReachEnd: () => this.onLoadMore(),
              onRetryLoadMore: () => this.onRetryLoadMore()
            })
          } else {
            PhotoAlbumGrid({
              albums: this.ui.albums,
              currentBreakpoint: this.currentBreakpoint,
              nativeSurface: this.nativeSurface,
              scroller: this.scroller,
              loadingMore: this.ui.albumsLoadingMore,
              loadMoreFailed: this.ui.albumsLoadMoreFailed,
              onAlbumClick: (album: PhotoAlbumItem) => this.onAlbumPressed(album),
              onReachEnd: () => this.onLoadMore(),
              onRetryLoadMore: () => this.onRetryLoadMore()
            })
          }
        }
        .onRefreshing(() => this.onRefresh())
      }
    }
    .width('100%')
    .height('100%')
  }
}

@ComponentV2
struct PhotoLibraryContent {
  @Require @Param ui: PhotoLibraryUIState
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param horizontalPadding: number
  @Require @Param bottomInset: number
  @Require @Param tabsController: TabsController
  @Require @Param photosScroller: Scroller
  @Require @Param albumsScroller: Scroller
  @Require @Param photosErrorText: ResourceStr
  @Require @Param albumsErrorText: ResourceStr
  @Event onSectionChange: (index: number) => void = (_index: number) => {}
  @Event onRefresh: () => void = () => {}
  @Event onRetry: () => void = () => {}
  @Event onLoadMore: () => void = () => {}
  @Event onRetryLoadMore: () => void = () => {}
  @Event onPhotoPressed: (photo: PhotoItem, index: number) => void =
    (_photo: PhotoItem, _index: number) => {}
  @Event onAlbumPressed: (album: PhotoAlbumItem) => void = (_album: PhotoAlbumItem) => {}

  build() {
    Tabs({
      index: this.ui.selectedSection,
      controller: this.tabsController
    }) {
      TabContent() {
        PhotoLibrarySectionContent({
          ui: this.ui,
          section: PhotoLibrarySection.Photos,
          currentBreakpoint: this.currentBreakpoint,
          nativeSurface: this.nativeSurface,
          scroller: this.photosScroller,
          errorText: this.photosErrorText,
          onRefresh: () => this.onRefresh(),
          onRetry: () => this.onRetry(),
          onLoadMore: () => this.onLoadMore(),
          onRetryLoadMore: () => this.onRetryLoadMore(),
          onPhotoPressed: (photo: PhotoItem, index: number) => this.onPhotoPressed(photo, index)
        })
      }
      .tabBar($r('app.string.photo_tab_photos'))
      TabContent() {
        PhotoLibrarySectionContent({
          ui: this.ui,
          section: PhotoLibrarySection.Albums,
          currentBreakpoint: this.currentBreakpoint,
          nativeSurface: this.nativeSurface,
          scroller: this.albumsScroller,
          errorText: this.albumsErrorText,
          onRefresh: () => this.onRefresh(),
          onRetry: () => this.onRetry(),
          onLoadMore: () => this.onLoadMore(),
          onRetryLoadMore: () => this.onRetryLoadMore(),
          onAlbumPressed: (album: PhotoAlbumItem) => this.onAlbumPressed(album)
        })
      }
      .tabBar($r('app.string.photo_tab_albums'))
    }
    .width(`calc(100% - ${this.horizontalPadding * 2}vp)`)
    .height('100%')
    .barMode(BarMode.Fixed)
    .scrollable(false)
    .animationDuration(0)
    .padding({ bottom: this.bottomInset })
    .onChange((index: number) => this.onSectionChange(index))
  }
}

@HMRouter({
  pageUrl: RouterConsts.PhotoLibraryPage,
  useNavDst: true
})
@ComponentV2
export struct PhotoLibraryPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Param fromHome: boolean = false
  @Param mediaItem: MediaItem = {} as MediaItem
  @Param contentBottomInset?: number = undefined
  @Local ui: PhotoLibraryUIState = new PhotoLibraryUIState(this.getUIContext())
  @Local vm: PhotoLibraryViewModel = new PhotoLibraryViewModel(this.ui)
  private readonly tabsController: TabsController = new TabsController()
  private readonly photosScroller: Scroller = new Scroller()
  private readonly albumsScroller: Scroller = new Scroller()
  private initialized: boolean = false

  private initEmbeddedMediaItem(force: boolean = false): void {
    const libraryId = this.mediaItem.id || ''
    if (!this.fromHome || libraryId.length === 0 || (!force && this.ui.libraryId === libraryId)) {
      return
    }
    this.initialized = true
    void this.vm.init(libraryId, this.mediaItem.name || '')
  }

  @Monitor('mediaItem')
  onEmbeddedMediaItemChange(): void {
    this.initEmbeddedMediaItem(true)
  }

  aboutToAppear(): void {
    if (this.fromHome) {
      this.initEmbeddedMediaItem()
      return
    }
    if (this.initialized) {
      this.vm.refreshCurrent()
      return
    }
    this.initialized = true
    const param = HMRouterMgr.getCurrentParam() as MediaItem | undefined
    this.vm.init(param?.id || '', param?.name || '')
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private contentBreakpoint(): string {
    return HomeContentLayoutPolicy.breakpoint(
      this.vm.appUIState,
      this.vm.appUIState.homeContentNavigationMounted
    )
  }

  private horizontalPadding(): number {
    return this.contentBreakpoint().includes('s') ? 12 : 24
  }

  private title(): ResourceStr {
    return this.ui.libraryName.length > 0 ? this.ui.libraryName : $r('app.string.photo_library_title')
  }

  private menus(): Array<NavigationMenuItem> {
    return [{
      value: $r('app.string.photo_refresh'),
      icon: $r('sys.symbol.arrow_clockwise'),
      action: () => this.vm.refreshCurrent()
    }]
  }

  private openPhoto(photo: PhotoItem, _index: number): void {
    const sourceKey = photoViewerSourceStore.save(
      this.vm.viewerScope(), this.ui.libraryName, this.ui.photos)
    if (sourceKey.length === 0) {
      return
    }
    const params: PhotoViewerRouteParam = {
      sourceKey: sourceKey,
      initialPhotoId: photo.id
    }
    HMRouterMgr.to(RouterConsts.PhotoViewerPage)
      .withNavigation(Constants.NAVIGATION_ID)
      .withParam(params)
      .push()
  }

  private openAlbum(album: PhotoAlbumItem): void {
    HMRouterMgr.to(RouterConsts.PhotoAlbumPage)
      .withParam({ albumId: album.id, title: album.name })
      .push()
  }

  private bottomInset(): number {
    return this.fromHome && this.contentBottomInset !== undefined ?
      Math.max(0, this.contentBottomInset) : Math.max(0, this.vm.appUIState.safeBottom)
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
          title: this.title(),
          menus: this.menus(),
          scrollControllers: [this.photosScroller, this.albumsScroller]
        }
  }

  build() {
    if (this.fromHome) {
      PhotoLibraryContent({
        ui: this.ui,
        currentBreakpoint: this.contentBreakpoint(),
        nativeSurface: this.useNativeSurface(),
        horizontalPadding: this.horizontalPadding(),
        bottomInset: this.bottomInset(),
        tabsController: this.tabsController,
        photosScroller: this.photosScroller,
        albumsScroller: this.albumsScroller,
        photosErrorText: photoBrowseErrorText(this.ui.photosErrorCode),
        albumsErrorText: photoBrowseErrorText(this.ui.albumsErrorCode),
        onSectionChange: (index: number) => this.vm.selectSection(index),
        onRefresh: () => this.vm.refreshCurrent(),
        onRetry: () => this.vm.retryCurrent(),
        onLoadMore: () => this.vm.loadMoreCurrent(),
        onRetryLoadMore: () => this.vm.retryLoadMoreCurrent(),
        onPhotoPressed: (photo: PhotoItem, index: number) => this.openPhoto(photo, index),
        onAlbumPressed: (album: PhotoAlbumItem) => this.openAlbum(album)
      })
    } else {
      if (this.routeDestination.usesHdsDestination()) {
        HdsNavDestination() {
          Column() {
            Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
            Stack() {
              if (this.routeDestination.usesNativeChrome()) {
                PhotoLibraryContent({
                  ui: this.ui,
                  currentBreakpoint: this.contentBreakpoint(),
                  nativeSurface: true,
                  horizontalPadding: this.horizontalPadding(),
                  bottomInset: this.bottomInset(),
                  tabsController: this.tabsController,
                  photosScroller: this.photosScroller,
                  albumsScroller: this.albumsScroller,
                  photosErrorText: photoBrowseErrorText(this.ui.photosErrorCode),
                  albumsErrorText: photoBrowseErrorText(this.ui.albumsErrorCode),
                  onSectionChange: (index: number) => this.vm.selectSection(index),
                  onRefresh: () => this.vm.refreshCurrent(),
                  onRetry: () => this.vm.retryCurrent(),
                  onLoadMore: () => this.vm.loadMoreCurrent(),
                  onRetryLoadMore: () => this.vm.retryLoadMoreCurrent(),
                  onPhotoPressed: (photo: PhotoItem, index: number) => this.openPhoto(photo, index),
                  onAlbumPressed: (album: PhotoAlbumItem) => this.openAlbum(album)
                })
              } else {
                PhotoLibraryContent({
                  ui: this.ui,
                  currentBreakpoint: this.contentBreakpoint(),
                  nativeSurface: false,
                  horizontalPadding: this.horizontalPadding(),
                  bottomInset: this.bottomInset(),
                  tabsController: this.tabsController,
                  photosScroller: this.photosScroller,
                  albumsScroller: this.albumsScroller,
                  photosErrorText: photoBrowseErrorText(this.ui.photosErrorCode),
                  albumsErrorText: photoBrowseErrorText(this.ui.albumsErrorCode),
                  onSectionChange: (index: number) => this.vm.selectSection(index),
                  onRefresh: () => this.vm.refreshCurrent(),
                  onRetry: () => this.vm.retryCurrent(),
                  onLoadMore: () => this.vm.loadMoreCurrent(),
                  onRetryLoadMore: () => this.vm.retryLoadMoreCurrent(),
                  onPhotoPressed: (photo: PhotoItem, index: number) => this.openPhoto(photo, index),
                  onAlbumPressed: (album: PhotoAlbumItem) => this.openAlbum(album)
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
          PhotoLibraryContent({
            ui: this.ui,
            currentBreakpoint: this.contentBreakpoint(),
            nativeSurface: false,
            horizontalPadding: this.horizontalPadding(),
            bottomInset: this.bottomInset(),
            tabsController: this.tabsController,
            photosScroller: this.photosScroller,
            albumsScroller: this.albumsScroller,
            photosErrorText: photoBrowseErrorText(this.ui.photosErrorCode),
            albumsErrorText: photoBrowseErrorText(this.ui.albumsErrorCode),
            onSectionChange: (index: number) => this.vm.selectSection(index),
            onRefresh: () => this.vm.refreshCurrent(),
            onRetry: () => this.vm.retryCurrent(),
            onLoadMore: () => this.vm.loadMoreCurrent(),
            onRetryLoadMore: () => this.vm.retryLoadMoreCurrent(),
            onPhotoPressed: (photo: PhotoItem, index: number) => this.openPhoto(photo, index),
            onAlbumPressed: (album: PhotoAlbumItem) => this.openAlbum(album)
          })
        }
        .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
        .gestureModifier(this.routeDestination.gestureModifier)
        .width('100%')
        .height('100%')
      }
    }
  }
}
