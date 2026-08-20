import { uiMaterial } from '@kit.ArkUI'
import { ThemeStyle } from '../entity/ThemeStyle'
import { HdsUiCapability } from './HdsUiCapability'
import { AppThemeMaterialRole, AppThemeSurfaceModifier } from './AppThemeSurfaceModifier'

export { AppThemeMaterialRole, AppThemeSurfaceModifier } from './AppThemeSurfaceModifier'

export class AppThemeSurfaceResolver {
  private static contentGroup?: uiMaterial.Material
  private static floating?: uiMaterial.Material
  private static interactiveFloating?: uiMaterial.Material
  private static primaryInteractive?: uiMaterial.Material
  private static adaptiveInteractive?: uiMaterial.Material
  private static musicMiniPlayer?: uiMaterial.Material
  private static homeLibraryChip?: uiMaterial.Material
  private static homeLibraryChipSelected?: uiMaterial.Material
  private static interactiveLightEffect?: uiMaterial.LightEffectOptions

  private static lightEffect(): uiMaterial.LightEffectOptions {
    if (!AppThemeSurfaceResolver.interactiveLightEffect) {
      AppThemeSurfaceResolver.interactiveLightEffect = {
        color: Color.White
      }
    }
    return AppThemeSurfaceResolver.interactiveLightEffect
  }

  static useNativeSurface(style: ThemeStyle, available: boolean): boolean {
    return style === ThemeStyle.Native && available
  }

  static appCanvasBackground(ready: boolean, effectiveTheme: ThemeStyle): ResourceColor {
    if (!ready) {
      return $r('app.color.start_window_background')
    }
    return effectiveTheme === ThemeStyle.Native ?
      $r('app.color.native_canvas_background') : $r('app.color.bg_main')
  }

