import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { PageState } from '../../common/base/BaseUIState';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ManagementPermissionState } from './ManagementModels';
import { ManagementUserCreateUIState } from './ManagementUserCreateUIState';
import { ManagementUserCreateViewModel } from './ManagementUserCreateViewModel';
import {
  ManagementAccessDraft,
  ManagementUserActionResult,
  ManagementUserOperationStatus,
  ManagementUserOption,
  ManagementUserTab
} from './ManagementUserModels';

@HMRouter({
  pageUrl: RouterConsts.ManagementUserCreatePage,
  useNavDst: true
})
@ComponentV2
export struct ManagementUserCreatePage {
  @Local ui: ManagementUserCreateUIState = new ManagementUserCreateUIState(this.getUIContext())
  @Local vm: ManagementUserCreateViewModel = new ManagementUserCreateViewModel(this.ui)

  aboutToAppear(): void {
    this.vm.init()
  }

  aboutToDisappear(): void {
    this.vm.stop()
  }

  @Monitor('ui.permissionState')
  onPermissionStateChange(): void {
    if (this.ui.permissionState !== ManagementPermissionState.Denied) {
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

  private openDetails(userId: string): void {
    if (userId.length === 0) {
      return
    }
    HMRouterMgr.to(RouterConsts.ManagementUserDetailPage)
      .withParam({ userId: userId, initialTab: ManagementUserTab.Profile })
      .replace()
  }

  private submit(): void {
    this.vm.submit().then((result: ManagementUserActionResult) => {
      if (result.ok) {
        this.getUIContext().getPromptAction().showToast({
          message: $r('app.string.management_user_create_success')
        })
        this.openDetails(result.userId)
      }
    })
  }

  private retryAccess(): void {
    this.vm.retryAccess().then((result: ManagementUserActionResult) => {
      if (result.ok) {
        this.openDetails(result.userId)
      }
    })
  }

  private copyAccess(): ManagementAccessDraft {
    const result = new ManagementAccessDraft()
    result.enableAllFolders = this.ui.access.enableAllFolders
    result.enabledFolderIds = this.ui.access.enabledFolderIds.slice()
    result.enableAllChannels = this.ui.access.enableAllChannels
    result.enabledChannelIds = this.ui.access.enabledChannelIds.slice()
    result.enableAllDevices = this.ui.access.enableAllDevices
    result.enabledDeviceIds = this.ui.access.enabledDeviceIds.slice()
    return result
  }

  private updateSelection(ids: string[], optionId: string, checked: boolean): string[] {
    if (checked && !ids.includes(optionId)) {
      return ids.concat(optionId)
    }
    if (!checked) {
      return ids.filter((id: string) => id !== optionId)
    }
    return ids.slice()
  }

  @Builder
  private groupHeader(title: ResourceStr, description: ResourceStr) {
    Column({ space: 4 }) {
      Text(title)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor())
        .width('100%')
      Text(description)
        .fontSize(12)
        .fontColor(this.secondaryTextColor())
        .width('100%')
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private passwordField(title: ResourceStr, placeholder: ResourceStr, value: string,
    onChange: (value: string) => void) {
    Column({ space: 6 }) {
      Text(title)
        .fontSize(13)
        .fontColor(this.secondaryTextColor())
        .width('100%')
      TextInput({ text: value, placeholder: placeholder })
        .type(InputType.Password)
        .showPasswordIcon(true)
        .height(44)
        .maxLength(128)
        .fontSize(14)
        .fontColor(this.primaryTextColor())
        .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(
          this.vm.appUIState.themeStyle))
        .borderRadius(8)
        .enabled(!this.ui.saving && !this.ui.partialSuccess)
        .onChange(onChange)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private credentialsContent() {
    Column({ space: 12 }) {
      this.groupHeader($r('app.string.management_user_create'),
        $r('app.string.management_user_initial_password'))
      Column({ space: 6 }) {
        Text($r('app.string.management_user_name'))
          .fontSize(13)
          .fontColor(this.secondaryTextColor())
          .width('100%')
        TextInput({
          text: this.ui.name,
          placeholder: $r('app.string.management_user_name_placeholder')
        })
          .height(44)
          .maxLength(128)
          .fontSize(14)
          .fontColor(this.primaryTextColor())
          .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(
            this.vm.appUIState.themeStyle))
          .borderRadius(8)
          .enabled(!this.ui.saving && !this.ui.partialSuccess)
          .onChange((value: string) => {
            this.ui.name = value
          })
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
      this.passwordField($r('app.string.management_user_initial_password'),
        $r('app.string.management_user_password_placeholder'), this.ui.password,
        (value: string) => {
          this.ui.password = value
        })
      this.passwordField($r('app.string.management_user_confirm_password'),
        $r('app.string.management_user_confirm_password_placeholder'),
        this.ui.confirmPassword, (value: string) => {
          this.ui.confirmPassword = value
        })
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private credentialsGroup() {
    Column() { this.credentialsContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuCredentialsGroup() {
    Column() { this.credentialsContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private credentialsSurface() {
    if (this.useNativeMaterial()) {
      this.credentialsGroup()
    } else {
      this.feiniuCredentialsGroup()
    }
  }

  @Builder
  private allowAllRow(title: ResourceStr, description: ResourceStr, value: boolean,
    onChange: (value: boolean) => void) {
    Row({ space: 12 }) {
      Column({ space: 3 }) {
        Text(title)
          .fontSize(14)
          .fontColor(this.primaryTextColor())
          .width('100%')
        Text(description)
          .fontSize(11)
          .fontColor(this.secondaryTextColor())
          .width('100%')
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      Toggle({ type: ToggleType.Switch, isOn: value })
        .selectedColor($r('app.color.color_main'))
        .enabled(!this.ui.saving)
        .onChange(onChange)
    }
    .width('100%')
    .constraintSize({ minHeight: 58 })
  }

  @Builder
  private optionRow(option: ManagementUserOption, selected: boolean,
    onChange: (checked: boolean) => void) {
    Row({ space: 12 }) {
      Toggle({ type: ToggleType.Checkbox, isOn: selected })
        .selectedColor($r('app.color.color_main'))
        .enabled(!this.ui.saving)
        .onChange(onChange)
      Text(option.name)
        .fontSize(13)
        .fontColor(this.primaryTextColor())
        .layoutWeight(1)
        .maxLines(2)
    }
    .width('100%')
    .constraintSize({ minHeight: 48 })
  }

  @Builder
  private optionState(status: ManagementUserOperationStatus, options: ManagementUserOption[]) {
    if (status === ManagementUserOperationStatus.Loading) {
      Row({ space: 8 }) {
        LoadingProgress().width(18).height(18).color($r('app.color.color_main'))
        Text($r('app.string.management_user_loading'))
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
      }
      .width('100%')
      .padding({ top: 14, bottom: 14 })
    } else if (status === ManagementUserOperationStatus.Error) {
      Text($r('app.string.management_user_access_options_failed'))
        .fontSize(12)
        .fontColor($r('app.color.main_red_color'))
        .width('100%')
        .padding({ top: 14, bottom: 14 })
    } else if (options.length === 0) {
      Text($r('app.string.management_user_access_empty'))
        .fontSize(12)
        .fontColor(this.secondaryTextColor())
        .width('100%')
        .padding({ top: 14, bottom: 14 })
    }
  }

  @Builder
  private foldersContent() {
    Column() {
      this.groupHeader($r('app.string.management_user_access_folders'),
        $r('app.string.management_user_access_folders_desc'))
      this.allowAllRow($r('app.string.management_user_access_all_folders'),
        $r('app.string.management_user_access_all_folders_desc'),
        this.ui.access.enableAllFolders, (value: boolean) => {
          const next = this.copyAccess()
          next.enableAllFolders = value
          this.ui.access = next
        })
      if (!this.ui.access.enableAllFolders) {
        this.optionState(this.ui.foldersStatus, this.ui.folders)
        ForEach(this.ui.folders, (option: ManagementUserOption) => {
          this.optionRow(option, this.ui.access.enabledFolderIds.includes(option.id),
            (checked: boolean) => {
              const next = this.copyAccess()
              next.enabledFolderIds = this.updateSelection(
                next.enabledFolderIds, option.id, checked)
              this.ui.access = next
            })
        }, (option: ManagementUserOption) => option.id)
      }
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private foldersGroup() {
    Column() { this.foldersContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuFoldersGroup() {
    Column() { this.foldersContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private foldersSurface() {
    if (this.useNativeMaterial()) {
      this.foldersGroup()
    } else {
      this.feiniuFoldersGroup()
    }
  }

  @Builder
  private channelsContent() {
    Column() {
      this.groupHeader($r('app.string.management_user_access_channels'),
        $r('app.string.management_user_access_channels_desc'))
      this.allowAllRow($r('app.string.management_user_access_all_channels'),
        $r('app.string.management_user_access_all_channels_desc'),
        this.ui.access.enableAllChannels, (value: boolean) => {
          const next = this.copyAccess()
          next.enableAllChannels = value
          this.ui.access = next
        })
      if (!this.ui.access.enableAllChannels) {
        this.optionState(this.ui.channelsStatus, this.ui.channels)
        ForEach(this.ui.channels, (option: ManagementUserOption) => {
          this.optionRow(option, this.ui.access.enabledChannelIds.includes(option.id),
            (checked: boolean) => {
              const next = this.copyAccess()
              next.enabledChannelIds = this.updateSelection(
                next.enabledChannelIds, option.id, checked)
              this.ui.access = next
            })
        }, (option: ManagementUserOption) => option.id)
      }
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private channelsGroup() {
    Column() { this.channelsContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuChannelsGroup() {
    Column() { this.channelsContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private channelsSurface() {
    if (this.useNativeMaterial()) {
      this.channelsGroup()
    } else {
      this.feiniuChannelsGroup()
    }
  }

  @Builder
  private partialSuccess() {
    if (this.ui.partialSuccess) {
      Column({ space: 10 }) {
        Row({ space: 8 }) {
          SymbolGlyph($r('sys.symbol.exclamationmark_triangle'))
            .fontSize(18)
            .fontColor([$r('app.color.status_warning')])
          Text($r('app.string.management_user_created_policy_pending'))
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.primaryTextColor())
            .layoutWeight(1)
        }
        if (this.ui.message) {
          Text(this.localizedMessage(this.ui.message))
            .fontSize(12)
            .fontColor(this.secondaryTextColor())
            .width('100%')
        }
        Row({ space: 10 }) {
          Button($r('app.string.management_user_create_retry_access'),
            { type: ButtonType.Normal })
            .height(40)
            .padding({ left: 16, right: 16 })
            .fontColor($r('app.color.on_primary'))
            .backgroundColor($r('app.color.color_main'))
            .borderRadius(9)
            .enabled(!this.ui.saving)
            .onClick(() => this.retryAccess())
          Button($r('app.string.management_user_open_details'),
            { type: ButtonType.Normal })
            .height(40)
            .padding({ left: 16, right: 16 })
            .fontColor(this.primaryTextColor())
            .backgroundColor(Color.Transparent)
            .border({
              width: 1,
              color: AppThemeSurfaceResolver.contentOutline(
                this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
            })
            .borderRadius(9)
            .enabled(!this.ui.saving)
            .onClick(() => this.openDetails(this.ui.createdUserId))
        }
        .width('100%')
      }
      .width('100%')
      .padding(16)
      .alignItems(HorizontalAlign.Start)
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .backgroundColor(this.useNativeMaterial() ?
        $r('sys.color.comp_background_tertiary') : $r('app.color.bg_1'))
      .border({ width: 1, color: $r('app.color.status_warning') })
    }
  }

  @Builder
  private formContent() {
    Scroll() {
      Column({ space: AppThemeTokens.groupSpacing(this.vm.appUIState.themeStyle) }) {
        this.credentialsSurface()
        this.foldersSurface()
        this.channelsSurface()
        this.partialSuccess()
        if (!this.ui.partialSuccess) {
          if (this.ui.message) {
            Text(this.localizedMessage(this.ui.message))
              .fontSize(12)
              .fontColor($r('app.color.main_red_color'))
              .width('100%')
          }
          Button(this.ui.saving ?
            $r('app.string.management_user_saving') :
            $r('app.string.management_user_create'), { type: ButtonType.Normal })
            .width('100%')
            .height(46)
            .fontColor($r('app.color.on_primary'))
            .backgroundColor($r('app.color.color_main'))
            .borderRadius(10)
            .enabled(!this.ui.saving)
            .onClick(() => this.submit())
        }
      }
      .width('100%')
      .constraintSize({ maxWidth: 760 })
      .padding({
        left: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
        right: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
        top: 12,
        bottom: this.vm.appUIState.safeBottom + 24
      })
    }
    .width('100%')
    .height('100%')
    .scrollBar(BarState.Auto)
  }

  @Builder
  private stateView(message: ResourceStr, retry: boolean = false) {
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
          .backgroundColor($r('app.color.color_main'))
          .borderRadius(10)
          .onClick(() => this.vm.init())
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
          title: $r('app.string.management_user_create'),
          showBackButton: true,
          onBackClick: () => HMRouterMgr.pop()
        })
      }
      if (this.ui.pageState === PageState.Loading) {
        Column({ space: 12 }) {
          LoadingProgress().width(28).height(28).color($r('app.color.color_main'))
          Text($r('app.string.management_user_loading'))
            .fontSize(13)
            .fontColor(this.secondaryTextColor())
        }
        .layoutWeight(1)
        .width('100%')
        .justifyContent(FlexAlign.Center)
      } else if (this.ui.pageState === PageState.Error) {
        this.stateView(this.localizedMessage(
          this.ui.permissionMessage || 'management_permission_check_failed'), true)
      } else {
        this.formContent()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_user_create'),
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
