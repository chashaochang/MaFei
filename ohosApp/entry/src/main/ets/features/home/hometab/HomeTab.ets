import { HMRouterMgr } from "@hadss/hmrouter"
import { ImageKnifeComponentV2 } from "@ohos/imageknife"
import { HomeUIState, MediaItem, VideoItem } from "./HomeUIState"
import { HomeViewModel } from "./HomeViewModel"
import { AppStorageV2 } from "@kit.ArkUI"
import { AppUIState } from "../../../entity/AppUIState"
import { UIConstants } from "../../../common/UIConstants"
import { BaseItemKind } from "sdk/src/main/ets/generated-client/models"
import { playItems } from "../../../common/CommonFunc"
import { HomeRefreshEvent } from "../../../events/Events"
import { CustomRefreshComponent } from "../../../common/CustomRefreshComponent"
import { ToastUtil } from "../../../utils/ToastUtil"
import { RouterConsts } from "../../../common/RouterConsts"

@ComponentV2
export struct HomeTab {
  @Local ui: HomeUIState = new HomeUIState()
  @Local vm: HomeViewModel = new HomeViewModel(this.ui)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  aboutToAppear(): void {
    //监听刷新事件
    this.getUIContext().getHostContext()?.eventHub.on(HomeRefreshEvent, () => {
      setTimeout(() => {
        this.vm.init()
      }, 1000) //延时1秒，避免播放器退出时数据还未更新完毕
    })
    this.vm.init()
  }

