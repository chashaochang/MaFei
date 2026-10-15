import { Constants } from '../../../utils/Constants'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaProviderSession } from '../../../media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../../../media/MediaRef'
import {
  MediaPlaybackEngineHint,
  MediaPlaybackExternalSubtitle,
  MediaPlaybackItem,
  MediaPlaybackItemKind,
  MediaPlaybackMetadata,
  MediaPlaybackProtocol,
  MediaPlaybackQueueItem,
  MediaPlaybackRequestHeader,
  MediaPlaybackSource,
  MediaPlaybackSourceInfo,
  MediaPlaybackStream,
  MediaPlaybackStreamType,
  MediaPlaybackSubtitleDeliveryMethod,
  MediaPlaybackVideoRange
} from '../../../media/playback/MediaPlaybackModels'
import {
  FeiniuVideoCatalogImageAccess,
  FeiniuVideoCatalogMapper
} from '../catalog/FeiniuVideoCatalogMapper'
import {
  FnMediaItem,
  FnMediaItemDetailResp
} from '../catalog/FeiniuVideoDtos'
import {
  FnMediaStreamAudioInfo,
  FnMediaStreamFileInfo,
  FnMediaStreamSubtitleInfo,
  FnMediaStreamVideoInfo
} from './FeiniuVideoPlaybackDtos'

const FEINIU_SECONDS_TO_TICKS: number = 1000 * Constants.TICKS_PER_MILLISECOND

export class FeiniuVideoPlaybackAccess {
  readonly baseUrl: string
  readonly token: string
  readonly cookie: string
  readonly relay: boolean

  constructor(baseUrl: string, token: string = '', cookie: string = '', relay: boolean = false) {
    this.baseUrl = FeiniuVideoPlaybackMapper.normalizeBaseUrl(baseUrl)
    this.token = token.trim()
    this.cookie = cookie.trim()
    this.relay = relay
  }
}

export interface FeiniuVideoPlaybackSourceMapping {
  readonly ref: MediaRef
  readonly item: FnMediaItemDetailResp
  readonly file?: FnMediaStreamFileInfo
  readonly video?: FnMediaStreamVideoInfo
  readonly audios: FnMediaStreamAudioInfo[]
  readonly subtitles: FnMediaStreamSubtitleInfo[]
  readonly selectedAudio?: FnMediaStreamAudioInfo
  readonly selectedSubtitle?: FnMediaStreamSubtitleInfo
  readonly playSessionId: string
  readonly mediaGuid: string
  readonly videoUrl: string
  readonly directOriginal: boolean
  /** Explicit direct-link headers. Empty means do not fall back to Feiniu credentials. */
  readonly requestHeaders?: MediaPlaybackRequestHeader[]
  readonly multiConnectionAllowed?: boolean
  /** Only enabled for the official Feiniu STRM cloud direct-link exception. */
  readonly allowInsecureTls?: boolean
  readonly startTimeTicks: number
  readonly maxStreamingBitrate?: number
  readonly engineHint: MediaPlaybackEngineHint
  readonly seriesId?: string
  readonly seriesName?: string
}

export class FeiniuVideoPlaybackMapper {
  private readonly session: MediaProviderSession
  private readonly access: FeiniuVideoPlaybackAccess
  private readonly catalogMapper: FeiniuVideoCatalogMapper

  constructor(session: MediaProviderSession,
    access: FeiniuVideoPlaybackAccess = new FeiniuVideoPlaybackAccess(session.baseUrl)) {
    if (session.provider !== MediaProviderKind.FeiniuVideo) {
      throw new Error('Feiniu Video playback mapper requires a Feiniu Video session')
    }
    this.session = session
    this.access = access
    this.catalogMapper = new FeiniuVideoCatalogMapper(session, new FeiniuVideoCatalogImageAccess(
      access.baseUrl,
      access.token,
      access.cookie,
      access.relay
    ))
  }

