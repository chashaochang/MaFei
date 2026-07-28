import { DeviceInfo } from 'sdk/src/main/ets/generated-client/models/device-info';
import {
  AccessSchedule,
  BaseItemDto,
  NameIdPair,
  ParentalRating,
  UserDto,
  UserPolicy
} from 'sdk/src/main/ets/generated-client/models';
import { ApiClient } from '../../network/ApiClient';
import { ObjectUtil } from '../../utils/ObjectUtil';
import { ManagementErrorCode } from './ManagementModels';
import { ManagementUserApiService } from './ManagementUserApiService';
import {
  ManagementAccessDraft,
  ManagementAccessScheduleItem,
  ManagementParentalDraft,
  ManagementParentalRatingOption,
  ManagementProfileDraft,
  ManagementUserActionCode,
  ManagementUserActionResult,
  ManagementUserCreateDraft,
  ManagementUserEditorSnapshot,
  ManagementUserItem,
  ManagementUserOption
} from './ManagementUserModels';
import { ManagementUserPolicy } from './ManagementUserPolicy';

interface ManagementUserHttpResponse {
  status: number
  data: string | ManagementUserServerErrorPayload
}

interface ManagementUserHttpError {
  isAxiosError?: boolean
  response?: ManagementUserHttpResponse
}

interface ManagementUserServerErrorPayload {
  Message?: string
  message?: string
  Error?: string
  error?: string
  Detail?: string
  detail?: string
  Title?: string
  title?: string
}

export class ManagementUserRepository {
  private service: ManagementUserApiService

  constructor(service: ManagementUserApiService = new ManagementUserApiService()) {
    this.service = service
  }

  isApiClientInitialized(): boolean {
    return this.service.isApiClientInitialized()
  }

  async checkCurrentUserAccess(): Promise<ManagementErrorCode> {
    try {
      const current = await this.service.getCurrentUser()
      return current.Policy?.IsAdministrator === true ? ManagementErrorCode.None :
        ManagementErrorCode.PermissionDenied
    } catch (error) {
      return this.classifyError(error as Object)
    }
  }

  async loadUsers(): Promise<ManagementUserItem[]> {
    const users = await this.service.getUsers()
    return users.map((user: UserDto) => this.mapUser(user))
  }

  async loadEditor(userId: string): Promise<ManagementUserEditorSnapshot> {
    const currentUser = await this.service.getCurrentUser()
    const user = await this.service.getUser(userId)
    const snapshot = this.mapEditor(currentUser.Id || '', user)
    snapshot.authenticationProviders = this.mapNameOptions(await this.service.getAuthProviders())
    snapshot.passwordResetProviders = this.mapNameOptions(
      await this.service.getPasswordResetProviders())
    snapshot.folders = this.mapItemOptions(await this.service.getMediaFolders())
    snapshot.channels = this.mapItemOptions(await this.service.getChannels())
    snapshot.devices = this.mapDeviceOptions(await this.service.getDevices())
    snapshot.parentalRatings = this.mapParentalRatings(await this.service.getParentalRatings())
    return snapshot
  }

  async saveProfile(userId: string, currentUserId: string,
    draft: ManagementProfileDraft): Promise<ManagementUserActionResult> {
    if (draft.name.trim().length === 0) {
      return this.failureResult(ManagementUserActionCode.ValidationFailed, userId,
        'user_name_required')
    }
    try {
      const users = await this.service.getUsers()
      const current = await this.service.getUser(userId)
      const protection = ManagementUserPolicy.validateProfileChange(
        currentUserId, current, users, draft)
      if (!protection.allowed) {
        return this.failureResult(ManagementUserActionCode.ProtectedAdministrator,
          userId, protection.message)
      }
      const updatedUser = ObjectUtil.Assign({}, current) as UserDto
      updatedUser.Name = draft.name.trim()
      await this.service.updateUser(userId, updatedUser)
      const currentAfterName = await this.service.getUser(userId)
      const nextPolicy = ManagementUserPolicy.patchProfile(
        this.requirePolicy(currentAfterName), draft)
      await this.service.updatePolicy(userId, nextPolicy)
      await this.service.getUser(userId)
      return this.successResult(ManagementUserActionCode.Saved, userId)
    } catch (error) {
      return this.errorResult(error as Object, userId)
    }
  }

