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
  @Param triggerTextColor: ResourceColor = $r('app.color.text_primary')
  @Local isShowingSubtitleOptions: boolean = false
  @Local isShowingAudioOptions: boolean = false
  @Event onSelectSubtitle: (index: number) => void = (index: number) => {}
  @Event onSelectAudio: (index: number) => void = (index: number) => {}
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

  private subtitleLabel(): string {
    if (this.subtitleOptions.length === 0) return '无字幕'
    const index = Math.min(Math.max(this.selectedSubtitleIndex, 0), this.subtitleOptions.length - 1)
    return this.subtitleOptions[index].title
  }

  private audioLabel(): string {
    if (this.audioOptions.length === 0) return '无音频'
    const index = Math.min(Math.max(this.selectedAudioIndex, 0), this.audioOptions.length - 1)
    return this.audioOptions[index].title
  }

  private showSubtitleOptions(): void {
    if (this.subtitleOptions.length > 1) this.isShowingSubtitleOptions = true
  }

  private showAudioOptions(): void {
    if (this.audioOptions.length > 1) this.isShowingAudioOptions = true
  }

  private selectSubtitle(index: number): void {
    if (index < 0 || index >= this.subtitleOptions.length) return
    this.onSelectSubtitle(index)
    this.isShowingSubtitleOptions = false
  }

  private selectAudio(index: number): void {
    if (index < 0 || index >= this.audioOptions.length) return
    this.onSelectAudio(index)
    this.isShowingAudioOptions = false
  }

  @Builder
  private triggerContent(label: string, hasOptions: boolean, expanded: boolean) {
    Row({ space: 2 }) {
      Text(label)
        .fontSize(12)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.triggerTextColor)
        .textAlign(TextAlign.Center)
        .constraintSize({ maxWidth: hasOptions ? 'calc(100% - 18vp)' : '100%' })
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      if (hasOptions) {
        SymbolGlyph(expanded ? $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
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
        this.triggerContent(this.subtitleLabel(), this.subtitleOptions.length > 1, this.isShowingSubtitleOptions)
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showSubtitleOptions())
        .bindSheet($$this.isShowingSubtitleOptions, this.buildOptions(this.subtitleOptions,
          this.selectedSubtitleIndex, true, true, 'subtitle'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '字幕' },
          backgroundColor: Color.Transparent
        })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.triggerContent(this.subtitleLabel(), this.subtitleOptions.length > 1, this.isShowingSubtitleOptions)
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showSubtitleOptions())
        .bindSheet($$this.isShowingSubtitleOptions, this.buildOptions(this.subtitleOptions,
          this.selectedSubtitleIndex, true, false, 'subtitle'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '字幕' },
          backgroundColor: Color.Transparent,
          systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
        })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.triggerContent(this.subtitleLabel(), this.subtitleOptions.length > 1, this.isShowingSubtitleOptions)
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showSubtitleOptions())
        .bindSheet($$this.isShowingSubtitleOptions, this.buildOptions(this.subtitleOptions,
          this.selectedSubtitleIndex, false, false, 'subtitle'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '字幕' },
          systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
        })
    } else {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.triggerContent(this.subtitleLabel(), this.subtitleOptions.length > 1, this.isShowingSubtitleOptions)
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showSubtitleOptions())
        .bindSheet($$this.isShowingSubtitleOptions, this.buildOptions(this.subtitleOptions,
          this.selectedSubtitleIndex, false, false, 'subtitle'), {
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
        this.triggerContent(this.audioLabel(), this.audioOptions.length > 1, this.isShowingAudioOptions)
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showAudioOptions())
        .bindSheet($$this.isShowingAudioOptions, this.buildOptions(this.audioOptions,
          this.selectedAudioIndex, true, true, 'audio'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '音频' },
          backgroundColor: Color.Transparent
        })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.triggerContent(this.audioLabel(), this.audioOptions.length > 1, this.isShowingAudioOptions)
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showAudioOptions())
        .bindSheet($$this.isShowingAudioOptions, this.buildOptions(this.audioOptions,
          this.selectedAudioIndex, true, false, 'audio'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '音频' },
          backgroundColor: Color.Transparent,
          systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
        })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.triggerContent(this.audioLabel(), this.audioOptions.length > 1, this.isShowingAudioOptions)
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showAudioOptions())
        .bindSheet($$this.isShowingAudioOptions, this.buildOptions(this.audioOptions,
          this.selectedAudioIndex, false, false, 'audio'), {
          preferType: SheetType.BOTTOM,
          height: SheetSize.FIT_CONTENT,
          title: { title: '音频' },
          systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
        })
    } else {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.triggerContent(this.audioLabel(), this.audioOptions.length > 1, this.isShowingAudioOptions)
      }
        .layoutWeight(1).margin({ left: 4, right: 4 }).height(32).padding(0).borderRadius(16)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
        .onClick(() => this.showAudioOptions())
        .bindSheet($$this.isShowingAudioOptions, this.buildOptions(this.audioOptions,
          this.selectedAudioIndex, false, false, 'audio'), {
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
            item: option,
            group: group,
            isSelected: selectedIndex === index,
            native: native
          })
            .hitTestBehavior(HitTestMode.Block)
            .onClick(() => {
              if (group === 'subtitle') this.selectSubtitle(index)
              else this.selectAudio(index)
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
  @Require @Param item: MediaOptionViewData
  @Require @Param group: string
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
  }
}
