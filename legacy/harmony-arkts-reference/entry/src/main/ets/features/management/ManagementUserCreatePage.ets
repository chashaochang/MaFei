import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { PageState } from '../../common/base/BaseUIState';
import { RouterConsts } from '../../common/RouterConsts';
import { UIConstants } from '../../common/UIConstants';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { ThemeStyle } from '../../entity/ThemeStyle';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ManagementNativePagePolicy } from './ManagementNativePagePolicy';
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
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct ManagementUserCreateGroupHeader {
  @Require @Param title: ResourceStr
  @Require @Param description: ResourceStr
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor

  build() {
    Column({ space: 4 }) {
      Text(this.title)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor)
        .width('100%')
      Text(this.description)
        .fontSize(12)
        .fontColor(this.secondaryTextColor)
        .width('100%')
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
struct ManagementUserCreatePasswordField {
  @Require @Param title: ResourceStr
  @Require @Param placeholder: ResourceStr
  @Param value: string = ''
  @Param controlEnabled: boolean = true
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor
  @Require @Param fieldBackgroundColor: ResourceColor
  @Event onChange: (value: string) => void = () => {}

  build() {
    Column({ space: 6 }) {
      Text(this.title)
        .fontSize(13)
        .fontColor(this.secondaryTextColor)
        .width('100%')
      TextInput({ text: this.value, placeholder: this.placeholder })
        .type(InputType.Password)
        .showPasswordIcon(true)
        .height(44)
        .maxLength(128)
        .fontSize(14)
        .fontColor(this.primaryTextColor)
        .backgroundColor(this.fieldBackgroundColor)
        .borderRadius(8)
        .enabled(this.controlEnabled)
        .onChange(this.onChange)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
struct ManagementUserCreateAllowAllRow {
  @Require @Param title: ResourceStr
  @Require @Param description: ResourceStr
  @Param value: boolean = false
  @Param controlEnabled: boolean = true
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor
  @Event onChange: (value: boolean) => void = () => {}

  build() {
    Row({ space: 12 }) {
      Column({ space: 3 }) {
        Text(this.title)
          .fontSize(14)
          .fontColor(this.primaryTextColor)
          .width('100%')
        Text(this.description)
          .fontSize(11)
          .fontColor(this.secondaryTextColor)
          .width('100%')
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      Toggle({ type: ToggleType.Switch, isOn: this.value })
        .selectedColor($r('app.color.color_main'))
        .enabled(this.controlEnabled)
        .onChange(this.onChange)
    }
    .width('100%')
    .constraintSize({ minHeight: 58 })
  }
}

@ComponentV2
struct ManagementUserCreateOptionRow {
  @Require @Param option: ManagementUserOption
  @Param selected: boolean = false
  @Param controlEnabled: boolean = true
  @Require @Param primaryTextColor: ResourceColor
  @Event onChange: (checked: boolean) => void = () => {}

  build() {
    Row({ space: 12 }) {
      Toggle({ type: ToggleType.Checkbox, isOn: this.selected })
        .selectedColor($r('app.color.color_main'))
        .enabled(this.controlEnabled)
        .onChange(this.onChange)
      Text(this.option.name)
        .fontSize(13)
        .fontColor(this.primaryTextColor)
        .layoutWeight(1)
        .maxLines(2)
    }
    .width('100%')
    .constraintSize({ minHeight: 48 })
  }
}

@ComponentV2
struct ManagementUserCreateOptionState {
  @Require @Param status: ManagementUserOperationStatus
  @Param options: ManagementUserOption[] = []
  @Require @Param secondaryTextColor: ResourceColor

  build() {
    if (this.status === ManagementUserOperationStatus.Loading) {
      Row({ space: 8 }) {
        LoadingProgress().width(18).height(18).color($r('app.color.color_main'))
        Text($r('app.string.management_user_loading'))
          .fontSize(12)
          .fontColor(this.secondaryTextColor)
      }
      .width('100%')
      .padding({ top: 14, bottom: 14 })
    } else if (this.status === ManagementUserOperationStatus.Error) {
      Text($r('app.string.management_user_access_options_failed'))
        .fontSize(12)
        .fontColor($r('app.color.main_red_color'))
        .width('100%')
        .padding({ top: 14, bottom: 14 })
    } else if (this.options.length === 0) {
      Text($r('app.string.management_user_access_empty'))
        .fontSize(12)
        .fontColor(this.secondaryTextColor)
        .width('100%')
        .padding({ top: 14, bottom: 14 })
    }
  }
}

@ComponentV2
struct ManagementUserCredentialsSection {
  @Require @Param ui: ManagementUserCreateUIState
  @Require @Param themeStyle: ThemeStyle
  @Param nativeMaterial: boolean = false
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor

  private inputBackground(): ResourceColor {
    return this.nativeMaterial ? $r('sys.color.comp_background_tertiary') :
      AppThemeSurfaceResolver.interactionOverlay(this.themeStyle)
  }

  build() {
    Column() {
      Column({ space: 12 }) {
        ManagementUserCreateGroupHeader({
          title: $r('app.string.management_user_create'),
          description: $r('app.string.management_user_initial_password'),
          primaryTextColor: this.primaryTextColor,
          secondaryTextColor: this.secondaryTextColor
        })
        Column({ space: 6 }) {
          Text($r('app.string.management_user_name'))
            .fontSize(13)
            .fontColor(this.secondaryTextColor)
            .width('100%')
          TextInput({
            text: this.ui.name,
            placeholder: $r('app.string.management_user_name_placeholder')
          })
            .height(44)
            .maxLength(128)
            .fontSize(14)
            .fontColor(this.primaryTextColor)
            .backgroundColor(this.inputBackground())
            .borderRadius(8)
            .enabled(!this.ui.saving && !this.ui.partialSuccess)
            .onChange((value: string) => this.ui.name = value)
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
        ManagementUserCreatePasswordField({
          title: $r('app.string.management_user_initial_password'),
          placeholder: $r('app.string.management_user_password_placeholder'),
          value: this.ui.password,
          controlEnabled: !this.ui.saving && !this.ui.partialSuccess,
          primaryTextColor: this.primaryTextColor,
          secondaryTextColor: this.secondaryTextColor,
          fieldBackgroundColor: this.inputBackground(),
          onChange: (value: string) => this.ui.password = value
        })
        ManagementUserCreatePasswordField({
          title: $r('app.string.management_user_confirm_password'),
          placeholder: $r('app.string.management_user_confirm_password_placeholder'),
          value: this.ui.confirmPassword,
          controlEnabled: !this.ui.saving && !this.ui.partialSuccess,
          primaryTextColor: this.primaryTextColor,
          secondaryTextColor: this.secondaryTextColor,
          fieldBackgroundColor: this.inputBackground(),
          onChange: (value: string) => this.ui.confirmPassword = value
        })
      }
      .width('100%')
      .padding(16)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .clip(this.nativeMaterial)
    .backgroundColor(this.nativeMaterial ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
  }
}

@ComponentV2
struct ManagementUserPartialSuccessSection {
  @Require @Param ui: ManagementUserCreateUIState
  @Require @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Param nativeMaterial: boolean = false
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor
  @Param messageText: string = ''
  @Event onRetry: () => void = () => {}
  @Event onOpenDetails: () => void = () => {}

  build() {
    if (this.ui.partialSuccess) {
      Column({ space: 10 }) {
        Row({ space: 8 }) {
          SymbolGlyph($r('sys.symbol.exclamationmark_triangle'))
            .fontSize(18)
            .fontColor([$r('app.color.status_warning')])
          Text($r('app.string.management_user_created_policy_pending'))
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.primaryTextColor)
            .layoutWeight(1)
        }
        if (this.messageText) {
          Text(this.messageText)
            .fontSize(12)
            .fontColor(this.secondaryTextColor)
            .width('100%')
        }
        Row({ space: 10 }) {
          Button($r('app.string.management_user_create_retry_access'), { type: ButtonType.Normal })
            .height(40)
            .padding({ left: 16, right: 16 })
            .fontColor($r('app.color.on_primary'))
            .backgroundColor(this.nativeMaterial ? Color.Transparent : $r('app.color.color_main'))
            .borderRadius(this.nativeMaterial ? 20 : 9)
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.PrimaryInteractive, this.nativeMaterial))
            .enabled(!this.ui.saving)
            .onClick(this.onRetry)
          Button($r('app.string.management_user_open_details'), { type: ButtonType.Normal })
            .height(40)
            .padding({ left: 16, right: 16 })
            .fontColor(this.primaryTextColor)
            .backgroundColor(Color.Transparent)
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.AdaptiveControl, this.nativeMaterial))
            .border({
              width: 1,
              color: AppThemeSurfaceResolver.contentOutline(this.themeStyle, this.nativeThemeAvailable)
            })
            .borderRadius(this.nativeMaterial ? 20 : 9)
            .enabled(!this.ui.saving)
            .onClick(this.onOpenDetails)
        }
        .width('100%')
      }
      .width('100%')
      .padding(16)
      .alignItems(HorizontalAlign.Start)
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor(this.nativeMaterial ?
        AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
      .border({
        width: AppThemeSurfaceResolver.outlineWidth(this.themeStyle, this.nativeThemeAvailable, 1),
        color: $r('app.color.status_warning')
      })
    }
  }
}

@ComponentV2
struct ManagementUserCreateContent {
  @Param showLegacyActionBar: boolean = false
  @Param contentScroller: Scroller = new Scroller()
  @Local ui: ManagementUserCreateUIState = new ManagementUserCreateUIState(this.getUIContext())
  @Local vm: ManagementUserCreateViewModel = new ManagementUserCreateViewModel(this.ui)
  @Local advancedExpanded: boolean = false

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

  private nativeTopSpacer(): number {
    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  private createEnabled(): boolean {
    return !this.ui.saving && this.ui.name.trim().length > 0 &&
      this.ui.password === this.ui.confirmPassword
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





























  build() {
    Column() {
      if (this.showLegacyActionBar) {
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
        Column({ space: 14 }) {
          SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
            .fontSize(40)
            .fontColor([$r('app.color.color_main')])
          Text((this.localizedMessage(
              this.ui.permissionMessage || 'management_permission_check_failed')))
            .fontSize(14)
            .fontColor(this.secondaryTextColor())
            .textAlign(TextAlign.Center)
          if ((true)) {
            Button($r('app.string.management_user_retry'), { type: ButtonType.Normal })
              .height(40)
              .padding({ left: 20, right: 20 })
              .fontColor($r('app.color.on_primary'))
              .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
              .borderRadius(this.useNativeMaterial() ? 20 : 10)
              .attributeModifier(AppThemeSurfaceResolver.modifier(
                AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
              .onClick(() => this.vm.init())
          }
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
        .padding(24)
      } else {
        if (this.showLegacyActionBar) {
          Scroll() {
            Column({ space: AppThemeTokens.groupSpacing(this.vm.appUIState.themeStyle) }) {
              ManagementUserCredentialsSection({
                ui: this.ui,
                themeStyle: this.vm.appUIState.themeStyle,
                nativeMaterial: this.useNativeMaterial(),
                primaryTextColor: this.primaryTextColor(),
                secondaryTextColor: this.secondaryTextColor()
              })
              Column() { Column() {
                ManagementUserCreateGroupHeader({
                  title: $r('app.string.management_user_access_folders'),
                  description: $r('app.string.management_user_access_folders_desc'),
                  primaryTextColor: this.primaryTextColor(),
                  secondaryTextColor: this.secondaryTextColor()
                })
                ManagementUserCreateAllowAllRow({
                  title: $r('app.string.management_user_access_all_folders'),
                  description: $r('app.string.management_user_access_all_folders_desc'),
                  value: this.ui.access.enableAllFolders,
                  onChange: (value: boolean) => {
                    const next = this.copyAccess()
                    next.enableAllFolders = value
                    this.ui.access = next
                  },
                  controlEnabled: !this.ui.saving,
                  primaryTextColor: this.primaryTextColor(),
                  secondaryTextColor: this.secondaryTextColor()
                })
                if (!this.ui.access.enableAllFolders) {
                  ManagementUserCreateOptionState({
                    status: this.ui.foldersStatus,
                    options: this.ui.folders,
                    secondaryTextColor: this.secondaryTextColor()
                  })
                  ForEach(this.ui.folders, (option: ManagementUserOption) => {
                    ManagementUserCreateOptionRow({
                      option: option,
                      selected: this.ui.access.enabledFolderIds.includes(option.id),
                      onChange: (checked: boolean) => {
                        const next = this.copyAccess()
                        next.enabledFolderIds = this.updateSelection(
                          next.enabledFolderIds, option.id, checked)
                        this.ui.access = next
                      },
                      controlEnabled: !this.ui.saving,
                      primaryTextColor: this.primaryTextColor()
                    })
                  }, (option: ManagementUserOption) => option.id)
                }
              }
              .width('100%')
              .padding(16)
              .alignItems(HorizontalAlign.Start) }
                .width('100%')
                .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
                .clip(this.useNativeMaterial())
                .backgroundColor(this.useNativeMaterial() ?
                  AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
              Column() { Column() {
                ManagementUserCreateGroupHeader({
                  title: $r('app.string.management_user_access_channels'),
                  description: $r('app.string.management_user_access_channels_desc'),
                  primaryTextColor: this.primaryTextColor(),
                  secondaryTextColor: this.secondaryTextColor()
                })
                ManagementUserCreateAllowAllRow({
                  title: $r('app.string.management_user_access_all_channels'),
                  description: $r('app.string.management_user_access_all_channels_desc'),
                  value: this.ui.access.enableAllChannels,
                  onChange: (value: boolean) => {
                    const next = this.copyAccess()
                    next.enableAllChannels = value
                    this.ui.access = next
                  },
                  controlEnabled: !this.ui.saving,
                  primaryTextColor: this.primaryTextColor(),
                  secondaryTextColor: this.secondaryTextColor()
                })
                if (!this.ui.access.enableAllChannels) {
                  ManagementUserCreateOptionState({
                    status: this.ui.channelsStatus,
                    options: this.ui.channels,
                    secondaryTextColor: this.secondaryTextColor()
                  })
                  ForEach(this.ui.channels, (option: ManagementUserOption) => {
                    ManagementUserCreateOptionRow({
                      option: option,
                      selected: this.ui.access.enabledChannelIds.includes(option.id),
                      onChange: (checked: boolean) => {
                        const next = this.copyAccess()
                        next.enabledChannelIds = this.updateSelection(
                          next.enabledChannelIds, option.id, checked)
                        this.ui.access = next
                      },
                      controlEnabled: !this.ui.saving,
                      primaryTextColor: this.primaryTextColor()
                    })
                  }, (option: ManagementUserOption) => option.id)
                }
              }
              .width('100%')
              .padding(16)
              .alignItems(HorizontalAlign.Start) }
                .width('100%')
                .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
                .clip(this.useNativeMaterial())
                .backgroundColor(this.useNativeMaterial() ?
                  AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
              ManagementUserPartialSuccessSection({
                ui: this.ui,
                themeStyle: this.vm.appUIState.themeStyle,
                nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
                nativeMaterial: this.useNativeMaterial(),
                primaryTextColor: this.primaryTextColor(),
                secondaryTextColor: this.secondaryTextColor(),
                messageText: this.localizedMessage(this.ui.message),
                onRetry: () => this.retryAccess(),
                onOpenDetails: () => this.openDetails(this.ui.createdUserId)
              })
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
        } else {
          Stack({ alignContent: Alignment.Bottom }) {
            Scroll(this.contentScroller) {
              Column({ space: AppThemeTokens.groupSpacing(this.vm.appUIState.themeStyle) }) {
                Blank().height(this.nativeTopSpacer())
                ManagementUserCredentialsSection({
                  ui: this.ui,
                  themeStyle: this.vm.appUIState.themeStyle,
                  nativeMaterial: this.useNativeMaterial(),
                  primaryTextColor: this.primaryTextColor(),
                  secondaryTextColor: this.secondaryTextColor()
                })
                Column() { Column() {
                  ManagementUserCreateGroupHeader({
                    title: $r('app.string.management_user_access_scope'),
                    description: $r('app.string.management_user_advanced_access'),
                    primaryTextColor: this.primaryTextColor(),
                    secondaryTextColor: this.secondaryTextColor()
                  })
                  ManagementUserCreateAllowAllRow({
                    title: $r('app.string.management_user_access_all_folders'),
                    description: $r('app.string.management_user_access_all_folders_desc'),
                    value: this.ui.access.enableAllFolders,
                    onChange: (value: boolean) => {
                      const next = this.copyAccess()
                      next.enableAllFolders = value
                      this.ui.access = next
                      if (!value) {
                        this.advancedExpanded = true
                      }
                    },
                    controlEnabled: !this.ui.saving,
                    primaryTextColor: this.primaryTextColor(),
                    secondaryTextColor: this.secondaryTextColor()
                  })
                  Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
                  ManagementUserCreateAllowAllRow({
                    title: $r('app.string.management_user_access_all_channels'),
                    description: $r('app.string.management_user_access_all_channels_desc'),
                    value: this.ui.access.enableAllChannels,
                    onChange: (value: boolean) => {
                      const next = this.copyAccess()
                      next.enableAllChannels = value
                      this.ui.access = next
                      if (!value) {
                        this.advancedExpanded = true
                      }
                    },
                    controlEnabled: !this.ui.saving,
                    primaryTextColor: this.primaryTextColor(),
                    secondaryTextColor: this.secondaryTextColor()
                  })
                }
                .width('100%')
                .padding(16)
                .alignItems(HorizontalAlign.Start) }
                  .width('100%')
                  .borderRadius(8)
                  .clip(true)
                  .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
                Column({ space: AppThemeTokens.groupSpacing(this.vm.appUIState.themeStyle) }) {
                  Row({ space: 12 }) {
                    Text($r('app.string.management_user_advanced_access'))
                      .fontSize(16)
                      .fontWeight(FontWeight.Medium)
                      .fontColor(this.primaryTextColor())
                      .layoutWeight(1)
                    SymbolGlyph(this.advancedExpanded ?
                      $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
                      .fontSize(16)
                      .fontColor([this.secondaryTextColor()])
                  }
                  .width('100%')
                  .height(56)
                  .padding({ left: 16, right: 16 })
                  .borderRadius(8)
                  .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
                  .onClick(() => this.advancedExpanded = !this.advancedExpanded)
                  if (this.advancedExpanded && !this.ui.access.enableAllFolders) {
                    Column() {
                      ManagementUserCreateGroupHeader({
                        title: $r('app.string.management_user_access_folders'),
                        description: $r('app.string.management_user_access_folders_desc'),
                        primaryTextColor: this.primaryTextColor(),
                        secondaryTextColor: this.secondaryTextColor()
                      })
                      ManagementUserCreateOptionState({
                        status: this.ui.foldersStatus,
                        options: this.ui.folders,
                        secondaryTextColor: this.secondaryTextColor()
                      })
                      ForEach(this.ui.folders, (option: ManagementUserOption) => {
                        ManagementUserCreateOptionRow({
                          option: option,
                          selected: this.ui.access.enabledFolderIds.includes(option.id),
                          onChange: (checked: boolean) => {
                            const next = this.copyAccess()
                            next.enabledFolderIds = this.updateSelection(next.enabledFolderIds, option.id, checked)
                            this.ui.access = next
                          },
                          controlEnabled: !this.ui.saving,
                          primaryTextColor: this.primaryTextColor()
                        })
                      }, (option: ManagementUserOption) => option.id)
                    }
                    .width('100%')
                    .padding(16)
                    .alignItems(HorizontalAlign.Start)
                    .borderRadius(8)
                    .clip(true)
                    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
                  }
                  if (this.advancedExpanded && !this.ui.access.enableAllChannels) {
                    Column() {
                      ManagementUserCreateGroupHeader({
                        title: $r('app.string.management_user_access_channels'),
                        description: $r('app.string.management_user_access_channels_desc'),
                        primaryTextColor: this.primaryTextColor(),
                        secondaryTextColor: this.secondaryTextColor()
                      })
                      ManagementUserCreateOptionState({
                        status: this.ui.channelsStatus,
                        options: this.ui.channels,
                        secondaryTextColor: this.secondaryTextColor()
                      })
                      ForEach(this.ui.channels, (option: ManagementUserOption) => {
                        ManagementUserCreateOptionRow({
                          option: option,
                          selected: this.ui.access.enabledChannelIds.includes(option.id),
                          onChange: (checked: boolean) => {
                            const next = this.copyAccess()
                            next.enabledChannelIds = this.updateSelection(next.enabledChannelIds, option.id, checked)
                            this.ui.access = next
                          },
                          controlEnabled: !this.ui.saving,
                          primaryTextColor: this.primaryTextColor()
                        })
                      }, (option: ManagementUserOption) => option.id)
                    }
                    .width('100%')
                    .padding(16)
                    .alignItems(HorizontalAlign.Start)
                    .borderRadius(8)
                    .clip(true)
                    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
                  }
                }
                .width('100%')
                ManagementUserPartialSuccessSection({
                  ui: this.ui,
                  themeStyle: this.vm.appUIState.themeStyle,
                  nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
                  nativeMaterial: this.useNativeMaterial(),
                  primaryTextColor: this.primaryTextColor(),
                  secondaryTextColor: this.secondaryTextColor(),
                  messageText: this.localizedMessage(this.ui.message),
                  onRetry: () => this.retryAccess(),
                  onOpenDetails: () => this.openDetails(this.ui.createdUserId)
                })
                if (!this.ui.partialSuccess && this.ui.message) {
                  Text(this.localizedMessage(this.ui.message))
                    .fontSize(12)
                    .fontColor($r('app.color.main_red_color'))
                    .width('100%')
                }
              }
              .width('100%')
              .constraintSize({ maxWidth: 760 })
              .padding({
                left: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
                right: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
                bottom: ManagementNativePagePolicy.contentBottomPadding(
                  this.vm.appUIState.safeBottom + 24, !this.ui.partialSuccess)
              })
            }
            .width('100%')
            .height('100%')
            .scrollBar(BarState.Auto)
            if (!this.ui.partialSuccess) {
              Column() { Row() {
                Button(this.ui.saving ?
                  $r('app.string.management_user_saving') :
                  $r('app.string.management_user_create'), { type: ButtonType.Normal })
                  .width('100%')
                  .height(46)
                  .fontColor($r('app.color.on_primary'))
                  .backgroundColor(Color.Transparent)
                  .borderRadius(23)
                  .attributeModifier(AppThemeSurfaceResolver.modifier(
                    AppThemeMaterialRole.PrimaryInteractive, true))
                  .enabled(this.createEnabled())
                  .onClick(() => this.submit())
              }
              .width('100%')
              .padding({ left: 16, right: 16, top: 10, bottom: this.vm.appUIState.safeBottom + 10 }) }
                .width('100%')
                .attributeModifier(AppThemeSurfaceResolver.modifier(AppThemeMaterialRole.Floating, true))
            }
          }
          .width('100%')
          .height('100%')
        }
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }
}

@HMRouter({
  pageUrl: RouterConsts.ManagementUserCreatePage,
  useNavDst: true
})
@ComponentV2
export struct ManagementUserCreatePage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private readonly contentScroller: Scroller = new Scroller()

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.management_user_create'),
        contentExtendsUnderTitleBar: true,
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
              ManagementUserCreateContent({
                showLegacyActionBar: false,
                contentScroller: this.contentScroller
              })
            } else {
              ManagementUserCreateContent({
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
        ManagementUserCreateContent({
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
