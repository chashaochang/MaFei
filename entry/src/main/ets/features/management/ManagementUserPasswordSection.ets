import { ThemeStyle } from '../../entity/ThemeStyle';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ManagementUserDetailUIState } from './ManagementUserDetailUIState';
import { ManagementPasswordDraft, ManagementUserOperationStatus } from './ManagementUserModels';

@ComponentV2
struct ManagementPasswordGroupHeader {
  @Require
  @Param title: ResourceStr
  @Require
  @Param description: ResourceStr
  @Require
  @Param primaryColor: ResourceColor
  @Require
  @Param secondaryColor: ResourceColor

  build() {
    Column({ space: 4 }) {
      Text(this.title)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryColor)
        .width('100%')
      Text(this.description)
        .fontSize(12)
        .fontColor(this.secondaryColor)
        .width('100%')
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
struct ManagementPasswordField {
  @Require
  @Param title: ResourceStr
  @Require
  @Param placeholder: ResourceStr
  @Require
  @Param value: string
  @Require
  @Param primaryColor: ResourceColor
  @Require
  @Param secondaryColor: ResourceColor
  @Require
  @Param fieldColor: ResourceColor
  @Param isLocked: boolean = false
  @Event onValueChange: (value: string) => void = (_value: string) => {
  }

  build() {
    Column({ space: 6 }) {
      Text(this.title)
        .fontSize(13)
        .fontColor(this.secondaryColor)
        .width('100%')
      TextInput({ text: this.value, placeholder: this.placeholder })
        .type(InputType.Password)
        .showPasswordIcon(true)
        .height(44)
        .maxLength(128)
        .fontSize(14)
        .fontColor(this.primaryColor)
        .backgroundColor(this.fieldColor)
        .borderRadius(8)
        .enabled(!this.isLocked)
        .onChange((value: string) => this.onValueChange(value))
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
struct ManagementPasswordUpdateSurface {
  @Require
  @Param ui: ManagementUserDetailUIState
  @Require
  @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onChanged: () => void = () => {
  }
  @Event onSetPassword: () => void = () => {
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private copyPassword(): ManagementPasswordDraft {
    const next = new ManagementPasswordDraft()
    next.currentPassword = this.ui.password.currentPassword
    next.newPassword = this.ui.password.newPassword
    next.confirmPassword = this.ui.password.confirmPassword
    return next
  }

  private commitPassword(next: ManagementPasswordDraft): void {
    this.ui.password = next
    this.onChanged()
  }

  private editingCurrentUser(): boolean {
    return this.ui.userId === this.ui.currentUserId
  }

  private actionLocked(): boolean {
    return this.ui.passwordStatus === ManagementUserOperationStatus.Saving
  }

  build() {
    Column({ space: 12 }) {
      ManagementPasswordGroupHeader({
        title: $r('app.string.management_user_password_group_update'),
        description: this.editingCurrentUser() ?
          $r('app.string.management_user_password_group_update_self_desc') :
          $r('app.string.management_user_password_group_update_other_desc'),
        primaryColor: this.primaryTextColor(),
        secondaryColor: this.secondaryTextColor()
      })
      Row({ space: 8 }) {
        SymbolGlyph(this.ui.user.hasPassword ?
          $r('sys.symbol.lock_fill') : $r('sys.symbol.lock_open'))
          .fontSize(18)
          .fontColor([$r('app.color.color_main')])
        Text(this.ui.user.hasPassword ?
          $r('app.string.management_user_password_set') :
          $r('app.string.management_user_password_not_set'))
          .fontSize(13)
          .fontColor(this.secondaryTextColor())
      }
      .width('100%')
      .constraintSize({ minHeight: 36 })
      if (this.editingCurrentUser()) {
        ManagementPasswordField({
          title: $r('app.string.management_user_current_password'),
          placeholder: $r('app.string.management_user_current_password_placeholder'),
          value: this.ui.password.currentPassword,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          fieldColor: AppThemeSurfaceResolver.interactionOverlay(this.themeStyle),
          isLocked: this.actionLocked(),
          onValueChange: (value: string) => {
            const next = this.copyPassword()
            next.currentPassword = value
            this.commitPassword(next)
          }
        })
      }
      ManagementPasswordField({
        title: $r('app.string.management_user_new_password'),
        placeholder: $r('app.string.management_user_new_password_placeholder'),
        value: this.ui.password.newPassword,
        primaryColor: this.primaryTextColor(),
        secondaryColor: this.secondaryTextColor(),
        fieldColor: AppThemeSurfaceResolver.interactionOverlay(this.themeStyle),
        isLocked: this.actionLocked(),
        onValueChange: (value: string) => {
          const next = this.copyPassword()
          next.newPassword = value
          this.commitPassword(next)
        }
      })
      ManagementPasswordField({
        title: $r('app.string.management_user_confirm_password'),
        placeholder: $r('app.string.management_user_confirm_password_placeholder'),
        value: this.ui.password.confirmPassword,
        primaryColor: this.primaryTextColor(),
        secondaryColor: this.secondaryTextColor(),
        fieldColor: AppThemeSurfaceResolver.interactionOverlay(this.themeStyle),
        isLocked: this.actionLocked(),
        onValueChange: (value: string) => {
          const next = this.copyPassword()
          next.confirmPassword = value
          this.commitPassword(next)
        }
      })
      Button(this.actionLocked() ?
        $r('app.string.management_user_saving') :
        $r('app.string.management_user_set_password'), { type: ButtonType.Normal })
        .height(42)
        .padding({ left: 18, right: 18 })
        .fontColor($r('app.color.on_primary'))
        .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
        .borderRadius(this.useNativeMaterial() ? 21 : 9)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
        .enabled(!this.actionLocked())
        .onClick(() => this.onSetPassword())
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .clip(this.useNativeMaterial())
    .backgroundColor(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
  }
}

@ComponentV2
struct ManagementPasswordDangerousActions {
  @Require
  @Param ui: ManagementUserDetailUIState
  @Require
  @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onResetPassword: () => void = () => {
  }
  @Event onClearPassword: () => void = () => {
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private actionLocked(): boolean {
    return this.ui.passwordStatus === ManagementUserOperationStatus.Saving
  }

  build() {
    Column({ space: 10 }) {
      ManagementPasswordGroupHeader({
        title: $r('app.string.management_user_password_group_danger'),
        description: $r('app.string.management_user_password_group_danger_desc'),
        primaryColor: this.primaryTextColor(),
        secondaryColor: this.secondaryTextColor()
      })
      Button($r('app.string.management_user_reset_password'), { type: ButtonType.Normal })
        .width('100%')
        .height(42)
        .fontColor($r('app.color.main_red_color'))
        .backgroundColor(Color.Transparent)
        .border({
          width: AppThemeSurfaceResolver.outlineWidth(this.themeStyle, this.nativeThemeAvailable, 1),
          color: $r('app.color.main_red_color')
        })
        .borderRadius(this.useNativeMaterial() ? 21 : 8)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
        .enabled(!this.actionLocked())
        .onClick(() => this.onResetPassword())
      Button($r('app.string.management_user_clear_password'), { type: ButtonType.Normal })
        .width('100%')
        .height(42)
        .fontColor($r('app.color.main_red_color'))
        .backgroundColor(Color.Transparent)
        .border({
          width: AppThemeSurfaceResolver.outlineWidth(this.themeStyle, this.nativeThemeAvailable, 1),
          color: $r('app.color.main_red_color')
        })
        .borderRadius(this.useNativeMaterial() ? 21 : 8)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
        .enabled(!this.actionLocked() && this.ui.user.hasPassword)
        .onClick(() => this.onClearPassword())
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .backgroundColor(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
    .border({
      width: AppThemeSurfaceResolver.outlineWidth(this.themeStyle, this.nativeThemeAvailable, 1),
      color: $r('app.color.main_red_color')
    })
  }
}

@ComponentV2
export struct ManagementUserPasswordSection {
  @Require
  @Param ui: ManagementUserDetailUIState
  @Require
  @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onChanged: () => void = () => {
  }
  @Event onSetPassword: () => void = () => {
  }
  @Event onResetPassword: () => void = () => {
  }
  @Event onClearPassword: () => void = () => {
  }

  build() {
    Column({ space: AppThemeTokens.groupSpacing(this.themeStyle) }) {
      ManagementPasswordUpdateSurface({
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onChanged: () => this.onChanged(),
        onSetPassword: () => this.onSetPassword()
      })
      ManagementPasswordDangerousActions({
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onResetPassword: () => this.onResetPassword(),
        onClearPassword: () => this.onClearPassword()
      })
    }
    .width('100%')
  }
}
