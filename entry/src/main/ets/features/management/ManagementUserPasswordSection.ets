import { ThemeStyle } from '../../entity/ThemeStyle';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ManagementUserDetailUIState } from './ManagementUserDetailUIState';
import { ManagementPasswordDraft, ManagementUserOperationStatus } from './ManagementUserModels';

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
        .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
        .borderRadius(8)
        .enabled(!this.actionLocked())
        .onChange(onChange)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private updateContent() {
    Column({ space: 12 }) {
      this.groupHeader($r('app.string.management_user_password_group_update'),
        this.editingCurrentUser() ?
          $r('app.string.management_user_password_group_update_self_desc') :
          $r('app.string.management_user_password_group_update_other_desc'))
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
        this.passwordField($r('app.string.management_user_current_password'),
          $r('app.string.management_user_current_password_placeholder'),
          this.ui.password.currentPassword, (value: string) => {
            const next = this.copyPassword()
            next.currentPassword = value
            this.commitPassword(next)
          })
      }
      this.passwordField($r('app.string.management_user_new_password'),
        $r('app.string.management_user_new_password_placeholder'),
        this.ui.password.newPassword, (value: string) => {
          const next = this.copyPassword()
          next.newPassword = value
          this.commitPassword(next)
        })
      this.passwordField($r('app.string.management_user_confirm_password'),
        $r('app.string.management_user_confirm_password_placeholder'),
        this.ui.password.confirmPassword, (value: string) => {
          const next = this.copyPassword()
          next.confirmPassword = value
          this.commitPassword(next)
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
  }

  @Builder
  private updateGroup() {
    Column() { this.updateContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuUpdateGroup() {
    Column() { this.updateContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private updateSurface() {
    if (this.useNativeMaterial()) {
      this.updateGroup()
    } else {
      this.feiniuUpdateGroup()
    }
  }

  @Builder
  private dangerousActions() {
    Column({ space: 10 }) {
      this.groupHeader($r('app.string.management_user_password_group_danger'),
        $r('app.string.management_user_password_group_danger_desc'))
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
          AppThemeMaterialRole.AdaptiveInteractive, this.useNativeMaterial()))
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
          AppThemeMaterialRole.AdaptiveInteractive, this.useNativeMaterial()))
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

  build() {
    Column({ space: AppThemeTokens.groupSpacing(this.themeStyle) }) {
      this.updateSurface()
      this.dangerousActions()
    }
    .width('100%')
  }
}
