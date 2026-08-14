import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { AppRouteDestination } from '../../component/AppRouteDestination'
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

@HMRouter({
  pageUrl: RouterConsts.MusicLibraryPage,
  useNavDst: true
})
@ComponentV2
export struct MusicLibraryPage {
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

  aboutToAppear(): void {
    if (this.fromHome) {
      this.vm.init(this.mediaItem.id || '', this.mediaItem.name || '')
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

  private horizontalPadding(): number {
    return this.vm.appUIState.currentBreakpoint.includes('s') ? 16 : 24
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
      HMRouterMgr.to(RouterConsts.MusicPlayerPage).push()
      return
    }
    this.playbackController.playQueue(this.ui.tracks, index).catch((): void => {})
  }

  private emptyTitle(section: MusicLibrarySection): ResourceStr {
    if (section === MusicLibrarySection.Artists) {
      return $r('app.string.music_empty_artists')
    }
    if (section === MusicLibrarySection.Tracks) {
      return $r('app.string.music_empty_tracks')
    }
    return $r('app.string.music_empty_albums')
  }

  private currentErrorText(): ResourceStr {
    if (this.ui.selectedSection === MusicLibrarySection.Artists) {
      return musicBrowseErrorText(this.ui.artistsErrorCode)
    }
    if (this.ui.selectedSection === MusicLibrarySection.Tracks) {
      return musicBrowseErrorText(this.ui.tracksErrorCode)
    }
    return musicBrowseErrorText(this.ui.albumsErrorCode)
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

  @Builder
  private loadingState(section: MusicLibrarySection, nativeSurface: boolean) {
    MusicLibrarySkeleton({
      section: section,
      currentBreakpoint: this.vm.appUIState.currentBreakpoint,
      nativeSurface: nativeSurface
    })
  }

  @Builder
  private emptyState(section: MusicLibrarySection, nativeSurface: boolean) {
    Column({ space: 12 }) {
      Image($r('app.media.ic_audio'))
        .width(44)
        .height(44)
        .opacity(0.65)
      Text(this.emptyTitle(section))
        .fontSize(14)
        .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
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
        .fontColor([nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')])
      Text(this.currentErrorText())
        .fontSize(14)
        .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
      Button($r('app.string.music_retry'), { type: ButtonType.Normal })
        .height(44)
        .borderRadius(22)
        .onClick(() => this.vm.retryCurrent())
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private albumsContent(nativeSurface: boolean) {
    if (this.ui.albumsState === MusicContentState.Loading) {
      this.loadingState(MusicLibrarySection.Albums, nativeSurface)
    } else if (this.ui.albumsState === MusicContentState.Empty) {
      this.emptyState(MusicLibrarySection.Albums, nativeSurface)
    } else if (this.ui.albumsState === MusicContentState.Error) {
      this.errorState(nativeSurface)
    } else {
      Refresh({ refreshing: $$this.ui.albumsRefreshing }) {
        MusicAlbumGrid({
          albums: this.ui.albums,
          currentBreakpoint: this.vm.appUIState.currentBreakpoint,
          nativeSurface: nativeSurface,
          scroller: this.albumsScroller,
          loadingMore: this.ui.albumsLoadingMore,
          loadMoreFailed: this.ui.albumsLoadMoreFailed,
          loadMoreErrorCode: this.ui.albumsErrorCode,
          onAlbumClick: (album: MusicAlbum) => this.openAlbum(album),
          onReachEnd: () => this.vm.loadMoreCurrent(),
          onRetryLoadMore: () => this.vm.retryLoadMoreCurrent(),
          onContentScrollStop: () => this.saveOffset(MusicLibrarySection.Albums)
        })
      }
      .onRefreshing(() => this.vm.refreshCurrent())
    }
  }

  @Builder
  private artistsContent(nativeSurface: boolean) {
    if (this.ui.artistsState === MusicContentState.Loading) {
      this.loadingState(MusicLibrarySection.Artists, nativeSurface)
    } else if (this.ui.artistsState === MusicContentState.Empty) {
      this.emptyState(MusicLibrarySection.Artists, nativeSurface)
    } else if (this.ui.artistsState === MusicContentState.Error) {
      this.errorState(nativeSurface)
    } else {
      Refresh({ refreshing: $$this.ui.artistsRefreshing }) {
        MusicArtistGrid({
          artists: this.ui.artists,
          currentBreakpoint: this.vm.appUIState.currentBreakpoint,
          nativeSurface: nativeSurface,
          scroller: this.artistsScroller,
          loadingMore: this.ui.artistsLoadingMore,
          loadMoreFailed: this.ui.artistsLoadMoreFailed,
          loadMoreErrorCode: this.ui.artistsErrorCode,
          onArtistClick: (artist: MusicArtist) => this.openArtist(artist),
          onReachEnd: () => this.vm.loadMoreCurrent(),
          onRetryLoadMore: () => this.vm.retryLoadMoreCurrent(),
          onContentScrollStop: () => this.saveOffset(MusicLibrarySection.Artists)
        })
      }
      .onRefreshing(() => this.vm.refreshCurrent())
    }
  }

  @Builder
  private tracksContent(nativeSurface: boolean) {
    if (this.ui.tracksState === MusicContentState.Loading) {
      this.loadingState(MusicLibrarySection.Tracks, nativeSurface)
    } else if (this.ui.tracksState === MusicContentState.Empty) {
      this.emptyState(MusicLibrarySection.Tracks, nativeSurface)
    } else if (this.ui.tracksState === MusicContentState.Error) {
      this.errorState(nativeSurface)
    } else {
      Refresh({ refreshing: $$this.ui.tracksRefreshing }) {
        MusicTrackList({
          tracks: this.ui.tracks,
          nativeSurface: nativeSurface,
          scroller: this.tracksScroller,
          loadingMore: this.ui.tracksLoadingMore,
          loadMoreFailed: this.ui.tracksLoadMoreFailed,
          loadMoreErrorCode: this.ui.tracksErrorCode,
          onTrackClick: (track: MusicTrack, index: number) => this.onTrackClick(track, index),
          onReachEnd: () => this.vm.loadMoreCurrent(),
          onRetryLoadMore: () => this.vm.retryLoadMoreCurrent(),
          onContentScrollStop: () => this.saveOffset(MusicLibrarySection.Tracks)
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
        this.albumsContent(nativeSurface)
      }
      .tabBar($r('app.string.music_albums'))
      TabContent() {
        this.artistsContent(nativeSurface)
      }
      .tabBar($r('app.string.music_artists'))
      TabContent() {
        this.tracksContent(nativeSurface)
      }
      .tabBar($r('app.string.music_tracks'))
    }
    .width(`calc(100% - ${this.horizontalPadding() * 2}vp)`)
    .height('100%')
    .barMode(BarMode.Fixed)
    .scrollable(false)
    .animationDuration(0)
    .padding({ bottom: Math.max(0, this.vm.appUIState.safeBottom) })
    .onChange((index: number) => this.onSectionChanged(index))
  }

  build() {
    if (this.fromHome) {
      this.pageContent(this.useNativeSurface())
    } else {
      AppRouteDestination({
        title: this.title(),
        menus: this.menus(),
        scrollControllers: [this.albumsScroller, this.artistsScroller, this.tracksScroller],
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
