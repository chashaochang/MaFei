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
  static readonly MIN_API_VERSION: number = 23

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
