import { AppPreference } from '../data/AppPreference';
import { CommonConstants } from '../utils/CommonConstants';
import { SplashConstants } from '../utils/SplashConstants';

@Entry
@Component
struct SplashPage {
  private preferenceManager: AppPreference = AppPreference.getInstance();
  isAgreedPrivacy: boolean =
    this.getUIContext().getRouter().getParams() ?
      (this.getUIContext().getRouter().getParams() as Record<string, boolean>)['isAgreedPrivacy'] :
      false;

  aboutToAppear(): void {
    setTimeout(() => {
      this.checkAgreedPrivacy();
    }, 0);
  }

  init(): void {
    setTimeout(() => {
      this.jump();
    }, SplashConstants.LOADING_RESOURCE_DURATION);
  }

  checkAgreedPrivacy(): void {
    if (this.isAgreedPrivacy) {
      this.preferenceManager.setValue('isAgreedPrivacy', true)
      this.init();
    } else {
      let isAgreed = this.preferenceManager.getValue<boolean>('isAgreedPrivacy')
      if (isAgreed) {
        this.init();
      } else {
        this.getUIContext().getRouter().replaceUrl({ url: 'pages/SafePage' }).catch()
      }
    }
  }

  jump(): void {
    this.getUIContext().getRouter().replaceUrl({
      url: `pages/Index`
    }).catch()
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

      Column() {
        Text($r('app.string.splash_sub_title'))
          .fontColor(Color.White)
          .fontSize($r('app.float.splash_title_text_size'))
          .fontWeight(SplashConstants.BUTTON_FONT_WEIGHT)
      }
      .transition(TransitionEffect.asymmetric(TransitionEffect.OPACITY.animation({
        duration: SplashConstants.SPLASH_OPACITY_DURATION,
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