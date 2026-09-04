import { ThemeStyle } from '../../../entity/ThemeStyle';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../../theme/AppThemeTokens';
import { ManagementDeviceItem } from './ManagementDeviceModels';
import { ManagementDevicesUIState } from './ManagementDevicesUIState';

@ComponentV2
struct ManagementDeviceDetailRow {
  @Require @Param label: ResourceStr
  @Require @Param value: ResourceStr
  @Require @Param primaryColor: ResourceColor
  @Require @Param secondaryColor: ResourceColor

  build() {
    Row({ space: 14 }) {
      Text(this.label)
        .fontSize(13)
        .fontColor(this.secondaryColor)
        .constraintSize({ minWidth: 94 })
      Text(this.value)
        .fontSize(13)
        .fontColor(this.primaryColor)
        .layoutWeight(1)
        .textAlign(TextAlign.End)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .width('100%')
    .constraintSize({ minHeight: 46 })
  }
}

@ComponentV2
struct ManagementDeviceIdentityHeader {
  @Require @Param device: ManagementDeviceItem
  @Require @Param themeStyle: ThemeStyle
  @Require @Param primaryColor: ResourceColor
  @Require @Param secondaryColor: ResourceColor
  @Param useNativeLayout: boolean = false

  build() {
    Row({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.externaldrive'))
        .fontSize(24)
        .fontColor([$r('app.color.color_main')])
        .width(48)
        .height(48)
        .backgroundColor(this.useNativeLayout ?
          AppThemeSurfaceResolver.iconWellBackground(this.themeStyle) :
          AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
        .borderRadius(this.useNativeLayout ? 8 : 14)
      Column({ space: 4 }) {
        if (this.useNativeLayout) {
          Text(this.device.displayName)
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.primaryColor)
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        } else {
          Text(this.device.displayName)
            .fontSize(19)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.primaryColor)
        }
        Text(this.device.current ? $r('app.string.management_device_current') :
          $r('app.string.management_device_authorized'))
          .fontSize(this.useNativeLayout ? 13 : 12)
          .fontColor(this.device.current ? $r('app.color.status_success') : this.secondaryColor)
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .padding(this.useNativeLayout ? { left: 4, right: 4, top: 4, bottom: 4 } : 0)
  }
}

@ComponentV2
struct ManagementDeviceMetadataGroup {
  @Require @Param device: ManagementDeviceItem
  @Require @Param primaryColor: ResourceColor
  @Require @Param secondaryColor: ResourceColor
  @Param useNativeLayout: boolean = false

  build() {
    Column() {
      ManagementDeviceDetailRow({
        label: $r('app.string.management_device_app'),
        value: `${this.device.appName} ${this.device.appVersion}`,
        primaryColor: this.primaryColor,
        secondaryColor: this.secondaryColor
      })
      if (this.useNativeLayout) {
        Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
      }
      ManagementDeviceDetailRow({
        label: $r('app.string.management_device_last_user'),
        value: this.device.lastUserName,
        primaryColor: this.primaryColor,
        secondaryColor: this.secondaryColor
      })
      if (this.useNativeLayout) {
        Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
      }
      ManagementDeviceDetailRow({
        label: $r('app.string.management_device_last_activity'),
        value: this.device.lastActivityDate,
        primaryColor: this.primaryColor,
        secondaryColor: this.secondaryColor
      })
    }
    .width('100%')
    .padding(this.useNativeLayout ? { left: 16, right: 16, top: 4, bottom: 4 } : 0)
    .borderRadius(this.useNativeLayout ? 8 : 0)
    .clip(this.useNativeLayout)
    .backgroundColor(this.useNativeLayout ?
      AppThemeSurfaceResolver.contentGroupBackground() : Color.Transparent)
  }
}

@ComponentV2
struct ManagementDeviceRenameGroup {
  @Require
  @Param ui: ManagementDevicesUIState
  @Require
  @Param device: ManagementDeviceItem
  @Require
  @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onRename: () => void = () => {
  }
  @Require @Param secondaryColor: ResourceColor
  @Param useNativeLayout: boolean = false

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  build() {
    if (this.useNativeLayout) {
      Column({ space: 10 }) {
        Text($r('app.string.management_device_custom_name'))
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.secondaryColor)
          .width('100%')
        Row({ space: 8 }) {
          TextInput({ text: this.ui.renameDraft, placeholder: this.device.name })
            .layoutWeight(1)
            .height(42)
            .backgroundColor($r('sys.color.comp_background_tertiary'))
            .borderRadius(8)
            .onChange((value: string) => this.ui.renameDraft = value)
          Button($r('app.string.management_device_save_name'), { type: ButtonType.Normal })
            .height(42)
            .padding({ left: 16, right: 16 })
            .borderRadius(21)
            .fontColor($r('app.color.on_primary'))
            .backgroundColor(Color.Transparent)
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
            .enabled(this.ui.pendingDeviceId.length === 0 && !this.ui.batchPending)
            .onClick(() => this.onRename())
        }
        .width('100%')
      }
      .width('100%')
      .padding(16)
      .alignItems(HorizontalAlign.Start)
      .borderRadius(8)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
    } else {
      Column({ space: 10 }) {
        Text($r('app.string.management_device_custom_name'))
          .fontSize(13)
          .fontColor(this.secondaryColor)
          .width('100%')
        Row({ space: 8 }) {
          TextInput({ text: this.ui.renameDraft, placeholder: this.device.name })
            .layoutWeight(1)
            .height(42)
            .onChange((value: string) => this.ui.renameDraft = value)
          Button($r('app.string.management_device_save_name'), { type: ButtonType.Normal })
            .height(42)
            .enabled(this.ui.pendingDeviceId.length === 0 && !this.ui.batchPending)
            .onClick(() => this.onRename())
        }
        .width('100%')
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
    }
  }
}

@ComponentV2
struct ManagementDeviceRevokeGroup {
  @Require @Param ui: ManagementDevicesUIState
  @Require @Param device: ManagementDeviceItem
  @Require @Param themeStyle: ThemeStyle
  @Require @Param secondaryColor: ResourceColor
  @Param nativeThemeAvailable: boolean = false
  @Param useNativeLayout: boolean = false
  @Event onRevoke: () => void = () => {
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  build() {
    if (this.useNativeLayout) {
      Column({ space: 8 }) {
        Button($r('app.string.management_device_revoke'), { type: ButtonType.Normal })
          .width('100%')
          .height(44)
          .borderRadius(22)
          .fontColor($r('app.color.main_red_color'))
          .backgroundColor(Color.Transparent)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
          .enabled(!this.device.current && this.ui.pendingDeviceId.length === 0 && !this.ui.batchPending)
          .onClick(() => this.onRevoke())
        if (this.device.current) {
          Text($r('app.string.management_device_current_protected'))
            .fontSize(12)
            .fontColor(this.secondaryColor)
            .width('100%')
            .textAlign(TextAlign.Center)
        }
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 4, bottom: 4 })
      .borderRadius(8)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
    } else {
      Column({ space: 8 }) {
        Button($r('app.string.management_device_revoke'), { type: ButtonType.Normal })
          .width('100%')
          .height(42)
          .fontColor($r('app.color.main_red_color'))
          .enabled(!this.device.current && this.ui.pendingDeviceId.length === 0 && !this.ui.batchPending)
          .onClick(() => this.onRevoke())
        if (this.device.current) {
          Text($r('app.string.management_device_current_protected'))
            .fontSize(12)
            .fontColor(this.secondaryColor)
            .width('100%')
        }
      }
      .width('100%')
    }
  }
}

@ComponentV2
export struct ManagementDeviceDetailPanel {
  @Require
  @Param ui: ManagementDevicesUIState
  @Require
  @Param device: ManagementDeviceItem
  @Require
  @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onRename: () => void = () => {
  }
  @Event onRevoke: () => void = () => {
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  build() {
    if (this.useNativeMaterial()) {
      Column({ space: 12 }) {
        ManagementDeviceIdentityHeader({
          device: this.device,
          themeStyle: this.themeStyle,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          useNativeLayout: true
        })
        ManagementDeviceMetadataGroup({
          device: this.device,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          useNativeLayout: true
        })
        ManagementDeviceRenameGroup({
          ui: this.ui,
          device: this.device,
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          secondaryColor: this.secondaryTextColor(),
          useNativeLayout: true,
          onRename: () => this.onRename()
        })
        ManagementDeviceRevokeGroup({
          ui: this.ui,
          device: this.device,
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          secondaryColor: this.secondaryTextColor(),
          useNativeLayout: true,
          onRevoke: () => this.onRevoke()
        })
      }
      .width('100%')
    } else {
      Column({ space: 12 }) {
        ManagementDeviceIdentityHeader({
          device: this.device,
          themeStyle: this.themeStyle,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        ManagementDeviceMetadataGroup({
          device: this.device,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        ManagementDeviceRenameGroup({
          ui: this.ui,
          device: this.device,
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          secondaryColor: this.secondaryTextColor(),
          onRename: () => this.onRename()
        })
        ManagementDeviceRevokeGroup({
          ui: this.ui,
          device: this.device,
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          secondaryColor: this.secondaryTextColor(),
          onRevoke: () => this.onRevoke()
        })
      }
      .width('100%')
      .padding(18)
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor(AppThemeTokens.isNative(this.themeStyle) ?
        AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
    }
  }
}
