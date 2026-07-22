import { uiMaterial } from '@kit.ArkUI'
import { ThemeStyle } from '../entity/ThemeStyle'

export enum AppThemeMaterialRole {
  ContentGroup = 'content_group',
  Floating = 'floating'
}

export class AppThemeSurfaceResolver {
  private static contentGroup?: uiMaterial.Material
  private static floating?: uiMaterial.Material

  static useNativeSurface(style: ThemeStyle, available: boolean): boolean {
    return style === ThemeStyle.Native && available
  }

  static appCanvasBackground(ready: boolean, effectiveTheme: ThemeStyle): ResourceColor {
    if (!ready) {
      return $r('app.color.start_window_background')
    }
    return effectiveTheme === ThemeStyle.Native ?
      $r('sys.color.comp_background_primary') : $r('app.color.bg_main')
  }

  static routeBackground(style: ThemeStyle, available: boolean): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(style, available) ?
      Color.Transparent : $r('app.color.bg_main')
  }

  static iconWellBackground(style: ThemeStyle): ResourceColor {
    return style === ThemeStyle.Native ?
      $r('sys.color.comp_background_tertiary') : $r('app.color.component_bg')
  }

  static interactionOverlay(style: ThemeStyle): ResourceColor {
    return style === ThemeStyle.Native ?
      $r('sys.color.interactive_pressed') : $r('app.color.primary_light_hover')
  }

  static material(role: AppThemeMaterialRole): uiMaterial.Material {
    if (role === AppThemeMaterialRole.ContentGroup) {
      if (!AppThemeSurfaceResolver.contentGroup) {
        AppThemeSurfaceResolver.contentGroup = new uiMaterial.ImmersiveMaterial({
          style: uiMaterial.ImmersiveStyle.THIN,
          materialColor: Color.Transparent,
          colorInvert: false,
          applyShadow: false,
          interactive: false
        })
      }
      return AppThemeSurfaceResolver.contentGroup
    }
    if (!AppThemeSurfaceResolver.floating) {
      AppThemeSurfaceResolver.floating = new uiMaterial.ImmersiveMaterial({
        style: uiMaterial.ImmersiveStyle.REGULAR,
        materialColor: Color.Transparent,
        colorInvert: false,
        applyShadow: true,
        interactive: false
      })
    }
    return AppThemeSurfaceResolver.floating
  }

  static disabledSystemMaterial(): uiMaterial.Material {
    return uiMaterial.Material.empty
  }
}
