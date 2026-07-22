import { describe, expect, it } from '@ohos/hypium'
import { ThemeStyle } from '../main/ets/entity/ThemeStyle'
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../main/ets/theme/AppThemeOverlayPolicy'

export default function appThemeOverlayPolicyTest() {
  describe('AppThemeOverlayPolicy', () => {
    it('keepsLegacyStyleBelowApi26', 0, () => {
      const decision = AppThemeOverlayPolicy.resolve(
        OverlaySurfaceRole.AppFloating,
        ThemeStyle.Feiniu,
        false,
        false
      )
      expect(decision).assertEqual(OverlayMaterialDecision.LegacyStyle)
    })

    it('usesPlatformDefaultForNativeSystemOverlay', 0, () => {
      const decision = AppThemeOverlayPolicy.resolve(
        OverlaySurfaceRole.PlatformDefault,
        ThemeStyle.Native,
        true,
        true
      )
      expect(decision).assertEqual(OverlayMaterialDecision.UsePlatformDefault)
    })

    it('usesFloatingMaterialForNativeAppOverlay', 0, () => {
      const decision = AppThemeOverlayPolicy.resolve(
        OverlaySurfaceRole.AppFloating,
        ThemeStyle.Native,
        true,
        true
      )
      expect(decision).assertEqual(OverlayMaterialDecision.UseFloatingMaterial)
    })

    it('disablesApi26MaterialForFeiniuAndDangerousOverlays', 0, () => {
      const feiniu = AppThemeOverlayPolicy.resolve(
        OverlaySurfaceRole.PlatformDefault,
        ThemeStyle.Feiniu,
        true,
        true
      )
      const dangerous = AppThemeOverlayPolicy.resolve(
        OverlaySurfaceRole.Dangerous,
        ThemeStyle.Native,
        true,
        true
      )
      expect(feiniu).assertEqual(OverlayMaterialDecision.DisableSystemMaterial)
      expect(dangerous).assertEqual(OverlayMaterialDecision.DisableSystemMaterial)
    })
  })
}
