import { CollectionTypeOptions } from 'sdk/src/main/ets/generated-client/models'
import { ThemeStyle } from '../../../entity/ThemeStyle'
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { ManagementNativeSelect } from '../ManagementNativeSelect'

@ComponentV2
export struct ManagementLibraryBasicSection {
  @Require @Param name: string
  @Require @Param collectionType: CollectionTypeOptions
  @Require @Param types: CollectionTypeOptions[]
  @Require @Param isNew: boolean
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param onNameChange: (value: string) => void
  @Require @Param onTypeChange: (value: CollectionTypeOptions) => void

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

  private inputBackground(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.comp_background_tertiary') : $r('app.color.component_bg')
  }

  private typeLabel(type: CollectionTypeOptions): ResourceStr {
    if (type === CollectionTypeOptions.Movies) return $r('app.string.management_library_type_movies')
    if (type === CollectionTypeOptions.Tvshows) return $r('app.string.management_library_type_tvshows')
    if (type === CollectionTypeOptions.Music) return $r('app.string.management_library_type_music')
    if (type === CollectionTypeOptions.Musicvideos) return $r('app.string.management_library_type_musicvideos')
    if (type === CollectionTypeOptions.Homevideos) return $r('app.string.management_library_type_homevideos')
    if (type === CollectionTypeOptions.Boxsets) return $r('app.string.management_library_type_boxsets')
    if (type === CollectionTypeOptions.Books) return $r('app.string.management_library_type_books')
    return $r('app.string.management_library_type_mixed')
  }

  private typeLabels(): ResourceStr[] {
    return this.types.map((type: CollectionTypeOptions): ResourceStr => this.typeLabel(type))
  }

  private selectedTypeIndex(): number {
    const index = this.types.indexOf(this.collectionType)
    return index >= 0 ? index : 0
  }

  build() {
    Column() {
      Column({ space: 14 }) {
        Text($r('app.string.management_library_basic'))
          .fontSize(this.isNativeTheme() ? 16 : 18)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .textAlign(TextAlign.Start)
        Column({ space: 6 }) {
          Text($r('app.string.management_library_name'))
            .fontSize(13)
            .fontColor(this.secondaryTextColor())
            .textAlign(TextAlign.Start)
          TextInput({
            text: this.name,
            placeholder: $r('app.string.management_library_name_placeholder')
          })
            .height(44)
            .borderRadius(8)
            .backgroundColor(this.inputBackground())
            .fontColor(this.primaryTextColor())
            .onChange((value: string) => this.onNameChange(value))
        }
        .alignItems(HorizontalAlign.Start)
        Row({ space: 12 }) {
          if (this.isNativeTheme()) {
            Text($r('app.string.management_library_type'))
              .layoutWeight(1)
              .fontSize(14)
              .fontColor(this.primaryTextColor())
              .textAlign(TextAlign.Start)
          } else {
            Column({ space: 3 }) {
              Text($r('app.string.management_library_type'))
                .fontSize(14)
                .fontColor(this.primaryTextColor())
              Text(this.isNew ? $r('app.string.management_library_type_create_hint') :
                $r('app.string.management_library_type_locked_hint'))
                .fontSize(12)
                .fontColor(this.secondaryTextColor())
                .maxLines(2)
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)
          }
          ManagementNativeSelect({
            options: this.typeLabels(),
            selectedIndex: this.selectedTypeIndex(),
            themeStyle: this.themeStyle,
            nativeThemeAvailable: this.nativeThemeAvailable,
            selectorWidth: this.isNativeTheme() ? 'auto' : 184,
            valueTextAlign: TextAlign.End,
            isEnabled: this.isNew,
            onSelect: (index: number) => {
              if (index >= 0 && index < this.types.length) this.onTypeChange(this.types[index])
            }
          })
        }
      }
      .width(this.isNativeTheme() ? 'calc(100% - 24vp)' : 'calc(100% - 32vp)')
      .padding({
        left: this.isNativeTheme() ? 12 : 16,
        right: this.isNativeTheme() ? 12 : 16,
        top: this.isNativeTheme() ? 14 : 16,
        bottom: this.isNativeTheme() ? 14 : 16
      })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .backgroundColor(this.groupBackground())
    .clip(true)
  }
}
