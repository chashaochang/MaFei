import { AppUIState } from '../../entity/AppUIState'
import { BreakpointTypeEnum } from '../../utils/BreakpointPolicy'

const TABLET_HERO_MIN_HEIGHT: number = 420
const TABLET_HERO_MD_HEIGHT: number = 460
const TABLET_HERO_LARGE_HEIGHT: number = 500
const TABLET_HERO_LANDSCAPE_HEIGHT: number = 480
const TABLET_HERO_MAX_HEIGHT: number = 540

export class HomeContentLayoutPolicy {
  static breakpoint(appUIState: AppUIState, useHomeContentWidth: boolean): string {
    if (!useHomeContentWidth || appUIState.currentBreakpoint === BreakpointTypeEnum.XS ||
      appUIState.currentBreakpoint === BreakpointTypeEnum.SM || appUIState.homeContentWidth <= 0) {
      return appUIState.currentBreakpoint
    }
    return appUIState.homeContentBreakpoint
  }

  static width(appUIState: AppUIState, useHomeContentWidth: boolean): number {
    if (!useHomeContentWidth || appUIState.homeContentWidth <= 0) {
      return appUIState.deviceWidth
    }
    return appUIState.homeContentWidth
  }

  static tabletHeroHeight(appUIState: AppUIState, useHomeContentWidth: boolean): number {
    let targetHeight = HomeContentLayoutPolicy.breakpoint(appUIState, useHomeContentWidth) === BreakpointTypeEnum.MD ?
      TABLET_HERO_MD_HEIGHT : TABLET_HERO_LARGE_HEIGHT
    if (appUIState.deviceWidth > appUIState.deviceHeight) {
      targetHeight = Math.min(targetHeight, TABLET_HERO_LANDSCAPE_HEIGHT)
    }
    const windowHeight = Math.max(0, appUIState.deviceHeight)
    if (windowHeight === 0) {
      return targetHeight
    }
    return Math.min(TABLET_HERO_MAX_HEIGHT,
      Math.max(TABLET_HERO_MIN_HEIGHT, Math.min(targetHeight, windowHeight * 0.74)))
  }
}
