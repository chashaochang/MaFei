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
import { AppRouteDestination, AppRouteDestinationOptions } from '../../../component/AppRouteDestination';
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
import { ManagementDetailRow } from '../ManagementDetailRow';
import { HdsNavDestination, HdsNavDestinationAttribute, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

enum ManagementActivitySheetKind {
  Filter,
  Detail
}

@ComponentV2
struct ManagementActivityPagingControls {
  @Param pageIndex: number = 0
  @Param pageCount: number = 1
  @Param nativeMaterial: boolean = false
  @Require @Param secondaryTextColor: ResourceColor
  @Event onPrevious: () => void = () => {}
  @Event onNext: () => void = () => {}

  build() {
    Row({ space: 10 }) {
      Button($r('app.string.management_activity_previous'))
        .height(36)
        .padding({ left: 16, right: 16 })
        .borderRadius(this.nativeMaterial ? 18 : 8)
        .backgroundColor(this.nativeMaterial ? Color.Transparent : $r('app.color.bg_1'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.nativeMaterial))
        .enabled(this.pageIndex > 0)
        .onClick(this.onPrevious)
      Text(`${this.pageIndex + 1} / ${Math.max(1, this.pageCount)}`)
        .fontSize(12)
        .fontColor(this.secondaryTextColor)
      Button($r('app.string.management_activity_next'))
        .height(36)
        .padding({ left: 16, right: 16 })
        .borderRadius(this.nativeMaterial ? 18 : 8)
        .backgroundColor(this.nativeMaterial ? Color.Transparent : $r('app.color.bg_1'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.nativeMaterial))
        .enabled(this.pageIndex + 1 < this.pageCount)
        .onClick(this.onNext)
    }
    .height(50)
    .width('100%')
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
struct ManagementActivityStaleNotice {
  @Param refreshing: boolean = false
  @Param nativeMaterial: boolean = false
  @Event onRetry: () => void = () => {}

  build() {
    Row({ space: 10 }) {
      Text($r('app.string.management_stale_result'))
        .layoutWeight(1)
        .fontSize(12)
        .fontColor($r('app.color.status_warning'))
      Button($r('app.string.management_retry'), { type: ButtonType.Normal })
        .height(32)
        .padding({ left: 12, right: 12 })
        .borderRadius(this.nativeMaterial ? 16 : 8)
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.nativeMaterial))
        .enabled(!this.refreshing)
        .onClick(this.onRetry)
    }
    .width('100%')
    .padding({ left: 12, right: 12, top: 6 })
  }
}

@ComponentV2
struct ManagementActivityStateContent {
  @Param status: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Param nativeMaterial: boolean = false
  @Require @Param secondaryTextColor: ResourceColor
  @Event onRetry: () => void = () => {}

  build() {
    if (this.status === ManagementSectionStatus.Loading) {
      LoadingProgress().layoutWeight(1).width(28).height(28).color($r('app.color.color_main'))
    } else {
      Column({ space: 10 }) {
        Text(this.status === ManagementSectionStatus.Unsupported ?
          $r('app.string.management_section_unsupported') : $r('app.string.management_activity_empty'))
          .fontSize(14)
          .fontColor(this.secondaryTextColor)
        if (this.status === ManagementSectionStatus.Error) {
          Button($r('app.string.management_retry'))
            .height(40)
            .padding({ left: 22, right: 22 })
            .borderRadius(this.nativeMaterial ? 20 : 10)
            .fontColor($r('app.color.on_primary'))
            .backgroundColor(this.nativeMaterial ? Color.Transparent : $r('app.color.color_main'))
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.PrimaryInteractive, this.nativeMaterial))
            .onClick(this.onRetry)
        }
      }
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
    }
  }
}

@ComponentV2
struct ManagementActivityDetailBody {
  @Require @Param item: ManagementActivityItem
  @Require @Param formattedDate: string
  @Require @Param groupBackground: ResourceColor
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor
  @Require @Param severityColor: ResourceColor

