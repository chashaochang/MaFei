import {
  BaseItemDto,
  MediaSourceInfo,
  MediaStream,
  MediaStreamType,
  PlayMethod,
  SubtitleDeliveryMethod,
  VideoRangeType
} from 'sdk/src/main/ets/generated-client/models'
import { Constants } from '../../utils/Constants'
import { CodecHelpers } from '../deviceprofile/CodecHelpers'
import { ExternalSubtitleStream } from './ExternalSubtitleStream'
import { avSession, avSession as AVSessionManager } from '@kit.AVSessionKit';
import { ImageUrlsApi } from 'sdk'
import { ApiClient } from '../../network/ApiClient'

const AV_PLAYER_SUPPORTED_VIDEO_CONTAINERS: string[] = ['mp4', 'm4v', 'mov', 'mkv', 'ts']
const AV_PLAYER_SUPPORTED_VIDEO_CODECS: string[] = ['h264', 'avc', 'hevc', 'h265']
const AV_PLAYER_SUPPORTED_AUDIO_CODECS: string[] = ['aac', 'mp3']
const AV_PLAYER_SUPPORTED_SUBTITLE_CODECS: string[] = ['srt', 'vtt', 'webvtt']

@ObservedV2
export class JellyfinMediaSource {
  itemId: string
  item: BaseItemDto
  sourceInfo: MediaSourceInfo
  playSessionId: string
  liveStreamId?: string | null
  maxStreamingBitrate?: number | null
  private startTimeTicks?: number | null
  audioStreamIndex?: number | null
  subtitleStreamIndex?: number | null
  id: string
  name: string
  playMethod: PlayMethod
  runTimeTicks: number
  runTimeMs: number
  mediaStreams: MediaStream[]
  audioStreams: Array<MediaStream>
  subtitleStreams: Array<MediaStream>
  externalSubtitleStreams: ExternalSubtitleStream[]
  _selectedVideoStream?: MediaStream

  private set selectedVideoStream(_selectedVideoStream: MediaStream | undefined) {
    this._selectedVideoStream = _selectedVideoStream
  }

  get selectedVideoStream(): MediaStream | undefined {
    return this._selectedVideoStream
  }

  @Trace _selectedAudioStream?: MediaStream

  private set selectedAudioStream(_selectedAudioStream: MediaStream | undefined) {
    this._selectedAudioStream = _selectedAudioStream
  }

  get selectedAudioStream(): MediaStream | undefined {
    return this._selectedAudioStream
  }

  @Trace _selectedSubtitleStream?: MediaStream

  private set selectedSubtitleStream(_selectedSubtitleStream: MediaStream | undefined) {
    this._selectedSubtitleStream = _selectedSubtitleStream
  }

  get selectedSubtitleStream(): MediaStream | undefined {
    return this._selectedSubtitleStream
  }

  get selectedAudioStreamIndex(): number | undefined {
    return this.selectedAudioStream?.Index
  }

  // -1 disables subtitles, null would select the default subtitle
  // If the default should be played, it would be explicitly set above
  get selectedSubtitleStreamIndex(): number {
    return this.selectedSubtitleStream?.Index ?? -1
  }

  get selectedVideoRangeType(): string {
    return this.selectedVideoStream?.VideoRangeType ?? VideoRangeType.Unknown
  }

  get selectedVideoDvProfile(): number | null {
    return this.selectedVideoStream?.DvProfile ?? null
  }

  isDolbyVisionStream(): boolean {
    const range = this.selectedVideoStream?.VideoRangeType
    return range === VideoRangeType.Dovi ||
      range === VideoRangeType.DoviWithHdr10 ||
      range === VideoRangeType.DoviWithHlg ||
      range === VideoRangeType.DoviWithSdr ||
      this.selectedVideoDvProfile != null
  }

