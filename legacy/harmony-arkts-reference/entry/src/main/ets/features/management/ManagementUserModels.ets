export enum ManagementUserRoleFilter {
  All,
  Administrators,
  Standard
}

export enum ManagementUserStatusFilter {
  All,
  Enabled,
  Disabled
}

export enum ManagementUserVisibilityFilter {
  All,
  Visible,
  Hidden
}

export enum ManagementUserSortField {
  Name,
  LastActivity
}

export enum ManagementUserTab {
  Profile,
  Access,
  Parental,
  Password
}

export enum ManagementUserOperationStatus {
  Idle,
  Loading,
  Saving,
  Success,
  Error,
  Unsupported
}

export enum ManagementUserActionCode {
  None,
  Created,
  CreatedPolicyPending,
  Saved,
  PasswordUpdated,
  AvatarUpdated,
  Deleted,
  AuthenticationExpired,
  PermissionDenied,
  Offline,
  ValidationFailed,
  ProtectedAdministrator,
  RequestFailed
}

export class ManagementUserItem {
  /** Jellyfin user identifier. */
  id: string = ''
  /** Display name returned by Jellyfin. */
  name: string = ''
  /** Authenticated primary-image URL. */
  avatarUrl: string = ''
  /** Primary image tag used to invalidate caches. */
  avatarTag: string = ''
  /** Whether the user currently has administrator rights. */
  administrator: boolean = false
  /** Whether login is disabled. */
  disabled: boolean = false
  /** Whether the user is hidden from login screens. */
  hidden: boolean = false
  /** Whether a password is configured. */
  hasPassword: boolean = false
  /** Last successful login timestamp. */
  lastLoginDate: string = ''
  /** Last server activity timestamp. */
  lastActivityDate: string = ''
}

export class ManagementUserActionResult {
  /** Whether the requested operation fully completed. */
  ok: boolean = false
  /** Stable result code consumed by ViewModels. */
  code: ManagementUserActionCode = ManagementUserActionCode.None
  /** Affected user identifier, including partial create success. */
  userId: string = ''
  /** Sanitized server detail for display. */
  message: string = ''
}

export class ManagementProfileDraft {
  /** Editable user name. */
  name: string = ''
  /** Authentication provider identifier. */
  authenticationProviderId: string = ''
  /** Password reset provider identifier. */
  passwordResetProviderId: string = ''
  /** Administrator privilege toggle. */
  isAdministrator: boolean = false
  /** Login disabled toggle. */
  isDisabled: boolean = false
  /** Login-screen visibility toggle. */
  isHidden: boolean = false
  /** Collection management privilege. */
  enableCollectionManagement: boolean = false
  /** Subtitle management privilege. */
  enableSubtitleManagement: boolean = false
  /** Lyric management privilege. */
  enableLyricManagement: boolean = false
  /** Live TV management privilege. */
  enableLiveTvManagement: boolean = false
  /** Live TV playback privilege. */
  enableLiveTvAccess: boolean = false
  /** General media playback privilege. */
  enableMediaPlayback: boolean = false
  /** Audio transcoding privilege. */
  enableAudioPlaybackTranscoding: boolean = false
  /** Video transcoding privilege. */
  enableVideoPlaybackTranscoding: boolean = false
  /** Remux playback privilege. */
  enablePlaybackRemuxing: boolean = false
  /** Force transcoding for remote sources. */
  forceRemoteSourceTranscoding: boolean = false
  /** Content download privilege. */
  enableContentDownloading: boolean = false
  /** Remote server access privilege. */
  enableRemoteAccess: boolean = true
  /** Remote-control privilege for other users. */
  enableRemoteControlOfOtherUsers: boolean = false
  /** Shared-device control privilege. */
  enableSharedDeviceControl: boolean = false
  /** Global content deletion privilege. */
  enableContentDeletion: boolean = false
  /** Folder-specific deletion privileges. */
  contentDeletionFolderIds: string[] = []
  /** Remote client bitrate limit in megabits per second. */
  remoteClientBitrateLimitMbps: number = 0
  /** Failed login attempts before lockout. */
  loginAttemptsBeforeLockout: number = 0
  /** Maximum simultaneous sessions. */
  maxActiveSessions: number = 0
  /** SyncPlay access value from Jellyfin. */
  syncPlayAccess: string = ''
}