  build() {
    Column({ space: 12 }) {
      Column({ space: 8 }) {
        Row({ space: 10 }) {
          Row().width(10).height(10).borderRadius(5).backgroundColor(this.severityColor)
          Text(this.item.name)
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.primaryTextColor)
            .layoutWeight(1)
            .maxLines(3)
        }
        if (this.item.overview || this.item.shortOverview) {
          Text(this.item.overview || this.item.shortOverview)
            .fontSize(14)
            .fontColor(this.primaryTextColor)
            .lineHeight(22)
        }
      }
      .width('100%')
      .padding(18)
      .alignItems(HorizontalAlign.Start)
      .borderRadius(8)
      .backgroundColor(this.groupBackground)
      Column() {
        ManagementDetailRow({
          label: $r('app.string.management_detail_status'),
          value: this.item.severity,
          showDivider: true,
          valueMaxLines: 3,
          emptyValue: $r('app.string.management_detail_unknown'),
          dividerLeftMargin: 16,
          primaryTextColor: this.primaryTextColor,
          secondaryTextColor: this.secondaryTextColor,
          dividerColor: $r('sys.color.comp_divider')
        })
        ManagementDetailRow({
          label: $r('app.string.management_detail_last_activity'),
          value: this.formattedDate,
          showDivider: true,
          valueMaxLines: 3,
          emptyValue: $r('app.string.management_detail_unknown'),
          dividerLeftMargin: 16,
          primaryTextColor: this.primaryTextColor,
          secondaryTextColor: this.secondaryTextColor,
          dividerColor: $r('sys.color.comp_divider')
        })
        ManagementDetailRow({
          label: $r('app.string.management_detail_user'),
          value: this.item.userName,
          showDivider: true,
          valueMaxLines: 3,
          emptyValue: $r('app.string.management_detail_unknown'),
          dividerLeftMargin: 16,
          primaryTextColor: this.primaryTextColor,
          secondaryTextColor: this.secondaryTextColor,
          dividerColor: $r('sys.color.comp_divider')
        })
        ManagementDetailRow({
          label: $r('app.string.management_detail_category'),
          value: this.item.type,
          showDivider: false,
          valueMaxLines: 3,
          emptyValue: $r('app.string.management_detail_unknown'),
          dividerLeftMargin: 16,
          primaryTextColor: this.primaryTextColor,
          secondaryTextColor: this.secondaryTextColor,
          dividerColor: $r('sys.color.comp_divider')
        })
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
      .borderRadius(8)
      .clip(true)
      .backgroundColor(this.groupBackground)
    }
    .width('100%')
  }
}

@ComponentV2
struct ManagementActivityOpenMediaButton {
  @Require @Param item: ManagementActivityItem
  @Param pending: boolean = false
  @Param nativeMaterial: boolean = false
  @Event onOpen: () => void = () => {}

  build() {
    if (this.item.itemId) {
      Button($r('app.string.management_activity_item'), { type: ButtonType.Normal })
        .width('100%')
        .height(44)
        .borderRadius(22)
        .fontColor($r('app.color.on_primary'))
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.PrimaryInteractive, this.nativeMaterial))
        .enabled(!this.pending)
        .onClick(this.onOpen)
    }
  }
}

@ComponentV2
struct ManagementActivitySheetContent {
  @Require @Param sheetKind: ManagementActivitySheetKind
  @Param selectedItem: ManagementActivityItem | null = null
  @Param recentDays: number = 0
  @Param selectedSeverities: string[] = []
  @Param selectedUsers: string[] = []
  @Param availableUsers: string[] = []
  @Require @Param sortField: ManagementActivitySortField
  @Param ascending: boolean = false
  @Param pending: boolean = false
  @Param nativeMaterial: boolean = false
  @Param formattedDate: string = ''
  @Require @Param groupBackground: ResourceColor
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor
  @Require @Param severityColor: ResourceColor
  @Param safeBottom: number = 0
  @Param blurFallback: boolean = false
  @Param sheetScroller: Scroller = new Scroller()
  @Event onApply: (recentDays: number, severities: string[], users: string[],
    sortField: ManagementActivitySortField, ascending: boolean) => void =
    (_recentDays: number, _severities: string[], _users: string[],
      _sortField: ManagementActivitySortField, _ascending: boolean) => {}
  @Event onOpen: () => void = () => {}

