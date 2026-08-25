import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../../entity/AppUIState'
import {
  MediaFileInfo,
  MediaStreamInfo,
  MediaStreamKind
} from '../../../media/catalog/MediaCatalogModels'
import {
  AppThemeMaterialRole,
  AppThemeSurfaceResolver
} from '../../../theme/AppThemeSurfaceResolver'
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../../theme/AppThemeOverlayPolicy'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'

@ComponentV2
struct MediaSourceOptionItem {
  @Require @Param index: number
  @Require @Param selected: boolean
  @Require @Param title: string
  @Require @Param summary: string
  @Require @Param onSelect: (index: number) => void
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.appUIState.themeStyle)
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  build() {
    Row({ space: 12 }) {
      Column() {
        SymbolGlyph($r('sys.symbol.checkmark'))
          .fontSize(18)
          .fontColor([this.primaryTextColor()])
          .opacity(this.selected ? 1 : 0)
      }
      .width(20)
      .height('100%')
      .justifyContent(FlexAlign.Center)

      Column({ space: 4 }) {
        Text(this.title)
          .fontSize(15)
          .fontWeight(this.selected ? FontWeight.Medium : FontWeight.Regular)
          .fontColor(this.primaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.summary)
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .height(68)
    .padding({ left: 16, right: 16 })
    .borderRadius(12)
    .clip(true)
    .backgroundColor(this.isNativeTheme() ? Color.Transparent : $r('app.color.bg_2'))
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.AdaptiveControl,
      this.useNativeSurface(),
      this.appUIState.systemMaterialAvailable
    ))
    .onClick(() => this.onSelect(this.index))
  }
}

