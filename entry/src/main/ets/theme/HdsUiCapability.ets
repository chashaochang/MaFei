import { deviceInfo } from '@kit.BasicServicesKit';

export class HdsUiCapability {
  static supportsSystemMaterial(): boolean {
    return deviceInfo.distributionOSApiVersion >= 60100
  }
}
