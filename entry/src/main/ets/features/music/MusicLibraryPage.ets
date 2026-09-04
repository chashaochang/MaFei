import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination'
import { RouterConsts } from '../../common/RouterConsts'
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { MediaItem } from '../home/hometab/HomeUIState'
import { MusicAlbumGrid } from './MusicAlbumGrid'
import { MusicArtistGrid } from './MusicArtistGrid'
import { musicBrowseErrorText } from './MusicErrorText'
import { MusicContentState, MusicLibraryUIState } from './MusicLibraryUIState'
import { MusicLibrarySkeleton } from './MusicLibrarySkeleton'
import { MusicLibraryViewModel } from './MusicLibraryViewModel'
import { MusicAlbum, MusicArtist, MusicLibrarySection, MusicTrack } from './MusicModels'
import { MusicPlaybackController } from './MusicPlaybackController'
import { connectMusicPlaybackState, MusicPlaybackState } from './MusicPlaybackState'
import { MusicTrackList } from './MusicTrackList'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'
import { Constants } from '../../utils/Constants'
import { HomeContentLayoutPolicy } from '../home/HomeContentLayoutPolicy'

@ComponentV2
struct MusicLibraryEmptyState {
  @Require @Param title: ResourceStr
  @Require @Param nativeSurface: boolean

