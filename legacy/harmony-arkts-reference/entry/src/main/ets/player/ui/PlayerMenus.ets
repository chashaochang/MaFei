import {
  MediaPlaybackPlayMethod,
  MediaPlaybackProtocol,
  MediaPlaybackSource,
  MediaPlaybackStream,
  MediaPlaybackSubtitleDeliveryMethod
} from '../../media/playback/MediaPlaybackModels'

const MAX_VIDEO_STREAMS_DISPLAY = 3
const MAX_AUDIO_STREAMS_DISPLAY = 5

const BITRATE_MEGA_BIT = 1_000_000
const BITRATE_KILO_BIT = 1_000

const SPEED_MENU_STEP_SIZE = 0.25
const SPEED_MENU_STEP_MIN = 2 // → 0.5x
const SPEED_MENU_STEP_MAX = 8 // → 2x

export class PlaybackInfoEntry {
  readonly label: string
  readonly value: string

  constructor(label: string, value: string) {
    this.label = label
    this.value = value
  }
}

export class PlaybackInfoSection {
  readonly title: string
  readonly entries: PlaybackInfoEntry[]

  constructor(title: string, entries: PlaybackInfoEntry[]) {
    this.title = title
    this.entries = entries
  }
}

/** 由 PlayerChrome 从 MFPlayer 采集的运行时播放快照。 */
export class PlaybackInfoRuntime {
  decodeMode: string = ''
  decoder: string = ''
  renderer: string = ''
  videoCodec: string = ''
  videoCodecProfile: string = ''
  videoPixelFormat: string = ''
  videoPrimaries: string = ''
  videoTransfer: string = ''
  outputPrimaries: string = ''
  outputTransfer: string = ''
  dynamicRange: string = ''
  videoWidth: number = 0
  videoHeight: number = 0
  videoBitrate: number = 0
  audioBitrate: number = 0
  audioSampleRate: number = 0
  audioChannels: number = 0
  videoDropCount: number = -1
  decoderDropCount: number = -1
  delayedFrameCount: number = -1
  downloadSpeedBps: number = 0
  bufferedDurationSec: number = 0
  bufferingPercent: number = -1
  networkSource: boolean = false
  multiConnectionEnabled: boolean = false
  multiConnectionConfigured: number = 0
  multiConnectionActive: number = 0
  multiConnectionAggregateSpeedBps: number = 0
}

@ObservedV2
export class PlayerMenus {
  @Trace playBackInfo: string = ''
  @Trace playBackInfoEntries: PlaybackInfoEntry[] = []
  @Trace playbackInfoSections: PlaybackInfoSection[] = []
  @Trace isShowPlayBackInfo: boolean = false
  @Trace hasNext: boolean = false
  private subtitleCount: number = 0
  private subtitlesEnabled: boolean = false
  private mediaSource?: MediaPlaybackSource = undefined
  private currentVideoStream?: MediaPlaybackStream = undefined
  private currentAudioStream?: MediaPlaybackStream = undefined
  private currentSubtitleStream?: MediaPlaybackStream = undefined
  private runtimeInfo: PlaybackInfoRuntime = new PlaybackInfoRuntime()

  onQueueItemChanged(mediaSource: MediaPlaybackSource, hasNext: boolean): void {
    this.hasNext = hasNext
    this.mediaSource = mediaSource
    this.currentVideoStream = mediaSource.selectedVideoStream
    this.currentAudioStream = mediaSource.selectedAudioStream
    this.currentSubtitleStream = mediaSource.selectedSubtitleStream
    this.subtitleCount = mediaSource.subtitleStreams.length
    this.subtitlesEnabled = this.currentSubtitleStream !== undefined
    this.runtimeInfo = new PlaybackInfoRuntime()
    this.rebuildPlaybackInfo()
  }

