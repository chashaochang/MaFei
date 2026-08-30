import {
  HMLifecycleContext,
  HMLifecycleState,
  HMRouter,
  HMRouterMgr,
  IHMLifecycleOwner
} from '@hadss/hmrouter';
import { PageState } from '../../common/base/BaseUIState';
import { CustomRefreshComponent } from '../../common/CustomRefreshComponent';
import { RouterConsts } from '../../common/RouterConsts';
import { UIConstants } from '../../common/UIConstants';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../theme/AppThemeOverlayPolicy';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ManagementNativePagePolicy } from './ManagementNativePagePolicy';
import { ManagementPermissionState, ManagementSectionStatus } from './ManagementModels';
import { ManagementUserDetailPage } from './ManagementUserDetailPage';
import { ManagementUserFilterSheet } from './ManagementUserFilterSheet';
import {
  ManagementUserActionResult,
  ManagementUserItem,
  ManagementUserRoleFilter,
  ManagementUserSortField,
  ManagementUserStatusFilter,
  ManagementUserTab,
  ManagementUserVisibilityFilter
} from './ManagementUserModels';
import { ManagementUserQueryOptions } from './ManagementUserQuery';
import { ManagementUsersUIState } from './ManagementUsersUIState';
import { ManagementUsersViewModel } from './ManagementUsersViewModel';
import { HdsNavDestination, HdsNavDestinationAttribute, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

class ManagementUserMenuElement implements MenuElement {
  value: ResourceStr
  action: () => void
  enabled?: boolean

  constructor(value: ResourceStr, action: () => void, enabled: boolean = true) {
    this.value = value
    this.action = action
    this.enabled = enabled
  }
}

@ComponentV2
struct ManagementUserAvatar {
  @Require @Param user: ManagementUserItem
  @Require @Param fallbackBackground: ResourceColor

  build() {
    if (this.user.avatarUrl) {
      Image(this.user.avatarUrl)
        .width(44)
        .height(44)
        .objectFit(ImageFit.Cover)
        .borderRadius(22)
    } else {
      Stack() {
        SymbolGlyph($r('sys.symbol.person_fill'))
          .fontSize(22)
          .fontColor([$r('app.color.color_main')])
      }
      .width(44)
      .height(44)
      .borderRadius(22)
      .backgroundColor(this.fallbackBackground)
    }
  }
}

@ComponentV2
struct ManagementUsersLoadingRows {
  @Require @Param placeholderColor: ResourceColor

  build() {
    Column() {
      ForEach([0, 1, 2, 3, 4], (_index: number) => {
        Row({ space: 10 }) {
          Row()
            .width(44)
            .height(44)
            .borderRadius(22)
            .backgroundColor(this.placeholderColor)
          Column({ space: 7 }) {
            Row().width('45%').height(12).borderRadius(4).backgroundColor(this.placeholderColor)
            Row().width('68%').height(10).borderRadius(4).backgroundColor(this.placeholderColor)
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
        }
        .width('100%')
        .constraintSize({ minHeight: 72 })
        .padding({ left: 12, right: 12 })
      })
    }
    .width('100%')
  }
}

@ComponentV2
struct ManagementUsersStateView {
  @Require @Param icon: Resource
  @Require @Param title: ResourceStr
  @Param action: ResourceStr = ''
  @Param showAction: boolean = false
  @Param nativeMaterial: boolean = false
  @Require @Param secondaryTextColor: ResourceColor
  @Event onAction: () => void = () => {}

  build() {
    Column({ space: 12 }) {
      SymbolGlyph(this.icon)
        .fontSize(38)
        .fontColor([$r('app.color.color_main')])
      Text(this.title)
        .fontSize(14)
        .fontColor(this.secondaryTextColor)
        .textAlign(TextAlign.Center)
        .maxLines(3)
      if (this.showAction) {
        Button(this.action, { type: ButtonType.Normal })
          .height(40)
          .padding({ left: 20, right: 20 })
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(this.nativeMaterial ? Color.Transparent : $r('app.color.color_main'))
          .borderRadius(20)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.PrimaryInteractive, this.nativeMaterial))
          .onClick(this.onAction)
      }
    }
    .width('100%')
    .layoutWeight(1)
    .padding(24)
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
struct ManagementUsersContent {
  @Param showLegacyActionBar: boolean = false
  @Param contentScroller: Scroller = new Scroller()
  @Local ui: ManagementUsersUIState = new ManagementUsersUIState(this.getUIContext())
  @Local vm: ManagementUsersViewModel = new ManagementUsersViewModel(this.ui)
  private permissionPopTimer: number = -1
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
    this.clearPermissionPopTimer()
    this.vm.stop()
  }

  @Monitor('ui.permissionState')
  onPermissionStateChange(): void {
    this.clearPermissionPopTimer()
    if (this.ui.permissionState !== ManagementPermissionState.Denied) {
      return
    }
    this.getUIContext().getPromptAction().showToast({ message: this.ui.permissionMessage })
    this.permissionPopTimer = setTimeout(() => {
      this.permissionPopTimer = -1
      HMRouterMgr.pop()
    }, 100)
  }

  private clearPermissionPopTimer(): void {
    if (this.permissionPopTimer < 0) {
      return
    }
    clearTimeout(this.permissionPopTimer)
    this.permissionPopTimer = -1
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

  private filterSheetOptions(): SheetOptions {
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
    if (decision === OverlayMaterialDecision.UseBlurFallback) {
      return {
        height: '70%',
        preferType: SheetType.BOTTOM,
        showClose: true,
        backgroundColor: Color.Transparent
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
    return {
      height: '70%',
      preferType: SheetType.BOTTOM,
      showClose: true,
      backgroundColor: $r('app.color.bg_1')
    }
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  // CustomBuilderAdapter: management-users-filter-sheet
  @Builder
  private filterSheetBuilder() {
    ManagementUserFilterSheet({
      initialStatus: this.ui.statusFilter,
      initialVisibility: this.ui.visibilityFilter,
      initialSortField: this.ui.sortField,
      initialAscending: this.ui.ascending,
      onApply: (options: ManagementUserQueryOptions) => {
        this.ui.statusFilter = options.status
        this.ui.visibilityFilter = options.visibility
        this.ui.sortField = options.sortField
        this.ui.ascending = options.ascending
        this.ui.filterSheetVisible = false
      }
    })
  }

  private legacyGroupColor(): ResourceColor {
    return $r('app.color.bg_1')
  }

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private localizedMessage(message: string): string {
    if (!message) {
      return ''
    }
    try {
      return this.getString(message)
    } catch (error) {
      return message
    }
  }

  private roleLabel(filter: ManagementUserRoleFilter): ResourceStr {
    if (filter === ManagementUserRoleFilter.Administrators) {
      return $r('app.string.management_user_filter_administrators')
    }
    if (filter === ManagementUserRoleFilter.Standard) {
      return $r('app.string.management_user_filter_standard')
    }
    return $r('app.string.management_user_filter_all')
  }

  private nativeTopSpacer(): number {
    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  private clearFilters(): void {
    this.ui.query = ''
    this.ui.roleFilter = ManagementUserRoleFilter.All
    this.ui.statusFilter = ManagementUserStatusFilter.All
    this.ui.visibilityFilter = ManagementUserVisibilityFilter.All
    this.ui.sortField = ManagementUserSortField.Name
    this.ui.ascending = true
  }

  private legacyFilterSelected(filter: number): boolean {
    if (filter === 1) {
      return this.ui.roleFilter === ManagementUserRoleFilter.Administrators &&
        this.ui.statusFilter === ManagementUserStatusFilter.All
    }
    if (filter === 2) {
      return this.ui.roleFilter === ManagementUserRoleFilter.Standard &&
        this.ui.statusFilter === ManagementUserStatusFilter.All
    }
    if (filter === 3) {
      return this.ui.roleFilter === ManagementUserRoleFilter.All &&
        this.ui.statusFilter === ManagementUserStatusFilter.Disabled
    }
    if (filter === 4) {
      return this.ui.visibilityFilter === ManagementUserVisibilityFilter.Hidden
    }
    return this.ui.roleFilter === ManagementUserRoleFilter.All &&
      this.ui.statusFilter === ManagementUserStatusFilter.All &&
      this.ui.visibilityFilter === ManagementUserVisibilityFilter.All
  }

  private applyLegacyFilter(filter: number): void {
    this.ui.roleFilter = filter === 1 ? ManagementUserRoleFilter.Administrators :
      (filter === 2 ? ManagementUserRoleFilter.Standard : ManagementUserRoleFilter.All)
    this.ui.statusFilter = filter === 3 ? ManagementUserStatusFilter.Disabled :
      ManagementUserStatusFilter.All
    this.ui.visibilityFilter = filter === 4 ? ManagementUserVisibilityFilter.Hidden :
      ManagementUserVisibilityFilter.All
  }

  private statusText(user: ManagementUserItem): string {
    const values: string[] = [this.getString(user.administrator ?
      'management_user_role_administrator' : 'management_user_role_standard')]
    if (user.disabled) {
      values.push(this.getString('management_user_status_disabled'))
    } else {
      values.push(this.getString('management_user_status_enabled'))
    }
    if (user.hidden) {
      values.push(this.getString('management_user_status_hidden'))
    }
    return values.join(' · ')
  }

  private formatDate(value: string): string {
    if (!value) {
      return this.getString('management_user_never')
    }
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
      return value
    }
    const month = (date.getMonth() + 1).toString().padStart(2, '0')
    const day = date.getDate().toString().padStart(2, '0')
    const hour = date.getHours().toString().padStart(2, '0')
    const minute = date.getMinutes().toString().padStart(2, '0')
    return `${month}-${day} ${hour}:${minute}`
  }

  private activityText(user: ManagementUserItem): string {
    return `${this.getString('management_user_last_login')}: ${this.formatDate(user.lastLoginDate)} · ` +
      `${this.getString('management_user_last_activity')}: ${this.formatDate(user.lastActivityDate)}`
  }

  private selectedUser(): ManagementUserItem | null {
    return this.ui.users.find((user: ManagementUserItem) =>
      user.id === this.ui.selectedUserId) || null
  }

  private openCreate(): void {
    HMRouterMgr.to(RouterConsts.ManagementUserCreatePage).push()
  }

  private openUser(user: ManagementUserItem, initialTab: ManagementUserTab = ManagementUserTab.Profile): void {
    this.vm.selectUser(user.id)
    if (this.isLarge()) {
      return
    }
    HMRouterMgr.to(RouterConsts.ManagementUserDetailPage)
      .withParam({ userId: user.id, initialTab: initialTab })
      .push()
  }

  private deleteProtected(user: ManagementUserItem): boolean {
    if (user.id === this.ui.currentUserId) {
      return true
    }
    return user.administrator && !user.disabled && this.ui.enabledAdministratorCount <= 1
  }

  private userMenuItems(user: ManagementUserItem): MenuElement[] {
    return [
      new ManagementUserMenuElement(
        $r('app.string.management_user_menu_profile'),
        () => this.openUser(user, ManagementUserTab.Profile)),
      new ManagementUserMenuElement(
        $r('app.string.management_user_menu_access'),
        () => this.openUser(user, ManagementUserTab.Access)),
      new ManagementUserMenuElement(
        $r('app.string.management_user_menu_parental'),
        () => this.openUser(user, ManagementUserTab.Parental)),
      new ManagementUserMenuElement(
        $r('app.string.management_user_delete'),
        () => this.confirmDelete(user),
        !this.deleteProtected(user) && this.ui.deletingUserId.length === 0)
    ]
  }

  private deleteProtectionMessage(user: ManagementUserItem): ResourceStr {
    return user.id === this.ui.currentUserId ?
      $r('app.string.management_user_protect_current_admin') :
      $r('app.string.management_user_protect_last_admin')
  }

  private confirmDelete(user: ManagementUserItem): void {
    if (this.deleteProtected(user)) {
      this.getUIContext().getPromptAction().showToast({
        message: this.deleteProtectionMessage(user)
      })
      return
    }
    if (this.ui.deletingUserId.length > 0) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_user_operation_in_progress')
      })
      return
    }
    if (this.dangerousDialogMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_delete_title'),
        message: `${this.getString('management_user_delete_message')} ${user.name}\n\n${
          this.getString('management_user_delete_impact')}`,
        buttons: [
          { text: $r('app.string.management_user_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_user_delete'), color: $r('app.color.main_red_color') }
        ],
        backgroundColor: this.useNativeMaterial() ?
          $r('sys.color.comp_background_primary') : $r('app.color.bg_1'),
        backgroundBlurStyle: BlurStyle.NONE,
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }).then((response) => {
        if (response.index !== 1) {
          return
        }
        this.vm.deleteUser(user.id).then((result: ManagementUserActionResult) => {
          this.getUIContext().getPromptAction().showToast({
            message: result.ok ? $r('app.string.management_user_delete_success') :
              (result.message ? this.localizedMessage(result.message) :
                $r('app.string.management_user_delete_failed'))
          })
        })
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_delete_title'),
        message: `${this.getString('management_user_delete_message')} ${user.name}\n\n${
          this.getString('management_user_delete_impact')}`,
        buttons: [
          { text: $r('app.string.management_user_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_user_delete'), color: $r('app.color.main_red_color') }
        ]
      }).then((response) => {
        if (response.index !== 1) {
          return
        }
        this.vm.deleteUser(user.id).then((result: ManagementUserActionResult) => {
          this.getUIContext().getPromptAction().showToast({
            message: result.ok ? $r('app.string.management_user_delete_success') :
              (result.message ? this.localizedMessage(result.message) :
                $r('app.string.management_user_delete_failed'))
          })
        })
      }).catch(() => {
      })
    }
  }

  @Styles
  pressedRowStyle(): void {
    .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle))
  }

  @Styles
  normalRowStyle(): void {
    .backgroundColor(Color.Transparent)
  }
























  build() {
    Column() {
      if ((this.showLegacyActionBar)) {
        ActionBar({
          title: $r('app.string.management_user_title'),
          showBackButton: true,
          rightBtnIcon: $r('app.media.add'),
          rightBtnAccessibilityText: $r('app.string.management_user_create'),
          onRightBtnClick: () => this.openCreate()
        })
      } else {
        Blank().height(this.nativeTopSpacer())
      }
      if (this.ui.pageState === PageState.Loading) {
        ManagementUsersLoadingRows({
          placeholderColor: this.useNativeMaterial() ?
            AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) :
            $r('app.color.component_bg')
        })
      } else if (this.ui.pageState === PageState.Error) {
        ManagementUsersStateView({
          icon: $r('sys.symbol.exclamationmark_circle'),
          title: this.ui.permissionMessage || $r('app.string.management_permission_check_failed'),
          action: $r('app.string.management_user_retry'),
          showAction: true,
          nativeMaterial: this.useNativeMaterial(),
          secondaryTextColor: this.secondaryTextColor(),
          onAction: () => this.vm.init()
        })
      } else {
        Row({ space: this.isLarge() ? 12 : 0 }) {
          Column() {
            if (this.useNativeMaterial()) {
              Column({ space: 10 }) {
                Row({ space: 8 }) {
                  Search({ value: this.ui.query, placeholder: $r('app.string.management_user_search_placeholder') })
                    .layoutWeight(1)
                    .height(40)
                    .backgroundColor(Color.Transparent)
                    .attributeModifier(AppThemeSurfaceResolver.modifier(
                      AppThemeMaterialRole.AdaptiveControl, true))
                    .onChange((value: string) => this.ui.query = value)
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
                      AppThemeMaterialRole.AdaptiveControl, true))
                    .accessibilityText($r('app.string.management_user_filter_title'))
                    .onClick(() => this.ui.filterSheetVisible = true)
                    if (this.ui.activeAdvancedFilterCount > 0) {
                      Text(this.ui.activeAdvancedFilterCount.toString())
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
                  ForEach([
                    ManagementUserRoleFilter.All,
                    ManagementUserRoleFilter.Administrators,
                    ManagementUserRoleFilter.Standard
                  ], (filter: ManagementUserRoleFilter) => {
                    Button(this.roleLabel(filter), { type: ButtonType.Normal })
                      .layoutWeight(1)
                      .height(36)
                      .fontSize(13)
                      .fontColor(this.ui.roleFilter === filter ? this.primaryTextColor() : this.secondaryTextColor())
                      .backgroundColor(this.ui.roleFilter === filter ?
                        AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) : Color.Transparent)
                      .borderRadius(18)
                      .onClick(() => this.ui.roleFilter = filter)
                  }, (filter: ManagementUserRoleFilter) => filter.toString())
                }
                .width('100%')
                .padding(2)
                .borderRadius(20)
                .attributeModifier(AppThemeSurfaceResolver.modifier(
                  AppThemeMaterialRole.AdaptiveControl, true))
              }
              .width('100%')
              .padding({ left: 12, right: 12, top: 12, bottom: 10 })
            } else {
              Column({ space: 10 }) {
                Search({ value: this.ui.query, placeholder: $r('app.string.management_user_search_placeholder') })
                  .width('100%')
                  .height(40)
                  .backgroundColor($r('app.color.bg_main'))
                  .borderRadius(10)
                  .onChange((value: string) => this.ui.query = value)
                List({ space: 8 }) {
                  ForEach([0, 1, 2, 3, 4], (filter: number) => {
                    ListItem() {
                      Button(filter === 1 ? $r('app.string.management_user_filter_administrators') :
                        (filter === 2 ? $r('app.string.management_user_filter_standard') :
                          (filter === 3 ? $r('app.string.management_user_filter_disabled') :
                            (filter === 4 ? $r('app.string.management_user_filter_hidden') :
                              $r('app.string.management_user_filter_all')))), { type: ButtonType.Normal })
                        .height(34)
                        .padding({ left: 13, right: 13 })
                        .fontSize(13)
                        .fontColor(this.legacyFilterSelected(filter) ? $r('app.color.color_main') :
                          this.secondaryTextColor())
                        .backgroundColor(this.legacyFilterSelected(filter) ?
                          AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) : Color.Transparent)
                        .borderRadius(8)
                        .onClick(() => this.applyLegacyFilter(filter))
                    }
                  })
                }
                .width('100%')
                .height(36)
                .listDirection(Axis.Horizontal)
                .scrollBar(BarState.Off)
              }
              .width('100%')
              .padding({ left: 12, right: 12, top: 12, bottom: 10 })
            }
            if (this.ui.hasStaleData) {
              Row({ space: 8 }) {
                SymbolGlyph($r('sys.symbol.exclamationmark_triangle'))
                  .fontSize(16)
                  .fontColor([$r('app.color.status_warning')])
                Text($r('app.string.management_user_stale'))
                  .fontSize(12)
                  .fontColor(this.secondaryTextColor())
                  .layoutWeight(1)
                Button($r('app.string.management_user_retry'), { type: ButtonType.Normal })
                  .height(32)
                  .borderRadius(16)
                  .fontSize(12)
                  .fontColor($r('app.color.color_main'))
                  .backgroundColor(Color.Transparent)
                  .attributeModifier(AppThemeSurfaceResolver.modifier(
                    AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
                  .onClick(() => this.vm.refresh())
              }
              .width('100%')
              .padding({ left: 12, right: 12, top: 4, bottom: 4 })
            }
            Refresh({ refreshing: $$this.ui.isRefreshing }) {
              if (this.ui.status === ManagementSectionStatus.Loading && this.ui.users.length === 0) {
                ManagementUsersLoadingRows({
                  placeholderColor: this.useNativeMaterial() ?
                    AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) :
                    $r('app.color.component_bg')
                })
              } else if (this.ui.status === ManagementSectionStatus.Error && this.ui.users.length === 0) {
                ManagementUsersStateView({
                  icon: $r('sys.symbol.exclamationmark_circle'),
                  title: $r('app.string.management_user_load_failed'),
                  action: $r('app.string.management_user_retry'),
                  showAction: true,
                  nativeMaterial: this.useNativeMaterial(),
                  secondaryTextColor: this.secondaryTextColor(),
                  onAction: () => this.vm.refresh(false)
                })
              } else if (this.ui.users.length === 0) {
                ManagementUsersStateView({
                  icon: $r('sys.symbol.person_2'),
                  title: $r('app.string.management_user_empty'),
                  action: $r('app.string.management_user_create'),
                  showAction: true,
                  nativeMaterial: this.useNativeMaterial(),
                  secondaryTextColor: this.secondaryTextColor(),
                  onAction: () => this.openCreate()
                })
              } else if (this.ui.filteredUsers.length === 0) {
                ManagementUsersStateView({
                  icon: $r('sys.symbol.magnifyingglass'),
                  title: $r('app.string.management_user_no_results'),
                  action: $r('app.string.management_user_clear_filters'),
                  showAction: true,
                  nativeMaterial: this.useNativeMaterial(),
                  secondaryTextColor: this.secondaryTextColor(),
                  onAction: () => {
                    this.clearFilters()
                  }
                })
              } else {
                List({ scroller: this.contentScroller }) {
                  ForEach(this.ui.filteredUsers, (user: ManagementUserItem) => {
                    ListItem() {
                      if (this.useNativeMaterial()) {
                        Row({ space: 12 }) {
                          ManagementUserAvatar({
                            user: user,
                            fallbackBackground: AppThemeSurfaceResolver.interactionOverlay(
                              this.vm.appUIState.themeStyle)
                          })
                          Column({ space: 3 }) {
                            Text(((user)).name)
                              .fontSize(16)
                              .fontWeight(FontWeight.Medium)
                              .fontColor(this.primaryTextColor())
                              .maxLines(1)
                              .textOverflow({ overflow: TextOverflow.Ellipsis })
                            Text(this.statusText(((user))))
                              .fontSize(13)
                              .fontColor(this.secondaryTextColor())
                              .maxLines(1)
                              .textOverflow({ overflow: TextOverflow.Ellipsis })
                          }
                          .layoutWeight(1)
                          .alignItems(HorizontalAlign.Start)
                          SymbolGlyph(((user)).hasPassword ? $r('sys.symbol.lock_fill') : $r('sys.symbol.lock_open'))
                            .fontSize(16)
                            .fontColor([((user)).hasPassword ? $r('sys.color.icon_tertiary') : $r('app.color.status_warning')])
                            .accessibilityText(((user)).hasPassword ?
                              $r('app.string.management_user_password_set') :
                              $r('app.string.management_user_password_not_set'))
                          SymbolGlyph($r('sys.symbol.chevron_right'))
                            .fontSize(20)
                            .fontColor([$r('sys.color.icon_tertiary')])
                        }
                        .width('100%')
                        .constraintSize({ minHeight: 64 })
                        .padding({ left: 12, right: 12, top: 8, bottom: 8 })
                        .stateStyles({ pressed: this.pressedRowStyle, normal: this.normalRowStyle })
                        .onClick(() => this.openUser(((user))))
                      } else {
                        Row({ space: 10 }) {
                          ManagementUserAvatar({
                            user: user,
                            fallbackBackground: AppThemeSurfaceResolver.interactionOverlay(
                              this.vm.appUIState.themeStyle)
                          })
                          Column({ space: 2 }) {
                            Text(((user)).name)
                              .fontSize(15)
                              .fontWeight(FontWeight.Medium)
                              .fontColor(this.primaryTextColor())
                              .maxLines(1)
                              .textOverflow({ overflow: TextOverflow.Ellipsis })
                            Text(this.statusText(((user))))
                              .fontSize(12)
                              .fontColor(this.secondaryTextColor())
                              .maxLines(1)
                              .textOverflow({ overflow: TextOverflow.Ellipsis })
                            Text(this.activityText(((user))))
                              .fontSize(10)
                              .fontColor(this.secondaryTextColor())
                              .maxLines(1)
                              .textOverflow({ overflow: TextOverflow.Ellipsis })
                          }
                          .layoutWeight(1)
                          .alignItems(HorizontalAlign.Start)
                          SymbolGlyph(((user)).hasPassword ? $r('sys.symbol.lock_fill') : $r('sys.symbol.lock_open'))
                            .fontSize(15)
                            .fontColor([((user)).hasPassword ? this.secondaryTextColor() : $r('app.color.status_warning')])
                            .accessibilityText(((user)).hasPassword ?
                              $r('app.string.management_user_password_set') :
                              $r('app.string.management_user_password_not_set'))
                          Button() {
                            SymbolGlyph($r('sys.symbol.more'))
                              .fontSize(17)
                              .fontColor([this.secondaryTextColor()])
                          }
                          .width(40)
                          .height(48)
                          .borderRadius(10)
                          .backgroundColor(Color.Transparent)
                          .accessibilityText($r('app.string.management_user_more_actions'))
                          .bindMenu(this.userMenuItems(user))
                          SymbolGlyph($r('sys.symbol.chevron_right'))
                            .fontSize(12)
                            .fontColor([this.secondaryTextColor()])
                        }
                        .width('100%')
                        .constraintSize({ minHeight: 72 })
                        .padding({ left: 12, right: 8, top: 8, bottom: 8 })
                        .borderRadius(8)
                        .stateStyles({ pressed: this.pressedRowStyle, normal: this.normalRowStyle })
                        .onClick(() => this.openUser(((user))))
                      }
                    }
                  }, (user: ManagementUserItem) => user.id)
                }
                .width('100%')
                .height('100%')
                .scrollBar(BarState.Auto)
                .cachedCount(10)
              }
            }
            .layoutWeight(1)
            .pullDownRatio(0.3)
            .refreshOffset(56)
            .maxPullDownDistance(64)
            .onStateChange((status: RefreshStatus) => {
              this.ui.refreshStatus = status
            })
            .onOffsetChange((offset: number) => {
              this.ui.refreshOffset = offset
            })
            .onRefreshing(() => this.vm.refresh())
          }
          .width(((this.isLarge() ? 300 : '100%')))
          .height('100%')
          .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
          .clip(true)
          .backgroundColor(this.useNativeMaterial() ?
            AppThemeSurfaceResolver.contentGroupBackground() : this.legacyGroupColor())
          if (this.isLarge()) {
            Column() {
              if (this.selectedUser()) {
                ManagementUserDetailPage({
                  embedded: true,
                  embeddedUserId: this.selectedUser()!.id,
                  embeddedInitialTab: ManagementUserTab.Profile,
                  onDeleted: (_userId: string) => {
                    this.ui.selectedUserId = ''
                    this.vm.refresh(false)
                  }
                })
                .width('100%')
                .height('100%')
              } else {
                ManagementUsersStateView({
                  icon: $r('sys.symbol.person_2'),
                  title: $r('app.string.management_user_select_hint'),
                  showAction: false,
                  nativeMaterial: this.useNativeMaterial(),
                  secondaryTextColor: this.secondaryTextColor()
                })
              }
            }
            .layoutWeight(1)
            .height('100%')
            .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
            .clip(true)
            .backgroundColor(this.useNativeMaterial() ? Color.Transparent : this.legacyGroupColor())
          }
        }
        .layoutWeight(1)
        .width('100%')
        .padding({
          left: this.isLarge() ? 16 : 12,
          right: this.isLarge() ? 16 : 12,
          top: this.isLarge() ? 12 : 8,
          bottom: this.vm.appUIState.safeBottom + (this.isLarge() ? 16 : 12)
        })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor((this.showLegacyActionBar) ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
    .bindSheet($$this.ui.filterSheetVisible, this.filterSheetBuilder(), this.filterSheetOptions())
  }
}

@HMRouter({
  pageUrl: RouterConsts.ManagementUsersPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementUsersPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private readonly contentScroller: Scroller = new Scroller()

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.management_user_title'),
        contentExtendsUnderTitleBar: true,
        menus: [{
                value: $r('app.string.management_user_create'),
                icon: $r('sys.symbol.plus'),
                action: () => HMRouterMgr.to(RouterConsts.ManagementUserCreatePage).push()
              }],
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
              ManagementUsersContent({
                showLegacyActionBar: false,
                contentScroller: this.contentScroller
              })
            } else {
              ManagementUsersContent({
                showLegacyActionBar: true,
                contentScroller: this.contentScroller
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
        ManagementUsersContent({
          showLegacyActionBar: true,
          contentScroller: this.contentScroller
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
