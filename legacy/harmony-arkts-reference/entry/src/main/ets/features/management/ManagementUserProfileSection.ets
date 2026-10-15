import { ThemeStyle } from '../../entity/ThemeStyle';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ObjectUtil } from '../../utils/ObjectUtil';
import { ManagementNativeSelect } from './ManagementNativeSelect';
import { ManagementUserDetailUIState } from './ManagementUserDetailUIState';
import {
  ManagementProfileDraft,
  ManagementUserOperationStatus,
  ManagementUserOption
} from './ManagementUserModels';

const SYNC_PLAY_NONE: string = 'None'
const SYNC_PLAY_JOIN: string = 'JoinGroups'
const SYNC_PLAY_CREATE: string = 'CreateAndJoinGroups'

enum ManagementProfileGroupKind {
  Identity,
  Privileges,
  Playback,
  Remote,
  Limits,
  Status
}

@ComponentV2
struct ManagementProfileGroupHeader {
  @Require @Param title: ResourceStr
  @Require @Param description: ResourceStr
  @Require @Param primaryColor: ResourceColor
  @Require @Param secondaryColor: ResourceColor

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
struct ManagementProfileToggleRow {
  @Require @Param title: ResourceStr
  @Require @Param description: ResourceStr
  @Require @Param value: boolean
  @Require @Param controlEnabled: boolean
  @Require @Param primaryColor: ResourceColor
  @Require @Param secondaryColor: ResourceColor
  @Event onChange: (value: boolean) => void = () => {
  }

  build() {
    Row({ space: 12 }) {
      Column({ space: 3 }) {
        Text(this.title)
          .fontSize(14)
          .fontColor(this.controlEnabled ? this.primaryColor : this.secondaryColor)
          .width('100%')
        Text(this.description)
          .fontSize(11)
          .fontColor(this.secondaryColor)
          .width('100%')
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      Toggle({ type: ToggleType.Switch, isOn: this.value })
        .selectedColor($r('app.color.color_main'))
        .enabled(this.controlEnabled)
        .onChange((value: boolean) => this.onChange(value))
    }
    .width('100%')
    .constraintSize({ minHeight: 58 })
    .alignItems(VerticalAlign.Center)
  }
}

@ComponentV2
struct ManagementProfileProviderRow {
  @Require @Param title: ResourceStr
  @Require @Param options: ManagementUserOption[]
  @Require @Param selectedId: string
  @Require @Param primaryColor: ResourceColor
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Event onSelect: (id: string) => void = () => {
  }

  private optionLabels(): ResourceStr[] {
    return this.options.map((option: ManagementUserOption) => option.name)
  }

  private optionIndex(): number {
    const index = this.options.findIndex((option: ManagementUserOption) => option.id === this.selectedId)
    return index >= 0 ? index : 0
  }

  build() {
    Row({ space: 12 }) {
      Text(this.title)
        .fontSize(14)
        .fontColor(this.primaryColor)
        .layoutWeight(1)
      ManagementNativeSelect({
        options: this.optionLabels(),
        selectedIndex: this.optionIndex(),
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        isEnabled: this.options.length > 0,
        onSelect: (index: number) => {
          if (index >= 0 && index < this.options.length) {
            this.onSelect(this.options[index].id)
          }
        }
      })
    }
    .width('100%')
    .constraintSize({ minHeight: 54 })
  }
}

@ComponentV2
struct ManagementProfileNumericRow {
  @Require @Param title: ResourceStr
  @Require @Param description: ResourceStr
  @Require @Param value: number
  @Require @Param primaryColor: ResourceColor
  @Require @Param secondaryColor: ResourceColor
  @Require @Param fieldColor: ResourceColor
  @Event onChange: (value: number) => void = () => {
  }

  build() {
    Row({ space: 12 }) {
      Column({ space: 3 }) {
        Text(this.title)
          .fontSize(14)
          .fontColor(this.primaryColor)
          .width('100%')
        Text(this.description)
          .fontSize(11)
          .fontColor(this.secondaryColor)
          .width('100%')
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      TextInput({ text: Math.max(0, this.value).toString() })
        .type(InputType.Number)
        .width(104)
        .height(40)
        .fontSize(14)
        .fontColor(this.primaryColor)
        .textAlign(TextAlign.End)
        .backgroundColor(this.fieldColor)
        .borderRadius(8)
        .onChange((text: string) => this.onChange(Math.max(0, Number(text) || 0)))
    }
    .width('100%')
    .constraintSize({ minHeight: 58 })
  }
}

@ComponentV2
struct ManagementProfileDeletionFolderRow {
  @Require @Param option: ManagementUserOption
  @Require @Param selected: boolean
  @Require @Param primaryColor: ResourceColor
  @Event onChange: (checked: boolean) => void = () => {
  }

