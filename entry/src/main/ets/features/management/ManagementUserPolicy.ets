import {
  AccessSchedule,
  DynamicDayOfWeek,
  SyncPlayUserAccessType,
  UnratedItem,
  UserDto,
  UserPolicy
} from 'sdk/src/main/ets/generated-client/models';
import { ObjectUtil } from '../../utils/ObjectUtil';
import {
  ManagementAccessDraft,
  ManagementAccessScheduleItem,
  ManagementAdministratorProtectionResult,
  ManagementParentalDraft,
  ManagementProfileDraft
} from './ManagementUserModels';

const CURRENT_ADMINISTRATOR_PROTECTED: string = 'current_administrator_protected'
const LAST_ADMINISTRATOR_PROTECTED: string = 'last_enabled_administrator_protected'

export class ManagementUserPolicy {
  static clonePolicy(policy: UserPolicy): UserPolicy {
    return ObjectUtil.Assign({}, policy) as UserPolicy
  }

  static patchProfile(policy: UserPolicy, draft: ManagementProfileDraft): UserPolicy {
    const next = ManagementUserPolicy.clonePolicy(policy)
    next.IsAdministrator = draft.isAdministrator
    next.IsDisabled = draft.isDisabled
    next.IsHidden = draft.isHidden
    next.EnableCollectionManagement = draft.enableCollectionManagement
    next.EnableSubtitleManagement = draft.enableSubtitleManagement
    next.EnableLyricManagement = draft.enableLyricManagement
    next.EnableLiveTvManagement = draft.enableLiveTvManagement
    next.EnableLiveTvAccess = draft.enableLiveTvAccess
    next.EnableMediaPlayback = draft.enableMediaPlayback
    next.EnableAudioPlaybackTranscoding = draft.enableAudioPlaybackTranscoding
    next.EnableVideoPlaybackTranscoding = draft.enableVideoPlaybackTranscoding
    next.EnablePlaybackRemuxing = draft.enablePlaybackRemuxing
    next.ForceRemoteSourceTranscoding = draft.forceRemoteSourceTranscoding
    next.EnableContentDownloading = draft.enableContentDownloading
    next.EnableRemoteAccess = draft.enableRemoteAccess
    next.EnableRemoteControlOfOtherUsers = draft.enableRemoteControlOfOtherUsers
    next.EnableSharedDeviceControl = draft.enableSharedDeviceControl
    next.EnableContentDeletion = draft.enableContentDeletion
    next.EnableContentDeletionFromFolders = draft.contentDeletionFolderIds.slice()
    next.RemoteClientBitrateLimit = Math.max(0,
      Math.floor(draft.remoteClientBitrateLimitMbps * 1000000))
    next.LoginAttemptsBeforeLockout = Math.max(0, Math.floor(draft.loginAttemptsBeforeLockout))
    next.MaxActiveSessions = Math.max(0, Math.floor(draft.maxActiveSessions))
    next.AuthenticationProviderId = draft.authenticationProviderId
    next.PasswordResetProviderId = draft.passwordResetProviderId
    next.SyncPlayAccess = ManagementUserPolicy.syncPlayValue(draft.syncPlayAccess)
    return next
  }

  static patchAccess(policy: UserPolicy, draft: ManagementAccessDraft): UserPolicy {
    const next = ManagementUserPolicy.clonePolicy(policy)
    next.EnableAllFolders = draft.enableAllFolders
    next.EnabledFolders = draft.enabledFolderIds.slice()
    next.EnableAllChannels = draft.enableAllChannels
    next.EnabledChannels = draft.enabledChannelIds.slice()
    next.EnableAllDevices = draft.enableAllDevices
    next.EnabledDevices = draft.enabledDeviceIds.slice()
    return next
  }

  static patchParental(policy: UserPolicy, draft: ManagementParentalDraft): UserPolicy {
    const next = ManagementUserPolicy.clonePolicy(policy)
    next.MaxParentalRating = draft.maxParentalRating
    next.BlockedTags = ManagementUserPolicy.normalizeTags(draft.blockedTags)
    next.AllowedTags = ManagementUserPolicy.normalizeTags(draft.allowedTags)
    next.BlockUnratedItems = draft.blockedUnratedItems.map((item: string) => item as UnratedItem)
    next.AccessSchedules = draft.schedules.map((item: ManagementAccessScheduleItem) => {
      const schedule: AccessSchedule = {
        Id: item.id > 0 ? item.id : undefined,
        DayOfWeek: item.dayOfWeek as DynamicDayOfWeek,
        StartHour: ManagementUserPolicy.clampHour(item.startHour),
        EndHour: ManagementUserPolicy.clampHour(item.endHour)
      }
      return schedule
    })
    return next
  }