  build() {
    Column() {
      if (this.sheetKind === ManagementActivitySheetKind.Filter) {
        ManagementActivityFilterSheet({
          initialRecentDays: this.recentDays,
          initialSeverities: this.selectedSeverities,
          initialUsers: this.selectedUsers,
          availableUsers: this.availableUsers,
          initialSortField: this.sortField,
          initialAscending: this.ascending,
          onApply: this.onApply
        })
      } else {
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
              ManagementActivityDetailBody({
                item: this.selectedItem,
                formattedDate: this.formattedDate,
                groupBackground: this.groupBackground,
                primaryTextColor: this.primaryTextColor,
                secondaryTextColor: this.secondaryTextColor,
                severityColor: this.severityColor
              })
            }
            .layoutWeight(1)
            .width('100%')
            .padding({ left: 16, right: 16, bottom: 12 })
            .scrollBar(BarState.Auto)
            Column() {
              ManagementActivityOpenMediaButton({
                item: this.selectedItem,
                pending: this.pending,
                nativeMaterial: this.nativeMaterial,
                onOpen: this.onOpen
              })
            }
            .width('100%')
            .padding({ left: 16, right: 16, top: 10, bottom: Math.max(12, this.safeBottom) })
            .backgroundColor(Color.Transparent)
          }
        }
        .width('100%')
        .height('100%')
        .alignItems(HorizontalAlign.Start)
        .backgroundColor(Color.Transparent)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Transparent)
    .backgroundBlurStyle(this.blurFallback ? BlurStyle.Thin : BlurStyle.NONE)
  }
}

@ComponentV2
struct ManagementActivityContent {
  @Param showLegacyActionBar: boolean = false
  @Param listScroller: Scroller = new Scroller()
  @Param detailScroller: Scroller = new Scroller()
  @Param sheetScroller: Scroller = new Scroller()
  @Local ui: ManagementActivityUIState = new ManagementActivityUIState(this.getUIContext())
  @Local vm: ManagementActivityViewModel = new ManagementActivityViewModel(this.ui)
  @Local sheetVisible: boolean = false
  @Local sheetKind: ManagementActivitySheetKind = ManagementActivitySheetKind.Filter
  @Local selectedItem: ManagementActivityItem | null = null
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

  private sheetOptions(): SheetOptions {
    const decision = this.floatingOverlayMaterialDecision()
    if (decision === OverlayMaterialDecision.UseFloatingMaterial) {
      return {
        height: '70%',
        preferType: SheetType.BOTTOM,
        showClose: true,
        backgroundColor: Color.Transparent,
        systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
      }
    }
    if (decision === OverlayMaterialDecision.DisableSystemMaterial) {
      return {
        height: '70%',
        preferType: SheetType.BOTTOM,
        showClose: true,
        backgroundColor: $r('app.color.bg_1'),
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }
    }
    if (decision === OverlayMaterialDecision.UseBlurFallback) {
      return {
        height: '70%',
        preferType: SheetType.BOTTOM,
        showClose: true,
        backgroundColor: Color.Transparent
      }
    }
    return {
      height: '70%',
      preferType: SheetType.BOTTOM,
      showClose: true
    }
  }

  // CustomBuilderAdapter: management-activity-sheet
  @Builder
  private activitySheetBuilder() {
    ManagementActivitySheetContent({
      sheetKind: this.sheetKind,
      selectedItem: this.selectedItem,
      recentDays: this.recentDays(),
      selectedSeverities: this.ui.selectedSeverities,
      selectedUsers: this.ui.selectedUsers,
      availableUsers: this.userNames(),
      sortField: this.ui.sortField,
      ascending: this.ui.ascending,
      pending: this.ui.pendingItemId.length > 0,
      nativeMaterial: this.useNativeMaterial(),
      formattedDate: this.selectedItem ? this.formatDate(this.selectedItem.date) : '',
      groupBackground: this.detailGroupBackground(true),
      primaryTextColor: this.primaryTextColor(),
      secondaryTextColor: this.secondaryTextColor(),
      severityColor: this.selectedItem ? this.severityColor(this.selectedItem.severity) : Color.Transparent,
      safeBottom: this.vm.appUIState.safeBottom,
      blurFallback: this.floatingOverlayMaterialDecision() === OverlayMaterialDecision.UseBlurFallback,
      sheetScroller: this.sheetScroller,
      onApply: (recentDays: number, severities: string[], users: string[],
        sortField: ManagementActivitySortField, ascending: boolean) => {
        this.applyNativeFilters(recentDays, severities, users, sortField, ascending)
      },
      onOpen: () => {
        if (this.selectedItem) {
          this.openMedia(this.selectedItem)
        }
      }
    })
  }



















