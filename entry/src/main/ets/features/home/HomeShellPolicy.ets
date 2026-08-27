import { ThemeStyle } from '../../entity/ThemeStyle'
import { BreakpointTypeEnum } from '../../utils/BreakpointPolicy'

export enum HomeShellKind {
  PhoneStandard = 'phone_standard',
  PhoneNativeHds = 'phone_native_hds',
  MediumDrawer = 'medium_drawer',
  LargeSidebar = 'large_sidebar'
}

export class HomeShellPolicy {
  static resolve(breakpoint: BreakpointTypeEnum, theme: ThemeStyle,
    nativeAvailable: boolean, isPortraitViewport: boolean = false): HomeShellKind {
    if (breakpoint === BreakpointTypeEnum.MD ||
      (breakpoint === BreakpointTypeEnum.LG && isPortraitViewport)) {
      return HomeShellKind.MediumDrawer
    }
    if (breakpoint === BreakpointTypeEnum.LG ||
      breakpoint === BreakpointTypeEnum.XL ||
      breakpoint === BreakpointTypeEnum.XXL) {
      return HomeShellKind.LargeSidebar
    }
    if (theme === ThemeStyle.Native && nativeAvailable) {
      return HomeShellKind.PhoneNativeHds
    }
    return HomeShellKind.PhoneStandard
  }
}
