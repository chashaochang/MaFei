import { HMRouterMgr } from "@hadss/hmrouter"
import { ImageKnifeComponent, ImageKnifeOption } from "@ohos/imageknifepro"
import { HOME_RECENT_MEDIA_ID, HomeUIState, MediaItem, VideoItem } from "./HomeUIState"
import { HomeViewModel } from "./HomeViewModel"
import { UIConstants } from "../../../common/UIConstants"
import { playMediaRefs } from "../../../common/CommonFunc"
import { HomeRefreshEvent } from "../../../events/Events"
import { CustomRefreshComponent } from "../../../common/CustomRefreshComponent"
import { ToastUtil } from "../../../utils/ToastUtil"
import { RouterConsts } from "../../../common/RouterConsts"
import { HorizontalVideoCard } from "../../../component/HorizontalVideoCard"
import {
  HORIZONTAL_CARD_LIST_HEIGHT, HORIZONTAL_CARD_SPACE
} from "../../../common/OneMoreConst"
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../../../theme/AppThemeSurfaceResolver"
import { AppThemeTokens } from "../../../theme/AppThemeTokens"
import { HomeInsetsPolicy } from "../HomeInsetsPolicy"
import { HomeLatestMediaSection } from "./HomeLatestMediaSection"
import { HomeFeedTabItem, HomeLibraryChipSelector } from "./HomeLibraryChipSelector"
import { curves } from "@kit.ArkUI"
import { ConfigurationConstant } from '@kit.AbilityKit'
import { HomeInitialSkeleton } from './HomeInitialSkeleton'
import { MediaLibraryKind } from '../../../media/catalog/MediaCatalogModels'
import { createMediaImageKnifeOption } from '../../../media/catalog/MediaImageKnifeAdapter'
import { MediaRef, MediaRefKind } from '../../../media/MediaRef'
import { toMediaRouteRefData } from '../../../media/navigation/MediaRouteRef'

const HERO_AUTOPLAY_INTERVAL: number = 6500
const HERO_TRANSITION_DURATION: number = 450
const HERO_PROGRESS_START_DELAY: number = 16
const HOME_HERO_BASE_HEIGHT: number = 430
const HOME_HERO_STRETCH_FACTOR: number = 3
const HOME_HERO_SPRING_DURATION: number = 250
const HOME_HERO_REFRESH_THRESHOLD: number = 64
const HOME_LIBRARY_HEADER_EXTRA_MENU_GAP: number = 12
const HOME_LIBRARY_HEADER_ALIGNMENT_OFFSET: number = 3
const HOME_LIBRARY_INLINE_CHIP_ALIGNMENT_OFFSET: number = 4
const HOME_LIBRARY_SEARCH_MENU_WIDTH: number = 56
const HOME_LIBRARY_LIVE_MENU_WIDTH: number = 104
const HOME_LIBRARY_SEARCH_BUTTON_SIZE: number = 40
const HOME_LIBRARY_SEARCH_ICON_SIZE: number = 24
const HOME_LIBRARY_INLINE_HEADER_HEIGHT: number = 64

@ComponentV2
export struct HomeTab {
  @Param active: boolean = true
  @Param contentBottomInset?: number = undefined
  @Param contentScroller: Scroller = new Scroller()
  @Local ui: HomeUIState = new HomeUIState(this.getUIContext())
  @Local vm: HomeViewModel = new HomeViewModel(this.ui)
  @Local heroIndex: number = 0
  @Local heroProgress: number = 0
  @Local nativeHeroHeight: number = HOME_HERO_BASE_HEIGHT
  @Local nativeHeroScrollState: ScrollState = ScrollState.Idle
  @Local nativeHeroRefreshProgress: number = 0
  @Local latestChipStripPinned: boolean = false
  @Local latestChipStripMenuReserved: boolean = false
  private heroProgressTimer: number = -1
  private nativeHeroSpringCurve: curves.ICurve = curves.interpolatingSpring(0, 1, 288, 30)