  build() {
    Column() {
      if ((this.showLegacyActionBar)) {
        Column() {
          ActionBar({ title: $r('app.string.management_activity_title') })
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
          if ((this.ui.status === ManagementSectionStatus.Content ||
            this.ui.status === ManagementSectionStatus.Error) && this.ui.items.length > 0) {
            List() {
              ForEach(this.ui.items, (item: ManagementActivityItem) => {
                ListItem() {
                  if (this.isLarge()) {
                    Row({ space: 10 }) {
                      Text(this.formatDate((item).date)).width(138).fontSize(11).fontColor(this.secondaryTextColor())
                      Text((item).severity).width(78).fontSize(11).fontColor(this.severityColor((item).severity))
                      Text((item).userName).width(100).fontSize(11).fontColor(this.secondaryTextColor())
                      Text((item).name).layoutWeight(0.7).fontSize(12).fontColor(this.primaryTextColor()).maxLines(2)
                      Text((item).shortOverview).layoutWeight(1).fontSize(11).fontColor(this.secondaryTextColor()).maxLines(2)
                      Text((item).type).width(96).fontSize(11).fontColor(this.secondaryTextColor())
                      if ((item).itemId) {
                        Button($r('app.string.management_activity_item')).width(72).height(30).fontSize(10)
                          .enabled(this.ui.pendingItemId.length === 0)
                          .onClick(() => this.openMedia((item)))
                      } else {
                        Blank().width(72)
                      }
                    }
                    .width('100%').constraintSize({ minHeight: 62 }).padding({ left: 12, right: 12 })
                  } else {
                    Row({ space: 10 }) {
                      Row().width(8).height(8).borderRadius(4).backgroundColor(this.severityColor((item).severity))
                      Column({ space: 3 }) {
                        Text((item).name).fontSize(14).fontWeight(FontWeight.Medium)
                          .fontColor(this.primaryTextColor()).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
                        if ((item).shortOverview) {
                          Text((item).shortOverview).fontSize(12).fontColor(this.secondaryTextColor())
                            .maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis })
                        }
                        Text(`${this.formatDate(item.date)} · ${item.userName || item.type}`)
                          .fontSize(11).fontColor(this.secondaryTextColor())
                      }
                      .layoutWeight(1).alignItems(HorizontalAlign.Start)
                      if ((item).overview.length > (item).shortOverview.length) {
                        Button($r('app.string.management_activity_info')).height(30).fontSize(11)
                          .onClick(() => this.showOverview((item)))
                      }
                      if ((item).itemId) {
                        Button($r('app.string.management_activity_item')).height(30).fontSize(11)
                          .enabled(this.ui.pendingItemId.length === 0)
                          .onClick(() => this.openMedia((item)))
                      }
                    }
                    .width('100%').constraintSize({ minHeight: 70 }).padding({ left: 12, right: 12 })
                  }
                }
              }, (item: ManagementActivityItem) => item.id.toString())
            }
            .layoutWeight(1).width('100%').scrollBar(BarState.Auto)
            if (this.ui.stale) {
              ManagementActivityStaleNotice({
                refreshing: this.ui.refreshing,
                nativeMaterial: this.useNativeMaterial(),
                onRetry: () => this.vm.loadPage(this.ui.retryPageIndex)
              })
            }
            ManagementActivityPagingControls({
              pageIndex: this.ui.displayedPageIndex,
              pageCount: this.ui.pageCount,
              nativeMaterial: this.useNativeMaterial(),
              secondaryTextColor: this.secondaryTextColor(),
              onPrevious: () => this.vm.loadPage(this.ui.displayedPageIndex - 1),
              onNext: () => this.vm.loadPage(this.ui.displayedPageIndex + 1)
            })
          } else {
            ManagementActivityStateContent({
              status: this.ui.status,
              nativeMaterial: this.useNativeMaterial(),
              secondaryTextColor: this.secondaryTextColor(),
              onRetry: () => this.vm.loadPage(this.ui.retryPageIndex)
            })
          }
        }
        .width('100%')
        .height('100%')
        .padding({ bottom: this.vm.appUIState.safeBottom })
        .backgroundColor(AppThemeSurfaceResolver.routeBackground(
          this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
      } else {
        Column() {
          Blank().height(this.nativeTopSpacer())
          Row({ space: this.isLarge() ? 12 : 0 }) {
            Column() {
              Column({ space: 10 }) {
                Row({ space: 8 }) {
                  Search({ value: this.ui.query, placeholder: $r('app.string.management_activity_search') })
                    .layoutWeight(1)
                    .height(40)
                    .backgroundColor(Color.Transparent)
                    .attributeModifier(AppThemeSurfaceResolver.modifier(
                      AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
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
                      AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
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
                  AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
              }
              .width('100%')
              .padding({ left: 12, right: 12, top: 12, bottom: 10 })
              if ((this.ui.status === ManagementSectionStatus.Content ||
                this.ui.status === ManagementSectionStatus.Error) && this.ui.items.length > 0) {
                List({ scroller: this.listScroller }) {
                  ForEach(this.ui.items, (item: ManagementActivityItem, index: number) => {
                    ListItem() {
                      Column() {
                        Row({ space: 12 }) {
                          Row()
                            .width(8)
                            .height(8)
                            .borderRadius(4)
                            .backgroundColor(this.severityColor((item).severity))
                          Column({ space: 4 }) {
                            Text((item).name)
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
                        .backgroundColor(this.isLarge() && this.selectedActivity()?.id === (item).id ?
                          AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) : Color.Transparent)
                        .onClick(() => this.selectActivity((item)))
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
                if (this.ui.stale) {
                  ManagementActivityStaleNotice({
                    refreshing: this.ui.refreshing,
                    nativeMaterial: this.useNativeMaterial(),
                    onRetry: () => this.vm.loadPage(this.ui.retryPageIndex)
                  })
                }
                ManagementActivityPagingControls({
                  pageIndex: this.ui.displayedPageIndex,
                  pageCount: this.ui.pageCount,
                  nativeMaterial: this.useNativeMaterial(),
                  secondaryTextColor: this.secondaryTextColor(),
                  onPrevious: () => this.vm.loadPage(this.ui.displayedPageIndex - 1),
                  onNext: () => this.vm.loadPage(this.ui.displayedPageIndex + 1)
                })
              } else {
                ManagementActivityStateContent({
                  status: this.ui.status,
                  nativeMaterial: this.useNativeMaterial(),
                  secondaryTextColor: this.secondaryTextColor(),
                  onRetry: () => this.vm.loadPage(this.ui.retryPageIndex)
                })
              }
            }
            .width(this.isLarge() ? 340 : '100%')
            .height('100%')
            if (this.isLarge() && this.selectedActivity()) {
              Scroll(this.detailScroller) {
                Column({ space: 12 }) {
                  ManagementActivityDetailBody({
                    item: this.selectedActivity()!,
                    formattedDate: this.formatDate(this.selectedActivity()!.date),
                    groupBackground: this.detailGroupBackground(false),
                    primaryTextColor: this.primaryTextColor(),
                    secondaryTextColor: this.secondaryTextColor(),
                    severityColor: this.severityColor(this.selectedActivity()!.severity)
                  })
                  ManagementActivityOpenMediaButton({
                    item: this.selectedActivity()!,
                    pending: this.ui.pendingItemId.length > 0,
                    nativeMaterial: this.useNativeMaterial(),
                    onOpen: () => this.openMedia(this.selectedActivity()!)
                  })
                }
                .width('100%')
                .padding({ left: 16, right: 16, top: 12, bottom: this.vm.appUIState.safeBottom + 24 })
              }
              .layoutWeight(1)
              .height('100%')
              .scrollBar(BarState.Auto)
            } else if (this.isLarge()) {
              Blank().layoutWeight(1)
            }
          }
          .layoutWeight(1)
          .width('100%')
          .padding({
            left: 12,
            right: 12,
            bottom: this.isLarge() ? 0 : this.vm.appUIState.safeBottom
          })
        }
        .width('100%')
        .height('100%')
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Transparent)
    .bindSheet($$this.sheetVisible, this.activitySheetBuilder(), this.sheetOptions())
  }
}

@HMRouter({
  pageUrl: RouterConsts.ManagementActivityPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementActivityPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private readonly listScroller: Scroller = new Scroller()
  private readonly detailScroller: Scroller = new Scroller()
  private readonly sheetScroller: Scroller = new Scroller()

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.management_activity_title'),
        contentExtendsUnderTitleBar: true,
        scrollControllers: [this.listScroller, this.detailScroller]
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              ManagementActivityContent({
                showLegacyActionBar: false,
                listScroller: this.listScroller,
                detailScroller: this.detailScroller,
                sheetScroller: this.sheetScroller
              })
            } else {
              ManagementActivityContent({
                showLegacyActionBar: true,
                listScroller: this.listScroller,
                detailScroller: this.detailScroller,
                sheetScroller: this.sheetScroller
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
        ManagementActivityContent({
          showLegacyActionBar: true,
          listScroller: this.listScroller,
          detailScroller: this.detailScroller,
          sheetScroller: this.sheetScroller
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
