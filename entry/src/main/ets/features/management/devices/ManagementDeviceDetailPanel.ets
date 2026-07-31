import { ThemeStyle } from '../../../entity/ThemeStyle';
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../../theme/AppThemeTokens';
import { ManagementDeviceItem } from './ManagementDeviceModels';
import { ManagementDevicesUIState } from './ManagementDevicesUIState';

@ComponentV2
export struct ManagementDeviceDetailPanel {
  @Require
  @Param ui: ManagementDevicesUIState
  @Require
  @Param device: ManagementDeviceItem
  @Require
  @Param themeStyle: ThemeStyle
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

  @Builder
  private row(label: ResourceStr, value: ResourceStr) {
    Row({ space: 14 }) {
      Text(label).fontSize(13).fontColor(this.secondaryTextColor()).constraintSize({ minWidth: 94 })
      Text(value).fontSize(13).fontColor(this.primaryTextColor()).layoutWeight(1)
        .textAlign(TextAlign.End).maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .width('100%').constraintSize({ minHeight: 46 })
  }

  build() {
    Column({ space: 12 }) {
      Row({ space: 12 }) {
        SymbolGlyph($r('sys.symbol.externaldrive'))
          .fontSize(24).fontColor([$r('app.color.color_main')])
          .width(48).height(48)
          .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
          .borderRadius(14)
        Column({ space: 4 }) {
          Text(this.device.displayName)
            .fontSize(19).fontWeight(FontWeight.Bold).fontColor(this.primaryTextColor())
          Text(this.device.current ? $r('app.string.management_device_current') :
            $r('app.string.management_device_authorized'))
            .fontSize(12).fontColor(this.device.current ? $r('app.color.status_success') :
              this.secondaryTextColor())
        }
        .layoutWeight(1).alignItems(HorizontalAlign.Start)
      }
      Column() {
        this.row($r('app.string.management_device_app'),
          `${this.device.appName} ${this.device.appVersion}`)
        this.row($r('app.string.management_device_last_user'), this.device.lastUserName)
        this.row($r('app.string.management_device_last_activity'), this.device.lastActivityDate)
      }
      .width('100%')

      Text($r('app.string.management_device_custom_name'))
        .fontSize(13).fontColor(this.secondaryTextColor()).width('100%')
      Row({ space: 8 }) {
        TextInput({
          text: this.ui.renameDraft,
          placeholder: this.device.name
        })
          .layoutWeight(1).height(42)
          .onChange((value: string) => this.ui.renameDraft = value)
        Button($r('app.string.management_device_save_name'), { type: ButtonType.Normal })
          .height(42)
          .enabled(this.ui.pendingDeviceId.length === 0 && !this.ui.batchPending)
          .onClick(() => this.onRename())
      }
      .width('100%')

      Button($r('app.string.management_device_revoke'), { type: ButtonType.Normal })
        .width('100%').height(42)
        .fontColor($r('app.color.main_red_color'))
        .enabled(!this.device.current && this.ui.pendingDeviceId.length === 0 && !this.ui.batchPending)
        .onClick(() => this.onRevoke())
      if (this.device.current) {
        Text($r('app.string.management_device_current_protected'))
          .fontSize(12).fontColor(this.secondaryTextColor()).width('100%')
      }
    }
    .width('100%')
    .padding(18)
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .backgroundColor(AppThemeTokens.isNative(this.themeStyle) ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
  }
}
