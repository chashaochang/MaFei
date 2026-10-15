import { ThemeStyle } from '../entity/ThemeStyle'

export enum OverlaySurfaceRole {
  PlatformDefault = 'platform_default',
  AppFloating = 'app_floating',
  Dangerous = 'dangerous'
}

export enum OverlayMaterialDecision {
  LegacyStyle = 'legacy_style',
  UseBlurFallback = 'use_blur_fallback',
  UsePlatformDefault = 'use_platform_default',
  UseFloatingMaterial = 'use_floating_material',
  DisableSystemMaterial = 'disable_system_material'
}

export class AppThemeOverlayPolicy {
  static resolve(role: OverlaySurfaceRole, style: ThemeStyle,
    systemMaterialAvailable: boolean, nativeThemeAvailable: boolean): OverlayMaterialDecision {
    if (role === OverlaySurfaceRole.Dangerous || style === ThemeStyle.Feiniu ||
      !nativeThemeAvailable) {
      return systemMaterialAvailable ? OverlayMaterialDecision.DisableSystemMaterial :
        OverlayMaterialDecision.LegacyStyle
    }
    if (!systemMaterialAvailable) {
      return OverlayMaterialDecision.UseBlurFallback
    }
    if (role === OverlaySurfaceRole.AppFloating) {
      return OverlayMaterialDecision.UseFloatingMaterial
    }
    return OverlayMaterialDecision.UsePlatformDefault
  }
}