  private playItem(item: VideoItem): void {
    if (item.mediaRef) {
      playMediaRefs([item.mediaRef], this.getUIContext().getHostContext()!)
    }
  }

  private cancelHeroProgressStart(): void {
    if (this.heroProgressTimer >= 0) {
      clearTimeout(this.heroProgressTimer)
      this.heroProgressTimer = -1
    }
  }

  private restartHeroProgress(index: number): void {
    this.cancelHeroProgressStart()
    const itemCount = this.ui.recommendationList.length
    this.heroIndex = itemCount > 0 ? Math.max(0, Math.min(index, itemCount - 1)) : 0
    this.heroProgress = 0
    if (itemCount <= 1) {
      return
    }
    this.heroProgressTimer = setTimeout(() => {
      this.heroProgressTimer = -1
      this.getUIContext().animateTo({
        duration: HERO_AUTOPLAY_INTERVAL,
        curve: Curve.Linear
      }, () => {
        this.heroProgress = 100
      })
    }, HERO_PROGRESS_START_DELAY)
  }

  private heroSegmentProgress(index: number): number {
    if (index < this.heroIndex) {
      return 100
    }
    return index === this.heroIndex ? this.heroProgress : 0
  }

  private heroProgressTrackColor(): ResourceColor {
    return this.vm.appUIState.colorMode === ConfigurationConstant.ColorMode.COLOR_MODE_DARK ?
      'rgba(255,255,255,0.20)' : 'rgba(0,0,0,0.12)'
  }

