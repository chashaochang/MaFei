export enum AppThemeMaterialRole {
  ContentGroup = 'content_group',
  Floating = 'floating',
  InteractiveFloating = 'interactive_floating',
  PrimaryInteractive = 'primary_interactive',
  AdaptiveInteractive = 'adaptive_interactive',
  AdaptiveControl = 'adaptive_control',
  MusicMiniPlayer = 'music_mini_player',
  HomeLibraryChip = 'home_library_chip',
  HomeLibraryChipSelected = 'home_library_chip_selected'
}

export class AppThemeSurfaceModifier implements AttributeModifier<CommonAttribute> {
  private readonly role: AppThemeMaterialRole
  private readonly nativeSurface: boolean
  private readonly systemMaterialAvailable: boolean
  private readonly systemMaterial?: SystemUiMaterial

  constructor(role: AppThemeMaterialRole, nativeSurface: boolean,
    systemMaterialAvailable: boolean, systemMaterial?: SystemUiMaterial) {
    this.role = role
    this.nativeSurface = nativeSurface
    this.systemMaterialAvailable = systemMaterialAvailable
    this.systemMaterial = systemMaterial
  }

  applyNormalAttribute(instance: CommonAttribute): void {
    if (this.systemMaterialAvailable) {
      // systemMaterial is API 26. Never enter this branch on the blur fallback path.
      instance.systemMaterial(this.systemMaterial)
      return
    }
    if (!this.nativeSurface) {
      // Attributes previously owned by this modifier return to their defaults when omitted.
      // This preserves the caller's Feiniu-theme surface styling.
      return
    }
    this.applyBlurFallback(instance)
  }

  private applyBlurFallback(instance: CommonAttribute): void {
    if (this.role === AppThemeMaterialRole.ContentGroup) {
      instance.backgroundColor($r('app.color.native_fallback_content_group'))
      return
    }
    if (this.role === AppThemeMaterialRole.PrimaryInteractive) {
      instance
        .backgroundBlurStyle(BlurStyle.Thin)
        .backgroundColor($r('app.color.color_main'))
      return
    }
    if (this.role === AppThemeMaterialRole.Floating) {
      instance
        .backgroundBlurStyle(BlurStyle.Thin)
        .backgroundColor($r('app.color.native_fallback_floating'))
      return
    }
    if (this.role === AppThemeMaterialRole.InteractiveFloating) {
      instance
        .backgroundBlurStyle(BlurStyle.Thin)
        .backgroundColor($r('app.color.native_fallback_interactive_floating'))
      return
    }
    if (this.role === AppThemeMaterialRole.AdaptiveControl) {
      instance
        .backgroundBlurStyle(BlurStyle.Thin)
        .backgroundColor($r('app.color.native_fallback_adaptive_interactive'))
      return
    }
    if (this.role === AppThemeMaterialRole.MusicMiniPlayer) {
      instance
        .backgroundBlurStyle(BlurStyle.Thin)
        .backgroundColor($r('app.color.native_fallback_music_mini_player'))
      return
    }
    if (this.role === AppThemeMaterialRole.HomeLibraryChip) {
      instance
        .backgroundBlurStyle(BlurStyle.Thin)
        .backgroundColor($r('app.color.native_fallback_home_library_chip'))
      return
    }
    if (this.role === AppThemeMaterialRole.HomeLibraryChipSelected) {
      instance
        .backgroundBlurStyle(BlurStyle.Thin)
        .backgroundColor($r('app.color.native_fallback_home_library_chip_selected'))
      return
    }
    if (this.role === AppThemeMaterialRole.AdaptiveInteractive) {
      instance.backgroundBlurStyle(BlurStyle.Thin)
    }
  }
}
