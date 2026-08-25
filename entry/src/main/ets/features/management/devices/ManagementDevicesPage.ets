import {
  HMLifecycleContext,
  HMLifecycleState,
  HMRouter,
  HMRouterMgr,
  IHMLifecycleOwner
} from '@hadss/hmrouter';
import { RouterConsts } from '../../../common/RouterConsts';
import { UIConstants } from '../../../common/UIConstants';
import { ActionBar } from '../../../component/ActionBar';
import { AppRouteDestination } from '../../../component/AppRouteDestination';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../../theme/AppThemeOverlayPolicy';
import { AppThemeTokens } from '../../../theme/AppThemeTokens';
import { ManagementNativePagePolicy } from '../ManagementNativePagePolicy';
import { ManagementPermissionState, ManagementSectionStatus } from '../ManagementModels';
import { ManagementDeviceDetailPanel } from './ManagementDeviceDetailPanel';
import { ManagementDeviceFilterSheet } from './ManagementDeviceFilterSheet';
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
  private readonly contentScroller: Scroller = new Scroller()
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
    return ManagementNativePagePolicy.isExpanded(this.vm.appUIState.deviceWidth)
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private floatingOverlayMaterialDecision(): OverlayMaterialDecision {
    return AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.AppFloating,
      this.vm.appUIState.themeStyle,
      this.vm.appUIState.systemMaterialAvailable,
      this.vm.appUIState.nativeThemeAvailable
    )
  }

  private dangerousDialogMaterialDecision(): OverlayMaterialDecision {
    return AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.Dangerous,
      this.vm.appUIState.themeStyle,
      this.vm.appUIState.systemMaterialAvailable,
      this.vm.appUIState.nativeThemeAvailable
    )
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

  private nativeTopSpacer(): number {
    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  private activeFilterCount(): number {
    return (this.ui.recentDays > 0 ? 1 : 0) +
      (this.ui.selectedUsers.length > 0 ? 1 : 0) +
      (this.ui.sortField === ManagementDeviceSortField.LastActivity && !this.ui.ascending ? 0 : 1)
  }

  private canRevokeOthers(): boolean {
    return !this.ui.batchPending && this.ui.pendingDeviceId.length === 0 &&
      this.ui.sourceDevices.some((item: ManagementDeviceItem) => !item.current)
  }

  private formatDate(value: string): string {
    if (!value) return ''
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    const month = (date.getMonth() + 1).toString().padStart(2, '0')
    const day = date.getDate().toString().padStart(2, '0')
    const hour = date.getHours().toString().padStart(2, '0')
    const minute = date.getMinutes().toString().padStart(2, '0')
    return `${month}-${day} ${hour}:${minute}`
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
    if (this.dangerousDialogMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
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
    this.ui.recentDays = days
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

  private applyNativeFilters(recentDays: number, users: string[],
    sortField: ManagementDeviceSortField, ascending: boolean): void {
    this.ui.recentDays = recentDays
    this.ui.fromMs = recentDays <= 0 ? 0 : Date.now() - recentDays * 24 * 60 * 60 * 1000
    this.ui.toMs = 0
    this.ui.selectedUsers = users
    this.ui.sortField = sortField
    this.ui.ascending = ascending
    this.ui.filterSheetVisible = false
    this.vm.applyQuery(true)
  }

  @Styles
  private pressedRowStyle(): void {
    .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle))
  }

  @Styles
  private normalRowStyle(): void {
    .backgroundColor(Color.Transparent)
  }

  @Builder
  private legacyFilters() {
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
  private nativeFilters() {
    Row({ space: 8 }) {
      Search({ value: this.ui.query, placeholder: $r('app.string.management_device_search') })
        .layoutWeight(1)
        .height(40)
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
        .onChange((value: string) => {
          this.ui.query = value
          this.vm.applyQuery(true)
        })
      Stack({ alignContent: Alignment.TopEnd }) {
        Button() {
          SymbolGlyph($r('sys.symbol.more'))
            .fontSize(18)
            .fontColor([this.primaryTextColor()])
        }
        .width(40)
        .height(40)
        .borderRadius(20)
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
        .accessibilityText($r('app.string.management_device_filter_title'))
        .onClick(() => this.ui.filterSheetVisible = true)
        if (this.activeFilterCount() > 0) {
          Text(this.activeFilterCount().toString())
            .fontSize(10)
            .fontColor($r('app.color.on_primary'))
            .textAlign(TextAlign.Center)
            .width(18)
            .height(18)
            .borderRadius(9)
            .backgroundColor($r('app.color.color_main'))
        }
      }
      .width(40)
      .height(40)
    }
    .width('100%')
    .padding({ left: 12, right: 12, top: 12, bottom: 10 })
  }

  @Builder
  private filters() {
    if (this.useNativeMaterial()) {
      this.nativeFilters()
    } else {
      this.legacyFilters()
    }
  }

  @Builder
  private legacyDeviceRow(device: ManagementDeviceItem) {
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
  private nativeDeviceRow(device: ManagementDeviceItem) {
    Row({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.externaldrive'))
        .fontSize(20)
        .fontColor([$r('app.color.color_main')])
        .width(36)
        .height(36)
      Column({ space: 4 }) {
        Row({ space: 8 }) {
          Text(device.displayName)
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.primaryTextColor())
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          if (device.current) {
            Text($r('app.string.management_device_current'))
              .fontSize(11)
              .fontColor($r('app.color.status_success'))
          }
        }
        Text(`${device.appName} ${device.appVersion} · ${device.lastUserName}`)
          .fontSize(13)
          .fontColor(this.secondaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      Column({ space: 4 }) {
        Text(this.formatDate(device.lastActivityDate))
          .fontSize(11)
          .fontColor(this.secondaryTextColor())
          .maxLines(1)
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(20)
          .fontColor([this.secondaryTextColor()])
      }
      .alignItems(HorizontalAlign.End)
    }
    .width('100%')
    .constraintSize({ minHeight: 68 })
    .padding({ left: 12, right: 10, top: 8, bottom: 8 })
    .borderRadius(8)
    .stateStyles({ pressed: this.pressedRowStyle, normal: this.normalRowStyle })
    .onClick(() => this.openDevice(device))
  }

  @Builder
  private deviceRow(device: ManagementDeviceItem) {
    if (this.useNativeMaterial()) {
      this.nativeDeviceRow(device)
    } else {
      this.legacyDeviceRow(device)
    }
  }

  @Builder
  private listContent() {
    Column() {
      this.filters()
      if ((this.ui.status === ManagementSectionStatus.Content ||
        this.ui.status === ManagementSectionStatus.Error) && this.ui.devices.length > 0) {
        List({ scroller: this.contentScroller }) {
          ForEach(this.ui.devices, (item: ManagementDeviceItem) => {
            ListItem() { this.deviceRow(item) }
          }, (item: ManagementDeviceItem) => item.id)
        }
        .layoutWeight(1).width('100%').scrollBar(BarState.Auto)
        Row({ space: 10 }) {
          Button($r('app.string.management_device_previous'), { type: ButtonType.Normal })
            .height(36)
            .padding({ left: 16, right: 16 })
            .borderRadius(18)
            .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.bg_1'))
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
            .enabled(this.ui.pageIndex > 0)
            .onClick(() => this.vm.setPage(this.ui.pageIndex - 1))
          Text(`${this.ui.pageIndex + 1} / ${Math.max(1, this.ui.pageCount)}`)
            .fontSize(12).fontColor(this.secondaryTextColor())
          Button($r('app.string.management_device_next'), { type: ButtonType.Normal })
            .height(36)
            .padding({ left: 16, right: 16 })
            .borderRadius(18)
            .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.bg_1'))
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
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
    .width('100%')
    .height('100%')
    .borderRadius(this.useNativeMaterial() ? 8 : 0)
    .clip(this.useNativeMaterial())
    .backgroundColor(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.contentGroupBackground() : Color.Transparent)
  }

  @Builder
  private deviceFilterSheetBody() {
    ManagementDeviceFilterSheet({
      initialRecentDays: this.ui.recentDays,
      initialUsers: this.ui.selectedUsers,
      availableUsers: this.userNames(),
      initialSortField: this.ui.sortField,
      initialAscending: this.ui.ascending,
      onApply: (recentDays: number, users: string[],
        sortField: ManagementDeviceSortField, ascending: boolean) => {
        this.applyNativeFilters(recentDays, users, sortField, ascending)
      }
    })
  }

  @Builder
  private deviceFilterSheet() {
    if (this.floatingOverlayMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
      Column() {
        this.deviceFilterSheetBody()
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Transparent)
      .backgroundBlurStyle(BlurStyle.Thin)
    } else {
      Column() {
        this.deviceFilterSheetBody()
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Transparent)
    }
  }

  @Builder
  private pageContentBody(showLegacyActionBar: boolean) {
    if (showLegacyActionBar) {
      ActionBar({ title: $r('app.string.management_devices_title') })
    } else {
      Blank().height(this.nativeTopSpacer())
    }
    if (this.isLarge()) {
      Row({ space: 12 }) {
        Column() { this.listContent() }.width(320).height('100%')
        if (this.selectedDevice()) {
          Scroll() {
            ManagementDeviceDetailPanel({
              ui: this.ui,
              device: this.selectedDevice()!,
              themeStyle: this.vm.appUIState.themeStyle,
              nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
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

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    if (this.floatingOverlayMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
      Column() {
        this.pageContentBody(showLegacyActionBar)
      }
      .width('100%').height('100%')
      .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
      .bindSheet($$this.ui.filterSheetVisible, this.deviceFilterSheet(), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        showClose: true,
        backgroundColor: Color.Transparent
      })
    } else if (this.floatingOverlayMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Column() {
        this.pageContentBody(showLegacyActionBar)
      }
      .width('100%').height('100%')
      .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
      .bindSheet($$this.ui.filterSheetVisible, this.deviceFilterSheet(), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        showClose: true,
        backgroundColor: Color.Transparent,
        systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
      })
    } else if (this.floatingOverlayMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      Column() {
        this.pageContentBody(showLegacyActionBar)
      }
      .width('100%').height('100%')
      .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
      .bindSheet($$this.ui.filterSheetVisible, this.deviceFilterSheet(), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        showClose: true,
        backgroundColor: $r('app.color.bg_1'),
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      })
    } else {
      Column() {
        this.pageContentBody(showLegacyActionBar)
      }
      .width('100%').height('100%')
      .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
      .bindSheet($$this.ui.filterSheetVisible, this.deviceFilterSheet(), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        showClose: true
      })
    }
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_devices_title'),
      contentExtendsUnderTitleBar: true,
      menus: [{
        value: $r('app.string.management_device_revoke_others'),
        icon: $r('sys.symbol.trash'),
        isEnabled: this.canRevokeOthers(),
        action: () => this.confirmRevokeOthers()
      }],
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
