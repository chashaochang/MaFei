import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { RouterConsts } from '../../../common/RouterConsts';
import { UIConstants } from '../../../common/UIConstants';
import { ActionBar } from '../../../component/ActionBar';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../../component/AppRouteDestination';
import { ThemeStyle } from '../../../entity/ThemeStyle';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../../theme/AppThemeOverlayPolicy';
import { ManagementNativePagePolicy } from '../ManagementNativePagePolicy';
import { ManagementPermissionState, ManagementSectionStatus } from '../ManagementModels';
import { ManagementDeviceDetailPanel } from './ManagementDeviceDetailPanel';
import { ManagementDeviceActionResult, ManagementDeviceItem } from './ManagementDeviceModels';
import { ManagementDevicesUIState } from './ManagementDevicesUIState';
import { ManagementDevicesViewModel } from './ManagementDevicesViewModel';
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct ManagementDeviceDetailContent {
  @Require @Param ui: ManagementDevicesUIState
  @Require @Param device: ManagementDeviceItem | null
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param deviceWidth: number
  @Require @Param safeTop: number
  @Require @Param safeBottom: number
  @Require @Param showLegacyActionBar: boolean
  @Require @Param contentScroller: Scroller
  @Require @Param unavailableText: ResourceStr
  @Event onRetry: () => void = () => {}
  @Event onRename: () => void = () => {}
  @Event onRevoke: () => void = () => {}

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private nativeTopSpacer(): number {
    return Math.max(0, this.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({ title: $r('app.string.management_device_detail_title') })
      }
      if (this.device) {
        if (this.showLegacyActionBar) {
          Scroll() {
            ManagementDeviceDetailPanel({
              ui: this.ui,
              device: this.device!,
              themeStyle: this.themeStyle,
              nativeThemeAvailable: this.nativeThemeAvailable,
              onRename: () => this.onRename(),
              onRevoke: () => this.onRevoke()
            })
          }
          .layoutWeight(1)
          .width('100%')
          .padding({ left: 16, right: 16, top: 12, bottom: this.safeBottom + 24 })
        } else {
          Scroll(this.contentScroller) {
            Column({ space: 12 }) {
              Blank().height(this.nativeTopSpacer())
              ManagementDeviceDetailPanel({
                ui: this.ui,
                device: this.device!,
                themeStyle: this.themeStyle,
                nativeThemeAvailable: this.nativeThemeAvailable,
                onRename: () => this.onRename(),
                onRevoke: () => this.onRevoke()
              })
            }
            .width('100%')
            .constraintSize({ maxWidth: 760 })
            .padding({
              left: this.deviceWidth >= 600 ? 24 : 16,
              right: this.deviceWidth >= 600 ? 24 : 16,
              bottom: ManagementNativePagePolicy.contentBottomPadding(this.safeBottom + 24, false)
            })
          }
          .width('100%')
          .height('100%')
          .layoutWeight(1)
          .scrollBar(BarState.Auto)
        }
      } else if (this.ui.status === ManagementSectionStatus.Loading) {
        LoadingProgress().layoutWeight(1).width(28).height(28).color($r('app.color.color_main'))
      } else {
        Column({ space: 14 }) {
          Text(this.unavailableText)
            .fontSize(14)
            .fontColor($r('app.color.text_1'))
            .textAlign(TextAlign.Center)
          Button($r('app.string.management_retry'), { type: ButtonType.Normal })
            .height(40)
            .padding({ left: 20, right: 20 })
            .borderRadius(this.useNativeMaterial() ? 20 : 10)
            .fontColor($r('app.color.on_primary'))
            .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
            .onClick(() => this.onRetry())
        }
        .layoutWeight(1)
        .width('100%')
        .justifyContent(FlexAlign.Center)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.themeStyle, this.nativeThemeAvailable) : Color.Transparent)
  }
}

