import { MediaSourceInfo, MediaStream, MediaStreamType } from 'sdk/src/main/ets/generated-client/models'
import { AppUIState } from '../../../entity/AppUIState'
import { LanguageUtil } from '../../../utils/LanguageUtil'
import { AppStorageV2 } from '@kit.ArkUI'

@ComponentV2
export struct AudioSubtitleSelector {
  @Param @Require source: MediaSourceInfo
  @Local subtitle: string = '无字幕'
  @Local audio: string = '未知音频'
  @Consumer() selectedSubtitleIndex: number = 0
  @Consumer() selectedAudioIndex: number = 0
  @Local isShowingSubtitleOptions: boolean = false
  @Local isShowingAudioOptions: boolean = false
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  @Local subtitles: Array<MediaStream> = []
  @Local audios: Array<MediaStream> = []
  @Event onSelectSubtitle: (subtitle: MediaStream) => void = (subtitle: MediaStream) => {}
  @Event onSelectAudio: (audio: MediaStream) => void = (audio: MediaStream) => {}

  @Monitor('source')
  onSourceChange() {
    this.getDataFromSource()
  }

  aboutToAppear(): void {
    this.getDataFromSource()
  }

  getDataFromSource() {
    this.subtitles = []
    this.audios = []
    let subtitlesNew: Array<MediaStream> = []
    let audiosNew: Array<MediaStream> = []
    this.source.MediaStreams?.forEach((stream) => {
      if (stream.Type === MediaStreamType.Audio) {
        audiosNew.push(stream)
      } else if (stream.Type === MediaStreamType.Subtitle) {
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
        LanguageUtil.getLanguageDisplayNameBy6392T(this.subtitles[this.selectedSubtitleIndex].Language || '')
      this.subtitle = displayName + '字幕'
    } else {
      this.subtitle = '无字幕'
    }
    if (this.audios.length > 0) {
      let displayName = LanguageUtil.getLanguageDisplayNameBy6392T(this.audios[this.selectedAudioIndex].Language || '')
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

  build() {
    Row({ space: 16 }) {
      Row() {
        Text(this.getShowName(0)).fontSize(12).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
        SymbolGlyph(this.isShowingSubtitleOptions ? $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
          .fontSize(12)
          .fontWeight(FontWeight.Bold)
          .fontColor([$r('app.color.text_primary')])
          .visibility(this.subtitles.length > 1 ? Visibility.Visible : Visibility.Hidden)
      }.onClick(() => {
        if (this.subtitles.length > 1) {
          this.isShowingSubtitleOptions = true
        }
      }).bindSheet($$this.isShowingSubtitleOptions, this.buildSubtitleOptions(), {
        preferType: SheetType.BOTTOM,
        height: SheetSize.FIT_CONTENT,
        title: { title: '字幕' },
      })

      Row() {
        Text(this.getShowName(1)).fontSize(12).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
        SymbolGlyph(this.isShowingAudioOptions ? $r('sys.symbol.chevron_up') : $r('sys.symbol.chevron_down'))
          .fontSize(12)
          .fontWeight(FontWeight.Bold)
          .fontColor([$r('app.color.text_primary')])
          .visibility(this.audios.length > 1 ? Visibility.Visible : Visibility.Hidden)
      }.onClick(() => {
        if (this.audios.length > 1) {
          this.isShowingAudioOptions = true
        }
      }).bindSheet($$this.isShowingAudioOptions, this.buildAudioOptions(), {
        preferType: SheetType.BOTTOM,
        height: SheetSize.FIT_CONTENT,
        title: { title: '音频' },
      })
    }
  }

  @Builder
  buildSubtitleOptions() {
    Scroll() {
      Column({ space: 12 }) {
        ForEach(this.subtitles, (subtitle: MediaStream, index) => {
          SheetListItem({
            item: subtitle,
            isSelected: this.selectedSubtitleIndex === index,
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
  }

  @Builder
  buildAudioOptions() {
    Scroll() {
      Column({ space: 12 }) {
        ForEach(this.audios, (audio: MediaStream, index) => {
          SheetListItem({
            item: audio,
            isSelected: this.selectedAudioIndex === index,
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
  }
}

@ComponentV2
struct SheetListItem {
  @Param @Require item: MediaStream
  @Param @Require isSelected: boolean = false

  build() {
    Row() {
      Radio({
        value: 'Radio1', group: this.item.Type ===
        MediaStreamType.Subtitle ? 'subtitle' : 'audio'
      })
        .checked(this.isSelected)
        .width(16)
      Column({ space: 4 }) {
        Text(LanguageUtil.getLanguageDisplayNameBy6392T(this.item.Language || '') + this.item.Type ===
        MediaStreamType.Subtitle ? '字幕' : '音频')
          .fontSize(16)
          .fontColor($r('app.color.text_primary'))
          .padding({ left: 8 })
        Text(this.item.DisplayTitle)
          .fontSize(12)
          .fontColor($r('app.color.text_1'))
          .padding({ left: 8 })
      }.layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .height(66)
    .backgroundColor($r('app.color.bg_2'))
    .borderRadius(8)
    .padding({
      left: 16,
      right: 16
    })
  }
}