  async saveAccess(userId: string, draft: ManagementAccessDraft): Promise<ManagementUserActionResult> {
    try {
      const current = await this.service.getUser(userId)
      const nextPolicy = ManagementUserPolicy.patchAccess(this.requirePolicy(current), draft)
      await this.service.updatePolicy(userId, nextPolicy)
      await this.service.getUser(userId)
      return this.successResult(ManagementUserActionCode.Saved, userId)
    } catch (error) {
      return this.errorResult(error as Object, userId)
    }
  }

  async saveParental(userId: string,
    draft: ManagementParentalDraft): Promise<ManagementUserActionResult> {
    const validation = ManagementUserPolicy.validateParental(draft)
    if (validation.length > 0) {
      return this.failureResult(ManagementUserActionCode.ValidationFailed, userId, validation)
    }
    try {
      const current = await this.service.getUser(userId)
      const nextPolicy = ManagementUserPolicy.patchParental(this.requirePolicy(current), draft)
      await this.service.updatePolicy(userId, nextPolicy)
      await this.service.getUser(userId)
      return this.successResult(ManagementUserActionCode.Saved, userId)
    } catch (error) {
      return this.errorResult(error as Object, userId)
    }
  }

  async updatePassword(userId: string, currentPassword: string,
    newPassword: string, reset: boolean): Promise<ManagementUserActionResult> {
    try {
      await this.service.updatePassword(userId, currentPassword, newPassword, reset)
      await this.service.getUser(userId)
      return this.successResult(ManagementUserActionCode.PasswordUpdated, userId)
    } catch (error) {
      return this.errorResult(error as Object, userId)
    }
  }

  async uploadAvatar(userId: string, bytes: ArrayBuffer,
    contentType: string): Promise<ManagementUserActionResult> {
    try {
      await this.service.uploadUserImage(userId, bytes, contentType)
      await this.service.getUser(userId)
      return this.successResult(ManagementUserActionCode.AvatarUpdated, userId)
    } catch (error) {
      return this.errorResult(error as Object, userId)
    }
  }

  async removeAvatar(userId: string): Promise<ManagementUserActionResult> {
    try {
      await this.service.removeUserImage(userId)
      await this.service.getUser(userId)
      return this.successResult(ManagementUserActionCode.AvatarUpdated, userId)
    } catch (error) {
      return this.errorResult(error as Object, userId)
    }
  }

  async deleteUser(userId: string, currentUserId: string): Promise<ManagementUserActionResult> {
    try {
      const users = await this.service.getUsers()
      const target = await this.service.getUser(userId)
      const protection = ManagementUserPolicy.canDelete(
        userId,
        currentUserId,
        target.Policy?.IsAdministrator === true,
        target.Policy?.IsDisabled !== true,
        ManagementUserPolicy.countEnabledAdministrators(users)
      )
      if (!protection.allowed) {
        return this.failureResult(ManagementUserActionCode.ProtectedAdministrator,
          userId, protection.message)
      }
      await this.service.deleteUser(userId)
      await this.service.getUsers()
      return this.successResult(ManagementUserActionCode.Deleted, userId)
    } catch (error) {
      return this.errorResult(error as Object, userId)
    }
  }

  async createUser(draft: ManagementUserCreateDraft): Promise<ManagementUserActionResult> {
    const name = draft.name.trim()
    if (name.length === 0) {
      return this.failureResult(ManagementUserActionCode.ValidationFailed, '', 'user_name_required')
    }
    let created: UserDto
    try {
      created = await this.service.createUser(name, draft.password)
    } catch (error) {
      return this.errorResult(error as Object, '')
    }
    const userId = created.Id || ''
    if (userId.length === 0) {
      return this.failureResult(ManagementUserActionCode.RequestFailed, '')
    }
    try {
      const latest = await this.service.getUser(created.Id!)
      const policy = ManagementUserPolicy.patchAccess(this.requirePolicy(latest), draft.access)
      await this.service.updatePolicy(created.Id!, policy)
      await this.service.getUser(created.Id!)
      return this.successResult(ManagementUserActionCode.Created, userId)
    } catch (error) {
      return this.failureResult(ManagementUserActionCode.CreatedPolicyPending,
        userId, this.getServerErrorMessage(error as Object))
    }
  }

  private requirePolicy(user: UserDto): UserPolicy {
    if (!user.Policy) {
      throw Error('Jellyfin user policy is missing')
    }
    return user.Policy
  }