  build() {
    Stack() {
      Refresh({ refreshing: $$this.ui.isRefreshing, builder: this.customRefreshComponent() }) {
        List() {
          ListItem().height(this.appUIState.safeTop + UIConstants.ACTION_BAR_HEIGHT)
          if (this.ui.mediaList.length > 0) {
            ListItem() {
              this.buildMediaList()
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

          ForEach(this.ui.latestMediaList, (item: MediaItem) => {
            ListItem() {
              this.buildLastAddedList(item)
            }
          })
          ListItem().height(UIConstants.BOTTOM_BAR_HEIGHT + this.appUIState.safeBottom + 20)
        }.width('100%').height('100%').listDirection(Axis.Vertical).scrollBar(BarState.Off)
      }
      .height('100%')
      .onRefreshing(() => {
        this.vm.init()
      })

      //Header
      Row({ space: 12 }) {
        Row() {
          Image($r('app.media.app_icon')).height(40)
          Text($r('app.string.app_name')).fontColor(Color.White).fontSize(18).fontWeight(FontWeight.Bold)
        }

        Blank()
        Image($r('app.media.ic_search'))
          .width(40)
          .height(40)
          .borderRadius(20)
          .padding({
            top: 8, bottom: 8
          })
          .objectFit(ImageFit.Contain)
          .backgroundColor($r('app.color.component_bg'))
          .visibility(Visibility.None)
      }.width('100%').height(UIConstants.ACTION_BAR_HEIGHT + this.appUIState.safeTop)
      .backgroundBlurStyle(BlurStyle.COMPONENT_THICK)
      .padding({
        left: 8, right: 16, top: this.appUIState.safeTop
      })
    }
    .backgroundColor($r('app.color.bg_main'))
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Top)
  }

  @Builder
  buildMediaList() {
    Column() {
      List({ space: 8 }) {
        ListItem().width(8)
        ForEach(this.ui.mediaList, (item: MediaItem) => {
          ListItem() {
            Stack() {
              ImageKnifeComponentV2({
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
            .backgroundColor('#293039')
            .borderColor('#535b60')
            .borderWidth(0.5)
            .borderRadius(8)
          }.onClick(() => {
            if (item.type == undefined) {
              ToastUtil.showToast(this.getUIContext(), '暂不支持')
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
  buildResumeList() {
    Column() {
      Text('继续观看').fontSize(14).fontWeight(FontWeight.Medium).fontColor(Color.White)
      List({ space: 8 }) {
        ForEach(this.ui.resumeList, (item: VideoItem) => {
          ListItem() {
            Column({ space: 8 }) {
              Stack() {
                ImageKnifeComponentV2({
                  imageKnifeOption: {
                    loadSrc: item.image,
                    objectFit: ImageFit.Cover,
                    border: {
                      radius: 6
                    },
                    placeholderSrc: $r('app.media.placeholder_video'),
                    placeholderObjectFit: ImageFit.CENTER
                  }
                })
                  .width(160)
                  .height(90)
                  .borderColor('#535b60')
                  .borderWidth(0.5)
                  .backgroundColor('#0affffff')
                Progress({
                  value: item.percent || 0,
                  total: 100,
                  type: ProgressType.Linear,
                })
                  .height(4)
                  .layoutWeight(1)
                  .color('#3B82F6')
              }.alignContent(Alignment.Bottom).borderRadius(6).clip(true)

              Text(item.name)
                .fontColor(Color.White)
                .fontSize(14)
                .fontWeight(FontWeight.Medium)
                .width('100%')
                .maxLines(1)
                .ellipsisMode(EllipsisMode.END)
                .textOverflow({
                  overflow: TextOverflow.Ellipsis
                })
              Text(item.subtitle).fontColor(Color.Gray).fontSize(12)
            }.width(160).alignItems(HorizontalAlign.Start)
          }.onClick(() => {
            playItems([item.id], this.getUIContext().getHostContext()!)
          })
        })
      }
      .width('100%')
      .height(134)
      .margin({ top: 12 })
      .listDirection(Axis.Horizontal)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .padding({
      left: 16, right: 16
    })
    .margin({
      top: 20
    })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  buildNextUpList() {
    Column() {
      Text('接下来').fontSize(14).fontWeight(FontWeight.Medium).fontColor(Color.White)
      List({ space: 8 }) {
        ForEach(this.ui.nextUpList, (item: VideoItem) => {
          ListItem() {
            Column({ space: 8 }) {
              ImageKnifeComponentV2({
                imageKnifeOption: {
                  loadSrc: item.image,
                  objectFit: ImageFit.Cover,
                  border: {
                    radius: 6
                  },
                  placeholderSrc: $r('app.media.placeholder_video'),
                  placeholderObjectFit: ImageFit.CENTER
                }
              })
                .width(160)
                .height(90)
                .borderColor('#535b60')
                .borderWidth(0.5)
                .borderRadius(6)
                .backgroundColor('#0affffff')
              Text(item.name)
                .fontColor(Color.White)
                .fontSize(14)
                .fontWeight(FontWeight.Medium)
                .width('100%')
                .maxLines(1)
                .ellipsisMode(EllipsisMode.END)
                .textOverflow({
                  overflow: TextOverflow.Ellipsis
                })
              Text(item.subtitle)
                .fontColor(Color.Gray)
                .fontSize(12)
                .width('100%')
                .maxLines(1)
                .ellipsisMode(EllipsisMode.END)
                .textOverflow({
                  overflow: TextOverflow.Ellipsis
                })
            }.width(160).alignItems(HorizontalAlign.Start)
          }.onClick(() => {
            playItems([item.id], this.getUIContext().getHostContext()!)
          })
        })
      }
      .width('100%')
      .height(134)
      .margin({ top: 12 })
      .listDirection(Axis.Horizontal)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .padding({
      left: 16, right: 16
    })
    .margin({
      top: 20
    })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  buildLastAddedList(item: MediaItem) {
    Column() {
      Text('最近添加的 ' + item.name).fontSize(14).fontWeight(FontWeight.Medium).fontColor(Color.White)
      List({ space: 8 }) {
        ForEach(this.ui.latestMap.get(item.id), (item: VideoItem) => {
          ListItem() {
            Column({ space: 8 }) {
              ImageKnifeComponentV2({
                imageKnifeOption: {
                  loadSrc: item.image,
                  objectFit: ImageFit.Cover,
                  border: {
                    radius: 6
                  },
                  placeholderSrc: $r('app.media.placeholder_video'),
                  placeholderObjectFit: ImageFit.CENTER
                }
              })
                .width(100)
                .height(150)
                .borderColor('#535b60')
                .borderWidth(0.5)
                .borderRadius(6)
                .backgroundColor('#0affffff')
              Text(item.name)
                .fontColor(Color.White)
                .fontSize(14)
                .fontWeight(FontWeight.Medium)
                .width('100%')
                .maxLines(1)
                .ellipsisMode(EllipsisMode.END)
                .textOverflow({
                  overflow: TextOverflow.Ellipsis
                })
              Text(item.subtitle)
                .fontColor(Color.Gray)
                .fontSize(12)
                .width('100%')
                .maxLines(1)
                .ellipsisMode(EllipsisMode.END)
                .textOverflow({
                  overflow: TextOverflow.Ellipsis
                })
            }.width(100).alignItems(HorizontalAlign.Start)
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
      }
      .width('100%')
      .height(194)
      .margin({ top: 12 })
      .listDirection(Axis.Horizontal)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .padding({
      left: 16, right: 16
    })
    .margin({
      top: 24
    })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  customRefreshComponent() {
    CustomRefreshComponent()
      .offset({
        y: this.appUIState.safeTop + UIConstants.ACTION_BAR_HEIGHT
      })
  }
}