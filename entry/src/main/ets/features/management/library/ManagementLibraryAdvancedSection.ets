import {
  CountryInfo,
  CultureDto,
  EmbeddedSubtitleOptions,
  ImageOption
} from 'sdk/src/main/ets/generated-client/models'
import { ThemeStyle } from '../../../entity/ThemeStyle'
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { ManagementNativeSelect } from '../ManagementNativeSelect'
import {
  ManagementLibraryOptions,
  ManagementLibraryTypeOptions
} from './ManagementLibraryModels'

@ComponentV2
export struct ManagementLibraryAdvancedSection {
  @Require @Param options: ManagementLibraryOptions
  @Require @Param countries: CountryInfo[]
  @Require @Param cultures: CultureDto[]
  @Require @Param expanded: boolean
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param onExpandedChange: (expanded: boolean) => void
  @Require @Param onOptionsChange: (options: ManagementLibraryOptions) => void

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

  private cloneOptions(): ManagementLibraryOptions {
    const result: ManagementLibraryOptions = JSON.parse(JSON.stringify(this.options))
    return result
  }

  private parseNumber(value: string, fallback: number): number {
    const parsed = Number(value.trim())
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
  }

  private parseLines(value: string): string[] {
    const result: string[] = []
    value.split('\n').forEach((item: string) => {
      const normalized = item.trim()
      if (normalized.length > 0 && !result.includes(normalized)) result.push(normalized)
    })
    return result
  }

  private languageLabels(): ResourceStr[] {
    const labels: ResourceStr[] = [$r('app.string.management_library_automatic')]
    this.cultures.forEach((item: CultureDto) => labels.push(item.DisplayName || item.Name || ''))
    return labels
  }

  private languageIndex(): number {
    const value = this.options.PreferredMetadataLanguage || ''
    const index = this.cultures.findIndex((item: CultureDto): boolean => (item.Name || '') === value)
    return index < 0 ? 0 : index + 1
  }

  private countryLabels(): ResourceStr[] {
    const labels: ResourceStr[] = [$r('app.string.management_library_automatic')]
    this.countries.forEach((item: CountryInfo) => labels.push(item.DisplayName || item.Name || ''))
    return labels
  }

  private countryIndex(): number {
    const value = this.options.MetadataCountryCode || ''
    const index = this.countries.findIndex((item: CountryInfo): boolean =>
      (item.TwoLetterISORegionName || '') === value)
    return index < 0 ? 0 : index + 1
  }

  private embeddedOptions(): EmbeddedSubtitleOptions[] {
    return [
      EmbeddedSubtitleOptions.AllowAll,
      EmbeddedSubtitleOptions.AllowText,
      EmbeddedSubtitleOptions.AllowImage,
      EmbeddedSubtitleOptions.AllowNone
    ]
  }

  private embeddedLabels(): ResourceStr[] {
    return [
      $r('app.string.management_library_embedded_subtitles_all'),
      $r('app.string.management_library_embedded_subtitles_text'),
      $r('app.string.management_library_embedded_subtitles_image'),
      $r('app.string.management_library_embedded_subtitles_none')
    ]
  }

  private embeddedIndex(): number {
    const index = this.embeddedOptions().indexOf(
      this.options.AllowEmbeddedSubtitles || EmbeddedSubtitleOptions.AllowAll)
    return index >= 0 ? index : 0
  }

  private updateImageOption(typeIndex: number, imageIndex: number, limit: number, minWidth: number): void {
    const next = this.cloneOptions()
    const types = (next.TypeOptions || []).slice() as ManagementLibraryTypeOptions[]
    if (typeIndex < 0 || typeIndex >= types.length) return
    const type: ManagementLibraryTypeOptions = JSON.parse(JSON.stringify(types[typeIndex]))
    const images = (type.ImageOptions || []).slice() as ImageOption[]
    if (imageIndex < 0 || imageIndex >= images.length) return
    const image: ImageOption = JSON.parse(JSON.stringify(images[imageIndex]))
    image.Limit = limit
    image.MinWidth = minWidth
    images[imageIndex] = image
    type.ImageOptions = images
    types[typeIndex] = type
    next.TypeOptions = types
    this.onOptionsChange(next)
  }

