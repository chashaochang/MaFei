import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { RouterConsts } from '../../common/RouterConsts'
import { UIConstants } from '../../common/UIConstants'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination'
import { ActivityEventHandler } from '../../events/ActivityEventHandler'
import { ApiClient } from '../../network/ApiClient'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { WebITongUIState } from './WebITongUIState'
import { WebITongViewModel } from './WebITongViewModel'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct WebITongContent {
  @Require @Param ui: WebITongUIState
  @Require @Param vm: WebITongViewModel
  @Require @Param activityEventHandler: ActivityEventHandler
  @Require @Param showLegacyTopInset: boolean
  @Event onClose: () => void = () => {}

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private goBack(): void {
    if (this.vm.controller?.accessBackward()) {
      this.vm.controller?.backward()
      return
    }
    this.onClose()
  }

  private refresh(): void {
    try {
      this.vm.controller?.refresh()
    } catch (error) {
      console.log(`刷新失败:${JSON.stringify(error)}`)
    }
  }

  build() {
    Stack() {
      Column() {
        if (this.showLegacyTopInset) {
          Stack()
            .width('100%')
            .height(this.vm.appUIState.safeTop)
            .backgroundColor(this.ui.progress === 100 ?
              $r('app.color.start_window_background') : Color.Transparent)
        }
        Progress({ value: this.ui.progress })
          .visibility(this.ui.progress === 100 && !this.vm.isFirst ? Visibility.None : Visibility.Visible)
        Web({
          controller: this.vm.controller,
          src: ApiClient.Instance().basePath
        })
          .backgroundColor($r('app.color.start_window_background'))
          .darkMode(WebDarkMode.Auto)
          .domStorageAccess(true)
          .zoomAccess(true)
          .fileAccess(true)
          .mixedMode(MixedMode.All)
          .cacheMode(CacheMode.Default)
          .javaScriptAccess(true)
          .onControllerAttached(() => this.vm.registerJavaScriptProxy(this.activityEventHandler))
          .onPageEnd(() => this.vm.injectCredentials())
          .onProgressChange((event) => {
            this.ui.progress = event.newProgress
          })
          .onTitleReceive((event) => {
            this.ui.pageTitle = event.title
          })
          .onInterceptRequest((event) => {
            if (!event) return
            return this.vm.interceptRequest(event.request.getRequestUrl())
          })
          .onHttpErrorReceive((_event) => {
          })
          .onSslErrorEventReceive((event) => event.handler.handleCancel())
          .onConsole((_event) => false)
      }
      .width('100%')
      .height('100%')

      Row({ space: 12 }) {
        Stack() {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(20)
            .fontColor([$r('app.color.text_primary')])
            .fontWeight(FontWeight.Bold)
        }
        .align(Alignment.Center)
        .width(40)
        .height(40)
        .borderRadius(26)
        .backgroundBlurStyle(this.useNativeSurface() ? BlurStyle.NONE : BlurStyle.COMPONENT_ULTRA_THIN)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.InteractiveFloating,
          this.useNativeSurface(),
          this.vm.appUIState.systemMaterialAvailable
        ))
        .onClick(() => this.goBack())
        .visibility(this.ui.expandControl ? Visibility.Visible : Visibility.None)

        Stack() {
          SymbolGlyph($r('sys.symbol.xmark'))
            .fontSize(20)
            .fontColor([$r('app.color.text_primary')])
            .fontWeight(FontWeight.Bold)
        }
        .align(Alignment.Center)
        .width(40)
        .height(40)
        .borderRadius(26)
        .backgroundBlurStyle(this.useNativeSurface() ? BlurStyle.NONE : BlurStyle.COMPONENT_ULTRA_THIN)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.InteractiveFloating,
          this.useNativeSurface(),
          this.vm.appUIState.systemMaterialAvailable
        ))
        .onClick(() => this.onClose())
        .visibility(this.ui.expandControl ? Visibility.Visible : Visibility.None)

        Text(this.ui.pageTitle)
          .fontSize(14)
          .fontColor($r('app.color.text_primary'))
          .fontWeight(FontWeight.Medium)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .layoutWeight(1)
          .height(40)
          .borderWidth(AppThemeSurfaceResolver.outlineWidth(
            this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 1))
          .borderColor($r('app.color.border'))
          .backgroundBlurStyle(this.useNativeSurface() ? BlurStyle.NONE : BlurStyle.COMPONENT_ULTRA_THIN)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.Floating,
            this.useNativeSurface(),
            this.vm.appUIState.systemMaterialAvailable
          ))
          .borderRadius(6)
          .padding({ left: 12, right: 12 })
          .visibility(this.ui.expandControl ? Visibility.Visible : Visibility.None)

        Stack() {
          SymbolGlyph($r('sys.symbol.arrow_clockwise'))
            .fontSize(20)
            .fontColor([$r('app.color.text_primary')])
            .fontWeight(FontWeight.Bold)
        }
        .align(Alignment.Center)
        .width(40)
        .height(40)
        .borderRadius(26)
        .backgroundBlurStyle(this.useNativeSurface() ? BlurStyle.NONE : BlurStyle.COMPONENT_ULTRA_THIN)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.InteractiveFloating,
          this.useNativeSurface(),
          this.vm.appUIState.systemMaterialAvailable
        ))
        .onClick(() => this.refresh())
        .visibility(this.ui.expandControl ? Visibility.Visible : Visibility.None)

        Stack() {
          SymbolGlyph(this.ui.expandControl ? $r('sys.symbol.chevron_right_circle') :
            $r('sys.symbol.chevron_left_circle'))
            .fontSize(20)
            .fontColor([$r('app.color.text_primary')])
            .fontWeight(FontWeight.Bold)
        }
        .align(Alignment.Center)
        .width(40)
        .height(40)
        .borderRadius(26)
        .backgroundBlurStyle(this.useNativeSurface() ? BlurStyle.NONE : BlurStyle.COMPONENT_ULTRA_THIN)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.InteractiveFloating,
          this.useNativeSurface(),
          this.vm.appUIState.systemMaterialAvailable
        ))
        .onClick(() => {
          this.ui.expandControl = !this.ui.expandControl
        })
      }
      .alignItems(VerticalAlign.Center)
      .justifyContent(FlexAlign.End)
      .height(UIConstants.ACTION_BAR_HEIGHT)
      .width('100%')
      .padding({ left: 16, right: 16 })
      .margin({ bottom: this.vm.appUIState.safeBottom })
    }
    .width('100%')
    .height('100%')
    .align(Alignment.Bottom)
  }
}

