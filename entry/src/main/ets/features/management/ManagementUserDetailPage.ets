import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { deviceInfo } from '@kit.BasicServicesKit';
import { PageState } from '../../common/base/BaseUIState';
import { RouterConsts } from '../../common/RouterConsts';
import { UIConstants } from '../../common/UIConstants';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
import {
  AppThemeMaterialRole,
  AppThemeSurfaceResolver
} from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ManagementNativePagePolicy } from './ManagementNativePagePolicy';
import { ManagementPermissionState } from './ManagementModels';
import { ManagementUserAccessSection } from './ManagementUserAccessSection';
import { ManagementUserDetailUIState } from './ManagementUserDetailUIState';
import { ManagementUserDetailViewModel } from './ManagementUserDetailViewModel';
import {
  ManagementUserActionResult,
  ManagementUserOperationStatus,
  ManagementUserTab
} from './ManagementUserModels';
import { ManagementUserParentalSection } from './ManagementUserParentalSection';
import { ManagementUserPasswordSection } from './ManagementUserPasswordSection';
import { ManagementUserProfileSection } from './ManagementUserProfileSection';

@HMRouter({
  pageUrl: RouterConsts.ManagementUserDetailPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementUserDetailPage {
  @Param embedded: boolean = false
  @Param embeddedUserId: string = ''
  @Param embeddedInitialTab: ManagementUserTab = ManagementUserTab.Profile
  @Local ui: ManagementUserDetailUIState = new ManagementUserDetailUIState(this.getUIContext())
  @Local vm: ManagementUserDetailViewModel = new ManagementUserDetailViewModel(this.ui)
  private readonly contentScroller: Scroller = new Scroller()
  private readonly tabsController: TabsController = new TabsController()
  @Event onDeleted: (userId: string) => void = (_userId: string) => {
  }

  aboutToAppear(): void {
    if (this.embedded) {
      this.vm.init(this.embeddedUserId, this.embeddedInitialTab)
      return
    }
    const param = HMRouterMgr.getCurrentParam() as object
    const userId = param ? param['userId'] as string : ''
    const initialTabValue = param ? param['initialTab'] as number : ManagementUserTab.Profile
    const initialTab = initialTabValue >= ManagementUserTab.Profile &&
      initialTabValue <= ManagementUserTab.Password ? initialTabValue : ManagementUserTab.Profile
    this.vm.init(typeof userId === 'string' ? userId : '', initialTab)
  }

  aboutToDisappear(): void {
    this.vm.stop()
  }

  @Monitor('embeddedUserId')
  onEmbeddedUserChange(): void {
    if (this.embedded && this.embeddedUserId.length > 0 &&
      this.embeddedUserId !== this.ui.userId) {
      this.attemptLeave(() => this.vm.init(this.embeddedUserId, this.embeddedInitialTab))
    }
  }

  @Monitor('ui.permissionState')
  onPermissionStateChange(): void {
    if (this.embedded || this.ui.permissionState !== ManagementPermissionState.Denied) {
      return
    }
    this.getUIContext().getPromptAction().showToast({
      message: this.localizedMessage(this.ui.permissionMessage)
    })
    HMRouterMgr.pop()
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

  private tabLabel(tab: ManagementUserTab): ResourceStr {
    if (tab === ManagementUserTab.Access) {
      return $r('app.string.management_user_tab_access')
    }
    if (tab === ManagementUserTab.Parental) {
      return $r('app.string.management_user_tab_parental')
    }
    if (tab === ManagementUserTab.Password) {
      return $r('app.string.management_user_tab_password')
    }
    return $r('app.string.management_user_tab_profile')
  }

  private managementTabIndex(tab: ManagementUserTab): number {
    return tab === ManagementUserTab.Access ? 1 :
      (tab === ManagementUserTab.Parental ? 2 :
        (tab === ManagementUserTab.Password ? 3 : 0))
  }

  private tabFromIndex(index: number): ManagementUserTab {
    return index === 1 ? ManagementUserTab.Access :
      (index === 2 ? ManagementUserTab.Parental :
        (index === 3 ? ManagementUserTab.Password : ManagementUserTab.Profile))
  }

  private nativeTopSpacer(): number {
    return this.embedded ? 0 : Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  private currentStatus(): ManagementUserOperationStatus {
    if (this.ui.selectedTab === ManagementUserTab.Access) {
      return this.ui.accessStatus
    }
    if (this.ui.selectedTab === ManagementUserTab.Parental) {
      return this.ui.parentalStatus
    }
    if (this.ui.selectedTab === ManagementUserTab.Password) {
      return this.ui.passwordStatus
    }
    return this.ui.profileStatus
  }

  private showSaveBar(): boolean {
    return this.ui.currentTabDirty && this.ui.selectedTab !== ManagementUserTab.Password
  }

  private showResult(result: ManagementUserActionResult, successMessage: ResourceStr): void {
    if (result.ok) {
      this.getUIContext().getPromptAction().showToast({ message: successMessage })
    } else if (result.message) {
      this.getUIContext().getPromptAction().showToast({
        message: this.localizedMessage(result.message)
      })
    }
  }

  private async setPassword(): Promise<void> {
    const result = await this.vm.setPassword()
    this.showResult(result, $r('app.string.management_user_password_updated'))
  }

  private confirmResetPassword(): void {
    if (deviceInfo.sdkApiVersion >= 26) {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_reset_password_title'),
        message: $r('app.string.management_user_reset_password_message'),
        buttons: [
          { text: $r('app.string.management_user_continue_editing'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_user_reset_password'), color: $r('app.color.main_red_color') }
        ],
        backgroundColor: this.useNativeMaterial() ?
          $r('sys.color.comp_background_primary') : $r('app.color.bg_1'),
        backgroundBlurStyle: BlurStyle.NONE,
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }).then((response) => {
        if (response.index === 1) {
          this.vm.resetPassword().then((result: ManagementUserActionResult) => {
            this.showResult(result, $r('app.string.management_user_password_reset_success'))
          })
        }
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_reset_password_title'),
        message: $r('app.string.management_user_reset_password_message'),
        buttons: [
          { text: $r('app.string.management_user_continue_editing'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_user_reset_password'), color: $r('app.color.main_red_color') }
        ]
      }).then((response) => {
        if (response.index === 1) {
          this.vm.resetPassword().then((result: ManagementUserActionResult) => {
            this.showResult(result, $r('app.string.management_user_password_reset_success'))
          })
        }
      }).catch(() => {
      })
    }
  }

  private confirmClearPassword(): void {
    if (deviceInfo.sdkApiVersion >= 26) {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_clear_password_title'),
        message: $r('app.string.management_user_clear_password_message'),
        buttons: [
          { text: $r('app.string.management_user_continue_editing'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_user_clear_password'), color: $r('app.color.main_red_color') }
        ],
        backgroundColor: this.useNativeMaterial() ?
          $r('sys.color.comp_background_primary') : $r('app.color.bg_1'),
        backgroundBlurStyle: BlurStyle.NONE,
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }).then((response) => {
        if (response.index === 1) {
          this.vm.clearPassword().then((result: ManagementUserActionResult) => {
            this.showResult(result, $r('app.string.management_user_password_clear_success'))
          })
        }
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_clear_password_title'),
        message: $r('app.string.management_user_clear_password_message'),
        buttons: [
          { text: $r('app.string.management_user_continue_editing'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_user_clear_password'), color: $r('app.color.main_red_color') }
        ]
      }).then((response) => {
        if (response.index === 1) {
          this.vm.clearPassword().then((result: ManagementUserActionResult) => {
            this.showResult(result, $r('app.string.management_user_password_clear_success'))
          })
        }
      }).catch(() => {
      })
    }
  }

  private async updateAvatar(): Promise<void> {
    const result = await this.vm.selectAvatar()
    if (result.ok || result.message) {
      this.showResult(result, $r('app.string.management_user_avatar_updated'))
    }
  }

  private confirmRemoveAvatar(): void {
    if (deviceInfo.sdkApiVersion >= 26) {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_remove_avatar_title'),
        message: $r('app.string.management_user_remove_avatar_message'),
        buttons: [
          { text: $r('app.string.management_user_continue_editing'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_user_remove_avatar'), color: $r('app.color.main_red_color') }
        ],
        backgroundColor: this.useNativeMaterial() ?
          $r('sys.color.comp_background_primary') : $r('app.color.bg_1'),
        backgroundBlurStyle: BlurStyle.NONE,
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }).then((response) => {
        if (response.index === 1) {
          this.vm.removeAvatar().then((result: ManagementUserActionResult) => {
            this.showResult(result, $r('app.string.management_user_avatar_removed'))
          })
        }
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_remove_avatar_title'),
        message: $r('app.string.management_user_remove_avatar_message'),
        buttons: [
          { text: $r('app.string.management_user_continue_editing'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_user_remove_avatar'), color: $r('app.color.main_red_color') }
        ]
      }).then((response) => {
        if (response.index === 1) {
          this.vm.removeAvatar().then((result: ManagementUserActionResult) => {
            this.showResult(result, $r('app.string.management_user_avatar_removed'))
          })
        }
      }).catch(() => {
      })
    }
  }

  private deleteProtected(): boolean {
    if (this.ui.userId === this.ui.currentUserId) {
      return true
    }
    return this.ui.user.administrator && !this.ui.user.disabled &&
      this.ui.enabledAdministratorCount <= 1
  }

  private deleteProtectionMessage(): ResourceStr {
    return this.ui.userId === this.ui.currentUserId ?
      $r('app.string.management_user_protect_current_admin') :
      $r('app.string.management_user_protect_last_admin')
  }

  private requestDelete(): void {
    if (this.deleteProtected()) {
      this.getUIContext().getPromptAction().showToast({
        message: this.deleteProtectionMessage()
      })
      return
    }
    if (this.vm.isSaving()) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_user_operation_in_progress')
      })
      return
    }
    if (this.ui.currentTabDirty) {
      this.showUnsavedDialog(() => this.confirmDelete())
      return
    }
    this.confirmDelete()
  }

  private confirmDelete(): void {
    if (deviceInfo.sdkApiVersion >= 26) {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_delete_title'),
        message: `${this.getString('management_user_delete_message')} ${this.ui.userName}\n\n${
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
        this.vm.deleteUser().then((result: ManagementUserActionResult) => {
          if (!result.ok) {
            this.showResult(result, $r('app.string.management_user_delete_success'))
            return
          }
          this.getUIContext().getPromptAction().showToast({
            message: $r('app.string.management_user_delete_success')
          })
          const deletedUserId = this.ui.userId
          if (this.embedded) {
            this.onDeleted(deletedUserId)
          } else {
            HMRouterMgr.pop()
          }
        })
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_delete_title'),
        message: `${this.getString('management_user_delete_message')} ${this.ui.userName}\n\n${
          this.getString('management_user_delete_impact')}`,
        buttons: [
          { text: $r('app.string.management_user_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_user_delete'), color: $r('app.color.main_red_color') }
        ]
      }).then((response) => {
        if (response.index !== 1) {
          return
        }
        this.vm.deleteUser().then((result: ManagementUserActionResult) => {
          if (!result.ok) {
            this.showResult(result, $r('app.string.management_user_delete_success'))
            return
          }
          this.getUIContext().getPromptAction().showToast({
            message: $r('app.string.management_user_delete_success')
          })
          const deletedUserId = this.ui.userId
          if (this.embedded) {
            this.onDeleted(deletedUserId)
          } else {
            HMRouterMgr.pop()
          }
        })
      }).catch(() => {
      })
    }
  }

  private selectTab(tab: ManagementUserTab): void {
    if (this.vm.isSaving()) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_user_operation_in_progress')
      })
      return
    }
    if (tab === this.ui.selectedTab) {
      return
    }
    if (!this.ui.currentTabDirty) {
      this.vm.selectTab(tab)
      this.tabsController.changeIndex(this.managementTabIndex(tab))
      return
    }
    this.showUnsavedDialog(() => {
      this.vm.selectTab(tab)
      this.tabsController.changeIndex(this.managementTabIndex(tab))
    })
  }

  private selectSystemTab(index: number): void {
    const currentIndex = this.managementTabIndex(this.ui.selectedTab)
    if (index === currentIndex) {
      return
    }
    this.tabsController.changeIndex(currentIndex)
    this.selectTab(this.tabFromIndex(index))
  }

  private attemptLeave(onLeave: () => void): void {
    if (this.vm.isSaving()) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_user_operation_in_progress')
      })
      return
    }
    if (this.vm.canLeave()) {
      onLeave()
      return
    }
    this.showUnsavedDialog(onLeave)
  }

  private handleRouteBack(): boolean {
    this.attemptLeave(() => HMRouterMgr.pop())
    return true
  }

  private showUnsavedDialog(onContinue: () => void): void {
    if (deviceInfo.sdkApiVersion >= 26) {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_unsaved_title'),
        message: $r('app.string.management_user_unsaved_message'),
        buttons: [
          { text: $r('app.string.management_user_discard'), color: $r('app.color.main_red_color') },
          { text: $r('app.string.management_user_continue_editing'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_user_save'), color: $r('app.color.color_main') }
        ],
        backgroundColor: this.useNativeMaterial() ?
          $r('sys.color.comp_background_primary') : $r('app.color.bg_1'),
        backgroundBlurStyle: BlurStyle.NONE,
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }).then((response) => {
        if (response.index === 0) {
          this.vm.discardCurrentTab()
          onContinue()
        } else if (response.index === 2) {
          this.vm.saveCurrentTab().then((result: ManagementUserActionResult) => {
            if (result.ok) {
              onContinue()
            }
          })
        }
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_user_unsaved_title'),
        message: $r('app.string.management_user_unsaved_message'),
        buttons: [
          { text: $r('app.string.management_user_discard'), color: $r('app.color.main_red_color') },
          { text: $r('app.string.management_user_continue_editing'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_user_save'), color: $r('app.color.color_main') }
        ]
      }).then((response) => {
        if (response.index === 0) {
          this.vm.discardCurrentTab()
          onContinue()
        } else if (response.index === 2) {
          this.vm.saveCurrentTab().then((result: ManagementUserActionResult) => {
            if (result.ok) {
              onContinue()
            }
          })
        }
      }).catch(() => {
      })
    }
  }

  @Builder
  tabBar() {
    List({ space: 6 }) {
      ForEach([
        ManagementUserTab.Profile,
        ManagementUserTab.Access,
        ManagementUserTab.Parental,
        ManagementUserTab.Password
      ], (tab: ManagementUserTab) => {
        ListItem() {
          Button(this.tabLabel(tab), { type: ButtonType.Normal })
            .height(40)
            .padding({ left: 16, right: 16 })
            .fontSize(14)
            .fontWeight(this.ui.selectedTab === tab ? FontWeight.Medium : FontWeight.Regular)
            .fontColor(this.ui.selectedTab === tab ? $r('app.color.color_main') :
              this.secondaryTextColor())
            .backgroundColor(this.ui.selectedTab === tab ?
              AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) :
              Color.Transparent)
            .borderRadius(this.useNativeMaterial() ? 20 : 9)
            .onClick(() => this.selectTab(tab))
        }
      }, (tab: ManagementUserTab) => tab.toString())
    }
    .width('100%')
    .height(44)
    .listDirection(Axis.Horizontal)
    .scrollBar(BarState.Off)
  }

  @Builder
  sectionPlaceholder(icon: Resource, title: ResourceStr, description: ResourceStr) {
    Column({ space: 12 }) {
      SymbolGlyph(icon)
        .fontSize(34)
        .fontColor([$r('app.color.color_main')])
      Text(title)
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor())
      Text(description)
        .fontSize(13)
        .fontColor(this.secondaryTextColor())
        .textAlign(TextAlign.Center)
        .maxLines(3)
    }
    .width('100%')
    .constraintSize({ minHeight: 260 })
    .padding(24)
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private avatar() {
    if (this.ui.user.avatarUrl) {
      Image(this.ui.user.avatarUrl)
        .width(64)
        .height(64)
        .objectFit(ImageFit.Cover)
        .borderRadius(32)
    } else {
      Stack() {
        SymbolGlyph($r('sys.symbol.person_fill'))
          .fontSize(30)
          .fontColor([$r('app.color.color_main')])
      }
      .width(64)
      .height(64)
      .borderRadius(32)
      .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(
        this.vm.appUIState.themeStyle))
    }
  }

  @Builder
  private profileHeaderContent() {
    Row({ space: 14 }) {
      this.avatar()
      Column({ space: 4 }) {
        Text(this.ui.userName || $r('app.string.management_user_title'))
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor(this.primaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.ui.user.administrator ?
          $r('app.string.management_user_role_administrator') :
          $r('app.string.management_user_filter_standard'))
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      Button() {
        SymbolGlyph($r('sys.symbol.plus'))
          .fontSize(18)
          .fontColor([$r('app.color.color_main')])
      }
      .width(44)
      .height(44)
      .borderRadius(this.useNativeMaterial() ? 22 : 10)
      .backgroundColor(this.useNativeMaterial() ? Color.Transparent :
        AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle))
      .systemMaterial(this.useNativeMaterial() ?
        AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive) :
        AppThemeSurfaceResolver.disabledSystemMaterial())
      .enabled(!this.vm.isSaving())
      .accessibilityText($r('app.string.management_user_change_avatar'))
      .onClick(() => this.updateAvatar())
      if (this.ui.user.avatarUrl) {
        Button() {
          SymbolGlyph($r('sys.symbol.xmark'))
            .fontSize(17)
            .fontColor([$r('app.color.main_red_color')])
        }
        .width(44)
        .height(44)
        .borderRadius(this.useNativeMaterial() ? 22 : 10)
        .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.bg_1'))
        .systemMaterial(this.useNativeMaterial() ?
          AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive) :
          AppThemeSurfaceResolver.disabledSystemMaterial())
        .border({
          width: AppThemeSurfaceResolver.outlineWidth(
            this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 1),
          color: $r('app.color.main_red_color')
        })
        .enabled(!this.vm.isSaving())
        .accessibilityText($r('app.string.management_user_remove_avatar'))
        .onClick(() => this.confirmRemoveAvatar())
      }
    }
    .width('100%')
    .padding(14)
  }

  @Builder
  private profileHeader() {
    Column() { this.profileHeaderContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuProfileHeader() {
    Column() { this.profileHeaderContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private profileHeaderSurface() {
    if (this.useNativeMaterial()) {
      this.profileHeader()
    } else {
      this.feiniuProfileHeader()
    }
  }

  @Builder
  currentSection() {
    if (this.ui.selectedTab === ManagementUserTab.Access) {
      this.accessSection()
    } else if (this.ui.selectedTab === ManagementUserTab.Parental) {
      this.parentalSection()
    } else if (this.ui.selectedTab === ManagementUserTab.Password) {
      this.passwordSection()
    } else {
      this.profileSection()
    }
  }

  @Builder
  private profileSection() {
    ManagementUserProfileSection({
      ui: this.ui,
      themeStyle: this.vm.appUIState.themeStyle,
      nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
      onChanged: () => this.vm.markCurrentTabDirty()
    })
  }

  @Builder
  private accessSection() {
    ManagementUserAccessSection({
      ui: this.ui,
      themeStyle: this.vm.appUIState.themeStyle,
      nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
      onChanged: () => this.vm.markCurrentTabDirty()
    })
  }

  @Builder
  private parentalSection() {
    ManagementUserParentalSection({
      ui: this.ui,
      themeStyle: this.vm.appUIState.themeStyle,
      nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
      onChanged: () => this.vm.markCurrentTabDirty()
    })
  }

  @Builder
  private passwordSection() {
    ManagementUserPasswordSection({
      ui: this.ui,
      themeStyle: this.vm.appUIState.themeStyle,
      nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
      onChanged: () => this.vm.markCurrentTabDirty(),
      onSetPassword: () => this.setPassword(),
      onResetPassword: () => this.confirmResetPassword(),
      onClearPassword: () => this.confirmClearPassword()
    })
  }

  @Builder
  private nativeTabs() {
    Tabs({
      index: this.managementTabIndex(this.ui.selectedTab),
      controller: this.tabsController
    }) {
      TabContent() { this.profileSection() }
        .tabBar($r('app.string.management_user_tab_profile'))
      TabContent() { this.accessSection() }
        .tabBar($r('app.string.management_user_tab_access'))
      TabContent() { this.parentalSection() }
        .tabBar($r('app.string.management_user_tab_parental'))
      TabContent() { this.passwordSection() }
        .tabBar($r('app.string.management_user_tab_password'))
    }
    .width('100%')
    .height('auto')
    .barMode(BarMode.Fixed)
    .scrollable(false)
    .animationDuration(0)
    .onChange((index: number) => this.selectSystemTab(index))
  }

  @Builder
  private dangerousActions() {
    Column({ space: 10 }) {
      Column({ space: 4 }) {
        Text($r('app.string.management_user_delete_title'))
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.main_red_color'))
          .width('100%')
        Text($r('app.string.management_user_delete_impact'))
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .width('100%')
        if (this.deleteProtected()) {
          Text(this.deleteProtectionMessage())
            .fontSize(12)
            .fontColor($r('app.color.main_red_color'))
            .width('100%')
        }
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
      Button(this.ui.deleteStatus === ManagementUserOperationStatus.Saving ?
        $r('app.string.management_user_saving') :
        $r('app.string.management_user_delete'), { type: ButtonType.Normal })
        .width('100%')
        .height(42)
        .fontColor($r('app.color.main_red_color'))
        .backgroundColor(Color.Transparent)
        .systemMaterial(this.useNativeMaterial() ?
          AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive) :
          AppThemeSurfaceResolver.disabledSystemMaterial())
        .border({
          width: AppThemeSurfaceResolver.outlineWidth(
            this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 1),
          color: $r('app.color.main_red_color')
        })
        .borderRadius(this.useNativeMaterial() ? 21 : 8)
        .enabled(!this.vm.isSaving() && !this.deleteProtected())
        .onClick(() => this.requestDelete())
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .backgroundColor(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
    .border({
      width: AppThemeSurfaceResolver.outlineWidth(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 1),
      color: $r('app.color.main_red_color')
    })
  }

  @Builder
  private editorBody(useSystemTabs: boolean) {
    Column({ space: 12 }) {
      this.profileHeaderSurface()
      if (useSystemTabs) {
        this.nativeTabs()
      } else {
        this.tabBar()
        this.currentSection()
      }
      this.dangerousActions()
      if (this.ui.message) {
        Text(this.localizedMessage(this.ui.message))
          .fontSize(12)
          .fontColor($r('app.color.main_red_color'))
          .width('100%')
      }
    }
    .width('100%')
    .constraintSize({ maxWidth: 900 })
  }

  @Builder
  nativeEditorContent() {
    Scroll(this.contentScroller) {
      Column({ space: 12 }) {
        Blank().height(this.nativeTopSpacer())
        this.editorBody(true)
      }
      .width('100%')
      .constraintSize({ maxWidth: 900 })
      .padding({
        left: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
        right: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
        bottom: ManagementNativePagePolicy.contentBottomPadding(
          this.vm.appUIState.safeBottom + 24, this.showSaveBar())
      })
    }
    .width('100%')
    .height('100%')
    .scrollBar(BarState.Auto)
  }

  @Builder
  legacyEditorContent() {
    Scroll() {
      Column() {
        this.editorBody(false)
      }
        .width('100%')
        .padding({
          left: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
          right: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
          top: 12,
          bottom: this.showSaveBar() ? 104 : 24
        })
    }
    .width('100%')
    .height('100%')
    .scrollBar(BarState.Auto)
  }

  @Builder
  saveBarContent() {
    Row({ space: 10 }) {
      Text($r('app.string.management_user_unsaved_short'))
        .fontSize(13)
        .fontColor(this.secondaryTextColor())
        .layoutWeight(1)
      Button($r('app.string.management_user_discard'), { type: ButtonType.Normal })
        .height(40)
        .padding({ left: 16, right: 16 })
        .borderRadius(this.useNativeMaterial() ? 20 : 8)
        .fontColor(this.secondaryTextColor())
        .backgroundColor(Color.Transparent)
        .systemMaterial(this.useNativeMaterial() ?
          AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive) :
          AppThemeSurfaceResolver.disabledSystemMaterial())
        .onClick(() => this.vm.discardCurrentTab())
      Button(this.currentStatus() === ManagementUserOperationStatus.Saving ?
        $r('app.string.management_user_saving') : $r('app.string.management_user_save'),
      { type: ButtonType.Normal })
        .height(40)
        .padding({ left: 20, right: 20 })
        .fontColor($r('app.color.on_primary'))
        .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
        .borderRadius(this.useNativeMaterial() ? 20 : 10)
        .systemMaterial(this.useNativeMaterial() ?
          AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive) :
          AppThemeSurfaceResolver.disabledSystemMaterial())
        .enabled(this.currentStatus() !== ManagementUserOperationStatus.Saving)
        .onClick(() => this.vm.saveCurrentTab())
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 10, bottom: this.vm.appUIState.safeBottom + 10 })
  }

  @Builder
  saveBar() {
    Column() {
      this.saveBarContent()
    }
    .width('100%')
    .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating))
  }

  @Builder
  feiniuSaveBar() {
    Column() {
      this.saveBarContent()
    }
    .width('100%')
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  saveBarSurface() {
    if (this.useNativeMaterial()) {
      this.saveBar()
    } else {
      this.feiniuSaveBar()
    }
  }

  @Builder
  stateView(message: ResourceStr, retry: boolean = false) {
    Column({ space: 14 }) {
      SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
        .fontSize(40)
        .fontColor([$r('app.color.color_main')])
      Text(message)
        .fontSize(14)
        .fontColor(this.secondaryTextColor())
        .textAlign(TextAlign.Center)
      if (retry) {
        Button($r('app.string.management_user_retry'), { type: ButtonType.Normal })
          .height(40)
          .padding({ left: 20, right: 20 })
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
          .borderRadius(this.useNativeMaterial() ? 20 : 10)
          .systemMaterial(this.useNativeMaterial() ?
            AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive) :
            AppThemeSurfaceResolver.disabledSystemMaterial())
          .onClick(() => this.vm.reload())
      }
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .padding(24)
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({
          title: this.ui.userName || $r('app.string.management_user_title'),
          showBackButton: true,
          onBackClick: () => this.attemptLeave(() => HMRouterMgr.pop())
        })
      }
      Stack({ alignContent: Alignment.Bottom }) {
        if (this.ui.pageState === PageState.Loading) {
          Column({ space: 12 }) {
            LoadingProgress().width(28).height(28).color($r('app.color.color_main'))
            Text($r('app.string.management_user_loading'))
              .fontSize(13)
              .fontColor(this.secondaryTextColor())
          }
          .width('100%')
          .height('100%')
          .justifyContent(FlexAlign.Center)
        } else if (this.ui.pageState === PageState.Error) {
          this.stateView(this.localizedMessage(this.ui.permissionMessage || this.ui.message), true)
        } else {
          if (showLegacyActionBar) {
            this.legacyEditorContent()
          } else {
            this.nativeEditorContent()
          }
          if (this.showSaveBar()) {
            this.saveBarSurface()
          }
        }
      }
      .layoutWeight(1)
      .width('100%')
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }

  build() {
    if (this.embedded) {
      this.pageContent(false)
    } else {
      AppRouteDestination({
        title: this.ui.userName || $r('app.string.management_user_title'),
        contentExtendsUnderTitleBar: true,
        scrollControllers: [this.contentScroller],
        contentBuilder: () => {
          this.pageContent(false)
        },
        legacyContentBuilder: () => {
          this.pageContent(true)
        },
        beforeBack: () => this.handleRouteBack()
      })
    }
  }
}