  refreshPlaybackInfo(runtimeInfo: PlaybackInfoRuntime, videoStream?: MediaPlaybackStream,
    audioStream?: MediaPlaybackStream, subtitleStream?: MediaPlaybackStream): void {
    this.runtimeInfo = runtimeInfo
    this.currentVideoStream = videoStream ?? this.mediaSource?.selectedVideoStream
    this.currentAudioStream = audioStream ?? this.mediaSource?.selectedAudioStream
    this.currentSubtitleStream = subtitleStream ?? this.mediaSource?.selectedSubtitleStream
    this.subtitlesEnabled = this.currentSubtitleStream !== undefined
    this.rebuildPlaybackInfo()
  }

  private rebuildPlaybackInfo(): void {
    const mediaSource: MediaPlaybackSource | undefined = this.mediaSource
    if (mediaSource === undefined) {
      this.playbackInfoSections = []
      this.playBackInfoEntries = []
      this.playBackInfo = ''
      return
    }

    const sections: PlaybackInfoSection[] = []
    this.appendSection(sections, '播放链路', this.buildPlaybackEntries(mediaSource))
    this.appendSection(sections, '视频', this.buildVideoEntries(mediaSource))
    this.appendSection(sections, '音频', this.buildAudioEntries())
    this.appendSection(sections, '字幕', this.buildSubtitleEntries(mediaSource))
    this.appendSection(sections, '网络与缓冲', this.buildNetworkEntries(mediaSource))
    this.playbackInfoSections = sections

    const flattenedEntries: PlaybackInfoEntry[] = []
    for (const section of sections) {
      for (const entry of section.entries) {
        flattenedEntries.push(entry)
      }
    }
    this.playBackInfoEntries = flattenedEntries
    this.playBackInfo = flattenedEntries.map((item: PlaybackInfoEntry): string => `${item.label}：${item.value}`)
      .join('\n')
  }

  private appendSection(target: PlaybackInfoSection[], title: string, entries: PlaybackInfoEntry[]): void {
    if (entries.length > 0) {
      target.push(new PlaybackInfoSection(title, entries))
    }
  }

  private buildPlaybackEntries(mediaSource: MediaPlaybackSource): PlaybackInfoEntry[] {
    const entries: PlaybackInfoEntry[] = []
    this.appendEntry(entries, '标题', this.buildDisplayTitle(mediaSource))
    this.appendEntry(entries, '播放方式', this.playMethodLabel(mediaSource.playMethod))
    this.appendEntry(entries, '协议', this.normalizeValue(mediaSource.sourceInfo.Protocol))
    this.appendEntry(entries, '容器格式', this.normalizeValue(mediaSource.sourceInfo.Container))
    this.appendEntry(entries, '渲染器', this.normalizeValue(this.runtimeInfo.renderer))
    return entries
  }

  private buildVideoEntries(mediaSource: MediaPlaybackSource): PlaybackInfoEntry[] {
    const entries: PlaybackInfoEntry[] = []
    const videoStream: MediaPlaybackStream | undefined = this.currentVideoStream ?? mediaSource.selectedVideoStream
    this.appendEntry(entries, '解码', this.decodeLabel())
    this.appendEntry(entries, '视频编码', this.videoCodecLabel(videoStream))
    this.appendEntry(entries, '分辨率', this.videoResolutionLabel(videoStream))
    this.appendEntry(entries, '视频码率', this.videoBitrateLabel(videoStream))
    this.appendEntry(entries, '动态范围', this.normalizeValue(this.runtimeInfo.dynamicRange))
    this.appendEntry(entries, '色彩空间', this.colorSpaceLabel(videoStream))
    this.appendEntry(entries, '像素格式', this.firstNonEmpty(this.runtimeInfo.videoPixelFormat, videoStream?.PixelFormat))
    this.appendEntry(entries, '位深度', videoStream?.BitDepth ? `${videoStream.BitDepth} bit` : '')
    this.appendEntry(entries, '帧统计', this.frameStatsLabel())
    return entries
  }

