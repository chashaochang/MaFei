import { GridView, RefreshLayoutStatus } from "@abner/refresh_v2";
import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { ImageKnifeComponent } from "@ohos/imageknifepro";
import { BaseItemKind } from "sdk/src/main/ets/generated-client/models";
import { PageState } from "../../common/base/BaseUIState";
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { EmptyView } from "../../component/EmptyView";
import { FailedView } from "../../component/FailedView";
import { SkeletonGridView } from "../../component/skeleton/SkeletonGridView";
import { sortOptions } from "../../entity/SortOption";
import { VideoItem } from "../home/hometab/HomeUIState";
import { LiveTvChannelListUIState } from "./LiveTvChannelListUIState";
import { LiveTvChannelListViewModel } from "./LiveTvChannelListViewModel";
import { BreakpointTypeEnum } from "../../utils/BreakpointSystem";
import { AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../../entity/AppUIState";
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@HMRouter({
  pageUrl: RouterConsts.LiveTvChannelListPage,
  useNavDst: true
})
@ComponentV2
export struct LiveTvChannelListPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Local ui: LiveTvChannelListUIState = new LiveTvChannelListUIState(this.getUIContext())
  @Local vm: LiveTvChannelListViewModel = new LiveTvChannelListViewModel(this.ui)

  aboutToAppear(): void {
    this.vm.init()
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: this.ui.title
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              LiveTvChannelListContent({ ui: this.ui, vm: this.vm, showLegacyActionBar: false })
            } else {
              LiveTvChannelListContent({ ui: this.ui, vm: this.vm, showLegacyActionBar: true })
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
        LiveTvChannelListContent({ ui: this.ui, vm: this.vm, showLegacyActionBar: true })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}

@ComponentV2
struct LiveTvChannelListContent {
  @Require @Param ui: LiveTvChannelListUIState
  @Require @Param vm: LiveTvChannelListViewModel
  @Param showLegacyActionBar: boolean = false

  private primaryTextColor(): ResourceColor {
    return this.showLegacyActionBar ? $r('app.color.text_primary') : $r('sys.color.font_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.showLegacyActionBar ? $r('app.color.text_1') : $r('sys.color.font_secondary')
  }

  // CustomBuilderAdapter: live-tv-grid-item
  @Builder
  private gridItemBuilder(item: Object, _index: number) {
    VideoCard({ item: item as VideoItem })
      .onClick(() => {
        HMRouterMgr.to(RouterConsts.VideoDetailPage)
          .withParam({
            id: (item as VideoItem).id,
            mediaName: (item as VideoItem).mediaName,
            isTvShows: (item as VideoItem).type === 'Series'
          })
          .push()
      })
  }

  // CustomBuilderAdapter: live-tv-grid-empty
  @Builder
  private gridEmptyBuilder() {
    EmptyView()
  }

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({ title: this.ui.title })
      }
      Column() {
        Row() {
          Text(sortOptions[this.ui.selectedSortIndex].label)
            .fontSize(14)
            .fontColor(this.primaryTextColor())
            .height(46)
            .onClick(() => this.vm.sortDialog.show())
          Image(this.ui.isDesc ? $r('app.media.jiangxu') : $r('app.media.shengxu'))
            .width(16)
            .height(16)
            .fillColor(this.primaryTextColor())
            .margin({ left: 6 })
            .onClick(() => {
              if (this.vm.controller.getRefreshLayoutStatus() === RefreshLayoutStatus.Finish) {
                this.ui.isDesc = !this.ui.isDesc
                this.vm.refresh()
              }
            })
          Text(this.ui.totalCount)
            .fontSize(12)
            .fontColor(this.secondaryTextColor())
            .backgroundColor(this.showLegacyActionBar ? $r('app.color.bg_1') : Color.Transparent)
            .padding({ left: 6, right: 6, top: 4, bottom: 4 })
            .margin({ left: 12 })
            .borderRadius(4)
          Blank().layoutWeight(1)
        }
        .width('100%')
        .height(46)

        GridView({
          lazyDataSource: this.vm.dataSource,
          onLazyDataSource: (dataSource) => {
            this.vm.dataSource = dataSource
          },
          itemLayout: (item: Object, index: number) => this.gridItemBuilder(item, index),
          emptyLayout: () => this.gridEmptyBuilder(),
          showEmptyLayout: this.ui.totalCount === '0',
          controller: this.vm.controller,
          onRefresh: () => this.vm.refresh(),
          onLoadMore: () => this.vm.loadMore(),
          gridAttribute: (attr) => {
            attr.scrollBar = BarState.Off
            switch (this.vm.appUIState.currentBreakpoint) {
              case BreakpointTypeEnum.XS:
                attr.columnsTemplate = '1fr '.repeat(2)
                break
              case BreakpointTypeEnum.SM:
                attr.columnsTemplate = '1fr '.repeat(3)
                break
              case BreakpointTypeEnum.MD:
                attr.columnsTemplate = '1fr '.repeat(4)
                break
              case BreakpointTypeEnum.LG:
                attr.columnsTemplate = '1fr '.repeat(6)
                break
              case BreakpointTypeEnum.XL:
                attr.columnsTemplate = '1fr '.repeat(8)
                break
              case BreakpointTypeEnum.XXL:
                attr.columnsTemplate = '1fr '.repeat(12)
                break
              default:
                attr.columnsTemplate = '1fr '.repeat(3)
                break
            }
            attr.columnsGap = 8
          },
          lazyCachedCount: 10
        })
          .width('100%')
          .layoutWeight(1)
        if (this.ui.pageState === PageState.Loading) {
          SkeletonGridView()
        }
        if (this.ui.pageState === PageState.Error) {
          FailedView().onClick(() => this.vm.refresh())
        }
      }
      .width('100%')
      .layoutWeight(1)
      .backgroundColor(this.showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
      .borderRadius({
        topLeft: this.showLegacyActionBar ? 20 : 0,
        topRight: this.showLegacyActionBar ? 20 : 0
      })
      .padding({ left: 16, right: 16, bottom: this.vm.appUIState.safeBottom })
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
  }
}

@ComponentV2
export struct VideoCard {
  @Param @Require item: VideoItem
  @Param textAlign: TextAlign = TextAlign.Start
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  build() {
    Column() {
      RelativeContainer() {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: this.item.image,
            objectFit: ImageFit.Cover,
            border: {
              radius: 6
            },
            placeholderSrc: this.item.type == BaseItemKind.Season ? $r('app.media.placeholder_folder') :
              $r('app.media.placeholder_video'),
            placeholderObjectFit: ImageFit.CENTER
          }
        })
          .aspectRatio(2)
          .width('100%')
          .borderColor(AppThemeSurfaceResolver.mediaOutline(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
          .borderWidth(AppThemeSurfaceResolver.outlineWidth(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable, 0.5))
          .borderRadius(6)
          .backgroundColor(AppThemeSurfaceResolver.useNativeSurface(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
            AppThemeSurfaceResolver.mediaPlaceholderBackground(
              this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) : '#CBD2D3')
        // .backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
        if (this.item.score) {
          Text(this.item.score.toFixed(1) + '')
            .fontSize(11)
            .fontColor(Color.Yellow)
            .fontWeight(FontWeight.Medium)
            .padding({
              left: 6,
              right: 6,
              top: 4,
              bottom: 4
            })
            .alignRules({
              top: {
                anchor: '__container__',
                align: VerticalAlign.Top,
              },
              left: {
                anchor: '__container__',
                align: HorizontalAlign.Start
              }
            })
            .margin(4)
            .backgroundColor(0x60000000)
            // .backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
            .borderRadius(4)
        }
        if (this.item.tags && this.item.tags.length > 0) {
          Row({ space: 4 }) {
            ForEach(this.item.tags, (tag: string) => {
              if (tag === '4K') {
                Image($r('app.media.4K')).height(20).width(28).opacity(0.7).objectFit(ImageFit.Fill)
              } else {
                Text(tag)
                  .fontSize(9)
                  .fontColor(Color.White)
                  .fontWeight(FontWeight.Medium)
                  .padding({
                    top: 1,
                    bottom: 1,
                    left: 2,
                    right: 2
                  })
                  .borderWidth(1)
                  .borderColor(Color.White)
                  .borderRadius(3)
                  .margin(3)
              }
            })
          }
          .alignRules({
            bottom: {
              anchor: '__container__',
              align: VerticalAlign.Bottom,
            },
            right: {
              anchor: '__container__',
              align: HorizontalAlign.End
            }
          })
          .margin(4)
        }
      }.width('100%').aspectRatio(2)

      Text(this.item.name)
        .fontColor($r('app.color.text_primary'))
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .width('100%')
        .maxLines(1)
        .ellipsisMode(EllipsisMode.END)
        .textOverflow({
          overflow: TextOverflow.Ellipsis
        })
        .margin({
          top: 8
        })
        .textAlign(this.textAlign)
      Text(this.item.subtitle)
        .fontColor(Color.Gray)
        .fontSize(12)
        .width('100%')
        .maxLines(1)
        .ellipsisMode(EllipsisMode.END)
        .textOverflow({
          overflow: TextOverflow.Ellipsis
        })
        .textAlign(this.textAlign)
        .margin({
          top: 4
        })
    }.alignItems(HorizontalAlign.Start).margin({
      bottom: 8
    })
  }
}
