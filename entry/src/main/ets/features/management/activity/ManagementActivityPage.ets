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
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../../theme/AppThemeTokens';
import { ManagementPermissionState, ManagementSectionStatus } from '../ManagementModels';
import {
  ManagementActivityItem,
  ManagementActivityMediaTarget,
  ManagementActivitySortField,
  ManagementActivityView
} from './ManagementActivityModels';
import { ManagementActivityUIState } from './ManagementActivityUIState';
import { ManagementActivityViewModel } from './ManagementActivityViewModel';

@HMRouter({
  pageUrl: RouterConsts.ManagementActivityPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementActivityPage {
  @Local ui: ManagementActivityUIState = new ManagementActivityUIState(this.getUIContext())
  @Local vm: ManagementActivityViewModel = new ManagementActivityViewModel(this.ui)
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

  private viewLabel(view: ManagementActivityView): ResourceStr {
    if (view === ManagementActivityView.User) return $r('app.string.management_activity_user')
    if (view === ManagementActivityView.System) return $r('app.string.management_activity_system')
    return $r('app.string.management_activity_all')
  }

  private severityColor(value: string): ResourceColor {
    if (value === 'Error' || value === 'Critical') return $r('app.color.main_red_color')
    if (value === 'Warning') return $r('app.color.status_warning')
    return $r('app.color.color_main')
  }

  private formatDate(value: string): string {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    const year = date.getFullYear()
    const month = (date.getMonth() + 1).toString().padStart(2, '0')
    const day = date.getDate().toString().padStart(2, '0')
    const hour = date.getHours().toString().padStart(2, '0')
    const minute = date.getMinutes().toString().padStart(2, '0')
    return `${year}-${month}-${day} ${hour}:${minute}`
  }

  private setRecentDays(days: number): void {
    this.ui.fromMs = days <= 0 ? 0 : Date.now() - days * 24 * 60 * 60 * 1000
    this.ui.toMs = 0
    this.vm.applyFilters()
  }

  private toggleSeverity(value: string): void {
    if (this.ui.selectedSeverities.includes(value)) {
      this.ui.selectedSeverities = this.ui.selectedSeverities.filter((item: string) => item !== value)
    } else {
      this.ui.selectedSeverities = this.ui.selectedSeverities.concat(value)
    }
    this.vm.applyFilters()
  }

  private toggleUser(value: string): void {
    if (this.ui.selectedUsers.includes(value)) {
      this.ui.selectedUsers = this.ui.selectedUsers.filter((item: string) => item !== value)
    } else {
      this.ui.selectedUsers = this.ui.selectedUsers.concat(value)
    }
    this.vm.applyFilters()
  }

  private userNames(): string[] {
    const result: string[] = []
    this.ui.sourceItems.forEach((item: ManagementActivityItem) => {
      if (item.userName.length > 0 && !result.includes(item.userName)) {
        result.push(item.userName)
      }
    })
    return result
  }

  private showOverview(item: ManagementActivityItem): void {
    if (deviceInfo.sdkApiVersion >= 26) {
      this.getUIContext().getPromptAction().showDialog({
        title: item.name,
        message: item.overview,
        buttons: [{ text: $r('app.string.management_confirm'), color: $r('app.color.color_main') }],
        backgroundColor: this.useNativeMaterial() ? Color.Transparent : $r('app.color.bg_1'),
        backgroundBlurStyle: BlurStyle.NONE,
        systemMaterial: this.useNativeMaterial() ?
          AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating) :
          AppThemeSurfaceResolver.disabledSystemMaterial()
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().showDialog({
        title: item.name,
        message: item.overview,
        buttons: [{ text: $r('app.string.management_confirm'), color: $r('app.color.color_main') }]
      }).catch(() => {
      })
    }
  }

  private async openMedia(item: ManagementActivityItem): Promise<void> {
    try {
      const target: ManagementActivityMediaTarget = await this.vm.loadMedia(item.itemId)
      if (!target.available) {
        this.getUIContext().getPromptAction().showToast({
          message: $r('app.string.management_activity_item_unavailable')
        })
        return
      }
      HMRouterMgr.to(RouterConsts.VideoDetailPage).withParam({
        id: target.id,
        mediaName: target.mediaName,
        isTvShows: target.isTvShows
      }).push()
    } catch (error) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_activity_item_failed')
      })
    }
  }

  @Builder
  private filters() {
    Column({ space: 8 }) {
      Row({ space: 6 }) {
        ForEach([ManagementActivityView.All, ManagementActivityView.User, ManagementActivityView.System],
          (view: ManagementActivityView) => {
            Button(this.viewLabel(view), { type: ButtonType.Normal })
              .layoutWeight(1).height(36)
              .fontColor(this.ui.view === view ? $r('app.color.color_main') : this.secondaryTextColor())
              .backgroundColor(this.ui.view === view ?
                AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) : Color.Transparent)
              .onClick(() => this.vm.selectView(view))
          }, (view: ManagementActivityView) => view.toString())
      }
      .width('100%')
      Search({ value: this.ui.query, placeholder: $r('app.string.management_activity_search') })
        .width('100%').height(40)
        .onChange((value: string) => {
          this.ui.query = value
          this.vm.applyFilters()
        })
      List({ space: 6 }) {
        ForEach([0, 1, 7, 30], (days: number) => {
          ListItem() {
            Button(days === 0 ? $r('app.string.management_activity_time_all') :
              `${days} ${this.getString('management_activity_days')}`)
              .height(32).fontSize(11).onClick(() => this.setRecentDays(days))
          }
        }, (days: number) => days.toString())
        ForEach(['Information', 'Warning', 'Error'], (severity: string) => {
          ListItem() {
            Button(severity).height(32).fontSize(11)
              .fontColor(this.ui.selectedSeverities.includes(severity) ?
                $r('app.color.color_main') : this.secondaryTextColor())
              .onClick(() => this.toggleSeverity(severity))
          }
        }, (severity: string) => severity)
        ForEach(this.userNames(), (user: string) => {
          ListItem() {
            Button(user).height(32).fontSize(11)
              .fontColor(this.ui.selectedUsers.includes(user) ?
                $r('app.color.color_main') : this.secondaryTextColor())
              .onClick(() => this.toggleUser(user))
          }
        }, (user: string) => user)
      }
      .width('100%').height(34).listDirection(Axis.Horizontal).scrollBar(BarState.Off)
      Row({ space: 5 }) {
        Button($r('app.string.management_activity_sort_date')).height(28).fontSize(10)
          .onClick(() => this.vm.setSort(ManagementActivitySortField.Date))
        Button($r('app.string.management_activity_sort_severity')).height(28).fontSize(10)
          .onClick(() => this.vm.setSort(ManagementActivitySortField.Severity))
        Button($r('app.string.management_activity_sort_user')).height(28).fontSize(10)
          .onClick(() => this.vm.setSort(ManagementActivitySortField.User))
        Button($r('app.string.management_activity_sort_name')).height(28).fontSize(10)
          .onClick(() => this.vm.setSort(ManagementActivitySortField.Name))
      }
      .width('100%')
    }
    .width('100%').padding(12)
  }

  @Builder
  private compactRow(item: ManagementActivityItem) {
    Row({ space: 10 }) {
      Row().width(8).height(8).borderRadius(4).backgroundColor(this.severityColor(item.severity))
      Column({ space: 3 }) {
        Text(item.name).fontSize(14).fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor()).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
        if (item.shortOverview) {
          Text(item.shortOverview).fontSize(12).fontColor(this.secondaryTextColor())
            .maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis })
        }
        Text(`${this.formatDate(item.date)} · ${item.userName || item.type}`)
          .fontSize(11).fontColor(this.secondaryTextColor())
      }
      .layoutWeight(1).alignItems(HorizontalAlign.Start)
      if (item.overview.length > item.shortOverview.length) {
        Button($r('app.string.management_activity_info')).height(30).fontSize(11)
          .onClick(() => this.showOverview(item))
      }
      if (item.itemId) {
        Button($r('app.string.management_activity_item')).height(30).fontSize(11)
          .enabled(this.ui.pendingItemId.length === 0)
          .onClick(() => this.openMedia(item))
      }
    }
    .width('100%').constraintSize({ minHeight: 70 }).padding({ left: 12, right: 12 })
  }

  @Builder
  private wideRow(item: ManagementActivityItem) {
    Row({ space: 10 }) {
      Text(this.formatDate(item.date)).width(138).fontSize(11).fontColor(this.secondaryTextColor())
      Text(item.severity).width(78).fontSize(11).fontColor(this.severityColor(item.severity))
      Text(item.userName).width(100).fontSize(11).fontColor(this.secondaryTextColor())
      Text(item.name).layoutWeight(0.7).fontSize(12).fontColor(this.primaryTextColor()).maxLines(2)
      Text(item.shortOverview).layoutWeight(1).fontSize(11).fontColor(this.secondaryTextColor()).maxLines(2)
      Text(item.type).width(96).fontSize(11).fontColor(this.secondaryTextColor())
      if (item.itemId) {
        Button($r('app.string.management_activity_item')).width(72).height(30).fontSize(10)
          .enabled(this.ui.pendingItemId.length === 0)
          .onClick(() => this.openMedia(item))
      } else {
        Blank().width(72)
      }
    }
    .width('100%').constraintSize({ minHeight: 62 }).padding({ left: 12, right: 12 })
  }

  @Builder
  private activityList() {
    if ((this.ui.status === ManagementSectionStatus.Content ||
      this.ui.status === ManagementSectionStatus.Error) && this.ui.items.length > 0) {
      List() {
        ForEach(this.ui.items, (item: ManagementActivityItem) => {
          ListItem() {
            if (this.isLarge()) {
              this.wideRow(item)
            } else {
              this.compactRow(item)
            }
          }
        }, (item: ManagementActivityItem) => item.id.toString())
      }
      .layoutWeight(1).width('100%').scrollBar(BarState.Auto)
      if (this.ui.stale) {
        Row({ space: 10 }) {
          Text($r('app.string.management_stale_result'))
            .layoutWeight(1)
            .fontSize(12)
            .fontColor($r('app.color.status_warning'))
          Button($r('app.string.management_retry'), { type: ButtonType.Normal })
            .height(32)
            .enabled(!this.ui.refreshing)
            .onClick(() => this.vm.loadPage(this.ui.retryPageIndex))
        }
        .width('100%').padding({ left: 12, right: 12, top: 6 })
      }
      Row({ space: 10 }) {
        Button($r('app.string.management_activity_previous')).enabled(this.ui.displayedPageIndex > 0)
          .onClick(() => this.vm.loadPage(this.ui.displayedPageIndex - 1))
        Text(`${this.ui.displayedPageIndex + 1} / ${Math.max(1, this.ui.pageCount)}`)
          .fontSize(12).fontColor(this.secondaryTextColor())
        Button($r('app.string.management_activity_next'))
          .enabled(this.ui.displayedPageIndex + 1 < this.ui.pageCount)
          .onClick(() => this.vm.loadPage(this.ui.displayedPageIndex + 1))
      }
      .height(50).width('100%').justifyContent(FlexAlign.Center)
    } else if (this.ui.status === ManagementSectionStatus.Loading) {
      LoadingProgress().layoutWeight(1).width(28).height(28).color($r('app.color.color_main'))
    } else {
      Column({ space: 10 }) {
        Text(this.ui.status === ManagementSectionStatus.Unsupported ?
          $r('app.string.management_section_unsupported') : $r('app.string.management_activity_empty'))
          .fontSize(14).fontColor(this.secondaryTextColor())
        if (this.ui.status === ManagementSectionStatus.Error) {
          Button($r('app.string.management_retry')).onClick(() => this.vm.loadPage(this.ui.retryPageIndex))
        }
      }
      .layoutWeight(1).justifyContent(FlexAlign.Center)
    }
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) ActionBar({ title: $r('app.string.management_activity_title') })
      this.filters()
      this.activityList()
    }
    .width('100%').height('100%')
    .padding({ bottom: this.vm.appUIState.safeBottom })
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_activity_title'),
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
