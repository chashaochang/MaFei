import { uiMaterial } from '@kit.ArkUI'
import { deviceInfo } from '@kit.BasicServicesKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

export enum NativeThemeCapabilityReason {
  Available = 'available',
  UnsupportedApi = 'unsupported_api',
  MaterialConfigurationMismatch = 'material_configuration_mismatch',
  MaterialReadFailed = 'material_read_failed'
}

export enum NativeThemeRenderMode {
  Unsupported = 'unsupported',
  BlurFallback = 'blur_fallback',
  SystemMaterial = 'system_material'
}

export interface NativeThemeMaterialInfo {
  state: number
  type: number
}

export interface NativeThemeCapabilityResult {
  available: boolean
  systemMaterialAvailable: boolean
  renderMode: NativeThemeRenderMode
  reason: NativeThemeCapabilityReason
  sdkApiVersion: number
  materialState?: number
  materialType?: number
}

export interface NativeThemeRuntime {
  getSdkApiVersion(): number
  getMaterialInfo(): NativeThemeMaterialInfo
}

class SystemNativeThemeRuntime implements NativeThemeRuntime {
  getSdkApiVersion(): number {
    return deviceInfo.sdkApiVersion
  }

  getMaterialInfo(): NativeThemeMaterialInfo {
    return uiMaterial.getMaterialInfo()
  }
}

export class NativeThemeCapability {
  static readonly MIN_API_VERSION: number = 24
  static readonly MIN_SYSTEM_MATERIAL_API_VERSION: number = 26
  private static readonly DEFAULT_MATERIAL_STATE: number = 0
  private static readonly IMMERSIVE_MATERIAL_TYPE: number = 2
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
        systemMaterialAvailable: false,
        renderMode: NativeThemeRenderMode.Unsupported,
        reason: NativeThemeCapabilityReason.UnsupportedApi,
        sdkApiVersion
      }
      return this.cachedResult
    }
    if (sdkApiVersion < NativeThemeCapability.MIN_SYSTEM_MATERIAL_API_VERSION) {
      this.cachedResult = {
        available: true,
        systemMaterialAvailable: false,
        renderMode: NativeThemeRenderMode.BlurFallback,
        reason: NativeThemeCapabilityReason.Available,
        sdkApiVersion
      }
      return this.cachedResult
    }
    try {
      const info = this.runtime.getMaterialInfo()
      const systemMaterialAvailable =
        info.state === NativeThemeCapability.DEFAULT_MATERIAL_STATE &&
        info.type === NativeThemeCapability.IMMERSIVE_MATERIAL_TYPE
      this.cachedResult = {
        available: true,
        systemMaterialAvailable,
        renderMode: systemMaterialAvailable ? NativeThemeRenderMode.SystemMaterial :
          NativeThemeRenderMode.BlurFallback,
        reason: systemMaterialAvailable ? NativeThemeCapabilityReason.Available :
          NativeThemeCapabilityReason.MaterialConfigurationMismatch,
        sdkApiVersion,
        materialState: info.state,
        materialType: info.type
      }
      if (!systemMaterialAvailable) {
        hilog.error(0x0000, 'Mafei',
          'Native material configuration mismatch; using blur fallback. state=%{public}d type=%{public}d',
          info.state, info.type)
      }
    } catch (error) {
      this.cachedResult = {
        available: true,
        systemMaterialAvailable: false,
        renderMode: NativeThemeRenderMode.BlurFallback,
        reason: NativeThemeCapabilityReason.MaterialReadFailed,
        sdkApiVersion
      }
      hilog.error(0x0000, 'Mafei',
        'Failed to read native material info; using blur fallback: %{public}s', String(error))
    }
    return this.cachedResult
  }

  current(): NativeThemeCapabilityResult {
    return this.initialize()
  }

  isAvailable(): boolean {
    return this.initialize().available
  }

  isSystemMaterialAvailable(): boolean {
    return this.initialize().systemMaterialAvailable
  }

  resetForTest(): void {
    this.cachedResult = undefined
  }
}

export const nativeThemeCapability: NativeThemeCapability = new NativeThemeCapability()
