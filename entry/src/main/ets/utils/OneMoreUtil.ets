import { deviceInfo } from "@kit.BasicServicesKit";
import { display, window } from "@kit.ArkUI";
import { NavModifier } from "../pages/Index";
import { AppUIState } from "../entity/AppUIState";
import { GridAttr } from "@abner/refresh_v2";
import { BreakpointTypeEnum } from "./BreakpointSystem";

export class OneMoreUtil {

  static setResponsiveGridColumnsAttr(appUIState: AppUIState, attr: GridAttr) {
    attr.columnsGap = 8
    switch (appUIState.currentBreakpoint) {
      case BreakpointTypeEnum.XS:
        attr.columnsTemplate = '1fr '.repeat(2)
        break
      case BreakpointTypeEnum.SM:
        attr.columnsTemplate = '1fr '.repeat(3)
        break
      case BreakpointTypeEnum.MD:
        attr.columnsTemplate = '1fr '.repeat(5)
        attr.columnsGap = 12
        break
      case BreakpointTypeEnum.LG:
        attr.columnsTemplate = '1fr '.repeat(6)
        attr.columnsGap = 16
        break
      case BreakpointTypeEnum.XL:
        attr.columnsTemplate = '1fr '.repeat(8)
        attr.columnsGap = 20
        break
      case BreakpointTypeEnum.XXL:
        attr.columnsTemplate = '1fr '.repeat(12)
        break
      default:
        attr.columnsTemplate = '1fr '.repeat(3)
        break
    }

  }
  //是否大屏设备(需要分栏的) 暂时没适配折叠屏
  static isBigScreen() {
    return deviceInfo.deviceType == 'tablet' || deviceInfo.deviceType == 'car' || deviceInfo.deviceType == '2in1'
  }

  //是正常的小屏手机
  static isNormalPhone() {
    return deviceInfo.deviceType == 'phone' && !OneMoreUtil.isFoldable()
  }

  //是不是折叠屏
  static isFoldable(): boolean {
    if (canIUse('SystemCapability.Window.SessionManager')) {
      if (deviceInfo.deviceType == 'phone' && display.isFoldable()) {
        return true
      }
    }
    return false
  }

  static isPc() {
    return deviceInfo.deviceType == '2in1'
  }

  static shouldRequestPlayerManualRotation(windowClass?: window.Window): boolean {
    // Pad/2in1 播放器始终跟随系统方向，不能由全屏按钮主动锁定横竖屏。
    if (!OneMoreUtil.isNormalPhone()) {
      return false
    }
    if (!windowClass || !canIUse('SystemCapability.Window.SessionManager')) {
      return false
    }
    try {
      const windowStatus = windowClass.getWindowStatus()
      return windowStatus !== window.WindowStatusType.FLOATING &&
        windowStatus !== window.WindowStatusType.SPLIT_SCREEN
    } catch (_error) {
      return true
    }
  }

  static getNavigationMode(_currentDisplay: display.Orientation): NavigationMode {
    return NavigationMode.Stack
  }

  static updateNavModifier(appUIState: AppUIState, instance?: NavigationAttribute) {
    let modifier = AppStorage.get<NavModifier>('modifier')
    let navInstance = instance || modifier?.instance
    navInstance?.mode(NavigationMode.Stack)
    if (appUIState.isFullScreen) {
      navInstance?.navBarWidth(0)
    } else {
      navInstance?.navBarWidth('100%')
    }

  }

  static maximize(): void {
    window.getLastWindow(getContext()).then((mainWindowClass)=>{
      if(canIUse("SystemCapability.Window.SessionManager")){
        if (mainWindowClass.getWindowStatus() === window.WindowStatusType.FLOATING) {
          mainWindowClass.maximize()
        }
      }
    })
  }

  static recover() {
    window.getLastWindow(getContext()).then((mainWindowClass)=>{
      if(canIUse("SystemCapability.Window.SessionManager")){
        if (mainWindowClass.getWindowStatus() === window.WindowStatusType.FULL_SCREEN) {
          mainWindowClass.recover()
        }
      }
    })
  }
}
