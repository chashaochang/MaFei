import { HMRouterMgr } from "@hadss/hmrouter"
import { ImageKnifeComponent } from "@ohos/imageknifepro"
import { HomeUIState, MediaItem, VideoItem } from "./HomeUIState"
import { HomeViewModel } from "./HomeViewModel"
import { UIConstants } from "../../../common/UIConstants"
import { BaseItemKind } from "sdk/src/main/ets/generated-client/models"
import { generateBeautifulDeepBlueColor, playItems } from "../../../common/CommonFunc"
import { HomeRefreshEvent } from "../../../events/Events"
import { CustomRefreshComponent } from "../../../common/CustomRefreshComponent"
import { ToastUtil } from "../../../utils/ToastUtil"
import { RouterConsts } from "../../../common/RouterConsts"
import { HorizontalVideoCard } from "../../../component/HorizontalVideoCard"
import {
  HORIZONTAL_CARD_LIST_HEIGHT,
  HORIZONTAL_CARD_SPACE,
  VERTICAL_CARD_LIST_HEIGHT
} from "../../../common/OneMoreConst"
import { VerticalVideoCard } from "../../../component/VerticalVideoCard"
import { AppThemeSurfaceResolver } from "../../../theme/AppThemeSurfaceResolver"
import { AppThemeTokens } from "../../../theme/AppThemeTokens"
import { FeiniuPointLightModifier } from "../../../theme/FeiniuPointLightModifier"
import { HomeInsetsPolicy } from "../HomeInsetsPolicy"

@ComponentV2
export struct HomeTab {
  @Param contentBottomInset?: number = undefined
  @Param contentScroller: Scroller = new Scroller()
  @Local ui: HomeUIState = new HomeUIState(this.getUIContext())
  @Local vm: HomeViewModel = new HomeViewModel(this.ui)