@ComponentV2
export struct MediaSourceSelector {
  @Require @Param sources: MediaFileInfo[]
  @Require @Param selectedIndex: number
  @Require @Param onSelect: (index: number) => void
  @Local isShowingOptions: boolean = false
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.appUIState.themeStyle)
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private sheetMaterialDecision(): OverlayMaterialDecision {
    return AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.AppFloating,
      this.appUIState.themeStyle,
      this.appUIState.systemMaterialAvailable,
      this.appUIState.nativeThemeAvailable
    )
  }

  private selectedSource(): MediaFileInfo | undefined {
    if (this.selectedIndex < 0 || this.selectedIndex >= this.sources.length) {
      return undefined
    }
    return this.sources[this.selectedIndex]
  }

  private getString(name: string): string {
    try {
      return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
    } catch (_) {
      return name
    }
  }

  private sourceName(source: MediaFileInfo, index: number): string {
    const name = source.name.trim()
    return name.length > 0 ? name : `${this.getString('video_detail_playback_version')} ${index + 1}`
  }

  private resolutionLabel(stream?: MediaStreamInfo): string {
    if (!stream) {
      return ''
    }
    const displayTitle = stream.displayTitle.trim()
    if (displayTitle.length > 0) {
      return displayTitle
    }
    const width = stream.width || 0
    const height = stream.height || 0
    if (width >= 3800 || height >= 2000) {
      return '4K'
    }
    return width > 0 && height > 0 ? `${width} × ${height}` : ''
  }

  private sourceSummary(source: MediaFileInfo, includeSize: boolean): string {
    const video = source.streams.find((stream: MediaStreamInfo): boolean =>
      stream.kind === MediaStreamKind.Video)
    const parts: string[] = []
    const resolution = this.resolutionLabel(video)
    if (resolution.length > 0) parts.push(resolution)
    const codec = `${video?.codec || ''}`.trim().toUpperCase()
    if (codec.length > 0 && parts.indexOf(codec) < 0) parts.push(codec)
    const range = `${video?.videoRangeType || ''}`.trim()
    if (range.length > 0 && parts.indexOf(range) < 0) parts.push(range)
    const container = source.container.trim().toUpperCase()
    if (container.length > 0 && parts.indexOf(container) < 0) parts.push(container)
    if (includeSize) {
      const size = this.fileSize(source.sizeBytes)
      if (size.length > 0) parts.push(size)
    }
    return parts.length > 0 ? parts.join(' · ') : this.getString('video_detail_playback_version')
  }

  private fileSize(sizeBytes: number): string {
    const bytes = Math.max(0, sizeBytes)
    if (bytes >= 1024 * 1024 * 1024) {
      return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
    }
    if (bytes >= 1024 * 1024) {
      return `${(bytes / 1024 / 1024).toFixed(1)} MB`
    }
    return bytes > 0 ? `${Math.floor(bytes / 1024)} KB` : ''
  }

  private selectedSummary(): string {
    const source = this.selectedSource()
    return source ? this.sourceSummary(source, false) : ''
  }

  private openOptions(): void {
    if (this.sources.length > 1) {
      this.isShowingOptions = true
    }
  }

  private select(index: number): void {
    if (index < 0 || index >= this.sources.length) {
      return
    }
    this.onSelect(index)
    this.isShowingOptions = false
  }

  @Builder
  private optionsContent(nativeSurface: boolean) {
    Scroll() {
      Column({ space: 4 }) {
        ForEach(this.sources, (source: MediaFileInfo, index: number) => {
          MediaSourceOptionItem({
            index: index,
            selected: index === this.selectedIndex,
            title: this.sourceName(source, index),
            summary: this.sourceSummary(source, true),
            onSelect: (selectedIndex: number) => this.select(selectedIndex)
          })
        }, (source: MediaFileInfo): string => source.id)
      }
      .width('100%')
      .padding({ left: 16, right: 16, bottom: 10 + this.appUIState.safeBottom })
    }
    .width('100%')
    .height('100%')
    .scrollable(ScrollDirection.Vertical)
    .scrollBar(BarState.Off)
    .edgeEffect(EdgeEffect.None)
    .nestedScroll({
      scrollForward: NestedScrollMode.PARENT_FIRST,
      scrollBackward: NestedScrollMode.SELF_FIRST
    })
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating,
      nativeSurface,
      false
    ))
  }

  @Builder
  private triggerContent() {
    Row({ space: 8 }) {
      Text($r('app.string.video_detail_playback_version'))
        .fontSize(15)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.primaryTextColor())
      Text(this.selectedSummary())
        .layoutWeight(1)
        .fontSize(13)
        .fontColor(this.secondaryTextColor())
        .textAlign(TextAlign.End)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(16)
        .fontColor([this.secondaryTextColor()])
    }
    .width('100%')
    .height('100%')
    .padding({ left: 16, right: 12 })
  }

  @Builder
  private trigger() {
    if (this.selectedSource()) {
      if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
        Row() {
          this.triggerContent()
        }
        .width('100%')
        .height(52)
        .borderRadius(16)
        .clip(true)
        .backgroundColor(this.isNativeTheme() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl,
          this.useNativeSurface(),
          this.appUIState.systemMaterialAvailable
        ))
        .onClick(() => this.openOptions())
        .bindSheet($$this.isShowingOptions, this.optionsContent(true), {
          height: '70%',
          preferType: SheetType.BOTTOM,
          showClose: true,
          title: { title: $r('app.string.video_detail_choose_playback_version') },
          backgroundColor: Color.Transparent
        })
      } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
        Row() {
          this.triggerContent()
        }
        .width('100%')
        .height(52)
        .borderRadius(16)
        .clip(true)
        .backgroundColor(this.isNativeTheme() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl,
          this.useNativeSurface(),
          this.appUIState.systemMaterialAvailable
        ))
        .onClick(() => this.openOptions())
        .bindSheet($$this.isShowingOptions, this.optionsContent(false), {
          height: '70%',
          preferType: SheetType.BOTTOM,
          showClose: true,
          title: { title: $r('app.string.video_detail_choose_playback_version') },
          backgroundColor: Color.Transparent
        })
      } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
        Row() {
          this.triggerContent()
        }
        .width('100%')
        .height(52)
        .borderRadius(16)
        .clip(true)
        .backgroundColor(this.isNativeTheme() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl,
          this.useNativeSurface(),
          this.appUIState.systemMaterialAvailable
        ))
        .onClick(() => this.openOptions())
        .bindSheet($$this.isShowingOptions, this.optionsContent(false), {
          height: '70%',
          preferType: SheetType.BOTTOM,
          showClose: true,
          title: { title: $r('app.string.video_detail_choose_playback_version') },
          backgroundColor: $r('app.color.bg_1')
        })
      } else {
        Row() {
          this.triggerContent()
        }
        .width('100%')
        .height(52)
        .borderRadius(16)
        .clip(true)
        .backgroundColor(this.isNativeTheme() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl,
          this.useNativeSurface(),
          this.appUIState.systemMaterialAvailable
        ))
        .onClick(() => this.openOptions())
        .bindSheet($$this.isShowingOptions, this.optionsContent(false), {
          height: '70%',
          preferType: SheetType.BOTTOM,
          showClose: true,
          title: { title: $r('app.string.video_detail_choose_playback_version') }
        })
      }
    }
  }

  build() {
    if (this.sources.length > 1) {
      this.trigger()
    }
  }
}