  canClientFallbackToHdr(): boolean {
    const range = this.selectedVideoStream?.VideoRangeType
    return this.isDolbyVisionStream() ||
      range === VideoRangeType.DoviWithHdr10 ||
      range === VideoRangeType.DoviWithHlg ||
      this.selectedVideoDvProfile === 8
  }

  getVideoHdrStrategyLabel(): string {
    if (!this.isDolbyVisionStream()) {
      return this.selectedVideoRangeType
    }
    return `DoVi-P${this.selectedVideoDvProfile ?? '?'} -> MPV direct play preferred`
  }

  private normalizeStreamLabel(value?: string | null): string {
    if (!value) {
      return ''
    }
    return value.trim().toLowerCase()
  }

  private normalizeCodec(value?: string | null): string {
    return this.normalizeStreamLabel(value)
      .replace(/[^a-z0-9+_-]/g, '')
  }

  private normalizeContainer(value?: string | null): string {
    const normalizedValue = this.normalizeStreamLabel(value)
    if (normalizedValue === 'mpegts') {
      return 'ts'
    }
    return normalizedValue
  }

  private normalizePixelFormat(value?: string | null): string {
    return this.normalizeStreamLabel(value)
      .replace(/[^a-z0-9]/g, '')
  }

  private collectHdrHintText(): string {
    const videoStream = this.selectedVideoStream
    return [
      videoStream?.ColorTransfer,
      videoStream?.VideoDoViTitle,
      videoStream?.Title,
      videoStream?.DisplayTitle,
      videoStream?.Comment
    ]
      .filter((value?: string | null): boolean => value != null && value.length > 0)
      .join(' ')
      .toLowerCase()
  }

  isHdrVividStream(): boolean {
    const hdrHintText = this.collectHdrHintText()
    return hdrHintText.includes('hdr vivid') ||
      hdrHintText.includes('hdrvivid') ||
      hdrHintText.includes('vivid')
  }

  isHdrPreferredForAvPlayer(): boolean {
    const range = this.selectedVideoStream?.VideoRangeType
    return range === VideoRangeType.Hdr10 ||
      range === VideoRangeType.Hdr10Plus ||
      range === VideoRangeType.Hlg
  }

  private isAvPlayerSupportedVideoContainer(): boolean {
    return AV_PLAYER_SUPPORTED_VIDEO_CONTAINERS.includes(this.normalizeContainer(this.sourceInfo.Container))
  }

  private isAvPlayerSupportedVideoCodec(): boolean {
    return AV_PLAYER_SUPPORTED_VIDEO_CODECS.includes(this.normalizeCodec(this.selectedVideoStream?.Codec))
  }

  private isAvPlayerSupportedVideoRange(): boolean {
    const range = this.selectedVideoStream?.VideoRangeType ?? VideoRangeType.Unknown
    return range === VideoRangeType.Unknown ||
      range === VideoRangeType.Sdr ||
      range === VideoRangeType.Hdr10 ||
      range === VideoRangeType.Hdr10Plus ||
      range === VideoRangeType.Hlg
  }

  private isAvPlayerSupportedPixelFormat(): boolean {
    const pixelFormat = this.normalizePixelFormat(this.selectedVideoStream?.PixelFormat)
    if (pixelFormat.length === 0) {
      return true
    }
    if (pixelFormat.includes('444') || pixelFormat.includes('422')) {
      return false
    }
    if (pixelFormat.includes('rgb') || pixelFormat.includes('gbr')) {
      return false
    }
    return pixelFormat.includes('420') ||
      pixelFormat === 'nv12' ||
      pixelFormat.includes('p010')
  }

  isHighChromaHdrPixelFormat(): boolean {
    const pixelFormat = this.normalizePixelFormat(this.selectedVideoStream?.PixelFormat)
    if (pixelFormat.length === 0) {
      return false
    }
    return pixelFormat.includes('444') ||
      pixelFormat.includes('422') ||
      pixelFormat.includes('rgb') ||
      pixelFormat.includes('gbr')
  }

