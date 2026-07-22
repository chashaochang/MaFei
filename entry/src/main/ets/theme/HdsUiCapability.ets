import { deviceInfo } from '@kit.BasicServicesKit';
import { ThemeStyle } from '../entity/ThemeStyle';
import { nativeThemeCapability } from './NativeThemeCapability';

export class HdsUiCapability {
  static readonly MIN_HDS_API_VERSION: number = 23

  static supportsHdsComponents(): boolean {
    return deviceInfo.sdkApiVersion >= HdsUiCapability.MIN_HDS_API_VERSION
  }

  static supportsNativeTheme(): boolean {
    return nativeThemeCapability.isAvailable()
  }

  static supportsSystemMaterial(): boolean {
    return nativeThemeCapability.isAvailable()
  }

  static resolveThemeStyle(style: ThemeStyle): ThemeStyle {
    if (style === ThemeStyle.Native && !nativeThemeCapability.isAvailable()) {
      return ThemeStyle.Feiniu
    }
    return style
  }
}
