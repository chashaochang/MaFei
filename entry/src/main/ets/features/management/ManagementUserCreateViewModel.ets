import { PageState } from '../../common/base/BaseUIState';
import { BaseViewModel } from '../../common/base/BaseViewModel';
import { ManagementErrorCode, ManagementPermissionState } from './ManagementModels';
import { ManagementUserCreateUIState } from './ManagementUserCreateUIState';
import {
  ManagementAccessDraft,
  ManagementUserActionCode,
  ManagementUserActionResult,
  ManagementUserCreateDraft,
  ManagementUserOperationStatus
} from './ManagementUserModels';
import { ManagementUserRepository } from './ManagementUserRepository';

export class ManagementUserCreateViewModel extends BaseViewModel<ManagementUserCreateUIState> {
  private repository: ManagementUserRepository = new ManagementUserRepository()
  private visible: boolean = false
  private generation: number = 0

  async init(): Promise<void> {
    this.visible = true
    const generation = this.generation
    this.ui.pageState = PageState.Loading
    this.ui.permissionState = ManagementPermissionState.Checking
    this.ui.permissionMessage = ''
    this.ui.message = ''
    if (!this.repository.isApiClientInitialized()) {
      this.failAccess('management_access_no_server')
      return
    }
    const accessCode = await this.repository.checkCurrentUserAccess()
    if (!this.isActive(generation) || this.handleAccessError(accessCode)) {
      return
    }
    this.ui.permissionState = ManagementPermissionState.Allowed
    this.ui.pageState = PageState.Content
    await this.loadAccessOptions(generation)
  }

  async submit(): Promise<ManagementUserActionResult> {
    if (this.ui.saving || !this.validate()) {
      return this.failureResult(this.ui.saving ?
        'management_user_operation_in_progress' : this.ui.message)
    }
    this.ui.saving = true
    this.ui.message = ''
    const generation = this.generation
    try {
      const result = await this.repository.createUser(this.toDraft())
      if (result.userId.length > 0) {
        this.ui.password = ''
        this.ui.confirmPassword = ''
        this.ui.createdUserId = result.userId
        this.ui.partialSuccess = result.code === ManagementUserActionCode.CreatedPolicyPending
      }
      if (!this.isActive(generation)) {
        return result
      }
      if (result.ok) {
        this.ui.message = ''
      } else if (this.ui.partialSuccess) {
        this.ui.message = result.message
      } else {
        this.ui.message = result.message || 'management_user_create_failed'
        this.handleActionAccessError(result)
      }
      return result
    } finally {
      if (generation === this.generation) {
        this.ui.saving = false
      }
    }
  }

  async retryAccess(): Promise<ManagementUserActionResult> {
    if (this.ui.saving || this.ui.createdUserId.length === 0) {
      return this.failureResult(this.ui.saving ?
        'management_user_operation_in_progress' : 'management_user_invalid_parameter')
    }
    this.ui.saving = true
    this.ui.message = ''
    const generation = this.generation
    try {
      const result = await this.repository.retryCreateAccess(
        this.ui.createdUserId, this.cloneAccess(this.ui.access))
      this.ui.password = ''
      this.ui.confirmPassword = ''
      if (!this.isActive(generation)) {
        return result
      }
      if (result.ok) {
        this.ui.partialSuccess = false
      } else {
        this.ui.message = result.message || 'management_user_retry_access_failed'
        this.handleActionAccessError(result)
      }
      return result
    } finally {
      if (generation === this.generation) {
        this.ui.saving = false
      }
    }
  }

  stop(): void {
    this.ui.password = ''
    this.ui.confirmPassword = ''
    this.ui.saving = false
    this.generation += 1
    this.visible = false
  }

  private async loadAccessOptions(generation: number): Promise<void> {
    this.ui.foldersStatus = ManagementUserOperationStatus.Loading
    this.ui.channelsStatus = ManagementUserOperationStatus.Loading
    const foldersPromise: Promise<void> = this.repository.loadFolderOptions()
      .then((folders) => {
        if (this.isActive(generation)) {
          this.ui.folders = folders
          this.ui.foldersStatus = ManagementUserOperationStatus.Success
        }
      })
      .catch(() => {
        if (this.isActive(generation)) {
          this.ui.folders = []
          this.ui.foldersStatus = ManagementUserOperationStatus.Error
        }
      })
    const channelsPromise: Promise<void> = this.repository.loadChannelOptions()
      .then((channels) => {
        if (this.isActive(generation)) {
          this.ui.channels = channels
          this.ui.channelsStatus = ManagementUserOperationStatus.Success
        }
      })
      .catch(() => {
        if (this.isActive(generation)) {
          this.ui.channels = []
          this.ui.channelsStatus = ManagementUserOperationStatus.Error
        }
      })
    await Promise.all([foldersPromise, channelsPromise])
  }

  private validate(): boolean {
    if (this.ui.name.trim().length === 0) {
      this.ui.message = 'management_user_name_required'
      return false
    }
    if (this.ui.password !== this.ui.confirmPassword) {
      this.ui.message = 'management_user_password_mismatch'
      return false
    }
    this.ui.message = ''
    return true
  }

  private toDraft(): ManagementUserCreateDraft {
    const draft = new ManagementUserCreateDraft()
    draft.name = this.ui.name.trim()
    draft.password = this.ui.password
    draft.access = this.cloneAccess(this.ui.access)
    return draft
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
    result.userId = this.ui.createdUserId
    result.message = message
    return result
  }

  private isActive(generation: number): boolean {
    return this.visible && generation === this.generation
  }
}