  @Builder
  sectionTitle(title: ResourceStr) {
    if (this.isNativeTheme()) {
      Text(title)
        .width('100%')
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.secondaryTextColor())
        .textAlign(TextAlign.Start)
        .padding({ top: 8, bottom: 2 })
    }
  }

  @Builder
  booleanRow(label: ResourceStr, value: boolean, onChange: (value: boolean) => void) {
    Row({ space: 12 }) {
      Text(label)
        .layoutWeight(1)
        .fontSize(14)
        .fontColor(this.primaryTextColor())
        .maxLines(3)
        .textAlign(TextAlign.Start)
      Toggle({ type: ToggleType.Switch, isOn: value })
        .onChange(onChange)
    }
    .width('100%')
    .constraintSize({ minHeight: 48 })
  }

  @Builder
  textField(label: ResourceStr, value: string, placeholder: ResourceStr,
    onChange: (value: string) => void) {
    Column({ space: 6 }) {
      Text(label)
        .fontSize(13)
        .fontColor(this.secondaryTextColor())
        .textAlign(TextAlign.Start)
      TextInput({ text: value, placeholder: placeholder })
        .height(42)
        .borderRadius(8)
        .backgroundColor(this.inputBackground())
        .fontColor(this.primaryTextColor())
        .onChange(onChange)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  numberField(label: ResourceStr, value: number, onChange: (value: number) => void) {
    Column({ space: 6 }) {
      Text(label)
        .fontSize(13)
        .fontColor(this.secondaryTextColor())
        .textAlign(TextAlign.Start)
      TextInput({ text: value.toString(), placeholder: '0' })
        .height(42)
        .type(InputType.Number)
        .borderRadius(8)
        .backgroundColor(this.inputBackground())
        .fontColor(this.primaryTextColor())
        .onChange((text: string) => onChange(this.parseNumber(text, value)))
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  arrayField(label: ResourceStr, values: string[], onChange: (values: string[]) => void) {
    Column({ space: 6 }) {
      Text(label)
        .fontSize(13)
        .fontColor(this.secondaryTextColor())
        .textAlign(TextAlign.Start)
      TextArea({
        text: values.join('\n'),
        placeholder: $r('app.string.management_library_array_placeholder')
      })
        .constraintSize({ minHeight: 72, maxHeight: 128 })
        .borderRadius(8)
        .backgroundColor(this.inputBackground())
        .fontColor(this.primaryTextColor())
        .onChange((text: string) => onChange(this.parseLines(text)))
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  localeRows() {
    Row({ space: 12 }) {
      Text($r('app.string.management_library_metadata_language'))
        .layoutWeight(1)
        .fontSize(14)
        .fontColor(this.primaryTextColor())
        .textAlign(TextAlign.Start)
      ManagementNativeSelect({
        options: this.languageLabels(),
        selectedIndex: this.languageIndex(),
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        selectorWidth: this.isNativeTheme() ? 'auto' : 184,
        valueTextAlign: TextAlign.End,
        onSelect: (index: number) => {
          const next = this.cloneOptions()
          next.PreferredMetadataLanguage = index <= 0 ? null : this.cultures[index - 1].Name || null
          this.onOptionsChange(next)
        }
      })
    }
    .width('100%')
    .constraintSize({ minHeight: 48 })
    Row({ space: 12 }) {
      Text($r('app.string.management_library_country'))
        .layoutWeight(1)
        .fontSize(14)
        .fontColor(this.primaryTextColor())
        .textAlign(TextAlign.Start)
      ManagementNativeSelect({
        options: this.countryLabels(),
        selectedIndex: this.countryIndex(),
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        selectorWidth: this.isNativeTheme() ? 'auto' : 184,
        valueTextAlign: TextAlign.End,
        onSelect: (index: number) => {
          const next = this.cloneOptions()
          next.MetadataCountryCode = index <= 0 ? null :
            this.countries[index - 1].TwoLetterISORegionName || null
          this.onOptionsChange(next)
        }
      })
    }
    .width('100%')
    .constraintSize({ minHeight: 48 })
  }

  @Builder
  imageOptionRows() {
    ForEach((this.options.TypeOptions || []) as ManagementLibraryTypeOptions[],
      (type: ManagementLibraryTypeOptions, typeIndex: number) => {
        if ((type.ImageOptions || []).length > 0) {
          Column({ space: 10 }) {
            Text(`${this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(
              'management_library_image_options') || ''} · ${type.Type || ''}`)
              .fontSize(14)
              .fontWeight(FontWeight.Medium)
              .fontColor(this.primaryTextColor())
              .textAlign(TextAlign.Start)
            ForEach((type.ImageOptions || []) as ImageOption[],
              (image: ImageOption, imageIndex: number) => {
                Column({ space: 8 }) {
                  Text(image.Type || '-')
                    .fontSize(13)
                    .fontColor(this.secondaryTextColor())
                    .textAlign(TextAlign.Start)
                  Row({ space: 10 }) {
                    Column({ space: 4 }) {
                      Text($r('app.string.management_library_image_limit'))
                        .fontSize(12)
                        .fontColor(this.secondaryTextColor())
                      TextInput({ text: (image.Limit || 0).toString(), placeholder: '0' })
                        .height(40)
                        .type(InputType.Number)
                        .borderRadius(8)
                        .backgroundColor(this.inputBackground())
                        .onChange((text: string) => this.updateImageOption(typeIndex, imageIndex,
                          this.parseNumber(text, image.Limit || 0), image.MinWidth || 0))
                    }
                    .layoutWeight(1)
                    .alignItems(HorizontalAlign.Start)
                    Column({ space: 4 }) {
                      Text($r('app.string.management_library_image_min_width'))
                        .fontSize(12)
                        .fontColor(this.secondaryTextColor())
                      TextInput({ text: (image.MinWidth || 0).toString(), placeholder: '0' })
                        .height(40)
                        .type(InputType.Number)
                        .borderRadius(8)
                        .backgroundColor(this.inputBackground())
                        .onChange((text: string) => this.updateImageOption(typeIndex, imageIndex,
                          image.Limit || 0, this.parseNumber(text, image.MinWidth || 0)))
                    }
                    .layoutWeight(1)
                    .alignItems(HorizontalAlign.Start)
                  }
                  .width('100%')
                }
                .width('100%')
              }, (image: ImageOption) => `${type.Type || ''}-${image.Type || ''}`)
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }
      }, (type: ManagementLibraryTypeOptions) => type.Type || '')
  }

  @Builder
  expandedContent() {
    Column({ space: 10 }) {
      this.sectionTitle($r('app.string.management_library_advanced_scan_group'))
      this.booleanRow($r('app.string.management_library_enabled'), this.options.Enabled !== false,
        (value: boolean) => { const next = this.cloneOptions(); next.Enabled = value; this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_archive_media'),
        this.options.EnableArchiveMediaFiles === true,
        (value: boolean) => { const next = this.cloneOptions(); next.EnableArchiveMediaFiles = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_photos'), this.options.EnablePhotos === true,
        (value: boolean) => { const next = this.cloneOptions(); next.EnablePhotos = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_realtime_monitor'),
        this.options.EnableRealtimeMonitor === true,
        (value: boolean) => { const next = this.cloneOptions(); next.EnableRealtimeMonitor = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_lufs_scan'), this.options.EnableLUFSScan === true,
        (value: boolean) => { const next = this.cloneOptions(); next.EnableLUFSScan = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_chapter_images'),
        this.options.EnableChapterImageExtraction === true,
        (value: boolean) => { const next = this.cloneOptions(); next.EnableChapterImageExtraction = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_chapter_images_during_scan'),
        this.options.ExtractChapterImagesDuringLibraryScan === true,
        (value: boolean) => { const next = this.cloneOptions(); next.ExtractChapterImagesDuringLibraryScan = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_trickplay'),
        this.options.EnableTrickplayImageExtraction === true,
        (value: boolean) => { const next = this.cloneOptions(); next.EnableTrickplayImageExtraction = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_trickplay_during_scan'),
        this.options.ExtractTrickplayImagesDuringLibraryScan === true,
        (value: boolean) => { const next = this.cloneOptions(); next.ExtractTrickplayImagesDuringLibraryScan = value;
          this.onOptionsChange(next) })
      this.sectionTitle($r('app.string.management_library_advanced_metadata_group'))
      this.booleanRow($r('app.string.management_library_save_local_metadata'),
        this.options.SaveLocalMetadata === true,
        (value: boolean) => { const next = this.cloneOptions(); next.SaveLocalMetadata = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_internet_providers'),
        this.options.EnableInternetProviders !== false,
        (value: boolean) => { const next = this.cloneOptions(); next.EnableInternetProviders = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_auto_series_grouping'),
        this.options.EnableAutomaticSeriesGrouping === true,
        (value: boolean) => { const next = this.cloneOptions(); next.EnableAutomaticSeriesGrouping = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_embedded_titles'),
        this.options.EnableEmbeddedTitles === true,
        (value: boolean) => { const next = this.cloneOptions(); next.EnableEmbeddedTitles = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_embedded_extra_titles'),
        this.options.EnableEmbeddedExtrasTitles === true,
        (value: boolean) => { const next = this.cloneOptions(); next.EnableEmbeddedExtrasTitles = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_embedded_episode_info'),
        this.options.EnableEmbeddedEpisodeInfos === true,
        (value: boolean) => { const next = this.cloneOptions(); next.EnableEmbeddedEpisodeInfos = value;
          this.onOptionsChange(next) })
      this.numberField($r('app.string.management_library_scan_interval'),
        this.options.AutomaticRefreshIntervalDays || 0,
        (value: number) => { const next = this.cloneOptions(); next.AutomaticRefreshIntervalDays = value;
          this.onOptionsChange(next) })
      this.localeRows()
      this.textField($r('app.string.management_library_season_zero_name'),
        this.options.SeasonZeroDisplayName || '', $r('app.string.management_library_season_zero_placeholder'),
        (value: string) => { const next = this.cloneOptions(); next.SeasonZeroDisplayName = value;
          this.onOptionsChange(next) })
      this.sectionTitle($r('app.string.management_library_advanced_subtitle_group'))
      Row({ space: 12 }) {
        Text($r('app.string.management_library_embedded_subtitles'))
          .layoutWeight(1)
          .fontSize(14)
          .fontColor(this.primaryTextColor())
          .textAlign(TextAlign.Start)
        ManagementNativeSelect({
          options: this.embeddedLabels(),
          selectedIndex: this.embeddedIndex(),
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          selectorWidth: this.isNativeTheme() ? 'auto' : 184,
          valueTextAlign: TextAlign.End,
          onSelect: (index: number) => {
            const next = this.cloneOptions()
            next.AllowEmbeddedSubtitles = this.embeddedOptions()[index]
            this.onOptionsChange(next)
          }
        })
      }
      .width('100%')
      .constraintSize({ minHeight: 48 })
      this.booleanRow($r('app.string.management_library_skip_subtitles_embedded'),
        this.options.SkipSubtitlesIfEmbeddedSubtitlesPresent === true,
        (value: boolean) => { const next = this.cloneOptions(); next.SkipSubtitlesIfEmbeddedSubtitlesPresent = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_skip_subtitles_audio_match'),
        this.options.SkipSubtitlesIfAudioTrackMatches === true,
        (value: boolean) => { const next = this.cloneOptions(); next.SkipSubtitlesIfAudioTrackMatches = value;
          this.onOptionsChange(next) })
      this.arrayField($r('app.string.management_library_subtitle_languages'),
        this.options.SubtitleDownloadLanguages || [],
        (values: string[]) => { const next = this.cloneOptions(); next.SubtitleDownloadLanguages = values;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_perfect_subtitle_match'),
        this.options.RequirePerfectSubtitleMatch === true,
        (value: boolean) => { const next = this.cloneOptions(); next.RequirePerfectSubtitleMatch = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_save_subtitles'),
        this.options.SaveSubtitlesWithMedia === true,
        (value: boolean) => { const next = this.cloneOptions(); next.SaveSubtitlesWithMedia = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_save_lyrics'), this.options.SaveLyricsWithMedia === true,
        (value: boolean) => { const next = this.cloneOptions(); next.SaveLyricsWithMedia = value;
          this.onOptionsChange(next) })
      this.sectionTitle($r('app.string.management_library_advanced_image_group'))
      this.booleanRow($r('app.string.management_library_save_trickplay'),
        this.options.SaveTrickplayWithMedia === true,
        (value: boolean) => { const next = this.cloneOptions(); next.SaveTrickplayWithMedia = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_prefer_nonstandard_artist_tag'),
        this.options.PreferNonstandardArtistsTag === true,
        (value: boolean) => { const next = this.cloneOptions(); next.PreferNonstandardArtistsTag = value;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_use_custom_delimiters'),
        this.options.UseCustomTagDelimiters === true,
        (value: boolean) => { const next = this.cloneOptions(); next.UseCustomTagDelimiters = value;
          this.onOptionsChange(next) })
      this.arrayField($r('app.string.management_library_custom_delimiters'),
        this.options.CustomTagDelimiters || [],
        (values: string[]) => { const next = this.cloneOptions(); next.CustomTagDelimiters = values;
          this.onOptionsChange(next) })
      this.arrayField($r('app.string.management_library_delimiter_whitelist'),
        this.options.DelimiterWhitelist || [],
        (values: string[]) => { const next = this.cloneOptions(); next.DelimiterWhitelist = values;
          this.onOptionsChange(next) })
      this.booleanRow($r('app.string.management_library_auto_add_collection'),
        this.options.AutomaticallyAddToCollection === true,
        (value: boolean) => { const next = this.cloneOptions(); next.AutomaticallyAddToCollection = value;
          this.onOptionsChange(next) })
      this.imageOptionRows()
      if (!this.isNativeTheme()) {
        Text($r('app.string.management_library_unsupported_option'))
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .maxLines(4)
          .textAlign(TextAlign.Start)
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  build() {
    Column() {
      Column({ space: 10 }) {
        Row({ space: 12 }) {
          if (this.isNativeTheme()) {
            Text($r('app.string.management_library_advanced'))
              .layoutWeight(1)
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontColor(this.primaryTextColor())
              .textAlign(TextAlign.Start)
          } else {
            Column({ space: 3 }) {
              Text($r('app.string.management_library_advanced'))
                .fontSize(18)
                .fontWeight(FontWeight.Medium)
                .fontColor(this.primaryTextColor())
              Text($r('app.string.management_library_advanced_hint'))
                .fontSize(12)
                .fontColor(this.secondaryTextColor())
                .maxLines(3)
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)
          }
          SymbolGlyph(this.expanded ? $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
            .fontSize(18)
            .fontColor([this.secondaryTextColor()])
        }
        .constraintSize({ minHeight: 48 })
        .onClick(() => this.onExpandedChange(!this.expanded))
        if (this.expanded) this.expandedContent()
      }
      .width(this.isNativeTheme() ? 'calc(100% - 24vp)' : 'calc(100% - 32vp)')
      .padding({
        left: this.isNativeTheme() ? 12 : 16,
        right: this.isNativeTheme() ? 12 : 16,
        top: this.isNativeTheme() ? 10 : 12,
        bottom: this.isNativeTheme() ? 10 : 12
      })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .backgroundColor(this.groupBackground())
    .clip(true)
  }
}
