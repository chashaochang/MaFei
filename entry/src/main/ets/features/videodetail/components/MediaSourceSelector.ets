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
      .margin({ left: 16 })
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
      .margin({ right: 16 })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .height(68)
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

  private compactResolutionLabel(stream: MediaStreamInfo): string {
    const width = stream.width || 0
    const height = stream.height || 0
    if (width >= 3800 || height >= 2000) {
      return '4K'
    }
    if (width >= 2500 || height >= 1400) {
      return '1440P'
    }
    if (width >= 1900 || height >= 1000) {
      return '1080P'
    }
    if (width >= 1200 || height >= 700) {
      return '720P'
    }

    const title = stream.displayTitle.trim().toUpperCase()
    if (title.includes('4K') || title.includes('2160') || title.includes('UHD')) {
      return '4K'
    }
    if (title.includes('1440')) {
      return '1440P'
    }
    if (title.includes('1080') || title.includes('FHD')) {
      return '1080P'
    }
    if (title.includes('720')) {
      return '720P'
    }
    return height > 0 ? `${height}P` : ''
  }

  private compactRangeLabel(stream: MediaStreamInfo): string {
    const range = `${stream.videoRangeType || ''} ${stream.displayTitle || ''} ` +
      `${stream.colorTransfer || ''}`
    const normalized = range.trim().toUpperCase()
    if (normalized.includes('DOVI') || normalized.includes('DOLBY VISION') ||
      normalized === 'DV' || normalized.startsWith('DV ') || normalized.endsWith(' DV')) {
      return '杜比'
    }
    if (normalized.includes('HDR') || normalized.includes('HLG') || normalized.includes('VIVID') ||
      normalized.includes('PQ') || normalized.includes('2084')) {
      return 'HDR'
    }
    return 'SDR'
  }

  private selectedSourceLabel(): string {
    const source = this.selectedSource()
    if (!source) {
      return '视频源'
    }
    const video = source.streams.find((stream: MediaStreamInfo): boolean =>
      stream.kind === MediaStreamKind.Video)
    if (!video) {
      const name = source.name.trim()
      return name.length > 0 ? name : '视频源'
    }
    const resolution = this.compactResolutionLabel(video)
    const range = this.compactRangeLabel(video)
    return resolution.length > 0 ? `${resolution}${range}` : range
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

  private sourceSummary(source: MediaFileInfo): string {
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
    const size = this.fileSize(source.sizeBytes)
    if (size.length > 0) parts.push(size)
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

  private optionsMaxHeight(): number {
    const deviceHeight = this.appUIState.deviceHeight > 0 ? this.appUIState.deviceHeight : 800
    return Math.max(180, Math.floor(deviceHeight * 0.7) - 72)
  }

  @Builder
  private optionsContent(blurFallback: boolean) {
    Scroll() {
      Column({ space: 4 }) {
        ForEach(this.sources, (source: MediaFileInfo, index: number) => {
          MediaSourceOptionItem({
            index: index,
            selected: index === this.selectedIndex,
            title: this.sourceName(source, index),
            summary: this.sourceSummary(source),
            onSelect: (selectedIndex: number) => this.select(selectedIndex)
          })
        }, (source: MediaFileInfo): string => source.id)
      }
      .width('calc(100% - 32vp)')
      .padding({ bottom: 10 + this.appUIState.safeBottom })
      .alignSelf(ItemAlign.Center)
    }
    .width('100%')
    .constraintSize({ maxHeight: this.optionsMaxHeight() })
    .scrollable(ScrollDirection.Vertical)
    .scrollBar(BarState.Off)
    .edgeEffect(EdgeEffect.None)
    .nestedScroll({
      scrollForward: NestedScrollMode.PARENT_FIRST,
      scrollBackward: NestedScrollMode.SELF_FIRST
    })
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating,
      blurFallback,
      false
    ))
  }

  @Builder
  private triggerContent() {
    Row({ space: 2 }) {
      Text(this.selectedSourceLabel())
        .fontSize(12)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor())
        .textAlign(TextAlign.Center)
        .constraintSize({ maxWidth: 'calc(100% - 18vp)' })
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      SymbolGlyph(this.isShowingOptions ? $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
        .fontSize(12)
        .fontWeight(FontWeight.Bold)
        .fontColor([this.primaryTextColor()])
        .width(14)
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private trigger() {
    if (this.selectedSource()) {
      if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
        Button({ type: ButtonType.Normal, stateEffect: false }) {
          this.triggerContent()
        }
        .width('calc(100% - 8vp)')
        .margin({ left: 4, right: 4 })
        .height(32)
        .padding(0)
        .borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.isNativeTheme() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl,
          this.useNativeSurface(),
          this.appUIState.systemMaterialAvailable
        ))
        .onClick(() => this.openOptions())
        .bindSheet($$this.isShowingOptions, this.optionsContent(false), {
          height: SheetSize.FIT_CONTENT,
          preferType: SheetType.BOTTOM,
          showClose: true,
          title: { title: $r('app.string.video_detail_choose_playback_version') },
          backgroundColor: Color.Transparent,
          systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
        })
      } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
        Button({ type: ButtonType.Normal, stateEffect: false }) {
          this.triggerContent()
        }
        .width('calc(100% - 8vp)')
        .margin({ left: 4, right: 4 })
        .height(32)
        .padding(0)
        .borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.isNativeTheme() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl,
          this.useNativeSurface(),
          this.appUIState.systemMaterialAvailable
        ))
        .onClick(() => this.openOptions())
        .bindSheet($$this.isShowingOptions, this.optionsContent(true), {
          height: SheetSize.FIT_CONTENT,
          preferType: SheetType.BOTTOM,
          showClose: true,
          title: { title: $r('app.string.video_detail_choose_playback_version') },
          backgroundColor: Color.Transparent
        })
      } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
        Button({ type: ButtonType.Normal, stateEffect: false }) {
          this.triggerContent()
        }
        .width('calc(100% - 8vp)')
        .margin({ left: 4, right: 4 })
        .height(32)
        .padding(0)
        .borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.isNativeTheme() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl,
          this.useNativeSurface(),
          this.appUIState.systemMaterialAvailable
        ))
        .onClick(() => this.openOptions())
        .bindSheet($$this.isShowingOptions, this.optionsContent(false), {
          height: SheetSize.FIT_CONTENT,
          preferType: SheetType.BOTTOM,
          showClose: true,
          title: { title: $r('app.string.video_detail_choose_playback_version') },
          backgroundColor: $r('app.color.bg_1'),
          systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
        })
      } else {
        Button({ type: ButtonType.Normal, stateEffect: false }) {
          this.triggerContent()
        }
        .width('calc(100% - 8vp)')
        .margin({ left: 4, right: 4 })
        .height(32)
        .padding(0)
        .borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.isNativeTheme() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl,
          this.useNativeSurface(),
          this.appUIState.systemMaterialAvailable
        ))
        .onClick(() => this.openOptions())
        .bindSheet($$this.isShowingOptions, this.optionsContent(false), {
          height: SheetSize.FIT_CONTENT,
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
