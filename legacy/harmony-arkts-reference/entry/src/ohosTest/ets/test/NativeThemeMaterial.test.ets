import { describe, expect, it } from '@ohos/hypium';
import { uiMaterial } from '@kit.ArkUI';
import { deviceInfo } from '@kit.BasicServicesKit';
import {
  NativeThemeCapability,
  NativeThemeRenderMode
} from '../../../main/ets/theme/NativeThemeCapability';

export default function nativeThemeMaterialTest() {
  describe('NativeThemeMaterialReleaseGate', () => {
    it('usesBlurFallbackOnApi24To25', 0, () => {
      const sdkApiVersion = deviceInfo.sdkApiVersion;
      if (sdkApiVersion < NativeThemeCapability.MIN_API_VERSION ||
        sdkApiVersion >= NativeThemeCapability.MIN_SYSTEM_MATERIAL_API_VERSION) {
        return;
      }

      const result = new NativeThemeCapability().initialize();

      expect(result.systemMaterialAvailable).assertFalse();
      expect(result.renderMode).assertEqual(NativeThemeRenderMode.BlurFallback);
    })

    it('usesDefaultImmersiveMaterialPolicyOnApi26OrLater', 0, () => {
      if (deviceInfo.sdkApiVersion < NativeThemeCapability.MIN_SYSTEM_MATERIAL_API_VERSION) {
        return;
      }
      const info = uiMaterial.getMaterialInfo();

      expect(info.state).assertEqual(uiMaterial.MaterialState.DEFAULT);
      expect(info.type).assertEqual(uiMaterial.MaterialType.IMMERSIVE);
    })
  })
}
