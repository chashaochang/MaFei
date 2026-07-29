import { GridView, RefreshLayoutStatus } from "@abner/refresh_v2";
import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { ImageKnifeComponent } from "@ohos/imageknifepro";
import { BaseItemKind } from "sdk/src/main/ets/generated-client/models";
import { PageState } from "../../common/base/BaseUIState";
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { AppRouteDestination } from '../../component/AppRouteDestination';
import { EmptyView } from "../../component/EmptyView";
import { FailedView } from "../../component/FailedView";
import { SkeletonGridView } from "../../component/skeleton/SkeletonGridView";
import { sortOptions } from "../../entity/SortOption";
import { MediaItem, VideoItem } from "../home/hometab/HomeUIState";
import { VideoListUIState } from "./VideoListUIState";
import { VideoListViewModel } from "./VideoListViewModel";
import { BreakpointTypeEnum } from "../../utils/BreakpointSystem";
import { HORIZONTAL_CARD_SPACE } from "../../common/OneMoreConst";
import { AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../../entity/AppUIState";

@HMRouter({
  pageUrl: RouterConsts.VideoListPage,
  useNavDst: true
})
@ComponentV2
export struct VideoListPage {
  //首页来的要隐藏顶部栏
  @Param fromHome: boolean = false
  @Param mediaItem: MediaItem = {} as MediaItem
  @Local ui: VideoListUIState = new VideoListUIState(this.getUIContext())
  @Local vm: VideoListViewModel = new VideoListViewModel(this.ui)

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private pageBackground(): ResourceColor {
    if (this.vm.appUIState.isBigScreen) {
      return Color.Transparent
    }
    return AppThemeSurfaceResolver.routeBackground(this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private primaryTextColor(nativeSurface: boolean): ResourceColor {
    return nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  aboutToAppear(): void {
    if (this.fromHome && this.mediaItem) {
      this.vm.init(this.mediaItem)
    } else {
      let param = HMRouterMgr.getCurrentParam()
      if (param) {
        this.vm.init(param as MediaItem)
      }
    }
  }

  @Builder
  private nativeCountLabel() {
    Text(this.ui.totalCount)
      .fontSize(12)
      .fontColor($r('sys.color.font_secondary'))
      .padding({
        left: 6,
        right: 6,
        top: 4,
        bottom: 4
      })
      .margin({
        left: 12
      })
      .borderRadius(4)
  }

  @Builder
  private feiniuCountLabel() {
    Text(this.ui.totalCount)
      .fontSize(12)
      .fontColor($r('app.color.text_1'))
      .backgroundColor($r('app.color.bg_1'))
      .padding({
        left: 6,
        right: 6,
        top: 4,
        bottom: 4
      })
      .margin({
        left: 12
      })
      .borderRadius(4)
  }

  @Builder
  private listContent(nativeSurface: boolean) {
    Column() {
      Row() {
        Text(sortOptions[this.ui.selectedSortIndex].label)
          .fontSize(14)
          .fontColor(this.primaryTextColor(nativeSurface))
          .height(46)
          .onClick(() => {
            this.vm.sortDialog.show()
          })
        Image(this.ui.isDesc ? $r('app.media.jiangxu') : $r('app.media.shengxu'))
          .width(16)
          .height(16)
          .fillColor(this.primaryTextColor(nativeSurface))
          .margin({ left: 6 })
          .onClick(() => {
            if (this.vm.controller.getRefreshLayoutStatus() === RefreshLayoutStatus.Finish) {
              this.ui.isDesc = !this.ui.isDesc
              this.vm.refresh()
            }
          })
        if (nativeSurface) {
          this.nativeCountLabel()
        } else {
          this.feiniuCountLabel()
        }
        Blank().layoutWeight(1)
      }
      .width('100%')
      .height(46)

      GridView({
        lazyDataSource: this.vm.dataSource,
        onLazyDataSource: (dataSource) => {
          this.vm.dataSource = dataSource
        },
        itemLayout: (item, index) => this.itemLayout(item, index),
        emptyLayout: () => this.emptyLayout(),
        showEmptyLayout: this.ui.totalCount === '0',
        controller: this.vm.controller,
        onRefresh: () => {
          this.vm.refresh()
        },
        onLoadMore: () => {
          this.vm.loadMore()
        },
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
          attr.columnsGap = HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint)
        },
        lazyCachedCount: 10
      }).width('100%')
        .layoutWeight(1)
      if (this.ui.pageState == PageState.Loading) {
        SkeletonGridView()
      }
      if (this.ui.pageState == PageState.Error) {
        FailedView()
          .onClick(() => {
            this.vm.refresh()
          })
      }
    }
    .width('100%')
    .layoutWeight(1)
    .backgroundColor(nativeSurface ? Color.Transparent : this.pageBackground())
    .borderRadius({
      topLeft: 20,
      topRight: 20
    })
    .padding({
      left: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2,
      right: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2,
      bottom: this.vm.appUIState.safeBottom
    })
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({
          title: this.ui.title
        })
      }
      this.listContent(!showLegacyActionBar)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
  }

  @Builder
  private embeddedContent() {
    Column() {
      this.listContent(this.useNativeSurface())
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.pageBackground())
  }

  build() {
    if (this.fromHome) {
      this.embeddedContent()
    } else {
      AppRouteDestination({
        title: this.ui.title,
        contentBuilder: () => {
          this.pageContent(false)
        },
        legacyContentBuilder: () => {
          this.pageContent(true)
        }
      })
    }
  }

  @Builder
  itemLayout(item: Object, _index: number): void {
    // 自定义item
    VideoCard({ item: item as VideoItem })
      .onClick(() => {
        HMRouterMgr.to(RouterConsts.VideoDetailPage)
          .withParam({
            id: (item as VideoItem).id,
            mediaName: (item as VideoItem).mediaName,
            isTvShows: (item as VideoItem).type === 'Series',
          })
          .push()
      })
  }

  @Builder
  emptyLayout() {
    EmptyView()
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
          .aspectRatio(2 / 3)
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
      }.width('100%').aspectRatio(2 / 3)

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
