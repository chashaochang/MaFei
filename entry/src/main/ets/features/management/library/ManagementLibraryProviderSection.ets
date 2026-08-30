import { LibraryOptionInfoDto } from 'sdk/src/main/ets/generated-client/models'
import { ThemeStyle } from '../../../entity/ThemeStyle'
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import {
  ManagementLibraryAvailableOptions,
  ManagementLibraryAvailableTypeOptions,
  ManagementLibraryOptions,
  ManagementLibraryTypeOptions
} from './ManagementLibraryModels'

@ComponentV2
struct ManagementProviderMoveButton {
  @Require @Param icon: Resource
  @Require @Param label: Resource
  @Require @Param isEnabled: boolean
  @Require @Param themeStyle: ThemeStyle
  @Event onPress: () => void = () => {}

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.themeStyle)
  }

  private secondaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  build() {
    Button() {
      SymbolGlyph(this.icon)
        .fontSize(16)
        .fontColor([this.isEnabled ? this.secondaryTextColor() : $r('sys.color.icon_fourth')])
    }
    .width(this.isNativeTheme() ? 32 : 36)
    .height(this.isNativeTheme() ? 32 : 36)
    .borderRadius(this.isNativeTheme() ? 16 : 18)
    .backgroundColor(Color.Transparent)
    .enabled(this.isEnabled)
    .accessibilityText(this.label)
    .gesture(LongPressGesture().onAction(() => {
      this.getUIContext().getPromptAction().showToast({ message: this.label })
    }))
    .onClick(() => this.onPress())
  }
}

@ComponentV2
struct ManagementProviderGroup {
  @Require @Param title: ResourceStr
  @Param items: LibraryOptionInfoDto[] | undefined = undefined
  @Require @Param enabledNames: string[]
  @Require @Param order: string[]
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param onMoveProvider: (order: string[], from: number, to: number) => string[]
  @Event onUpdate: (enabled: string[], order: string[]) => void =
    (_enabled: string[], _order: string[]) => {}

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.themeStyle)
  }

  private primaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private names(): string[] {
    return (this.items || []).map((item: LibraryOptionInfoDto): string => item.Name || '')
      .filter((name: string): boolean => name.length > 0)
  }

  private orderedNames(): string[] {
    const available = this.names()
    const result = this.order.filter((name: string): boolean => available.includes(name))
    available.forEach((name: string) => {
      if (!result.includes(name)) result.push(name)
    })
    return result
  }

  private toggled(name: string): string[] {
    return this.enabledNames.includes(name) ?
      this.enabledNames.filter((value: string): boolean => value !== name) : this.enabledNames.concat([name])
  }

  private move(index: number, target: number): void {
    this.onUpdate(this.enabledNames, this.onMoveProvider(this.orderedNames(), index, target))
  }

  build() {
    if (this.names().length > 0) {
      Column({ space: 6 }) {
        Text(this.title)
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .textAlign(TextAlign.Start)
        Column() {
          ForEach(this.orderedNames(), (name: string, index: number) => {
            if (this.isNativeTheme()) {
              Column() {
                Row({ space: 8 }) {
                  Toggle({ type: ToggleType.Checkbox, isOn: this.enabledNames.includes(name) })
                    .width(24)
                    .height(24)
                    .onChange(() => this.onUpdate(this.toggled(name), this.orderedNames()))
                  Text(name)
                    .layoutWeight(1)
                    .fontSize(14)
                    .fontColor(this.enabledNames.includes(name) ?
                      this.primaryTextColor() : this.secondaryTextColor())
                    .maxLines(2)
                    .textAlign(TextAlign.Start)
                    .onClick(() => this.onUpdate(this.toggled(name), this.orderedNames()))
                  Row({ space: 2 }) {
                    ManagementProviderMoveButton({
                      icon: $r('sys.symbol.chevron_up'),
                      label: $r('app.string.management_library_provider_move_up'),
                      isEnabled: index > 0,
                      themeStyle: this.themeStyle,
                      onPress: () => this.move(index, index - 1)
                    })
                    ManagementProviderMoveButton({
                      icon: $r('sys.symbol.chevron_down'),
                      label: $r('app.string.management_library_provider_move_down'),
                      isEnabled: index < this.orderedNames().length - 1,
                      themeStyle: this.themeStyle,
                      onPress: () => this.move(index, index + 1)
                    })
                  }
                }
                .width('100%')
                .constraintSize({ minHeight: 48 })
                .padding({ top: 4, bottom: 4 })
                if (index < this.orderedNames().length - 1) {
                  Divider()
                    .color(AppThemeSurfaceResolver.contentDivider(this.themeStyle, this.nativeThemeAvailable))
                    .margin({ left: 40 })
                }
              }
              .width('100%')
            } else {
              Row({ space: 6 }) {
                Stack() {
                  SymbolGlyph($r('sys.symbol.checkmark'))
                    .fontSize(16)
                    .fontColor([this.enabledNames.includes(name) ? $r('app.color.color_main') : Color.Transparent])
                }
                .width(24)
                .height(36)
                .onClick(() => this.onUpdate(this.toggled(name), this.orderedNames()))
                Text(name)
                  .layoutWeight(1)
                  .fontSize(13)
                  .fontColor(this.enabledNames.includes(name) ?
                    this.primaryTextColor() : this.secondaryTextColor())
                  .maxLines(2)
                  .textAlign(TextAlign.Start)
                  .onClick(() => this.onUpdate(this.toggled(name), this.orderedNames()))
                ManagementProviderMoveButton({
                  icon: $r('sys.symbol.chevron_up'),
                  label: $r('app.string.management_library_provider_move_up'),
                  isEnabled: index > 0,
                  themeStyle: this.themeStyle,
                  onPress: () => this.move(index, index - 1)
                })
                ManagementProviderMoveButton({
                  icon: $r('sys.symbol.chevron_down'),
                  label: $r('app.string.management_library_provider_move_down'),
                  isEnabled: index < this.orderedNames().length - 1,
                  themeStyle: this.themeStyle,
                  onPress: () => this.move(index, index + 1)
                })
              }
              .width('calc(100% - 16vp)')
              .constraintSize({ minHeight: 48 })
              .padding({ left: 8, right: 8, top: 4, bottom: 4 })
            }
          }, (name: string) => name)
        }
      }
      .alignItems(HorizontalAlign.Start)
    }
  }
}