  private mapUser(user: UserDto): ManagementUserItem {
    const result = new ManagementUserItem()
    result.id = user.Id || ''
    result.name = user.Name || ''
    result.avatarTag = user.PrimaryImageTag || ''
    result.avatarUrl = this.userImageUrl(result.id, result.avatarTag)
    result.administrator = user.Policy?.IsAdministrator === true
    result.disabled = user.Policy?.IsDisabled === true
    result.hidden = user.Policy?.IsHidden === true
    result.hasPassword = user.HasPassword === true || user.HasConfiguredPassword === true
    result.lastLoginDate = user.LastLoginDate || ''
    result.lastActivityDate = user.LastActivityDate || ''
    return result
  }

  private mapEditor(currentUserId: string, user: UserDto): ManagementUserEditorSnapshot {
    const snapshot = new ManagementUserEditorSnapshot()
    snapshot.currentUserId = currentUserId
    snapshot.user = this.mapUser(user)
    const policy = this.requirePolicy(user)
    snapshot.profile = this.mapProfile(user, policy)
    snapshot.access = this.mapAccess(policy)
    snapshot.parental = this.mapParental(policy)
    return snapshot
  }

  private mapProfile(user: UserDto, policy: UserPolicy): ManagementProfileDraft {
    const draft = new ManagementProfileDraft()
    draft.name = user.Name || ''
    draft.authenticationProviderId = policy.AuthenticationProviderId
    draft.passwordResetProviderId = policy.PasswordResetProviderId
    draft.isAdministrator = policy.IsAdministrator === true
    draft.isDisabled = policy.IsDisabled === true
    draft.isHidden = policy.IsHidden === true
    draft.enableCollectionManagement = policy.EnableCollectionManagement === true
    draft.enableSubtitleManagement = policy.EnableSubtitleManagement === true
    draft.enableLyricManagement = policy.EnableLyricManagement === true
    draft.enableLiveTvManagement = policy.EnableLiveTvManagement === true
    draft.enableLiveTvAccess = policy.EnableLiveTvAccess === true
    draft.enableMediaPlayback = policy.EnableMediaPlayback === true
    draft.enableAudioPlaybackTranscoding = policy.EnableAudioPlaybackTranscoding === true
    draft.enableVideoPlaybackTranscoding = policy.EnableVideoPlaybackTranscoding === true
    draft.enablePlaybackRemuxing = policy.EnablePlaybackRemuxing === true
    draft.forceRemoteSourceTranscoding = policy.ForceRemoteSourceTranscoding === true
    draft.enableContentDownloading = policy.EnableContentDownloading === true
    draft.enableRemoteAccess = policy.EnableRemoteAccess !== false
    draft.enableRemoteControlOfOtherUsers = policy.EnableRemoteControlOfOtherUsers === true
    draft.enableSharedDeviceControl = policy.EnableSharedDeviceControl === true
    draft.enableContentDeletion = policy.EnableContentDeletion === true
    draft.contentDeletionFolderIds = (policy.EnableContentDeletionFromFolders || []).slice()
    draft.remoteClientBitrateLimitMbps = Math.max(0, policy.RemoteClientBitrateLimit || 0) / 1000000
    draft.loginAttemptsBeforeLockout = Math.max(0, policy.LoginAttemptsBeforeLockout || 0)
    draft.maxActiveSessions = Math.max(0, policy.MaxActiveSessions || 0)
    draft.syncPlayAccess = policy.SyncPlayAccess || ''
    return draft
  }

  private mapAccess(policy: UserPolicy): ManagementAccessDraft {
    const draft = new ManagementAccessDraft()
    draft.enableAllFolders = policy.EnableAllFolders !== false
    draft.enabledFolderIds = (policy.EnabledFolders || []).slice()
    draft.enableAllChannels = policy.EnableAllChannels !== false
    draft.enabledChannelIds = (policy.EnabledChannels || []).slice()
    draft.enableAllDevices = policy.EnableAllDevices !== false
    draft.enabledDeviceIds = (policy.EnabledDevices || []).slice()
    return draft
  }

  private mapParental(policy: UserPolicy): ManagementParentalDraft {
    const draft = new ManagementParentalDraft()
    draft.maxParentalRating = policy.MaxParentalRating ?? null
    draft.blockedUnratedItems = (policy.BlockUnratedItems || [])
      .map((item) => item.toString())
    draft.allowedTags = (policy.AllowedTags || []).slice()
    draft.blockedTags = (policy.BlockedTags || []).slice()
    draft.schedules = (policy.AccessSchedules || [])
      .map((schedule: AccessSchedule) => this.mapSchedule(schedule))
    return draft
  }