@HMRouter({
  pageUrl: RouterConsts.ManagementDeviceDetailPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementDeviceDetailPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Local ui: ManagementDevicesUIState = new ManagementDevicesUIState(this.getUIContext())
  @Local vm: ManagementDevicesViewModel = new ManagementDevicesViewModel(this.ui)
  private readonly contentScroller: Scroller = new Scroller()

  aboutToAppear(): void {
    const param = HMRouterMgr.getCurrentParam() as object
    const deviceId = param ? param['deviceId'] as string : ''
    this.vm.init().then(() => {
      if (typeof deviceId === 'string') {
        this.vm.selectDevice(deviceId)
        const selected = this.vm.selectedDevice()
        if (selected) this.vm.startRename(selected)
      }
    })
  }

  aboutToDisappear(): void {
    this.vm.stop()
  }

  @Monitor('ui.permissionState')
  onPermissionChange(): void {
    if (this.ui.permissionState === ManagementPermissionState.Denied) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_permission_denied')
      })
      HMRouterMgr.pop()
    }
  }

  private selected(): ManagementDeviceItem | null {
    return this.vm.selectedDevice()
  }

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private dangerousDialogMaterialDecision(): OverlayMaterialDecision {
    return AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.Dangerous,
      this.vm.appUIState.themeStyle,
      this.vm.appUIState.systemMaterialAvailable,
      this.vm.appUIState.nativeThemeAvailable
    )
  }

  private async rename(): Promise<void> {
    const selected = this.selected()
    if (!selected) return
    const result = await this.vm.rename(selected)
    this.getUIContext().getPromptAction().showToast({
      message: result.ok ? $r('app.string.management_device_rename_success') :
        this.getString(result.message || 'management_device_action_failed')
    })
  }

  private async revoke(): Promise<void> {
    const selected = this.selected()
    if (!selected) return
    const result: ManagementDeviceActionResult = await this.vm.revoke(selected)
    this.getUIContext().getPromptAction().showToast({
      message: result.ok ? $r('app.string.management_device_revoke_success') :
        this.getString(result.message || 'management_device_action_failed')
    })
    if (result.ok) HMRouterMgr.pop()
  }

  private confirmRevoke(): void {
    const selected = this.selected()
    if (!selected) return
    if (selected.current) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_device_current_protected')
      })
      return
    }
    const message = `${selected.displayName}\n${selected.appName} ${selected.appVersion}\n${
      selected.lastUserName}\n${this.getString('management_device_login_will_expire')}`
    if (this.dangerousDialogMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_device_revoke_title'),
        message: message,
        buttons: [
          { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_device_revoke'), color: $r('app.color.main_red_color') }
        ],
        backgroundColor: this.useNativeMaterial() ?
          $r('sys.color.comp_background_primary') : $r('app.color.bg_1'),
        backgroundBlurStyle: BlurStyle.NONE,
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }).then((response) => {
        if (response.index === 1) this.revoke()
      }).catch(() => {
      })
      return
    }
    this.getUIContext().getPromptAction().showDialog({
      title: $r('app.string.management_device_revoke_title'),
      message: message,
      buttons: [
        { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
        { text: $r('app.string.management_device_revoke'), color: $r('app.color.main_red_color') }
      ]
    }).then((response) => {
      if (response.index === 1) this.revoke()
    }).catch(() => {
    })
  }

  private unavailableText(): ResourceStr {
    return this.ui.status === ManagementSectionStatus.Unsupported ?
      $r('app.string.management_device_unsupported') :
      (this.ui.status === ManagementSectionStatus.Empty || this.ui.status === ManagementSectionStatus.Content ?
        $r('app.string.management_device_empty') :
        this.getString(this.ui.message || 'management_device_load_failed'))
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.management_device_detail_title'),
        contentExtendsUnderTitleBar: true,
        scrollControllers: [this.contentScroller]
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              ManagementDeviceDetailContent({
                ui: this.ui,
                device: this.selected(),
                themeStyle: this.vm.appUIState.themeStyle,
                nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
                deviceWidth: this.vm.appUIState.deviceWidth,
                safeTop: this.vm.appUIState.safeTop,
                safeBottom: this.vm.appUIState.safeBottom,
                showLegacyActionBar: false,
                contentScroller: this.contentScroller,
                unavailableText: this.unavailableText(),
                onRetry: () => this.vm.init(),
                onRename: () => this.rename(),
                onRevoke: () => this.confirmRevoke()
              })
            } else {
              ManagementDeviceDetailContent({
                ui: this.ui,
                device: this.selected(),
                themeStyle: this.vm.appUIState.themeStyle,
                nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
                deviceWidth: this.vm.appUIState.deviceWidth,
                safeTop: this.vm.appUIState.safeTop,
                safeBottom: this.vm.appUIState.safeBottom,
                showLegacyActionBar: true,
                contentScroller: this.contentScroller,
                unavailableText: this.unavailableText(),
                onRetry: () => this.vm.init(),
                onRename: () => this.rename(),
                onRevoke: () => this.confirmRevoke()
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
        ManagementDeviceDetailContent({
          ui: this.ui,
          device: this.selected(),
          themeStyle: this.vm.appUIState.themeStyle,
          nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
          deviceWidth: this.vm.appUIState.deviceWidth,
          safeTop: this.vm.appUIState.safeTop,
          safeBottom: this.vm.appUIState.safeBottom,
          showLegacyActionBar: true,
          contentScroller: this.contentScroller,
          unavailableText: this.unavailableText(),
          onRetry: () => this.vm.init(),
          onRename: () => this.rename(),
          onRevoke: () => this.confirmRevoke()
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
