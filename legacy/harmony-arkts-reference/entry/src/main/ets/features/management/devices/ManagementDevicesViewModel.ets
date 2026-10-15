import { BaseViewModel } from '../../../common/base/BaseViewModel';
import {
  ManagementErrorCode,
  ManagementPermissionState,
  ManagementSectionStatus
} from '../ManagementModels';
import {
  MANAGEMENT_DEVICE_PAGE_SIZE,
  ManagementDeviceActionResult,
  ManagementDeviceBatchResult,
  ManagementDeviceItem,
  ManagementDeviceQueryOptions,
  ManagementDeviceSortField
} from './ManagementDeviceModels';
import { ManagementDeviceProtection } from './ManagementDeviceProtection';
import { ManagementDeviceQuery } from './ManagementDeviceQuery';
import { ManagementDeviceRepository } from './ManagementDeviceRepository';
import { ManagementDevicesUIState } from './ManagementDevicesUIState';

export class ManagementDevicesViewModel extends BaseViewModel<ManagementDevicesUIState> {
  private repository: ManagementDeviceRepository = new ManagementDeviceRepository()
  private generation: number = 0
  private visible: boolean = false
  private loading: boolean = false

  async init(): Promise<void> {
    this.stop()
    this.visible = true
    const generation = this.generation
    this.ui.permissionState = ManagementPermissionState.Checking
    if (!this.repository.isApiClientInitialized()) {
      this.deny(ManagementErrorCode.RequestFailed, 'management_access_no_server')
      return
    }
    const accessCode = await this.repository.checkCurrentUserAccess()
    if (!this.isActive(generation)) return
    if (accessCode !== ManagementErrorCode.None) {
      this.deny(accessCode, accessCode === ManagementErrorCode.PermissionDenied ?
        'management_permission_denied' : 'management_permission_check_failed')
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
    if (this.ui.sourceDevices.length === 0) this.ui.status = ManagementSectionStatus.Loading
    try {
      const devices = await this.repository.loadDevices()
      if (!this.isActive(generation)) return
      this.ui.sourceDevices = devices
      this.ui.stale = false
      this.ui.errorCode = ManagementErrorCode.None
      this.applyQuery()
      if (!this.ui.selectedDeviceId && devices.length > 0) {
        this.ui.selectedDeviceId = devices[0].id
      }
    } catch (error) {
      if (!this.isActive(generation)) return
      const code = this.repository.classifyError(error as Object)
      this.ui.errorCode = code
      this.ui.message = code === ManagementErrorCode.Unsupported ?
        'management_device_unsupported' : 'management_device_load_failed'
      this.ui.stale = this.ui.sourceDevices.length > 0
      this.ui.status = code === ManagementErrorCode.Unsupported ?
        ManagementSectionStatus.Unsupported : ManagementSectionStatus.Error
    } finally {
      if (generation === this.generation) {
        this.loading = false
        this.ui.refreshing = false
      }
    }
  }

  applyQuery(resetPage: boolean = false): void {
    if (resetPage) this.ui.pageIndex = 0
    const options = new ManagementDeviceQueryOptions()
    options.text = this.ui.query
    options.fromMs = this.ui.fromMs
    options.toMs = this.ui.toMs
    options.userNames = this.ui.selectedUsers
    options.sortField = this.ui.sortField
    options.ascending = this.ui.ascending
    options.pageIndex = this.ui.pageIndex
    options.pageSize = MANAGEMENT_DEVICE_PAGE_SIZE
    const page = ManagementDeviceQuery.apply(this.ui.sourceDevices, options)
    this.ui.devices = page.items
    this.ui.pageIndex = page.pageIndex
    this.ui.pageCount = page.pageCount
    this.ui.totalCount = page.totalCount
    this.ui.status = page.totalCount > 0 ? ManagementSectionStatus.Content : ManagementSectionStatus.Empty
  }

  setPage(pageIndex: number): void {
    this.ui.pageIndex = pageIndex
    this.applyQuery()
  }

  setSort(field: ManagementDeviceSortField): void {
    if (this.ui.sortField === field) {
      this.ui.ascending = !this.ui.ascending
    } else {
      this.ui.sortField = field
      this.ui.ascending = field !== ManagementDeviceSortField.LastActivity
    }
    this.applyQuery(true)
  }

  selectDevice(deviceId: string): void {
    this.ui.selectedDeviceId = deviceId
  }

  selectedDevice(): ManagementDeviceItem | null {
    return this.ui.sourceDevices.find((item: ManagementDeviceItem) =>
      item.id === this.ui.selectedDeviceId) || null
  }

  startRename(device: ManagementDeviceItem): void {
    this.ui.editingDeviceId = device.id
    this.ui.renameDraft = device.customName || device.name
  }

  async rename(device: ManagementDeviceItem): Promise<ManagementDeviceActionResult> {
    if (this.ui.pendingDeviceId || this.ui.batchPending) return this.lockedResult()
    this.ui.pendingDeviceId = device.id
    try {
      const result = await this.repository.rename(device, this.ui.renameDraft)
      if (result.ok) {
        this.ui.editingDeviceId = ''
        await this.refresh()
      }
      return result
    } finally {
      this.ui.pendingDeviceId = ''
    }
  }

  async revoke(device: ManagementDeviceItem): Promise<ManagementDeviceActionResult> {
    if (this.ui.pendingDeviceId || this.ui.batchPending) return this.lockedResult()
    try {
      ManagementDeviceProtection.assertNotCurrentDevice(device.id, this.repository.currentDeviceId())
    } catch (error) {
      const result = new ManagementDeviceActionResult()
      result.errorCode = ManagementErrorCode.PermissionDenied
      result.message = 'management_device_current_protected'
      return result
    }
    this.ui.pendingDeviceId = device.id
    try {
      const result = await this.repository.revoke(device.id)
      if (result.ok) await this.refresh()
      return result
    } finally {
      this.ui.pendingDeviceId = ''
    }
  }

  async revokeOthers(): Promise<ManagementDeviceBatchResult> {
    if (this.ui.pendingDeviceId || this.ui.batchPending) return new ManagementDeviceBatchResult()
    this.ui.batchPending = true
    try {
      const result = await this.repository.revokeOthers(this.ui.sourceDevices)
      this.ui.batchResult = result
      await this.refresh()
      return result
    } finally {
      this.ui.batchPending = false
    }
  }

  private lockedResult(): ManagementDeviceActionResult {
    const result = new ManagementDeviceActionResult()
    result.errorCode = ManagementErrorCode.RequestFailed
    result.message = 'management_device_action_in_progress'
    return result
  }

  private deny(code: ManagementErrorCode, message: string): void {
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
