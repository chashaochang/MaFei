import { display, window } from "@kit.ArkUI";
import { common, ConfigurationConstant } from "@kit.AbilityKit";
import { CustomColorMode } from "./CustomColorMode";
import ServerEntity from "../data/ServerEntity";

@ObservedV2
export class AppUIState {
  @Trace orientation: display.Orientation = display.Orientation.PORTRAIT
  @Trace isFullScreen: boolean = false
  @Trace colorMode: ConfigurationConstant.ColorMode = ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET
  @Trace customColorMode: CustomColorMode = CustomColorMode.Auto
  @Trace safeTop: number = 0
  @Trace safeBottom: number = 0
  @Trace isLogin: boolean = false
  @Trace isConnecting: boolean = false
  @Trace deviceWidth: number = 0
  @Trace deviceHeight: number = 0
  @Trace currentBreakpoint: string = "MD"
  context?: common.UIAbilityContext
  windowClass?: window.Window
  navInstance?: NavigationAttribute
  server?: ServerEntity
}