  static validateParental(draft: ManagementParentalDraft): string {
    const allowed = ManagementUserPolicy.normalizeTags(draft.allowedTags)
    const blocked = ManagementUserPolicy.normalizeTags(draft.blockedTags)
    const conflicts = allowed.filter((tag: string) => blocked.includes(tag))
    if (conflicts.length > 0) {
      return 'conflicting_tags'
    }
    const invalidSchedule = draft.schedules.some((schedule: ManagementAccessScheduleItem) =>
      schedule.startHour < 0 || schedule.endHour > 24 || schedule.startHour >= schedule.endHour)
    return invalidSchedule ? 'invalid_schedule' : ''
  }

  static normalizeTags(tags: string[]): string[] {
    const result: string[] = []
    tags.forEach((value: string) => {
      const normalized = value.trim()
      if (normalized.length > 0 && !result.includes(normalized)) {
        result.push(normalized)
      }
    })
    return result
  }

  static countEnabledAdministrators(users: UserDto[]): number {
    return users.filter((user: UserDto) =>
      user.Policy?.IsAdministrator === true && user.Policy?.IsDisabled !== true).length
  }

  static validateProfileChange(currentUserId: string, target: UserDto, users: UserDto[],
    draft: ManagementProfileDraft): ManagementAdministratorProtectionResult {
    const targetId = target.Id || ''
    const currentPolicy = target.Policy
    const wasAdministrator = currentPolicy?.IsAdministrator === true
    const wasEnabled = currentPolicy?.IsDisabled !== true
    const enabledAdministrators = ManagementUserPolicy.countEnabledAdministrators(users)
    if (targetId === currentUserId && wasAdministrator &&
      (!draft.isAdministrator || draft.isDisabled)) {
      return ManagementUserPolicy.blocked(CURRENT_ADMINISTRATOR_PROTECTED)
    }
    if (wasAdministrator && wasEnabled && enabledAdministrators <= 1 &&
      (!draft.isAdministrator || draft.isDisabled)) {
      return ManagementUserPolicy.blocked(LAST_ADMINISTRATOR_PROTECTED)
    }
    return new ManagementAdministratorProtectionResult()
  }

  static canChangeAdministratorState(targetId: string, currentUserId: string,
    currentIsAdministrator: boolean, currentIsEnabled: boolean,
    nextIsAdministrator: boolean, enabledAdministrators: number): ManagementAdministratorProtectionResult {
    if (targetId === currentUserId && currentIsAdministrator && !nextIsAdministrator) {
      return ManagementUserPolicy.blocked(CURRENT_ADMINISTRATOR_PROTECTED)
    }
    if (currentIsAdministrator && currentIsEnabled && !nextIsAdministrator &&
      enabledAdministrators <= 1) {
      return ManagementUserPolicy.blocked(LAST_ADMINISTRATOR_PROTECTED)
    }
    return new ManagementAdministratorProtectionResult()
  }

  static canDelete(targetId: string, currentUserId: string, targetIsAdministrator: boolean,
    targetIsEnabled: boolean, enabledAdministrators: number): ManagementAdministratorProtectionResult {
    if (targetId === currentUserId) {
      return ManagementUserPolicy.blocked(CURRENT_ADMINISTRATOR_PROTECTED)
    }
    if (targetIsAdministrator && targetIsEnabled && enabledAdministrators <= 1) {
      return ManagementUserPolicy.blocked(LAST_ADMINISTRATOR_PROTECTED)
    }
    return new ManagementAdministratorProtectionResult()
  }

  private static blocked(message: string): ManagementAdministratorProtectionResult {
    const result = new ManagementAdministratorProtectionResult()
    result.allowed = false
    result.message = message
    return result
  }

  private static clampHour(value: number): number {
    return Math.max(0, Math.min(24, value))
  }

  private static syncPlayValue(value: string): SyncPlayUserAccessType {
    if (value === SyncPlayUserAccessType.JoinGroups) {
      return SyncPlayUserAccessType.JoinGroups
    }
    if (value === SyncPlayUserAccessType.None) {
      return SyncPlayUserAccessType.None
    }
    return SyncPlayUserAccessType.CreateAndJoinGroups
  }
}
