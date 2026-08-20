import { avSession } from '@kit.AVSessionKit'
import { MediaRef } from '../MediaRef'
import { MediaImageRef } from '../catalog/MediaCatalogModels'
import { Constants } from '../../utils/Constants'

const AV_PLAYER_SUPPORTED_VIDEO_CONTAINERS: string[] = ['mp4', 'm4v', 'mov', 'mkv', 'ts']
const AV_PLAYER_SUPPORTED_VIDEO_CODECS: string[] = ['h264', 'avc', 'hevc', 'h265']
const AV_PLAYER_SUPPORTED_AUDIO_CODECS: string[] = ['aac', 'mp3']
const AV_PLAYER_SUPPORTED_SUBTITLE_CODECS: string[] = ['srt', 'vtt', 'webvtt']

export enum MediaPlaybackItemKind {
  Unknown = 'Unknown',
  Video = 'Video',
  Movie = 'Movie',
  Series = 'Series',
  Season = 'Season',
  Episode = 'Episode',
  TvChannel = 'TvChannel',
  LiveTvChannel = 'LiveTvChannel'
}

export enum MediaPlaybackStreamType {
  Audio = 'Audio',
  Video = 'Video',
  Subtitle = 'Subtitle',
  EmbeddedImage = 'EmbeddedImage',
  Data = 'Data'
}

export enum MediaPlaybackPlayMethod {
  Transcode = 'Transcode',
  DirectStream = 'DirectStream',
  DirectPlay = 'DirectPlay'
}

export enum MediaPlaybackProtocol {
  File = 'File',
  Http = 'Http',
  Rtmp = 'Rtmp',
  Rtsp = 'Rtsp',
  Udp = 'Udp',
  Rtp = 'Rtp',
  Ftp = 'Ftp',
  Unknown = 'Unknown'
}

export enum MediaPlaybackSubtitleDeliveryMethod {
  Encode = 'Encode',
  Embed = 'Embed',
  External = 'External',
  Hls = 'Hls',
  Drop = 'Drop'
}

export enum MediaPlaybackVideoRange {
  Unknown = 'Unknown',
  Sdr = 'SDR',
  Hdr10 = 'HDR10',
  Hlg = 'HLG',
  Dovi = 'DOVI',
  DoviWithHdr10 = 'DOVIWithHDR10',
  DoviWithHlg = 'DOVIWithHLG',
  DoviWithSdr = 'DOVIWithSDR',
  Hdr10Plus = 'HDR10Plus'
}

export enum MediaPlaybackEngineHint {
  Auto = 'auto',
  PreferAvPlayer = 'prefer_av_player',
  PreferMpvHardware = 'prefer_mpv_hardware',
  PreferMpvSoftware = 'prefer_mpv_software'
}

export interface MediaPlaybackRequestHeader {
  readonly name: string
  readonly value: string
}

/** Provider-resolved HTTP resource that can be consumed without provider-specific clients. */
export interface MediaPlaybackHttpResource {
  readonly url: string
  readonly headers: MediaPlaybackRequestHeader[]
}

/** Optional player features advertised by the provider for this resolved source. */
export interface MediaPlaybackCapabilities {
  readonly framePreviewAvailable: boolean
  readonly danmakuAvailable: boolean
  readonly externalSubtitleAvailable: boolean
  readonly transcodeCleanupAvailable: boolean
}

export interface MediaPlaybackItemUserData {
  readonly Played?: boolean
}

/** Provider-neutral item metadata retained by the shared player and queue. */
export interface MediaPlaybackItem {
  readonly Id?: string
  readonly Name?: string
  readonly SeriesId?: string
  readonly SeriesName?: string
  readonly Type?: MediaPlaybackItemKind
  readonly IndexNumber?: number
  readonly RunTimeTicks?: number
  readonly UserData?: MediaPlaybackItemUserData
}

