import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../../entity/AppUIState'
import { MediaOptionViewData } from '../../../media/presentation/VideoDetailPresentationModels'
import {
  AppThemeMaterialRole,
  AppThemeSurfaceResolver
} from '../../../theme/AppThemeSurfaceResolver'
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../../theme/AppThemeOverlayPolicy'

@ComponentV2
struct MediaOptionTriggerContent {
  @Require @Param label: string
  @Require @Param hasOptions: boolean
  @Require @Param isShowingOptions: boolean
  @Require @Param textColor: ResourceColor

  build() {
    Row({ space: 2 }) {
      Text(this.label)
        .fontSize(12)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.textColor)
        .textAlign(TextAlign.Center)
        .constraintSize({ maxWidth: this.hasOptions ? 'calc(100% - 18vp)' : '100%' })
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      if (this.hasOptions) {
        SymbolGlyph(this.isShowingOptions ?
          $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
          .fontSize(12)
          .fontWeight(FontWeight.Bold)
          .fontColor([this.textColor])
          .width(14)
      }
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
struct MediaOptionSheetItem {
  @Require @Param index: number
  @Require @Param item: MediaOptionViewData
  @Require @Param group: string
  @Param isSelected: boolean = false
  @Param nativeSurface: boolean = false
  @Event onSelect: (index: number) => void = (_index: number) => {}

  build() {
    Row() {
      Radio({ value: this.item.id, group: this.group })
        .checked(this.isSelected)
        .width(16)
      Column({ space: 4 }) {
        Text(this.item.title)
          .fontSize(16)
          .fontColor($r('app.color.text_primary'))
          .padding({ left: 8 })
        if (this.item.summary.length > 0) {
          Text(this.item.summary)
            .fontSize(12)
            .fontColor($r('app.color.text_1'))
            .padding({ left: 8 })
        }
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .height(66)
    .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.bg_2'))
    .borderRadius(8)
    .padding({ left: 16, right: 16 })
    .hitTestBehavior(HitTestMode.Block)
    .onClick(() => this.onSelect(this.index))
  }
}

@ComponentV2
struct MediaOptionSheetContent {
  @Require @Param options: MediaOptionViewData[]
  @Require @Param selectedIndex: number
  @Require @Param group: string
  @Require @Param safeBottom: number
  @Param nativeSurface: boolean = false
  @Param blurFallback: boolean = false
  @Event onSelect: (index: number) => void = (_index: number) => {}

  build() {
    Scroll() {
      Column({ space: 12 }) {
        ForEach(this.options, (option: MediaOptionViewData, index: number) => {
          MediaOptionSheetItem({
            index,
            item: option,
            group: this.group,
            isSelected: this.selectedIndex === index,
            nativeSurface: this.nativeSurface,
            onSelect: (selectedIndex: number) => this.onSelect(selectedIndex)
          })
        }, (option: MediaOptionViewData): string => option.id)
      }
    }
    .scrollBar(BarState.Off)
    .scrollable(ScrollDirection.Vertical)
    .padding({ left: 16, right: 16, bottom: 10 + this.safeBottom })
    .width('100%')
    .constraintSize({ maxHeight: '100%' })
    .align(Alignment.Top)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, this.blurFallback, false))
  }
}

@ComponentV2
struct MediaOptionSelectorButton {
  @Require @Param options: MediaOptionViewData[]
  @Require @Param selectedIndex: number
  @Require @Param selectedLabel: string
  @Require @Param group: string
  @Require @Param sheetTitle: ResourceStr
  @Require @Param triggerTextColor: ResourceColor
  @Event onSelect: (index: number) => void = (_index: number) => {}
  @Local isShowingOptions: boolean = false
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private sheetMaterialDecision(): OverlayMaterialDecision {
    return AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.AppFloating,
      this.appUIState.themeStyle,
      this.appUIState.systemMaterialAvailable,
      this.appUIState.nativeThemeAvailable
    )
  }

  private showOptions(): void {
    if (this.options.length > 1) {
      this.isShowingOptions = true
    }
  }

  private select(index: number): void {
    if (index < 0 || index >= this.options.length) {
      return
    }
    this.onSelect(index)
    this.isShowingOptions = false
  }

  // CustomBuilderAdapter: media-option-sheet-content
  @Builder
  private optionsSheetContent(nativeSurface: boolean, blurFallback: boolean) {
    MediaOptionSheetContent({
      options: this.options,
      selectedIndex: this.selectedIndex,
      group: this.group,
      safeBottom: this.appUIState.safeBottom,
      nativeSurface,
      blurFallback,
      onSelect: (selectedIndex: number) => this.select(selectedIndex)
    })
  }

  build() {
    if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        MediaOptionTriggerContent({
          label: this.selectedLabel,
          hasOptions: this.options.length > 1,
          isShowingOptions: this.isShowingOptions,
          textColor: this.triggerTextColor
        })
      }
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl,
        this.useNativeSurface(),
        this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showOptions())
      .bindSheet($$this.isShowingOptions, this.optionsSheetContent(true, true), {
        preferType: SheetType.BOTTOM,
        height: SheetSize.FIT_CONTENT,
        title: { title: this.sheetTitle },
        backgroundColor: Color.Transparent
      })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        MediaOptionTriggerContent({
          label: this.selectedLabel,
          hasOptions: this.options.length > 1,
          isShowingOptions: this.isShowingOptions,
          textColor: this.triggerTextColor
        })
      }
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl,
        this.useNativeSurface(),
        this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showOptions())
      .bindSheet($$this.isShowingOptions, this.optionsSheetContent(true, false), {
        preferType: SheetType.BOTTOM,
        height: SheetSize.FIT_CONTENT,
        title: { title: this.sheetTitle },
        backgroundColor: Color.Transparent,
        systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
      })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        MediaOptionTriggerContent({
          label: this.selectedLabel,
          hasOptions: this.options.length > 1,
          isShowingOptions: this.isShowingOptions,
          textColor: this.triggerTextColor
        })
      }
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl,
        this.useNativeSurface(),
        this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showOptions())
      .bindSheet($$this.isShowingOptions, this.optionsSheetContent(false, false), {
        preferType: SheetType.BOTTOM,
        height: SheetSize.FIT_CONTENT,
        title: { title: this.sheetTitle },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      })
    } else {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        MediaOptionTriggerContent({
          label: this.selectedLabel,
          hasOptions: this.options.length > 1,
          isShowingOptions: this.isShowingOptions,
          textColor: this.triggerTextColor
        })
      }
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl,
        this.useNativeSurface(),
        this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showOptions())
      .bindSheet($$this.isShowingOptions, this.optionsSheetContent(false, false), {
        preferType: SheetType.BOTTOM,
        height: SheetSize.FIT_CONTENT,
        title: { title: this.sheetTitle }
      })
    }
  }
}