  build() {
    Row({ space: 12 }) {
      Toggle({ type: ToggleType.Checkbox, isOn: this.selected })
        .selectedColor($r('app.color.color_main'))
        .onChange((checked: boolean) => this.onChange(checked))
      Text(this.option.name)
        .fontSize(13)
        .fontColor(this.primaryColor)
        .layoutWeight(1)
        .maxLines(2)
    }
    .width('100%')
    .constraintSize({ minHeight: 48 })
  }
}

@ComponentV2
struct ManagementProfileGroupSurface {
  @Require @Param kind: ManagementProfileGroupKind
  @Require @Param ui: ManagementUserDetailUIState
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
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

  private copyProfile(): ManagementProfileDraft {
    const next = ObjectUtil.Assign({}, this.ui.profile) as ManagementProfileDraft
    next.contentDeletionFolderIds = this.ui.profile.contentDeletionFolderIds.slice()
    return next
  }

  private commitProfile(next: ManagementProfileDraft): void {
    this.ui.profile = next
    this.onChanged()
  }

  private administratorLocked(): boolean {
    if (!this.ui.user.administrator || this.ui.user.disabled) {
      return false
    }
    return this.ui.userId === this.ui.currentUserId || this.ui.enabledAdministratorCount <= 1
  }

  private protectionMessage(): ResourceStr {
    if (this.ui.userId === this.ui.currentUserId) {
      return $r('app.string.management_user_protect_current_admin')
    }
    return $r('app.string.management_user_protect_last_admin')
  }

  private syncPlayOptions(): ResourceStr[] {
    return [
      $r('app.string.management_user_sync_play_none'),
      $r('app.string.management_user_sync_play_join'),
      $r('app.string.management_user_sync_play_create')
    ]
  }

  private syncPlayIndex(): number {
    if (this.ui.profile.syncPlayAccess === SYNC_PLAY_NONE) {
      return 0
    }
    if (this.ui.profile.syncPlayAccess === SYNC_PLAY_JOIN) {
      return 1
    }
    return 2
  }

