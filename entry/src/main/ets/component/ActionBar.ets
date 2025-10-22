import { HMRouterMgr } from "@hadss/hmrouter"
import { UIConstants } from "../common/UIConstants"
import { AppUIState } from "../entity/AppUIState";
import { AppStorageV2 } from "@kit.ArkUI";

@ComponentV2
export struct ActionBar {
  @Param title: string = ""
  @Param showBackButton: boolean = true
  @Param onBackClick: () => void = () => {
    HMRouterMgr.pop()
  }
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  build() {
    Stack() {
      Row() {
        Image($r('app.media.ic_back')).width(48).height(48).padding(16).onClick(() => {
          this.onBackClick()
        }).visibility(this.showBackButton ? Visibility.Visible : Visibility.Hidden)
        Blank()
      }.width('100%').height('100%')

      Text(this.title).fontSize(18).fontColor(Color.White).fontWeight(FontWeight.Medium)
    }
    .width('100%')
    .height(UIConstants.ACTION_BAR_HEIGHT + this.appUIState.safeTop)
    .padding({ top: this.appUIState.safeTop })
  }
}