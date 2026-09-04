import { PageState } from '../../common/base/BaseUIState';
import { BaseViewModel } from '../../common/base/BaseViewModel';
import { fileIo } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { ManagementErrorCode, ManagementPermissionState } from './ManagementModels';
import { ManagementUserDetailUIState } from './ManagementUserDetailUIState';
import {
  ManagementAccessDraft,
  ManagementAccessScheduleItem,
  ManagementParentalDraft,
  ManagementPasswordDraft,
  ManagementProfileDraft,
  ManagementUserActionCode,
  ManagementUserActionResult,
  ManagementUserEditorSnapshot,
  ManagementUserOperationStatus,
  ManagementUserTab
} from './ManagementUserModels';
import {
  MAX_AVATAR_BYTES,
  ManagementUserRepository,
  SUPPORTED_AVATAR_MIME_TYPES
} from './ManagementUserRepository';

class ManagementAvatarUploadData {
  bytes: ArrayBuffer = new ArrayBuffer(0)
  contentType: string = ''
}

export class ManagementUserDetailViewModel extends BaseViewModel<ManagementUserDetailUIState> {
  private repository: ManagementUserRepository = new ManagementUserRepository()
  private visible: boolean = false
  private loading: boolean = false
  private saving: boolean = false
  private generation: number = 0
  private baselineProfile: ManagementProfileDraft = new ManagementProfileDraft()
  private baselineAccess: ManagementAccessDraft = new ManagementAccessDraft()
  private baselineParental: ManagementParentalDraft = new ManagementParentalDraft()

  async init(userId: string, initialTab: ManagementUserTab = ManagementUserTab.Profile): Promise<void> {
    this.stop()
    this.visible = true
    const generation = this.generation
    this.ui.userId = userId.trim()
    this.ui.selectedTab = initialTab
    this.ui.message = ''
    this.ui.permissionMessage = ''
    this.ui.permissionState = ManagementPermissionState.Checking
    this.ui.pageState = PageState.Loading
    if (this.ui.userId.length === 0) {
      this.failAccess('management_user_invalid_parameter')
      return
    }
    if (!this.repository.isApiClientInitialized()) {
      this.failAccess('management_access_no_server')
      return
    }
    const accessCode = await this.repository.checkCurrentUserAccess()
    if (!this.isActive(generation) || this.handleAccessError(accessCode)) {
      return
    }
    this.ui.permissionState = ManagementPermissionState.Allowed
    await this.loadEditor(generation)
  }

  async reload(): Promise<void> {
    if (this.loading || !this.visible ||
      this.ui.permissionState !== ManagementPermissionState.Allowed) {
      return
    }
    await this.loadEditor(this.generation)
  }

  markCurrentTabDirty(): void {
    if (this.ui.selectedTab === ManagementUserTab.Access) {
      this.ui.accessDirty = JSON.stringify(this.ui.access) !== JSON.stringify(this.baselineAccess)
    } else if (this.ui.selectedTab === ManagementUserTab.Parental) {
      this.ui.parentalDirty = JSON.stringify(this.ui.parental) !==
        JSON.stringify(this.baselineParental)
    } else if (this.ui.selectedTab === ManagementUserTab.Password) {
      this.ui.passwordDirty = this.ui.password.currentPassword.length > 0 ||
        this.ui.password.newPassword.length > 0 || this.ui.password.confirmPassword.length > 0
    } else {
      this.ui.profileDirty = JSON.stringify(this.ui.profile) !== JSON.stringify(this.baselineProfile)
    }
  }

  selectTab(tab: ManagementUserTab): void {
    if (this.saving) {
      return
    }
    this.ui.selectedTab = tab
    this.ui.message = ''
  }

  isSaving(): boolean {
    return this.saving
  }

  canLeave(): boolean {
    return !this.ui.anyDirty && !this.saving
  }

  discardCurrentTab(): void {
    if (this.ui.selectedTab === ManagementUserTab.Access) {
      this.ui.access = this.cloneAccess(this.baselineAccess)
      this.ui.accessDirty = false
      this.ui.accessStatus = ManagementUserOperationStatus.Idle
    } else if (this.ui.selectedTab === ManagementUserTab.Parental) {
      this.ui.parental = this.cloneParental(this.baselineParental)
      this.ui.parentalDirty = false
      this.ui.parentalStatus = ManagementUserOperationStatus.Idle
    } else if (this.ui.selectedTab === ManagementUserTab.Password) {
      this.ui.password.clear()
      this.ui.password = new ManagementPasswordDraft()
      this.ui.passwordDirty = false
      this.ui.passwordStatus = ManagementUserOperationStatus.Idle
    } else {
      this.ui.profile = this.cloneProfile(this.baselineProfile)
      this.ui.profileDirty = false
      this.ui.profileStatus = ManagementUserOperationStatus.Idle
    }
    this.ui.message = ''
  }