  private scrollTail(): number {
    return HomeInsetsPolicy.scrollTail(this.contentBottomInset, this.vm.appUIState.safeBottom)
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private phoneContentTopInset(): number {
    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  private showNativeHero(): boolean {
    return this.useNativeSurface() &&
      this.vm.appUIState.currentBreakpoint.includes('s') &&
      this.ui.recommendationList.length > 0
  }

  private showNativeHeroSkeleton(): boolean {
    return this.useNativeSurface() && this.vm.appUIState.currentBreakpoint.includes('s')
  }

  private springBackNativeHero(): void {
    if (this.nativeHeroHeight === HOME_HERO_BASE_HEIGHT) {
      this.nativeHeroScrollState = ScrollState.Idle
      return
    }
    const shouldRefresh = this.nativeHeroHeight >=
      HOME_HERO_BASE_HEIGHT + HOME_HERO_REFRESH_THRESHOLD && !this.ui.isRefreshing
    this.nativeHeroScrollState = ScrollState.Fling
    this.getUIContext().animateTo({
      curve: this.nativeHeroSpringCurve,
      duration: HOME_HERO_SPRING_DURATION
    }, () => {
      this.nativeHeroHeight = HOME_HERO_BASE_HEIGHT
      this.nativeHeroScrollState = ScrollState.Idle
      if (!this.ui.isRefreshing) {
        this.nativeHeroRefreshProgress = 0
      }
    })
    if (shouldRefresh) {
      this.nativeHeroRefreshProgress = 100
      this.ui.isRefreshing = true
      this.vm.init()
    } else {
      this.nativeHeroRefreshProgress = 0
    }
  }

  private stretchNativeHero(offset: number, state: ScrollState): boolean {
    if (!this.showNativeHero() || this.contentScroller.currentOffset().yOffset > 0) {
      return false
    }
    if (state === ScrollState.Scroll && offset !== 0) {
      this.nativeHeroScrollState = ScrollState.Scroll
      const nextHeight = this.nativeHeroHeight - offset
      if (nextHeight < HOME_HERO_BASE_HEIGHT) {
        this.nativeHeroHeight = HOME_HERO_BASE_HEIGHT
        this.nativeHeroScrollState = ScrollState.Fling
        return false
      }
      this.nativeHeroHeight += -(offset / HOME_HERO_STRETCH_FACTOR)
      this.nativeHeroRefreshProgress = Math.min(100, Math.max(0,
        (this.nativeHeroHeight - HOME_HERO_BASE_HEIGHT) / HOME_HERO_REFRESH_THRESHOLD * 100))
      return true
    }
    if (this.nativeHeroScrollState === ScrollState.Scroll && state === ScrollState.Fling) {
      this.springBackNativeHero()
      return true
    }
    return false
  }

  private latestFeedContentMinHeight(): number {
    const availableHeight = this.vm.appUIState.deviceHeight -
      this.phoneContentTopInset() - this.scrollTail()
    return Math.max(360, availableHeight)
  }

  private latestMediaTabs(): HomeFeedTabItem[] {
    const tabs: HomeFeedTabItem[] = [{ id: HOME_RECENT_MEDIA_ID, label: '最近添加' }]
    this.ui.latestMediaList.forEach((media: MediaItem): void => {
      tabs.push({ id: media.id, label: media.name })
    })
    return tabs
  }

  private latestMediaHeaderSidePadding(): number {
    return HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2
  }

  private latestMediaHeaderTopInset(): number {
    if (!this.useNativeSurface() || !this.vm.appUIState.currentBreakpoint.includes('s')) {
      return 0
    }
    return this.latestChipStripPinned ? Math.max(0, this.vm.appUIState.safeTop) : 0
  }

  private latestMediaHeaderHeight(): number {
    if (!this.useNativeSurface() || !this.vm.appUIState.currentBreakpoint.includes('s')) {
      return UIConstants.ACTION_BAR_HEIGHT
    }
    return this.latestChipStripPinned ?
      UIConstants.ACTION_BAR_HEIGHT + this.latestMediaHeaderTopInset() : HOME_LIBRARY_INLINE_HEADER_HEIGHT
  }

  private latestMediaHeaderMenuInset(): number {
    if (!this.useNativeSurface() || !this.latestChipStripMenuReserved) {
      return 0
    }
    const menuWidth = this.ui.hasLiveTv ?
      HOME_LIBRARY_LIVE_MENU_WIDTH : HOME_LIBRARY_SEARCH_MENU_WIDTH
    return this.latestChipStripPinned ? this.latestMediaHeaderSidePadding() : menuWidth
  }

  private latestMediaHeaderTopPadding(): number {
    return this.latestMediaHeaderTopInset()
  }

  private latestMediaChipAlignmentOffset(): number {
    if (!this.useNativeSurface() || !this.vm.appUIState.currentBreakpoint.includes('s')) {
      return 0
    }
    return this.latestChipStripPinned ?
      HOME_LIBRARY_HEADER_ALIGNMENT_OFFSET : HOME_LIBRARY_INLINE_CHIP_ALIGNMENT_OFFSET
  }

  private updateLatestChipStripPinned(pinned: boolean): void {
    if (this.latestChipStripPinned !== pinned) {
      this.latestChipStripPinned = pinned
    }
    this.syncRootNavigationPinnedState()
  }

  private syncRootNavigationPinnedState(): void {
    this.vm.appUIState.rootNavigationHomeLibraryPinned =
      this.active && this.useNativeSurface() ? this.latestChipStripPinned : false
  }

  private selectLatestMedia(id: string): void {
    this.vm.selectLatestMedia(id)
  }

  private openHeroDetails(item: VideoItem): void {
    const kind = item.kind || item.mediaRef?.kind
    const seriesRef: MediaRef | undefined = item.seriesRef
    const openSeries = kind === MediaRefKind.Series ||
      (kind === MediaRefKind.Episode && seriesRef !== undefined)
    const targetRef = kind === MediaRefKind.Episode && seriesRef ? seriesRef : item.mediaRef
    const targetId = targetRef?.id || item.seriesId || item.id
    const mediaName = item.mediaName ||
      (openSeries ? '电视节目' : kind === MediaRefKind.Movie ? '电影' : '')
    HMRouterMgr.to(RouterConsts.VideoDetailPage)
      .withParam({
        id: targetId,
        ref: targetRef ? toMediaRouteRefData(targetRef) : undefined,
        mediaName: mediaName,
        isTvShows: openSeries
      })
      .push()
  }

  private imageOption(item: MediaItem): ImageKnifeOption {
    const option = createMediaImageKnifeOption(item.imageRef, item.image)
    option.objectFit = ImageFit.Contain
    option.border = {
      radius: 6
    }
    option.placeholderSrc = $r('app.media.placeholder_video')
    option.placeholderObjectFit = ImageFit.CENTER
    return option
  }

  private heroImageOption(item: VideoItem): ImageKnifeOption {
    const option = createMediaImageKnifeOption(item.imageRef, item.image)
    option.objectFit = ImageFit.Cover
    option.placeholderSrc = $r('app.media.placeholder_video')
    option.placeholderObjectFit = ImageFit.CENTER
    return option
  }

  private syncRootNavigationLiveTvAvailability(): void {
    this.vm.appUIState.rootNavigationLiveTvAvailable = this.ui.hasLiveTv
  }

  @Monitor('ui.hasLiveTv')
  onLiveTvAvailabilityChange(): void {
    this.syncRootNavigationLiveTvAvailability()
  }

  @Monitor('active')
  onActiveChange(): void {
    this.syncRootNavigationPinnedState()
  }

  @Monitor('ui.isRefreshing')
  onNativeHeroRefreshingChange(): void {
    if (!this.ui.isRefreshing) {
      this.nativeHeroRefreshProgress = 0
    }
  }

  aboutToAppear(): void {
    //监听刷新事件
    this.getUIContext().getHostContext()?.eventHub.on(HomeRefreshEvent, () => {
      setTimeout(() => {
        this.vm.init()
      }, 1000) //延时1秒，避免播放器退出时数据还未更新完毕
    })
    this.syncRootNavigationLiveTvAvailability()
    this.vm.init()
  }

  aboutToDisappear(): void {
    this.cancelHeroProgressStart()
    this.nativeHeroHeight = HOME_HERO_BASE_HEIGHT
    this.nativeHeroScrollState = ScrollState.Idle
    this.nativeHeroRefreshProgress = 0
    this.latestChipStripPinned = false
    this.latestChipStripMenuReserved = false
    this.vm.appUIState.rootNavigationHomeLibraryPinned = false
  }

  private updateLatestMediaHeaderPinned(area: Area): void {
    if (!this.useNativeSurface() || !this.vm.appUIState.currentBreakpoint.includes('s')) {
      this.latestChipStripMenuReserved = false
      this.updateLatestChipStripPinned(false)
      return
    }
    const globalY = area.globalPosition.y as number
    this.latestChipStripMenuReserved = globalY <= UIConstants.ACTION_BAR_HEIGHT
    this.updateLatestChipStripPinned(globalY <= 0)
  }

  @Builder
  private latestMediaStickyHeader() {
    Row({ space: 8 }) {
      HomeLibraryChipSelector({
        tabs: this.latestMediaTabs(),
        selectedId: this.ui.selectedLatestMediaId,
        nativeSurface: this.useNativeSurface(),
        fadeRightEdge: this.latestChipStripMenuReserved,
        rightContentPadding: this.latestChipStripMenuReserved ?
          HOME_LIBRARY_HEADER_EXTRA_MENU_GAP : 0,
        onSelectedIdChange: (id: string) => {
          this.selectLatestMedia(id)
        }
      })
        .layoutWeight(1)
        .translate({ y: this.latestMediaChipAlignmentOffset() })

      if (this.latestChipStripPinned && this.useNativeSurface()) {
        if (this.ui.hasLiveTv) {
          Button({ type: ButtonType.Circle, stateEffect: false }) {
            Image($r('app.media.iptv'))
              .width(22)
              .height(22)
              .fillColor($r('sys.color.icon_primary'))
          }
          .width(HOME_LIBRARY_SEARCH_BUTTON_SIZE)
          .height(HOME_LIBRARY_SEARCH_BUTTON_SIZE)
          .buttonStyle(ButtonStyleMode.TEXTUAL)
          .backgroundColor(Color.Transparent)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.HomeLibraryChip,
            this.useNativeSurface(),
            this.vm.appUIState.systemMaterialAvailable
          ))
          .accessibilityText('直播电视')
          .onClick((): void => {
            HMRouterMgr.to(RouterConsts.LiveTvPage).push()
          })
        }
        Button({ type: ButtonType.Circle, stateEffect: false }) {
          SymbolGlyph($r('sys.symbol.magnifyingglass'))
            .fontSize(HOME_LIBRARY_SEARCH_ICON_SIZE)
            .fontWeight(FontWeight.Regular)
            .fontColor([$r('sys.color.icon_primary')])
        }
        .width(HOME_LIBRARY_SEARCH_BUTTON_SIZE)
        .height(HOME_LIBRARY_SEARCH_BUTTON_SIZE)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.HomeLibraryChip,
          this.useNativeSurface(),
          this.vm.appUIState.systemMaterialAvailable
        ))
        .accessibilityText('搜索')
        .onClick(() => {
          HMRouterMgr.to(RouterConsts.SearchPage).push()
        })
      }
    }
    .width('100%')
    .height(this.latestMediaHeaderHeight())
    .padding({
      left: this.latestMediaHeaderSidePadding(),
      right: this.latestMediaHeaderMenuInset(),
      top: this.latestMediaHeaderTopPadding()
    })
    .alignItems(VerticalAlign.Center)
    .backgroundColor(Color.Transparent)
    .zIndex(this.latestChipStripPinned && this.useNativeSurface() ? 10 : 0)
    .hitTestBehavior(HitTestMode.Transparent)
    .onAreaChange((_oldValue: Area, newValue: Area) => {
      this.updateLatestMediaHeaderPinned(newValue)
    })
  }

  @Builder
  private homeHeaderContent() {
    Row({ space: AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ? 8 : 0 }) {
      Image($r('app.media.app_icon'))
        .height(AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ? 32 : 40)
      Text($r('app.string.app_name'))
        .fontColor(AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
          $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .fontSize(AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ? 22 : 18)
        .fontWeight(FontWeight.Bold)
    }

    Blank()
    if (this.ui.hasLiveTv) {
      Image($r('app.media.iptv'))
        .height(40)
        .fillColor(AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
          $r('sys.color.icon_primary') : $r('app.color.text_primary'))
        .padding(9)
        .onClick(() => {
          HMRouterMgr.to(RouterConsts.LiveTvPage).push()
        })
    }
    SymbolGlyph($r('sys.symbol.magnifyingglass'))
      .fontSize(24)
      .fontWeight(FontWeight.Medium)
      .align(Alignment.Center)
      .height(40)
      .fontColor([AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
        $r('sys.color.icon_primary') : $r('app.color.text_primary')])
      .onClick(() => {
        HMRouterMgr.to(RouterConsts.SearchPage).push()
      })
  }

  @Builder
  private feiniuHomeHeader() {
    Row({ space: 12 }) {
      this.homeHeaderContent()
    }
    .width('100%')
    .height(UIConstants.ACTION_BAR_HEIGHT + this.vm.appUIState.safeTop)
    .backgroundBlurStyle(BlurStyle.COMPONENT_THICK)
    .padding({
      left: 8,
      right: 16,
      top: this.vm.appUIState.safeTop
    })
  }

  @Builder
  private homeContentList(nativeHeroStretch: boolean) {
    List({ scroller: this.contentScroller }) {
        if (this.vm.appUIState.currentBreakpoint.includes('s') &&
          (!this.useNativeSurface() || (!this.ui.initialLoading && !this.showNativeHero()))) {
          ListItem().height(this.phoneContentTopInset())
        }
        if (this.ui.initialLoading) {
          ListItem() {
            HomeInitialSkeleton({
              showHero: this.showNativeHeroSkeleton(),
              currentBreakpoint: this.vm.appUIState.currentBreakpoint
            })
          }
        } else {
          if (this.showNativeHero()) {
            ListItem() {
              this.nativeHomeHeroCarousel()
            }
            .height(this.nativeHeroHeight)
          }
          if (this.ui.resumeList.length > 0) {
            ListItem() {
              this.buildResumeList()
            }
          }
          if (this.ui.nextUpList.length > 0) {
            ListItem() {
              this.buildNextUpList()
            }
          }

          ListItemGroup({ header: this.latestMediaStickyHeader }) {
            ListItem() {
              HomeLatestMediaSection({
                latestMap: this.ui.latestMap,
                selectedMediaId: this.ui.selectedLatestMediaId,
                nativeSurface: this.useNativeSurface(),
                currentBreakpoint: this.vm.appUIState.currentBreakpoint,
                loading: this.ui.latestMediaLoading,
                loadingMore: this.ui.latestMediaLoadingMore,
                hasMore: this.ui.latestMediaHasMore,
                loadFailed: this.ui.latestMediaLoadFailed,
                contentMinHeight: this.latestFeedContentMinHeight(),
                onRetry: () => {
                  this.vm.retryLatestMedia()
                }
              })
            }
          }
        }

        ListItem().height(this.scrollTail())
    }
    .width('100%')
    .height('100%')
    .clip(false)
    .edgeEffect(EdgeEffect.None)
    .listDirection(Axis.Vertical)
    .sticky(this.useNativeSurface() && this.vm.appUIState.currentBreakpoint.includes('s') ?
      StickyStyle.Header : StickyStyle.None)
    .scrollBar(BarState.Off)
    .onScrollFrameBegin((offset: number, state: ScrollState) => {
      if (nativeHeroStretch && this.stretchNativeHero(offset, state)) {
        return { offsetRemain: 0 }
      }
      return { offsetRemain: offset }
    })
    .onDidScroll(() => {
      if (nativeHeroStretch && this.contentScroller.currentOffset().yOffset === 0 &&
        this.nativeHeroScrollState === ScrollState.Scroll) {
        this.springBackNativeHero()
      }
    })
    .onReachEnd(() => {
      this.vm.loadMoreLatestMedia()
    })
  }

  @Builder
  private homeRefreshContent() {
    if (this.useNativeSurface() && this.vm.appUIState.currentBreakpoint.includes('s')) {
      this.homeContentList(true)
    } else {
      Refresh({ refreshing: $$this.ui.isRefreshing, builder: this.customRefreshComponent() }) {
        this.homeContentList(false)
      }
      .pullDownRatio(0.3)
      .refreshOffset(64)
      .maxPullDownDistance(64 + this.vm.appUIState.safeTop)
      .height('100%')
      .onStateChange((status) => {
        this.ui.refreshStatus = status
      })
      .onOffsetChange((offset) => {
        this.ui.refreshOffset = offset
      })
      .onRefreshing(() => {
        this.vm.init()
      })
    }
  }

  @Builder
  private standardHome() {
    Stack() {
      this.homeRefreshContent()

      if (this.vm.appUIState.currentBreakpoint.includes('s')) {
        this.feiniuHomeHeader()
      }
    }
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Top)
  }

  build() {
    if (this.useNativeSurface() && this.vm.appUIState.currentBreakpoint.includes('s')) {
      this.homeRefreshContent()
    } else {
      this.standardHome()
    }
  }

  @Builder
  buildMediaList() {
    Column() {
      List({ space: 8 }) {
        ListItem().width(8)
        ForEach(this.ui.mediaList, (item: MediaItem) => {
          ListItem() {
            Stack() {
              ImageKnifeComponent({
                imageKnifeOption: this.imageOption(item)
              }).height(80)
            }
            .width(150)
            .height(90)
            .padding({
              top: 4,
              left: 4,
              right: 4,
              bottom: 4
            })
            .borderColor(AppThemeSurfaceResolver.mediaOutline(
              this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
            .borderWidth(AppThemeSurfaceResolver.outlineWidth(
              this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 0.5))
            .borderRadius(8)
          }.onClick(() => {
            if (item.libraryKind === MediaLibraryKind.LiveTv || item.kind === MediaRefKind.LiveChannel) {
              HMRouterMgr.to(RouterConsts.LiveTvPage)
                .push()
              return
            }
            if (item.libraryKind !== MediaLibraryKind.Movies &&
              item.libraryKind !== MediaLibraryKind.Series &&
              item.libraryKind !== MediaLibraryKind.Mixed &&
              item.libraryKind !== MediaLibraryKind.Other) {
              ToastUtil.showToast(this.getUIContext(), '暂不支持混合文件夹')
              return;
            }
            HMRouterMgr.to(RouterConsts.VideoListPage)
              .withParam(item)
              .push()
          })
        })
        ListItem().width(8)
      }.width('100%').height(90).listDirection(Axis.Horizontal).scrollBar(BarState.Off)
    }
  }

  @Builder
  private nativeHomeHeroCarousel() {
    Stack({ alignContent: Alignment.BottomStart }) {
      Swiper() {
        ForEach(this.ui.recommendationList, (item: VideoItem) => {
          this.nativeHomeHero(item)
        }, (item: VideoItem) => item.id)
      }
      .width('100%')
      .height('100%')
      .autoPlay(this.ui.recommendationList.length > 1)
      .interval(HERO_AUTOPLAY_INTERVAL)
      .duration(HERO_TRANSITION_DURATION)
      .loop(this.ui.recommendationList.length > 1)
      .indicator(false)
      .onChange((index: number) => {
        this.restartHeroProgress(index)
      })

      if (this.ui.recommendationList.length > 1) {
        this.nativeHomeHeroProgressIndicator()
      }

      if (this.nativeHeroRefreshProgress > 0 || this.ui.isRefreshing) {
        this.nativeHomeHeroRefreshIndicator()
      }
    }
    .width('100%')
    .height('100%')
    .onAppear(() => {
      this.restartHeroProgress(this.heroIndex)
    })
    .onDisAppear(() => {
      this.cancelHeroProgressStart()
    })
  }

  @Builder
  private nativeHomeHeroRefreshIndicator() {
    Row() {
      Progress({
        value: this.nativeHeroRefreshProgress,
        total: 100,
        type: ProgressType.Ring
      })
        .width(22)
        .height(22)
        .color(Color.White)
        .style({
          strokeWidth: 3,
          status: this.ui.isRefreshing ? ProgressStatus.LOADING : ProgressStatus.PROGRESSING
        })
    }
    .width(36)
    .height(36)
    .justifyContent(FlexAlign.Center)
    .borderRadius(18)
    .backgroundColor('rgba(0,0,0,0.30)')
    .opacity(this.ui.isRefreshing ? 1 : Math.min(1, this.nativeHeroRefreshProgress / 35))
    .position({
      x: (this.vm.appUIState.deviceWidth - 36) / 2,
      y: this.vm.appUIState.safeTop + UIConstants.ACTION_BAR_HEIGHT + 8
    })
  }

  @Builder
  private nativeHomeHeroProgressIndicator() {
    Row({ space: 4 }) {
      ForEach(this.ui.recommendationList, (item: VideoItem, index: number) => {
        Progress({
          value: this.heroSegmentProgress(index),
          total: 100,
          type: ProgressType.Linear
        })
          .layoutWeight(1)
          .height(3)
          .color($r('sys.color.icon_primary'))
          .backgroundColor(this.heroProgressTrackColor())
          .style({
            strokeWidth: 3,
            strokeRadius: 1.5,
            enableSmoothEffect: false,
            enableScanEffect: false
          })
      }, (item: VideoItem) => `hero-progress:${item.id}`)
    }
    .width('100%')
    .height(3)
    .padding({ left: 16, right: 16 })
    .margin({ bottom: 14 })
  }

  @Builder
  private nativeHomeHeroReadabilityScrim() {
    Column()
      .width('100%')
      .height('100%')
      .linearGradient({
        angle: 180,
        colors: [
          ['rgba(0,0,0,0.08)', 0.0],
          ['rgba(0,0,0,0.16)', 0.42],
          ['rgba(0,0,0,0.88)', 1.0]
        ]
      })
  }

  @Builder
  private nativeHomeHeroCanvasTransition() {
    Column()
      .width('100%')
      .height(96)
      .linearGradient({
        angle: 180,
        colors: [
          [Color.Transparent, 0.0],
          [$r('app.color.native_canvas_background'), 1.0]
        ]
      })
  }

  @Builder
  private nativeHomeHero(item: VideoItem) {
    Stack({ alignContent: Alignment.BottomStart }) {
      ImageKnifeComponent({
        imageKnifeOption: this.heroImageOption(item)
      })
        .width('100%')
        .height('100%')

      this.nativeHomeHeroReadabilityScrim()
      this.nativeHomeHeroCanvasTransition()

      Column({ space: 8 }) {
        Text(item.name)
          .fontSize(28)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })

        if (item.subtitle && item.subtitle.length > 0) {
          Text(item.subtitle)
            .fontSize(14)
            .fontColor('rgba(255,255,255,0.82)')
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }

        if (item.description && item.description.length > 0) {
          Text(item.description)
            .fontSize(13)
            .fontColor('rgba(255,255,255,0.74)')
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }

      }
      .width('100%')
      .padding({
        left: 16,
        right: 16,
        bottom: 44
      })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Black)
    .onClick((): void => {
      this.openHeroDetails(item)
    })
  }

  @Builder
  buildResumeList() {
    Column() {
      Text('继续观看')
        .fontSize(20)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.useNativeSurface() ?
          $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .margin({ left: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2 })
      List({ space: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) }) {
        ListItem().width(HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint))
        ForEach(this.ui.resumeList, (item: VideoItem, index: number) => {
          ListItem() {
            HorizontalVideoCard({
              image: item.image,
              imageRef: item.imageRef,
              percent: item.percent,
              name: item.name,
              subtitle: item.subtitle,
              index: index
            })
          }.onClick(() => {
            this.playItem(item)
          })
        }, (item: VideoItem) => item.id)
        ListItem().width(8)
      }
      .width('100%')
      .height(HORIZONTAL_CARD_LIST_HEIGHT)
      .margin({ top: 12 })
      .listDirection(Axis.Horizontal)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .margin({
      top: 20
    })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  buildNextUpList() {
    Column() {
      Text('接下来')
        .fontSize(20)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.useNativeSurface() ?
          $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .margin({ left: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2 })
      List({ space: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) }) {
        ListItem().width(HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint))

        ForEach(this.ui.nextUpList, (item: VideoItem, index: number) => {
          ListItem() {
            HorizontalVideoCard({
              image: item.image,
              imageRef: item.imageRef,
              percent: item.percent,
              name: item.name,
              subtitle: item.subtitle,
              index: index
            })
          }.onClick(() => {
            this.playItem(item)
          })
        })
        ListItem().width(8)
      }
      .width('100%')
      .height(HORIZONTAL_CARD_LIST_HEIGHT)
      .margin({ top: 12 })
      .listDirection(Axis.Horizontal)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .margin({
      top: 20
    })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  customRefreshComponent() {
    CustomRefreshComponent({
      refreshOffset: this.ui.refreshOffset,
      refreshStatus: this.ui.refreshStatus
    })
  }

}
