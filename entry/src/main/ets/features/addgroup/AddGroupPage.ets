import { HMRouter } from '@hadss/hmrouter';
import { AppStorageV2 } from '@kit.ArkUI';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { AppUIState } from '../../entity/AppUIState';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ThemeStyle } from '../../entity/ThemeStyle';
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct AddGroupQrGroup {
  @Require @Param nativeSurface: boolean
  @Require @Param themeStyle: ThemeStyle

  build() {
    Column() {
      Image($r('app.media.qrcode'))
        .width(240)
        .borderRadius(16)
        .padding(8)
        .backgroundColor('#fcfcfc')
      Blank().height(30)
      Text('扫描二维码加入群聊')
        .fontSize(16)
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
      Blank().height(20)
    }
    .width('100%')
    .padding(20)
    .backgroundColor(this.nativeSurface ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
    .borderRadius(this.nativeSurface ? AppThemeTokens.groupRadius(this.themeStyle) : 20)
    .clip(true)
  }
}

@ComponentV2
struct AddGroupContent {
  @Require @Param showLegacyActionBar: boolean
  @Require @Param themeStyle: ThemeStyle

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({
          title: '加入群聊'
        })
      }
      AddGroupQrGroup({
        nativeSurface: !this.showLegacyActionBar,
        themeStyle: this.themeStyle
      })
      Blank()
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
    .padding({ left: 16, right: 16 })
  }
}

@HMRouter({
  pageUrl: RouterConsts.AddGroupPage,
  useNavDst: true
})
@ComponentV2
export struct AddGroupPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: '加入群聊'
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              AddGroupContent({
                showLegacyActionBar: false,
                themeStyle: this.appUIState.themeStyle
              })
            } else {
              AddGroupContent({
                showLegacyActionBar: true,
                themeStyle: this.appUIState.themeStyle
              })
            }
          }
          .width('100%')
          .layoutWeight(1)
        }
        .width('100%')
        .height('100%')
      }
      .backgroundColor(this.routeDestination.hdsBackgroundColor())
      .titleMode(HdsNavDestinationTitleMode.MINI)
      .titleBar(this.routeDestination.nativeTitleBarOptions(this.routeOptions()))
      .hideTitleBar(this.routeDestination.hdsTitleBarHidden(this.routeOptions()))
      .hideBackButton(!this.routeDestination.backButtonVisible(this.routeOptions()))
      .hideToolBar(true)
      .bindToScrollable(this.routeDestination.hdsBoundScrollers(this.routeOptions()))
      .onWillAppear(() => this.routeDestination.helper.onWillAppear())
      .onAppear(() => this.routeDestination.helper.onAppear())
      .onWillShow(() => this.routeDestination.helper.onWillShow())
      .onShown(() => this.routeDestination.helper.onShown())
      .onWillHide(() => this.routeDestination.helper.onWillHide())
      .onHidden(() => this.routeDestination.helper.onHidden())
      .onWillDisappear(() => this.routeDestination.helper.onWillDisappear())
      .onDisAppear(() => this.routeDestination.helper.onDisAppear())
      .onReady((ctx: NavDestinationContext) => this.routeDestination.helper.onReady(ctx))
      .onBackPressed(() => this.routeDestination.handleBackPressed(this.routeOptions()))
      .onResult((result: ESObject) => this.routeDestination.helper.onResult(result))
      .onActive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onActive(reason))
      .onInactive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onInactive(reason))
      .ignoreLayoutSafeArea(
        this.routeDestination.nativeSafeAreaTypes(),
        this.routeDestination.nativeSafeAreaEdges()
      )
      .width('100%')
      .height('100%')
    } else {
      NavDestination() {
        AddGroupContent({
          showLegacyActionBar: true,
          themeStyle: this.appUIState.themeStyle
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
