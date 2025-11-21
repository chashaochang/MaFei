import { common } from '@kit.AbilityKit';
import { ActivityEventHandler } from '../events/ActivityEventHandler';
import { AppStorageV2, AttributeUpdater, display } from '@kit.ArkUI';
import { HMDefaultGlobalAnimator, HMNavigation } from '@hadss/hmrouter';
import { Constants } from '../utils/Constants';
import { BackController } from '../webapp/BackController';
import { OhosSmartDialog } from 'ohos_smart_dialog';
import { OneMoreUtil } from '../utils/OneMoreUtil';
import { AppUIState } from '../entity/AppUIState';
import { WindowUtil } from '../utils/WindowUtil';
import { RouterConsts } from '../common/RouterConsts';

@Entry
@ComponentV2
struct Index {
  private eventHub: common.EventHub = (getContext() as common.UIAbilityContext).eventHub;
  @Provider() activityEventHandler: ActivityEventHandler = new ActivityEventHandler(this.eventHub)
  @Provider() showExternalConnectionError: boolean = false
  @Provider() isLogin: boolean = false
  modifier: NavModifier = new NavModifier();
  backController?: BackController
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  @Monitor('appUIState.orientation')
  onBreakpointChanged() {
    this.appUIState.isFullScreen = this.appUIState.orientation === display.Orientation.LANDSCAPE ||
      this.appUIState.orientation === display.Orientation.LANDSCAPE_INVERTED
    OneMoreUtil.updateNavModifier(this.appUIState)
  }

  @Monitor('appUIState.isFullScreen')
  onFullScreenChanged() {
    if (OneMoreUtil.isBigScreen()) {
      if (this.appUIState.isFullScreen) {
        this.modifier.instance?.navBarWidth(0)
      } else {
        this.modifier.instance?.navBarWidth('33%')
      }
    }
  }

  @Monitor('appUIState.colorMode')
  onColorModeChanged() {
    let context = this.getUIContext().getHostContext() as common.UIAbilityContext
    WindowUtil.updateStatusBarColor(context, this.appUIState.colorMode === 0)
  }

  onPageShow(): void {
    AppStorage.setOrCreate('modifier', this.modifier)
    let context = this.getUIContext().getHostContext() as common.UIAbilityContext
    WindowUtil.updateStatusBarColor(context, this.appUIState.colorMode === 0)
  }

  aboutToAppear(): void {
    this.activityEventHandler.subscribe()
  }

  aboutToDisappear(): void {
    this.activityEventHandler.unsubscribe()
  }

  build() {
    // @Entry中需要再套一层容器组件,Column或者Stack
    Stack() {
      Column() {
        // 使用HMNavigation容器
        HMNavigation({
          navigationId: Constants.NAVIGATION_ID,
          homePageUrl: RouterConsts.SplashPage,
          options: {
            standardAnimator: HMDefaultGlobalAnimator.STANDARD_ANIMATOR,
            dialogAnimator: HMDefaultGlobalAnimator.DIALOG_ANIMATOR,
            modifier: this.modifier,
          }
        })
      }
      // here dialog init
      OhosSmartDialog()
    }
    .height('100%')
    .width('100%')
  }

  onBackPress(): boolean | void {
    console.log('Index', 'onBackPress');
    return this.backController?.onBackPress()
  }
}

export class NavModifier extends AttributeUpdater<NavigationAttribute> {
  instance?: NavigationAttribute

  initializeModifier(instance: NavigationAttribute): void {
    this.instance = instance
    instance.hideTitleBar(true)
    instance.hideToolBar(true)
    let appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
    appUIState.navInstance = instance
    OneMoreUtil.updateNavModifier(appUIState, instance)
  }
}