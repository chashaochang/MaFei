import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../../entity/AppUIState'
import { PlaybackSourceViewData } from '../../../media/presentation/VideoDetailPresentationModels'
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
  @Event onSelect: (index: number) => void = (_index: number) => {}
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
struct MediaSourceOptionsContent {
  @Require @Param sources: PlaybackSourceViewData[]
  @Require @Param selectedIndex: number
  @Require @Param safeBottom: number
  @Require @Param contentMaxHeight: number
  @Param blurFallback: boolean = false
  @Event onSelect: (index: number) => void = (_index: number) => {}

  build() {
    Scroll() {
      Column({ space: 4 }) {
        ForEach(this.sources, (source: PlaybackSourceViewData, index: number) => {
          MediaSourceOptionItem({
            index: index,
            selected: index === this.selectedIndex,
            title: source.title,
            summary: source.summary,
            onSelect: (selectedIndex: number) => this.onSelect(selectedIndex)
          })
        }, (source: PlaybackSourceViewData): string => source.id)
      }
      .width('calc(100% - 32vp)')
      .padding({ bottom: 10 + this.safeBottom })
      .alignSelf(ItemAlign.Center)
    }
    .width('100%')
    .constraintSize({ maxHeight: this.contentMaxHeight })
    .scrollable(ScrollDirection.Vertical)
    .scrollBar(BarState.Off)
    .edgeEffect(EdgeEffect.None)
    .nestedScroll({
      scrollForward: NestedScrollMode.PARENT_FIRST,
      scrollBackward: NestedScrollMode.SELF_FIRST
    })
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating,
      this.blurFallback,
      false
    ))
  }
}

@ComponentV2
struct MediaSourceTriggerContent {
  @Require @Param label: string
  @Require @Param isShowingOptions: boolean
  @Require @Param primaryColor: ResourceColor

  build() {
    Row({ space: 2 }) {
      Text(this.label)
        .fontSize(12)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryColor)
        .textAlign(TextAlign.Center)
        .constraintSize({ maxWidth: 'calc(100% - 18vp)' })
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      SymbolGlyph(this.isShowingOptions ? $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
        .fontSize(12)
        .fontWeight(FontWeight.Bold)
        .fontColor([this.primaryColor])
        .width(14)
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
export struct MediaSourceSelector {
  @Require @Param sources: PlaybackSourceViewData[]
  @Require @Param selectedIndex: number
  @Event onSelect: (index: number) => void = (_index: number) => {}
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

  private selectedSource(): PlaybackSourceViewData | undefined {
    if (this.selectedIndex < 0 || this.selectedIndex >= this.sources.length) {
      return undefined
    }
    return this.sources[this.selectedIndex]
  }

  private selectedSourceLabel(): string {
    const source = this.selectedSource()
    return source?.compactLabel || '视频源'
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

  // CustomBuilderAdapter: media-source-sheet-content
  @Builder
  private optionsSheetContent(blurFallback: boolean) {
    MediaSourceOptionsContent({
      sources: this.sources,
      selectedIndex: this.selectedIndex,
      safeBottom: this.appUIState.safeBottom,
      contentMaxHeight: this.optionsMaxHeight(),
      blurFallback,
      onSelect: (selectedIndex: number) => this.select(selectedIndex)
    })
  }

  build() {
    if (this.sources.length > 1 && this.selectedSource()) {
      if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
        Button({ type: ButtonType.Normal, stateEffect: false }) {
          MediaSourceTriggerContent({
            label: this.selectedSourceLabel(),
            isShowingOptions: this.isShowingOptions,
            primaryColor: this.primaryTextColor()
          })
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
        .bindSheet($$this.isShowingOptions, this.optionsSheetContent(false), {
          height: SheetSize.FIT_CONTENT,
          preferType: SheetType.BOTTOM,
          showClose: true,
          title: { title: $r('app.string.video_detail_choose_playback_version') },
          backgroundColor: Color.Transparent,
          systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
        })
      } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
        Button({ type: ButtonType.Normal, stateEffect: false }) {
          MediaSourceTriggerContent({
            label: this.selectedSourceLabel(),
            isShowingOptions: this.isShowingOptions,
            primaryColor: this.primaryTextColor()
          })
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
        .bindSheet($$this.isShowingOptions, this.optionsSheetContent(true), {
          height: SheetSize.FIT_CONTENT,
          preferType: SheetType.BOTTOM,
          showClose: true,
          title: { title: $r('app.string.video_detail_choose_playback_version') },
          backgroundColor: Color.Transparent
        })
      } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
        Button({ type: ButtonType.Normal, stateEffect: false }) {
          MediaSourceTriggerContent({
            label: this.selectedSourceLabel(),
            isShowingOptions: this.isShowingOptions,
            primaryColor: this.primaryTextColor()
          })
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
        .bindSheet($$this.isShowingOptions, this.optionsSheetContent(false), {
          height: SheetSize.FIT_CONTENT,
          preferType: SheetType.BOTTOM,
          showClose: true,
          title: { title: $r('app.string.video_detail_choose_playback_version') },
          backgroundColor: $r('app.color.bg_1'),
          systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
        })
      } else {
        Button({ type: ButtonType.Normal, stateEffect: false }) {
          MediaSourceTriggerContent({
            label: this.selectedSourceLabel(),
            isShowingOptions: this.isShowingOptions,
            primaryColor: this.primaryTextColor()
          })
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
        .bindSheet($$this.isShowingOptions, this.optionsSheetContent(false), {
          height: SheetSize.FIT_CONTENT,
          preferType: SheetType.BOTTOM,
          showClose: true,
          title: { title: $r('app.string.video_detail_choose_playback_version') }
        })
      }
    }
  }
}
