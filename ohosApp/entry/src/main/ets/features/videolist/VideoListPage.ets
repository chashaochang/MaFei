import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { ImageKnifeComponentV2 } from "@ohos/imageknife";
import { BaseItemKind } from "sdk/src/main/ets/generated-client/models";
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
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
      let mediaItem = param as MediaItem
      this.ui.title = mediaItem.name
      this.vm.getVideoList(mediaItem.id, mediaItem.type)
    }
  }

  build() {
    Column() {
      ActionBar({
        title: this.ui.title
      }).backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
      Grid(this.vm.scroller){
        LazyForEach(this.ui.dataSource, (item: VideoItem) => {
          GridItem(){
            // 自定义item
            VideoCard({ item: item })
              .onClick(() => {
                HMRouterMgr.to(RouterConsts.VideoDetailPage)
                  .withParam({
                    id: item.id,
                    mediaName: item.mediaName,
                    isTvShows: item.type === 'Series',
                  })
                  .push()
              })
          }
        })
      }
      .cachedCount(12)
      .width('100%') // 同原生方式链式调用设置属性
      .layoutWeight(1)
      .scrollBar(BarState.Off)
      .enableScrollInteraction(true)
      .edgeEffect(EdgeEffect.Spring)
      .columnsTemplate('1fr 1fr 1fr')
      .columnsGap(8)
      .padding({
        left: 16, right: 16, bottom: this.vm.appUIState.safeBottom
      })
    }.backgroundColor($r('app.color.bg_main'))
  }
}

@ComponentV2
export struct VideoCard {
  @Param @Require item: VideoItem
  @Param textAlign: TextAlign = TextAlign.Start

  build() {
    Column() {
      RelativeContainer() {
        ImageKnifeComponentV2({
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
          .borderColor('#535b60')
          .borderWidth(0.5)
          .borderRadius(6)
          .backgroundColor('#CBD2D3')
          .backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
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
            .backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
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
        .fontColor(Color.White)
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