  source(mapping: FeiniuVideoPlaybackSourceMapping): MediaPlaybackSource {
    const mappedItem = this.item(mapping.item, mapping.seriesId, mapping.seriesName)
    const streams: MediaPlaybackStream[] = []
    if (mapping.video) {
      streams.push(this.videoStream(mapping.video))
    }
    mapping.audios.forEach((audio: FnMediaStreamAudioInfo): void => {
      streams.push(this.audioStream(audio))
    })
    mapping.subtitles.forEach((subtitle: FnMediaStreamSubtitleInfo): void => {
      streams.push(this.subtitleStream(subtitle))
    })
    const sourceInfo: MediaPlaybackSourceInfo = {
      Id: mapping.mediaGuid,
      Name: mapping.item.title || mapping.file?.file_name || '',
      Container: this.container(mapping.file, mapping.video, mapping.videoUrl),
      Protocol: MediaPlaybackProtocol.Http,
      Path: mapping.directOriginal ? mapping.videoUrl : undefined,
      SupportsDirectPlay: mapping.directOriginal,
      SupportsDirectStream: false,
      SupportsTranscoding: !mapping.directOriginal,
      TranscodingUrl: mapping.directOriginal ? undefined : mapping.videoUrl,
      TranscodingSubProtocol: mapping.directOriginal ? undefined : this.transcodeProtocol(mapping.videoUrl),
      RunTimeTicks: this.durationTicks(mapping.item, mapping.video),
      MediaStreams: streams,
      DefaultAudioStreamIndex: mapping.selectedAudio?.index,
      DefaultSubtitleStreamIndex: mapping.selectedSubtitle?.index
    }
    const owner = new MediaRef(mapping.ref.provider, mapping.ref.accountScope, mapping.ref.id,
      this.refKind(mappedItem.Type))
    const externalSubtitles = this.externalSubtitles(mapping.subtitles)
    const requestHeaders = mapping.requestHeaders === undefined
      ? this.requestHeaders()
      : mapping.requestHeaders.slice()
    return new MediaPlaybackSource({
      ref: owner,
      item: mappedItem,
      sourceInfo: sourceInfo,
      playSessionId: mapping.playSessionId,
      maxStreamingBitrate: mapping.maxStreamingBitrate,
      startTimeTicks: mapping.startTimeTicks,
      audioStreamIndex: mapping.selectedAudio?.index,
      subtitleStreamIndex: mapping.selectedSubtitle?.index,
      requestHeaders: requestHeaders,
      externalSubtitles: externalSubtitles,
      metadata: this.metadata(owner, mapping.item, mapping.seriesName),
      serverUrl: this.access.baseUrl,
      capabilities: {
        framePreviewAvailable: mapping.directOriginal,
        danmakuAvailable: false,
        externalSubtitleAvailable: externalSubtitles.length > 0,
        transcodeCleanupAvailable: !mapping.directOriginal
      },
      engineHint: mapping.engineHint,
      multiConnectionAllowed: mapping.multiConnectionAllowed,
      allowInsecureTls: mapping.allowInsecureTls
    })
  }

  item(value: FnMediaItem, seriesId?: string, seriesName?: string): MediaPlaybackItem {
    return {
      Id: value.guid,
      Name: value.title || value.file_name || '',
      SeriesId: this.seriesId(value, seriesId),
      SeriesName: `${seriesName || FeiniuVideoCatalogMapper.seriesTitle(value)}`.trim() || undefined,
      Type: this.itemKind(value.type),
      IndexNumber: value.episode_number,
      RunTimeTicks: this.durationTicks(value),
      UserData: {
        Played: (value.watched || 0) > 0
      }
    }
  }

  queueItem(value: FnMediaItem, ordinal: number): MediaPlaybackQueueItem | undefined {
    const itemId = this.playableGuid(value)
    if (itemId.length === 0) {
      return undefined
    }
    const kind = this.itemKind(value.type)
    const owner = new MediaRef(this.session.provider, this.session.accountScope, itemId, this.refKind(kind))
    const summary = this.catalogMapper.summary(value)
    return {
      ref: owner,
      playIndex: value.episode_number ?? ordinal,
      name: value.title || value.file_name || '',
      seriesName: FeiniuVideoCatalogMapper.seriesTitle(value),
      image: summary.thumbnailImage || summary.primaryImage,
      durationTicks: this.durationTicks(value),
      played: (value.watched || 0) > 0,
      kind: kind
    }
  }

  videoStream(value: FnMediaStreamVideoInfo): MediaPlaybackStream {
    return {
      Index: value.index ?? 0,
      Type: MediaPlaybackStreamType.Video,
      Codec: value.codec_name || value.codec_type,
      DisplayTitle: value.title || value.resolution_type,
      Title: value.title,
      VideoRangeType: this.videoRange(value.color_range_type || value.color_range),
      PixelFormat: value.pix_fmt,
      BitDepth: value.bit_depth,
      Profile: value.profile,
      DvProfile: value.dv_profile,
      ColorTransfer: value.color_transfer,
      Width: value.width,
      Height: value.height,
      BitRate: value.bps,
      ReferenceFrameRate: this.numberValue(value.frame_rate),
      AverageFrameRate: this.numberValue(value.avg_frame_rate),
      RealFrameRate: this.numberValue(value.r_frame_rate),
      ColorPrimaries: value.color_primaries,
      ColorSpace: value.color_space
    }
  }

