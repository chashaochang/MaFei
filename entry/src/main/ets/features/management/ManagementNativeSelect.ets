import { SymbolGlyphModifier } from '@kit.ArkUI'
import { ThemeStyle } from '../../entity/ThemeStyle'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../theme/AppThemeTokens'

class ManagementSelectMenuElement implements MenuElement {
  value: ResourceStr
  action: () => void
  symbolIcon?: SymbolGlyphModifier

  constructor(value: ResourceStr, action: () => void, symbolIcon?: SymbolGlyphModifier) {
    this.value = value
    this.action = action
    this.symbolIcon = symbolIcon
  }
}

@ComponentV2
export struct ManagementNativeSelect {
  @Require @Param options: ResourceStr[]
  @Require @Param selectedIndex: number
  @Require @Param themeStyle: ThemeStyle
  @Require @Param onSelect: (index: number) => void
  @Param nativeThemeAvailable: boolean = false
  @Param selectorWidth: Length = 'auto'
  @Param valueTextAlign: TextAlign = TextAlign.End
  @Param menuPlacement: Placement = Placement.BottomRight
  @Param isEnabled: boolean = true

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.themeStyle)
  }

  private primaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private selectedLabel(): ResourceStr {
    if (this.selectedIndex < 0 || this.selectedIndex >= this.options.length) {
      return '-'
    }
    return this.options[this.selectedIndex]
  }

  private menuIcon(selected: boolean): SymbolGlyphModifier {
    const modifier = new SymbolGlyphModifier($r('sys.symbol.checkmark'))
    modifier.fontSize(16)
    modifier.fontColor([selected ? this.primaryTextColor() : Color.Transparent])
    return modifier
  }

  private menuItems(): MenuElement[] {
    return this.options.map((option: ResourceStr, index: number) =>
      new ManagementSelectMenuElement(
        option,
        () => this.onSelect(index),
        this.menuIcon(index === this.selectedIndex)))
  }

  build() {
    Button() {
      Row({ space: 6 }) {
        Text(this.selectedLabel())
          .layoutWeight(1)
          .fontSize(13)
          .fontColor(this.primaryTextColor())
          .textAlign(this.valueTextAlign)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        SymbolGlyph($r('sys.symbol.chevron_down'))
          .fontSize(12)
          .fontColor([this.secondaryTextColor()])
      }
      .constraintSize({ minWidth: 88, maxWidth: 196 })
    }
    .width(this.selectorWidth)
    .constraintSize({ minWidth: 112, maxWidth: 220 })
    .height(40)
    .padding({ left: 12, right: 12 })
    .borderRadius(8)
    .clip(true)
    .backgroundColor(this.useNativeMaterial() ?
      Color.Transparent : AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
    .systemMaterial(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive) : undefined)
    .enabled(this.isEnabled && this.options.length > 0)
    .bindMenu(this.menuItems(), {
      placement: this.menuPlacement,
      systemMaterial: this.useNativeMaterial() ?
        AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating) : undefined
    })
  }
}
