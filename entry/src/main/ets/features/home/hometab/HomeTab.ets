import { HMRouterMgr } from "@hadss/hmrouter"
import { ImageKnifeComponent } from "@ohos/imageknifepro"
import { HOME_RECENT_MEDIA_ID, HomeUIState, MediaItem, VideoItem } from "./HomeUIState"
import { HomeViewModel } from "./HomeViewModel"
import { UIConstants } from "../../../common/UIConstants"
import { BaseItemKind } from "sdk/src/main/ets/generated-client/models"
import { playItems } from "../../../common/CommonFunc"
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
import {
  ChipV2,
  ChipV2Label,
  ChipV2Options,
  ChipV2PrefixImageIcon,
  ChipV2Size,
  ColorMetrics
} from "@kit.ArkUI"

const HERO_AUTOPLAY_INTERVAL: number = 6500
const HERO_TRANSITION_DURATION: number = 450
const HERO_PROGRESS_START_DELAY: number = 16
const HOME_LIBRARY_HEADER_EXTRA_MENU_GAP: number = 12
const HOME_LIBRARY_HEADER_ALIGNMENT_OFFSET: number = 3
const HOME_LIBRARY_SEARCH_MENU_WIDTH: number = 56
const HOME_LIBRARY_LIVE_MENU_WIDTH: number = 104
const HOME_LIBRARY_SEARCH_BUTTON_SIZE: number = 40
const HOME_LIBRARY_SEARCH_ICON_SIZE: number = 24

