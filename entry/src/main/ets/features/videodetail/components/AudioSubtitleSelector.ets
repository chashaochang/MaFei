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
export struct AudioSubtitleSelector {
  @Require @Param subtitleOptions: MediaOptionViewData[]
  @Require @Param audioOptions: MediaOptionViewData[]
  @Require @Param selectedSubtitleIndex: number
  @Require @Param selectedAudioIndex: number
  @Require @Param selectedSubtitleLabel: string
  @Require @Param selectedAudioLabel: string
  @Param triggerTextColor: ResourceColor = $r('app.color.text_primary')
  @Local isShowingSubtitleOptions: boolean = false
  @Local isShowingAudioOptions: boolean = false
  @Local displayedSubtitleIndex: number = 0
  @Local displayedAudioIndex: number = 0
  @Local displayedSubtitleLabel: string = '无字幕'
  @Local displayedAudioLabel: string = '无音频'
  @Require @Param onSelectSubtitle: (index: number) => void
  @Require @Param onSelectAudio: (index: number) => void
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

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

  private showSubtitleOptions(): void {
    if (this.subtitleOptions.length > 1) this.isShowingSubtitleOptions = true
  }

  private showAudioOptions(): void {
    if (this.audioOptions.length > 1) this.isShowingAudioOptions = true
  }

  private selectSubtitle(index: number): void {
    if (index < 0 || index >= this.subtitleOptions.length) return
    this.displayedSubtitleIndex = index
    this.displayedSubtitleLabel = this.subtitleOptions[index].compactLabel || this.subtitleOptions[index].title
    this.onSelectSubtitle(index)
    this.isShowingSubtitleOptions = false
  }

  private selectAudio(index: number): void {
    if (index < 0 || index >= this.audioOptions.length) return
    this.displayedAudioIndex = index
    this.displayedAudioLabel = this.audioOptions[index].compactLabel || this.audioOptions[index].title
    this.onSelectAudio(index)
    this.isShowingAudioOptions = false
  }

  @Builder
  private subtitleTriggerContent() {
    Row({ space: 2 }) {
      Text(this.displayedSubtitleLabel)
        .fontSize(12)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.triggerTextColor)
        .textAlign(TextAlign.Center)
        .constraintSize({ maxWidth: this.subtitleOptions.length > 1 ? 'calc(100% - 18vp)' : '100%' })
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      if (this.subtitleOptions.length > 1) {
        SymbolGlyph(this.isShowingSubtitleOptions ?
          $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
          .fontSize(12)
          .fontWeight(FontWeight.Bold)
          .fontColor([this.triggerTextColor])
          .width(14)
      }
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private audioTriggerContent() {
    Row({ space: 2 }) {
      Text(this.displayedAudioLabel)
        .fontSize(12)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.triggerTextColor)
        .textAlign(TextAlign.Center)
        .constraintSize({ maxWidth: this.audioOptions.length > 1 ? 'calc(100% - 18vp)' : '100%' })
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      if (this.audioOptions.length > 1) {
        SymbolGlyph(this.isShowingAudioOptions ?
          $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
          .fontSize(12)
          .fontWeight(FontWeight.Bold)
          .fontColor([this.triggerTextColor])
          .width(14)
      }
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private subtitleTrigger() {
    if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.subtitleTriggerContent()
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showSubtitleOptions())
        .bindSheet($$this.isShowingSubtitleOptions, this.buildOptions(this.subtitleOptions,
          this.displayedSubtitleIndex, true, true, 'subtitle'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '字幕' },
          backgroundColor: Color.Transparent
        })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.subtitleTriggerContent()
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showSubtitleOptions())
        .bindSheet($$this.isShowingSubtitleOptions, this.buildOptions(this.subtitleOptions,
          this.displayedSubtitleIndex, true, false, 'subtitle'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '字幕' },
          backgroundColor: Color.Transparent,
          systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
        })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.subtitleTriggerContent()
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showSubtitleOptions())
        .bindSheet($$this.isShowingSubtitleOptions, this.buildOptions(this.subtitleOptions,
          this.displayedSubtitleIndex, false, false, 'subtitle'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '字幕' },
          systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
        })
    } else {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.subtitleTriggerContent()
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showSubtitleOptions())
        .bindSheet($$this.isShowingSubtitleOptions, this.buildOptions(this.subtitleOptions,
          this.displayedSubtitleIndex, false, false, 'subtitle'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '字幕' }
        })
    }
  }

  @Builder
  private audioTrigger() {
    if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.audioTriggerContent()
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showAudioOptions())
        .bindSheet($$this.isShowingAudioOptions, this.buildOptions(this.audioOptions,
          this.displayedAudioIndex, true, true, 'audio'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '音频' },
          backgroundColor: Color.Transparent
        })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.audioTriggerContent()
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showAudioOptions())
        .bindSheet($$this.isShowingAudioOptions, this.buildOptions(this.audioOptions,
          this.displayedAudioIndex, true, false, 'audio'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '音频' },
          backgroundColor: Color.Transparent,
          systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
        })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.audioTriggerContent()
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showAudioOptions())
        .bindSheet($$this.isShowingAudioOptions, this.buildOptions(this.audioOptions,
          this.displayedAudioIndex, false, false, 'audio'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '音频' },
          systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
        })
    } else {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.audioTriggerContent()
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showAudioOptions())
        .bindSheet($$this.isShowingAudioOptions, this.buildOptions(this.audioOptions,
          this.displayedAudioIndex, false, false, 'audio'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '音频' }
        })
    }
  }

  build() {
    Row() {
      this.subtitleTrigger()
      this.audioTrigger()
    }
    .width('100%')
    .height(32)
  }

  @Builder
  private buildOptions(options: MediaOptionViewData[], selectedIndex: number,
    native: boolean, blurFallback: boolean, group: string) {
    Scroll() {
      Column({ space: 12 }) {
        ForEach(options, (option: MediaOptionViewData, index: number) => {
          SheetListItem({
            index: index,
            item: option,
            group: group,
            isSelected: selectedIndex === index,
            native: native,
            onSelect: (selectedIndex: number) => {
              if (group === 'subtitle') this.selectSubtitle(selectedIndex)
              else this.selectAudio(selectedIndex)
            }
          })
        }, (option: MediaOptionViewData): string => option.id)
      }
    }
    .scrollBar(BarState.Off)
    .scrollable(ScrollDirection.Vertical)
    .padding({ left: 16, right: 16, bottom: 10 + this.appUIState.safeBottom })
    .width('100%')
    .constraintSize({ maxHeight: '100%' })
    .align(Alignment.Top)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, blurFallback, false))
  }
}

@ComponentV2
struct SheetListItem {
  @Require @Param index: number
  @Require @Param item: MediaOptionViewData
  @Require @Param group: string
  @Require @Param onSelect: (index: number) => void
  @Param isSelected: boolean = false
  @Param native: boolean = false

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
    .backgroundColor(this.native ? Color.Transparent : $r('app.color.bg_2'))
    .borderRadius(8)
    .padding({ left: 16, right: 16 })
    .hitTestBehavior(HitTestMode.Block)
    .onClick(() => this.onSelect(this.index))
  }
}
