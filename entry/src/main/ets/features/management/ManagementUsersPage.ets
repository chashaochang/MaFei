import {
  HMLifecycleContext,
  HMLifecycleState,
  HMRouter,
  HMRouterMgr,
  IHMLifecycleOwner
} from '@hadss/hmrouter';
import { deviceInfo } from '@kit.BasicServicesKit';
import { PageState } from '../../common/base/BaseUIState';
import { CustomRefreshComponent } from '../../common/CustomRefreshComponent';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ManagementPermissionState, ManagementSectionStatus } from './ManagementModels';
import { ManagementUserDetailPage } from './ManagementUserDetailPage';
import {
  ManagementUserActionResult,
  ManagementUserFilter,
  ManagementUserItem,
  ManagementUserTab
} from './ManagementUserModels';
import { ManagementUsersUIState } from './ManagementUsersUIState';
import { ManagementUsersViewModel } from './ManagementUsersViewModel';

@HMRouter({
  pageUrl: RouterConsts.ManagementUsersPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementUsersPage {
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

  private filterLabel(filter: ManagementUserFilter): ResourceStr {
    if (filter === ManagementUserFilter.Administrators) {
      return $r('app.string.management_user_filter_administrators')
    }
    if (filter === ManagementUserFilter.Standard) {
      return $r('app.string.management_user_filter_standard')
    }
    if (filter === ManagementUserFilter.Disabled) {
      return $r('app.string.management_user_filter_disabled')
    }
    if (filter === ManagementUserFilter.Hidden) {
      return $r('app.string.management_user_filter_hidden')
    }
    return $r('app.string.management_user_filter_all')
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
    if (deviceInfo.sdkApiVersion >= 26) {
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

  @Builder
  searchAndFilters() {
    Column({ space: 10 }) {
      Row({ space: 8 }) {
        Search({
          value: this.ui.query,
          placeholder: $r('app.string.management_user_search_placeholder')
        })
          .layoutWeight(1)
          .height(40)
          .backgroundColor(this.useNativeMaterial() ?
            AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) :
            $r('app.color.bg_main'))
          .borderRadius(10)
          .onChange((value: string) => {
            this.ui.query = value
          })
        if (this.useNativeMaterial()) {
          Button() {
            SymbolGlyph($r('sys.symbol.plus'))
              .fontSize(20)
              .fontColor([$r('app.color.color_main')])
          }
          .width(40)
          .height(40)
          .borderRadius(10)
          .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle))
          .accessibilityText($r('app.string.management_user_create'))
          .onClick(() => this.openCreate())
        }
      }
      .width('100%')
      .height(40)
      List({ space: 8 }) {
        ForEach([
          ManagementUserFilter.All,
          ManagementUserFilter.Administrators,
          ManagementUserFilter.Standard,
          ManagementUserFilter.Disabled,
          ManagementUserFilter.Hidden
        ], (filter: ManagementUserFilter) => {
          ListItem() {
            Button(this.filterLabel(filter), { type: ButtonType.Normal })
              .height(34)
              .padding({ left: 13, right: 13 })
              .fontSize(13)
              .fontColor(this.ui.filter === filter ? $r('app.color.color_main') :
                this.secondaryTextColor())
              .backgroundColor(this.ui.filter === filter ?
                AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) :
                Color.Transparent)
              .borderRadius(8)
              .onClick(() => {
                this.ui.filter = filter
              })
          }
        }, (filter: ManagementUserFilter) => filter.toString())
      }
      .width('100%')
      .height(36)
      .listDirection(Axis.Horizontal)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .padding({ left: 12, right: 12, top: 12, bottom: 10 })
  }

  @Builder
  userMenu(user: ManagementUserItem) {
    Menu() {
      MenuItem({ content: $r('app.string.management_user_menu_profile') })
        .onClick(() => this.openUser(user, ManagementUserTab.Profile))
      MenuItem({ content: $r('app.string.management_user_menu_access') })
        .onClick(() => this.openUser(user, ManagementUserTab.Access))
      MenuItem({ content: $r('app.string.management_user_menu_parental') })
        .onClick(() => this.openUser(user, ManagementUserTab.Parental))
      MenuItem({ content: $r('app.string.management_user_delete') })
        .enabled(!this.deleteProtected(user) && this.ui.deletingUserId.length === 0)
        .onClick(() => this.confirmDelete(user))
    }
  }

  @Builder
  avatar(user: ManagementUserItem) {
    if (user.avatarUrl) {
      Image(user.avatarUrl)
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
      .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle))
    }
  }

  @Builder
  userRow(user: ManagementUserItem) {
    Row({ space: 10 }) {
      this.avatar(user)
      Column({ space: 2 }) {
        Text(user.name)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.statusText(user))
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.activityText(user))
          .fontSize(10)
          .fontColor(this.secondaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      SymbolGlyph(user.hasPassword ? $r('sys.symbol.lock_fill') : $r('sys.symbol.lock_open'))
        .fontSize(15)
        .fontColor([user.hasPassword ? this.secondaryTextColor() : $r('app.color.status_warning')])
        .accessibilityText(user.hasPassword ?
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
      .bindMenu(this.userMenu(user))
      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(12)
        .fontColor([this.secondaryTextColor()])
    }
    .width('100%')
    .constraintSize({ minHeight: 72 })
    .padding({ left: 12, right: 8, top: 8, bottom: 8 })
    .borderRadius(8)
    .stateStyles({ pressed: this.pressedRowStyle, normal: this.normalRowStyle })
    .onClick(() => this.openUser(user))
  }

  @Builder
  loadingRows() {
    Column() {
      ForEach([0, 1, 2, 3, 4], (_index: number) => {
        Row({ space: 10 }) {
          Row()
            .width(44)
            .height(44)
            .borderRadius(22)
            .backgroundColor(this.useNativeMaterial() ?
              AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) :
              $r('app.color.component_bg'))
          Column({ space: 7 }) {
            Row().width('45%').height(12).borderRadius(4)
              .backgroundColor(this.useNativeMaterial() ?
                AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) :
                $r('app.color.component_bg'))
            Row().width('68%').height(10).borderRadius(4)
              .backgroundColor(this.useNativeMaterial() ?
                AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) :
                $r('app.color.component_bg'))
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

  @Builder
  stateView(icon: Resource, title: ResourceStr, action?: ResourceStr, onAction?: () => void) {
    Column({ space: 12 }) {
      SymbolGlyph(icon)
        .fontSize(38)
        .fontColor([$r('app.color.color_main')])
      Text(title)
        .fontSize(14)
        .fontColor(this.secondaryTextColor())
        .textAlign(TextAlign.Center)
        .maxLines(3)
      if (action && onAction) {
        Button(action, { type: ButtonType.Normal })
          .height(40)
          .padding({ left: 20, right: 20 })
          .fontColor($r('app.color.on_primary'))
          .backgroundColor($r('app.color.color_main'))
          .borderRadius(10)
          .onClick(onAction)
      }
    }
    .width('100%')
    .layoutWeight(1)
    .padding(24)
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  refreshBuilder() {
    CustomRefreshComponent({
      refreshOffset: this.ui.refreshOffset,
      refreshStatus: this.ui.refreshStatus,
      enableSafeTop: false
    })
  }

  @Builder
  userListContent() {
    if (this.ui.status === ManagementSectionStatus.Loading && this.ui.users.length === 0) {
      this.loadingRows()
    } else if (this.ui.status === ManagementSectionStatus.Error && this.ui.users.length === 0) {
      this.stateView($r('sys.symbol.exclamationmark_circle'),
        $r('app.string.management_user_load_failed'),
        $r('app.string.management_user_retry'), () => this.vm.refresh(false))
    } else if (this.ui.users.length === 0) {
      this.stateView($r('sys.symbol.person_2'), $r('app.string.management_user_empty'),
        $r('app.string.management_user_create'), () => this.openCreate())
    } else if (this.ui.filteredUsers.length === 0) {
      this.stateView($r('sys.symbol.magnifyingglass'),
        $r('app.string.management_user_no_results'),
        $r('app.string.management_user_clear_filters'), () => {
          this.ui.query = ''
          this.ui.filter = ManagementUserFilter.All
        })
    } else {
      List() {
        ForEach(this.ui.filteredUsers, (user: ManagementUserItem) => {
          ListItem() {
            this.userRow(user)
          }
        }, (user: ManagementUserItem) => user.id)
      }
      .width('100%')
      .height('100%')
      .scrollBar(BarState.Auto)
      .cachedCount(10)
    }
  }

  @Builder
  refreshableList() {
    Refresh({ refreshing: $$this.ui.isRefreshing, builder: this.refreshBuilder() }) {
      this.userListContent()
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

  @Builder
  staleBanner() {
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
          .fontSize(12)
          .fontColor($r('app.color.color_main'))
          .backgroundColor(Color.Transparent)
          .onClick(() => this.vm.refresh())
      }
      .width('100%')
      .padding({ left: 12, right: 12, top: 4, bottom: 4 })
    }
  }

  @Builder
  paneContent() {
    this.searchAndFilters()
    this.staleBanner()
    this.refreshableList()
  }

  @Builder
  nativeUserPane(width: Length) {
    Column() {
      this.paneContent()
    }
    .width(width)
    .height('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  feiniuUserPane(width: Length) {
    Column() {
      this.paneContent()
    }
    .width(width)
    .height('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .backgroundColor(this.legacyGroupColor())
  }

  @Builder
  userPane(width: Length) {
    if (this.useNativeMaterial()) {
      this.nativeUserPane(width)
    } else {
      this.feiniuUserPane(width)
    }
  }

  @Builder
  detailHostContent() {
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
      this.stateView($r('sys.symbol.person_2'), $r('app.string.management_user_select_hint'))
    }
  }

  @Builder
  detailHost() {
    if (this.useNativeMaterial()) {
      Column() {
        this.detailHostContent()
      }
      .layoutWeight(1)
      .height('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .clip(true)
      .backgroundColor(Color.Transparent)
    } else {
      Column() {
        this.detailHostContent()
      }
      .layoutWeight(1)
      .height('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .backgroundColor(this.legacyGroupColor())
    }
  }

  @Builder
  accessErrorView() {
    this.stateView($r('sys.symbol.exclamationmark_circle'),
      this.ui.permissionMessage || $r('app.string.management_permission_check_failed'),
      $r('app.string.management_user_retry'), () => this.vm.init())
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({
          title: $r('app.string.management_user_title'),
          showBackButton: true,
          rightBtnIcon: $r('app.media.add'),
          rightBtnAccessibilityText: $r('app.string.management_user_create'),
          onRightBtnClick: () => this.openCreate()
        })
      }
      if (this.ui.pageState === PageState.Loading) {
        this.loadingRows()
      } else if (this.ui.pageState === PageState.Error) {
        this.accessErrorView()
      } else if (this.isLarge()) {
        Row({ space: 12 }) {
          this.userPane(300)
          this.detailHost()
        }
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 16, right: 16, top: 12, bottom: this.vm.appUIState.safeBottom + 16 })
      } else {
        Column() {
          this.userPane('100%')
        }
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 12, right: 12, top: 8, bottom: this.vm.appUIState.safeBottom + 12 })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_user_title'),
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
