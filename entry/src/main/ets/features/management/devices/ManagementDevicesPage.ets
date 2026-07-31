import {
  HMLifecycleContext,
  HMLifecycleState,
  HMRouter,
  HMRouterMgr,
  IHMLifecycleOwner
} from '@hadss/hmrouter';
import { deviceInfo } from '@kit.BasicServicesKit';
import { RouterConsts } from '../../../common/RouterConsts';
import { ActionBar } from '../../../component/ActionBar';
import { AppRouteDestination } from '../../../component/AppRouteDestination';
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../../theme/AppThemeTokens';
import { ManagementPermissionState, ManagementSectionStatus } from '../ManagementModels';
import { ManagementDeviceDetailPanel } from './ManagementDeviceDetailPanel';
import {
  ManagementDeviceActionResult,
  ManagementDeviceBatchResult,
  ManagementDeviceItem,
  ManagementDeviceSortField
} from './ManagementDeviceModels';
import { ManagementDevicesUIState } from './ManagementDevicesUIState';
import { ManagementDevicesViewModel } from './ManagementDevicesViewModel';

@HMRouter({
  pageUrl: RouterConsts.ManagementDevicesPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementDevicesPage {
  @Local ui: ManagementDevicesUIState = new ManagementDevicesUIState(this.getUIContext())
  @Local vm: ManagementDevicesViewModel = new ManagementDevicesViewModel(this.ui)
  private lifecycleOwner: IHMLifecycleOwner | null = null
  private pageShownCallback = (_context: HMLifecycleContext): void => {
    this.vm.init()
  }
  private pageHiddenCallback = (_context: HMLifecycleContext): void => {
    this.vm.stop()
  }

  aboutToAppear(): void {
    this.lifecycleOwner = HMRouterMgr.getCurrentLifecycleOwner()
    if (this.lifecycleOwner) {
      this.lifecycleOwner.addObserver(HMLifecycleState.onShown, this.pageShownCallback)
      this.lifecycleOwner.addObserver(HMLifecycleState.onHidden, this.pageHiddenCallback)
    } else {
      this.vm.init()
    }
  }

  aboutToDisappear(): void {
    this.lifecycleOwner?.removeObserver(HMLifecycleState.onShown, this.pageShownCallback)
    this.lifecycleOwner?.removeObserver(HMLifecycleState.onHidden, this.pageHiddenCallback)
    this.lifecycleOwner = null
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

  private isLarge(): boolean {
    return this.vm.appUIState.deviceWidth >= 840
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private showActionResult(result: ManagementDeviceActionResult, success: ResourceStr): void {
    this.getUIContext().getPromptAction().showToast({
      message: result.ok ? success : (result.message ? this.getString(result.message) :
        $r('app.string.management_device_action_failed'))
    })
  }

  private openDevice(device: ManagementDeviceItem): void {
    this.vm.selectDevice(device.id)
    this.vm.startRename(device)
    if (!this.isLarge()) {
      HMRouterMgr.to(RouterConsts.ManagementDeviceDetailPage)
        .withParam({ deviceId: device.id })
        .push()
    }
  }

  private selectedDevice(): ManagementDeviceItem | null {
    return this.vm.selectedDevice()
  }

  private async renameSelected(): Promise<void> {
    const selected = this.selectedDevice()
    if (!selected) return
    this.showActionResult(await this.vm.rename(selected),
      $r('app.string.management_device_rename_success'))
  }

  private confirmRevoke(device: ManagementDeviceItem): void {
    if (device.current) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_device_current_protected')
      })
      return
    }
    const message = `${device.displayName}\n${device.appName} ${device.appVersion}\n${device.lastUserName}`
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
        if (response.index === 1) {
          this.vm.revoke(device).then((result: ManagementDeviceActionResult) =>
            this.showActionResult(result, $r('app.string.management_device_revoke_success')))
        }
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_device_revoke_title'),
        message: message,
        buttons: [
          { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_device_revoke'), color: $r('app.color.main_red_color') }
        ]
      }).then((response) => {
        if (response.index === 1) {
          this.vm.revoke(device).then((result: ManagementDeviceActionResult) =>
            this.showActionResult(result, $r('app.string.management_device_revoke_success')))
        }
      }).catch(() => {
      })
    }
  }

  private confirmRevokeOthers(): void {
    const count = this.ui.sourceDevices.filter((item: ManagementDeviceItem) => !item.current).length
    if (count <= 0) return
    const message = `${this.getString('management_device_revoke_others_count')} ${count}\n${
      this.getString('management_device_current_will_remain')}`
    if (deviceInfo.sdkApiVersion >= 26) {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_device_revoke_others'),
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
        if (response.index !== 1) return
        this.vm.revokeOthers().then((result: ManagementDeviceBatchResult) => {
          this.getUIContext().getPromptAction().showToast({
            message: `${this.getString('management_device_batch_success')} ${result.succeededIds.length}, ${
              this.getString('management_device_batch_failed')} ${result.failedIds.length}`
          })
        })
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_device_revoke_others'),
        message: message,
        buttons: [
          { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_device_revoke'), color: $r('app.color.main_red_color') }
        ]
      }).then((response) => {
        if (response.index !== 1) return
        this.vm.revokeOthers().then((result: ManagementDeviceBatchResult) => {
          this.getUIContext().getPromptAction().showToast({
            message: `${this.getString('management_device_batch_success')} ${result.succeededIds.length}, ${
              this.getString('management_device_batch_failed')} ${result.failedIds.length}`
          })
        })
      }).catch(() => {
      })
    }
  }

  private setRecentDays(days: number): void {
    this.ui.fromMs = days <= 0 ? 0 : Date.now() - days * 24 * 60 * 60 * 1000
    this.ui.toMs = 0
    this.vm.applyQuery(true)
  }

  private toggleUser(userName: string): void {
    const index = this.ui.selectedUsers.indexOf(userName)
    if (index >= 0) {
      this.ui.selectedUsers = this.ui.selectedUsers.filter((value: string) => value !== userName)
    } else {
      this.ui.selectedUsers = this.ui.selectedUsers.concat(userName)
    }
    this.vm.applyQuery(true)
  }

  private userNames(): string[] {
    const result: string[] = []
    this.ui.sourceDevices.forEach((item: ManagementDeviceItem) => {
      const value = item.lastUserName
      if (value.length > 0 && !result.includes(value)) {
        result.push(value)
      }
    })
    return result
  }

  @Builder
  private filters() {
    Column({ space: 8 }) {
      Row({ space: 8 }) {
        Search({ value: this.ui.query, placeholder: $r('app.string.management_device_search') })
          .layoutWeight(1).height(40)
          .onChange((value: string) => {
            this.ui.query = value
            this.vm.applyQuery(true)
          })
        Button($r('app.string.management_device_revoke_others'), { type: ButtonType.Normal })
          .height(40).fontColor($r('app.color.main_red_color'))
          .enabled(!this.ui.batchPending && this.ui.pendingDeviceId.length === 0)
          .onClick(() => this.confirmRevokeOthers())
      }
      List({ space: 6 }) {
        ForEach([0, 7, 30], (days: number) => {
          ListItem() {
            Button(days === 0 ? $r('app.string.management_device_all_time') :
              `${days} ${this.getString('management_device_days')}`, { type: ButtonType.Normal })
              .height(32).fontSize(12).onClick(() => this.setRecentDays(days))
          }
        }, (days: number) => days.toString())
        ForEach(this.userNames(), (user: string) => {
          ListItem() {
            Button(user, { type: ButtonType.Normal })
              .height(32).fontSize(12)
              .fontColor(this.ui.selectedUsers.includes(user) ? $r('app.color.color_main') :
                this.secondaryTextColor())
              .onClick(() => this.toggleUser(user))
          }
        }, (user: string) => user)
      }
      .width('100%').height(34).listDirection(Axis.Horizontal).scrollBar(BarState.Off)
      Row({ space: 6 }) {
        Button($r('app.string.management_device_sort_recent')).height(30).fontSize(11)
          .onClick(() => this.vm.setSort(ManagementDeviceSortField.LastActivity))
        Button($r('app.string.management_device_sort_name')).height(30).fontSize(11)
          .onClick(() => this.vm.setSort(ManagementDeviceSortField.Name))
        Button($r('app.string.management_device_sort_app')).height(30).fontSize(11)
          .onClick(() => this.vm.setSort(ManagementDeviceSortField.App))
        Button($r('app.string.management_device_sort_user')).height(30).fontSize(11)
          .onClick(() => this.vm.setSort(ManagementDeviceSortField.User))
      }
      .width('100%')
    }
    .width('100%').padding(12)
  }

  @Builder
  private deviceRow(device: ManagementDeviceItem) {
    Row({ space: 10 }) {
      SymbolGlyph($r('sys.symbol.externaldrive'))
        .fontSize(19).fontColor([$r('app.color.color_main')])
        .width(38).height(38)
      Column({ space: 3 }) {
        Row({ space: 6 }) {
          Text(device.displayName).fontSize(14).fontWeight(FontWeight.Medium)
            .fontColor(this.primaryTextColor()).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
          if (device.current) {
            Text($r('app.string.management_device_current')).fontSize(10)
              .fontColor($r('app.color.status_success'))
          }
        }
        Text(`${device.appName} ${device.appVersion} · ${device.lastUserName}`)
          .fontSize(12).fontColor(this.secondaryTextColor()).maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(device.lastActivityDate).fontSize(11).fontColor(this.secondaryTextColor())
      }
      .layoutWeight(1).alignItems(HorizontalAlign.Start)
      SymbolGlyph($r('sys.symbol.chevron_right')).fontSize(12).fontColor([this.secondaryTextColor()])
    }
    .width('100%').constraintSize({ minHeight: 66 }).padding({ left: 12, right: 12 })
    .onClick(() => this.openDevice(device))
  }

  @Builder
  private listContent() {
    Column() {
      this.filters()
      if ((this.ui.status === ManagementSectionStatus.Content ||
        this.ui.status === ManagementSectionStatus.Error) && this.ui.devices.length > 0) {
        List() {
          ForEach(this.ui.devices, (item: ManagementDeviceItem) => {
            ListItem() { this.deviceRow(item) }
          }, (item: ManagementDeviceItem) => item.id)
        }
        .layoutWeight(1).width('100%').scrollBar(BarState.Auto)
        Row({ space: 10 }) {
          Button($r('app.string.management_device_previous')).enabled(this.ui.pageIndex > 0)
            .onClick(() => this.vm.setPage(this.ui.pageIndex - 1))
          Text(`${this.ui.pageIndex + 1} / ${Math.max(1, this.ui.pageCount)}`)
            .fontSize(12).fontColor(this.secondaryTextColor())
          Button($r('app.string.management_device_next'))
            .enabled(this.ui.pageIndex + 1 < this.ui.pageCount)
            .onClick(() => this.vm.setPage(this.ui.pageIndex + 1))
        }
        .height(48).width('100%').justifyContent(FlexAlign.Center)
      } else if (this.ui.status === ManagementSectionStatus.Loading) {
        LoadingProgress().layoutWeight(1).width(28).height(28).color($r('app.color.color_main'))
      } else {
        Text(this.ui.status === ManagementSectionStatus.Unsupported ?
          $r('app.string.management_device_unsupported') : $r('app.string.management_device_empty'))
          .layoutWeight(1).fontSize(14).fontColor(this.secondaryTextColor())
      }
    }
    .width('100%').height('100%')
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) ActionBar({ title: $r('app.string.management_devices_title') })
      if (this.isLarge()) {
        Row({ space: 12 }) {
          Column() { this.listContent() }.width(320).height('100%')
          if (this.selectedDevice()) {
            Scroll() {
              ManagementDeviceDetailPanel({
                ui: this.ui,
                device: this.selectedDevice()!,
                themeStyle: this.vm.appUIState.themeStyle,
                onRename: () => this.renameSelected(),
                onRevoke: () => this.confirmRevoke(this.selectedDevice()!)
              })
            }
            .layoutWeight(1).height('100%').padding(16)
          } else {
            Blank().layoutWeight(1)
          }
        }
        .layoutWeight(1).width('100%').padding({ left: 12, right: 12 })
      } else {
        this.listContent()
      }
    }
    .width('100%').height('100%')
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_devices_title'),
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
