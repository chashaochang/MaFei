import { HMRouterMgr } from "@hadss/hmrouter";
import { AppStorageV2 } from "@kit.ArkUI";
import { CustomRefreshComponent } from "../../../common/CustomRefreshComponent";
import { RouterConsts } from "../../../common/RouterConsts";
import { UIConstants } from "../../../common/UIConstants";
import { ActionBar } from "../../../component/ActionBar";
import { BaseGroupItem, ListGroup } from "../../../component/ListGroup";
import { AppUIState } from "../../../entity/AppUIState";
import { ToastUtil } from "../../../utils/ToastUtil";
import { MediaUIState } from "./MediaUIState";
import { MediaViewModel } from "./MediaViewModel";

@ComponentV2
export struct MediaTab {
  @Local ui: MediaUIState = new MediaUIState()
  @Local vm: MediaViewModel = new MediaViewModel(this.ui)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  aboutToAppear(): void {
    this.vm.init()
  }

  build() {
    Stack() {
      Refresh({ refreshing: $$this.ui.isRefreshing, builder: this.customRefreshComponent() }) {
        Scroll() {
          Column() {
            Blank().height(this.appUIState.safeTop + UIConstants.ACTION_BAR_HEIGHT)
            Text('媒体库').fontColor(Color.White).fontSize(14).fontWeight(FontWeight.Medium).margin({ top: 20 })
            ListGroup({
              items: this.ui.mediaList.map((e) => {
                return new BaseGroupItem(e.name, e.type == 'Movie' ? $r('app.media.movie') : $r('app.media.tv'), e.childCount + '')
              }),
              onItemClick:(index)=>{
                if (this.ui.mediaList[index].type == undefined) {
                  ToastUtil.showToast(this.getUIContext(), '暂不支持')
                  return;
                }
                HMRouterMgr.to(RouterConsts.VideoListPage)
                  .withParam(this.ui.mediaList[index])
                  .push()
              }
            }).margin({ top: 12 })
          }.alignItems(HorizontalAlign.Start)
          .padding({
            left: 16,
            right: 16,
            bottom: 20
          })
        }
        .width('100%')
        .height('100%')
        .align(Alignment.Top)
      }
      .onRefreshing(() => {
        this.vm.init()
      })

      ActionBar({
        title: '媒体库',
        showBackButton: false
      }).backgroundBlurStyle(BlurStyle.COMPONENT_THICK)
    }
    .backgroundColor($r('app.color.bg_main'))
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Top)
  }

  @Builder
  customRefreshComponent() {
    CustomRefreshComponent()
      .offset({
        y: this.appUIState.safeTop + UIConstants.ACTION_BAR_HEIGHT
      })
  }
}