  shouldPreferMpvSoftwareDecode(): boolean {
    return this.isHighChromaHdrPixelFormat()
  }

  private getUnsupportedAudioStreamsForAvPlayer(): MediaStream[] {
    return this.audioStreams.filter((stream) => {
      return !AV_PLAYER_SUPPORTED_AUDIO_CODECS.includes(this.normalizeCodec(stream.Codec))
    })
  }

  private getUnsupportedSubtitleStreamsForAvPlayer(): MediaStream[] {
    return this.subtitleStreams.filter((stream) => {
      const codecSupported = AV_PLAYER_SUPPORTED_SUBTITLE_CODECS.includes(this.normalizeCodec(stream.Codec))
      const isExternalTextSubtitle = stream.IsExternal === true &&
        stream.DeliveryMethod == SubtitleDeliveryMethod.External &&
        (stream.IsTextSubtitleStream === true || codecSupported)
      return !codecSupported || !isExternalTextSubtitle
    })
  }

  private describeStreamList(streams: MediaStream[]): string {
    return streams.map((stream) => {
      const codec = stream.Codec ?? 'unknown'
      const title = stream.DisplayTitle ?? stream.Title ?? ''
      if (title.length > 0) {
        return `${codec}(${title})`
      }
      return codec
    }).join('、')
  }

  getAvPlayerUnsupportedReasons(): string[] {
    const reasons: string[] = []
    if (this.isHdrVividStream()) {
      reasons.push('检测到 HDR Vivid，系统播放器当前仍有黑屏风险')
    }
    if (!this.isAvPlayerSupportedVideoContainer()) {
      reasons.push(`视频封装 ${this.sourceInfo.Container ?? 'unknown'} 不支持`)
    }
    if (!this.isAvPlayerSupportedVideoCodec()) {
      reasons.push(`视频编码 ${this.selectedVideoStream?.Codec ?? 'unknown'} 不支持`)
    }
    if (!this.isAvPlayerSupportedVideoRange()) {
      reasons.push(`视频动态范围 ${this.selectedVideoRangeType} 不支持`)
    }
    if (!this.isAvPlayerSupportedPixelFormat()) {
      reasons.push(`像素格式 ${this.selectedVideoStream?.PixelFormat ?? 'unknown'} 不支持`)
    }
    const unsupportedAudioStreams = this.getUnsupportedAudioStreamsForAvPlayer()
    if (unsupportedAudioStreams.length > 0) {
      reasons.push(`包含系统播放器不支持的音轨：${this.describeStreamList(unsupportedAudioStreams)}`)
    }
    const unsupportedSubtitleStreams = this.getUnsupportedSubtitleStreamsForAvPlayer()
    if (unsupportedSubtitleStreams.length > 0) {
      reasons.push(`包含系统播放器不支持的字幕：${this.describeStreamList(unsupportedSubtitleStreams)}`)
    }
    return reasons
  }

  private pickMarkedSubtitleStream(streams: MediaStream[]): MediaStream | undefined {
    return streams.find((stream) => stream.IsDefault === true)
      ?? streams.find((stream) => {
        const label = this.normalizeStreamLabel(stream.DisplayTitle)
        return label.includes('default') || label.includes('默认')
      })
      ?? streams.find((stream) => stream.IsForced === true)
      ?? streams.find((stream) => {
        const label = this.normalizeStreamLabel(stream.DisplayTitle)
        return label.includes('forced') || label.includes('强制')
      })
      ?? streams[0]
  }


