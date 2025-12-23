import { GridView, RefreshLayoutStatus } from "@abner/refresh_v2";
import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { ImageKnifeComponent } from "@ohos/imageknifepro";
import { BaseItemKind } from "sdk/src/main/ets/generated-client/models";
import { PageState } from "../../common/base/BaseUIState";
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { EmptyView } from "../../component/EmptyView";
import { FailedView } from "../../component/FailedView";
import { SkeletonGridView } from "../../component/skeleton/SkeletonGridView";
import { sortOptions } from "../../entity/SortOption";
import { MediaItem, VideoItem } from "../home/hometab/HomeUIState";
import { VideoListUIState } from "./VideoListUIState";
import { VideoListViewModel } from "./VideoListViewModel";

@HMRouter({
  pageUrl: RouterConsts.VideoListPage
})
@ComponentV2
export struct VideoListPage {
  @Local ui: VideoListUIState = new VideoListUIState(this.getUIContext())
  @Local vm: VideoListViewModel = new VideoListViewModel(this.ui)

  aboutToAppear(): void {
    let param = HMRouterMgr.getCurrentParam()
    if (param) {
      this.vm.init(param as MediaItem)
    }
  }

  build() {
    Column() {
      ActionBar({
        title: this.ui.title
      })
      Column() {

        Row() {
          Text(sortOptions[this.ui.selectedSortIndex].label).fontSize(14).fontColor($r('app.color.text_primary'))
            .height(46)
            .onClick(()=>{
              this.vm.sortDialog.show()
            })
          Image(this.ui.isDesc ? $r('app.media.jiangxu') : $r('app.media.shengxu'))
            .width(16)
            .height(16)
            .fillColor($r('app.color.text_primary'))
            .margin({ left: 6 })
            .onClick(() => {
              if (this.vm.controller.getRefreshLayoutStatus() === RefreshLayoutStatus.Finish) {
                this.ui.isDesc = !this.ui.isDesc
                this.vm.refresh()
              }
            })
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
          Blank().layoutWeight(1)
        }
        .width('100%')
        .height(46)

        GridView({
          lazyDataSource: this.vm.dataSource,
          onLazyDataSource: (dataSource) => {
            this.vm.dataSource = dataSource
          },
          itemLayout: (item, index) => this.itemLayout(item, index), //条目布局
          emptyLayout: () => this.emptyLayout(),
          showEmptyLayout: this.ui.totalCount === '0',
          controller: this.vm.controller, //控制器，负责关闭下拉和上拉
          onRefresh: () => {
            //下拉刷新
            this.vm.refresh()
          },
          onLoadMore: () => {
            //上拉加载
            this.vm.loadMore()
          },
          gridAttribute: (attr) => {
            attr.scrollBar = BarState.Off
            attr.columnsTemplate = '1fr 1fr 1fr'
            attr.columnsGap = 8
          },
          lazyCachedCount: 10 //缓存
        }).width('100%')
          .layoutWeight(1)
        if (this.ui.pageState == PageState.Loading) {
          SkeletonGridView()
        }
        if (this.ui.pageState == PageState.Error) {
          FailedView()
            .onClick(()=>{
              this.vm.refresh()
            })
        }
      }
      .width('100%')
      .layoutWeight(1)
      .backgroundColor($r('app.color.bg_main'))
      .borderRadius({
        topLeft: 20,
        topRight: 20
      })
      .padding({
        left: 16, right: 16, bottom: this.vm.appUIState.safeBottom
      })
    }.width('100%')
    .height('100%')
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
          .borderColor($r('app.color.border_1'))
          .borderWidth(0.5)
          .borderRadius(6)
          .backgroundColor('#CBD2D3')
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