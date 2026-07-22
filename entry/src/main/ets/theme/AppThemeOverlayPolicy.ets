import { ThemeStyle } from '../entity/ThemeStyle'

export enum OverlaySurfaceRole {
  PlatformDefault = 'platform_default',
  AppFloating = 'app_floating',
  Dangerous = 'dangerous'
}

export enum OverlayMaterialDecision {
  LegacyStyle = 'legacy_style',
  UsePlatformDefault = 'use_platform_default',
  UseFloatingMaterial = 'use_floating_material',
  DisableSystemMaterial = 'disable_system_material'
}

export class AppThemeOverlayPolicy {
  static resolve(role: OverlaySurfaceRole, style: ThemeStyle,
    api26OrLater: boolean, nativeThemeAvailable: boolean): OverlayMaterialDecision {
    if (!api26OrLater) {
      return OverlayMaterialDecision.LegacyStyle
    }
    if (role === OverlaySurfaceRole.Dangerous || style === ThemeStyle.Feiniu ||
      !nativeThemeAvailable) {
      return OverlayMaterialDecision.DisableSystemMaterial
    }
    if (role === OverlaySurfaceRole.AppFloating) {
      return OverlayMaterialDecision.UseFloatingMaterial
    }
    return OverlayMaterialDecision.UsePlatformDefault
  }
}
