import { display, window } from "@kit.ArkUI";
import { common, ConfigurationConstant } from "@kit.AbilityKit";
import { CustomColorMode } from "./CustomColorMode";
import ServerEntity from "../data/ServerEntity";
import { hdsEffect } from "@kit.UIDesignKit";
import { ThemeStyle } from "./ThemeStyle";

@ObservedV2
export class AppUIState {
  @Trace orientation: display.Orientation = display.Orientation.PORTRAIT
  @Trace isFullScreen: boolean = false
  @Trace colorMode: ConfigurationConstant.ColorMode = ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET
  @Trace customColorMode: CustomColorMode = CustomColorMode.Auto
  @Trace themeStateReady: boolean = false
  @Trace nativeThemeAvailable: boolean = false
  @Trace themePromptPending: boolean = false
  @Trace themeStyle: ThemeStyle = ThemeStyle.Feiniu
  @Trace safeTop: number = 0
  @Trace safeBottom: number = 0
  @Trace isLogin: boolean = false
  @Trace isConnecting: boolean = false
  @Trace deviceWidth: number = 0
  @Trace deviceHeight: number = 0
  @Trace currentBreakpoint: string = "MD"
  @Trace isBigScreen: boolean = false // 大屏幕设备
  //点光源开关,关闭设置成NONE
  @Trace illuminatedType = hdsEffect.PointLightIlluminatedType.DEFAULT_FEATHERING_BORDER
  context?: common.UIAbilityContext
  windowClass?: window.Window
  navInstance?: NavigationAttribute
  server?: ServerEntity
}
