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
export struct ManagementLibraryProviderSection {
  @Require @Param options: ManagementLibraryOptions
  @Require @Param available: ManagementLibraryAvailableOptions
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param onOptionsChange: (options: ManagementLibraryOptions) => void
  @Require @Param onMoveProvider: (order: string[], from: number, to: number) => string[]

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

  private cloneOptions(): ManagementLibraryOptions {
    const result: ManagementLibraryOptions = JSON.parse(JSON.stringify(this.options))
    return result
  }

  private names(items: LibraryOptionInfoDto[] | undefined): string[] {
    return (items || []).map((item: LibraryOptionInfoDto): string => item.Name || '')
      .filter((name: string): boolean => name.length > 0)
  }

  private orderedNames(items: LibraryOptionInfoDto[] | undefined, order: string[]): string[] {
    const available = this.names(items)
    const result = order.filter((name: string): boolean => available.includes(name))
    available.forEach((name: string) => {
      if (!result.includes(name)) result.push(name)
    })
    return result
  }

  private enabledNames(items: LibraryOptionInfoDto[] | undefined, disabled: string[]): string[] {
    return this.names(items).filter((name: string): boolean => !disabled.includes(name))
  }

  private toggled(enabled: string[], name: string): string[] {
    return enabled.includes(name) ? enabled.filter((value: string): boolean => value !== name) :
      enabled.concat([name])
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
    const typeOption: ManagementLibraryTypeOptions = JSON.parse(JSON.stringify(typeOptions[typeIndex]))
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

  private showMoveHint(label: ResourceStr): void {
    this.getUIContext().getPromptAction().showToast({ message: label })
  }

  @Builder
  moveButton(icon: Resource, label: Resource, enabled: boolean, action: () => void) {
    Button() {
      SymbolGlyph(icon)
        .fontSize(16)
        .fontColor([enabled ? this.secondaryTextColor() : $r('sys.color.icon_fourth')])
    }
    .width(this.isNativeTheme() ? 32 : 36)
    .height(this.isNativeTheme() ? 32 : 36)
    .borderRadius(this.isNativeTheme() ? 16 : 18)
    .backgroundColor(Color.Transparent)
    .enabled(enabled)
    .accessibilityText(label)
    .gesture(LongPressGesture().onAction(() => this.showMoveHint(label)))
    .onClick(action)
  }

  @Builder
  providerGroup(title: ResourceStr, items: LibraryOptionInfoDto[] | undefined,
    enabledNames: string[], order: string[], onUpdate: (enabled: string[], order: string[]) => void) {
    if (this.names(items).length > 0) {
      Column({ space: 6 }) {
        Text(title)
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .textAlign(TextAlign.Start)
        Column() {
          ForEach(this.orderedNames(items, order), (name: string, index: number) => {
            if (this.isNativeTheme()) {
              Column() {
                Row({ space: 8 }) {
                  Toggle({ type: ToggleType.Checkbox, isOn: enabledNames.includes(name) })
                    .width(24)
                    .height(24)
                    .onChange(() => onUpdate(this.toggled(enabledNames, name),
                      this.orderedNames(items, order)))
                  Text(name)
                    .layoutWeight(1)
                    .fontSize(14)
                    .fontColor(enabledNames.includes(name) ? this.primaryTextColor() : this.secondaryTextColor())
                    .maxLines(2)
                    .textAlign(TextAlign.Start)
                    .onClick(() => onUpdate(this.toggled(enabledNames, name),
                      this.orderedNames(items, order)))
                  Row({ space: 2 }) {
                    this.moveButton($r('sys.symbol.chevron_up'),
                      $r('app.string.management_library_provider_move_up'), index > 0, () => {
                        onUpdate(enabledNames, this.onMoveProvider(this.orderedNames(items, order), index, index - 1))
                      })
                    this.moveButton($r('sys.symbol.chevron_down'),
                      $r('app.string.management_library_provider_move_down'),
                      index < this.orderedNames(items, order).length - 1, () => {
                        onUpdate(enabledNames, this.onMoveProvider(this.orderedNames(items, order), index, index + 1))
                      })
                  }
                }
                .width('100%')
                .constraintSize({ minHeight: 48 })
                .padding({ top: 4, bottom: 4 })
                if (index < this.orderedNames(items, order).length - 1) {
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
                    .fontColor([enabledNames.includes(name) ? $r('app.color.color_main') : Color.Transparent])
                }
                .width(24)
                .height(36)
                .onClick(() => onUpdate(this.toggled(enabledNames, name),
                  this.orderedNames(items, order)))
                Text(name)
                  .layoutWeight(1)
                  .fontSize(13)
                  .fontColor(enabledNames.includes(name) ? this.primaryTextColor() : this.secondaryTextColor())
                  .maxLines(2)
                  .textAlign(TextAlign.Start)
                  .onClick(() => onUpdate(this.toggled(enabledNames, name),
                    this.orderedNames(items, order)))
                this.moveButton($r('sys.symbol.chevron_up'),
                  $r('app.string.management_library_provider_move_up'), index > 0, () => {
                    onUpdate(enabledNames, this.onMoveProvider(this.orderedNames(items, order), index, index - 1))
                  })
                this.moveButton($r('sys.symbol.chevron_down'),
                  $r('app.string.management_library_provider_move_down'),
                  index < this.orderedNames(items, order).length - 1, () => {
                    onUpdate(enabledNames, this.onMoveProvider(this.orderedNames(items, order), index, index + 1))
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

  @Builder
  typeProviderGroups(typeOption: ManagementLibraryTypeOptions, index: number) {
    if (this.availableType(typeOption.Type || '')) {
      Column({ space: 12 }) {
        Text(`${this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(
          'management_library_provider_item_type') || ''} ${typeOption.Type || ''}`)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .textAlign(TextAlign.Start)
        this.providerGroup($r('app.string.management_library_metadata_fetchers'),
          this.availableType(typeOption.Type || '')?.MetadataFetchers,
          typeOption.MetadataFetchers || [], typeOption.MetadataFetcherOrder || [],
          (enabled: string[], order: string[]) => this.updateTypeProviders(index, 0, enabled, order))
        this.providerGroup($r('app.string.management_library_image_fetchers'),
          this.availableType(typeOption.Type || '')?.ImageFetchers,
          typeOption.ImageFetchers || [], typeOption.ImageFetcherOrder || [],
          (enabled: string[], order: string[]) => this.updateTypeProviders(index, 1, enabled, order))
        this.providerGroup($r('app.string.management_library_similar_item_providers'),
          this.availableType(typeOption.Type || '')?.SimilarItemProviders,
          typeOption.SimilarItemProviders || [], typeOption.SimilarItemProviderOrder || [],
          (enabled: string[], order: string[]) => this.updateTypeProviders(index, 2, enabled, order))
      }
      .alignItems(HorizontalAlign.Start)
    }
  }

  @Builder
  providerContent() {
    Column({ space: 16 }) {
        this.providerGroup($r('app.string.management_library_metadata_savers'),
          this.available.MetadataSavers, this.options.MetadataSavers || [],
          this.options.MetadataSavers || [],
          (enabled: string[], order: string[]) => this.updateMetadataSavers(enabled, order))
        this.providerGroup($r('app.string.management_library_metadata_readers'),
          this.available.MetadataReaders,
          this.enabledNames(this.available.MetadataReaders, this.options.DisabledLocalMetadataReaders || []),
          this.options.LocalMetadataReaderOrder || [],
          (enabled: string[], order: string[]) => this.updateMetadataReaders(enabled, order))
        this.providerGroup($r('app.string.management_library_subtitle_fetchers'),
          this.available.SubtitleFetchers,
          this.enabledNames(this.available.SubtitleFetchers, this.options.DisabledSubtitleFetchers || []),
          this.options.SubtitleFetcherOrder || [],
          (enabled: string[], order: string[]) => this.updateSubtitleFetchers(enabled, order))
        this.providerGroup($r('app.string.management_library_lyric_fetchers'),
          this.available.LyricFetchers,
          this.enabledNames(this.available.LyricFetchers, this.options.DisabledLyricFetchers || []),
          this.options.LyricFetcherOrder || [],
          (enabled: string[], order: string[]) => this.updateLyricFetchers(enabled, order))
        this.providerGroup($r('app.string.management_library_media_segment_providers'),
          this.available.MediaSegmentProviders,
          this.enabledNames(this.available.MediaSegmentProviders,
            this.options.DisabledMediaSegmentProviders || []),
          this.options.MediaSegmentProviderOrder || this.options.MediaSegmentProvideOrder || [],
          (enabled: string[], order: string[]) => this.updateSegmentProviders(enabled, order))
        ForEach((this.options.TypeOptions || []) as ManagementLibraryTypeOptions[],
          (typeOption: ManagementLibraryTypeOptions, index: number) => {
            this.typeProviderGroups(typeOption, index)
          }, (typeOption: ManagementLibraryTypeOptions) => typeOption.Type || '')
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
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
        this.providerContent()
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
