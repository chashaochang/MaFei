import { describe, expect, it } from '@ohos/hypium';
import { uiMaterial } from '@kit.ArkUI';
import { deviceInfo } from '@kit.BasicServicesKit';

export default function nativeThemeMaterialTest() {
  describe('NativeThemeMaterialReleaseGate', () => {
    it('runsOnApi26OrLater', 0, () => {
      expect(deviceInfo.sdkApiVersion >= 26).assertTrue();
    })

    it('usesDefaultImmersiveMaterialPolicy', 0, () => {
      if (deviceInfo.sdkApiVersion < 26) {
        expect(deviceInfo.sdkApiVersion >= 26).assertTrue();
        return;
      }
      const info = uiMaterial.getMaterialInfo();

      expect(info.state).assertEqual(uiMaterial.MaterialState.DEFAULT);
      expect(info.type).assertEqual(uiMaterial.MaterialType.IMMERSIVE);
    })
  })
}