/** Provider-neutral audio, video, or subtitle track. */
export interface MediaPlaybackStream {
  readonly Index?: number
  readonly Type?: MediaPlaybackStreamType
  readonly Codec?: string
  readonly CodecTag?: string
  readonly DisplayTitle?: string
  readonly Title?: string
  readonly Language?: string
  readonly VideoRangeType?: MediaPlaybackVideoRange
  readonly PixelFormat?: string
  readonly BitDepth?: number
  readonly Profile?: string
  readonly DvProfile?: number
  readonly ColorTransfer?: string
  readonly VideoDoViTitle?: string
  readonly Comment?: string
  readonly IsDefault?: boolean
  readonly IsForced?: boolean
  readonly IsExternal?: boolean
  readonly IsTextSubtitleStream?: boolean
  readonly DeliveryMethod?: MediaPlaybackSubtitleDeliveryMethod
  readonly DeliveryUrl?: string
  readonly Width?: number
  readonly Height?: number
  readonly BitRate?: number
  readonly ReferenceFrameRate?: number
  readonly AverageFrameRate?: number
  readonly RealFrameRate?: number
  readonly ColorPrimaries?: string
  readonly ColorSpace?: string
}

/** Provider-neutral server source selected for playback. */
export interface MediaPlaybackSourceInfo {
  readonly Id?: string
  readonly Name?: string
  readonly Container?: string
  readonly Protocol?: MediaPlaybackProtocol
  readonly IsoType?: string
  readonly Path?: string
  readonly SupportsDirectPlay?: boolean
  readonly SupportsDirectStream?: boolean
  readonly SupportsTranscoding?: boolean
  readonly TranscodingUrl?: string
  readonly TranscodingSubProtocol?: string
  readonly RunTimeTicks?: number
  readonly MediaStreams?: MediaPlaybackStream[]
  readonly DefaultAudioStreamIndex?: number
  readonly DefaultSubtitleStreamIndex?: number
}

export interface MediaPlaybackExternalSubtitle {
  readonly index: number
  readonly deliveryUrl: string
  readonly mimeType: string
  readonly displayTitle: string
  readonly language: string
  readonly headers: MediaPlaybackRequestHeader[]
}

export interface MediaPlaybackMetadata {
  readonly assetId: string
  readonly title: string
  readonly subtitle: string
  readonly image?: MediaImageRef
}

export interface MediaPlaybackResume {
  readonly startTimeTicks: number
}

export interface MediaPlaybackTranscodeContext {
  readonly playSessionId: string
  readonly liveStreamId?: string
  readonly active: boolean
}

export interface MediaPlaybackQueueItem {
  readonly ref: MediaRef
  readonly playIndex: number
  readonly name: string
  readonly seriesName: string
  readonly image?: MediaImageRef
  readonly durationTicks: number
  readonly played: boolean
  readonly kind: MediaPlaybackItemKind
}

export interface MediaPlaybackQueue {
  readonly items: MediaPlaybackQueueItem[]
  readonly refs: MediaRef[]
  readonly currentIndex: number
}

export interface MediaPlaybackQuality {
  readonly maxHeight: number
  readonly bitrate: number
}

export interface MediaPlaybackPreparedStreams {
  readonly videoUrl: string
  readonly videoHeaders: MediaPlaybackRequestHeader[]
  readonly externalSubtitles: MediaPlaybackExternalSubtitle[]
}

export interface MediaPlaybackSourceOptions {
  readonly ref: MediaRef
  readonly item: MediaPlaybackItem
  readonly sourceInfo: MediaPlaybackSourceInfo
  readonly playSessionId: string
  readonly liveStreamId?: string
  readonly maxStreamingBitrate?: number
  readonly startTimeTicks?: number
  readonly audioStreamIndex?: number
  readonly subtitleStreamIndex?: number
  readonly requestHeaders?: MediaPlaybackRequestHeader[]
  readonly externalSubtitles?: MediaPlaybackExternalSubtitle[]
  readonly metadata: MediaPlaybackMetadata
  readonly serverUrl: string
  readonly danmakuResource?: MediaPlaybackHttpResource
  readonly capabilities?: MediaPlaybackCapabilities
  readonly engineHint?: MediaPlaybackEngineHint
}

