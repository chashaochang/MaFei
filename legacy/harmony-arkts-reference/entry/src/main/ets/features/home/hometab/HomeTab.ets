import { HMRouterMgr } from "@hadss/hmrouter"
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
import { AnimatorResult, curves } from "@kit.ArkUI"
import { ConfigurationConstant } from '@kit.AbilityKit'
import { HomeInitialSkeleton } from './HomeInitialSkeleton'
import { MediaLibraryKind } from '../../../media/catalog/MediaCatalogModels'
import { MediaRef, MediaRefKind } from '../../../media/MediaRef'
import { toMediaRouteRefData } from '../../../media/navigation/MediaRouteRef'
import { MediaNetworkImage } from '../../../component/MediaNetworkImage'
import { HomeContentLayoutPolicy } from '../HomeContentLayoutPolicy'
import { HomeTabletHero } from './HomeTabletHero'
import { Constants } from '../../../utils/Constants'

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
const HOME_LIBRARY_SECTION_TOP_GAP: number = 16

@ComponentV2
struct HomeLatestMediaHeader {
  @Require @Param tabs: Array<HomeFeedTabItem>
  @Require @Param selectedId: string
  @Param nativeSurface: boolean = false
  @Param fadeRightEdge: boolean = false
  @Param rightContentPadding: number = 0
  @Param alignmentOffset: number = 0
  @Param headerHeight: number = HOME_LIBRARY_INLINE_HEADER_HEIGHT
  @Param sidePadding: number = 0
  @Param menuInset: number = 0
  @Param topPadding: number = 0
  @Param pinned: boolean = false
  @Param showPinnedActions: boolean = false
  @Param hasLiveTv: boolean = false
  @Param systemMaterialAvailable: boolean = false
  @Event onSelectedIdChange: (id: string) => void = (_id: string): void => {}
  @Event onLiveTv: () => void = (): void => {}
  @Event onSearch: () => void = (): void => {}
  @Event onAreaChanged: (newValue: Area) => void = (_newValue: Area): void => {}

  build() {
    Row({ space: 8 }) {
      HomeLibraryChipSelector({
        tabs: this.tabs,
        selectedId: this.selectedId,
        nativeSurface: this.nativeSurface,
        fadeRightEdge: this.fadeRightEdge,
        rightContentPadding: this.rightContentPadding,
        onSelectedIdChange: this.onSelectedIdChange
      })
        .layoutWeight(1)
        .translate({ y: this.alignmentOffset })
      if (this.pinned && this.nativeSurface && this.showPinnedActions) {
        if (this.hasLiveTv) {
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
            this.nativeSurface,
            this.systemMaterialAvailable))
          .accessibilityText('直播电视')
          .onClick(this.onLiveTv)
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
          this.nativeSurface,
          this.systemMaterialAvailable))
        .accessibilityText('搜索')
        .onClick(this.onSearch)
      }
    }
    .width('100%')
    .height(this.headerHeight)
    .padding({
      left: this.sidePadding,
      right: this.menuInset,
      top: this.topPadding
    })
    .alignItems(VerticalAlign.Center)
    .backgroundColor(Color.Transparent)
    .zIndex(this.pinned && this.nativeSurface ? 10 : 0)
    .hitTestBehavior(HitTestMode.Transparent)
    .onAreaChange((_oldValue: Area, newValue: Area): void => {
      this.onAreaChanged(newValue)
    })
  }
}

@ComponentV2
export struct HomeTab {
  @Param active: boolean = true
  @Param useHomeContentWidth: boolean = false
  @Param tabletHeroLeadingOverflow: number = 0
  @Param latestGridColumnCount: number = 0
  @Param stickyHeaderAnchorY: number = 0
  @Param stickyHeaderPinnedTopInset: number = -1
  @Param contentBottomInset?: number = undefined
  @Param contentScroller: Scroller = new Scroller()
  @Event onTabletHeroStateChange: (items: VideoItem[], selectedIndex: number,
    pagePositions: number[], mainAxisLength: number, verticalOffset: number) => void =
    (_items: VideoItem[], _selectedIndex: number, _pagePositions: number[], _mainAxisLength: number,
      _verticalOffset: number): void => {}
  @Local ui: HomeUIState = new HomeUIState(this.getUIContext())
  @Local vm: HomeViewModel = new HomeViewModel(this.ui)
  @Local heroIndex: number = 0
  @Local heroProgress: number = 0
  @Local tabletHeroSelectedIndex: number = 0
  @Local tabletHeroPagePositions: number[] = []
  @Local tabletHeroMainAxisLength: number = 0
  @Local tabletHeroVisible: boolean = true
  @Local tabletHeroVerticalOffset: number = 0
  @Local nativeHeroHeight: number = HOME_HERO_BASE_HEIGHT
  @Local nativeHeroScrollState: ScrollState = ScrollState.Idle
  @Local nativeHeroRefreshProgress: number = 0
  @Local latestChipStripPinned: boolean = false
  @Local latestChipStripMenuReserved: boolean = false
  private heroProgressTimer: number = -1
  private heroProgressAnimator?: AnimatorResult
  private heroProgressGeneration: number = 0
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

