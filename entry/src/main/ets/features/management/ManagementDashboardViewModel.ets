import { PageState } from '../../common/base/BaseUIState';
import { BaseViewModel } from '../../common/base/BaseViewModel';
import { ManagementDashboardUIState } from './ManagementDashboardUIState';
import {
  ManagementActionCode,
  ManagementActionResult,
  ManagementErrorCode,
  ManagementPermissionState,
  ManagementSectionResult,
  ManagementSectionStatus
} from './ManagementModels';
import { ManagementRepository } from './ManagementRepository';

export class ManagementDashboardViewModel extends BaseViewModel<ManagementDashboardUIState> {
  private repository: ManagementRepository = new ManagementRepository()
  private taskTimer: number = -1
  private sessionTimer: number = -1
  private initialized: boolean = false
  private visible: boolean = false
  private loading: boolean = false
  private serverLoading: boolean = false
  private countsLoading: boolean = false
  private usersLoading: boolean = false
  private tasksLoading: boolean = false
  private sessionsLoading: boolean = false
  private activitiesLoading: boolean = false
  private scanPollUntil: number = 0
  private generation: number = 0

  async init(): Promise<void> {
    if (this.visible) {
      return
    }
    const firstLoad = !this.initialized
    this.initialized = true
    this.stopPolling()
    this.visible = true
    const generation = this.generation
    if (firstLoad) {
      this.ui.pageState = PageState.Loading
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
    this.refresh(false)
    this.startSessionPolling()
    this.syncTaskPolling()
  }

  async refresh(showRefreshState: boolean = true): Promise<void> {
    if (this.loading || this.ui.permissionState !== ManagementPermissionState.Allowed) {
      return
    }
    this.loading = true
    const generation = this.generation
    if (showRefreshState) {
      this.ui.isRefreshing = true
    }
    try {
      await Promise.all([
        this.refreshServer(),
        this.refreshCounts(),
        this.refreshUsers(),
        this.refreshSessions(),
        this.refreshTasks(),
        this.refreshActivities()
      ])
    } finally {
      if (generation === this.generation) {
        this.loading = false
        this.ui.isRefreshing = false
      }
    }
  }

  async refreshServer(): Promise<void> {
    if (!this.visible || this.serverLoading ||
      this.ui.permissionState !== ManagementPermissionState.Allowed) {
      return
    }
    this.serverLoading = true
    const generation = this.generation
    try {
      const result = await this.repository.loadServerSection()
      if (!this.isActive(generation) || this.handleSectionAccessError(result)) {
        return
      }
      this.ui.serverStatus = result.status
      this.ui.serverMessage = result.message
      if (result.status !== ManagementSectionStatus.Error) {
        this.ui.server = result.server
      }
    } finally {
      if (generation === this.generation) {
        this.serverLoading = false
      }
    }
  }

  async refreshCounts(): Promise<void> {
    if (!this.visible || this.countsLoading ||
      this.ui.permissionState !== ManagementPermissionState.Allowed) {
      return
    }
    this.countsLoading = true
    const generation = this.generation
    try {
      const result = await this.repository.loadCountsSection()
      if (!this.isActive(generation) || this.handleSectionAccessError(result)) {
        return
      }
      this.ui.countsStatus = result.status
      this.ui.countsMessage = result.message
      if (result.status !== ManagementSectionStatus.Error) {
        this.ui.counts = result.counts
      }
    } finally {
      if (generation === this.generation) {
        this.countsLoading = false
      }
    }
  }

  async refreshUsers(): Promise<void> {
    if (!this.visible || this.usersLoading ||
      this.ui.permissionState !== ManagementPermissionState.Allowed) {
      return
    }
    this.usersLoading = true
    const generation = this.generation
    try {
      const result = await this.repository.loadUsersSection()
      if (!this.isActive(generation) || this.handleSectionAccessError(result)) {
        return
      }
      this.ui.usersStatus = result.status
      this.ui.usersMessage = result.message
      if (result.status !== ManagementSectionStatus.Error) {
        this.ui.users = result.users
      }
    } finally {
      if (generation === this.generation) {
        this.usersLoading = false
      }
    }
  }

  async refreshTasks(): Promise<void> {
    if (!this.visible || this.tasksLoading ||
      this.ui.permissionState !== ManagementPermissionState.Allowed) {
      return
    }
    this.tasksLoading = true
    const generation = this.generation
    try {
      const result = await this.repository.loadTasksSection()
      if (!this.isActive(generation)) {
        return
      }
      if (this.handleSectionAccessError(result)) {
        return
      }
      this.applyTasks(result)
      this.syncTaskPolling()
    } finally {
      if (generation === this.generation) {
        this.tasksLoading = false
      }
    }
  }

  async refreshSessions(): Promise<void> {
    if (!this.visible || this.sessionsLoading ||
      this.ui.permissionState !== ManagementPermissionState.Allowed) {
      return
    }
    this.sessionsLoading = true
    const generation = this.generation
    try {
      const result = await this.repository.loadSessionsSection()
      if (!this.isActive(generation)) {
        return
      }
      if (this.handleSectionAccessError(result)) {
        return
      }
      this.applySessions(result)
    } finally {
      if (generation === this.generation) {
        this.sessionsLoading = false
      }
    }
  }

  async refreshActivities(): Promise<void> {
    if (!this.visible || this.activitiesLoading ||
      this.ui.permissionState !== ManagementPermissionState.Allowed) {
      return
    }
    this.activitiesLoading = true
    const generation = this.generation
    try {
      const result = await this.repository.loadActivitiesSection()
      if (!this.isActive(generation) || this.handleSectionAccessError(result)) {
        return
      }
      this.ui.activitiesStatus = result.status
      this.ui.activitiesMessage = result.message
      if (result.status !== ManagementSectionStatus.Error) {
        this.ui.activities = result.activities
      }
    } finally {
      if (generation === this.generation) {
        this.activitiesLoading = false
      }
    }
  }

  async startLibraryScan(): Promise<void> {
    if (this.ui.isStartingScan || this.ui.isScanSubmissionLocked || this.ui.isLibraryScanRunning) {
      this.showToast(this.getString('management_scan_running'))
      return
    }
    this.ui.isStartingScan = true
    this.ui.isScanSubmissionLocked = true
    this.scanPollUntil = Date.now() + 15000
    const generation = this.generation
    try {
      const result: ManagementActionResult = await this.repository.startLibraryScan()
      if (!this.isActive(generation)) {
        return
      }
      if (result.ok) {
        this.showToast(this.getString('management_scan_started'))
        await this.refreshTasks()
        if (!this.isActive(generation)) {
          return
        }
        this.syncTaskPolling()
        return
      }
      if (result.code === ManagementActionCode.ScanAlreadyRunning) {
        this.showToast(this.getString('management_scan_running'))
        await this.refreshTasks()
        if (this.isActive(generation)) {
          this.syncTaskPolling()
        }
      } else if (result.code === ManagementActionCode.AuthenticationExpired) {
        this.clearScanSubmissionLock()
        this.stopPolling()
      } else if (result.code === ManagementActionCode.PermissionDenied) {
        this.clearScanSubmissionLock()
        this.denyAccess()
      } else if (result.code === ManagementActionCode.ScanTaskUnavailable) {
        this.clearScanSubmissionLock()
        this.showToast(this.getString('management_scan_unsupported'))
      } else {
        this.clearScanSubmissionLock()
        this.showToast(result.message || this.getString('management_scan_failed'))
      }
    } finally {
      if (generation === this.generation) {
        this.ui.isStartingScan = false
      }
    }
  }

  stopPolling(): void {
    this.generation += 1
    this.visible = false
    this.loading = false
    this.serverLoading = false
    this.countsLoading = false
    this.usersLoading = false
    this.tasksLoading = false
    this.sessionsLoading = false
    this.activitiesLoading = false
    this.ui.isRefreshing = false
    this.ui.isStartingScan = false
    if (this.taskTimer >= 0) {
      clearInterval(this.taskTimer)
      this.taskTimer = -1
    }
    if (this.sessionTimer >= 0) {
      clearInterval(this.sessionTimer)
      this.sessionTimer = -1
    }
  }

  private startSessionPolling(): void {
    if (!this.visible || this.ui.permissionState !== ManagementPermissionState.Allowed) {
      return
    }
    if (this.sessionTimer >= 0) {
      clearInterval(this.sessionTimer)
    }
    this.sessionTimer = setInterval(() => {
      this.refreshSessions()
    }, 15000)
  }

  private syncTaskPolling(): void {
    if (this.ui.isScanSubmissionLocked && Date.now() >= this.scanPollUntil) {
      this.clearScanSubmissionLock()
    }
    const shouldPoll = this.ui.hasRunningTasks || this.ui.isScanSubmissionLocked
    if (!this.visible || !shouldPoll) {
      if (this.taskTimer >= 0) {
        clearInterval(this.taskTimer)
        this.taskTimer = -1
      }
      return
    }
    if (this.taskTimer >= 0) {
      return
    }
    this.taskTimer = setInterval(() => {
      this.refreshTasks()
    }, 3000)
  }

  private applySessions(result: ManagementSectionResult): void {
    this.ui.sessionsStatus = result.status
    this.ui.sessionsMessage = result.message
    if (result.status !== ManagementSectionStatus.Error) {
      this.ui.sessions = result.sessions
    }
  }

  private applyTasks(result: ManagementSectionResult): void {
    this.ui.tasksStatus = result.status
    this.ui.tasksMessage = result.message
    if (result.status !== ManagementSectionStatus.Error) {
      this.ui.tasks = result.tasks
      if (this.ui.isLibraryScanRunning) {
        this.clearScanSubmissionLock()
      }
    }
  }

  private isActive(generation: number): boolean {
    return this.visible && generation === this.generation
  }

  private handleSectionAccessError(result: ManagementSectionResult): boolean {
    return this.handleAccessError(result.errorCode)
  }

  private handleAccessError(errorCode: ManagementErrorCode, accessCheck: boolean = false): boolean {
    if (errorCode === ManagementErrorCode.None) {
      return false
    }
    if (errorCode === ManagementErrorCode.AuthenticationExpired) {
      this.clearScanSubmissionLock()
      this.stopPolling()
      return true
    }
    if (errorCode === ManagementErrorCode.PermissionDenied) {
      this.denyAccess()
      return true
    }
    if (!accessCheck) {
      return false
    }
    if (errorCode === ManagementErrorCode.Offline) {
      this.failAccess('management_access_offline')
    } else {
      this.failAccess('management_permission_check_failed')
    }
    return true
  }

  private clearScanSubmissionLock(): void {
    this.ui.isScanSubmissionLocked = false
    this.scanPollUntil = 0
  }

  private denyAccess(): void {
    const message = this.getString('management_permission_denied')
    this.clearScanSubmissionLock()
    this.stopPolling()
    this.ui.permissionMessage = message
    this.ui.permissionState = ManagementPermissionState.Denied
  }

  private failAccess(resourceName: string): void {
    const message = this.getString(resourceName)
    this.clearScanSubmissionLock()
    this.stopPolling()
    this.ui.permissionState = ManagementPermissionState.Error
    this.ui.permissionMessage = message
    this.ui.pageState = PageState.Error
    this.showToast(message)
  }

  private getString(name: string): string {
    return this.ui.context.getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private showToast(message: string): void {
    this.ui.context.getPromptAction().showToast({ message: message })
  }
}
