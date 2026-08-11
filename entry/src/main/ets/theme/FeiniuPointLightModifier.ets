import { uiEffect } from '@kit.ArkGraphics2D'
import { deviceInfo } from '@kit.BasicServicesKit'
import { hdsEffect } from '@kit.UIDesignKit'
import { NativeThemeConstructionProbe } from './NativeThemeConstructionProbe'

export interface FeiniuPointLightOptions {
  enabled: boolean
  color?: ResourceColor
  intensity?: number
  height?: Dimension
}

export class FeiniuPointLightModifier {
  // The legacy shell is the complete fallback for API 20–25.  Although the
  // HDS effect API is present on some of those builds, constructing it there
  // makes the wide/tablet shell diverge from the verified fallback path.
  static readonly MIN_API_VERSION: number = 26

  static build(options: FeiniuPointLightOptions): VisualEffect {
    if (deviceInfo.sdkApiVersion < FeiniuPointLightModifier.MIN_API_VERSION ||
      !options.enabled) {
      return uiEffect.createEffect()
    }

    NativeThemeConstructionProbe.markHdsEffectConstruction()
    return new hdsEffect.HdsEffectBuilder()
      .pointLight({
        illuminatedType: hdsEffect.PointLightIlluminatedType.DEFAULT_FEATHERING_BORDER,
        options: {
          color: options.color ?? Color.Transparent,
          intensity: options.intensity ?? 2,
          height: options.height ?? 0,
        }
      })
      .buildEffect()
  }
}
