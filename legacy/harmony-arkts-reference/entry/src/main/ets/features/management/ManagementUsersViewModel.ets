import { PageState } from '../../common/base/BaseUIState';
import { BaseViewModel } from '../../common/base/BaseViewModel';
import {
  ManagementErrorCode,
  ManagementPermissionState,
  ManagementSectionStatus
} from './ManagementModels';
import {
  ManagementUserActionCode,
  ManagementUserActionResult,
  ManagementUserItem
} from './ManagementUserModels';
import { ManagementUserRepository } from './ManagementUserRepository';
import { ManagementNativePagePolicy } from './ManagementNativePagePolicy';
import { ManagementUsersUIState } from './ManagementUsersUIState';

export class ManagementUsersViewModel extends BaseViewModel<ManagementUsersUIState> {
  private repository: ManagementUserRepository = new ManagementUserRepository()
  private initialized: boolean = false
  private visible: boolean = false
  private loading: boolean = false
  private generation: number = 0

  async init(): Promise<void> {
    if (this.visible) {
      return
    }
    const firstLoad = !this.initialized
    this.initialized = true
    this.visible = true
    const generation = this.generation
    if (firstLoad) {
      this.ui.pageState = PageState.Loading
      this.ui.status = ManagementSectionStatus.Loading
    }
    this.ui.permissionState = ManagementPermissionState.Checking
    this.ui.permissionMessage = ''
    if (!this.repository.isApiClientInitialized()) {
      this.failAccess('management_access_no_server')
      return
    }
    const accessCode = await this.repository.checkCurrentUserAccess()
    if (!this.isActive(generation)) {
      return
    }
    if (this.handleAccessError(accessCode, true)) {
      return
    }
    this.ui.permissionState = ManagementPermissionState.Allowed
    this.ui.pageState = PageState.Content
    await this.refresh(false)
  }

  async refresh(showRefresh: boolean = true): Promise<void> {
    if (this.loading || !this.visible ||
      this.ui.permissionState !== ManagementPermissionState.Allowed) {
      return
    }
    this.loading = true
    const generation = this.generation
    this.ui.isRefreshing = showRefresh
    this.ui.message = ''
    try {
      const users = await this.repository.loadUsers()
      const currentUserId = await this.repository.loadCurrentUserId()
      if (!this.isActive(generation)) {
        return
      }
      this.ui.users = users
      this.ui.currentUserId = currentUserId
      this.ui.enabledAdministratorCount = users.filter((user: ManagementUserItem) =>
        user.administrator && !user.disabled).length
      this.ui.hasStaleData = false
      this.ui.status = users.length > 0 ? ManagementSectionStatus.Content :
        ManagementSectionStatus.Empty
      this.preserveSelection(users)
    } catch (error) {
      if (!this.isActive(generation)) {
        return
      }
      this.ui.message = 'management_user_load_failed'
      if (this.ui.users.length > 0) {
        this.ui.hasStaleData = true
        this.ui.status = ManagementSectionStatus.Content
      } else {
        this.ui.status = ManagementSectionStatus.Error
      }
    } finally {
      if (generation === this.generation) {
        this.loading = false
        this.ui.isRefreshing = false
      }
    }
  }

  selectUser(userId: string): void {
    this.ui.selectedUserId = userId
  }

  async deleteUser(userId: string): Promise<ManagementUserActionResult> {
    if (this.ui.deletingUserId.length > 0) {
      const locked = new ManagementUserActionResult()
      locked.code = ManagementUserActionCode.RequestFailed
      locked.message = 'management_user_operation_in_progress'
      return locked
    }
    this.ui.deletingUserId = userId
    const generation = this.generation
    try {
      const result = await this.repository.deleteUser(userId)
      if (!this.isActive(generation)) {
        return result
      }
      if (result.ok) {
        await this.refresh(false)
      } else {
        this.ui.message = result.message
        this.handleActionAccessError(result)
      }
      return result
    } finally {
      if (generation === this.generation) {
        this.ui.deletingUserId = ''
      }
    }
  }

  stop(): void {
    this.generation += 1
    this.visible = false
    this.loading = false
    this.ui.isRefreshing = false
    this.ui.deletingUserId = ''
  }

  private preserveSelection(users: ManagementUserItem[]): void {
    if (users.length === 0) {
      this.ui.selectedUserId = ''
      return
    }
    const selectedExists = users.some((user: ManagementUserItem) =>
      user.id === this.ui.selectedUserId)
    if (!selectedExists && ManagementNativePagePolicy.isExpanded(this.appUIState.deviceWidth)) {
      this.ui.selectedUserId = users[0].id
    }
  }

  private handleActionAccessError(result: ManagementUserActionResult): void {
    if (result.code === ManagementUserActionCode.AuthenticationExpired) {
      this.stop()
    } else if (result.code === ManagementUserActionCode.PermissionDenied) {
      this.denyAccess()
    }
  }

  private handleAccessError(errorCode: ManagementErrorCode,
    accessCheck: boolean = false): boolean {
    if (errorCode === ManagementErrorCode.None) {
      return false
    }
    if (errorCode === ManagementErrorCode.AuthenticationExpired) {
      this.stop()
      return true
    }
    if (errorCode === ManagementErrorCode.PermissionDenied) {
      this.denyAccess()
      return true
    }
    if (accessCheck) {
      this.failAccess(errorCode === ManagementErrorCode.Offline ?
        'management_access_offline' : 'management_permission_check_failed')
    }
    return true
  }

  private denyAccess(): void {
    this.stop()
    this.ui.permissionState = ManagementPermissionState.Denied
    this.ui.permissionMessage = 'management_permission_denied'
    this.ui.pageState = PageState.Error
  }

  private failAccess(message: string): void {
    this.stop()
    this.ui.permissionState = ManagementPermissionState.Error
    this.ui.permissionMessage = message
    this.ui.pageState = PageState.Error
  }

  private isActive(generation: number): boolean {
    return this.visible && generation === this.generation
  }
}