@ComponentV2
struct ManagementTypeProviderGroups {
  @Require @Param typeOption: ManagementLibraryTypeOptions
  @Require @Param typeIndex: number
  @Param available: ManagementLibraryAvailableTypeOptions | undefined = undefined
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param onMoveProvider: (order: string[], from: number, to: number) => string[]
  @Event onUpdate: (typeIndex: number, field: number, enabled: string[], order: string[]) => void =
    (_typeIndex: number, _field: number, _enabled: string[], _order: string[]) => {}

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.themeStyle)
  }

  private primaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  build() {
    if (this.available) {
      Column({ space: 12 }) {
        Text(`${this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(
          'management_library_provider_item_type') || ''} ${this.typeOption.Type || ''}`)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .textAlign(TextAlign.Start)
        ManagementProviderGroup({
          title: $r('app.string.management_library_metadata_fetchers'),
          items: this.available?.MetadataFetchers,
          enabledNames: this.typeOption.MetadataFetchers || [],
          order: this.typeOption.MetadataFetcherOrder || [],
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          onMoveProvider: this.onMoveProvider,
          onUpdate: (enabled: string[], order: string[]) => this.onUpdate(this.typeIndex, 0, enabled, order)
        })
        ManagementProviderGroup({
          title: $r('app.string.management_library_image_fetchers'),
          items: this.available?.ImageFetchers,
          enabledNames: this.typeOption.ImageFetchers || [],
          order: this.typeOption.ImageFetcherOrder || [],
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          onMoveProvider: this.onMoveProvider,
          onUpdate: (enabled: string[], order: string[]) => this.onUpdate(this.typeIndex, 1, enabled, order)
        })
        ManagementProviderGroup({
          title: $r('app.string.management_library_similar_item_providers'),
          items: this.available?.SimilarItemProviders,
          enabledNames: this.typeOption.SimilarItemProviders || [],
          order: this.typeOption.SimilarItemProviderOrder || [],
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          onMoveProvider: this.onMoveProvider,
          onUpdate: (enabled: string[], order: string[]) => this.onUpdate(this.typeIndex, 2, enabled, order)
        })
      }
      .alignItems(HorizontalAlign.Start)
    }
  }
}

@ComponentV2
struct ManagementProviderContent {
  @Require @Param options: ManagementLibraryOptions
  @Require @Param available: ManagementLibraryAvailableOptions
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param onMoveProvider: (order: string[], from: number, to: number) => string[]
  @Event onOptionsChange: (options: ManagementLibraryOptions) => void =
    (_options: ManagementLibraryOptions) => {}

