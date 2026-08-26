import {
  MediaFileInfo,
  MediaStreamInfo,
  MediaStreamKind
} from '../../../media/catalog/MediaCatalogModels'
import { AppUIState } from '../../../entity/AppUIState'
import { LanguageUtil } from '../../../utils/LanguageUtil'
import { AppStorageV2 } from '@kit.ArkUI'
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
  @Param source?: MediaFileInfo = undefined
  @Param triggerTextColor: ResourceColor = $r('app.color.text_primary')
  @Local subtitle: string = '无字幕'
  @Local audio: string = '未知音频'
  @Consumer() selectedSubtitleIndex: number = 0
  @Consumer() selectedAudioIndex: number = 0
  @Local isShowingSubtitleOptions: boolean = false
  @Local isShowingAudioOptions: boolean = false
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  @Local subtitles: Array<MediaStreamInfo> = []
  @Local audios: Array<MediaStreamInfo> = []
  @Event onSelectSubtitle: (subtitle: MediaStreamInfo) => void = (subtitle: MediaStreamInfo) => {}
  @Event onSelectAudio: (audio: MediaStreamInfo) => void = (audio: MediaStreamInfo) => {}

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
    if (this.subtitles.length > 1) {
      this.isShowingSubtitleOptions = true
    }
  }

  private showAudioOptions(): void {
    if (this.audios.length > 1) {
      this.isShowingAudioOptions = true
    }
  }

  @Monitor('source')
  onSourceChange() {
    this.getDataFromSource()
  }

  aboutToAppear(): void {
    this.getDataFromSource()
  }

  getDataFromSource(): void {
    this.subtitles = []
    this.audios = []
    let subtitlesNew: Array<MediaStreamInfo> = []
    let audiosNew: Array<MediaStreamInfo> = []
    const streams = this.source?.streams || []
    streams.forEach((stream: MediaStreamInfo) => {
      if (stream.kind === MediaStreamKind.Audio) {
        audiosNew.push(stream)
      } else if (stream.kind === MediaStreamKind.Subtitle) {
        subtitlesNew.push(stream)
      }
    })
    this.subtitles = subtitlesNew
    this.audios = audiosNew
    this.getShowName()
  }

  getShowName(type?: number): string {
    if (this.subtitles.length > 0) {
      let displayName =
        LanguageUtil.getLanguageDisplayNameBy6392T(this.subtitles[this.selectedSubtitleIndex]?.language || '')
      this.subtitle = displayName + '字幕'
    } else {
      this.subtitle = '无字幕'
    }
    if (this.audios.length > 0) {
      let displayName = LanguageUtil.getLanguageDisplayNameBy6392T(this.audios[this.selectedAudioIndex]?.language || '')
      this.audio = displayName + '音频'
    } else {
      this.audio = '无音频'
    }
    if (type === 0) {
      return this.subtitle
    } else {
      return this.audio
    }
  }

  @Builder
  private subtitleTriggerContent() {
    Row({ space: 2 }) {
      Text(this.getShowName(0))
        .fontSize(12)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.triggerTextColor)
        .textAlign(TextAlign.Center)
        .constraintSize({ maxWidth: this.subtitles.length > 1 ? 'calc(100% - 18vp)' : '100%' })
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      if (this.subtitles.length > 1) {
        SymbolGlyph(this.isShowingSubtitleOptions ? $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
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
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showSubtitleOptions())
      .bindSheet($$this.isShowingSubtitleOptions, this.buildSubtitleOptions(true, true), {
        preferType: SheetType.BOTTOM,
        height: SheetSize.FIT_CONTENT,
        title: { title: '字幕' },
        backgroundColor: Color.Transparent
      })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.subtitleTriggerContent()
      }
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showSubtitleOptions())
      .bindSheet($$this.isShowingSubtitleOptions, this.buildSubtitleOptions(true, false), {
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
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showSubtitleOptions())
      .bindSheet($$this.isShowingSubtitleOptions, this.buildSubtitleOptions(false, false), {
        preferType: SheetType.BOTTOM,
        height: SheetSize.FIT_CONTENT,
        title: { title: '字幕' },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      })
    } else {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.subtitleTriggerContent()
      }
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showSubtitleOptions())
      .bindSheet($$this.isShowingSubtitleOptions, this.buildSubtitleOptions(false, false), {
        preferType: SheetType.BOTTOM,
        height: SheetSize.FIT_CONTENT,
        title: { title: '字幕' }
      })
    }
  }

  @Builder
  private audioTriggerContent() {
    Row({ space: 2 }) {
      Text(this.getShowName(1))
        .fontSize(12)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.triggerTextColor)
        .textAlign(TextAlign.Center)
        .constraintSize({ maxWidth: this.audios.length > 1 ? 'calc(100% - 18vp)' : '100%' })
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      if (this.audios.length > 1) {
        SymbolGlyph(this.isShowingAudioOptions ? $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
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
  private audioTrigger() {
    if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.audioTriggerContent()
      }
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showAudioOptions())
      .bindSheet($$this.isShowingAudioOptions, this.buildAudioOptions(true, true), {
        preferType: SheetType.BOTTOM,
        height: SheetSize.FIT_CONTENT,
        title: { title: '音频' },
        backgroundColor: Color.Transparent
      })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.audioTriggerContent()
      }
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showAudioOptions())
      .bindSheet($$this.isShowingAudioOptions, this.buildAudioOptions(true, false), {
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
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showAudioOptions())
      .bindSheet($$this.isShowingAudioOptions, this.buildAudioOptions(false, false), {
        preferType: SheetType.BOTTOM,
        height: SheetSize.FIT_CONTENT,
        title: { title: '音频' },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      })
    } else {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        this.audioTriggerContent()
      }
      .layoutWeight(1)
      .margin({ left: 4, right: 4 })
      .height(32)
      .padding(0)
      .borderRadius(16)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl, this.useNativeSurface(), this.appUIState.systemMaterialAvailable))
      .onClick(() => this.showAudioOptions())
      .bindSheet($$this.isShowingAudioOptions, this.buildAudioOptions(false, false), {
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
  buildSubtitleOptions(native: boolean, blurFallback: boolean) {
    Scroll() {
      Column({ space: 12 }) {
        ForEach(this.subtitles, (subtitle: MediaStreamInfo, index) => {
          SheetListItem({
            item: subtitle,
            isSelected: this.selectedSubtitleIndex === index,
            native: native
          }).hitTestBehavior(HitTestMode.Block)
            .onClick(() => {
            this.selectedSubtitleIndex = index
            this.isShowingSubtitleOptions = false
            this.onSelectSubtitle(subtitle)
          })
        })
      }
    }
    .scrollBar(BarState.Off)
    .scrollable(ScrollDirection.Vertical)
    .padding({
      left: 16,
      right: 16,
      bottom: 10 + this.appUIState.safeBottom
    })
    .width('100%')
    .constraintSize({
      maxHeight: '100%'
    })
    .align(Alignment.Top)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, blurFallback, false))
  }

  @Builder
  buildAudioOptions(native: boolean, blurFallback: boolean) {
    Scroll() {
      Column({ space: 12 }) {
        ForEach(this.audios, (audio: MediaStreamInfo, index) => {
          SheetListItem({
            item: audio,
            isSelected: this.selectedAudioIndex === index,
            native: native
          }).hitTestBehavior(HitTestMode.Block)
            .onClick(() => {
            this.selectedAudioIndex = index
            this.isShowingAudioOptions = false
            this.onSelectAudio(audio)
          })
        })
      }
    }
    .scrollBar(BarState.Off)
    .scrollable(ScrollDirection.Vertical)
    .padding({
      left: 16,
      right: 16,
      bottom: 10 + this.appUIState.safeBottom
    })
    .width('100%')
    .constraintSize({
      maxHeight: '100%'
    })
    .align(Alignment.Top)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, blurFallback, false))
  }
}

@ComponentV2
struct SheetListItem {
  @Param @Require item: MediaStreamInfo
  @Param @Require isSelected: boolean = false
  @Param native: boolean = false

  build() {
    Row() {
      Radio({
        value: 'Radio1', group: this.item.kind ===
        MediaStreamKind.Subtitle ? 'subtitle' : 'audio'
      })
        .checked(this.isSelected)
        .width(16)
      Column({ space: 4 }) {
        Text(LanguageUtil.getLanguageDisplayNameBy6392T(this.item.language || '') +
          (this.item.kind === MediaStreamKind.Subtitle ? '字幕' : '音频'))
          .fontSize(16)
          .fontColor($r('app.color.text_primary'))
          .padding({ left: 8 })
        Text(this.item.displayTitle)
          .fontSize(12)
          .fontColor($r('app.color.text_1'))
          .padding({ left: 8 })
      }.layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .height(66)
    .backgroundColor(this.native ? Color.Transparent : $r('app.color.bg_2'))
    .borderRadius(8)
    .padding({
      left: 16,
      right: 16
    })
  }
}