  private scrollTail(): number {
    return HomeInsetsPolicy.scrollTail(this.contentBottomInset, this.vm.appUIState.safeBottom)
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private feiniuPhoneContentTopInset(): number {
    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
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
        if (this.vm.appUIState.currentBreakpoint.includes('s') && !this.useNativeSurface()) {
          ListItem().height(this.feiniuPhoneContentTopInset())
        }
        // if (this.ui.mediaList.length > 0) {
        //   ListItem() {
        //     this.buildMediaList()
        //   }
        // }
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

        ListItem() {
          this.buildAllMediaCount()
        }.visibility(this.vm.appUIState.isBigScreen ? Visibility.None : Visibility.Visible)

        ForEach(this.ui.latestMediaList, (item: MediaItem) => {
          ListItem() {
            this.buildLastAddedList(item)
          }
        })
        ListItem().height(this.scrollTail())
      }
      .width('100%')
      .height('100%')
      .listDirection(Axis.Vertical)
      .scrollBar(BarState.Off)
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
  buildResumeList() {
    Column() {
      Text('继续观看')
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))
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
  buildNextUpList() {
    Column() {
      Text('接下来')
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))
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
  buildLastAddedList(item: MediaItem) {
    Column() {
      Text('最近添加的 ' + item.name).fontSize(14).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
        .margin({ left: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2 })
      List({ space: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) }) {
        ListItem().width(HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint))

        ForEach(this.ui.latestMap.get(item.id), (item: VideoItem, index: number) => {
          ListItem() {
            VerticalVideoCard({
              image: item.image,
              name: item.name,
              subtitle: item.subtitle,
              index: index
            })
          }.onClick(() => {
            HMRouterMgr.to(RouterConsts.VideoDetailPage)
              .withParam({
                id: item.id,
                mediaName: item.mediaName,
                isTvShows: item.type === BaseItemKind.Series,
              })
              .push()
          })
        })
        if (this.vm.appUIState.currentBreakpoint.includes('s')) {
          ListItem().width(HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint))
        }
      }
      .width('100%')
      .height(VERTICAL_CARD_LIST_HEIGHT.getValue(this.vm.appUIState.currentBreakpoint))
      .margin({ top: 12 })
      .listDirection(Axis.Horizontal)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .margin({
      top: 24
    })
    .alignItems(HorizontalAlign.Start)
  }

  private nativeMediaIcon(type: BaseItemKind): Resource {
    if (type === BaseItemKind.Movie) {
      return $r('app.media.bg_trans_movie')
    }
    if (type === BaseItemKind.TvProgram) {
      return $r('app.media.bg_trans_tv')
    }
    return $r('app.media.bg_trans_all')
  }

  private nativeMediaTitle(type: BaseItemKind): string {
    if (type === BaseItemKind.Movie) {
      return '电影'
    }
    if (type === BaseItemKind.TvProgram) {
      return '电视节目'
    }
    return '全部影视'
  }

  private nativeMediaCount(type: BaseItemKind): string {
    if (type === BaseItemKind.Movie) {
      return this.ui.movieCount
    }
    if (type === BaseItemKind.TvProgram) {
      return this.ui.tvCount
    }
    return this.ui.totalCount
  }

  private nativeMediaRouteId(type: BaseItemKind): string {
    if (type === BaseItemKind.Movie) {
      return 'movies'
    }
    if (type === BaseItemKind.TvProgram) {
      return 'tvs'
    }
    return 'all'
  }

  @Builder
  private nativeMediaNavigationItem(type: BaseItemKind) {
    Stack() {
      Image(this.nativeMediaIcon(type))
        .fillColor($r('sys.color.icon_secondary'))
        .width(35)
        .height(35)
        .opacity(0.2)
        .rotate({
          x: 0,
          y: 0,
          z: 1,
          centerX: '50%',
          centerY: '50%',
          angle: 22.5
        })
        .translate({
          x: 5
        })
      Column({ space: 4 }) {
        Text(this.nativeMediaTitle(type))
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('sys.color.font_primary'))
        Text(this.nativeMediaCount(type))
          .fontSize(14)
          .fontWeight(FontWeight.Normal)
          .fontColor($r('sys.color.font_secondary'))
      }
      .width(90)
      .height(60)
      .justifyContent(FlexAlign.Center)
    }
    .width(90)
    .height(60)
    .align(Alignment.BottomEnd)
    .onClick(() => {
      let mediaItem: MediaItem = {
        name: this.nativeMediaTitle(type),
        id: this.nativeMediaRouteId(type),
        image: '',
        childCount: 0,
        type: type
      }
      HMRouterMgr.to(RouterConsts.VideoListPage)
        .withParam(mediaItem)
        .push()
    })
  }

  @Builder
  private nativeAllMediaCount() {
    Row({ space: 8 }) {
      this.nativeMediaNavigationItem(BaseItemKind.Video)
      this.nativeMediaNavigationItem(BaseItemKind.Movie)
      this.nativeMediaNavigationItem(BaseItemKind.TvProgram)
    }
    .width(286)
    .height(60)
    .borderRadius(8)
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
    .margin({
      top: 24, left: 16
    })
  }

  @Builder
  private feiniuAllMediaCount() {
    Row({ space: 8 }) {
      Stack() {
        Image($r('app.media.bg_trans_all'))
          .fillColor(Color.White)
          .width(35)
          .height(35)
          .opacity(0.2)
          .rotate({
            x: 0,
            y: 0,
            z: 1,
            centerX: '50%',
            centerY: '50%',
            angle: 22.5
          })
          .translate({
            x: 5
          })
        Column({ space: 4 }) {
          Text('全部影视')
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
          Text(this.ui.totalCount)
            .fontSize(14)
            .fontWeight(FontWeight.Normal)
            .fontColor(Color.White)
        }
        .width(90)
        .height(60)
        .justifyContent(FlexAlign.Center)
        .onClick(() => {
          let mediaItem: MediaItem = {
            name: '全部影视',
            id: 'all',
            image: '',
            childCount: 0,
            type: BaseItemKind.Video
          }
          HMRouterMgr.to(RouterConsts.VideoListPage)
            .withParam(mediaItem)
            .push()
        })
      }
      .backgroundColor(this.ui.allMediaBgColor)
      .visualEffect(FeiniuPointLightModifier.build({
        enabled: this.vm.appUIState.feiniuPointLightEnabled,
        color: this.ui.allMediaBgColor,
        intensity: 20,
        height: 60
      }))
      .align(Alignment.BottomEnd)
      .borderRadius(8)
      .clip(true)

      Stack() {
        Image($r('app.media.bg_trans_movie'))
          .fillColor(Color.White)
          .width(35)
          .height(35)
          .opacity(0.2)
          .rotate({
            x: 0,
            y: 0,
            z: 1,
            centerX: '50%',
            centerY: '50%',
            angle: 22.5
          })
          .translate({
            x: 5
          })
        Column({ space: 4 }) {
          Text('电影')
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
          Text(this.ui.movieCount)
            .fontSize(14)
            .fontWeight(FontWeight.Normal)
            .fontColor(Color.White)
        }
        .width(90)
        .height(60)
        .justifyContent(FlexAlign.Center)
        .onClick(() => {
          let mediaItem: MediaItem = {
            name: '电影',
            id: 'movies',
            image: '',
            childCount: 0,
            type: BaseItemKind.Movie
          }
          HMRouterMgr.to(RouterConsts.VideoListPage)
            .withParam(mediaItem)
            .push()
        })
      }.backgroundColor(generateBeautifulDeepBlueColor())
      .align(Alignment.BottomEnd)
      .borderRadius(8)
      .clip(true)

      Stack() {
        Image($r('app.media.bg_trans_tv'))
          .fillColor(Color.White)
          .width(35)
          .height(35)
          .opacity(0.2)
          .rotate({
            x: 0,
            y: 0,
            z: 1,
            centerX: '50%',
            centerY: '50%',
            angle: 22.5
          })
          .translate({
            x: 5
          })
        Column({ space: 4 }) {
          Text('电视节目')
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
          Text(this.ui.tvCount)
            .fontSize(14)
            .fontWeight(FontWeight.Normal)
            .fontColor(Color.White)
        }
        .width(90)
        .height(60)
        .justifyContent(FlexAlign.Center)
        .onClick(() => {
          let mediaItem: MediaItem = {
            name: '电视节目',
            id: 'tvs',
            image: '',
            childCount: 0,
            type: BaseItemKind.TvProgram
          }
          HMRouterMgr.to(RouterConsts.VideoListPage)
            .withParam(mediaItem)
            .push()
        })
      }.backgroundColor(generateBeautifulDeepBlueColor())
      .align(Alignment.BottomEnd)
      .borderRadius(8)
      .clip(true)
    }
    .margin({
      top: 24, left: 16
    })
  }

  @Builder
  buildAllMediaCount() {
    if (this.useNativeSurface()) {
      this.nativeAllMediaCount()
    } else {
      this.feiniuAllMediaCount()
    }
  }

  @Builder
  customRefreshComponent() {
    CustomRefreshComponent({
      refreshOffset: this.ui.refreshOffset,
      refreshStatus: this.ui.refreshStatus
    })
  }
}
