import { DeviceInfoDto } from 'sdk/src/main/ets/generated-client/models';
import { ApiClient } from '../../../network/ApiClient';
import { ManagementErrorCode } from '../ManagementModels';
import { ManagementRepository } from '../ManagementRepository';
import { ManagementDeviceApiService } from './ManagementDeviceApiService';
import {
  ManagementDeviceActionResult,
  ManagementDeviceBatchResult,
  ManagementDeviceItem
} from './ManagementDeviceModels';
import { ManagementDeviceProtection } from './ManagementDeviceProtection';

interface DeviceHttpResponse {
  status: number
}

interface DeviceHttpError {
  isAxiosError?: boolean
  response?: DeviceHttpResponse
}

export class ManagementDeviceRepository {
  private service: ManagementDeviceApiService = new ManagementDeviceApiService()
  private management: ManagementRepository = new ManagementRepository()

  isApiClientInitialized(): boolean {
    return this.management.isApiClientInitialized()
  }

  currentDeviceId(): string {
    return ApiClient.Instance().deviceInfo.id
  }

  async checkCurrentUserAccess(): Promise<ManagementErrorCode> {
    return this.management.checkCurrentUserAccess()
  }

  async loadDevices(): Promise<ManagementDeviceItem[]> {
    const currentDeviceId = this.currentDeviceId()
    return (await this.service.getDevices()).map((device: DeviceInfoDto) =>
      this.mapDevice(device, currentDeviceId))
  }

  async rename(device: ManagementDeviceItem, value: string): Promise<ManagementDeviceActionResult> {
    try {
      ManagementDeviceProtection.assertDeviceId(device.id)
    } catch (error) {
      return this.failure(ManagementErrorCode.RequestFailed, 'management_device_action_failed')
    }

    const accessCode = await this.checkCurrentUserAccess()
    if (accessCode !== ManagementErrorCode.None) {
      return this.failure(accessCode, 'management_permission_check_failed')
    }
    const customName = value.trim()
    const comparisonName = device.customName || device.name
    if (customName === comparisonName) {
      const skipped = new ManagementDeviceActionResult()
      skipped.ok = true
      skipped.skipped = true
      return skipped
    }
    try {
      await this.service.updateName(device.id, customName)
      const result = new ManagementDeviceActionResult()
      result.ok = true
      return result
    } catch (error) {
      return this.failure(this.classifyError(error as Object), 'management_device_rename_failed')
    }
  }

  async revoke(deviceId: string): Promise<ManagementDeviceActionResult> {
    const currentDeviceId = this.currentDeviceId()
    try {
      ManagementDeviceProtection.assertNotCurrentDevice(deviceId, currentDeviceId)
    } catch (error) {
      return this.failure(ManagementErrorCode.PermissionDenied, 'management_device_current_protected')
    }
    const accessCode = await this.checkCurrentUserAccess()
    if (accessCode !== ManagementErrorCode.None) {
      return this.failure(accessCode, 'management_permission_check_failed')
    }
    try {
      await this.service.revoke(deviceId, currentDeviceId)
      const result = new ManagementDeviceActionResult()
      result.ok = true
      return result
    } catch (error) {
      return this.failure(this.classifyError(error as Object), 'management_device_revoke_failed')
    }
  }

  async revokeOthers(devices: ManagementDeviceItem[]): Promise<ManagementDeviceBatchResult> {
    const result = new ManagementDeviceBatchResult()
    const currentDeviceId = this.currentDeviceId()
    if (!currentDeviceId) {
      for (const device of devices) {
        if (device.id.length > 0) {
          result.failedIds.push(device.id)
        }
      }
      return result
    }
    const accessCode = await this.checkCurrentUserAccess()
    if (accessCode !== ManagementErrorCode.None) {
      result.failedIds = ManagementDeviceProtection.revocableIds(devices, currentDeviceId)
      return result
    }
    const ids = ManagementDeviceProtection.revocableIds(devices, currentDeviceId)
    for (const id of ids) {
      try {
        ManagementDeviceProtection.assertNotCurrentDevice(id, currentDeviceId)
        await this.service.revoke(id, currentDeviceId)
        result.succeededIds.push(id)
      } catch (error) {
        result.failedIds.push(id)
      }
    }
    return result
  }

  classifyError(error: Object): ManagementErrorCode {
    const httpError = error as DeviceHttpError
    const status = httpError.response?.status
    if (status === 401) return ManagementErrorCode.AuthenticationExpired
    if (status === 403) return ManagementErrorCode.PermissionDenied
    if (status === 404) return ManagementErrorCode.Unsupported
    return httpError.isAxiosError === true && httpError.response === undefined ?
      ManagementErrorCode.Offline : ManagementErrorCode.RequestFailed
  }

  private mapDevice(device: DeviceInfoDto, currentDeviceId: string): ManagementDeviceItem {
    const result = new ManagementDeviceItem()
    result.id = device.Id || ''
    result.name = device.Name || ''
    result.customName = device.CustomName || ''
    result.displayName = result.customName || result.name
    result.appName = device.AppName || ''
    result.appVersion = device.AppVersion || ''
    result.lastUserId = device.LastUserId || ''
    result.lastUserName = device.LastUserName || ''
    result.lastActivityDate = device.DateLastActivity || ''
    result.current = result.id.length > 0 && result.id === currentDeviceId
    return result
  }

  private failure(code: ManagementErrorCode, message: string): ManagementDeviceActionResult {
    const result = new ManagementDeviceActionResult()
    result.errorCode = code
    result.message = message
    return result
  }
}