  audioStream(value: FnMediaStreamAudioInfo): MediaPlaybackStream {
    return {
      Index: value.index,
      Type: MediaPlaybackStreamType.Audio,
      Codec: value.codec_name || value.codec_type,
      DisplayTitle: this.audioTitle(value),
      Title: value.title,
      Language: value.language,
      Profile: value.profile,
      IsDefault: (value.is_default || 0) > 0,
      IsExternal: false,
      BitRate: value.bps
    }
  }

  subtitleStream(value: FnMediaStreamSubtitleInfo): MediaPlaybackStream {
    const external = this.isExternalSubtitle(value)
    return {
      Index: value.index,
      Type: MediaPlaybackStreamType.Subtitle,
      Codec: value.codec_name || value.codec_type || value.format,
      DisplayTitle: this.subtitleTitle(value),
      Title: value.title,
      Language: value.language,
      IsDefault: (value.is_default || 0) > 0,
      IsForced: (value.forced || 0) > 0,
      IsExternal: external,
      IsTextSubtitleStream: (value.is_bitmap || 0) === 0,
      DeliveryMethod: external
        ? MediaPlaybackSubtitleDeliveryMethod.External
        : MediaPlaybackSubtitleDeliveryMethod.Embed,
      DeliveryUrl: external ? this.subtitleUrl(value.guid || '') : undefined
    }
  }

  requestHeaders(): MediaPlaybackRequestHeader[] {
    const result: MediaPlaybackRequestHeader[] = []
    if (this.access.token.length > 0) {
      result.push({ name: 'Authorization', value: this.access.token })
    }
    const cookie = this.playbackCookie()
    if (cookie.length > 0) {
      result.push({ name: 'Cookie', value: cookie })
      result.push({ name: 'cookie', value: cookie })
    }
    return result
  }

  absoluteUrl(value: string): string {
    const normalized = value.trim()
    if (normalized.length === 0 || normalized.startsWith('http://') || normalized.startsWith('https://')) {
      return normalized
    }
    return `${this.access.baseUrl}${normalized.startsWith('/') ? '' : '/'}${normalized}`
  }

  directMediaUrl(mediaGuid: string): string {
    const normalized = mediaGuid.trim()
    return normalized.length > 0
      ? `${this.access.baseUrl}/v/api/v1/media/range/${encodeURIComponent(normalized)}`
      : ''
  }

  subtitleUrl(subtitleGuid: string): string {
    const normalized = this.normalizeSubtitleGuid(subtitleGuid)
    return normalized.length > 0
      ? `${this.access.baseUrl}/v/api/v1/subtitle/dl/${encodeURIComponent(normalized)}`
      : ''
  }

  relativePlayLink(value: string): string {
    const normalized = value.trim()
    if (normalized.startsWith(this.access.baseUrl)) {
      return normalized.substring(this.access.baseUrl.length)
    }
    return normalized
  }

  resumeTicks(item: FnMediaItem, playInfoSeconds: number = 0,
    explicitTicks?: number): number {
    if (explicitTicks !== undefined) {
      return Math.max(0, Math.floor(explicitTicks))
    }
    const durationSeconds = this.durationSeconds(item)
    const streamProgress = Math.max(0, Math.floor(item.media_stream?.progress || 0))
    const progressSeconds = durationSeconds > 0 && streamProgress > 0
      ? Math.floor(durationSeconds * Math.min(100, streamProgress) / 100)
      : 0
    const resumeSeconds = Math.max(
      0,
      Math.floor(item.ts || 0),
      Math.floor(item.media_stream?.current_time || 0),
      Math.floor(playInfoSeconds),
      progressSeconds
    )
    if (resumeSeconds <= 0 || (durationSeconds > 0 && resumeSeconds >= Math.max(0, durationSeconds - 8))) {
      return 0
    }
    return resumeSeconds * FEINIU_SECONDS_TO_TICKS
  }

  playableGuid(item: FnMediaItem): string {
    const playGuid = `${item.play_item_guid || ''}`.trim()
    return playGuid.length > 0 ? playGuid : `${item.guid || ''}`.trim()
  }

  normalizeSubtitleGuid(value: string): string {
    const normalized = value.trim()
    return normalized === '_no_display_' ? '' : normalized
  }