@HMRouter({
  pageUrl: RouterConsts.WebITongPage,
  useNavDst: true
})
@ComponentV2
export struct WebITongPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Local ui: WebITongUIState = new WebITongUIState(this.getUIContext())
  @Local vm: WebITongViewModel = new WebITongViewModel(this.ui)
  @Consumer() activityEventHandler: ActivityEventHandler =
    new ActivityEventHandler(this.vm.appUIState.context!.eventHub)

  private routeTitle(): string {
    return this.ui.pageTitle.length > 0 ? this.ui.pageTitle : '网(Web)易通'
  }

  private handleRouteBack(): boolean {
    if (this.vm.controller?.accessBackward()) {
      this.vm.controller?.backward()
      return true
    }
    return false
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: this.routeTitle(),
        titleBarVisible: false,
        beforeBack: () => this.handleRouteBack()
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              WebITongContent({
                ui: this.ui,
                vm: this.vm,
                activityEventHandler: this.activityEventHandler,
                showLegacyTopInset: true,
                onClose: () => HMRouterMgr.pop()
              })
            } else {
              WebITongContent({
                ui: this.ui,
                vm: this.vm,
                activityEventHandler: this.activityEventHandler,
                showLegacyTopInset: true,
                onClose: () => HMRouterMgr.pop()
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
        WebITongContent({
          ui: this.ui,
          vm: this.vm,
          activityEventHandler: this.activityEventHandler,
          showLegacyTopInset: true,
          onClose: () => HMRouterMgr.pop()
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