  private cloneOptions(): ManagementLibraryOptions {
    return JSON.parse(JSON.stringify(this.options)) as ManagementLibraryOptions
  }

  private names(items: LibraryOptionInfoDto[] | undefined): string[] {
    return (items || []).map((item: LibraryOptionInfoDto): string => item.Name || '')
      .filter((name: string): boolean => name.length > 0)
  }

  private enabledNames(items: LibraryOptionInfoDto[] | undefined, disabled: string[]): string[] {
    return this.names(items).filter((name: string): boolean => !disabled.includes(name))
  }

  private enabledInOrder(enabled: string[], order: string[]): string[] {
    return order.filter((name: string): boolean => enabled.includes(name))
  }

  private disabledFrom(items: LibraryOptionInfoDto[] | undefined, enabled: string[]): string[] {
    return this.names(items).filter((name: string): boolean => !enabled.includes(name))
  }

  private availableType(type: string): ManagementLibraryAvailableTypeOptions | undefined {
    return (this.available.TypeOptions || []).find((item: ManagementLibraryAvailableTypeOptions): boolean =>
      (item.Type || '') === type)
  }

  private updateMetadataSavers(enabled: string[], order: string[]): void {
    const next = this.cloneOptions()
    next.MetadataSavers = this.enabledInOrder(enabled, order)
    this.onOptionsChange(next)
  }

  private updateMetadataReaders(enabled: string[], order: string[]): void {
    const next = this.cloneOptions()
    next.DisabledLocalMetadataReaders = this.disabledFrom(this.available.MetadataReaders, enabled)
    next.LocalMetadataReaderOrder = order.slice()
    this.onOptionsChange(next)
  }

  private updateSubtitleFetchers(enabled: string[], order: string[]): void {
    const next = this.cloneOptions()
    next.DisabledSubtitleFetchers = this.disabledFrom(this.available.SubtitleFetchers, enabled)
    next.SubtitleFetcherOrder = order.slice()
    this.onOptionsChange(next)
  }

  private updateLyricFetchers(enabled: string[], order: string[]): void {
    const next = this.cloneOptions()
    next.DisabledLyricFetchers = this.disabledFrom(this.available.LyricFetchers, enabled)
    next.LyricFetcherOrder = order.slice()
    this.onOptionsChange(next)
  }

  private updateSegmentProviders(enabled: string[], order: string[]): void {
    const next = this.cloneOptions()
    next.DisabledMediaSegmentProviders = this.disabledFrom(this.available.MediaSegmentProviders, enabled)
    next.MediaSegmentProvideOrder = order.slice()
    next.MediaSegmentProviderOrder = order.slice()
    this.onOptionsChange(next)
  }

  private updateTypeProviders(typeIndex: number, field: number, enabled: string[], order: string[]): void {
    const next = this.cloneOptions()
    const typeOptions = (next.TypeOptions || []).slice() as ManagementLibraryTypeOptions[]
    if (typeIndex < 0 || typeIndex >= typeOptions.length) return
    const typeOption = JSON.parse(JSON.stringify(typeOptions[typeIndex])) as ManagementLibraryTypeOptions
    if (field === 0) {
      typeOption.MetadataFetchers = this.enabledInOrder(enabled, order)
      typeOption.MetadataFetcherOrder = order.slice()
    } else if (field === 1) {
      typeOption.ImageFetchers = this.enabledInOrder(enabled, order)
      typeOption.ImageFetcherOrder = order.slice()
    } else {
      typeOption.SimilarItemProviders = this.enabledInOrder(enabled, order)
      typeOption.SimilarItemProviderOrder = order.slice()
    }
    typeOptions[typeIndex] = typeOption
    next.TypeOptions = typeOptions
    this.onOptionsChange(next)
  }