  static normalizeBaseUrl(value: string): string {
    let normalized = value.trim()
    while (normalized.endsWith('/')) {
      normalized = normalized.substring(0, normalized.length - 1)
    }
    return normalized
  }

  private metadata(owner: MediaRef, item: FnMediaItem, resolvedSeriesName?: string): MediaPlaybackMetadata {
    const summary = this.catalogMapper.summary(item)
    const seriesTitle = `${resolvedSeriesName || FeiniuVideoCatalogMapper.seriesTitle(item)}`.trim()
    return {
      assetId: owner.identity,
      title: seriesTitle.length > 0 ? seriesTitle : (item.title || item.file_name || 'TITLE'),
      subtitle: seriesTitle.length > 0 ? (item.title || '') : '',
      image: summary.backdropImage || summary.thumbnailImage || summary.primaryImage
    }
  }

  private externalSubtitles(values: FnMediaStreamSubtitleInfo[]): MediaPlaybackExternalSubtitle[] {
    const result: MediaPlaybackExternalSubtitle[] = []
    values.forEach((value: FnMediaStreamSubtitleInfo): void => {
      if (!this.isExternalSubtitle(value) || value.index === undefined) {
        return
      }
      const deliveryUrl = this.subtitleUrl(value.guid || '')
      if (deliveryUrl.length === 0) {
        return
      }
      result.push({
        index: value.index,
        deliveryUrl: deliveryUrl,
        mimeType: this.subtitleMimeType(value),
        displayTitle: this.subtitleTitle(value),
        language: value.language || Constants.LANGUAGE_UNDEFINED,
        headers: this.requestHeaders()
      })
    })
    return result
  }

  private playbackCookie(): string {
    let cookie = this.access.cookie
    if (this.access.token.length > 0 && cookie.indexOf('Trim-MC-token=') < 0) {
      cookie = this.appendCookie(cookie, `Trim-MC-token=${this.access.token}`)
    }
    if (this.access.relay && cookie.indexOf('mode=relay') < 0) {
      cookie = this.appendCookie(cookie, 'mode=relay')
    }
    return cookie
  }

  private appendCookie(current: string, value: string): string {
    const normalizedCurrent = current.trim().replace(/;+\s*$/, '')
    const normalizedValue = value.trim().replace(/^;+\s*/, '')
    if (normalizedCurrent.length === 0) {
      return normalizedValue
    }
    return normalizedValue.length > 0 ? `${normalizedCurrent}; ${normalizedValue}` : normalizedCurrent
  }

  private itemKind(value?: string): MediaPlaybackItemKind {
    const normalized = `${value || ''}`.trim().toLowerCase()
    if (normalized === 'movie') return MediaPlaybackItemKind.Movie
    if (normalized === 'tv' || normalized === 'series') return MediaPlaybackItemKind.Series
    if (normalized === 'season') return MediaPlaybackItemKind.Season
    if (normalized === 'episode') return MediaPlaybackItemKind.Episode
    if (normalized === 'livechannel') return MediaPlaybackItemKind.LiveTvChannel
    return MediaPlaybackItemKind.Video
  }

  private refKind(value?: MediaPlaybackItemKind): MediaRefKind {
    if (value === MediaPlaybackItemKind.Movie) return MediaRefKind.Movie
    if (value === MediaPlaybackItemKind.Series) return MediaRefKind.Series
    if (value === MediaPlaybackItemKind.Season) return MediaRefKind.Season
    if (value === MediaPlaybackItemKind.Episode) return MediaRefKind.Episode
    if (value === MediaPlaybackItemKind.LiveTvChannel || value === MediaPlaybackItemKind.TvChannel) {
      return MediaRefKind.LiveChannel
    }
    return MediaRefKind.Video
  }

  private videoRange(value?: string): MediaPlaybackVideoRange {
    const normalized = `${value || ''}`.trim().toLowerCase().replace(/[\s_-]/g, '')
    if (normalized.indexOf('dolbyvision') >= 0 || normalized.indexOf('dovi') >= 0) {
      return MediaPlaybackVideoRange.Dovi
    }
    if (normalized.indexOf('hdr10plus') >= 0 || normalized.indexOf('hdr10+') >= 0) {
      return MediaPlaybackVideoRange.Hdr10Plus
    }
    if (normalized.indexOf('hdr10') >= 0 || normalized === 'hdr') return MediaPlaybackVideoRange.Hdr10
    if (normalized.indexOf('hlg') >= 0) return MediaPlaybackVideoRange.Hlg
    if (normalized === 'sdr') return MediaPlaybackVideoRange.Sdr
    return MediaPlaybackVideoRange.Unknown
  }

