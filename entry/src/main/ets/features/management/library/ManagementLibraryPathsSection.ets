import { ThemeStyle } from '../../../entity/ThemeStyle'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'

@ComponentV2
export struct ManagementLibraryPathsSection {
  @Require @Param paths: string[]
  @Require @Param pathErrors: Map<string, string>
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param onAdd: () => void
  @Require @Param onRemove: (path: string) => void

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

  private groupBackground(): ResourceColor {
    return this.isNativeTheme() ? AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1')
  }

  private getString(name: string): string {
    try {
      return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
    } catch (error) {
      return name
    }
  }

  private errorText(path: string): string {
    const error = this.pathErrors.get(path) || ''
    if (!error) return ''
    return error.startsWith('management_') ? this.getString(error) : error
  }

  @Builder
  pathRow(path: string, index: number) {
    Column() {
      Row({ space: 10 }) {
        SymbolGlyph($r('sys.symbol.externaldrive'))
          .fontSize(20)
          .fontColor([$r('app.color.color_main')])
        Column({ space: 4 }) {
          Text(path)
            .fontSize(14)
            .fontColor(this.primaryTextColor())
            .maxLines(3)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .textAlign(TextAlign.Start)
          if (this.errorText(path).length > 0) {
            Text(this.errorText(path))
              .fontSize(12)
              .fontColor($r('app.color.main_red_color'))
              .maxLines(3)
              .textAlign(TextAlign.Start)
          }
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        Button() {
          SymbolGlyph($r('sys.symbol.trash'))
            .fontSize(18)
            .fontColor([$r('app.color.main_red_color')])
        }
        .width(40)
        .height(40)
        .borderRadius(20)
        .backgroundColor(Color.Transparent)
        .systemMaterial(this.useNativeMaterial() ?
          AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive) :
          AppThemeSurfaceResolver.disabledSystemMaterial())
        .accessibilityText($r('app.string.management_library_path_remove'))
        .onClick(() => this.onRemove(path))
      }
      .width('calc(100% - 24vp)')
      .constraintSize({ minHeight: 58 })
      .padding({ left: 12, right: 12, top: 8, bottom: 8 })
      if (index < this.paths.length - 1) {
        Divider()
          .color(this.secondaryTextColor())
          .opacity(0.2)
          .margin({ left: 42 })
      }
    }
    .width('100%')
  }

  build() {
    Column() {
      Column({ space: 12 }) {
        Row({ space: 12 }) {
          Column({ space: 3 }) {
            Text($r('app.string.management_library_paths'))
              .fontSize(18)
              .fontWeight(FontWeight.Medium)
              .fontColor(this.primaryTextColor())
            Text($r('app.string.management_library_paths_hint'))
              .fontSize(12)
              .fontColor(this.secondaryTextColor())
              .maxLines(3)
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
          Button($r('app.string.management_library_add_path'), { type: ButtonType.Normal })
            .height(38)
            .padding({ left: 16, right: 16 })
            .borderRadius(19)
            .fontColor($r('app.color.color_main'))
            .backgroundColor(Color.Transparent)
            .systemMaterial(this.useNativeMaterial() ?
              AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive) :
              AppThemeSurfaceResolver.disabledSystemMaterial())
            .onClick(() => this.onAdd())
        }
        if (this.paths.length === 0) {
          Text($r('app.string.management_library_paths_empty'))
            .fontSize(13)
            .fontColor(this.secondaryTextColor())
            .textAlign(TextAlign.Start)
            .padding({ top: 6, bottom: 6 })
        } else {
          Column() {
            ForEach(this.paths, (path: string, index: number) => {
              this.pathRow(path, index)
            }, (path: string) => path)
          }
        }
      }
      .width('calc(100% - 32vp)')
      .padding({ left: 16, right: 16, top: 16, bottom: 16 })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .backgroundColor(this.groupBackground())
    .clip(true)
  }
}
