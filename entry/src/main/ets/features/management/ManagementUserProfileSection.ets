import { ThemeStyle } from '../../entity/ThemeStyle';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ObjectUtil } from '../../utils/ObjectUtil';
import { ManagementUserDetailUIState } from './ManagementUserDetailUIState';
import {
  ManagementProfileDraft,
  ManagementUserOperationStatus,
  ManagementUserOption
} from './ManagementUserModels';

const SYNC_PLAY_NONE: string = 'None'
const SYNC_PLAY_JOIN: string = 'JoinGroups'
const SYNC_PLAY_CREATE: string = 'CreateAndJoinGroups'

@ComponentV2
export struct ManagementUserProfileSection {
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

  private copyProfile(): ManagementProfileDraft {
    const next = ObjectUtil.Assign({}, this.ui.profile) as ManagementProfileDraft
    next.contentDeletionFolderIds = this.ui.profile.contentDeletionFolderIds.slice()
    return next
  }

  private commitProfile(next: ManagementProfileDraft): void {
    this.ui.profile = next
    this.onChanged()
  }

  private optionLabel(options: ManagementUserOption[], selectedId: string): string {
    const selected = options.find((option: ManagementUserOption) => option.id === selectedId)
    return selected?.name || selectedId || '-'
  }

  private syncPlayLabel(value: string): ResourceStr {
    if (value === SYNC_PLAY_NONE) {
      return $r('app.string.management_user_sync_play_none')
    }
    if (value === SYNC_PLAY_JOIN) {
      return $r('app.string.management_user_sync_play_join')
    }
    return $r('app.string.management_user_sync_play_create')
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
  private toggleRow(title: ResourceStr, description: ResourceStr, value: boolean,
    enabled: boolean, onChange: (value: boolean) => void) {
    Row({ space: 12 }) {
      Column({ space: 3 }) {
        Text(title)
          .fontSize(14)
          .fontColor(enabled ? this.primaryTextColor() : this.secondaryTextColor())
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
        .enabled(enabled)
        .onChange(onChange)
    }
    .width('100%')
    .constraintSize({ minHeight: 58 })
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  private providerMenu(options: ManagementUserOption[], selectedId: string,
    onSelect: (id: string) => void) {
    Menu() {
      ForEach(options, (option: ManagementUserOption) => {
        MenuItem({ content: option.name })
          .selectIcon(true)
          .selected(option.id === selectedId)
          .onClick(() => onSelect(option.id))
      }, (option: ManagementUserOption) => option.id)
    }
  }

  @Builder
  private providerRow(title: ResourceStr, options: ManagementUserOption[], selectedId: string,
    onSelect: (id: string) => void) {
    Row({ space: 12 }) {
      Text(title)
        .fontSize(14)
        .fontColor(this.primaryTextColor())
        .layoutWeight(1)
      Button() {
        Row({ space: 6 }) {
          Text(this.optionLabel(options, selectedId))
            .fontSize(13)
            .fontColor(this.primaryTextColor())
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          SymbolGlyph($r('sys.symbol.chevron_down'))
            .fontSize(12)
            .fontColor([this.secondaryTextColor()])
        }
      }
      .height(40)
      .constraintSize({ minWidth: 140, maxWidth: 260 })
      .padding({ left: 12, right: 12 })
      .borderRadius(8)
      .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
      .enabled(options.length > 0)
      .bindMenu(this.providerMenu(options, selectedId, onSelect))
    }
    .width('100%')
    .constraintSize({ minHeight: 54 })
  }

  @Builder
  private numericRow(title: ResourceStr, description: ResourceStr, value: number,
    onChange: (value: number) => void) {
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
      TextInput({ text: Math.max(0, value).toString() })
        .type(InputType.Number)
        .width(104)
        .height(40)
        .fontSize(14)
        .fontColor(this.primaryTextColor())
        .textAlign(TextAlign.End)
        .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
        .borderRadius(8)
        .onChange((text: string) => onChange(Math.max(0, Number(text) || 0)))
    }
    .width('100%')
    .constraintSize({ minHeight: 58 })
  }

  @Builder
  private deletionFolderRow(option: ManagementUserOption) {
    Row({ space: 12 }) {
      Toggle({
        type: ToggleType.Checkbox,
        isOn: this.ui.profile.contentDeletionFolderIds.includes(option.id)
      })
        .selectedColor($r('app.color.color_main'))
        .onChange((checked: boolean) => {
          const next = this.copyProfile()
          if (checked && !next.contentDeletionFolderIds.includes(option.id)) {
            next.contentDeletionFolderIds = next.contentDeletionFolderIds.concat(option.id)
          } else if (!checked) {
            next.contentDeletionFolderIds = next.contentDeletionFolderIds
              .filter((id: string) => id !== option.id)
          }
          this.commitProfile(next)
        })
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
  private identityContent() {
    Column({ space: 10 }) {
      this.groupHeader($r('app.string.management_user_group_identity'),
        $r('app.string.management_user_group_identity_desc'))
      Text($r('app.string.management_user_name'))
        .fontSize(13)
        .fontColor(this.secondaryTextColor())
        .width('100%')
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
      this.providerRow($r('app.string.management_user_auth_provider'),
        this.ui.authenticationProviders, this.ui.profile.authenticationProviderId, (id: string) => {
          const next = this.copyProfile()
          next.authenticationProviderId = id
          this.commitProfile(next)
        })
      this.providerRow($r('app.string.management_user_password_reset_provider'),
        this.ui.passwordResetProviders, this.ui.profile.passwordResetProviderId, (id: string) => {
          const next = this.copyProfile()
          next.passwordResetProviderId = id
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_hidden'),
        $r('app.string.management_user_hidden_desc'), this.ui.profile.isHidden, true,
        (value: boolean) => {
          const next = this.copyProfile()
          next.isHidden = value
          this.commitProfile(next)
        })
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private identityGroup() {
    Column() { this.identityContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuIdentityGroup() {
    Column() { this.identityContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private identitySurface() {
    if (this.useNativeMaterial()) {
      this.identityGroup()
    } else {
      this.feiniuIdentityGroup()
    }
  }

  @Builder
  private privilegesContent() {
    Column() {
      this.groupHeader($r('app.string.management_user_group_privileges'),
        $r('app.string.management_user_group_privileges_desc'))
      this.toggleRow($r('app.string.management_user_privilege_collections'),
        $r('app.string.management_user_privilege_collections_desc'),
        this.ui.profile.enableCollectionManagement, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableCollectionManagement = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_privilege_subtitles'),
        $r('app.string.management_user_privilege_subtitles_desc'),
        this.ui.profile.enableSubtitleManagement, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableSubtitleManagement = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_privilege_lyrics'),
        $r('app.string.management_user_privilege_lyrics_desc'),
        this.ui.profile.enableLyricManagement, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableLyricManagement = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_privilege_live_tv'),
        $r('app.string.management_user_privilege_live_tv_desc'),
        this.ui.profile.enableLiveTvManagement, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableLiveTvManagement = value
          this.commitProfile(next)
        })
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private privilegesGroup() {
    Column() { this.privilegesContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuPrivilegesGroup() {
    Column() { this.privilegesContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private privilegesSurface() {
    if (this.useNativeMaterial()) {
      this.privilegesGroup()
    } else {
      this.feiniuPrivilegesGroup()
    }
  }

  @Builder
  private playbackContent() {
    Column() {
      this.groupHeader($r('app.string.management_user_group_playback'),
        $r('app.string.management_user_group_playback_desc'))
      this.toggleRow($r('app.string.management_user_playback_media'),
        $r('app.string.management_user_playback_media_desc'),
        this.ui.profile.enableMediaPlayback, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableMediaPlayback = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_playback_live_tv'),
        $r('app.string.management_user_playback_live_tv_desc'),
        this.ui.profile.enableLiveTvAccess, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableLiveTvAccess = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_transcode_audio'),
        $r('app.string.management_user_transcode_audio_desc'),
        this.ui.profile.enableAudioPlaybackTranscoding, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableAudioPlaybackTranscoding = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_transcode_video'),
        $r('app.string.management_user_transcode_video_desc'),
        this.ui.profile.enableVideoPlaybackTranscoding, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableVideoPlaybackTranscoding = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_playback_remux'),
        $r('app.string.management_user_playback_remux_desc'),
        this.ui.profile.enablePlaybackRemuxing, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enablePlaybackRemuxing = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_force_remote_transcode'),
        $r('app.string.management_user_force_remote_transcode_desc'),
        this.ui.profile.forceRemoteSourceTranscoding, true, (value: boolean) => {
          const next = this.copyProfile()
          next.forceRemoteSourceTranscoding = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_content_download'),
        $r('app.string.management_user_content_download_desc'),
        this.ui.profile.enableContentDownloading, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableContentDownloading = value
          this.commitProfile(next)
        })
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private playbackGroup() {
    Column() { this.playbackContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuPlaybackGroup() {
    Column() { this.playbackContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private playbackSurface() {
    if (this.useNativeMaterial()) {
      this.playbackGroup()
    } else {
      this.feiniuPlaybackGroup()
    }
  }

  @Builder
  private remoteContent() {
    Column() {
      this.groupHeader($r('app.string.management_user_group_remote'),
        $r('app.string.management_user_group_remote_desc'))
      this.toggleRow($r('app.string.management_user_remote_access'),
        $r('app.string.management_user_remote_access_desc'),
        this.ui.profile.enableRemoteAccess, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableRemoteAccess = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_remote_control'),
        $r('app.string.management_user_remote_control_desc'),
        this.ui.profile.enableRemoteControlOfOtherUsers, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableRemoteControlOfOtherUsers = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_shared_device_control'),
        $r('app.string.management_user_shared_device_control_desc'),
        this.ui.profile.enableSharedDeviceControl, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableSharedDeviceControl = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_delete_all_content'),
        $r('app.string.management_user_delete_all_content_desc'),
        this.ui.profile.enableContentDeletion, true, (value: boolean) => {
          const next = this.copyProfile()
          next.enableContentDeletion = value
          this.commitProfile(next)
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
            this.deletionFolderRow(option)
          }, (option: ManagementUserOption) => option.id)
        }
      }
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private remoteGroup() {
    Column() { this.remoteContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuRemoteGroup() {
    Column() { this.remoteContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private remoteSurface() {
    if (this.useNativeMaterial()) {
      this.remoteGroup()
    } else {
      this.feiniuRemoteGroup()
    }
  }

  @Builder
  private syncPlayMenu() {
    Menu() {
      MenuItem({ content: $r('app.string.management_user_sync_play_none') })
        .selectIcon(true)
        .selected(this.ui.profile.syncPlayAccess === SYNC_PLAY_NONE)
        .onClick(() => {
          const next = this.copyProfile()
          next.syncPlayAccess = SYNC_PLAY_NONE
          this.commitProfile(next)
        })
      MenuItem({ content: $r('app.string.management_user_sync_play_join') })
        .selectIcon(true)
        .selected(this.ui.profile.syncPlayAccess === SYNC_PLAY_JOIN)
        .onClick(() => {
          const next = this.copyProfile()
          next.syncPlayAccess = SYNC_PLAY_JOIN
          this.commitProfile(next)
        })
      MenuItem({ content: $r('app.string.management_user_sync_play_create') })
        .selectIcon(true)
        .selected(this.ui.profile.syncPlayAccess === SYNC_PLAY_CREATE ||
          this.ui.profile.syncPlayAccess.length === 0)
        .onClick(() => {
          const next = this.copyProfile()
          next.syncPlayAccess = SYNC_PLAY_CREATE
          this.commitProfile(next)
        })
    }
  }

  @Builder
  private limitsContent() {
    Column() {
      this.groupHeader($r('app.string.management_user_group_limits'),
        $r('app.string.management_user_group_limits_desc'))
      this.numericRow($r('app.string.management_user_remote_bitrate'),
        $r('app.string.management_user_remote_bitrate_desc'),
        this.ui.profile.remoteClientBitrateLimitMbps, (value: number) => {
          const next = this.copyProfile()
          next.remoteClientBitrateLimitMbps = value
          this.commitProfile(next)
        })
      this.numericRow($r('app.string.management_user_lockout_attempts'),
        $r('app.string.management_user_lockout_attempts_desc'),
        this.ui.profile.loginAttemptsBeforeLockout, (value: number) => {
          const next = this.copyProfile()
          next.loginAttemptsBeforeLockout = Math.floor(value)
          this.commitProfile(next)
        })
      this.numericRow($r('app.string.management_user_max_sessions'),
        $r('app.string.management_user_max_sessions_desc'),
        this.ui.profile.maxActiveSessions, (value: number) => {
          const next = this.copyProfile()
          next.maxActiveSessions = Math.floor(value)
          this.commitProfile(next)
        })
      Row({ space: 12 }) {
        Text($r('app.string.management_user_sync_play'))
          .fontSize(14)
          .fontColor(this.primaryTextColor())
          .layoutWeight(1)
        Button() {
          Row({ space: 6 }) {
            Text(this.syncPlayLabel(this.ui.profile.syncPlayAccess))
              .fontSize(13)
              .fontColor(this.primaryTextColor())
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
            SymbolGlyph($r('sys.symbol.chevron_down'))
              .fontSize(12)
              .fontColor([this.secondaryTextColor()])
          }
        }
        .height(40)
        .constraintSize({ minWidth: 140, maxWidth: 260 })
        .padding({ left: 12, right: 12 })
        .borderRadius(8)
        .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
        .bindMenu(this.syncPlayMenu())
      }
      .width('100%')
      .constraintSize({ minHeight: 54 })
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private limitsGroup() {
    Column() { this.limitsContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuLimitsGroup() {
    Column() { this.limitsContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private limitsSurface() {
    if (this.useNativeMaterial()) {
      this.limitsGroup()
    } else {
      this.feiniuLimitsGroup()
    }
  }

  @Builder
  private dangerZone() {
    Column() {
      this.groupHeader($r('app.string.management_user_group_status'),
        $r('app.string.management_user_group_status_desc'))
      this.toggleRow($r('app.string.management_user_administrator'),
        this.administratorLocked() ? this.protectionMessage() :
          $r('app.string.management_user_administrator_desc'),
        this.ui.profile.isAdministrator, !this.administratorLocked(), (value: boolean) => {
          const next = this.copyProfile()
          next.isAdministrator = value
          this.commitProfile(next)
        })
      this.toggleRow($r('app.string.management_user_disabled'),
        this.administratorLocked() ? this.protectionMessage() :
          $r('app.string.management_user_disabled_desc'),
        this.ui.profile.isDisabled, !this.administratorLocked(), (value: boolean) => {
          const next = this.copyProfile()
          next.isDisabled = value
          this.commitProfile(next)
        })
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .backgroundColor(this.useNativeMaterial() ?
      $r('sys.color.comp_background_tertiary') : $r('app.color.bg_1'))
    .border({ width: 1, color: $r('app.color.main_red_color') })
  }

  build() {
    Column({ space: AppThemeTokens.groupSpacing(this.themeStyle) }) {
      this.identitySurface()
      this.privilegesSurface()
      this.playbackSurface()
      this.remoteSurface()
      this.limitsSurface()
      this.dangerZone()
    }
    .width('100%')
  }
}