@ComponentV2
export struct HomeTab {
  @Param contentBottomInset?: number = undefined
  @Param contentScroller: Scroller = new Scroller()
  @Local ui: HomeUIState = new HomeUIState(this.getUIContext())
  @Local vm: HomeViewModel = new HomeViewModel(this.ui)
  @Local heroIndex: number = 0
  @Local heroProgress: number = 0
  @Local latestChipStripPinned: boolean = false
  @Local latestChipStripMenuReserved: boolean = false
  private heroProgressTimer: number = -1

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
    return Math.max(0, this.vm.appUIState.safeTop)
  }

  private latestMediaHeaderMenuInset(): number {
    if (!this.latestChipStripMenuReserved) {
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
    return this.latestChipStripPinned ? HOME_LIBRARY_HEADER_ALIGNMENT_OFFSET : 0
  }

  private updateLatestChipStripPinned(pinned: boolean): void {
    if (this.latestChipStripPinned !== pinned) {
      this.latestChipStripPinned = pinned
    }
    this.vm.appUIState.rootNavigationHomeLibraryPinned = pinned
  }

  private selectLatestMedia(id: string): void {
    this.vm.selectLatestMedia(id)
  }

  private openHeroDetails(item: VideoItem): void {
    const seriesId = item.seriesId || ''
    const openSeries = item.type === BaseItemKind.Series ||
      (item.type === BaseItemKind.Episode && seriesId.length > 0)
    const targetId = item.type === BaseItemKind.Episode && seriesId.length > 0 ? seriesId : item.id
    const mediaName = item.mediaName ||
      (openSeries ? '电视节目' : item.type === BaseItemKind.Movie ? '电影' : '')
    HMRouterMgr.to(RouterConsts.VideoDetailPage)
      .withParam({
        id: targetId,
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

  private stickyLiveTvOptions(): ChipV2Options {
    return new ChipV2Options({
      label: new ChipV2Label({ text: '' }),
      prefixIcon: new ChipV2PrefixImageIcon({
        src: $r('app.media.iptv'),
        fillColor: ColorMetrics.resourceColor($r('sys.color.icon_primary'))
      }),
      size: ChipV2Size.NORMAL,
      allowClose: false,
      backgroundSystemMaterial: AppThemeSurfaceResolver.material(
        AppThemeMaterialRole.HomeLibraryChip),
      onClicked: (): void => {
        HMRouterMgr.to(RouterConsts.LiveTvPage).push()
      }
    })
  }

  @Builder
  private latestMediaStickyHeader() {
    Row({ space: 8 }) {
      HomeLibraryChipSelector({
        tabs: this.latestMediaTabs(),
        selectedId: this.ui.selectedLatestMediaId,
        fadeRightEdge: this.latestChipStripMenuReserved,
        rightContentPadding: this.latestChipStripMenuReserved ?
          HOME_LIBRARY_HEADER_EXTRA_MENU_GAP : 0,
        onSelectedIdChange: (id: string) => {
          this.selectLatestMedia(id)
        }
      })
        .layoutWeight(1)
        .translate({ y: this.latestMediaChipAlignmentOffset() })

      if (this.latestChipStripPinned) {
        if (this.ui.hasLiveTv) {
          ChipV2({ chipV2Options: this.stickyLiveTvOptions() })
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
        .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.HomeLibraryChip))
        .accessibilityText('搜索')
        .onClick(() => {
          HMRouterMgr.to(RouterConsts.SearchPage).push()
        })
      }
    }
    .width('100%')
    .height(UIConstants.ACTION_BAR_HEIGHT + this.latestMediaHeaderTopInset())
    .padding({
      left: this.latestMediaHeaderSidePadding(),
      right: this.latestMediaHeaderMenuInset(),
      top: this.latestMediaHeaderTopPadding()
    })
    .alignItems(VerticalAlign.Center)
    .backgroundColor(Color.Transparent)
    .zIndex(this.latestChipStripPinned ? 10 : 0)
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
  private homeRefreshContent() {
    Refresh({ refreshing: $$this.ui.isRefreshing, builder: this.customRefreshComponent() }) {
      List({ scroller: this.contentScroller }) {
        if (this.vm.appUIState.currentBreakpoint.includes('s') &&
          (!this.useNativeSurface() || !this.showNativeHero())) {
          ListItem().height(this.phoneContentTopInset())
        }
        // if (this.ui.mediaList.length > 0) {
        //   ListItem() {
        //     this.buildMediaList()
        //   }
        // }
        if (this.showNativeHero()) {
          ListItem() {
            this.nativeHomeHeroCarousel()
          }
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

        ListItem().height(this.scrollTail())
      }
      .width('100%')
      .height('100%')
      .listDirection(Axis.Vertical)
      .sticky(this.useNativeSurface() && this.vm.appUIState.currentBreakpoint.includes('s') ?
        StickyStyle.Header : StickyStyle.None)
      .scrollBar(BarState.Off)
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
                imageKnifeOption: {
                  loadSrc: item.image,
                  objectFit: ImageFit.Contain,
                  border: {
                    radius: 6
                  },
                  placeholderSrc: $r('app.media.placeholder_video'),
                  placeholderObjectFit: ImageFit.CENTER
                }
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
            if (item.type == undefined) {
              ToastUtil.showToast(this.getUIContext(), '暂不支持混合文件夹')
              return;
            } else if (item.type == BaseItemKind.LiveTvChannel) {
              HMRouterMgr.to(RouterConsts.LiveTvPage)
                .push()
              return
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
    }
    .width('100%')
    .height(430)
    .onAppear(() => {
      this.restartHeroProgress(this.heroIndex)
    })
    .onDisAppear(() => {
      this.cancelHeroProgressStart()
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
          .backgroundColor($r('sys.color.icon_secondary'))
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
  private nativeHeroDetailsButton(item: VideoItem) {
    Button({ type: ButtonType.Normal, stateEffect: false }) {
      Row({ space: 4 }) {
        Text('详情')
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor(Color.White)
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(15)
          .fontColor([Color.White])
      }
      .justifyContent(FlexAlign.Center)
    }
    .width(92)
    .height(44)
    .borderRadius(22)
    .buttonStyle(ButtonStyleMode.TEXTUAL)
    .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.InteractiveFloating))
    .onClick(() => {
      this.openHeroDetails(item)
    })
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
        imageKnifeOption: {
          loadSrc: item.image,
          objectFit: ImageFit.Cover,
          placeholderSrc: $r('app.media.placeholder_video'),
          placeholderObjectFit: ImageFit.CENTER
        }
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

        Row({ space: 10 }) {
          Button({ type: ButtonType.Normal, stateEffect: true }) {
            Row({ space: 7 }) {
              SymbolGlyph($r('sys.symbol.play_fill'))
                .fontSize(18)
                .fontColor([Color.Black])
              Text('播放')
                .fontSize(15)
                .fontWeight(FontWeight.Medium)
                .fontColor(Color.Black)
            }
            .justifyContent(FlexAlign.Center)
          }
          .width(148)
          .height(44)
          .borderRadius(22)
          .backgroundColor(Color.White)
          .onClick(() => {
            playItems([item.id], this.getUIContext().getHostContext()!)
          })

          this.nativeHeroDetailsButton(item)
        }
        .width('100%')
      }
      .width('100%')
      .padding({
        left: 16,
        right: 16,
        bottom: 64
      })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .height(430)
    .backgroundColor(Color.Black)
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
              percent: item.percent,
              name: item.name,
              subtitle: item.subtitle,
              index: index
            })
          }.onClick(() => {
            playItems([item.id], this.getUIContext().getHostContext()!)
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
              percent: item.percent,
              name: item.name,
              subtitle: item.subtitle,
              index: index
            })
          }.onClick(() => {
            playItems([item.id], this.getUIContext().getHostContext()!)
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
