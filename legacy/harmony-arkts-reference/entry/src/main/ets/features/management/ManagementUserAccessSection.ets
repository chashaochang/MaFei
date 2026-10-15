import { ThemeStyle } from '../../entity/ThemeStyle';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ObjectUtil } from '../../utils/ObjectUtil';
import { ManagementUserDetailUIState } from './ManagementUserDetailUIState';
import {
  ManagementAccessDraft,
  ManagementUserOperationStatus,
  ManagementUserOption
} from './ManagementUserModels';

@ComponentV2
struct ManagementAccessGroup {
  @Require @Param title: ResourceStr
  @Require @Param description: ResourceStr
  @Require @Param allowAllTitle: ResourceStr
  @Require @Param allowAllDescription: ResourceStr
  @Require @Param allowAll: boolean
  @Require @Param options: ManagementUserOption[]
  @Require @Param status: ManagementUserOperationStatus
  @Require @Param selectedIds: string[]
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeMaterial: boolean
  @Event onAllowAllChange: (value: boolean) => void = () => {
  }
  @Event onOptionChange: (optionId: string, checked: boolean) => void = () => {
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  build() {
    Column() {
      Column({ space: 4 }) {
        Text(this.title)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor(this.primaryTextColor())
          .width('100%')
        Text(this.description)
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .width('100%')
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)

      Row({ space: 12 }) {
        Column({ space: 3 }) {
          Text(this.allowAllTitle)
            .fontSize(14)
            .fontColor(this.primaryTextColor())
            .width('100%')
          Text(this.allowAllDescription)
            .fontSize(11)
            .fontColor(this.secondaryTextColor())
            .width('100%')
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        Toggle({ type: ToggleType.Switch, isOn: this.allowAll })
          .selectedColor($r('app.color.color_main'))
          .onChange((value: boolean) => {
            this.onAllowAllChange(value)
          })
      }
      .width('100%')
      .constraintSize({ minHeight: 58 })

      if (!this.allowAll) {
        if (this.status === ManagementUserOperationStatus.Error) {
          Text($r('app.string.management_user_access_options_failed'))
            .fontSize(12)
            .fontColor($r('app.color.main_red_color'))
            .width('100%')
            .padding({ top: 14, bottom: 14 })
        } else if (this.options.length === 0) {
          Text($r('app.string.management_user_access_empty'))
            .fontSize(12)
            .fontColor(this.secondaryTextColor())
            .width('100%')
            .padding({ top: 14, bottom: 14 })
        }

        ForEach(this.options, (option: ManagementUserOption) => {
          Row({ space: 12 }) {
            Toggle({ type: ToggleType.Checkbox, isOn: this.selectedIds.includes(option.id) })
              .selectedColor($r('app.color.color_main'))
              .onChange((checked: boolean) => {
                this.onOptionChange(option.id, checked)
              })
            Column({ space: 2 }) {
              Text(option.name)
                .fontSize(13)
                .fontColor(this.primaryTextColor())
                .width('100%')
                .maxLines(2)
              if (option.detail) {
                Text(option.detail)
                  .fontSize(11)
                  .fontColor(this.secondaryTextColor())
                  .width('100%')
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
              }
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')
          .constraintSize({ minHeight: 48 })
        }, (option: ManagementUserOption) => option.id)
      }
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .clip(this.nativeMaterial)
    .backgroundColor(this.nativeMaterial ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
  }
}

@ComponentV2
export struct ManagementUserAccessSection {
  @Require @Param ui: ManagementUserDetailUIState
  @Require @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onChanged: () => void = () => {
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private copyAccess(): ManagementAccessDraft {
    const next = ObjectUtil.Assign({}, this.ui.access) as ManagementAccessDraft
    next.enabledFolderIds = this.ui.access.enabledFolderIds.slice()
    next.enabledChannelIds = this.ui.access.enabledChannelIds.slice()
    next.enabledDeviceIds = this.ui.access.enabledDeviceIds.slice()
    return next
  }

  private commitAccess(next: ManagementAccessDraft): void {
    this.ui.access = next
    this.onChanged()
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
    Column({ space: AppThemeTokens.groupSpacing(this.themeStyle) }) {
      ManagementAccessGroup({
        title: $r('app.string.management_user_access_folders'),
        description: $r('app.string.management_user_access_folders_desc'),
        allowAllTitle: $r('app.string.management_user_access_all_folders'),
        allowAllDescription: $r('app.string.management_user_access_all_folders_desc'),
        allowAll: this.ui.access.enableAllFolders,
        options: this.ui.folders,
        status: this.ui.foldersStatus,
        selectedIds: this.ui.access.enabledFolderIds,
        themeStyle: this.themeStyle,
        nativeMaterial: this.useNativeMaterial(),
        onAllowAllChange: (value: boolean) => {
          const next = this.copyAccess()
          next.enableAllFolders = value
          this.commitAccess(next)
        },
        onOptionChange: (optionId: string, checked: boolean) => {
          const next = this.copyAccess()
          next.enabledFolderIds = this.updateSelection(next.enabledFolderIds, optionId, checked)
          this.commitAccess(next)
        }
      })

      ManagementAccessGroup({
        title: $r('app.string.management_user_access_channels'),
        description: $r('app.string.management_user_access_channels_desc'),
        allowAllTitle: $r('app.string.management_user_access_all_channels'),
        allowAllDescription: $r('app.string.management_user_access_all_channels_desc'),
        allowAll: this.ui.access.enableAllChannels,
        options: this.ui.channels,
        status: this.ui.channelsStatus,
        selectedIds: this.ui.access.enabledChannelIds,
        themeStyle: this.themeStyle,
        nativeMaterial: this.useNativeMaterial(),
        onAllowAllChange: (value: boolean) => {
          const next = this.copyAccess()
          next.enableAllChannels = value
          this.commitAccess(next)
        },
        onOptionChange: (optionId: string, checked: boolean) => {
          const next = this.copyAccess()
          next.enabledChannelIds = this.updateSelection(next.enabledChannelIds, optionId, checked)
          this.commitAccess(next)
        }
      })

      ManagementAccessGroup({
        title: $r('app.string.management_user_access_devices'),
        description: $r('app.string.management_user_access_devices_desc'),
        allowAllTitle: $r('app.string.management_user_access_all_devices'),
        allowAllDescription: $r('app.string.management_user_access_all_devices_desc'),
        allowAll: this.ui.access.enableAllDevices,
        options: this.ui.devices,
        status: this.ui.devicesStatus,
        selectedIds: this.ui.access.enabledDeviceIds,
        themeStyle: this.themeStyle,
        nativeMaterial: this.useNativeMaterial(),
        onAllowAllChange: (value: boolean) => {
          const next = this.copyAccess()
          next.enableAllDevices = value
          this.commitAccess(next)
        },
        onOptionChange: (optionId: string, checked: boolean) => {
          const next = this.copyAccess()
          next.enabledDeviceIds = this.updateSelection(next.enabledDeviceIds, optionId, checked)
          this.commitAccess(next)
        }
      })
    }
    .width('100%')
  }
}