  private stopHeroProgress(): void {
    this.heroProgressGeneration++
    this.cancelHeroProgressStart()
    if (this.heroProgressAnimator) {
      this.heroProgressAnimator.cancel()
      this.heroProgressAnimator = undefined
    }
  }

  private restartHeroProgress(index: number): void {
    this.stopHeroProgress()
    const itemCount = this.ui.recommendationList.length
    this.heroIndex = itemCount > 0 ? Math.max(0, Math.min(index, itemCount - 1)) : 0
    this.heroProgress = 0
    if (itemCount <= 1) {
      return
    }
    const generation = this.heroProgressGeneration
    const targetIndex = this.heroIndex
    this.heroProgressTimer = setTimeout(() => {
      this.heroProgressTimer = -1
      if (generation !== this.heroProgressGeneration || targetIndex !== this.heroIndex) {
        return
      }
      const progressAnimator = this.getUIContext().createAnimator({
        duration: HERO_AUTOPLAY_INTERVAL,
        easing: 'linear',
        delay: 0,
        fill: 'forwards',
        direction: 'normal',
        iterations: 1,
        begin: 0,
        end: 100
      })
      progressAnimator.onFrame = (progress: number): void => {
        if (generation === this.heroProgressGeneration && targetIndex === this.heroIndex) {
          this.heroProgress = progress
        }
      }
      progressAnimator.onFinish = (): void => {
        if (generation === this.heroProgressGeneration && targetIndex === this.heroIndex) {
          this.heroProgress = 100
          this.heroProgressAnimator = undefined
        }
      }
      this.heroProgressAnimator = progressAnimator
      progressAnimator.play()
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

  private contentBreakpoint(): string {
    return HomeContentLayoutPolicy.breakpoint(this.vm.appUIState, this.useHomeContentWidth)
  }

  private contentWidth(): number {
    return HomeContentLayoutPolicy.width(this.vm.appUIState, this.useHomeContentWidth)
  }

  private phoneContentTopInset(): number {
    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  private showNativeHero(): boolean {
    return (this.useNativeSurface() || !this.contentBreakpoint().includes('s')) &&
      this.ui.recommendationList.length > 0
  }

  private showNativeHeroSkeleton(): boolean {
    return this.useNativeSurface() || !this.contentBreakpoint().includes('s')
  }

  private tabletHeroHeight(): number {
    return HomeContentLayoutPolicy.tabletHeroHeight(this.vm.appUIState, this.useHomeContentWidth)
  }

  private updateTabletHeroMotion(selectedIndex: number, index: number, position: number,
    mainAxisLength: number): void {
    if (index < 0 || index >= this.ui.recommendationList.length) {
      return
    }
    this.tabletHeroSelectedIndex = selectedIndex
    this.tabletHeroMainAxisLength = mainAxisLength
    const nextPositions = this.tabletHeroPagePositions.slice()
    nextPositions[index] = position
    this.tabletHeroPagePositions = nextPositions
    this.publishTabletHeroState()
  }

  private commitTabletHeroIndex(index: number): void {
    if (index < 0 || index >= this.ui.recommendationList.length) {
      return
    }
    this.heroIndex = index
    this.tabletHeroSelectedIndex = index
    const nextPositions: number[] = []
    nextPositions[index] = 0
    if (this.ui.recommendationList.length > 1) {
      const previousIndex = (index - 1 + this.ui.recommendationList.length) %
        this.ui.recommendationList.length
      const nextIndex = (index + 1) % this.ui.recommendationList.length
      nextPositions[previousIndex] = -1
      nextPositions[nextIndex] = 1
    }
    this.tabletHeroPagePositions = nextPositions
    this.publishTabletHeroState()
  }

  private publishTabletHeroState(): void {
    this.onTabletHeroStateChange(
      this.ui.recommendationList.slice(),
      this.tabletHeroSelectedIndex,
      this.tabletHeroPagePositions.slice(),
      this.tabletHeroMainAxisLength,
      this.tabletHeroVerticalOffset
    )
  }

  private updateTabletHeroVisibility(): void {
    if (this.contentBreakpoint().includes('s')) {
      this.tabletHeroVisible = true
      this.tabletHeroVerticalOffset = 0
      this.publishTabletHeroState()
      return
    }
    const yOffset = this.contentScroller.currentOffset().yOffset
    this.tabletHeroVerticalOffset = Math.max(0, yOffset)
    this.tabletHeroVisible = yOffset < Math.max(1, this.tabletHeroHeight() - 24)
    this.publishTabletHeroState()
  }

  private heroDisplayHeight(): number {
    if (this.contentBreakpoint().includes('s')) {
      return this.nativeHeroHeight
    }
    return this.tabletHeroHeight()
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
    return HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2
  }

  private latestMediaHeaderTopInset(): number {
    if (!this.useNativeSurface() || !this.latestChipStripPinned) {
      return 0
    }
    if (this.stickyHeaderPinnedTopInset >= 0) {
      return this.stickyHeaderPinnedTopInset
    }
    return this.contentBreakpoint().includes('s') ? Math.max(0, this.vm.appUIState.safeTop) : 0
  }

  private latestMediaHeaderHeight(): number {
    if (!this.latestChipStripPinned) {
      const inlineHeight = this.useNativeSurface() && this.contentBreakpoint().includes('s') ?
        HOME_LIBRARY_INLINE_HEADER_HEIGHT : UIConstants.ACTION_BAR_HEIGHT
      return inlineHeight + HOME_LIBRARY_SECTION_TOP_GAP
    }
    return UIConstants.ACTION_BAR_HEIGHT + this.latestMediaHeaderTopInset()
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
    return this.latestChipStripPinned ? this.latestMediaHeaderTopInset() : HOME_LIBRARY_SECTION_TOP_GAP
  }

  private latestMediaChipAlignmentOffset(): number {
    if (!this.useNativeSurface() || !this.contentBreakpoint().includes('s')) {
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

  @Monitor('ui.recommendationList')
  onRecommendationListChange(): void {
    if (this.ui.recommendationList.length === 0) {
      this.heroIndex = 0
      this.tabletHeroSelectedIndex = 0
      this.tabletHeroPagePositions = []
      this.tabletHeroMainAxisLength = 0
      this.tabletHeroVerticalOffset = 0
    } else if (this.tabletHeroSelectedIndex >= this.ui.recommendationList.length) {
      this.commitTabletHeroIndex(0)
      return
    }
    this.publishTabletHeroState()
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
    this.stopHeroProgress()
    this.nativeHeroHeight = HOME_HERO_BASE_HEIGHT
    this.nativeHeroScrollState = ScrollState.Idle
    this.nativeHeroRefreshProgress = 0
    this.latestChipStripPinned = false
    this.latestChipStripMenuReserved = false
    this.vm.appUIState.rootNavigationHomeLibraryPinned = false
  }

  private updateLatestMediaHeaderPinned(area: Area): void {
    if (!this.useNativeSurface()) {
      this.latestChipStripMenuReserved = false
      this.updateLatestChipStripPinned(false)
      return
    }
    const globalY = area.globalPosition.y as number
    const isPhone = this.contentBreakpoint().includes('s')
    this.latestChipStripMenuReserved = isPhone &&
      globalY <= this.stickyHeaderAnchorY + UIConstants.ACTION_BAR_HEIGHT
    this.updateLatestChipStripPinned(globalY <= this.stickyHeaderAnchorY)
  }

  // CustomBuilderAdapter: home-latest-media-sticky-header
  @Builder
  private latestMediaStickyHeader() {
    HomeLatestMediaHeader({
      tabs: this.latestMediaTabs(),
      selectedId: this.ui.selectedLatestMediaId,
      nativeSurface: this.useNativeSurface(),
      fadeRightEdge: this.latestChipStripMenuReserved,
      rightContentPadding: this.latestChipStripMenuReserved ?
        HOME_LIBRARY_HEADER_EXTRA_MENU_GAP : 0,
      alignmentOffset: this.latestMediaChipAlignmentOffset(),
      headerHeight: this.latestMediaHeaderHeight(),
      sidePadding: this.latestMediaHeaderSidePadding(),
      menuInset: this.latestMediaHeaderMenuInset(),
      topPadding: this.latestMediaHeaderTopPadding(),
      pinned: this.latestChipStripPinned,
      showPinnedActions: this.contentBreakpoint().includes('s'),
      hasLiveTv: this.ui.hasLiveTv,
      systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable,
      onSelectedIdChange: (id: string): void => this.selectLatestMedia(id),
      onLiveTv: (): void => {
        HMRouterMgr.to(RouterConsts.LiveTvPage)
          .withNavigation(Constants.NAVIGATION_ID)
          .push()
      },
      onSearch: (): void => {
        HMRouterMgr.to(RouterConsts.SearchPage).push()
      },
      onAreaChanged: (newValue: Area): void => {
        this.updateLatestMediaHeaderPinned(newValue)
      }
    })
  }







  build() {
    Stack() {
      if (this.useNativeSurface() && this.contentBreakpoint().includes('s')) {
        List({ scroller: this.contentScroller }) {
            if (this.contentBreakpoint().includes('s') &&
              (!this.useNativeSurface() || (!this.ui.initialLoading && !this.showNativeHero()))) {
              ListItem().height(this.phoneContentTopInset())
            }
            if (this.ui.initialLoading) {
              ListItem() {
                HomeInitialSkeleton({
                  showHero: this.showNativeHeroSkeleton(),
                  currentBreakpoint: this.contentBreakpoint(),
                  heroHeight: this.heroDisplayHeight(),
                  gridColumnCount: this.latestGridColumnCount
                })
              }
            } else {
              if (this.showNativeHero()) {
                ListItem() {
                  if (this.contentBreakpoint().includes('s')) {
                    Stack({ alignContent: Alignment.BottomStart }) {
                      Swiper() {
                        ForEach(this.ui.recommendationList, (item: VideoItem) => {
                          Stack({ alignContent: Alignment.BottomStart }) {
                            MediaNetworkImage({
                              imageRef: (item).imageRef,
                              fallbackSource: (item).image
                            })
                              .width('100%')
                              .height('100%')

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
                            Column()
                              .width('100%')
                              .height(96)
                              .linearGradient({
                                angle: 180,
                                colors: [
                                  [Color.Transparent, 0.0],
                                  [this.useNativeSurface() ? $r('app.color.native_canvas_background') :
                                    $r('app.color.bg_main'), 1.0]
                                ]
                              })

                            Column({ space: 8 }) {
                              Text((item).name)
                                .fontSize(28)
                                .fontWeight(FontWeight.Bold)
                                .fontColor(Color.White)
                                .maxLines(2)
                                .textOverflow({ overflow: TextOverflow.Ellipsis })

                              if ((item).subtitle && (item).subtitle.length > 0) {
                                Text((item).subtitle)
                                  .fontSize(14)
                                  .fontColor('rgba(255,255,255,0.82)')
                                  .maxLines(1)
                                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                              }

                              if ((item).description && (item).description.length > 0) {
                                Text((item).description)
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
                            this.openHeroDetails((item))
                          })
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

                      if (this.nativeHeroRefreshProgress > 0 || this.ui.isRefreshing) {
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
                          x: (this.contentWidth() - 36) / 2,
                          y: this.vm.appUIState.safeTop + UIConstants.ACTION_BAR_HEIGHT + 8
                        })
                      }
                    }
                    .width('100%')
                    .height('100%')
                    .onAppear(() => {
                      this.restartHeroProgress(this.heroIndex)
                    })
                    .onDisAppear(() => {
                      this.stopHeroProgress()
                    })
                  } else {
                    HomeTabletHero({
                      items: this.ui.recommendationList,
                      heroHeight: this.tabletHeroHeight(),
                      active: this.active && this.tabletHeroVisible,
                      leadingOverflow: this.tabletHeroLeadingOverflow,
                      autoplayInterval: HERO_AUTOPLAY_INTERVAL,
                      transitionDuration: HERO_TRANSITION_DURATION,
                      onOpenDetails: (item: VideoItem): void => {
                        this.openHeroDetails(item)
                      },
                      onIndexChange: (index: number): void => {
                        this.commitTabletHeroIndex(index)
                      },
                      onContentScroll: (selectedIndex: number, index: number, position: number,
                        mainAxisLength: number): void => {
                        this.updateTabletHeroMotion(selectedIndex, index, position, mainAxisLength)
                      }
                    })
                  }
                }
                .height(this.heroDisplayHeight())
              }
              if (this.ui.resumeList.length > 0) {
                ListItem() {
                  Column() {
                    Text('继续观看')
                      .fontSize(20)
                      .fontWeight(FontWeight.Medium)
                      .fontColor(this.useNativeSurface() ?
                        $r('sys.color.font_primary') : $r('app.color.text_primary'))
                      .margin({ left: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2 })
                    List({ space: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) }) {
                      ListItem().width(HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()))
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
              }
              if (this.ui.nextUpList.length > 0) {
                ListItem() {
                  Column() {
                    Text('接下来')
                      .fontSize(20)
                      .fontWeight(FontWeight.Medium)
                      .fontColor(this.useNativeSurface() ?
                        $r('sys.color.font_primary') : $r('app.color.text_primary'))
                      .margin({ left: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2 })
                    List({ space: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) }) {
                      ListItem().width(HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()))

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
              }

              ListItemGroup({ header: this.latestMediaStickyHeader() }) {
                ListItem() {
                HomeLatestMediaSection({
                  latestMap: this.ui.latestMap,
                  selectedMediaId: this.ui.selectedLatestMediaId,
                  nativeSurface: this.useNativeSurface(),
                  currentBreakpoint: this.contentBreakpoint(),
                  gridColumnCount: this.latestGridColumnCount,
                  loading: this.ui.latestMediaLoading,
                  loadingMore: this.ui.latestMediaLoadingMore,
                  hasMore: this.ui.latestMediaHasMore,
                  loadFailed: this.ui.latestMediaLoadFailed,
                  contentMinHeight: this.latestFeedContentMinHeight(),
                  onRetry: (): void => {
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
        .sticky(this.useNativeSurface() ?
          StickyStyle.Header : StickyStyle.None)
        .scrollBar(BarState.Off)
        .onScrollFrameBegin((offset: number, state: ScrollState) => {
          if ((true) && this.stretchNativeHero(offset, state)) {
            return { offsetRemain: 0 }
          }
          return { offsetRemain: offset }
        })
        .onDidScroll(() => {
          this.updateTabletHeroVisibility()
          if ((true) && this.contentScroller.currentOffset().yOffset === 0 &&
            this.nativeHeroScrollState === ScrollState.Scroll) {
            this.springBackNativeHero()
          }
        })
        .onReachEnd(() => {
          this.vm.loadMoreLatestMedia()
        })
      } else {
        Refresh({ refreshing: $$this.ui.isRefreshing }) {
          List({ scroller: this.contentScroller }) {
              if (this.contentBreakpoint().includes('s') &&
                (!this.useNativeSurface() || (!this.ui.initialLoading && !this.showNativeHero()))) {
                ListItem().height(this.phoneContentTopInset())
              }
              if (this.ui.initialLoading) {
                ListItem() {
                  HomeInitialSkeleton({
                    showHero: this.showNativeHeroSkeleton(),
                    currentBreakpoint: this.contentBreakpoint(),
                    heroHeight: this.heroDisplayHeight(),
                    gridColumnCount: this.latestGridColumnCount
                  })
                }
              } else {
                if (this.showNativeHero()) {
                  ListItem() {
                    if (this.contentBreakpoint().includes('s')) {
                      Stack({ alignContent: Alignment.BottomStart }) {
                        Swiper() {
                          ForEach(this.ui.recommendationList, (item: VideoItem) => {
                            Stack({ alignContent: Alignment.BottomStart }) {
                              MediaNetworkImage({
                                imageRef: (item).imageRef,
                                fallbackSource: (item).image
                              })
                                .width('100%')
                                .height('100%')

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
                              Column()
                                .width('100%')
                                .height(96)
                                .linearGradient({
                                  angle: 180,
                                  colors: [
                                    [Color.Transparent, 0.0],
                                    [this.useNativeSurface() ? $r('app.color.native_canvas_background') :
                                      $r('app.color.bg_main'), 1.0]
                                  ]
                                })

                              Column({ space: 8 }) {
                                Text((item).name)
                                  .fontSize(28)
                                  .fontWeight(FontWeight.Bold)
                                  .fontColor(Color.White)
                                  .maxLines(2)
                                  .textOverflow({ overflow: TextOverflow.Ellipsis })

                                if ((item).subtitle && (item).subtitle.length > 0) {
                                  Text((item).subtitle)
                                    .fontSize(14)
                                    .fontColor('rgba(255,255,255,0.82)')
                                    .maxLines(1)
                                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                                }

                                if ((item).description && (item).description.length > 0) {
                                  Text((item).description)
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
                              this.openHeroDetails((item))
                            })
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

                        if (this.nativeHeroRefreshProgress > 0 || this.ui.isRefreshing) {
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
                            x: (this.contentWidth() - 36) / 2,
                            y: this.vm.appUIState.safeTop + UIConstants.ACTION_BAR_HEIGHT + 8
                          })
                        }
                      }
                      .width('100%')
                      .height('100%')
                      .onAppear(() => {
                        this.restartHeroProgress(this.heroIndex)
                      })
                      .onDisAppear(() => {
                        this.stopHeroProgress()
                      })
                    } else {
                      HomeTabletHero({
                        items: this.ui.recommendationList,
                        heroHeight: this.tabletHeroHeight(),
                        active: this.active && this.tabletHeroVisible,
                        leadingOverflow: this.tabletHeroLeadingOverflow,
                        autoplayInterval: HERO_AUTOPLAY_INTERVAL,
                        transitionDuration: HERO_TRANSITION_DURATION,
                        onOpenDetails: (item: VideoItem): void => {
                          this.openHeroDetails(item)
                        },
                        onIndexChange: (index: number): void => {
                          this.commitTabletHeroIndex(index)
                        },
                        onContentScroll: (selectedIndex: number, index: number, position: number,
                          mainAxisLength: number): void => {
                          this.updateTabletHeroMotion(selectedIndex, index, position, mainAxisLength)
                        }
                      })
                    }
                  }
                  .height(this.heroDisplayHeight())
                }
                if (this.ui.resumeList.length > 0) {
                  ListItem() {
                    Column() {
                      Text('继续观看')
                        .fontSize(20)
                        .fontWeight(FontWeight.Medium)
                        .fontColor(this.useNativeSurface() ?
                          $r('sys.color.font_primary') : $r('app.color.text_primary'))
                        .margin({ left: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2 })
                      List({ space: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) }) {
                        ListItem().width(HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()))
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
                }
                if (this.ui.nextUpList.length > 0) {
                  ListItem() {
                    Column() {
                      Text('接下来')
                        .fontSize(20)
                        .fontWeight(FontWeight.Medium)
                        .fontColor(this.useNativeSurface() ?
                          $r('sys.color.font_primary') : $r('app.color.text_primary'))
                        .margin({ left: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2 })
                      List({ space: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) }) {
                        ListItem().width(HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()))

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
                }

                ListItemGroup({ header: this.latestMediaStickyHeader() }) {
                  ListItem() {
                  HomeLatestMediaSection({
                    latestMap: this.ui.latestMap,
                    selectedMediaId: this.ui.selectedLatestMediaId,
                    nativeSurface: this.useNativeSurface(),
                    currentBreakpoint: this.contentBreakpoint(),
                    gridColumnCount: this.latestGridColumnCount,
                    loading: this.ui.latestMediaLoading,
                    loadingMore: this.ui.latestMediaLoadingMore,
                    hasMore: this.ui.latestMediaHasMore,
                    loadFailed: this.ui.latestMediaLoadFailed,
                    contentMinHeight: this.latestFeedContentMinHeight(),
                    onRetry: (): void => {
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
          .sticky(this.useNativeSurface() ?
            StickyStyle.Header : StickyStyle.None)
          .scrollBar(BarState.Off)
          .onScrollFrameBegin((offset: number, state: ScrollState) => {
            if ((false) && this.stretchNativeHero(offset, state)) {
              return { offsetRemain: 0 }
            }
            return { offsetRemain: offset }
          })
          .onDidScroll(() => {
            this.updateTabletHeroVisibility()
            if ((false) && this.contentScroller.currentOffset().yOffset === 0 &&
              this.nativeHeroScrollState === ScrollState.Scroll) {
              this.springBackNativeHero()
            }
          })
          .onReachEnd(() => {
            this.vm.loadMoreLatestMedia()
          })
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
      if (!(this.useNativeSurface() && this.contentBreakpoint().includes('s')) &&
        this.contentBreakpoint().includes('s')) {
        Row({ space: 12 }) {
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
                HMRouterMgr.to(RouterConsts.LiveTvPage)
                  .withNavigation(Constants.NAVIGATION_ID)
                  .push()
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
        .width('100%')
        .height(UIConstants.ACTION_BAR_HEIGHT + this.vm.appUIState.safeTop)
        .backgroundBlurStyle(BlurStyle.COMPONENT_THICK)
        .padding({
          left: 8,
          right: 16,
          top: this.vm.appUIState.safeTop
          })
      }
    }
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Top)
  }













}