  constructor(itemId: string,
    item: BaseItemDto,
    sourceInfo: MediaSourceInfo,
    playSessionId: string,
    liveStreamId?: string | null,
    maxStreamingBitrate?: number | null,
    startTimeTicks?: number | null,
    audioStreamIndex?: number | null,
    subtitleStreamIndex?: number | null,
  ) {
    this.itemId = itemId
    this.item = item
    this.sourceInfo = sourceInfo
    this.playSessionId = playSessionId
    this.liveStreamId = liveStreamId
    this.maxStreamingBitrate = maxStreamingBitrate
    this.startTimeTicks = startTimeTicks
    this.audioStreamIndex = audioStreamIndex
    this.subtitleStreamIndex = subtitleStreamIndex

    this.id = this.sourceInfo.Id ?? "Media source has no id"
    this.name = item.Name ?? sourceInfo.Name ?? ''
    if (sourceInfo.SupportsDirectPlay) {
      this.playMethod = PlayMethod.DirectPlay
    } else if (sourceInfo.SupportsDirectStream) {
      this.playMethod = PlayMethod.DirectStream
    } else if (sourceInfo.SupportsTranscoding) {
      this.playMethod = PlayMethod.Transcode
    } else {
      throw new Error(`No play method found for ${this.name} (${itemId})`)
    }
    this.runTimeTicks = this.sourceInfo.RunTimeTicks ?? 0
    this.runTimeMs = this.runTimeTicks / Constants.TICKS_PER_MILLISECOND
    this.mediaStreams = sourceInfo.MediaStreams ?? []
    const desiredAudioStreamIndex = audioStreamIndex ?? sourceInfo.DefaultAudioStreamIndex
    const desiredSubtitleStreamIndex = subtitleStreamIndex ?? sourceInfo.DefaultSubtitleStreamIndex
    let audio: MediaStream[] = []
    let subtitles: MediaStream[] = []
    let externalSubtitles: ExternalSubtitleStream[] = []
    for (const mediaStream of this.mediaStreams) {
      switch (mediaStream.Type) {
        case MediaStreamType.Video:
          // Always select the first available video stream
          if (this.selectedVideoStream == null) {
            this.selectedVideoStream = mediaStream
          }
          break
        case MediaStreamType.Audio:
          audio.push(mediaStream)
          if (mediaStream.Index == desiredAudioStreamIndex) {
            this.selectedAudioStream = mediaStream
          }
          break
        case MediaStreamType.Subtitle:
          subtitles.push(mediaStream)
          if (mediaStream.Index == desiredSubtitleStreamIndex) {
            this.selectedSubtitleStream = mediaStream
          }
          // External subtitles as specified by the deliveryMethod.
          // It is set to external either for external subtitle files or when transcoding.
          // In the latter case, subtitles are extracted from the source file by the server.
          if (mediaStream.DeliveryMethod == SubtitleDeliveryMethod.External) {
            let deliveryUrl = mediaStream.DeliveryUrl
            let mimeType = CodecHelpers.getSubtitleMimeType(mediaStream.Codec)
            if (deliveryUrl != null && mimeType != null) {
              const subtitleUrl = deliveryUrl.startsWith('http://') || deliveryUrl.startsWith('https://')
                ? deliveryUrl
                : ApiClient.Instance().createUrl(deliveryUrl)
              externalSubtitles.push(new ExternalSubtitleStream(
                mediaStream.Index!,
                subtitleUrl,
                mimeType,
                mediaStream.DisplayTitle ?? '',
                mediaStream.Language ?? Constants.LANGUAGE_UNDEFINED,
              ))
            }
          }
          break
      }
    }
    if (!this.selectedAudioStream) {
      this.selectedAudioStream = audio.find((stream) => stream.IsDefault === true) ?? audio[0]
    }
    if (subtitleStreamIndex !== -1 && !this.selectedSubtitleStream) {
      this.selectedSubtitleStream = this.pickMarkedSubtitleStream(subtitles)
    }
    this.audioStreams = audio
    this.subtitleStreams = subtitles
    this.externalSubtitleStreams = externalSubtitles
    console.warn(`[MediaSource] resolved audioSelected=${this.selectedAudioStream?.Index ?? 'none'}, subtitleDefault=${sourceInfo.DefaultSubtitleStreamIndex ?? 'null'}, requestedSubtitle=${subtitleStreamIndex ?? 'null'}, subtitleSelected=${this.selectedSubtitleStream?.Index ?? 'none'}, subtitleCount=${subtitles.length}`)
    console.info(`[MediaSource] video range=${this.selectedVideoRangeType}, dvProfile=${this.selectedVideoDvProfile ?? 'none'}, dolbyVision=${this.isDolbyVisionStream()}, hdrFallback=${this.canClientFallbackToHdr()}, strategy=${this.getVideoHdrStrategyLabel()}`)
    if (subtitles.length > 0) {
      console.info(`[MediaSource] subtitle selection defaultIndex=${sourceInfo.DefaultSubtitleStreamIndex ?? 'null'}, requestedIndex=${subtitleStreamIndex ?? 'null'}, selected=${this.selectedSubtitleStream?.Index ?? 'none'}, streams=${subtitles.map((stream) =>
        `${stream.Index}:${stream.DisplayTitle ?? ''}:default=${stream.IsDefault === true}:forced=${stream.IsForced === true}:external=${stream.IsExternal === true}`
      ).join(' | ')}`)
    }
    console.info(`[MediaSource] avplayer unsupported reasons=${this.getAvPlayerUnsupportedReasons().join(' | ') || 'none'}`)
  }