  private buildAudioEntries(): PlaybackInfoEntry[] {
    const entries: PlaybackInfoEntry[] = []
    const audioStream: MediaPlaybackStream | undefined = this.currentAudioStream
    this.appendEntry(entries, '当前音轨', this.streamTitle(audioStream))
    this.appendEntry(entries, '音频编码', this.firstNonEmpty(audioStream?.Codec, audioStream?.CodecTag))
    this.appendEntry(entries, '语言', this.normalizeValue(audioStream?.Language))
    this.appendEntry(entries, '声道与采样率', this.audioFormatLabel())
    this.appendEntry(entries, '音频码率', this.runtimeInfo.audioBitrate > 0 ? this.formatBitrate(this.runtimeInfo.audioBitrate) : '')
    return entries
  }

  private buildSubtitleEntries(mediaSource: MediaPlaybackSource): PlaybackInfoEntry[] {
    const entries: PlaybackInfoEntry[] = []
    const subtitleStream: MediaPlaybackStream | undefined = this.currentSubtitleStream
    if (subtitleStream === undefined) {
      if (this.subtitleCount > 0 || mediaSource.externalSubtitleStreams.length > 0) {
        this.appendEntry(entries, '当前字幕', '未启用')
      }
      return entries
    }
    this.appendEntry(entries, '当前字幕', this.streamTitle(subtitleStream))
    this.appendEntry(entries, '字幕格式', this.firstNonEmpty(subtitleStream.Codec, subtitleStream.CodecTag))
    this.appendEntry(entries, '语言', this.normalizeValue(subtitleStream.Language))
    this.appendEntry(entries, '来源', this.subtitleSourceLabel(subtitleStream))
    return entries
  }

  private buildNetworkEntries(mediaSource: MediaPlaybackSource): PlaybackInfoEntry[] {
    const entries: PlaybackInfoEntry[] = []
    if (!this.runtimeInfo.networkSource && !this.isNetworkPlayback(mediaSource)) {
      return entries
    }
    this.appendEntry(entries, '实时网速', this.runtimeInfo.downloadSpeedBps > 0 ?
      this.formatByteRate(this.runtimeInfo.downloadSpeedBps) : '')
    this.appendEntry(entries, '缓冲时长', this.bufferedDurationLabel())
    this.appendEntry(entries, '缓冲进度', this.runtimeInfo.bufferingPercent >= 0 ?
      `${Math.round(this.runtimeInfo.bufferingPercent)}%` : '')
    this.appendEntry(entries, '连接方式', this.multiConnectionLabel())
    return entries
  }

  private appendEntry(entries: PlaybackInfoEntry[], label: string, rawValue?: string): void {
    const value: string = rawValue?.trim() ?? ''
    if (value.length > 0) {
      entries.push(new PlaybackInfoEntry(label, value))
    }
  }

  private playMethodLabel(playMethod: MediaPlaybackPlayMethod): string {
    switch (playMethod) {
      case MediaPlaybackPlayMethod.DirectPlay:
        return '直放'
      case MediaPlaybackPlayMethod.DirectStream:
        return '直接串流'
      case MediaPlaybackPlayMethod.Transcode:
        return '转码'
      default:
        return ''
    }
  }

  private decodeLabel(): string {
    const decodeMode: string = this.normalizeValue(this.runtimeInfo.decodeMode).toLowerCase()
    const decoder: string = this.normalizeValue(this.runtimeInfo.decoder)
    if (decodeMode.length === 0 && decoder.length === 0) {
      return ''
    }
    const software: boolean = decodeMode === 'software' || decodeMode === 'soft' || decodeMode === 'no' ||
      decoder.toLowerCase() === 'no'
    const modeLabel: string = software ? '软解' : '硬解'
    return decoder.length > 0 && decoder.toLowerCase() !== 'no' ? `${modeLabel} · ${decoder}` : modeLabel
  }

  private videoCodecLabel(videoStream?: MediaPlaybackStream): string {
    const codec: string = this.firstNonEmpty(this.runtimeInfo.videoCodec, videoStream?.Codec)
    const profile: string = this.firstNonEmpty(this.runtimeInfo.videoCodecProfile, videoStream?.Profile)
    if (codec.length === 0) {
      return ''
    }
    return profile.length > 0 ? `${codec.toUpperCase()} · ${profile}` : codec.toUpperCase()
  }

