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
export struct ManagementUserAccessSection {
  @Require
  @Param ui: ManagementUserDetailUIState
  @Require
  @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onChanged: () => void = () => {
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
        .onChange(onChange)
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
  }

  @Builder
  private optionState(status: ManagementUserOperationStatus, options: ManagementUserOption[]) {
    if (status === ManagementUserOperationStatus.Error) {
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
          this.commitAccess(next)
        })
      if (!this.ui.access.enableAllFolders) {
        this.optionState(this.ui.foldersStatus, this.ui.folders)
        ForEach(this.ui.folders, (option: ManagementUserOption) => {
          this.optionRow(option, this.ui.access.enabledFolderIds.includes(option.id),
            (checked: boolean) => {
              const next = this.copyAccess()
              next.enabledFolderIds = this.updateSelection(next.enabledFolderIds, option.id, checked)
              this.commitAccess(next)
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
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuFoldersGroup() {
    Column() { this.foldersContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
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
          this.commitAccess(next)
        })
      if (!this.ui.access.enableAllChannels) {
        this.optionState(this.ui.channelsStatus, this.ui.channels)
        ForEach(this.ui.channels, (option: ManagementUserOption) => {
          this.optionRow(option, this.ui.access.enabledChannelIds.includes(option.id),
            (checked: boolean) => {
              const next = this.copyAccess()
              next.enabledChannelIds = this.updateSelection(next.enabledChannelIds, option.id, checked)
              this.commitAccess(next)
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
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuChannelsGroup() {
    Column() { this.channelsContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
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
  private devicesContent() {
    Column() {
      this.groupHeader($r('app.string.management_user_access_devices'),
        $r('app.string.management_user_access_devices_desc'))
      this.allowAllRow($r('app.string.management_user_access_all_devices'),
        $r('app.string.management_user_access_all_devices_desc'),
        this.ui.access.enableAllDevices, (value: boolean) => {
          const next = this.copyAccess()
          next.enableAllDevices = value
          this.commitAccess(next)
        })
      if (!this.ui.access.enableAllDevices) {
        this.optionState(this.ui.devicesStatus, this.ui.devices)
        ForEach(this.ui.devices, (option: ManagementUserOption) => {
          this.optionRow(option, this.ui.access.enabledDeviceIds.includes(option.id),
            (checked: boolean) => {
              const next = this.copyAccess()
              next.enabledDeviceIds = this.updateSelection(next.enabledDeviceIds, option.id, checked)
              this.commitAccess(next)
            })
        }, (option: ManagementUserOption) => option.id)
      }
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private devicesGroup() {
    Column() { this.devicesContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuDevicesGroup() {
    Column() { this.devicesContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private devicesSurface() {
    if (this.useNativeMaterial()) {
      this.devicesGroup()
    } else {
      this.feiniuDevicesGroup()
    }
  }

  build() {
    Column({ space: AppThemeTokens.groupSpacing(this.themeStyle) }) {
      this.foldersSurface()
      this.channelsSurface()
      this.devicesSurface()
    }
    .width('100%')
  }
}