@ObservedV2
export class MediaPlaybackSource {
  readonly ref: MediaRef
  readonly itemId: string
  readonly item: MediaPlaybackItem
  readonly sourceInfo: MediaPlaybackSourceInfo
  readonly playSessionId: string
  readonly liveStreamId?: string
  maxStreamingBitrate?: number
  private startTimeTicks: number
  audioStreamIndex?: number
  subtitleStreamIndex?: number
  readonly id: string
  readonly name: string
  readonly playMethod: MediaPlaybackPlayMethod
  readonly runTimeTicks: number
  readonly runTimeMs: number
  mediaStreams: MediaPlaybackStream[]
  audioStreams: MediaPlaybackStream[]
  subtitleStreams: MediaPlaybackStream[]
  readonly externalSubtitleStreams: MediaPlaybackExternalSubtitle[]
  readonly requestHeaders: MediaPlaybackRequestHeader[]
  readonly metadata: MediaPlaybackMetadata
  readonly serverUrl: string
  readonly danmakuResource?: MediaPlaybackHttpResource
  readonly capabilities: MediaPlaybackCapabilities
  readonly engineHint: MediaPlaybackEngineHint
  readonly resume: MediaPlaybackResume
  readonly transcodeContext: MediaPlaybackTranscodeContext
  private _selectedVideoStream?: MediaPlaybackStream

  private set selectedVideoStream(value: MediaPlaybackStream | undefined) {
    this._selectedVideoStream = value
  }

  get selectedVideoStream(): MediaPlaybackStream | undefined {
    return this._selectedVideoStream
  }

  @Trace _selectedAudioStream?: MediaPlaybackStream

  private set selectedAudioStream(value: MediaPlaybackStream | undefined) {
    this._selectedAudioStream = value
  }

  get selectedAudioStream(): MediaPlaybackStream | undefined {
    return this._selectedAudioStream
  }

  @Trace _selectedSubtitleStream?: MediaPlaybackStream

  private set selectedSubtitleStream(value: MediaPlaybackStream | undefined) {
    this._selectedSubtitleStream = value
  }

  get selectedSubtitleStream(): MediaPlaybackStream | undefined {
    return this._selectedSubtitleStream
  }

  constructor(options: MediaPlaybackSourceOptions) {
    this.ref = options.ref
    this.itemId = options.ref.id
    this.item = options.item
    this.sourceInfo = options.sourceInfo
    this.playSessionId = options.playSessionId
    this.liveStreamId = options.liveStreamId
    this.maxStreamingBitrate = options.maxStreamingBitrate
    this.startTimeTicks = options.startTimeTicks || 0
    this.audioStreamIndex = options.audioStreamIndex
    this.subtitleStreamIndex = options.subtitleStreamIndex
    this.id = options.sourceInfo.Id || 'Media source has no id'
    this.name = options.item.Name || options.sourceInfo.Name || ''
    this.playMethod = this.resolvePlayMethod(options.sourceInfo)
    this.runTimeTicks = options.sourceInfo.RunTimeTicks || options.item.RunTimeTicks || 0
    this.runTimeMs = this.runTimeTicks / Constants.TICKS_PER_MILLISECOND
    this.mediaStreams = options.sourceInfo.MediaStreams ? options.sourceInfo.MediaStreams.slice() : []
    this.requestHeaders = options.requestHeaders ? options.requestHeaders.slice() : []
    this.externalSubtitleStreams = options.externalSubtitles ? options.externalSubtitles.slice() : []
    this.metadata = options.metadata
    this.serverUrl = options.serverUrl
    this.danmakuResource = options.danmakuResource ? {
      url: options.danmakuResource.url,
      headers: options.danmakuResource.headers.slice()
    } : undefined
    this.engineHint = options.engineHint || MediaPlaybackEngineHint.Auto
    this.resume = { startTimeTicks: this.startTimeTicks }
    this.transcodeContext = {
      playSessionId: this.playSessionId,
      liveStreamId: this.liveStreamId,
      active: this.usesServerTranscoding()
    }
    this.capabilities = options.capabilities || {
      framePreviewAvailable: false,
      danmakuAvailable: false,
      externalSubtitleAvailable: this.externalSubtitleStreams.length > 0,
      transcodeCleanupAvailable: this.transcodeContext.active
    }

    const desiredAudioStreamIndex = options.audioStreamIndex ?? options.sourceInfo.DefaultAudioStreamIndex
    const desiredSubtitleStreamIndex = options.subtitleStreamIndex ?? options.sourceInfo.DefaultSubtitleStreamIndex
    const audio: MediaPlaybackStream[] = []
    const subtitles: MediaPlaybackStream[] = []
    for (const mediaStream of this.mediaStreams) {
      switch (mediaStream.Type) {
        case MediaPlaybackStreamType.Video:
          if (!this.selectedVideoStream) {
            this.selectedVideoStream = mediaStream
          }
          break
        case MediaPlaybackStreamType.Audio:
          audio.push(mediaStream)
          if (mediaStream.Index === desiredAudioStreamIndex) {
            this.selectedAudioStream = mediaStream
          }
          break
        case MediaPlaybackStreamType.Subtitle:
          subtitles.push(mediaStream)
          if (mediaStream.Index === desiredSubtitleStreamIndex) {
            this.selectedSubtitleStream = mediaStream
          }
          break
        default:
          break
      }
    }
    if (!this.selectedAudioStream) {
      this.selectedAudioStream = audio.find((stream: MediaPlaybackStream): boolean => stream.IsDefault === true) ||
        audio[0]
    }
    if (options.subtitleStreamIndex !== -1 && !this.selectedSubtitleStream) {
      this.selectedSubtitleStream = this.pickMarkedSubtitleStream(subtitles)
    }
    this.audioStreams = audio
    this.subtitleStreams = subtitles
  }

