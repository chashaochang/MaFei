import { MediaStream } from "sdk/src/main/ets/generated-client/models";
import { JellyfinMediaSource } from "../source/JellyfinMediaSource";

const SUBTITLES_MENU_GROUP = 0
const AUDIO_MENU_GROUP = 1
const SPEED_MENU_GROUP = 2
const QUALITY_MENU_GROUP = 3
const DECODER_MENU_GROUP = 4

const MAX_VIDEO_STREAMS_DISPLAY = 3
const MAX_AUDIO_STREAMS_DISPLAY = 5

const BITRATE_MEGA_BIT = 1_000_000
const BITRATE_KILO_BIT = 1_000

const SPEED_MENU_STEP_SIZE = 0.25
const SPEED_MENU_STEP_MIN = 2 // → 0.5x
const SPEED_MENU_STEP_MAX = 8 // → 2x

export class PlaybackInfoEntry {
  label: string
  value: string

  constructor(label: string, value: string) {
    this.label = label
    this.value = value
  }
}

@ObservedV2
export class PlayerMenus {
  //视频信息
  @Trace playBackInfo: string = ''
  @Trace playBackInfoEntries: PlaybackInfoEntry[] = []
  @Trace isShowPlayBackInfo: boolean = false
  @Trace hasNext: boolean = false
  private subtitleCount = 0
  private subtitlesEnabled = false

  onQueueItemChanged(mediaSource: JellyfinMediaSource, hasNext: boolean) {
    // previousButton is always enabled and will rewind if at the start of the queue
    //this.hasPrevious = true
    this.hasNext = hasNext
    let videoStream = mediaSource.selectedVideoStream
    let audioStreams = mediaSource.audioStreams
    // buildMenuItems(
    //   audioStreamsMenu.menu,
    //   AUDIO_MENU_GROUP,
    //   audioStreams,
    //   mediaSource.selectedAudioStream,
    // )
    let subtitleStreams = mediaSource.subtitleStreams
    let selectedSubtitleStream = mediaSource.selectedSubtitleStream
    // buildMenuItems(
    //   subtitlesMenu.menu,
    //   SUBTITLES_MENU_GROUP,
    //   subtitleStreams,
    //   selectedSubtitleStream,
    //   true,
    // )
    this.subtitleCount = subtitleStreams.length
    this.subtitlesEnabled = selectedSubtitleStream != null
    // updateSubtitlesButton()
    // val height = videoStream?.height
    // val width = videoStream?.width
    // if (height != null && width != null) {
    //   buildQualityMenu(qualityMenu.menu, mediaSource.maxStreamingBitrate, width, height)
    // } else {
    //   qualityButton.isVisible = false
    // }
    const infoEntries = this.buildPlaybackInfoEntries(mediaSource, videoStream)
    this.playBackInfoEntries = infoEntries
    this.playBackInfo = infoEntries.map((item) => `${item.label}：${item.value}`).join('\n')
  }

  /**
   * 格式化比特率显示
   * @param bitrate 比特率值
   * @returns 格式化后的比特率字符串
   */
  private formatBitrate(bitrate: number): string {
    let value: number;
    let unit: string;

    // 使用条件判断替代Kotlin的when表达式
    if (bitrate > BITRATE_MEGA_BIT) {
      value = bitrate / BITRATE_MEGA_BIT;
      unit = " Mbps";
    } else if (bitrate > BITRATE_KILO_BIT) {
      value = bitrate / BITRATE_KILO_BIT;
      unit = " kbps";
    } else {
      value = bitrate;
      unit = " bps";
    }

    // 格式化数字，保留两位小数，然后移除不必要的尾随零
    let formatted = value.toFixed(2);

    // 移除尾随的".00"
    if (formatted.endsWith('.00')) {
      formatted = formatted.substring(0, formatted.length - 3);
    }
    // 移除尾随的单个"0"（如"1.20" -> "1.2"）
    else if (formatted.endsWith('0') && formatted.includes('.')) {
      formatted = formatted.substring(0, formatted.length - 1);
    }

    return formatted + unit;
  }