  static routeBackground(style: ThemeStyle, available: boolean): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(style, available) ?
      Color.Transparent : $r('app.color.bg_main')
  }

  static destinationCanvasBackground(style: ThemeStyle, available: boolean): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(style, available) ?
      $r('app.color.native_canvas_background') : $r('app.color.bg_main')
  }

  static iconWellBackground(style: ThemeStyle): ResourceColor {
    return style === ThemeStyle.Native ?
      $r('sys.color.comp_background_tertiary') : $r('app.color.component_bg')
  }

  static interactionOverlay(style: ThemeStyle): ResourceColor {
    return style === ThemeStyle.Native ?
      $r('sys.color.interactive_pressed') : $r('app.color.primary_light_hover')
  }

  static mediaPlaceholderBackground(style: ThemeStyle, available: boolean): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(style, available) ?
      $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2')
  }

  static contentOutline(style: ThemeStyle, available: boolean): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(style, available) ?
      Color.Transparent : $r('app.color.border')
  }

  static mediaOutline(style: ThemeStyle, available: boolean): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(style, available) ?
      Color.Transparent : $r('app.color.border_1')
  }

  static outlineWidth(style: ThemeStyle, available: boolean, feiniuWidth: number): number {
    return AppThemeSurfaceResolver.useNativeSurface(style, available) ? 0 : feiniuWidth
  }

  static contentDivider(style: ThemeStyle, available: boolean): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(style, available) ?
      $r('sys.color.comp_divider') : $r('app.color.border')
  }

  static contentGroupBackground(): ResourceColor {
    return $r('sys.color.comp_background_primary')
  }

  static modifier(role: AppThemeMaterialRole, nativeSurface: boolean,
    systemMaterialAvailable?: boolean): AppThemeSurfaceModifier {
    const capabilityAvailable = HdsUiCapability.supportsSystemMaterial()
    const useSystemMaterial = capabilityAvailable && (systemMaterialAvailable ?? true)
    const material = useSystemMaterial ?
      (nativeSurface ? AppThemeSurfaceResolver.material(role) :
        AppThemeSurfaceResolver.disabledSystemMaterial()) : undefined
    return new AppThemeSurfaceModifier(role, nativeSurface, useSystemMaterial, material)
  }

  static material(role: AppThemeMaterialRole): uiMaterial.Material | undefined {
    if (!HdsUiCapability.supportsSystemMaterial()) {
      return undefined
    }
    if (role === AppThemeMaterialRole.ContentGroup) {
      if (!AppThemeSurfaceResolver.contentGroup) {
        AppThemeSurfaceResolver.contentGroup = new uiMaterial.ImmersiveMaterial({
          style: uiMaterial.ImmersiveStyle.THIN,
          materialColor: $r('sys.color.comp_background_primary'),
          colorInvert: false,
          applyShadow: false,
          interactive: false
        })
      }
      return AppThemeSurfaceResolver.contentGroup
    }
    if (role === AppThemeMaterialRole.AdaptiveInteractive) {
      if (!AppThemeSurfaceResolver.adaptiveInteractive) {
        AppThemeSurfaceResolver.adaptiveInteractive = new uiMaterial.ImmersiveMaterial({
          interactive: true
        })
      }
      return AppThemeSurfaceResolver.adaptiveInteractive
    }
    if (role === AppThemeMaterialRole.MusicMiniPlayer) {
      if (!AppThemeSurfaceResolver.musicMiniPlayer) {
        AppThemeSurfaceResolver.musicMiniPlayer = new uiMaterial.ImmersiveMaterial({
          applyShadow: true,
          interactive: true,
          lightEffect: AppThemeSurfaceResolver.lightEffect()
        })
      }
      return AppThemeSurfaceResolver.musicMiniPlayer
    }
    if (role === AppThemeMaterialRole.InteractiveFloating) {
      if (!AppThemeSurfaceResolver.interactiveFloating) {
        AppThemeSurfaceResolver.interactiveFloating = new uiMaterial.ImmersiveMaterial({
          style: uiMaterial.ImmersiveStyle.REGULAR,
          materialColor: 'rgba(255,255,255,0.14)',
          colorInvert: false,
          applyShadow: true,
          interactive: true,
          lightEffect: AppThemeSurfaceResolver.lightEffect()
        })
      }
      return AppThemeSurfaceResolver.interactiveFloating
    }
    if (role === AppThemeMaterialRole.PrimaryInteractive) {
      if (!AppThemeSurfaceResolver.primaryInteractive) {
        AppThemeSurfaceResolver.primaryInteractive = new uiMaterial.ImmersiveMaterial({
          style: uiMaterial.ImmersiveStyle.REGULAR,
          materialColor: $r('app.color.color_main'),
          colorInvert: false,
          applyShadow: true,
          interactive: true,
          lightEffect: AppThemeSurfaceResolver.lightEffect()
        })
      }
      return AppThemeSurfaceResolver.primaryInteractive
    }
    if (role === AppThemeMaterialRole.HomeLibraryChip) {
      if (!AppThemeSurfaceResolver.homeLibraryChip) {
        AppThemeSurfaceResolver.homeLibraryChip = new uiMaterial.ImmersiveMaterial({
          applyShadow: false,
          interactive: true
        })
      }
      return AppThemeSurfaceResolver.homeLibraryChip
    }
    if (role === AppThemeMaterialRole.HomeLibraryChipSelected) {
      if (!AppThemeSurfaceResolver.homeLibraryChipSelected) {
        AppThemeSurfaceResolver.homeLibraryChipSelected = new uiMaterial.ImmersiveMaterial({
          style: uiMaterial.ImmersiveStyle.REGULAR,
          materialColor: 'rgba(255,255,255,0.82)',
          colorInvert: false,
          applyShadow: false,
          interactive: true,
          lightEffect: AppThemeSurfaceResolver.lightEffect()
        })
      }
      return AppThemeSurfaceResolver.homeLibraryChipSelected
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

  static disabledSystemMaterial(): uiMaterial.Material | undefined {
    if (!HdsUiCapability.supportsSystemMaterial()) {
      return undefined
    }
    return uiMaterial.Material.empty
  }
}
