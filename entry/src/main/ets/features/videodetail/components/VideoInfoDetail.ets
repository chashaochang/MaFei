import {
  MediaFileInfo,
  MediaStreamInfo,
  MediaStreamKind
} from '../../../media/catalog/MediaCatalogModels'
import { LanguageUtil } from "../../../utils/LanguageUtil"
import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../../entity/AppUIState'
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'

@ComponentV2
export struct VideoInfoDetail {
  @Param source?: MediaFileInfo = undefined
  @Local videos: MediaStreamInfo[] = []
  @Local audios: MediaStreamInfo[] = []
  @Local subtitles: MediaStreamInfo[] = []
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private groupBackground(): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
      Color.Transparent : $r('app.color.bg_2')
  }

  private headingTextColor(): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
      $r('sys.color.font_primary') : $r('app.color.text_1')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
      $r('sys.color.font_secondary') : $r('app.color.text_2')
  }

  @Monitor('source')
  onSourceChange() {
    this.getDataFromSource()
  }

  aboutToAppear(): void {
    this.getDataFromSource()
  }

  getDataFromSource(): void {
    this.videos = []
    this.subtitles = []
    this.audios = []
    const streams = this.source?.streams || []
    streams.forEach((stream: MediaStreamInfo) => {
      if (stream.kind === MediaStreamKind.Video) {
        this.videos.push(stream)
      } else if (stream.kind === MediaStreamKind.Audio) {
        this.audios.push(stream)
      } else if (stream.kind === MediaStreamKind.Subtitle) {
        this.subtitles.push(stream)
      }
    })
  }

  build() {
    Column({ space: 8 }) {
      Text('视频').fontSize(15).fontWeight(FontWeight.Medium).fontColor(this.headingTextColor())
      ForEach(this.videos, (video: MediaStreamInfo) => {
        Column({ space: 16 }) {
          Text(video.displayTitle).fontSize(13).fontWeight(FontWeight.Medium).fontColor(this.primaryTextColor())
          Row() {
            Text(`编码器`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.codec).fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`配置`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.profile || '--').fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`等级`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.level !== undefined ? video.level.toString() : '--')
              .fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`分辨率`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.width && video.height ? `${video.width} * ${video.height}` : '--')
              .fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`宽高比`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.aspectRatio || '--').fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Divider()
          Row() {
            Text(`隔行扫描`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.interlaced ? '是' : '否').fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`帧率`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.frameRate !== undefined ? `${video.frameRate} fps` : '--')
              .fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`码率`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.bitrate !== undefined ? (video.bitrate * 1e-6).toFixed(2) + ' mbps' : '--')
              .fontSize(12)
              .fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`视频动态范围`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.videoRangeType || '--').fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`色彩原色`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.colorPrimaries || '--').fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Divider()

          Row() {
            Text(`色彩空间`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.colorSpace || '--').fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`色彩转换`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.colorTransfer || '--').fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`位深度`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.bitDepth !== undefined ? `${video.bitDepth} bit` : '--')
              .fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`像素格式`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.pixelFormat || '--').fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`参考帧`).fontSize(12).fontColor(this.secondaryTextColor())
            Blank().layoutWeight(1)
            Text(video.refFrames !== undefined ? video.refFrames.toString() : '--')
              .fontSize(12).fontColor(this.secondaryTextColor())
          }.justifyContent(FlexAlign.SpaceBetween)

        }
        .alignItems(HorizontalAlign.Start)
        .width('100%')
        .backgroundColor(this.groupBackground())
        .borderRadius(12)
        .padding(12)
        .margin({ top: 4 })
      })
      if (this.audios.length > 0) {
        Blank().height(8)
        Text('音频').fontSize(15).fontWeight(FontWeight.Medium).fontColor(this.headingTextColor())
        ForEach(this.audios, (audio: MediaStreamInfo) => {
          Column({ space: 16 }) {
            Text(audio.displayTitle).fontSize(13).fontWeight(FontWeight.Medium).fontColor(this.primaryTextColor())
            Row() {
              Text(`语言`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(LanguageUtil.getLanguageDisplayNameBy6392T(audio.language || ''))
                .fontSize(12)
                .fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`编码器`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(audio.codec).fontSize(12).fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`配置`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(audio.profile || '--').fontSize(12).fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)

            Divider()
            Row() {
              Text(`声道`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(audio.channels !== undefined ? `${audio.channels} ch` : '--')
                .fontSize(12).fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`采样率`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(audio.sampleRate !== undefined ? `${audio.sampleRate} Hz` : '--')
                .fontSize(12).fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`码率`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(audio.bitrate !== undefined ? `${audio.bitrate} bps` : '--')
                .fontSize(12).fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)

            Divider()
            Row() {
              Text(`布局`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(audio.channelLayout || '--').fontSize(12).fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`默认`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(audio.default ? '是' : '否').fontSize(12).fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
          .width('100%')
          .backgroundColor(this.groupBackground())
          .borderRadius(12)
          .padding(12)
          .margin({ top: 8 })
        })
      }
      if (this.subtitles.length > 0) {
        Blank().height(8)
        Text('字幕').fontSize(15).fontWeight(FontWeight.Medium).fontColor(this.headingTextColor())
        ForEach(this.subtitles, (subtitle: MediaStreamInfo) => {
          Column({ space: 16 }) {
            Text(subtitle.displayTitle).fontSize(13).fontWeight(FontWeight.Medium).fontColor(this.primaryTextColor())
            Row() {
              Text(`语言`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(LanguageUtil.getLanguageDisplayNameBy6392T(subtitle.language || ''))
                .fontSize(12)
                .fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`编码器`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(subtitle.codec || '--').fontSize(12).fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)

            Divider()
            Row() {
              Text(`默认`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(subtitle.default ? '是' : '否').fontSize(12).fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`强制`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(subtitle.forced ? '是' : '否').fontSize(12).fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)

            Divider()
            Row() {
              Text(`外部`).fontSize(12).fontColor(this.secondaryTextColor())
              Blank().layoutWeight(1)
              Text(subtitle.external ? '是' : '否').fontSize(12).fontColor(this.secondaryTextColor())
            }.justifyContent(FlexAlign.SpaceBetween)
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
          .width('100%')
          .backgroundColor(this.groupBackground())
          .borderRadius(12)
          .padding(12)
          .margin({ top: 8 })
        })
      }
    }.width('100%')
    .constraintSize({ maxWidth: 720 })
    .alignSelf(ItemAlign.Center)
    .alignItems(HorizontalAlign.Start)
  }
}