  build() {
    Column({ space: 12 }) {
      Image($r('app.media.ic_audio'))
        .width(44)
        .height(44)
        .opacity(0.65)
      Text(this.title)
        .fontSize(14)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
struct MusicLibraryErrorState {
  @Require @Param errorText: ResourceStr
  @Require @Param nativeSurface: boolean
  @Event onRetry: () => void = () => {}

  build() {
    Column({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
        .fontSize(42)
        .fontColor([this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')])
      Text(this.errorText)
        .fontSize(14)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
      Button($r('app.string.music_retry'), { type: ButtonType.Normal })
        .height(44)
        .borderRadius(22)
        .onClick(() => this.onRetry())
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
struct MusicAlbumsSectionContent {
  @Require @Param ui: MusicLibraryUIState
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param scroller: Scroller
  @Event onAlbumClick: (album: MusicAlbum) => void = (_album: MusicAlbum) => {}
  @Event onReachEnd: () => void = () => {}
  @Event onRetryLoadMore: () => void = () => {}
  @Event onContentScrollStop: () => void = () => {}
  @Event onRefresh: () => void = () => {}
  @Event onRetry: () => void = () => {}

  build() {
    if (this.ui.albumsState === MusicContentState.Loading) {
      MusicLibrarySkeleton({
        section: MusicLibrarySection.Albums,
        currentBreakpoint: this.currentBreakpoint,
        nativeSurface: this.nativeSurface
      })
    } else if (this.ui.albumsState === MusicContentState.Empty) {
      MusicLibraryEmptyState({
        title: $r('app.string.music_empty_albums'),
        nativeSurface: this.nativeSurface
      })
    } else if (this.ui.albumsState === MusicContentState.Error) {
      MusicLibraryErrorState({
        errorText: musicBrowseErrorText(this.ui.albumsErrorCode),
        nativeSurface: this.nativeSurface,
        onRetry: () => this.onRetry()
      })
    } else {
      Refresh({ refreshing: $$this.ui.albumsRefreshing }) {
        MusicAlbumGrid({
          albums: this.ui.albums,
          currentBreakpoint: this.currentBreakpoint,
          nativeSurface: this.nativeSurface,
          scroller: this.scroller,
          loadingMore: this.ui.albumsLoadingMore,
          loadMoreFailed: this.ui.albumsLoadMoreFailed,
          loadMoreErrorCode: this.ui.albumsErrorCode,
          onAlbumClick: (album: MusicAlbum) => this.onAlbumClick(album),
          onReachEnd: () => this.onReachEnd(),
          onRetryLoadMore: () => this.onRetryLoadMore(),
          onContentScrollStop: () => this.onContentScrollStop()
        })
      }
      .onRefreshing(() => this.onRefresh())
    }
  }
}

@ComponentV2
struct MusicArtistsSectionContent {
  @Require @Param ui: MusicLibraryUIState
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param scroller: Scroller
  @Event onArtistClick: (artist: MusicArtist) => void = (_artist: MusicArtist) => {}
  @Event onReachEnd: () => void = () => {}
  @Event onRetryLoadMore: () => void = () => {}
  @Event onContentScrollStop: () => void = () => {}
  @Event onRefresh: () => void = () => {}
  @Event onRetry: () => void = () => {}

  build() {
    if (this.ui.artistsState === MusicContentState.Loading) {
      MusicLibrarySkeleton({
        section: MusicLibrarySection.Artists,
        currentBreakpoint: this.currentBreakpoint,
        nativeSurface: this.nativeSurface
      })
    } else if (this.ui.artistsState === MusicContentState.Empty) {
      MusicLibraryEmptyState({
        title: $r('app.string.music_empty_artists'),
        nativeSurface: this.nativeSurface
      })
    } else if (this.ui.artistsState === MusicContentState.Error) {
      MusicLibraryErrorState({
        errorText: musicBrowseErrorText(this.ui.artistsErrorCode),
        nativeSurface: this.nativeSurface,
        onRetry: () => this.onRetry()
      })
    } else {
      Refresh({ refreshing: $$this.ui.artistsRefreshing }) {
        MusicArtistGrid({
          artists: this.ui.artists,
          currentBreakpoint: this.currentBreakpoint,
          nativeSurface: this.nativeSurface,
          scroller: this.scroller,
          loadingMore: this.ui.artistsLoadingMore,
          loadMoreFailed: this.ui.artistsLoadMoreFailed,
          loadMoreErrorCode: this.ui.artistsErrorCode,
          onArtistClick: (artist: MusicArtist) => this.onArtistClick(artist),
          onReachEnd: () => this.onReachEnd(),
          onRetryLoadMore: () => this.onRetryLoadMore(),
          onContentScrollStop: () => this.onContentScrollStop()
        })
      }
      .onRefreshing(() => this.onRefresh())
    }
  }
}

@ComponentV2
struct MusicTracksSectionContent {
  @Require @Param ui: MusicLibraryUIState
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param scroller: Scroller
  @Event onTrackClick: (track: MusicTrack, index: number) => void =
    (_track: MusicTrack, _index: number) => {}
  @Event onReachEnd: () => void = () => {}
  @Event onRetryLoadMore: () => void = () => {}
  @Event onContentScrollStop: () => void = () => {}
  @Event onRefresh: () => void = () => {}
  @Event onRetry: () => void = () => {}

  build() {
    if (this.ui.tracksState === MusicContentState.Loading) {
      MusicLibrarySkeleton({
        section: MusicLibrarySection.Tracks,
        currentBreakpoint: this.currentBreakpoint,
        nativeSurface: this.nativeSurface
      })
    } else if (this.ui.tracksState === MusicContentState.Empty) {
      MusicLibraryEmptyState({
        title: $r('app.string.music_empty_tracks'),
        nativeSurface: this.nativeSurface
      })
    } else if (this.ui.tracksState === MusicContentState.Error) {
      MusicLibraryErrorState({
        errorText: musicBrowseErrorText(this.ui.tracksErrorCode),
        nativeSurface: this.nativeSurface,
        onRetry: () => this.onRetry()
      })
    } else {
      Refresh({ refreshing: $$this.ui.tracksRefreshing }) {
        MusicTrackList({
          tracks: this.ui.tracks,
          nativeSurface: this.nativeSurface,
          scroller: this.scroller,
          loadingMore: this.ui.tracksLoadingMore,
          loadMoreFailed: this.ui.tracksLoadMoreFailed,
          loadMoreErrorCode: this.ui.tracksErrorCode,
          onTrackClick: (track: MusicTrack, index: number) => this.onTrackClick(track, index),
          onReachEnd: () => this.onReachEnd(),
          onRetryLoadMore: () => this.onRetryLoadMore(),
          onContentScrollStop: () => this.onContentScrollStop()
        })
      }
      .onRefreshing(() => this.onRefresh())
    }
  }
}

@ComponentV2
struct MusicLibraryPageContent {
  @Require @Param ui: MusicLibraryUIState
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param tabsController: TabsController
  @Require @Param albumsScroller: Scroller
  @Require @Param artistsScroller: Scroller
  @Require @Param tracksScroller: Scroller
  @Require @Param horizontalPadding: number
  @Require @Param safeBottom: number
  @Event onAlbumClick: (album: MusicAlbum) => void = (_album: MusicAlbum) => {}
  @Event onArtistClick: (artist: MusicArtist) => void = (_artist: MusicArtist) => {}
  @Event onTrackClick: (track: MusicTrack, index: number) => void =
    (_track: MusicTrack, _index: number) => {}
  @Event onLoadMore: () => void = () => {}
  @Event onRetryLoadMore: () => void = () => {}
  @Event onRefresh: () => void = () => {}
  @Event onRetry: () => void = () => {}
  @Event onSectionChanged: (index: number) => void = (_index: number) => {}
  @Event onSaveOffset: (section: MusicLibrarySection) => void = (_section: MusicLibrarySection) => {}

  build() {
    Tabs({
      index: this.ui.selectedSection,
      controller: this.tabsController
    }) {
      TabContent() {
        MusicAlbumsSectionContent({
          ui: this.ui,
          currentBreakpoint: this.currentBreakpoint,
          nativeSurface: this.nativeSurface,
          scroller: this.albumsScroller,
          onAlbumClick: (album: MusicAlbum) => this.onAlbumClick(album),
          onReachEnd: () => this.onLoadMore(),
          onRetryLoadMore: () => this.onRetryLoadMore(),
          onContentScrollStop: () => this.onSaveOffset(MusicLibrarySection.Albums),
          onRefresh: () => this.onRefresh(),
          onRetry: () => this.onRetry()
        })
      }
      .tabBar($r('app.string.music_albums'))
      TabContent() {
        MusicArtistsSectionContent({
          ui: this.ui,
          currentBreakpoint: this.currentBreakpoint,
          nativeSurface: this.nativeSurface,
          scroller: this.artistsScroller,
          onArtistClick: (artist: MusicArtist) => this.onArtistClick(artist),
          onReachEnd: () => this.onLoadMore(),
          onRetryLoadMore: () => this.onRetryLoadMore(),
          onContentScrollStop: () => this.onSaveOffset(MusicLibrarySection.Artists),
          onRefresh: () => this.onRefresh(),
          onRetry: () => this.onRetry()
        })
      }
      .tabBar($r('app.string.music_artists'))
      TabContent() {
        MusicTracksSectionContent({
          ui: this.ui,
          currentBreakpoint: this.currentBreakpoint,
          nativeSurface: this.nativeSurface,
          scroller: this.tracksScroller,
          onTrackClick: (track: MusicTrack, index: number) => this.onTrackClick(track, index),
          onReachEnd: () => this.onLoadMore(),
          onRetryLoadMore: () => this.onRetryLoadMore(),
          onContentScrollStop: () => this.onSaveOffset(MusicLibrarySection.Tracks),
          onRefresh: () => this.onRefresh(),
          onRetry: () => this.onRetry()
        })
      }
      .tabBar($r('app.string.music_tracks'))
    }
    .width(`calc(100% - ${this.horizontalPadding * 2}vp)`)
    .height('100%')
    .barMode(BarMode.Fixed)
    .scrollable(false)
    .animationDuration(0)
    .padding({ bottom: Math.max(0, this.safeBottom) })
    .onChange((index: number) => this.onSectionChanged(index))
  }
}

@HMRouter({
  pageUrl: RouterConsts.MusicLibraryPage,
  useNavDst: true
})
@ComponentV2
export struct MusicLibraryPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Param fromHome: boolean = false
  @Param mediaItem: MediaItem = {} as MediaItem
  @Local ui: MusicLibraryUIState = new MusicLibraryUIState(this.getUIContext())
  @Local vm: MusicLibraryViewModel = new MusicLibraryViewModel(this.ui)
  private playbackState: MusicPlaybackState = connectMusicPlaybackState()
  private playbackController: MusicPlaybackController = MusicPlaybackController.instance()
  private readonly tabsController: TabsController = new TabsController()
  private readonly albumsScroller: Scroller = new Scroller()
  private readonly artistsScroller: Scroller = new Scroller()
  private readonly tracksScroller: Scroller = new Scroller()
  private restoreEpoch: number = 0

  private initEmbeddedMediaItem(force: boolean = false): void {
    const libraryId = this.mediaItem.id || ''
    if (!this.fromHome || libraryId.length === 0 || (!force && this.ui.libraryId === libraryId)) {
      return
    }
    this.vm.init(libraryId, this.mediaItem.name || '')
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
    const param = HMRouterMgr.getCurrentParam() as MediaItem | undefined
    this.vm.init(param?.id || '', param?.name || '')
  }

  aboutToDisappear(): void {
    this.restoreEpoch++
    this.saveOffset(this.ui.selectedSection)
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
    return this.contentBreakpoint().includes('s') ? 16 : 24
  }

  private title(): ResourceStr {
    return this.ui.libraryName.length > 0 ? this.ui.libraryName : $r('app.string.music_albums')
  }

  private menus(): Array<NavigationMenuItem> {
    return [{
      value: $r('app.string.music_refresh'),
      icon: $r('sys.symbol.arrow_clockwise'),
      action: () => this.vm.refreshCurrent()
    }]
  }

  private openAlbum(album: MusicAlbum): void {
    HMRouterMgr.to(RouterConsts.MusicAlbumPage)
      .withParam({ albumId: album.id, title: album.title })
      .push()
  }

  private openArtist(artist: MusicArtist): void {
    HMRouterMgr.to(RouterConsts.MusicArtistPage)
      .withParam({ artistId: artist.id, title: artist.name })
      .push()
  }

  private onTrackClick(track: MusicTrack, index: number): void {
    if (this.playbackState.currentItem()?.id === track.id) {
      HMRouterMgr.to(RouterConsts.MusicPlayerPage)
        .withNavigation(Constants.NAVIGATION_ID)
        .push()
      return
    }
    this.playbackController.playQueue(this.ui.tracks, index).catch((): void => {})
  }

  private scrollerFor(section: MusicLibrarySection): Scroller {
    if (section === MusicLibrarySection.Artists) {
      return this.artistsScroller
    }
    if (section === MusicLibrarySection.Tracks) {
      return this.tracksScroller
    }
    return this.albumsScroller
  }

  private savedOffset(section: MusicLibrarySection): number {
    if (section === MusicLibrarySection.Artists) {
      return this.ui.artistsScrollOffset
    }
    if (section === MusicLibrarySection.Tracks) {
      return this.ui.tracksScrollOffset
    }
    return this.ui.albumsScrollOffset
  }

  private saveOffset(section: MusicLibrarySection): void {
    const offset = this.scrollerFor(section).currentOffset()
    if (offset !== undefined) {
      this.vm.saveScrollOffset(section, offset.yOffset)
    }
  }

  private hasScrollableContent(section: MusicLibrarySection): boolean {
    if (section === MusicLibrarySection.Artists) {
      return this.ui.artistsState === MusicContentState.Content
    }
    if (section === MusicLibrarySection.Tracks) {
      return this.ui.tracksState === MusicContentState.Content
    }
    return this.ui.albumsState === MusicContentState.Content
  }

  private restoreOffset(section: MusicLibrarySection): void {
    const epoch = ++this.restoreEpoch
    setTimeout((): void => {
      if (epoch !== this.restoreEpoch || this.ui.selectedSection !== section ||
        !this.hasScrollableContent(section)) {
        return
      }
      this.scrollerFor(section).scrollTo({
        xOffset: 0,
        yOffset: this.savedOffset(section),
        animation: false
      })
    }, 0)
  }

  private onSectionChanged(index: number): void {
    this.saveOffset(this.ui.selectedSection)
    this.vm.selectSection(index)
    this.restoreOffset(this.ui.selectedSection)
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
          title: this.title(),
          menus: this.menus(),
          scrollControllers: [this.albumsScroller, this.artistsScroller, this.tracksScroller]
        }
  }

  build() {
    if (this.fromHome) {
      MusicLibraryPageContent({
        ui: this.ui,
        currentBreakpoint: this.contentBreakpoint(),
        nativeSurface: this.useNativeSurface(),
        tabsController: this.tabsController,
        albumsScroller: this.albumsScroller,
        artistsScroller: this.artistsScroller,
        tracksScroller: this.tracksScroller,
        horizontalPadding: this.horizontalPadding(),
        safeBottom: this.vm.appUIState.safeBottom,
        onAlbumClick: (album: MusicAlbum) => this.openAlbum(album),
        onArtistClick: (artist: MusicArtist) => this.openArtist(artist),
        onTrackClick: (track: MusicTrack, index: number) => this.onTrackClick(track, index),
        onLoadMore: () => this.vm.loadMoreCurrent(),
        onRetryLoadMore: () => this.vm.retryLoadMoreCurrent(),
        onRefresh: () => this.vm.refreshCurrent(),
        onRetry: () => this.vm.retryCurrent(),
        onSectionChanged: (index: number) => this.onSectionChanged(index),
        onSaveOffset: (section: MusicLibrarySection) => this.saveOffset(section)
      })
    } else {
      if (this.routeDestination.usesHdsDestination()) {
        HdsNavDestination() {
          Column() {
            Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
            Stack() {
              if (this.routeDestination.usesNativeChrome()) {
                MusicLibraryPageContent({
                  ui: this.ui,
                  currentBreakpoint: this.contentBreakpoint(),
                  nativeSurface: true,
                  tabsController: this.tabsController,
                  albumsScroller: this.albumsScroller,
                  artistsScroller: this.artistsScroller,
                  tracksScroller: this.tracksScroller,
                  horizontalPadding: this.horizontalPadding(),
                  safeBottom: this.vm.appUIState.safeBottom,
                  onAlbumClick: (album: MusicAlbum) => this.openAlbum(album),
                  onArtistClick: (artist: MusicArtist) => this.openArtist(artist),
                  onTrackClick: (track: MusicTrack, index: number) => this.onTrackClick(track, index),
                  onLoadMore: () => this.vm.loadMoreCurrent(),
                  onRetryLoadMore: () => this.vm.retryLoadMoreCurrent(),
                  onRefresh: () => this.vm.refreshCurrent(),
                  onRetry: () => this.vm.retryCurrent(),
                  onSectionChanged: (index: number) => this.onSectionChanged(index),
                  onSaveOffset: (section: MusicLibrarySection) => this.saveOffset(section)
                })
              } else {
                MusicLibraryPageContent({
                  ui: this.ui,
                  currentBreakpoint: this.contentBreakpoint(),
                  nativeSurface: false,
                  tabsController: this.tabsController,
                  albumsScroller: this.albumsScroller,
                  artistsScroller: this.artistsScroller,
                  tracksScroller: this.tracksScroller,
                  horizontalPadding: this.horizontalPadding(),
                  safeBottom: this.vm.appUIState.safeBottom,
                  onAlbumClick: (album: MusicAlbum) => this.openAlbum(album),
                  onArtistClick: (artist: MusicArtist) => this.openArtist(artist),
                  onTrackClick: (track: MusicTrack, index: number) => this.onTrackClick(track, index),
                  onLoadMore: () => this.vm.loadMoreCurrent(),
                  onRetryLoadMore: () => this.vm.retryLoadMoreCurrent(),
                  onRefresh: () => this.vm.refreshCurrent(),
                  onRetry: () => this.vm.retryCurrent(),
                  onSectionChanged: (index: number) => this.onSectionChanged(index),
                  onSaveOffset: (section: MusicLibrarySection) => this.saveOffset(section)
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
          MusicLibraryPageContent({
            ui: this.ui,
            currentBreakpoint: this.contentBreakpoint(),
            nativeSurface: false,
            tabsController: this.tabsController,
            albumsScroller: this.albumsScroller,
            artistsScroller: this.artistsScroller,
            tracksScroller: this.tracksScroller,
            horizontalPadding: this.horizontalPadding(),
            safeBottom: this.vm.appUIState.safeBottom,
            onAlbumClick: (album: MusicAlbum) => this.openAlbum(album),
            onArtistClick: (artist: MusicArtist) => this.openArtist(artist),
            onTrackClick: (track: MusicTrack, index: number) => this.onTrackClick(track, index),
            onLoadMore: () => this.vm.loadMoreCurrent(),
            onRetryLoadMore: () => this.vm.retryLoadMoreCurrent(),
            onRefresh: () => this.vm.refreshCurrent(),
            onRetry: () => this.vm.retryCurrent(),
            onSectionChanged: (index: number) => this.onSectionChanged(index),
            onSaveOffset: (section: MusicLibrarySection) => this.saveOffset(section)
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
