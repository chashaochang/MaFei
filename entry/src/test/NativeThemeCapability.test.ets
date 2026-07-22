import { describe, expect, it } from '@ohos/hypium'
import { uiMaterial } from '@kit.ArkUI'
import {
  NativeThemeCapability,
  NativeThemeCapabilityReason,
  NativeThemeRuntime
} from '../main/ets/theme/NativeThemeCapability'

class FakeNativeThemeRuntime implements NativeThemeRuntime {
  sdkApiVersion: number
  materialInfo: uiMaterial.MaterialInfo
  readCount: number = 0
  shouldThrow: boolean = false

  constructor(sdkApiVersion: number, state: uiMaterial.MaterialState,
    type: uiMaterial.MaterialType = uiMaterial.MaterialType.IMMERSIVE) {
    this.sdkApiVersion = sdkApiVersion
    this.materialInfo = { state, type }
  }

  getSdkApiVersion(): number {
    return this.sdkApiVersion
  }

  getMaterialInfo(): uiMaterial.MaterialInfo {
    this.readCount += 1
    if (this.shouldThrow) {
      throw new Error('material read failed')
    }
    return this.materialInfo
  }
}

export default function nativeThemeCapabilityTest() {
  describe('NativeThemeCapability', () => {
    it('doesNotReadApi26MaterialOnApi25', 0, () => {
      const runtime = new FakeNativeThemeRuntime(25, uiMaterial.MaterialState.DEFAULT)
      const result = new NativeThemeCapability(runtime).initialize()

      expect(result.available).assertFalse()
      expect(result.reason).assertEqual(NativeThemeCapabilityReason.UnsupportedApi)
      expect(runtime.readCount).assertEqual(0)
    })

    it('acceptsDefaultImmersiveMaterial', 0, () => {
      const runtime = new FakeNativeThemeRuntime(26, uiMaterial.MaterialState.DEFAULT)
      const result = new NativeThemeCapability(runtime).initialize()

      expect(result.available).assertTrue()
      expect(result.reason).assertEqual(NativeThemeCapabilityReason.Available)
      expect(runtime.readCount).assertEqual(1)
    })

    it('rejectsEnableAndNonImmersiveMaterial', 0, () => {
      const enabledRuntime = new FakeNativeThemeRuntime(26, uiMaterial.MaterialState.ENABLE)
      const wrongTypeRuntime = new FakeNativeThemeRuntime(
        26,
        uiMaterial.MaterialState.DEFAULT,
        99 as uiMaterial.MaterialType
      )

      expect(new NativeThemeCapability(enabledRuntime).initialize().available).assertFalse()
      expect(new NativeThemeCapability(wrongTypeRuntime).initialize().available).assertFalse()
    })

    it('cachesTheFirstMaterialRead', 0, () => {
      const runtime = new FakeNativeThemeRuntime(26, uiMaterial.MaterialState.DEFAULT)
      const capability = new NativeThemeCapability(runtime)

      capability.initialize()
      capability.current()
      capability.isAvailable()

      expect(runtime.readCount).assertEqual(1)
    })

    it('convertsReadFailureToUnavailableResult', 0, () => {
      const runtime = new FakeNativeThemeRuntime(26, uiMaterial.MaterialState.DEFAULT)
      runtime.shouldThrow = true
      const result = new NativeThemeCapability(runtime).initialize()

      expect(result.available).assertFalse()
      expect(result.reason).assertEqual(NativeThemeCapabilityReason.MaterialReadFailed)
    })
  })
}