  private resolvePlayMethod(sourceInfo: MediaPlaybackSourceInfo): MediaPlaybackPlayMethod {
    if (sourceInfo.SupportsDirectPlay) {
      return MediaPlaybackPlayMethod.DirectPlay
    }
    if (sourceInfo.SupportsDirectStream) {
      return MediaPlaybackPlayMethod.DirectStream
    }
    if (sourceInfo.SupportsTranscoding) {
      return MediaPlaybackPlayMethod.Transcode
    }
    throw new Error(`No play method found for ${sourceInfo.Name || this.itemId}`)
  }

  get selectedAudioStreamIndex(): number | undefined {
    return this.selectedAudioStream?.Index
  }

  get selectedSubtitleStreamIndex(): number {
    return this.selectedSubtitleStream?.Index ?? -1
  }

  get selectedVideoRangeType(): string {
    return this.selectedVideoStream?.VideoRangeType || MediaPlaybackVideoRange.Unknown
  }

  get selectedVideoDvProfile(): number | null {
    return this.selectedVideoStream?.DvProfile ?? null
  }

  get startTimeMs(): number {
    return this.startTimeTicks / Constants.TICKS_PER_MILLISECOND
  }

  set startTimeMs(value: number) {
    this.startTimeTicks = value * Constants.TICKS_PER_MILLISECOND
  }

  isDolbyVisionStream(): boolean {
    const range = this.selectedVideoStream?.VideoRangeType
    return range === MediaPlaybackVideoRange.Dovi ||
      range === MediaPlaybackVideoRange.DoviWithHdr10 ||
      range === MediaPlaybackVideoRange.DoviWithHlg ||
      range === MediaPlaybackVideoRange.DoviWithSdr ||
      this.selectedVideoDvProfile !== null
  }

  canClientFallbackToHdr(): boolean {
    const range = this.selectedVideoStream?.VideoRangeType
    return this.isDolbyVisionStream() ||
      range === MediaPlaybackVideoRange.DoviWithHdr10 ||
      range === MediaPlaybackVideoRange.DoviWithHlg ||
      this.selectedVideoDvProfile === 8
  }

  getVideoHdrStrategyLabel(): string {
    if (!this.isDolbyVisionStream()) {
      return this.selectedVideoRangeType
    }
    return `DoVi-P${this.selectedVideoDvProfile ?? '?'} -> MPV direct play preferred`
  }

  isIsoStaticPlaybackFallback(): boolean {
    return this.playMethod === MediaPlaybackPlayMethod.Transcode &&
      this.sourceInfo.Protocol === MediaPlaybackProtocol.File &&
      this.normalizeContainer(this.sourceInfo.Container) === 'iso'
  }

  usesServerTranscoding(): boolean {
    return this.playMethod === MediaPlaybackPlayMethod.Transcode && !this.isIsoStaticPlaybackFallback()
  }

  getEffectivePlayMethod(): MediaPlaybackPlayMethod {
    return this.isIsoStaticPlaybackFallback() ? MediaPlaybackPlayMethod.DirectPlay : this.playMethod
  }

  getEffectivePlayMethodLabel(): string {
    return `${this.getEffectivePlayMethod()}`
  }

  isHdrVividStream(): boolean {
    const hdrHintText = this.collectHdrHintText()
    return hdrHintText.includes('hdr vivid') || hdrHintText.includes('hdrvivid') || hdrHintText.includes('vivid')
  }