  private videoResolutionLabel(videoStream?: MediaPlaybackStream): string {
    const width: number = this.runtimeInfo.videoWidth > 0 ? this.runtimeInfo.videoWidth : (videoStream?.Width ?? 0)
    const height: number = this.runtimeInfo.videoHeight > 0 ? this.runtimeInfo.videoHeight : (videoStream?.Height ?? 0)
    const frameRate: number = videoStream?.RealFrameRate ?? videoStream?.AverageFrameRate ??
      videoStream?.ReferenceFrameRate ?? 0
    if (width <= 0 || height <= 0) {
      return frameRate > 0 ? this.formatFrameRate(frameRate) : ''
    }
    const resolution: string = `${width}×${height}`
    return frameRate > 0 ? `${resolution} · ${this.formatFrameRate(frameRate)}` : resolution
  }

  private videoBitrateLabel(videoStream?: MediaPlaybackStream): string {
    const actualBitrate: number = this.runtimeInfo.videoBitrate
    const sourceBitrate: number = videoStream?.BitRate ?? 0
    if (actualBitrate <= 0) {
      return sourceBitrate > 0 ? this.formatBitrate(sourceBitrate) : ''
    }
    const actualText: string = this.formatBitrate(actualBitrate)
    if (sourceBitrate > 0 && Math.abs(sourceBitrate - actualBitrate) > 1000) {
      return `${actualText}（源 ${this.formatBitrate(sourceBitrate)}）`
    }
    return actualText
  }

  private colorSpaceLabel(videoStream?: MediaPlaybackStream): string {
    const sourcePrimaries: string = this.firstNonEmpty(this.runtimeInfo.videoPrimaries, videoStream?.ColorPrimaries,
      videoStream?.ColorSpace)
    const sourceTransfer: string = this.firstNonEmpty(this.runtimeInfo.videoTransfer, videoStream?.ColorTransfer)
    const outputPrimaries: string = this.normalizeValue(this.runtimeInfo.outputPrimaries)
    const outputTransfer: string = this.normalizeValue(this.runtimeInfo.outputTransfer)
    const source: string = this.joinParts([sourcePrimaries, sourceTransfer], ' · ')
    const output: string = this.joinParts([outputPrimaries, outputTransfer], ' · ')
    if (source.length > 0 && output.length > 0 && source !== output) {
      return `${source} → ${output}`
    }
    return this.firstNonEmpty(source, output)
  }

  private frameStatsLabel(): string {
    if (this.runtimeInfo.videoDropCount < 0 && this.runtimeInfo.decoderDropCount < 0 &&
      this.runtimeInfo.delayedFrameCount < 0) {
      return ''
    }
    const parts: string[] = []
    if (this.runtimeInfo.videoDropCount >= 0) {
      parts.push(`渲染丢 ${this.runtimeInfo.videoDropCount}`)
    }
    if (this.runtimeInfo.decoderDropCount >= 0) {
      parts.push(`解码丢 ${this.runtimeInfo.decoderDropCount}`)
    }
    if (this.runtimeInfo.delayedFrameCount >= 0) {
      parts.push(`延迟 ${this.runtimeInfo.delayedFrameCount}`)
    }
    return parts.join(' · ')
  }

  private audioFormatLabel(): string {
    const parts: string[] = []
    if (this.runtimeInfo.audioChannels > 0) {
      parts.push(`${this.runtimeInfo.audioChannels} 声道`)
    }
    if (this.runtimeInfo.audioSampleRate > 0) {
      parts.push(`${Math.round(this.runtimeInfo.audioSampleRate / 1000)} kHz`)
    }
    return parts.join(' · ')
  }