  async saveCurrentTab(): Promise<ManagementUserActionResult> {
    if (this.saving || !this.visible ||
      this.ui.permissionState !== ManagementPermissionState.Allowed) {
      return this.failureResult('management_user_operation_in_progress')
    }
    if (this.ui.selectedTab === ManagementUserTab.Password) {
      return await this.setPassword()
    }
    this.saving = true
    const savingTab = this.ui.selectedTab
    const generation = this.generation
    this.setCurrentStatus(ManagementUserOperationStatus.Saving)
    this.ui.message = ''
    let result: ManagementUserActionResult
    if (savingTab === ManagementUserTab.Access) {
      result = await this.repository.saveAccess(this.ui.userId, this.ui.access)
    } else if (savingTab === ManagementUserTab.Parental) {
      result = await this.repository.saveParental(this.ui.userId, this.ui.parental)
    } else {
      result = await this.repository.saveProfile(this.ui.userId,
        this.ui.currentUserId, this.ui.profile)
    }
    if (!this.isActive(generation)) {
      this.saving = false
      return result
    }
    if (result.ok) {
      const preservedProfile = this.cloneProfile(this.ui.profile)
      const preservedAccess = this.cloneAccess(this.ui.access)
      const preservedParental = this.cloneParental(this.ui.parental)
      const preservedPassword = this.clonePassword(this.ui.password)
      const profileDirty = this.ui.profileDirty
      const accessDirty = this.ui.accessDirty
      const parentalDirty = this.ui.parentalDirty
      const passwordDirty = this.ui.passwordDirty
      await this.loadEditor(generation)
      if (this.isActive(generation)) {
        if (savingTab !== ManagementUserTab.Profile && profileDirty) {
          this.ui.profile = preservedProfile
          this.ui.profileDirty = true
        }
        if (savingTab !== ManagementUserTab.Access && accessDirty) {
          this.ui.access = preservedAccess
          this.ui.accessDirty = true
        }
        if (savingTab !== ManagementUserTab.Parental && parentalDirty) {
          this.ui.parental = preservedParental
          this.ui.parentalDirty = true
        }
        if (passwordDirty) {
          this.ui.password = preservedPassword
          this.ui.passwordDirty = true
        }
        this.ui.selectedTab = savingTab
        this.setCurrentStatus(ManagementUserOperationStatus.Success)
      }
    } else {
      this.ui.message = result.message || 'management_user_save_failed'
      this.setCurrentStatus(ManagementUserOperationStatus.Error)
      this.handleActionAccessError(result)
    }
    this.saving = false
    return result
  }

  async setPassword(): Promise<ManagementUserActionResult> {
    if (this.ui.password.newPassword.length === 0) {
      return this.passwordValidationFailure('management_user_password_required')
    }
    if (this.ui.password.newPassword !== this.ui.password.confirmPassword) {
      return this.passwordValidationFailure('management_user_password_mismatch')
    }
    const editingCurrentUser = this.ui.userId === this.ui.currentUserId
    if (editingCurrentUser && this.ui.password.currentPassword.length === 0) {
      return this.passwordValidationFailure('management_user_current_password_required')
    }
    const currentPassword = editingCurrentUser ? this.ui.password.currentPassword : ''
    return await this.runPasswordAction(currentPassword, this.ui.password.newPassword, false)
  }

  async resetPassword(): Promise<ManagementUserActionResult> {
    return await this.runPasswordAction('', '', true)
  }

  async clearPassword(): Promise<ManagementUserActionResult> {
    return await this.runPasswordAction('', '', false)
  }

  async selectAvatar(): Promise<ManagementUserActionResult> {
    if (!this.canStartAction()) {
      return this.failureResult('management_user_operation_in_progress')
    }
    this.saving = true
    const generation = this.generation
    this.ui.message = ''
    try {
      const options = new photoAccessHelper.PhotoSelectOptions()
      options.MIMEType = photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE
      options.maxSelectNumber = 1
      const picker = new photoAccessHelper.PhotoViewPicker()
      const selected = await picker.select(options)
      if (!this.isActive(generation) || selected.photoUris.length === 0) {
        return this.failureResult('')
      }
      const avatar = await this.readAvatar(selected.photoUris[0])
      const result = await this.repository.uploadAvatar(
        this.ui.userId, avatar.bytes, avatar.contentType)
      await this.finishDirectAction(result, generation)
      return result
    } catch (error) {
      const errorMessage = (error as Error).message
      const result = this.failureResult(errorMessage.startsWith('management_user_avatar_') ?
        errorMessage : 'management_user_avatar_read_failed')
      if (this.isActive(generation)) {
        this.ui.message = result.message
      }
      return result
    } finally {
      if (generation === this.generation) {
        this.saving = false
      }
    }
  }

