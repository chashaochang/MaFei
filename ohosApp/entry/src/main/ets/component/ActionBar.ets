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
        SymbolGlyph($r('sys.symbol.chevron_left'))
          .fontSize(20)
          .fontColor([$r('app.color.text_primary')])
          .fontWeight(FontWeight.Bold)
          .align(Alignment.Center)
          .width(48)
          .height(48)
          .padding(16)
          .onClick(() => {
            this.onBackClick()
          })
          .visibility(this.showBackButton ? Visibility.Visible : Visibility.Hidden)
        Blank()
      }.width('100%').height('100%')

      Text(this.title)
        .fontSize(18)
        .fontColor($r('app.color.text_primary'))
        .fontWeight(FontWeight.Medium)
        .maxLines(1)
        .textOverflow({
          overflow: TextOverflow.Ellipsis
        })
        .constraintSize({ maxWidth: '70%' })
    }
    .width('100%')
    .height(UIConstants.ACTION_BAR_HEIGHT + this.appUIState.safeTop)
    .padding({ top: this.appUIState.safeTop })
  }
}