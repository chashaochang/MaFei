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
import { ManagementActivityFilterSheet } from './ManagementActivityFilterSheet';
import {
  ManagementActivityItem,
  ManagementActivityMediaTarget,
  ManagementActivitySortField,
  ManagementActivityView
} from './ManagementActivityModels';
import { ManagementActivityUIState } from './ManagementActivityUIState';
import { ManagementActivityViewModel } from './ManagementActivityViewModel';

enum ManagementActivitySheetKind {
  Filter,
  Detail
}

@HMRouter({
  pageUrl: RouterConsts.ManagementActivityPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementActivityPage {
  @Local ui: ManagementActivityUIState = new ManagementActivityUIState(this.getUIContext())
  @Local vm: ManagementActivityViewModel = new ManagementActivityViewModel(this.ui)
  @Local sheetVisible: boolean = false
  @Local sheetKind: ManagementActivitySheetKind = ManagementActivitySheetKind.Filter
  @Local selectedItem: ManagementActivityItem | null = null
  private lifecycleOwner: IHMLifecycleOwner | null = null
  private readonly listScroller: Scroller = new Scroller()
  private readonly detailScroller: Scroller = new Scroller()
  private readonly sheetScroller: Scroller = new Scroller()
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

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private nativeTopSpacer(): number {
    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
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

  private detailGroupBackground(inSheet: boolean): ResourceColor {
    return inSheet && this.useNativeMaterial() ?
      Color.Transparent : AppThemeSurfaceResolver.contentGroupBackground()
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

  private recentDays(): number {
    if (this.ui.fromMs <= 0) return 0
    const days = Math.round((Date.now() - this.ui.fromMs) / (24 * 60 * 60 * 1000))
    if (days === 1 || days === 7 || days === 30) return days
    return 0
  }

  private activeFilterCount(): number {
    return (this.recentDays() > 0 ? 1 : 0) +
      (this.ui.selectedSeverities.length > 0 ? 1 : 0) +
      (this.ui.selectedUsers.length > 0 ? 1 : 0) +
      (this.ui.sortField === ManagementActivitySortField.Date && !this.ui.ascending ? 0 : 1)
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
      if (item.userName.length > 0 && !result.includes(item.userName)) result.push(item.userName)
    })
    return result
  }

  private applyNativeFilters(recentDays: number, severities: string[], users: string[],
    sortField: ManagementActivitySortField, ascending: boolean): void {
    this.ui.fromMs = recentDays <= 0 ? 0 : Date.now() - recentDays * 24 * 60 * 60 * 1000
    this.ui.toMs = 0
    this.ui.selectedSeverities = severities
    this.ui.selectedUsers = users
    this.ui.sortField = sortField
    this.ui.ascending = ascending
    this.sheetVisible = false
    this.vm.loadPage(0)
  }

  private showFilterSheet(): void {
    this.sheetKind = ManagementActivitySheetKind.Filter
    this.sheetVisible = true
  }

  private selectActivity(item: ManagementActivityItem): void {
    this.selectedItem = item
    if (!this.isLarge()) {
      this.sheetKind = ManagementActivitySheetKind.Detail
      this.sheetVisible = true
    }
  }

  private selectedActivity(): ManagementActivityItem | null {
    if (this.selectedItem && this.ui.items.some((item: ManagementActivityItem) =>
      item.id === this.selectedItem!.id)) {
      return this.selectedItem
    }
    return this.ui.items.length > 0 ? this.ui.items[0] : null
  }

  private showOverview(item: ManagementActivityItem): void {
    const decision = this.floatingOverlayMaterialDecision()
    if (decision === OverlayMaterialDecision.UseBlurFallback) {
      this.getUIContext().getPromptAction().showDialog({
        title: item.name,
        message: item.overview,
        buttons: [{ text: $r('app.string.management_confirm'), color: $r('app.color.color_main') }],
        backgroundColor: Color.Transparent,
        backgroundBlurStyle: BlurStyle.Thin
      }).catch(() => {
      })
    } else if (decision === OverlayMaterialDecision.UseFloatingMaterial) {
      this.getUIContext().getPromptAction().showDialog({
        title: item.name,
        message: item.overview,
        buttons: [{ text: $r('app.string.management_confirm'), color: $r('app.color.color_main') }],
        backgroundColor: Color.Transparent,
        systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
      }).catch(() => {
      })
    } else if (decision === OverlayMaterialDecision.DisableSystemMaterial) {
      this.getUIContext().getPromptAction().showDialog({
        title: item.name,
        message: item.overview,
        buttons: [{ text: $r('app.string.management_confirm'), color: $r('app.color.color_main') }],
        backgroundColor: $r('app.color.bg_1'),
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
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
      this.sheetVisible = false
      HMRouterMgr.to(RouterConsts.VideoDetailPage).withParam({
        id: target.id,
        mediaName: target.mediaName,
        isTvShows: target.isTvShows
      }).push()
    } catch (_error) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_activity_item_failed')
      })
    }
  }

  @Builder
  private nativeFilters() {
    Column({ space: 10 }) {
      Row({ space: 8 }) {
        Search({ value: this.ui.query, placeholder: $r('app.string.management_activity_search') })
          .layoutWeight(1)
          .height(40)
          .backgroundColor(Color.Transparent)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.AdaptiveInteractive, this.useNativeMaterial()))
          .onChange((value: string) => {
            this.ui.query = value
            this.vm.applyFilters()
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
            AppThemeMaterialRole.AdaptiveInteractive, this.useNativeMaterial()))
          .accessibilityText($r('app.string.management_activity_filter_title'))
          .onClick(() => this.showFilterSheet())
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
      Row() {
        ForEach([ManagementActivityView.All, ManagementActivityView.User, ManagementActivityView.System],
          (view: ManagementActivityView) => {
            Button(this.viewLabel(view), { type: ButtonType.Normal })
              .layoutWeight(1)
              .height(36)
              .fontSize(13)
              .fontColor(this.ui.view === view ? this.primaryTextColor() : this.secondaryTextColor())
              .backgroundColor(this.ui.view === view ?
                AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) : Color.Transparent)
              .borderRadius(18)
              .onClick(() => this.vm.selectView(view))
          }, (view: ManagementActivityView) => view.toString())
      }
      .width('100%')
      .padding(2)
      .borderRadius(20)
      .backgroundColor(Color.Transparent)
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveInteractive, this.useNativeMaterial()))
    }
    .width('100%')
    .padding({ left: 12, right: 12, top: 12, bottom: 10 })
  }

  @Builder
  private legacyFilters() {
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
  private nativeRow(item: ManagementActivityItem) {
    Row({ space: 12 }) {
      Row()
        .width(8)
        .height(8)
        .borderRadius(4)
        .backgroundColor(this.severityColor(item.severity))
      Column({ space: 4 }) {
        Text(item.name)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(`${this.formatDate(item.date)} · ${item.userName || item.type}`)
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(20)
        .fontColor([this.secondaryTextColor()])
    }
    .width('100%')
    .constraintSize({ minHeight: 60 })
    .padding({ left: 14, right: 14, top: 8, bottom: 8 })
    .backgroundColor(this.isLarge() && this.selectedActivity()?.id === item.id ?
      AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) : Color.Transparent)
    .onClick(() => this.selectActivity(item))
  }

  @Builder
  private legacyCompactRow(item: ManagementActivityItem) {
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
  private legacyWideRow(item: ManagementActivityItem) {
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
  private pagingControls() {
    Row({ space: 10 }) {
      Button($r('app.string.management_activity_previous'))
        .height(36)
        .padding({ left: 16, right: 16 })
        .borderRadius(this.useNativeMaterial() ? 18 : 8)
        .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.bg_1'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveInteractive, this.useNativeMaterial()))
        .enabled(this.ui.displayedPageIndex > 0)
        .onClick(() => this.vm.loadPage(this.ui.displayedPageIndex - 1))
      Text(`${this.ui.displayedPageIndex + 1} / ${Math.max(1, this.ui.pageCount)}`)
        .fontSize(12)
        .fontColor(this.secondaryTextColor())
      Button($r('app.string.management_activity_next'))
        .height(36)
        .padding({ left: 16, right: 16 })
        .borderRadius(this.useNativeMaterial() ? 18 : 8)
        .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.bg_1'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveInteractive, this.useNativeMaterial()))
        .enabled(this.ui.displayedPageIndex + 1 < this.ui.pageCount)
        .onClick(() => this.vm.loadPage(this.ui.displayedPageIndex + 1))
    }
    .height(50)
    .width('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private staleNotice() {
    Row({ space: 10 }) {
      Text($r('app.string.management_stale_result'))
        .layoutWeight(1)
        .fontSize(12)
        .fontColor($r('app.color.status_warning'))
      Button($r('app.string.management_retry'), { type: ButtonType.Normal })
        .height(32)
        .padding({ left: 12, right: 12 })
        .borderRadius(this.useNativeMaterial() ? 16 : 8)
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveInteractive, this.useNativeMaterial()))
        .enabled(!this.ui.refreshing)
        .onClick(() => this.vm.loadPage(this.ui.retryPageIndex))
    }
    .width('100%')
    .padding({ left: 12, right: 12, top: 6 })
  }

  @Builder
  private stateContent() {
    if (this.ui.status === ManagementSectionStatus.Loading) {
      LoadingProgress().layoutWeight(1).width(28).height(28).color($r('app.color.color_main'))
    } else {
      Column({ space: 10 }) {
        Text(this.ui.status === ManagementSectionStatus.Unsupported ?
          $r('app.string.management_section_unsupported') : $r('app.string.management_activity_empty'))
          .fontSize(14)
          .fontColor(this.secondaryTextColor())
        if (this.ui.status === ManagementSectionStatus.Error) {
          Button($r('app.string.management_retry'))
            .height(40)
            .padding({ left: 22, right: 22 })
            .borderRadius(this.useNativeMaterial() ? 20 : 10)
            .fontColor($r('app.color.on_primary'))
            .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
            .onClick(() => this.vm.loadPage(this.ui.retryPageIndex))
        }
      }
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
    }
  }

  @Builder
  private nativeActivityList() {
    if ((this.ui.status === ManagementSectionStatus.Content ||
      this.ui.status === ManagementSectionStatus.Error) && this.ui.items.length > 0) {
      List({ scroller: this.listScroller }) {
        ForEach(this.ui.items, (item: ManagementActivityItem, index: number) => {
          ListItem() {
            Column() {
              this.nativeRow(item)
              if (index + 1 < this.ui.items.length) {
                Divider().margin({ left: 34 }).color($r('sys.color.comp_divider'))
              }
            }
          }
        }, (item: ManagementActivityItem) => item.id.toString())
      }
      .layoutWeight(1)
      .width('100%')
      .scrollBar(BarState.Auto)
      .borderRadius(8)
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
      if (this.ui.stale) this.staleNotice()
      this.pagingControls()
    } else {
      this.stateContent()
    }
  }

  @Builder
  private legacyActivityList() {
    if ((this.ui.status === ManagementSectionStatus.Content ||
      this.ui.status === ManagementSectionStatus.Error) && this.ui.items.length > 0) {
      List() {
        ForEach(this.ui.items, (item: ManagementActivityItem) => {
          ListItem() {
            if (this.isLarge()) {
              this.legacyWideRow(item)
            } else {
              this.legacyCompactRow(item)
            }
          }
        }, (item: ManagementActivityItem) => item.id.toString())
      }
      .layoutWeight(1).width('100%').scrollBar(BarState.Auto)
      if (this.ui.stale) this.staleNotice()
      this.pagingControls()
    } else {
      this.stateContent()
    }
  }

  @Builder
  private detailRow(label: ResourceStr, value: ResourceStr, showDivider: boolean = true) {
    Column() {
      Row({ space: 16 }) {
        Text(label)
          .fontSize(13)
          .fontColor(this.secondaryTextColor())
          .constraintSize({ minWidth: 92 })
        Text(value || $r('app.string.management_detail_unknown'))
          .fontSize(13)
          .fontColor(this.primaryTextColor())
          .layoutWeight(1)
          .textAlign(TextAlign.End)
          .maxLines(3)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .width('100%')
      .constraintSize({ minHeight: 48 })
      if (showDivider) Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
    }
    .width('100%')
  }

  @Builder
  private activityDetailBody(item: ManagementActivityItem, inSheet: boolean = false) {
    Column({ space: 12 }) {
      Column({ space: 8 }) {
        Row({ space: 10 }) {
          Row().width(10).height(10).borderRadius(5).backgroundColor(this.severityColor(item.severity))
          Text(item.name)
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.primaryTextColor())
            .layoutWeight(1)
            .maxLines(3)
        }
        if (item.overview || item.shortOverview) {
          Text(item.overview || item.shortOverview)
            .fontSize(14)
            .fontColor(this.primaryTextColor())
            .lineHeight(22)
        }
      }
      .width('100%')
      .padding(18)
      .alignItems(HorizontalAlign.Start)
      .borderRadius(8)
      .backgroundColor(this.detailGroupBackground(inSheet))
      Column() {
        this.detailRow($r('app.string.management_detail_status'), item.severity)
        this.detailRow($r('app.string.management_detail_last_activity'), this.formatDate(item.date))
        this.detailRow($r('app.string.management_detail_user'), item.userName)
        this.detailRow($r('app.string.management_detail_category'), item.type, false)
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
      .borderRadius(8)
      .clip(true)
      .backgroundColor(this.detailGroupBackground(inSheet))
    }
    .width('100%')
  }

  @Builder
  private openMediaButton(item: ManagementActivityItem) {
    if (item.itemId) {
      Button($r('app.string.management_activity_item'), { type: ButtonType.Normal })
        .width('100%')
        .height(44)
        .borderRadius(22)
        .fontColor($r('app.color.on_primary'))
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
        .enabled(this.ui.pendingItemId.length === 0)
        .onClick(() => this.openMedia(item))
    }
  }

  @Builder
  private activityDetailSheet() {
    Column() {
      Row() {
        Text($r('app.string.management_activity_detail_title'))
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('sys.color.font_primary'))
          .layoutWeight(1)
      }
      .width('100%')
      .padding({ left: 20, right: 64, top: 16, bottom: 8 })
      if (this.selectedItem) {
        Scroll(this.sheetScroller) {
          this.activityDetailBody(this.selectedItem!, true)
        }
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 16, right: 16, bottom: 12 })
        .scrollBar(BarState.Auto)
        Column() {
          this.openMediaButton(this.selectedItem!)
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 10, bottom: Math.max(12, this.vm.appUIState.safeBottom) })
        .backgroundColor(Color.Transparent)
      }
    }
    .width('100%')
    .height('100%')
    .alignItems(HorizontalAlign.Start)
    .backgroundColor(Color.Transparent)
  }

  @Builder
  private expandedDetailPane() {
    if (this.selectedActivity()) {
      Scroll(this.detailScroller) {
        Column({ space: 12 }) {
          this.activityDetailBody(this.selectedActivity()!)
          this.openMediaButton(this.selectedActivity()!)
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 12, bottom: this.vm.appUIState.safeBottom + 24 })
      }
      .layoutWeight(1)
      .height('100%')
      .scrollBar(BarState.Auto)
    } else {
      Blank().layoutWeight(1)
    }
  }

  @Builder
  private filterSheet() {
    ManagementActivityFilterSheet({
      initialRecentDays: this.recentDays(),
      initialSeverities: this.ui.selectedSeverities,
      initialUsers: this.ui.selectedUsers,
      availableUsers: this.userNames(),
      initialSortField: this.ui.sortField,
      initialAscending: this.ui.ascending,
      onApply: (recentDays: number, severities: string[], users: string[],
        sortField: ManagementActivitySortField, ascending: boolean) => {
        this.applyNativeFilters(recentDays, severities, users, sortField, ascending)
      }
    })
  }

  @Builder
  private pageSheetBody() {
    if (this.sheetKind === ManagementActivitySheetKind.Filter) {
      this.filterSheet()
    } else {
      this.activityDetailSheet()
    }
  }

  @Builder
  private pageSheet() {
    if (this.floatingOverlayMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
      Column() {
        this.pageSheetBody()
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Transparent)
      .backgroundBlurStyle(BlurStyle.Thin)
    } else {
      Column() {
        this.pageSheetBody()
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Transparent)
    }
  }

  @Builder
  private nativePage() {
    Column() {
      Blank().height(this.nativeTopSpacer())
      if (this.isLarge()) {
        Row({ space: 12 }) {
          Column() {
            this.nativeFilters()
            this.nativeActivityList()
          }
          .width(340)
          .height('100%')
          this.expandedDetailPane()
        }
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 12, right: 12 })
      } else {
        Column() {
          this.nativeFilters()
          this.nativeActivityList()
        }
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 12, right: 12, bottom: this.vm.appUIState.safeBottom })
      }
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private legacyPage() {
    Column() {
      ActionBar({ title: $r('app.string.management_activity_title') })
      this.legacyFilters()
      this.legacyActivityList()
    }
    .width('100%')
    .height('100%')
    .padding({ bottom: this.vm.appUIState.safeBottom })
    .backgroundColor(AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
  }

  @Builder
  private pageContentBody(showLegacyActionBar: boolean) {
    if (showLegacyActionBar) {
      this.legacyPage()
    } else {
      this.nativePage()
    }
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    if (this.floatingOverlayMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
      Column() {
        this.pageContentBody(showLegacyActionBar)
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Transparent)
      .bindSheet($$this.sheetVisible, this.pageSheet(), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        showClose: true,
        backgroundColor: Color.Transparent
      })
    } else if (this.floatingOverlayMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Column() {
        this.pageContentBody(showLegacyActionBar)
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Transparent)
      .bindSheet($$this.sheetVisible, this.pageSheet(), {
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
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Transparent)
      .bindSheet($$this.sheetVisible, this.pageSheet(), {
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
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Transparent)
      .bindSheet($$this.sheetVisible, this.pageSheet(), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        showClose: true
      })
    }
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_activity_title'),
      contentExtendsUnderTitleBar: true,
      scrollControllers: [this.listScroller, this.detailScroller],
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