  build() {
    Column({ space: 16 }) {
      ManagementProviderGroup({
        title: $r('app.string.management_library_metadata_savers'),
        items: this.available.MetadataSavers,
        enabledNames: this.options.MetadataSavers || [],
        order: this.options.MetadataSavers || [],
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onMoveProvider: this.onMoveProvider,
        onUpdate: (enabled: string[], order: string[]) => this.updateMetadataSavers(enabled, order)
      })
      ManagementProviderGroup({
        title: $r('app.string.management_library_metadata_readers'),
        items: this.available.MetadataReaders,
        enabledNames: this.enabledNames(
          this.available.MetadataReaders, this.options.DisabledLocalMetadataReaders || []),
        order: this.options.LocalMetadataReaderOrder || [],
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onMoveProvider: this.onMoveProvider,
        onUpdate: (enabled: string[], order: string[]) => this.updateMetadataReaders(enabled, order)
      })
      ManagementProviderGroup({
        title: $r('app.string.management_library_subtitle_fetchers'),
        items: this.available.SubtitleFetchers,
        enabledNames: this.enabledNames(
          this.available.SubtitleFetchers, this.options.DisabledSubtitleFetchers || []),
        order: this.options.SubtitleFetcherOrder || [],
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onMoveProvider: this.onMoveProvider,
        onUpdate: (enabled: string[], order: string[]) => this.updateSubtitleFetchers(enabled, order)
      })
      ManagementProviderGroup({
        title: $r('app.string.management_library_lyric_fetchers'),
        items: this.available.LyricFetchers,
        enabledNames: this.enabledNames(
          this.available.LyricFetchers, this.options.DisabledLyricFetchers || []),
        order: this.options.LyricFetcherOrder || [],
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onMoveProvider: this.onMoveProvider,
        onUpdate: (enabled: string[], order: string[]) => this.updateLyricFetchers(enabled, order)
      })
      ManagementProviderGroup({
        title: $r('app.string.management_library_media_segment_providers'),
        items: this.available.MediaSegmentProviders,
        enabledNames: this.enabledNames(
          this.available.MediaSegmentProviders, this.options.DisabledMediaSegmentProviders || []),
        order: this.options.MediaSegmentProviderOrder || this.options.MediaSegmentProvideOrder || [],
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onMoveProvider: this.onMoveProvider,
        onUpdate: (enabled: string[], order: string[]) => this.updateSegmentProviders(enabled, order)
      })
      ForEach((this.options.TypeOptions || []) as ManagementLibraryTypeOptions[],
        (typeOption: ManagementLibraryTypeOptions, index: number) => {
          ManagementTypeProviderGroups({
            typeOption: typeOption,
            typeIndex: index,
            available: this.availableType(typeOption.Type || ''),
            themeStyle: this.themeStyle,
            nativeThemeAvailable: this.nativeThemeAvailable,
            onMoveProvider: this.onMoveProvider,
            onUpdate: (typeIndex: number, field: number, enabled: string[], order: string[]) =>
              this.updateTypeProviders(typeIndex, field, enabled, order)
          })
        }, (typeOption: ManagementLibraryTypeOptions) => typeOption.Type || '')
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
export struct ManagementLibraryProviderSection {
  @Require @Param options: ManagementLibraryOptions
  @Require @Param available: ManagementLibraryAvailableOptions
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param onMoveProvider: (order: string[], from: number, to: number) => string[]
  @Event onOptionsChange: (options: ManagementLibraryOptions) => void =
    (_options: ManagementLibraryOptions) => {}

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

  build() {
    Column() {
      Column({ space: this.isNativeTheme() ? 10 : 16 }) {
        if (this.isNativeTheme()) {
          Row({ space: 12 }) {
            Text($r('app.string.management_library_providers'))
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontColor(this.primaryTextColor())
              .textAlign(TextAlign.Start)
          }
          .constraintSize({ minHeight: 48 })
        } else {
          Column({ space: 3 }) {
            Text($r('app.string.management_library_providers'))
              .fontSize(18)
              .fontWeight(FontWeight.Medium)
              .fontColor(this.primaryTextColor())
            Text($r('app.string.management_library_providers_hint'))
              .fontSize(12)
              .fontColor(this.secondaryTextColor())
              .maxLines(3)
          }
          .alignItems(HorizontalAlign.Start)
        }
        ManagementProviderContent({
          options: this.options,
          available: this.available,
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          onMoveProvider: this.onMoveProvider,
          onOptionsChange: (options: ManagementLibraryOptions) => this.onOptionsChange(options)
        })
      }
      .width(this.isNativeTheme() ? 'calc(100% - 24vp)' : 'calc(100% - 32vp)')
      .padding({
        left: this.isNativeTheme() ? 12 : 16,
        right: this.isNativeTheme() ? 12 : 16,
        top: this.isNativeTheme() ? 10 : 16,
        bottom: this.isNativeTheme() ? 10 : 16
      })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .backgroundColor(this.groupBackground())
    .clip(true)
  }
}