  async removeAvatar(): Promise<ManagementUserActionResult> {
    if (!this.canStartAction()) {
      return this.failureResult('management_user_operation_in_progress')
    }
    this.saving = true
    const generation = this.generation
    this.ui.message = ''
    try {
      const result = await this.repository.removeAvatar(this.ui.userId)
      await this.finishDirectAction(result, generation)
      return result
    } finally {
      if (generation === this.generation) {
        this.saving = false
      }
    }
  }

  async deleteUser(): Promise<ManagementUserActionResult> {
    if (!this.canStartAction()) {
      return this.failureResult('management_user_operation_in_progress')
    }
    this.saving = true
    const generation = this.generation
    this.ui.deleteStatus = ManagementUserOperationStatus.Saving
    this.ui.message = ''
    try {
      const result = await this.repository.deleteUser(this.ui.userId)
      if (!this.isActive(generation)) {
        return result
      }
      if (result.ok) {
        this.ui.deleteStatus = ManagementUserOperationStatus.Success
      } else {
        this.ui.message = result.message || 'management_user_delete_failed'
        this.ui.deleteStatus = ManagementUserOperationStatus.Error
        this.handleActionAccessError(result)
      }
      return result
    } finally {
      if (generation === this.generation) {
        this.saving = false
      }
    }
  }

  stop(): void {
    this.ui.password.clear()
    this.ui.password = new ManagementPasswordDraft()
    this.generation += 1
    this.visible = false
    this.loading = false
    this.saving = false
  }

  private async runPasswordAction(currentPassword: string, newPassword: string,
    reset: boolean): Promise<ManagementUserActionResult> {
    if (!this.canStartAction()) {
      return this.failureResult('management_user_operation_in_progress')
    }
    this.saving = true
    const generation = this.generation
    this.ui.passwordStatus = ManagementUserOperationStatus.Saving
    this.ui.message = ''
    try {
      const result = await this.repository.updatePassword(
        this.ui.userId, currentPassword, newPassword, reset)
      if (!this.isActive(generation)) {
        return result
      }
      if (result.ok) {
        await this.refreshUserSummary(generation)
        if (this.isActive(generation)) {
          this.ui.password.clear()
          this.ui.password = new ManagementPasswordDraft()
          this.ui.passwordDirty = false
          this.ui.passwordStatus = ManagementUserOperationStatus.Success
        }
      } else {
        this.ui.message = result.message || 'management_user_password_update_failed'
        this.ui.passwordStatus = ManagementUserOperationStatus.Error
        this.handleActionAccessError(result)
      }
      return result
    } finally {
      if (generation === this.generation) {
        this.saving = false
      }
    }
  }

  private passwordValidationFailure(message: string): ManagementUserActionResult {
    const result = this.failureResult(message)
    this.ui.message = message
    this.ui.passwordStatus = ManagementUserOperationStatus.Error
    return result
  }

  private canStartAction(): boolean {
    return !this.saving && this.visible &&
      this.ui.permissionState === ManagementPermissionState.Allowed
  }

  private async finishDirectAction(result: ManagementUserActionResult,
    generation: number): Promise<void> {
    if (!this.isActive(generation)) {
      return
    }
    if (result.ok) {
      await this.refreshUserSummary(generation)
      if (this.isActive(generation)) {
        this.ui.message = ''
      }
    } else {
      this.ui.message = result.message || 'management_user_save_failed'
      this.handleActionAccessError(result)
    }
  }

  private async refreshUserSummary(generation: number): Promise<void> {
    const user = await this.repository.loadUser(this.ui.userId)
    if (this.isActive(generation)) {
      this.ui.user = user
      this.ui.userName = user.name
    }
  }

