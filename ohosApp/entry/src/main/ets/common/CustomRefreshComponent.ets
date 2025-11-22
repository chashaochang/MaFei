import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../entity/AppUIState";
import { UIConstants } from "./UIConstants";

@ComponentV2
export struct CustomRefreshComponent {
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  @Param refreshStatus: RefreshStatus = RefreshStatus.Inactive
  @Param refreshOffset: number = 0
  @Param enableSafeTop: boolean = true

  build() {
    Stack() {
      Progress({ value: this.refreshOffset, total: 100, type: ProgressType.Ring })
        .width(24).color($r('app.color.text_primary'))
        .style({
          strokeWidth: 3,
          status: this.refreshStatus === RefreshStatus.Refresh ? ProgressStatus.LOADING : ProgressStatus.PROGRESSING
        })
    }
    .align(Alignment.Center)
    .clip(true)
    // 设置最小高度约束保证自定义组件高度随刷新区域高度变化时自定义组件高度不会低于minHeight。
    .constraintSize({ minHeight: 32 })
    .scale({
      x: Math.min(1, this.refreshOffset / 100),
      y: Math.min(1, this.refreshOffset / 100)
    })
    .opacity(Math.min(1, this.refreshOffset / 100))
    .width("100%")
    .offset({
      y: (this.enableSafeTop ? this.appUIState.safeTop : 0) + UIConstants.ACTION_BAR_HEIGHT
    })
  }
}