@ComponentV2
export struct AudioSubtitleSelector {
  @Require @Param subtitleOptions: MediaOptionViewData[]
  @Require @Param audioOptions: MediaOptionViewData[]
  @Require @Param selectedSubtitleIndex: number
  @Require @Param selectedAudioIndex: number
  @Require @Param selectedSubtitleLabel: string
  @Require @Param selectedAudioLabel: string
  @Param triggerTextColor: ResourceColor = $r('app.color.text_primary')
  @Local displayedSubtitleIndex: number = 0
  @Local displayedAudioIndex: number = 0
  @Local displayedSubtitleLabel: string = '无字幕'
  @Local displayedAudioLabel: string = '无音频'
  @Event onSelectSubtitle: (index: number) => void = (_index: number) => {}
  @Event onSelectAudio: (index: number) => void = (_index: number) => {}

  aboutToAppear(): void {
    this.syncSubtitleSelection()
    this.syncAudioSelection()
  }

  @Monitor('selectedSubtitleIndex')
  onSelectedSubtitleIndexChanged(): void {
    this.syncSubtitleSelection()
  }

  @Monitor('selectedAudioIndex')
  onSelectedAudioIndexChanged(): void {
    this.syncAudioSelection()
  }

  @Monitor('selectedSubtitleLabel')
  onSelectedSubtitleLabelChanged(): void {
    this.displayedSubtitleLabel = this.selectedSubtitleLabel
  }

  @Monitor('selectedAudioLabel')
  onSelectedAudioLabelChanged(): void {
    this.displayedAudioLabel = this.selectedAudioLabel
  }

  private syncSubtitleSelection(): void {
    this.displayedSubtitleIndex = this.selectedSubtitleIndex
    this.displayedSubtitleLabel = this.selectedSubtitleLabel
  }

  private syncAudioSelection(): void {
    this.displayedAudioIndex = this.selectedAudioIndex
    this.displayedAudioLabel = this.selectedAudioLabel
  }

  private selectSubtitle(index: number): void {
    if (index < 0 || index >= this.subtitleOptions.length) {
      return
    }
    this.displayedSubtitleIndex = index
    this.displayedSubtitleLabel = this.subtitleOptions[index].compactLabel || this.subtitleOptions[index].title
    this.onSelectSubtitle(index)
  }

  private selectAudio(index: number): void {
    if (index < 0 || index >= this.audioOptions.length) {
      return
    }
    this.displayedAudioIndex = index
    this.displayedAudioLabel = this.audioOptions[index].compactLabel || this.audioOptions[index].title
    this.onSelectAudio(index)
  }

  build() {
    Row() {
      MediaOptionSelectorButton({
        options: this.subtitleOptions,
        selectedIndex: this.displayedSubtitleIndex,
        selectedLabel: this.displayedSubtitleLabel,
        group: 'subtitle',
        sheetTitle: '字幕',
        triggerTextColor: this.triggerTextColor,
        onSelect: (index: number) => this.selectSubtitle(index)
      })
      MediaOptionSelectorButton({
        options: this.audioOptions,
        selectedIndex: this.displayedAudioIndex,
        selectedLabel: this.displayedAudioLabel,
        group: 'audio',
        sheetTitle: '音频',
        triggerTextColor: this.triggerTextColor,
        onSelect: (index: number) => this.selectAudio(index)
      })
    }
    .width('100%')
    .height(32)
  }
}