  private async readAvatar(uri: string): Promise<ManagementAvatarUploadData> {
    let file: fileIo.File | null = null
    let source: image.ImageSource | null = null
    try {
      file = fileIo.openSync(uri, fileIo.OpenMode.READ_ONLY)
      const size = fileIo.statSync(file.fd).size
      if (size <= 0) {
        throw Error('management_user_avatar_empty')
      }
      if (size > MAX_AVATAR_BYTES) {
        throw Error('management_user_avatar_too_large')
      }
      const bytes = new ArrayBuffer(size)
      const readBytes = fileIo.readSync(file.fd, bytes)
      if (readBytes !== size) {
        throw Error('management_user_avatar_read_failed')
      }
      source = image.createImageSource(bytes)
      const info = await source.getImageInfo()
      const contentType = info.mimeType.toLowerCase()
      if (!SUPPORTED_AVATAR_MIME_TYPES.includes(contentType)) {
        throw Error('management_user_avatar_unsupported_type')
      }
      const result = new ManagementAvatarUploadData()
      result.bytes = bytes
      result.contentType = contentType
      return result
    } catch (error) {
      const message = (error as Error).message
      if (message.startsWith('management_user_avatar_')) {
        throw Error(message)
      }
      throw Error('management_user_avatar_read_failed')
    } finally {
      if (source) {
        try {
          await source.release()
        } catch (error) {
        }
      }
      if (file) {
        try {
          fileIo.closeSync(file)
        } catch (error) {
        }
      }
    }
  }

  private async loadEditor(generation: number): Promise<void> {
    if (this.loading) {
      return
    }
    this.loading = true
    try {
      const snapshot = await this.repository.loadEditor(this.ui.userId)
      if (!this.isActive(generation)) {
        return
      }
      this.applySnapshot(snapshot)
      this.ui.pageState = PageState.Content
    } catch (error) {
      if (this.isActive(generation)) {
        this.ui.message = 'management_user_load_failed'
        this.ui.pageState = PageState.Error
      }
    } finally {
      if (generation === this.generation) {
        this.loading = false
      }
    }
  }

  private applySnapshot(snapshot: ManagementUserEditorSnapshot): void {
    this.ui.currentUserId = snapshot.currentUserId
    this.ui.user = snapshot.user
    this.ui.userName = snapshot.user.name
    this.ui.enabledAdministratorCount = snapshot.enabledAdministratorCount
    this.baselineProfile = this.cloneProfile(snapshot.profile)
    this.baselineAccess = this.cloneAccess(snapshot.access)
    this.baselineParental = this.cloneParental(snapshot.parental)
    this.ui.profile = this.cloneProfile(this.baselineProfile)
    this.ui.access = this.cloneAccess(this.baselineAccess)
    this.ui.parental = this.cloneParental(this.baselineParental)
    this.ui.password.clear()
    this.ui.password = new ManagementPasswordDraft()
    this.ui.authenticationProviders = snapshot.authenticationProviders.slice()
    this.ui.passwordResetProviders = snapshot.passwordResetProviders.slice()
    this.ui.folders = snapshot.folders.slice()
    this.ui.foldersStatus = snapshot.foldersStatus
    this.ui.channels = snapshot.channels.slice()
    this.ui.channelsStatus = snapshot.channelsStatus
    this.ui.devices = snapshot.devices.slice()
    this.ui.devicesStatus = snapshot.devicesStatus
    this.ui.parentalRatings = snapshot.parentalRatings.slice()
    this.ui.profileDirty = false
    this.ui.accessDirty = false
    this.ui.parentalDirty = false
    this.ui.passwordDirty = false
    this.ui.profileStatus = ManagementUserOperationStatus.Idle
    this.ui.accessStatus = ManagementUserOperationStatus.Idle
    this.ui.parentalStatus = ManagementUserOperationStatus.Idle
    this.ui.passwordStatus = ManagementUserOperationStatus.Idle
    this.ui.deleteStatus = ManagementUserOperationStatus.Idle
    this.ui.message = ''
  }

  private setCurrentStatus(status: ManagementUserOperationStatus): void {
    if (this.ui.selectedTab === ManagementUserTab.Access) {
      this.ui.accessStatus = status
    } else if (this.ui.selectedTab === ManagementUserTab.Parental) {
      this.ui.parentalStatus = status
    } else if (this.ui.selectedTab === ManagementUserTab.Password) {
      this.ui.passwordStatus = status
    } else {
      this.ui.profileStatus = status
    }
  }

  private handleActionAccessError(result: ManagementUserActionResult): void {
    if (result.code === ManagementUserActionCode.AuthenticationExpired) {
      this.stop()
    } else if (result.code === ManagementUserActionCode.PermissionDenied) {
      this.ui.permissionState = ManagementPermissionState.Denied
      this.ui.permissionMessage = 'management_permission_denied'
      this.ui.pageState = PageState.Error
    }
  }