export class ManagementAccessDraft {
  /** Grants access to every media folder. */
  enableAllFolders: boolean = true
  /** Explicitly enabled folder identifiers. */
  enabledFolderIds: string[] = []
  /** Grants access to every channel. */
  enableAllChannels: boolean = true
  /** Explicitly enabled channel identifiers. */
  enabledChannelIds: string[] = []
  /** Grants access from every device. */
  enableAllDevices: boolean = true
  /** Explicitly enabled device identifiers. */
  enabledDeviceIds: string[] = []
}

export class ManagementAccessScheduleItem {
  /** Server schedule identifier when present. */
  id: number = 0
  /** Dynamic Jellyfin day value. */
  dayOfWeek: string = 'Everyday'
  /** Inclusive start hour. */
  startHour: number = 0
  /** Exclusive end hour. */
  endHour: number = 24
}

export class ManagementParentalDraft {
  /** Maximum parental rating; null disables the rating ceiling. */
  maxParentalRating: number | null = null
  /** Unrated media categories that are blocked. */
  blockedUnratedItems: string[] = []
  /** Tags that explicitly allow matching content. */
  allowedTags: string[] = []
  /** Tags that explicitly block matching content. */
  blockedTags: string[] = []
  /** Time-based access schedules. */
  schedules: ManagementAccessScheduleItem[] = []
}

export class ManagementPasswordDraft {
  /** Current password used only for an in-memory submission. */
  currentPassword: string = ''
  /** New password used only for an in-memory submission. */
  newPassword: string = ''
  /** Confirmation used only for local validation. */
  confirmPassword: string = ''

  clear(): void {
    this.currentPassword = ''
    this.newPassword = ''
    this.confirmPassword = ''
  }
}

export class ManagementUserOption {
  /** Jellyfin option identifier. */
  id: string = ''
  /** Primary option label. */
  name: string = ''
  /** Optional supporting label. */
  detail: string = ''
}

export class ManagementParentalRatingOption {
  /** Jellyfin rating label. */
  name: string = ''
  /** Numeric server rating value. */
  value: number | null = null
}

export class ManagementAdministratorProtectionResult {
  /** Whether the dangerous operation is allowed. */
  allowed: boolean = true
  /** Stable reason code when blocked. */
  message: string = ''
}

export class ManagementUserEditorSnapshot {
  /** Current authenticated administrator identifier. */
  currentUserId: string = ''
  /** Edited user summary. */
  user: ManagementUserItem = new ManagementUserItem()
  /** Number of administrators that are both enabled and able to sign in. */
  enabledAdministratorCount: number = 0
  /** Profile-section draft. */
  profile: ManagementProfileDraft = new ManagementProfileDraft()
  /** Access-section draft. */
  access: ManagementAccessDraft = new ManagementAccessDraft()
  /** Parental-section draft. */
  parental: ManagementParentalDraft = new ManagementParentalDraft()
  /** Authentication provider options. */
  authenticationProviders: ManagementUserOption[] = []
  /** Password reset provider options. */
  passwordResetProviders: ManagementUserOption[] = []
  /** Media folder options. */
  folders: ManagementUserOption[] = []
  /** Independent media-folder option loading result. */
  foldersStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  /** Channel options. */
  channels: ManagementUserOption[] = []
  /** Independent channel option loading result. */
  channelsStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  /** Device options. */
  devices: ManagementUserOption[] = []
  /** Independent device option loading result. */
  devicesStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  /** Parental rating options. */
  parentalRatings: ManagementParentalRatingOption[] = []
}

export class ManagementUserCreateDraft {
  /** Required user name. */
  name: string = ''
  /** Optional initial password kept only in memory. */
  password: string = ''
  /** Initial access policy. */
  access: ManagementAccessDraft = new ManagementAccessDraft()
}
