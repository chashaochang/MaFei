import { MediaSourceInfo, MediaStream, MediaStreamType } from "sdk/src/main/ets/generated-client/models"
import { LanguageUtil } from "../../../utils/LanguageUtil"

@ComponentV2
export struct VideoInfoDetail {
  @Require @Param source: MediaSourceInfo
  @Local videos: MediaStream[] = []
  @Local audios: MediaStream[] = []
  @Local subtitles: MediaStream[] = []

  @Monitor('source')
  onSourceChange() {
    this.getDataFromSource()
  }

  aboutToAppear(): void {
    this.getDataFromSource()
  }

  getDataFromSource() {
    this.videos = []
    this.subtitles = []
    this.audios = []
    this.source.MediaStreams?.forEach((stream) => {
      if (stream.Type === MediaStreamType.Video) {
        this.videos.push(stream)
      } else if (stream.Type === MediaStreamType.Audio) {
        this.audios.push(stream)
      } else if (stream.Type === MediaStreamType.Subtitle) {
        this.subtitles.push(stream)
      }
    })
  }

  build() {
    Column({ space: 8 }) {
      Text('视频').fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
      ForEach(this.videos, (video: MediaStream) => {
        Column({ space: 16 }) {
          Text(video.DisplayTitle).fontSize(13).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_1'))
          Row() {
            Text(`编码器`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.Codec).fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`配置`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.Profile).fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`等级`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.Level + '').fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`分辨率`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(`${video.Width} * ${video.Height}`).fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`宽高比`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.AspectRatio).fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Divider()
          Row() {
            Text(`隔行扫描`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.IsInterlaced ? '是' : '否').fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`帧率`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.RealFrameRate + ' fps').fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`码率`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(((video.BitRate || 0) * 1e-6).toFixed(2) + ' mbps')
              .fontSize(12)
              .fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`视频动态范围`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.VideoRangeType).fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`色彩原色`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.ColorPrimaries || '--').fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Divider()

          Row() {
            Text(`色彩空间`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.ColorSpace || '--').fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`色彩转换`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.ColorTransfer || '--').fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`位深度`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.BitDepth + ' bit').fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`像素格式`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.PixelFormat).fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

          Row() {
            Text(`参考帧`).fontSize(12).fontColor($r('app.color.text_2'))
            Blank().layoutWeight(1)
            Text(video.RefFrames + '').fontSize(12).fontColor($r('app.color.text_2'))
          }.justifyContent(FlexAlign.SpaceBetween)

        }
        .alignItems(HorizontalAlign.Start)
        .width('100%')
        .backgroundColor($r('app.color.bg_2'))
        .borderRadius(12)
        .padding(12)
        .margin({ top: 4 })
      })
      if (this.audios.length > 0) {
        Blank().height(8)
        Text('音频').fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
        ForEach(this.audios, (audio: MediaStream) => {
          Column({ space: 16 }) {
            Text(audio.DisplayTitle).fontSize(13).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_1'))
            Row() {
              Text(`语言`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(LanguageUtil.getLanguageDisplayNameBy6392T(audio.Language || ''))
                .fontSize(12)
                .fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`编码器`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(audio.Codec).fontSize(12).fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`配置`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(audio.Profile || '--').fontSize(12).fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)

            Divider()
            Row() {
              Text(`声道`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(audio.Channels + ' ch').fontSize(12).fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`采样率`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(audio.SampleRate + ' Hz').fontSize(12).fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`码率`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(audio.BitRate + ' bps').fontSize(12).fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)

            Divider()
            Row() {
              Text(`布局`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(audio.ChannelLayout || '--').fontSize(12).fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`默认`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(audio.IsDefault ? '是' : '否').fontSize(12).fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
          .width('100%')
          .backgroundColor($r('app.color.bg_2'))
          .borderRadius(12)
          .padding(12)
          .margin({ top: 8 })
        })
      }
      if (this.subtitles.length > 0) {
        Blank().height(8)
        Text('字幕').fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
        ForEach(this.subtitles, (subtitle: MediaStream) => {
          Column({ space: 16 }) {
            Text(subtitle.DisplayTitle).fontSize(13).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_1'))
            Row() {
              Text(`语言`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(LanguageUtil.getLanguageDisplayNameBy6392T(subtitle.Language || ''))
                .fontSize(12)
                .fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`编码器`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(subtitle.Codec || '--').fontSize(12).fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)

            Divider()
            Row() {
              Text(`默认`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(subtitle.IsDefault ? '是' : '否').fontSize(12).fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)

            Row() {
              Text(`强制`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(subtitle.IsForced ? '是' : '否').fontSize(12).fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)

            Divider()
            Row() {
              Text(`外部`).fontSize(12).fontColor($r('app.color.text_2'))
              Blank().layoutWeight(1)
              Text(subtitle.IsExternal ? '是' : '否').fontSize(12).fontColor($r('app.color.text_2'))
            }.justifyContent(FlexAlign.SpaceBetween)
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
          .width('100%')
          .backgroundColor($r('app.color.bg_2'))
          .borderRadius(12)
          .padding(12)
          .margin({ top: 8 })
        })
      }
    }.width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}