  isHdrPreferredForAvPlayer(): boolean {
    const range = this.selectedVideoStream?.VideoRangeType
    return range === MediaPlaybackVideoRange.Hdr10 ||
      range === MediaPlaybackVideoRange.Hdr10Plus ||
      range === MediaPlaybackVideoRange.Hlg
  }

  isHighChromaHdrPixelFormat(): boolean {
    const pixelFormat = this.normalizePixelFormat(this.selectedVideoStream?.PixelFormat)
    return pixelFormat.includes('444') || pixelFormat.includes('422') ||
      pixelFormat.includes('rgb') || pixelFormat.includes('gbr')
  }

  shouldPreferMpvSoftwareDecode(): boolean {
    return this.engineHint === MediaPlaybackEngineHint.PreferMpvSoftware || this.isHighChromaHdrPixelFormat()
  }

  getAvPlayerUnsupportedReasons(): string[] {
    const reasons: string[] = []
    if (this.isHdrVividStream()) {
      reasons.push('检测到 HDR Vivid，系统播放器当前仍有黑屏风险')
    }
    if (!AV_PLAYER_SUPPORTED_VIDEO_CONTAINERS.includes(this.normalizeContainer(this.sourceInfo.Container))) {
      reasons.push(`视频封装 ${this.sourceInfo.Container || 'unknown'} 不支持`)
    }
    if (!AV_PLAYER_SUPPORTED_VIDEO_CODECS.includes(this.normalizeCodec(this.selectedVideoStream?.Codec))) {
      reasons.push(`视频编码 ${this.selectedVideoStream?.Codec || 'unknown'} 不支持`)
    }
    if (!this.isAvPlayerSupportedVideoRange()) {
      reasons.push(`视频动态范围 ${this.selectedVideoRangeType} 不支持`)
    }
    if (!this.isAvPlayerSupportedPixelFormat()) {
      reasons.push(`像素格式 ${this.selectedVideoStream?.PixelFormat || 'unknown'} 不支持`)
    }
    const unsupportedAudioStreams = this.audioStreams.filter((stream: MediaPlaybackStream): boolean =>
      !AV_PLAYER_SUPPORTED_AUDIO_CODECS.includes(this.normalizeCodec(stream.Codec)))
    if (unsupportedAudioStreams.length > 0) {
      reasons.push(`包含系统播放器不支持的音轨：${this.describeStreamList(unsupportedAudioStreams)}`)
    }
    const unsupportedSubtitleStreams = this.subtitleStreams.filter((stream: MediaPlaybackStream): boolean => {
      const codecSupported = AV_PLAYER_SUPPORTED_SUBTITLE_CODECS.includes(this.normalizeCodec(stream.Codec))
      const externalText = stream.IsExternal === true &&
        stream.DeliveryMethod === MediaPlaybackSubtitleDeliveryMethod.External &&
        (stream.IsTextSubtitleStream === true || codecSupported)
      return !codecSupported || !externalText
    })
    if (unsupportedSubtitleStreams.length > 0) {
      reasons.push(`包含系统播放器不支持的字幕：${this.describeStreamList(unsupportedSubtitleStreams)}`)
    }
    return reasons
  }

  selectAudioStream(stream: MediaPlaybackStream): boolean {
    if (stream.Type !== MediaPlaybackStreamType.Audio) {
      return false
    }
    const matched = this.audioStreams.find((item: MediaPlaybackStream): boolean => item.Index === stream.Index) ||
      this.mediaStreams.find((item: MediaPlaybackStream): boolean =>
        item.Type === MediaPlaybackStreamType.Audio && item.Index === stream.Index)
    if (matched) {
      this.selectedAudioStream = matched
      return true
    }
    if (!this.isIsoStaticPlaybackFallback()) {
      return false
    }
    if (!this.audioStreams.some((item: MediaPlaybackStream): boolean => item.Index === stream.Index)) {
      this.audioStreams = [...this.audioStreams, stream]
    }
    this.selectedAudioStream = stream
    return true
  }

  selectSubtitleStream(stream?: MediaPlaybackStream | null): boolean {
    if (!stream) {
      this.selectedSubtitleStream = undefined
      return true
    }
    if (stream.Type !== MediaPlaybackStreamType.Subtitle) {
      throw new Error('stream.Type Require')
    }
    const matched = this.subtitleStreams.find((item: MediaPlaybackStream): boolean => item.Index === stream.Index) ||
      this.mediaStreams.find((item: MediaPlaybackStream): boolean =>
        item.Type === MediaPlaybackStreamType.Subtitle && item.Index === stream.Index)
    if (matched) {
      this.selectedSubtitleStream = matched
      return true
    }
    if (!this.isIsoStaticPlaybackFallback()) {
      return false
    }
    if (!this.subtitleStreams.some((item: MediaPlaybackStream): boolean => item.Index === stream.Index)) {
      this.subtitleStreams = [...this.subtitleStreams, stream]
    }
    this.selectedSubtitleStream = stream
    return true
  }

