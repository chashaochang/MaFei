import { describe, expect, it } from '@ohos/hypium'
import {
  NativeThemeCapability,
  NativeThemeCapabilityReason,
  NativeThemeMaterialInfo,
  NativeThemeRenderMode,
  NativeThemeRuntime
} from '../main/ets/theme/NativeThemeCapability'

const DEFAULT_MATERIAL_STATE: number = 0
const ENABLED_MATERIAL_STATE: number = 1
const IMMERSIVE_MATERIAL_TYPE: number = 2

class FakeNativeThemeRuntime implements NativeThemeRuntime {
  sdkApiVersion: number
  materialInfo: NativeThemeMaterialInfo
  readCount: number = 0
  shouldThrow: boolean = false

  constructor(sdkApiVersion: number, state: number = DEFAULT_MATERIAL_STATE,
    type: number = IMMERSIVE_MATERIAL_TYPE) {
    this.sdkApiVersion = sdkApiVersion
    this.materialInfo = { state, type }
  }

  getSdkApiVersion(): number {
    return this.sdkApiVersion
  }

  getMaterialInfo(): NativeThemeMaterialInfo {
    this.readCount += 1
    if (this.shouldThrow) {
      throw new Error('material read failed')
    }
    return this.materialInfo
  }
}

export default function nativeThemeCapabilityTest() {
  describe('NativeThemeCapability', () => {
    it('rejectsNativeThemeBeforeApi24WithoutReadingMaterial', 0, () => {
      const runtime = new FakeNativeThemeRuntime(23)
      const result = new NativeThemeCapability(runtime).initialize()

      expect(result.available).assertFalse()
      expect(result.systemMaterialAvailable).assertFalse()
      expect(result.renderMode).assertEqual(NativeThemeRenderMode.Unsupported)
      expect(result.reason).assertEqual(NativeThemeCapabilityReason.UnsupportedApi)
      expect(runtime.readCount).assertEqual(0)
    })

    it('usesBlurFallbackOnApi24To25WithoutReadingApi26Material', 0, () => {
      const api24Runtime = new FakeNativeThemeRuntime(24)
      const api25Runtime = new FakeNativeThemeRuntime(25)

      const api24Result = new NativeThemeCapability(api24Runtime).initialize()
      const api25Result = new NativeThemeCapability(api25Runtime).initialize()

      expect(api24Result.available).assertTrue()
      expect(api25Result.available).assertTrue()
      expect(api24Result.systemMaterialAvailable).assertFalse()
      expect(api25Result.systemMaterialAvailable).assertFalse()
      expect(api24Result.renderMode).assertEqual(NativeThemeRenderMode.BlurFallback)
      expect(api25Result.renderMode).assertEqual(NativeThemeRenderMode.BlurFallback)
      expect(api24Runtime.readCount).assertEqual(0)
      expect(api25Runtime.readCount).assertEqual(0)
    })

    it('acceptsDefaultImmersiveMaterial', 0, () => {
      const runtime = new FakeNativeThemeRuntime(26)
      const result = new NativeThemeCapability(runtime).initialize()

      expect(result.available).assertTrue()
      expect(result.systemMaterialAvailable).assertTrue()
      expect(result.renderMode).assertEqual(NativeThemeRenderMode.SystemMaterial)
      expect(result.reason).assertEqual(NativeThemeCapabilityReason.Available)
      expect(runtime.readCount).assertEqual(1)
    })

    it('fallsBackWhenApi26MaterialConfigurationDoesNotMatch', 0, () => {
      const enabledRuntime = new FakeNativeThemeRuntime(26, ENABLED_MATERIAL_STATE)
      const wrongTypeRuntime = new FakeNativeThemeRuntime(
        26,
        DEFAULT_MATERIAL_STATE,
        99
      )

      const enabledResult = new NativeThemeCapability(enabledRuntime).initialize()
      const wrongTypeResult = new NativeThemeCapability(wrongTypeRuntime).initialize()

      expect(enabledResult.available).assertTrue()
      expect(wrongTypeResult.available).assertTrue()
      expect(enabledResult.systemMaterialAvailable).assertFalse()
      expect(wrongTypeResult.systemMaterialAvailable).assertFalse()
      expect(enabledResult.renderMode).assertEqual(NativeThemeRenderMode.BlurFallback)
      expect(wrongTypeResult.renderMode).assertEqual(NativeThemeRenderMode.BlurFallback)
      expect(enabledResult.reason)
        .assertEqual(NativeThemeCapabilityReason.MaterialConfigurationMismatch)
      expect(wrongTypeResult.reason)
        .assertEqual(NativeThemeCapabilityReason.MaterialConfigurationMismatch)
    })

    it('cachesTheFirstMaterialRead', 0, () => {
      const runtime = new FakeNativeThemeRuntime(26)
      const capability = new NativeThemeCapability(runtime)

      capability.initialize()
      capability.current()
      capability.isAvailable()
      capability.isSystemMaterialAvailable()

      expect(runtime.readCount).assertEqual(1)
    })

    it('convertsReadFailureToBlurFallbackResult', 0, () => {
      const runtime = new FakeNativeThemeRuntime(26)
      runtime.shouldThrow = true
      const result = new NativeThemeCapability(runtime).initialize()

      expect(result.available).assertTrue()
      expect(result.systemMaterialAvailable).assertFalse()
      expect(result.renderMode).assertEqual(NativeThemeRenderMode.BlurFallback)
      expect(result.reason).assertEqual(NativeThemeCapabilityReason.MaterialReadFailed)
    })
  })
}