  private formatFrameRate(frameRate?: number | null): string {
    if (!frameRate || frameRate <= 0) {
      return '--'
    }
    const formatted = frameRate.toFixed(2).replace(/\.00$/, '').replace(/(\.\d)0$/, '$1')
    return `${formatted} fps`
  }

  private formatResolution(stream?: MediaStream): string {
    const width = stream?.Width
    const height = stream?.Height
    if (!width || !height) {
      return '--'
    }
    return `${width}x${height}`
  }

  private normalizeValue(value?: string | null): string {
    if (!value) {
      return '--'
    }
    const text = `${value}`.trim()
    return text.length > 0 ? text : '--'
  }

  private buildDisplayTitle(mediaSource: JellyfinMediaSource): string {
    const seriesName = mediaSource.item.SeriesName?.trim()
    const title = mediaSource.name.trim()
    if (seriesName && seriesName.length > 0 && seriesName !== title) {
      return `${seriesName} - ${title}`
    }
    return title
  }

  private buildPlaybackInfoEntries(mediaSource: JellyfinMediaSource, videoStream?: MediaStream): PlaybackInfoEntry[] {
    const frameRate = videoStream?.ReferenceFrameRate ?? videoStream?.AverageFrameRate ?? videoStream?.RealFrameRate
    const colorSpace = videoStream?.ColorPrimaries ?? videoStream?.ColorSpace
    const entries: PlaybackInfoEntry[] = [
      new PlaybackInfoEntry('标题', this.buildDisplayTitle(mediaSource)),
      new PlaybackInfoEntry('协议', this.normalizeValue(mediaSource.sourceInfo.Protocol)),
      new PlaybackInfoEntry('容器格式', this.normalizeValue(mediaSource.sourceInfo.Container)),
      new PlaybackInfoEntry('比特率', videoStream?.BitRate ? this.formatBitrate(videoStream.BitRate) : '--'),
      new PlaybackInfoEntry('编码器', this.normalizeValue(videoStream?.Codec)),
      new PlaybackInfoEntry('位深度', videoStream?.BitDepth ? `${videoStream.BitDepth}` : '--'),
      new PlaybackInfoEntry('分辨率', this.formatResolution(videoStream)),
      new PlaybackInfoEntry('帧率', this.formatFrameRate(frameRate)),
      new PlaybackInfoEntry('动态范围', this.normalizeValue(mediaSource.selectedVideoRangeType)),
      new PlaybackInfoEntry('色域', this.normalizeValue(colorSpace)),
      new PlaybackInfoEntry('像素格式', this.normalizeValue(videoStream?.PixelFormat))
    ]
    return entries.filter((item) => item.value !== '--' || item.label === '标题' || item.label === '协议' || item.label === '容器格式')
  }


  // 主要函数实现
  buildMediaStreamsInfo(
    mediaStreams: MediaStream[],
    prefix: Resource,
    maxStreams: number,
    streamSuffix: (stream: MediaStream) => string
  ): string {
    const prefixText = getContext().resourceManager.getStringSync(prefix);
    const streamTexts: string[] = [];

    // 处理流信息
    const streamsToProcess = mediaStreams.slice(0, maxStreams);

    for (const stream of streamsToProcess) {
      const title = (stream.DisplayTitle && stream.DisplayTitle.length > 0)
        ? stream.DisplayTitle
        : getContext().resourceManager.getStringSync($r('app.string.playback_info_stream_unknown_title'));

      const suffix = streamSuffix(stream);
      streamTexts.push(`- ${title}${suffix}`);
    }

    // 处理截断信息
    let result = `${prefixText}:\n${streamTexts.join('\n')}`;

    if (mediaStreams.length > maxStreams) {
      const truncatedText = getContext()
        .resourceManager
        .getStringSync($r('app.string.playback_info_and_x_more', mediaStreams.length - maxStreams))
      result += `\n${truncatedText}`;
    }

    return result;
  }
}