  build() {
    Column() {
      if (this.kind === ManagementProfileGroupKind.Identity) {
        ManagementProfileGroupHeader({
          title: $r('app.string.management_user_group_identity'),
          description: $r('app.string.management_user_group_identity_desc'),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        Text($r('app.string.management_user_name'))
          .fontSize(13)
          .fontColor(this.secondaryTextColor())
          .width('100%')
          .margin({ top: 10 })
        TextInput({
          text: this.ui.profile.name,
          placeholder: $r('app.string.management_user_name_placeholder')
        })
          .height(44)
          .fontSize(14)
          .fontColor(this.primaryTextColor())
          .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
          .borderRadius(8)
          .onChange((value: string) => {
            const next = this.copyProfile()
            next.name = value
            this.commitProfile(next)
          })
        ManagementProfileProviderRow({
          title: $r('app.string.management_user_auth_provider'),
          options: this.ui.authenticationProviders,
          selectedId: this.ui.profile.authenticationProviderId,
          primaryColor: this.primaryTextColor(),
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          onSelect: (id: string) => {
            const next = this.copyProfile()
            next.authenticationProviderId = id
            this.commitProfile(next)
          }
        })
        ManagementProfileProviderRow({
          title: $r('app.string.management_user_password_reset_provider'),
          options: this.ui.passwordResetProviders,
          selectedId: this.ui.profile.passwordResetProviderId,
          primaryColor: this.primaryTextColor(),
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          onSelect: (id: string) => {
            const next = this.copyProfile()
            next.passwordResetProviderId = id
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_hidden'),
          description: $r('app.string.management_user_hidden_desc'),
          value: this.ui.profile.isHidden,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.isHidden = value
            this.commitProfile(next)
          }
        })
      } else if (this.kind === ManagementProfileGroupKind.Privileges) {
        ManagementProfileGroupHeader({
          title: $r('app.string.management_user_group_privileges'),
          description: $r('app.string.management_user_group_privileges_desc'),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_privilege_collections'),
          description: $r('app.string.management_user_privilege_collections_desc'),
          value: this.ui.profile.enableCollectionManagement,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableCollectionManagement = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_privilege_subtitles'),
          description: $r('app.string.management_user_privilege_subtitles_desc'),
          value: this.ui.profile.enableSubtitleManagement,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableSubtitleManagement = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_privilege_lyrics'),
          description: $r('app.string.management_user_privilege_lyrics_desc'),
          value: this.ui.profile.enableLyricManagement,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableLyricManagement = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_privilege_live_tv'),
          description: $r('app.string.management_user_privilege_live_tv_desc'),
          value: this.ui.profile.enableLiveTvManagement,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableLiveTvManagement = value
            this.commitProfile(next)
          }
        })
      } else if (this.kind === ManagementProfileGroupKind.Playback) {
        ManagementProfileGroupHeader({
          title: $r('app.string.management_user_group_playback'),
          description: $r('app.string.management_user_group_playback_desc'),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_playback_media'),
          description: $r('app.string.management_user_playback_media_desc'),
          value: this.ui.profile.enableMediaPlayback,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableMediaPlayback = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_playback_live_tv'),
          description: $r('app.string.management_user_playback_live_tv_desc'),
          value: this.ui.profile.enableLiveTvAccess,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableLiveTvAccess = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_transcode_audio'),
          description: $r('app.string.management_user_transcode_audio_desc'),
          value: this.ui.profile.enableAudioPlaybackTranscoding,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableAudioPlaybackTranscoding = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_transcode_video'),
          description: $r('app.string.management_user_transcode_video_desc'),
          value: this.ui.profile.enableVideoPlaybackTranscoding,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableVideoPlaybackTranscoding = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_playback_remux'),
          description: $r('app.string.management_user_playback_remux_desc'),
          value: this.ui.profile.enablePlaybackRemuxing,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enablePlaybackRemuxing = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_force_remote_transcode'),
          description: $r('app.string.management_user_force_remote_transcode_desc'),
          value: this.ui.profile.forceRemoteSourceTranscoding,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.forceRemoteSourceTranscoding = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_content_download'),
          description: $r('app.string.management_user_content_download_desc'),
          value: this.ui.profile.enableContentDownloading,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableContentDownloading = value
            this.commitProfile(next)
          }
        })
      } else if (this.kind === ManagementProfileGroupKind.Remote) {
        ManagementProfileGroupHeader({
          title: $r('app.string.management_user_group_remote'),
          description: $r('app.string.management_user_group_remote_desc'),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_remote_access'),
          description: $r('app.string.management_user_remote_access_desc'),
          value: this.ui.profile.enableRemoteAccess,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableRemoteAccess = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_remote_control'),
          description: $r('app.string.management_user_remote_control_desc'),
          value: this.ui.profile.enableRemoteControlOfOtherUsers,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableRemoteControlOfOtherUsers = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_shared_device_control'),
          description: $r('app.string.management_user_shared_device_control_desc'),
          value: this.ui.profile.enableSharedDeviceControl,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableSharedDeviceControl = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_delete_all_content'),
          description: $r('app.string.management_user_delete_all_content_desc'),
          value: this.ui.profile.enableContentDeletion,
          controlEnabled: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.enableContentDeletion = value
            this.commitProfile(next)
          }
        })
        if (!this.ui.profile.enableContentDeletion) {
          Text($r('app.string.management_user_delete_folders'))
            .fontSize(13)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.primaryTextColor())
            .width('100%')
            .margin({ top: 4 })
          if (this.ui.foldersStatus === ManagementUserOperationStatus.Error) {
            Text($r('app.string.management_user_access_options_failed'))
              .fontSize(12)
              .fontColor($r('app.color.main_red_color'))
              .width('100%')
              .padding({ top: 12, bottom: 12 })
          } else if (this.ui.folders.length === 0) {
            Text($r('app.string.management_user_access_empty'))
              .fontSize(12)
              .fontColor(this.secondaryTextColor())
              .width('100%')
              .padding({ top: 12, bottom: 12 })
          } else {
            ForEach(this.ui.folders, (option: ManagementUserOption) => {
              ManagementProfileDeletionFolderRow({
                option,
                selected: this.ui.profile.contentDeletionFolderIds.includes(option.id),
                primaryColor: this.primaryTextColor(),
                onChange: (checked: boolean) => {
                  const next = this.copyProfile()
                  if (checked && !next.contentDeletionFolderIds.includes(option.id)) {
                    next.contentDeletionFolderIds = next.contentDeletionFolderIds.concat(option.id)
                  } else if (!checked) {
                    next.contentDeletionFolderIds = next.contentDeletionFolderIds
                      .filter((id: string) => id !== option.id)
                  }
                  this.commitProfile(next)
                }
              })
            }, (option: ManagementUserOption) => option.id)
          }
        }
      } else if (this.kind === ManagementProfileGroupKind.Limits) {
        ManagementProfileGroupHeader({
          title: $r('app.string.management_user_group_limits'),
          description: $r('app.string.management_user_group_limits_desc'),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        ManagementProfileNumericRow({
          title: $r('app.string.management_user_remote_bitrate'),
          description: $r('app.string.management_user_remote_bitrate_desc'),
          value: this.ui.profile.remoteClientBitrateLimitMbps,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          fieldColor: AppThemeSurfaceResolver.interactionOverlay(this.themeStyle),
          onChange: (value: number) => {
            const next = this.copyProfile()
            next.remoteClientBitrateLimitMbps = value
            this.commitProfile(next)
          }
        })
        ManagementProfileNumericRow({
          title: $r('app.string.management_user_lockout_attempts'),
          description: $r('app.string.management_user_lockout_attempts_desc'),
          value: this.ui.profile.loginAttemptsBeforeLockout,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          fieldColor: AppThemeSurfaceResolver.interactionOverlay(this.themeStyle),
          onChange: (value: number) => {
            const next = this.copyProfile()
            next.loginAttemptsBeforeLockout = Math.floor(value)
            this.commitProfile(next)
          }
        })
        ManagementProfileNumericRow({
          title: $r('app.string.management_user_max_sessions'),
          description: $r('app.string.management_user_max_sessions_desc'),
          value: this.ui.profile.maxActiveSessions,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          fieldColor: AppThemeSurfaceResolver.interactionOverlay(this.themeStyle),
          onChange: (value: number) => {
            const next = this.copyProfile()
            next.maxActiveSessions = Math.floor(value)
            this.commitProfile(next)
          }
        })
        Row({ space: 12 }) {
          Text($r('app.string.management_user_sync_play'))
            .fontSize(14)
            .fontColor(this.primaryTextColor())
            .layoutWeight(1)
          ManagementNativeSelect({
            options: this.syncPlayOptions(),
            selectedIndex: this.syncPlayIndex(),
            themeStyle: this.themeStyle,
            nativeThemeAvailable: this.nativeThemeAvailable,
            onSelect: (index: number) => {
              const next = this.copyProfile()
              next.syncPlayAccess = index === 0 ? SYNC_PLAY_NONE :
                (index === 1 ? SYNC_PLAY_JOIN : SYNC_PLAY_CREATE)
              this.commitProfile(next)
            }
          })
        }
        .width('100%')
        .constraintSize({ minHeight: 54 })
      } else {
        ManagementProfileGroupHeader({
          title: $r('app.string.management_user_group_status'),
          description: $r('app.string.management_user_group_status_desc'),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_administrator'),
          description: this.administratorLocked() ? this.protectionMessage() :
            $r('app.string.management_user_administrator_desc'),
          value: this.ui.profile.isAdministrator,
          controlEnabled: !this.administratorLocked(),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.isAdministrator = value
            this.commitProfile(next)
          }
        })
        ManagementProfileToggleRow({
          title: $r('app.string.management_user_disabled'),
          description: this.administratorLocked() ? this.protectionMessage() :
            $r('app.string.management_user_disabled_desc'),
          value: this.ui.profile.isDisabled,
          controlEnabled: !this.administratorLocked(),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          onChange: (value: boolean) => {
            const next = this.copyProfile()
            next.isDisabled = value
            this.commitProfile(next)
          }
        })
      }
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .clip(this.useNativeMaterial() && this.kind !== ManagementProfileGroupKind.Status)
    .backgroundColor(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
    .border({
      width: this.kind === ManagementProfileGroupKind.Status ?
        AppThemeSurfaceResolver.outlineWidth(this.themeStyle, this.nativeThemeAvailable, 1) : 0,
      color: this.kind === ManagementProfileGroupKind.Status ?
        $r('app.color.main_red_color') : Color.Transparent
    })
  }
}

@ComponentV2
export struct ManagementUserProfileSection {
  @Require @Param ui: ManagementUserDetailUIState
  @Require @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onChanged: () => void = () => {
  }

  build() {
    Column({ space: AppThemeTokens.groupSpacing(this.themeStyle) }) {
      ManagementProfileGroupSurface({
        kind: ManagementProfileGroupKind.Identity,
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onChanged: this.onChanged
      })
      ManagementProfileGroupSurface({
        kind: ManagementProfileGroupKind.Privileges,
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onChanged: this.onChanged
      })
      ManagementProfileGroupSurface({
        kind: ManagementProfileGroupKind.Playback,
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onChanged: this.onChanged
      })
      ManagementProfileGroupSurface({
        kind: ManagementProfileGroupKind.Remote,
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onChanged: this.onChanged
      })
      ManagementProfileGroupSurface({
        kind: ManagementProfileGroupKind.Limits,
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onChanged: this.onChanged
      })
      ManagementProfileGroupSurface({
        kind: ManagementProfileGroupKind.Status,
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onChanged: this.onChanged
      })
    }
    .width('100%')
  }
}
