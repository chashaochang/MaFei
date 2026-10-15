import { BaseViewModel } from '../../../common/base/BaseViewModel';
import {
  ManagementErrorCode,
  ManagementPermissionState,
  ManagementSectionStatus
} from '../ManagementModels';
import {
  ManagementActivityFilterOptions,
  ManagementActivityMediaTarget,
  ManagementActivitySortField,
  ManagementActivityView
} from './ManagementActivityModels';
import { ManagementActivityQuery } from './ManagementActivityQuery';
import { ManagementActivityRepository } from './ManagementActivityRepository';
import {
  ManagementActivityDashboardUIState,
  ManagementActivityUIState
} from './ManagementActivityUIState';

export class ManagementActivityDashboardViewModel extends BaseViewModel<ManagementActivityDashboardUIState> {
  private repository: ManagementActivityRepository = new ManagementActivityRepository()
  private generation: number = 0
  private visible: boolean = false
  private loading: boolean = false

  async init(): Promise<void> {
    this.stop()
    this.visible = true
    const generation = this.generation
    this.ui.permissionState = ManagementPermissionState.Checking
    if (!this.repository.isApiClientInitialized()) {
      this.fail(ManagementErrorCode.RequestFailed, 'management_access_no_server')
      return
    }
    const access = await this.repository.checkCurrentUserAccess()
    if (!this.isActive(generation)) return
    if (access !== ManagementErrorCode.None) {
      this.fail(access, 'management_permission_denied')
      return
    }
    this.ui.permissionState = ManagementPermissionState.Allowed
    await this.refresh()
  }

  stop(): void {
    this.visible = false
    this.generation += 1
    this.loading = false
  }

  async refresh(): Promise<void> {
    if (!this.visible || this.loading) return
    this.loading = true
    this.ui.refreshing = true
    const generation = this.generation
    try {
      const page = await this.repository.loadDashboard()
      if (!this.isActive(generation)) return
      this.ui.items = page.items
      this.ui.status = page.items.length > 0 ? ManagementSectionStatus.Content : ManagementSectionStatus.Empty
      this.ui.stale = false
      this.ui.message = ''
    } catch (error) {
      if (!this.isActive(generation)) return
      const code = this.repository.classifyError(error as Object)
      this.ui.errorCode = code
      this.ui.message = code === ManagementErrorCode.Unsupported ?
        'management_section_unsupported' : 'management_activity_failed'
      this.ui.stale = this.ui.items.length > 0
      this.ui.status = code === ManagementErrorCode.Unsupported ?
        ManagementSectionStatus.Unsupported : ManagementSectionStatus.Error
    } finally {
      if (generation === this.generation) {
        this.loading = false
        this.ui.refreshing = false
      }
    }
  }

  private fail(code: ManagementErrorCode, message: string): void {
    this.ui.permissionState = code === ManagementErrorCode.PermissionDenied ?
      ManagementPermissionState.Denied : ManagementPermissionState.Error
    this.ui.errorCode = code
    this.ui.message = message
    this.ui.status = ManagementSectionStatus.Error
  }

  private isActive(generation: number): boolean {
    return this.visible && generation === this.generation
  }
}

export class ManagementActivityViewModel extends BaseViewModel<ManagementActivityUIState> {
  private repository: ManagementActivityRepository = new ManagementActivityRepository()
  private generation: number = 0
  private visible: boolean = false
  private loading: boolean = false

  async init(): Promise<void> {
    this.stop()
    this.visible = true
    const generation = this.generation
    this.ui.permissionState = ManagementPermissionState.Checking
    if (!this.repository.isApiClientInitialized()) {
      this.fail(ManagementErrorCode.RequestFailed, 'management_access_no_server')
      return
    }
    const access = await this.repository.checkCurrentUserAccess()
    if (!this.isActive(generation)) return
    if (access !== ManagementErrorCode.None) {
      this.fail(access, 'management_permission_denied')
      return
    }
    this.ui.permissionState = ManagementPermissionState.Allowed
    await this.loadPage(0)
  }

  stop(): void {
    this.visible = false
    this.generation += 1
    this.loading = false
  }

  async selectView(view: ManagementActivityView): Promise<void> {
    if (view === this.ui.view) return
    this.ui.view = view
    this.generation += 1
    this.loading = false
    this.visible = true
    await this.loadPage(0)
  }

  async loadPage(pageIndex: number): Promise<void> {
    if (!this.visible || this.loading) return
    this.loading = true
    this.ui.refreshing = true
    this.ui.pageIndex = Math.max(0, pageIndex)
    const generation = this.generation
    if (this.ui.sourceItems.length === 0) this.ui.status = ManagementSectionStatus.Loading
    try {
      const page = await this.repository.loadPage(this.ui.pageIndex, this.ui.view)
      if (!this.isActive(generation)) return
      this.ui.sourceItems = page.items
      this.ui.totalRecordCount = page.totalRecordCount
      this.ui.pageCount = ManagementActivityQuery.pageCount(page.totalRecordCount)
      this.ui.displayedPageIndex = this.ui.pageIndex
      this.ui.retryPageIndex = this.ui.pageIndex
      this.ui.stale = false
      this.applyFilters()
    } catch (error) {
      if (!this.isActive(generation)) return
      const code = this.repository.classifyError(error as Object)
      this.ui.retryPageIndex = this.ui.pageIndex
      this.ui.errorCode = code
      this.ui.message = code === ManagementErrorCode.Unsupported ?
        'management_section_unsupported' : 'management_activity_failed'
      this.ui.stale = this.ui.sourceItems.length > 0
      this.ui.status = code === ManagementErrorCode.Unsupported ?
        ManagementSectionStatus.Unsupported : ManagementSectionStatus.Error
    } finally {
      if (generation === this.generation) {
        this.loading = false
        this.ui.refreshing = false
      }
    }
  }

  async refresh(): Promise<void> {
    await this.loadPage(this.ui.displayedPageIndex)
  }

  applyFilters(): void {
    const options = new ManagementActivityFilterOptions()
    options.text = this.ui.query
    options.fromMs = this.ui.fromMs
    options.toMs = this.ui.toMs
    options.severities = this.ui.selectedSeverities
    options.userNames = this.ui.selectedUsers
    options.sortField = this.ui.sortField
    options.ascending = this.ui.ascending
    this.ui.items = ManagementActivityQuery.applyCurrentPage(this.ui.sourceItems, options)
    this.ui.status = this.ui.items.length > 0 ? ManagementSectionStatus.Content : ManagementSectionStatus.Empty
  }

  setSort(field: ManagementActivitySortField): void {
    if (this.ui.sortField === field) {
      this.ui.ascending = !this.ui.ascending
    } else {
      this.ui.sortField = field
      this.ui.ascending = field !== ManagementActivitySortField.Date
    }
    this.applyFilters()
  }

  async loadMedia(itemId: string): Promise<ManagementActivityMediaTarget> {
    if (this.ui.pendingItemId) return new ManagementActivityMediaTarget()
    this.ui.pendingItemId = itemId
    try {
      return await this.repository.loadMediaTarget(itemId)
    } finally {
      this.ui.pendingItemId = ''
    }
  }

  private fail(code: ManagementErrorCode, message: string): void {
    this.ui.permissionState = code === ManagementErrorCode.PermissionDenied ?
      ManagementPermissionState.Denied : ManagementPermissionState.Error
    this.ui.errorCode = code
    this.ui.message = message
    this.ui.status = ManagementSectionStatus.Error
  }

  private isActive(generation: number): boolean {
    return this.visible && generation === this.generation
  }
}
