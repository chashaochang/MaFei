import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { RouterConsts } from '../../common/RouterConsts'
import { AppRouteDestination } from '../../component/AppRouteDestination'
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

@HMRouter({
  pageUrl: RouterConsts.PhotoLibraryPage,
  useNavDst: true
})
@ComponentV2
export struct PhotoLibraryPage {
  @Param fromHome: boolean = false
  @Param mediaItem: MediaItem = {} as MediaItem
  @Param contentBottomInset?: number = undefined
  @Local ui: PhotoLibraryUIState = new PhotoLibraryUIState(this.getUIContext())
  @Local vm: PhotoLibraryViewModel = new PhotoLibraryViewModel(this.ui)
  private readonly tabsController: TabsController = new TabsController()
  private readonly photosScroller: Scroller = new Scroller()
  private readonly albumsScroller: Scroller = new Scroller()
  private initialized: boolean = false

  aboutToAppear(): void {
    if (this.initialized) {
      this.vm.refreshCurrent()
      return
    }
    this.initialized = true
    if (this.fromHome) {
      this.vm.init(this.mediaItem.id || '', this.mediaItem.name || '')
      return
    }
    const param = HMRouterMgr.getCurrentParam() as MediaItem | undefined
    this.vm.init(param?.id || '', param?.name || '')
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private horizontalPadding(): number {
    return this.vm.appUIState.currentBreakpoint.includes('s') ? 12 : 24
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
    HMRouterMgr.to(RouterConsts.PhotoViewerPage).withParam(params).push()
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

  private stateTextColor(nativeSurface: boolean): ResourceColor {
    return nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private currentErrorText(): ResourceStr {
    return photoBrowseErrorText(this.ui.selectedSection === PhotoLibrarySection.Albums ?
      this.ui.albumsErrorCode : this.ui.photosErrorCode)
  }

  @Builder
  private emptyState(section: PhotoLibrarySection, nativeSurface: boolean) {
    Column({ space: 12 }) {
      Image(section === PhotoLibrarySection.Albums ?
        $r('app.media.placeholder_folder') : $r('app.media.ic_photo'))
        .width(46)
        .height(46)
        .fillColor(this.stateTextColor(nativeSurface))
      Text(section === PhotoLibrarySection.Albums ?
        $r('app.string.photo_empty_albums') : $r('app.string.photo_empty_photos'))
        .fontSize(14)
        .fontColor(this.stateTextColor(nativeSurface))
      Button($r('app.string.photo_refresh'), { type: ButtonType.Normal })
        .height(44)
        .borderRadius(22)
        .onClick(() => this.vm.refreshCurrent())
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private errorState(nativeSurface: boolean) {
    Column({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
        .fontSize(42)
        .fontColor([this.stateTextColor(nativeSurface)])
      Text(this.currentErrorText())
        .fontSize(14)
        .fontColor(this.stateTextColor(nativeSurface))
      Button($r('app.string.photo_retry'), { type: ButtonType.Normal })
        .height(44)
        .borderRadius(22)
        .onClick(() => this.vm.retryCurrent())
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private photosContent(nativeSurface: boolean) {
    if (this.ui.photosState === PhotoContentState.Loading) {
      PhotoSkeletonGrid({
        currentBreakpoint: this.vm.appUIState.currentBreakpoint,
        nativeSurface: nativeSurface
      })
    } else if (this.ui.photosState === PhotoContentState.Empty) {
      this.emptyState(PhotoLibrarySection.Photos, nativeSurface)
    } else if (this.ui.photosState === PhotoContentState.Error) {
      this.errorState(nativeSurface)
    } else {
      Refresh({ refreshing: $$this.ui.photosRefreshing }) {
        PhotoGrid({
          groups: this.ui.photoGroups,
          currentBreakpoint: this.vm.appUIState.currentBreakpoint,
          nativeSurface: nativeSurface,
          scroller: this.photosScroller,
          loadingMore: this.ui.photosLoadingMore,
          loadMoreFailed: this.ui.photosLoadMoreFailed,
          onPhotoClick: (photo: PhotoItem, index: number) => this.openPhoto(photo, index),
          onReachEnd: () => this.vm.loadMoreCurrent(),
          onRetryLoadMore: () => this.vm.retryLoadMoreCurrent()
        })
      }
      .onRefreshing(() => this.vm.refreshCurrent())
    }
  }

  @Builder
  private albumsContent(nativeSurface: boolean) {
    if (this.ui.albumsState === PhotoContentState.Loading) {
      PhotoSkeletonGrid({
        currentBreakpoint: this.vm.appUIState.currentBreakpoint,
        nativeSurface: nativeSurface,
        albumMode: true
      })
    } else if (this.ui.albumsState === PhotoContentState.Empty) {
      this.emptyState(PhotoLibrarySection.Albums, nativeSurface)
    } else if (this.ui.albumsState === PhotoContentState.Error) {
      this.errorState(nativeSurface)
    } else {
      Refresh({ refreshing: $$this.ui.albumsRefreshing }) {
        PhotoAlbumGrid({
          albums: this.ui.albums,
          currentBreakpoint: this.vm.appUIState.currentBreakpoint,
          nativeSurface: nativeSurface,
          scroller: this.albumsScroller,
          loadingMore: this.ui.albumsLoadingMore,
          loadMoreFailed: this.ui.albumsLoadMoreFailed,
          onAlbumClick: (album: PhotoAlbumItem) => this.openAlbum(album),
          onReachEnd: () => this.vm.loadMoreCurrent(),
          onRetryLoadMore: () => this.vm.retryLoadMoreCurrent()
        })
      }
      .onRefreshing(() => this.vm.refreshCurrent())
    }
  }

  @Builder
  private pageContent(nativeSurface: boolean) {
    Tabs({
      index: this.ui.selectedSection,
      controller: this.tabsController
    }) {
      TabContent() {
        this.photosContent(nativeSurface)
      }
      .tabBar($r('app.string.photo_tab_photos'))
      TabContent() {
        this.albumsContent(nativeSurface)
      }
      .tabBar($r('app.string.photo_tab_albums'))
    }
    .width(`calc(100% - ${this.horizontalPadding() * 2}vp)`)
    .height('100%')
    .barMode(BarMode.Fixed)
    .scrollable(false)
    .animationDuration(0)
    .padding({ bottom: this.bottomInset() })
    .onChange((index: number) => this.vm.selectSection(index))
  }

  build() {
    if (this.fromHome) {
      this.pageContent(this.useNativeSurface())
    } else {
      AppRouteDestination({
        title: this.title(),
        menus: this.menus(),
        scrollControllers: [this.photosScroller, this.albumsScroller],
        contentBuilder: () => {
          this.pageContent(true)
        },
        legacyContentBuilder: () => {
          this.pageContent(false)
        }
      })
    }
  }
}