  private seriesId(item: FnMediaItem, resolvedSeriesId?: string): string | undefined {
    const explicit = `${resolvedSeriesId || ''}`.trim()
    if (explicit.length > 0) return explicit
    const type = `${item.type || ''}`.trim().toLowerCase()
    if (type === 'tv' || type === 'series') return item.guid.trim() || undefined
    if (type === 'season') {
      const parent = `${item.parent_guid || ''}`.trim()
      const library = `${item.ancestor_guid || ''}`.trim()
      return parent.length > 0 && parent !== library ? parent : undefined
    }
    return undefined
  }

  private durationTicks(item: FnMediaItem, video?: FnMediaStreamVideoInfo): number {
    const seconds = Math.max(this.durationSeconds(item), Math.floor(video?.duration || 0))
    return seconds * FEINIU_SECONDS_TO_TICKS
  }

  private durationSeconds(item: FnMediaItem): number {
    const duration = Math.max(0, Math.floor(item.duration || 0))
    if (duration > 0) {
      return duration
    }
    return Math.max(0, Math.floor((item.runtime || 0) * 60))
  }

  private container(file: FnMediaStreamFileInfo | undefined,
    video: FnMediaStreamVideoInfo | undefined, videoUrl: string): string {
    const wrapper = `${video?.wrapper || ''}`.trim().toLowerCase()
    const fileSource = `${file?.file_name || file?.path || ''}`.trim()
    const strmReference = this.isStrmReference(fileSource)
    if (wrapper.length > 0 && wrapper !== 'strm' && !strmReference) {
      return wrapper
    }
    const source = (strmReference ? videoUrl : (fileSource || videoUrl)).split('?')[0]
    const lastSlash = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'))
    const dot = source.lastIndexOf('.')
    return dot > lastSlash ? source.substring(dot + 1).toLowerCase() : 'mp4'
  }

  private isStrmReference(value: string): boolean {
    return value.trim().toLowerCase().endsWith('.strm')
  }

  private transcodeProtocol(videoUrl: string): string {
    const normalized = videoUrl.toLowerCase()
    return normalized.indexOf('.m3u8') >= 0 ? 'hls' : 'http'
  }

  private audioTitle(value: FnMediaStreamAudioInfo): string {
    const title = `${value.title || ''}`.trim()
    if (title.length > 0) {
      return title
    }
    const parts: string[] = []
    if (`${value.language || ''}`.trim().length > 0) parts.push(`${value.language}`.trim())
    if (`${value.codec_name || ''}`.trim().length > 0) parts.push(`${value.codec_name}`.trim().toUpperCase())
    if ((value.channels || 0) > 0) parts.push(`${value.channels}声道`)
    return parts.length > 0 ? parts.join(' · ') : '音轨'
  }

  private subtitleTitle(value: FnMediaStreamSubtitleInfo): string {
    const title = `${value.title || ''}`.trim()
    if (title.length > 0) {
      return title
    }
    const parts: string[] = []
    if (`${value.language || ''}`.trim().length > 0) parts.push(`${value.language}`.trim())
    const codec = `${value.codec_name || value.format || ''}`.trim()
    if (codec.length > 0) parts.push(codec.toUpperCase())
    if (this.isExternalSubtitle(value)) parts.push('外挂')
    return parts.length > 0 ? parts.join(' · ') : '字幕'
  }

  private isExternalSubtitle(value: FnMediaStreamSubtitleInfo): boolean {
    return (value.is_external || 0) > 0 || (value.extra_file || 0) > 0
  }

  private subtitleMimeType(value: FnMediaStreamSubtitleInfo): string {
    const codec = `${value.codec_name || value.codec_type || value.format || ''}`.trim().toLowerCase()
    if (codec === 'vtt' || codec === 'webvtt') return 'text/vtt'
    if (codec === 'ass' || codec === 'ssa') return 'text/x-ssa'
    return 'application/x-subrip'
  }

  private numberValue(value?: number | string): number | undefined {
    const normalized = `${value ?? ''}`.trim()
    const separator = normalized.indexOf('/')
    if (separator > 0 && separator < normalized.length - 1) {
      const numerator = Number(normalized.substring(0, separator))
      const denominator = Number(normalized.substring(separator + 1))
      const ratio = denominator !== 0 ? numerator / denominator : 0
      return Number.isFinite(ratio) && ratio > 0 ? ratio : undefined
    }
    const parsed = Number(normalized)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
  }
}