  private mapSchedule(schedule: AccessSchedule): ManagementAccessScheduleItem {
    const result = new ManagementAccessScheduleItem()
    result.id = schedule.Id || 0
    result.dayOfWeek = schedule.DayOfWeek || 'Everyday'
    result.startHour = schedule.StartHour || 0
    result.endHour = schedule.EndHour ?? 24
    return result
  }

  private mapNameOptions(items: NameIdPair[]): ManagementUserOption[] {
    return items.map((item: NameIdPair) => {
      const option = new ManagementUserOption()
      option.id = item.Id || ''
      option.name = item.Name || option.id
      return option
    })
  }

  private mapItemOptions(items: BaseItemDto[]): ManagementUserOption[] {
    return items.map((item: BaseItemDto) => {
      const option = new ManagementUserOption()
      option.id = item.Id || ''
      option.name = item.Name || option.id
      return option
    })
  }

  private mapDeviceOptions(items: DeviceInfo[]): ManagementUserOption[] {
    return items.map((item: DeviceInfo) => {
      const option = new ManagementUserOption()
      option.id = item.Id || ''
      option.name = item.CustomName || item.Name || option.id
      option.detail = item.AppName || ''
      return option
    })
  }

  private mapParentalRatings(items: ParentalRating[]): ManagementParentalRatingOption[] {
    return items.map((item: ParentalRating) => {
      const option = new ManagementParentalRatingOption()
      option.name = item.Name || ''
      option.value = item.Value ?? null
      return option
    })
  }

  private userImageUrl(userId: string, tag: string): string {
    if (userId.length === 0 || tag.length === 0 || !ApiClient.isInitialized()) {
      return ''
    }
    const api = ApiClient.Instance()
    return `${api.basePath}/Users/${encodeURIComponent(userId)}/Images/Primary?tag=${
      encodeURIComponent(tag)}`
  }

  private successResult(code: ManagementUserActionCode,
    userId: string): ManagementUserActionResult {
    const result = new ManagementUserActionResult()
    result.ok = true
    result.code = code
    result.userId = userId
    return result
  }

  private failureResult(code: ManagementUserActionCode, userId: string,
    message: string = ''): ManagementUserActionResult {
    const result = new ManagementUserActionResult()
    result.code = code
    result.userId = userId
    result.message = message
    return result
  }

  private errorResult(error: Object, userId: string): ManagementUserActionResult {
    const code = this.classifyActionError(error)
    return this.failureResult(code, userId, this.getServerErrorMessage(error))
  }

  private classifyActionError(error: Object): ManagementUserActionCode {
    const status = this.getHttpStatus(error)
    if (status === 401) {
      return ManagementUserActionCode.AuthenticationExpired
    }
    if (status === 403) {
      return ManagementUserActionCode.PermissionDenied
    }
    return this.isOfflineError(error) ? ManagementUserActionCode.Offline :
      ManagementUserActionCode.RequestFailed
  }

  private classifyError(error: Object): ManagementErrorCode {
    const status = this.getHttpStatus(error)
    if (status === 401) {
      return ManagementErrorCode.AuthenticationExpired
    }
    if (status === 403) {
      return ManagementErrorCode.PermissionDenied
    }
    return this.isOfflineError(error) ? ManagementErrorCode.Offline : ManagementErrorCode.RequestFailed
  }

  private getHttpStatus(error: Object): number | undefined {
    return (error as ManagementUserHttpError).response?.status
  }

  private isOfflineError(error: Object): boolean {
    const httpError = error as ManagementUserHttpError
    return httpError.isAxiosError === true && httpError.response === undefined
  }

  private getServerErrorMessage(error: Object): string {
    const data = (error as ManagementUserHttpError).response?.data
    if (typeof data === 'string') {
      return this.normalizeServerMessage(data)
    }
    if (data) {
      return this.normalizeServerMessage(data.Message || data.message || data.Error || data.error ||
        data.Detail || data.detail || data.Title || data.title)
    }
    return ''
  }

  private normalizeServerMessage(message: string | undefined): string {
    if (!message) {
      return ''
    }
    const normalized = message.replace(/\s+/g, ' ').trim()
    const lowerCase = normalized.toLowerCase()
    if (lowerCase.includes('<html') || lowerCase.includes('<!doctype')) {
      return ''
    }
    return normalized.length > 240 ? `${normalized.slice(0, 237)}...` : normalized
  }
}
