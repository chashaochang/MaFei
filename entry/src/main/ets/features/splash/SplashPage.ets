import { AppPreference } from '../../data/AppPreference';
import { AppUIState } from '../../entity/AppUIState';
import { CommonConstants } from '../../utils/CommonConstants';
import { SplashConstants } from '../../utils/SplashConstants';
import { AppStorageV2 } from '@kit.ArkUI';
import { CustomColorMode } from '../../entity/CustomColorMode';
import { ConfigurationConstant } from '@kit.AbilityKit';
import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';

@HMRouter({
  pageUrl: RouterConsts.SplashPage
})
@ComponentV2
export struct SplashPage {
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  aboutToAppear(): void {
    this.appUIState.customColorMode = AppPreference.getInstance().getCustomColorMode()
    let context = this.getUIContext().getHostContext()!
    try {
      if (this.appUIState.customColorMode === CustomColorMode.Light) {
        context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT)
        this.appUIState.colorMode = ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT
      } else if (this.appUIState.customColorMode === CustomColorMode.Dark) {
        context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_DARK)
        this.appUIState.colorMode = ConfigurationConstant.ColorMode.COLOR_MODE_DARK
      } else {
        context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET)
        if (this.appUIState.context?.config.colorMode !== undefined) {
          this.appUIState.colorMode = this.appUIState.context?.config.colorMode
        } else {
          this.appUIState.colorMode = ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET
        }
      }
    } catch (e) {
      console.error('Index', 'onCustomColorModeChanged error:' + e)
    }
    this.init()
  }

  init(): void {
    setTimeout(() => {
      this.jump();
    }, SplashConstants.LOADING_RESOURCE_DURATION);
  }

  jump(): void {
    HMRouterMgr.to(RouterConsts.IndexPage).withAnimator(false).replace()
  }

  build() {
    Column() {
      Image($r('app.media.app_icon'))
        .width($r('app.float.image_width'))
        .aspectRatio(1)
        .objectFit(ImageFit.Contain)
        .transition(TransitionEffect.asymmetric(TransitionEffect.OPACITY.animation({
          duration: SplashConstants.SPLASH_ICON_OPACITY_DURATION,
          curve: Curve.Ease
        }), TransitionEffect.OPACITY.animation({
          duration: 0
        })))
    }
    .justifyContent(FlexAlign.Center)
    .backgroundColor($r('app.color.start_window_background'))
    .height(CommonConstants.FULL_PERCENT)
    .width(CommonConstants.FULL_PERCENT)
  }
}
