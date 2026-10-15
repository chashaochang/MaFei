import { ManagementDeviceItem } from './ManagementDeviceModels';

export class ManagementDeviceProtection {
  static assertDeviceId(deviceId: string): void {
    if (!deviceId) {
      throw Error('management_device_action_failed')
    }
  }

  static assertNotCurrentDevice(deviceId: string, currentDeviceId: string): void {
    ManagementDeviceProtection.assertDeviceId(deviceId)
    if (!currentDeviceId || deviceId === currentDeviceId) {
      throw Error('management_device_current_protected')
    }
  }

  static revocableIds(devices: ManagementDeviceItem[], currentDeviceId: string):
    string[] {
    if (!currentDeviceId) return []
    return devices.filter((item: ManagementDeviceItem) =>
      !item.current && item.id.length > 0 && item.id !== currentDeviceId)
      .map((item: ManagementDeviceItem) => item.id)
  }
}
