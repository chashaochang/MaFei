import { HMRouterMgr } from '@hadss/hmrouter'
import { RouterConsts } from '../../common/RouterConsts'
import { AppUIState } from '../../entity/AppUIState'
import { Constants } from '../../utils/Constants'

export class HomeContentNavigationController {
  private static rootShowsHome(): boolean {
    const names = HMRouterMgr.getPathStack(Constants.NAVIGATION_ID)?.getAllPathName() ?? []
    return names.length > 0 && names[names.length - 1] === RouterConsts.IndexPage
  }

  static popHomeContentIfNeeded(): boolean {
    if (!HomeContentNavigationController.rootShowsHome()) {
      return false
    }
    const pathStack = HMRouterMgr.getPathStack(Constants.HOME_CONTENT_NAVIGATION_ID)
    if ((pathStack?.size() ?? 0) <= 0) {
      return false
    }
    HMRouterMgr.pop({ navigationId: Constants.HOME_CONTENT_NAVIGATION_ID })
    return true
  }

  static syncDefaultNavigation(appUIState: AppUIState): void {
    const rootShowsHome = HomeContentNavigationController.rootShowsHome()
    appUIState.homeContentNavigationVisible = appUIState.homeContentNavigationMounted &&
      appUIState.homeContentNavigationActive && rootShowsHome
    const navigationId = appUIState.homeContentNavigationMounted && rootShowsHome ?
      Constants.HOME_CONTENT_NAVIGATION_ID : Constants.NAVIGATION_ID
    HMRouterMgr.setDefaultNavigationId(navigationId)
  }

  static reset(appUIState: AppUIState): void {
    HMRouterMgr.removeAll(false, Constants.HOME_CONTENT_NAVIGATION_ID)
    appUIState.homeContentNavigationActive = false
    appUIState.homeContentNavigationVisible = false
    HomeContentNavigationController.syncDefaultNavigation(appUIState)
  }
}