  private subtitleSourceLabel(stream: MediaPlaybackStream): string {
    if (stream.IsExternal === true || stream.DeliveryMethod === MediaPlaybackSubtitleDeliveryMethod.External) {
      return '外挂'
    }
    if (stream.DeliveryMethod === MediaPlaybackSubtitleDeliveryMethod.Hls) {
      return 'HLS 字幕'
    }
    return '内嵌'
  }

  private bufferedDurationLabel(): string {
    if (this.runtimeInfo.bufferedDurationSec <= 0) {
      return ''
    }
    const value: number = this.runtimeInfo.bufferedDurationSec
    return value >= 60 ? `${Math.floor(value / 60)} 分 ${Math.round(value % 60)} 秒` : `${value.toFixed(1)} 秒`
  }

  private multiConnectionLabel(): string {
    if (!this.runtimeInfo.multiConnectionEnabled) {
      return '单连接'
    }
    const configured: number = this.runtimeInfo.multiConnectionConfigured
    const active: number = this.runtimeInfo.multiConnectionActive
    if (configured <= 0) {
      return '单连接（多连接未启用）'
    }
    const activeText: string = active > 0 ? `，活跃 ${active}` : ''
    return `${configured} 路并发${activeText}`
  }

  private isNetworkPlayback(mediaSource: MediaPlaybackSource): boolean {
    return mediaSource.sourceInfo.Protocol !== undefined && mediaSource.sourceInfo.Protocol !== MediaPlaybackProtocol.File
  }

  private streamTitle(stream?: MediaPlaybackStream): string {
    return this.firstNonEmpty(stream?.DisplayTitle, stream?.Title)
  }

  private buildDisplayTitle(mediaSource: MediaPlaybackSource): string {
    const seriesName: string = mediaSource.item.SeriesName?.trim() ?? ''
    const title: string = mediaSource.name.trim()
    if (seriesName.length > 0 && seriesName !== title) {
      return `${seriesName} - ${title}`
    }
    return title
  }

  private formatBitrate(bitrate: number): string {
    if (bitrate >= BITRATE_MEGA_BIT) {
      return `${this.trimFraction(bitrate / BITRATE_MEGA_BIT)} Mbps`
    }
    if (bitrate >= BITRATE_KILO_BIT) {
      return `${this.trimFraction(bitrate / BITRATE_KILO_BIT)} Kbps`
    }
    return `${Math.round(bitrate)} bps`
  }

  private formatByteRate(bytesPerSecond: number): string {
    if (bytesPerSecond >= 1024 * 1024) {
      return `${this.trimFraction(bytesPerSecond / 1024 / 1024)} MB/s`
    }
    if (bytesPerSecond >= 1024) {
      return `${Math.max(1, Math.round(bytesPerSecond / 1024))} KB/s`
    }
    return `${Math.max(0, Math.round(bytesPerSecond))} B/s`
  }

  private formatFrameRate(frameRate: number): string {
    return `${this.trimFraction(frameRate)} fps`
  }

  private trimFraction(value: number): string {
    return value.toFixed(2).replace(/\.00$/, '').replace(/(\.\d)0$/, '$1')
  }

  private normalizeValue(value?: string | null): string {
    if (value === undefined || value === null) {
      return ''
    }
    const text: string = `${value}`.trim()
    return text.toLowerCase() === 'unknown' ? '' : text
  }

  private firstNonEmpty(...values: Array<string | undefined>): string {
    for (const value of values) {
      const normalized: string = this.normalizeValue(value)
      if (normalized.length > 0) {
        return normalized
      }
    }
    return ''
  }

  private joinParts(parts: string[], separator: string): string {
    const normalizedParts: string[] = []
    for (const part of parts) {
      const normalized: string = this.normalizeValue(part)
      if (normalized.length > 0) {
        normalizedParts.push(normalized)
      }
    }
    return normalizedParts.join(separator)
  }


  // 主要函数实现
  buildMediaStreamsInfo(
    mediaStreams: MediaPlaybackStream[],
    prefix: Resource,
    maxStreams: number,
    streamSuffix: (stream: MediaPlaybackStream) => string
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
