import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { deviceInfo } from '@kit.BasicServicesKit';
import { RouterConsts } from '../../../common/RouterConsts';
import { UIConstants } from '../../../common/UIConstants';
import { ActionBar } from '../../../component/ActionBar';
import { AppRouteDestination } from '../../../component/AppRouteDestination';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import { ManagementNativePagePolicy } from '../ManagementNativePagePolicy';
import { ManagementPermissionState, ManagementSectionStatus } from '../ManagementModels';
import { ManagementDeviceDetailPanel } from './ManagementDeviceDetailPanel';
import { ManagementDeviceActionResult, ManagementDeviceItem } from './ManagementDeviceModels';
import { ManagementDevicesUIState } from './ManagementDevicesUIState';
import { ManagementDevicesViewModel } from './ManagementDevicesViewModel';

@HMRouter({
  pageUrl: RouterConsts.ManagementDeviceDetailPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementDeviceDetailPage {
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

  private nativeTopSpacer(): number {
    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
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
    if (deviceInfo.sdkApiVersion >= 26) {
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

  @Builder
  private unavailableContent() {
    Column({ space: 14 }) {
      Text(this.ui.status === ManagementSectionStatus.Unsupported ?
        $r('app.string.management_device_unsupported') :
        (this.ui.status === ManagementSectionStatus.Empty ||
        this.ui.status === ManagementSectionStatus.Content ?
          $r('app.string.management_device_empty') :
          this.getString(this.ui.message || 'management_device_load_failed')))
        .fontSize(14)
        .fontColor($r('app.color.text_1'))
        .textAlign(TextAlign.Center)
      Button($r('app.string.management_retry'), { type: ButtonType.Normal })
        .height(40)
        .padding({ left: 20, right: 20 })
        .borderRadius(this.useNativeMaterial() ? 20 : 10)
        .fontColor($r('app.color.on_primary'))
        .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
        .systemMaterial(this.useNativeMaterial() ?
          AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive) :
          AppThemeSurfaceResolver.disabledSystemMaterial())
        .onClick(() => this.vm.init())
    }
    .layoutWeight(1)
    .width('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private nativeDetailContent() {
    Scroll(this.contentScroller) {
      Column({ space: 12 }) {
        Blank().height(this.nativeTopSpacer())
        ManagementDeviceDetailPanel({
          ui: this.ui,
          device: this.selected()!,
          themeStyle: this.vm.appUIState.themeStyle,
          nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
          onRename: () => this.rename(),
          onRevoke: () => this.confirmRevoke()
        })
      }
      .width('100%')
      .constraintSize({ maxWidth: 760 })
      .padding({
        left: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
        right: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
        bottom: ManagementNativePagePolicy.contentBottomPadding(this.vm.appUIState.safeBottom + 24, false)
      })
    }
    .width('100%')
    .height('100%')
    .layoutWeight(1)
    .scrollBar(BarState.Auto)
  }

  @Builder
  private legacyDetailContent() {
    Scroll() {
      ManagementDeviceDetailPanel({
        ui: this.ui,
        device: this.selected()!,
        themeStyle: this.vm.appUIState.themeStyle,
        nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
        onRename: () => this.rename(),
        onRevoke: () => this.confirmRevoke()
      })
    }
    .layoutWeight(1)
    .width('100%')
    .padding({ left: 16, right: 16, top: 12, bottom: this.vm.appUIState.safeBottom + 24 })
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) ActionBar({ title: $r('app.string.management_device_detail_title') })
      if (this.selected()) {
        if (showLegacyActionBar) {
          this.legacyDetailContent()
        } else {
          this.nativeDetailContent()
        }
      } else if (this.ui.status === ManagementSectionStatus.Loading) {
        LoadingProgress().layoutWeight(1).width(28).height(28).color($r('app.color.color_main'))
      } else {
        this.unavailableContent()
      }
    }
    .width('100%').height('100%')
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_device_detail_title'),
      contentExtendsUnderTitleBar: true,
      scrollControllers: [this.contentScroller],
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
