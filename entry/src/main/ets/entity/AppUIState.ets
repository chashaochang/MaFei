import { display } from "@kit.ArkUI";

@ObservedV2
export class AppUIState {
  @Trace orientation: display.Orientation = display.Orientation.PORTRAIT
  @Trace isFullScreen: boolean = false
  @Trace safeTop: number = 0
  @Trace safeBottom: number = 0
  @Trace isLogin: boolean = false
}