  private handleAccessError(code: ManagementErrorCode): boolean {
    if (code === ManagementErrorCode.None) {
      return false
    }
    if (code === ManagementErrorCode.PermissionDenied) {
      this.ui.permissionState = ManagementPermissionState.Denied
      this.ui.permissionMessage = 'management_permission_denied'
    } else {
      this.ui.permissionState = ManagementPermissionState.Error
      this.ui.permissionMessage = code === ManagementErrorCode.Offline ?
        'management_access_offline' : 'management_permission_check_failed'
    }
    this.ui.pageState = PageState.Error
    return true
  }

  private failAccess(message: string): void {
    this.ui.permissionState = ManagementPermissionState.Error
    this.ui.permissionMessage = message
    this.ui.pageState = PageState.Error
  }

  private failureResult(message: string): ManagementUserActionResult {
    const result = new ManagementUserActionResult()
    result.code = ManagementUserActionCode.ValidationFailed
    result.userId = this.ui.userId
    result.message = message
    return result
  }

  private isActive(generation: number): boolean {
    return this.visible && generation === this.generation
  }

  private cloneProfile(source: ManagementProfileDraft): ManagementProfileDraft {
    const result = new ManagementProfileDraft()
    result.name = source.name
    result.authenticationProviderId = source.authenticationProviderId
    result.passwordResetProviderId = source.passwordResetProviderId
    result.isAdministrator = source.isAdministrator
    result.isDisabled = source.isDisabled
    result.isHidden = source.isHidden
    result.enableCollectionManagement = source.enableCollectionManagement
    result.enableSubtitleManagement = source.enableSubtitleManagement
    result.enableLyricManagement = source.enableLyricManagement
    result.enableLiveTvManagement = source.enableLiveTvManagement
    result.enableLiveTvAccess = source.enableLiveTvAccess
    result.enableMediaPlayback = source.enableMediaPlayback
    result.enableAudioPlaybackTranscoding = source.enableAudioPlaybackTranscoding
    result.enableVideoPlaybackTranscoding = source.enableVideoPlaybackTranscoding
    result.enablePlaybackRemuxing = source.enablePlaybackRemuxing
    result.forceRemoteSourceTranscoding = source.forceRemoteSourceTranscoding
    result.enableContentDownloading = source.enableContentDownloading
    result.enableRemoteAccess = source.enableRemoteAccess
    result.enableRemoteControlOfOtherUsers = source.enableRemoteControlOfOtherUsers
    result.enableSharedDeviceControl = source.enableSharedDeviceControl
    result.enableContentDeletion = source.enableContentDeletion
    result.contentDeletionFolderIds = source.contentDeletionFolderIds.slice()
    result.remoteClientBitrateLimitMbps = source.remoteClientBitrateLimitMbps
    result.loginAttemptsBeforeLockout = source.loginAttemptsBeforeLockout
    result.maxActiveSessions = source.maxActiveSessions
    result.syncPlayAccess = source.syncPlayAccess
    return result
  }

  private cloneAccess(source: ManagementAccessDraft): ManagementAccessDraft {
    const result = new ManagementAccessDraft()
    result.enableAllFolders = source.enableAllFolders
    result.enabledFolderIds = source.enabledFolderIds.slice()
    result.enableAllChannels = source.enableAllChannels
    result.enabledChannelIds = source.enabledChannelIds.slice()
    result.enableAllDevices = source.enableAllDevices
    result.enabledDeviceIds = source.enabledDeviceIds.slice()
    return result
  }

  private cloneParental(source: ManagementParentalDraft): ManagementParentalDraft {
    const result = new ManagementParentalDraft()
    result.maxParentalRating = source.maxParentalRating
    result.blockedUnratedItems = source.blockedUnratedItems.slice()
    result.allowedTags = source.allowedTags.slice()
    result.blockedTags = source.blockedTags.slice()
    result.schedules = source.schedules.map((sourceItem: ManagementAccessScheduleItem) => {
      const item = new ManagementAccessScheduleItem()
      item.id = sourceItem.id
      item.dayOfWeek = sourceItem.dayOfWeek
      item.startHour = sourceItem.startHour
      item.endHour = sourceItem.endHour
      return item
    })
    return result
  }

  private clonePassword(source: ManagementPasswordDraft): ManagementPasswordDraft {
    const result = new ManagementPasswordDraft()
    result.currentPassword = source.currentPassword
    result.newPassword = source.newPassword
    result.confirmPassword = source.confirmPassword
    return result
  }
}
