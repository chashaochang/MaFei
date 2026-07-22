import { uiMaterial } from '@kit.ArkUI'
import { deviceInfo } from '@kit.BasicServicesKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

export enum NativeThemeCapabilityReason {
  Available = 'available',
  UnsupportedApi = 'unsupported_api',
  MaterialConfigurationMismatch = 'material_configuration_mismatch',
  MaterialReadFailed = 'material_read_failed'
}

export interface NativeThemeCapabilityResult {
  available: boolean
  reason: NativeThemeCapabilityReason
  sdkApiVersion: number
  materialState?: uiMaterial.MaterialState
  materialType?: uiMaterial.MaterialType
}

export interface NativeThemeRuntime {
  getSdkApiVersion(): number
  getMaterialInfo(): uiMaterial.MaterialInfo
}

class SystemNativeThemeRuntime implements NativeThemeRuntime {
  getSdkApiVersion(): number {
    return deviceInfo.sdkApiVersion
  }

  getMaterialInfo(): uiMaterial.MaterialInfo {
    return uiMaterial.getMaterialInfo()
  }
}

export class NativeThemeCapability {
  static readonly MIN_API_VERSION: number = 26
  private runtime: NativeThemeRuntime
  private cachedResult?: NativeThemeCapabilityResult

  constructor(runtime: NativeThemeRuntime = new SystemNativeThemeRuntime()) {
    this.runtime = runtime
  }

  initialize(): NativeThemeCapabilityResult {
    if (this.cachedResult) {
      return this.cachedResult
    }
    const sdkApiVersion = this.runtime.getSdkApiVersion()
    if (sdkApiVersion < NativeThemeCapability.MIN_API_VERSION) {
      this.cachedResult = {
        available: false,
        reason: NativeThemeCapabilityReason.UnsupportedApi,
        sdkApiVersion
      }
      return this.cachedResult
    }
    try {
      const info = this.runtime.getMaterialInfo()
      const available = info.state === uiMaterial.MaterialState.DEFAULT &&
        info.type === uiMaterial.MaterialType.IMMERSIVE
      this.cachedResult = {
        available,
        reason: available ? NativeThemeCapabilityReason.Available :
          NativeThemeCapabilityReason.MaterialConfigurationMismatch,
        sdkApiVersion,
        materialState: info.state,
        materialType: info.type
      }
      if (!available) {
        hilog.error(0x0000, 'Mafei',
          'Native material configuration mismatch. state=%{public}d type=%{public}d',
          info.state, info.type)
      }
    } catch (error) {
      this.cachedResult = {
        available: false,
        reason: NativeThemeCapabilityReason.MaterialReadFailed,
        sdkApiVersion
      }
      hilog.error(0x0000, 'Mafei', 'Failed to read native material info: %{public}s', String(error))
    }
    return this.cachedResult
  }

  current(): NativeThemeCapabilityResult {
    return this.initialize()
  }

  isAvailable(): boolean {
    return this.initialize().available
  }

  resetForTest(): void {
    this.cachedResult = undefined
  }
}

export const nativeThemeCapability: NativeThemeCapability = new NativeThemeCapability()