  toMediaMetadata(): avSession.AVMetadata {
    const metadata: avSession.AVMetadata = {
      assetId: this.metadata.assetId,
      title: this.metadata.title,
      subtitle: this.metadata.subtitle,
      filter: avSession.ProtocolType.TYPE_CAST_PLUS_STREAM | avSession.ProtocolType.TYPE_DLNA,
      duration: this.runTimeMs
    }
    if (this.metadata.image && this.metadata.image.headers.length === 0) {
      metadata.mediaImage = this.metadata.image.url
    }
    return metadata
  }

  getEmbeddedStreamIndex(mediaStream: MediaPlaybackStream): number {
    let index = 0
    for (const stream of this.mediaStreams) {
      if (stream === mediaStream) {
        return index
      }
      if (!stream.IsExternal) {
        return index++
      }
    }
    throw new Error('Invalid media stream')
  }

  private pickMarkedSubtitleStream(streams: MediaPlaybackStream[]): MediaPlaybackStream | undefined {
    return streams.find((stream: MediaPlaybackStream): boolean => stream.IsDefault === true) ||
      streams.find((stream: MediaPlaybackStream): boolean => {
        const label = this.normalizeStreamLabel(stream.DisplayTitle)
        return label.includes('default') || label.includes('默认')
      }) ||
      streams.find((stream: MediaPlaybackStream): boolean => stream.IsForced === true) ||
      streams.find((stream: MediaPlaybackStream): boolean => {
        const label = this.normalizeStreamLabel(stream.DisplayTitle)
        return label.includes('forced') || label.includes('强制')
      }) || streams[0]
  }

  private normalizeStreamLabel(value?: string | null): string {
    return value ? value.trim().toLowerCase() : ''
  }

  private normalizeCodec(value?: string | null): string {
    return this.normalizeStreamLabel(value).replace(/[^a-z0-9+_-]/g, '')
  }

  private normalizeContainer(value?: string | null): string {
    const normalized = this.normalizeStreamLabel(value)
    return normalized === 'mpegts' ? 'ts' : normalized
  }

  private normalizePixelFormat(value?: string | null): string {
    return this.normalizeStreamLabel(value).replace(/[^a-z0-9]/g, '')
  }

  private collectHdrHintText(): string {
    const stream = this.selectedVideoStream
    return [stream?.ColorTransfer, stream?.VideoDoViTitle, stream?.Title, stream?.DisplayTitle, stream?.Comment]
      .filter((value?: string | null): boolean => !!value && value.length > 0)
      .join(' ')
      .toLowerCase()
  }

  private isAvPlayerSupportedVideoRange(): boolean {
    const range = this.selectedVideoStream?.VideoRangeType || MediaPlaybackVideoRange.Unknown
    return range === MediaPlaybackVideoRange.Unknown || range === MediaPlaybackVideoRange.Sdr ||
      range === MediaPlaybackVideoRange.Hdr10 || range === MediaPlaybackVideoRange.Hdr10Plus ||
      range === MediaPlaybackVideoRange.Hlg
  }

  private isAvPlayerSupportedPixelFormat(): boolean {
    const pixelFormat = this.normalizePixelFormat(this.selectedVideoStream?.PixelFormat)
    if (pixelFormat.length === 0) {
      return true
    }
    if (pixelFormat.includes('444') || pixelFormat.includes('422') ||
      pixelFormat.includes('rgb') || pixelFormat.includes('gbr')) {
      return false
    }
    return pixelFormat.includes('420') || pixelFormat === 'nv12' || pixelFormat.includes('p010')
  }

  private describeStreamList(streams: MediaPlaybackStream[]): string {
    return streams.map((stream: MediaPlaybackStream): string => {
      const codec = stream.Codec || 'unknown'
      const title = stream.DisplayTitle || stream.Title || ''
      return title.length > 0 ? `${codec}(${title})` : codec
    }).join('、')
  }
}