  //private _startTimeMs: number = 0

  get startTimeMs(): number {
    return (this.startTimeTicks ?? 0) / Constants.TICKS_PER_MILLISECOND
  }

  set startTimeMs(value: number) {
    this.startTimeTicks = value * Constants.TICKS_PER_MILLISECOND
  }

  /**
   * Select the specified [audio stream][stream] in the source.
   *
   * @param stream The stream to select.
   * @return true if the stream was found and selected, false otherwise.
   */
  selectAudioStream(stream: MediaStream): boolean {
    if(stream.Type !== MediaStreamType.Audio) return false
    if (this.mediaStreams[stream.Index!] !== stream) {
      return false
    }

    this.selectedAudioStream = stream
    return true
  }

  selectSubtitleStream(stream: MediaStream | undefined | null): boolean {
    if (!stream) {
      this.selectedSubtitleStream = undefined
      return true
    }
    if (stream.Type == MediaStreamType.Subtitle) {
      if (this.mediaStreams[stream.Index!] !== stream) {
        return false
      }
      this.selectedSubtitleStream = stream
      return true
    } else {
      throw new Error('stream.Type Require')
    }
  }

  toMediaMetadata(): AVSessionManager.AVMetadata {
    let image =
      ApiClient.Instance().basePath + new ImageUrlsApi().getItemImageUrlById(this.item.SeriesId ?? this.itemId)
    // 设置必要的媒体信息
    let metadata: AVSessionManager.AVMetadata = {
      assetId: this.itemId, // 由应用指定，用于标识应用媒体库里的媒体
      title: this.item.SeriesName ?? this.item.Name ?? "TITLE",
      mediaImage: image,
      subtitle: this.item.SeriesName ? (this.item.Name ?? '') : '',
      // 发现Cast+ Stream 和 DLNA协议设备，TYPE_CAST_PLUS_STREAM为默认必选。
      filter: avSession.ProtocolType.TYPE_CAST_PLUS_STREAM | avSession.ProtocolType.TYPE_DLNA,
      duration:this.runTimeMs,
    };
    return metadata
  }

  /**
   * Returns the index of the media stream within the embedded streams.
   * Useful for handling track selection in ExoPlayer, where embedded streams are mapped first.
   */
  getEmbeddedStreamIndex(mediaStream: MediaStream): number {
    let index = 0
    for (const stream of this.mediaStreams) {
      if(stream === mediaStream) return index
      if(!stream.IsExternal) return index++
    }
    throw new Error("Invalid media stream")
  }
}
