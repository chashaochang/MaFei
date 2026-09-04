import { AppStorageV2, AttributeUpdater, uiObserver } from '@kit.ArkUI'
import { HMDefaultGlobalAnimator, HMNavigation, HMRouterMgr } from '@hadss/hmrouter'
import { AppUIState } from '../../entity/AppUIState'
import { Constants } from '../../utils/Constants'
import { HomeContentNavigationController } from './HomeContentNavigationController'

class HomeContentNavigationModifier extends AttributeUpdater<NavigationAttribute> {
  initializeModifier(instance: NavigationAttribute): void {
    instance.hideNavBar(true)
    instance.hideTitleBar(true)
    instance.hideToolBar(true)
    instance.mode(NavigationMode.Stack)
    instance.backgroundColor(Color.Transparent)
  }
}

@ComponentV2
export struct HomeContentNavigation {
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  private modifier: HomeContentNavigationModifier = new HomeContentNavigationModifier()
  private readonly observerOptions: uiObserver.NavDestinationSwitchObserverOptions = {
    navigationId: Constants.HOME_CONTENT_NAVIGATION_ID
  }
  private readonly onDestinationSwitch = (_info: uiObserver.NavDestinationSwitchInfo): void => {
    this.syncActiveState()
  }

  private syncActiveState(): void {
    const pathStack = HMRouterMgr.getPathStack(Constants.HOME_CONTENT_NAVIGATION_ID)
    this.appUIState.homeContentNavigationActive = (pathStack?.size() ?? 0) > 0
    HomeContentNavigationController.syncDefaultNavigation(this.appUIState)
  }

  aboutToAppear(): void {
    this.appUIState.homeContentNavigationMounted = true
    this.getUIContext().getUIObserver().on(
      'navDestinationSwitch',
      this.observerOptions,
      this.onDestinationSwitch
    )
    this.syncActiveState()
  }

  aboutToDisappear(): void {
    this.getUIContext().getUIObserver().off(
      'navDestinationSwitch',
      this.observerOptions,
      this.onDestinationSwitch
    )
    this.appUIState.homeContentNavigationMounted = false
    this.appUIState.homeContentNavigationActive = false
    this.appUIState.homeContentNavigationVisible = false
    HomeContentNavigationController.syncDefaultNavigation(this.appUIState)
  }

  build() {
    Column() {
      HMNavigation({
        navigationId: Constants.HOME_CONTENT_NAVIGATION_ID,
        options: {
          standardAnimator: HMDefaultGlobalAnimator.STANDARD_ANIMATOR,
          dialogAnimator: HMDefaultGlobalAnimator.DIALOG_ANIMATOR,
          modifier: this.modifier
        }
      })
        .layoutWeight(1)
        .width('100%')
    }
    .width('100%')
    .height('100%')
    .opacity(this.appUIState.homeContentNavigationActive ? 1 : 0)
    .translate({ x: this.appUIState.homeContentNavigationActive ? 0 : 24 })
    .animation({ duration: 240, curve: Curve.FastOutSlowIn })
    .hitTestBehavior(this.appUIState.homeContentNavigationActive ?
      HitTestMode.Default : HitTestMode.BLOCK_DESCENDANTS)
  }
}
