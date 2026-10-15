import { ImageUrlsApi } from 'sdk'
import {
  BaseItemDto,
  BaseItemKind,
  ImageType,
  MediaProtocol,
  MediaSourceInfo,
  MediaStream,
  MediaStreamType,
  SubtitleDeliveryMethod,
  VideoRangeType
} from 'sdk/src/main/ets/generated-client/models'
import { MediaProviderSession } from '../MediaProviderSession'
import { MediaRef, MediaRefKind } from '../MediaRef'
import { MediaImageKind, MediaImageRef } from '../catalog/MediaCatalogModels'
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
  MediaPlaybackSourceOptions,
  MediaPlaybackStream,
  MediaPlaybackStreamType,
  MediaPlaybackSubtitleDeliveryMethod,
  MediaPlaybackVideoRange
} from '../playback/MediaPlaybackModels'
import { CodecHelpers } from '../../player/deviceprofile/CodecHelpers'
import { Constants } from '../../utils/Constants'

function normalizeBaseUrl(value: string): string {
  let normalized = value.trim()
  while (normalized.endsWith('/')) {
    normalized = normalized.slice(0, -1)
  }
  return normalized
}

export class JellyfinPlaybackMapper {
  private readonly session: MediaProviderSession
  private readonly baseUrl: string
  private readonly requestHeaders: MediaPlaybackRequestHeader[]
  private readonly imageApi: ImageUrlsApi = new ImageUrlsApi()

  constructor(session: MediaProviderSession, authorizationHeader: string = '') {
    this.session = session
    this.baseUrl = normalizeBaseUrl(session.baseUrl)
    this.requestHeaders = authorizationHeader.length > 0 ? [{
      name: 'Authorization',
      value: authorizationHeader
    }] : []
  }

  source(requestRef: MediaRef, item: BaseItemDto, sourceInfo: MediaSourceInfo,
    playSessionId: string, liveStreamId?: string | null, maxStreamingBitrate?: number | null,
    startTimeTicks?: number | null, audioStreamIndex?: number | null,
    subtitleStreamIndex?: number | null, engineHint: MediaPlaybackEngineHint = MediaPlaybackEngineHint.Auto):
    MediaPlaybackSource {
    return new MediaPlaybackSource(this.sourceOptions(requestRef, item, sourceInfo, playSessionId,
      liveStreamId, maxStreamingBitrate, startTimeTicks, audioStreamIndex, subtitleStreamIndex, engineHint))
  }

  sourceOptions(requestRef: MediaRef, item: BaseItemDto, sourceInfo: MediaSourceInfo,
    playSessionId: string, liveStreamId?: string | null, maxStreamingBitrate?: number | null,
    startTimeTicks?: number | null, audioStreamIndex?: number | null,
    subtitleStreamIndex?: number | null, engineHint: MediaPlaybackEngineHint = MediaPlaybackEngineHint.Auto):
    MediaPlaybackSourceOptions {
    const mappedItem = this.item(item)
    const mappedSourceInfo = this.sourceInfo(sourceInfo)
    const externalSubtitles = this.externalSubtitles(mappedSourceInfo.MediaStreams || [])
    const ref = new MediaRef(requestRef.provider, requestRef.accountScope, requestRef.id,
      this.refKind(mappedItem.Type))
    return {
      ref: ref,
      item: mappedItem,
      sourceInfo: mappedSourceInfo,
      playSessionId: playSessionId,
      liveStreamId: liveStreamId || undefined,
      maxStreamingBitrate: maxStreamingBitrate ?? undefined,
      startTimeTicks: startTimeTicks ?? undefined,
      audioStreamIndex: audioStreamIndex ?? undefined,
      subtitleStreamIndex: subtitleStreamIndex ?? undefined,
      requestHeaders: this.requestHeaders,
      externalSubtitles: externalSubtitles,
      metadata: this.metadata(ref, item, sourceInfo),
      serverUrl: this.baseUrl,
      danmakuResource: {
        url: `${this.baseUrl}/api/danmu/${encodeURIComponent(requestRef.id)}`,
        headers: this.requestHeaders.slice()
      },
      capabilities: {
        framePreviewAvailable: mappedSourceInfo.SupportsDirectPlay === true ||
          mappedSourceInfo.SupportsDirectStream === true,
        danmakuAvailable: true,
        externalSubtitleAvailable: externalSubtitles.length > 0,
        transcodeCleanupAvailable: mappedSourceInfo.SupportsTranscoding === true
      },
      engineHint: engineHint
    }
  }

  item(value: BaseItemDto): MediaPlaybackItem {
    return {
      Id: value.Id ?? undefined,
      Name: value.Name ?? undefined,
      SeriesId: value.SeriesId ?? undefined,
      SeriesName: value.SeriesName ?? undefined,
      Type: this.itemKind(value.Type),
      IndexNumber: value.IndexNumber ?? undefined,
      RunTimeTicks: value.RunTimeTicks ?? undefined,
      UserData: value.UserData ? { Played: value.UserData.Played } : undefined
    }
  }

  stream(value: MediaStream): MediaPlaybackStream {
    return {
      Index: value.Index ?? undefined,
      Type: this.streamType(value.Type),
      Codec: value.Codec ?? undefined,
      CodecTag: value.CodecTag ?? undefined,
      DisplayTitle: value.DisplayTitle ?? undefined,
      Title: value.Title ?? undefined,
      Language: value.Language ?? undefined,
      VideoRangeType: this.videoRange(value.VideoRangeType),
      PixelFormat: value.PixelFormat ?? undefined,
      BitDepth: value.BitDepth ?? undefined,
      Profile: value.Profile ?? undefined,
      DvProfile: value.DvProfile ?? undefined,
      ColorTransfer: value.ColorTransfer ?? undefined,
      VideoDoViTitle: value.VideoDoViTitle ?? undefined,
      Comment: value.Comment ?? undefined,
      IsDefault: value.IsDefault ?? undefined,
      IsForced: value.IsForced ?? undefined,
      IsExternal: value.IsExternal ?? undefined,
      IsTextSubtitleStream: value.IsTextSubtitleStream ?? undefined,
      DeliveryMethod: this.subtitleDelivery(value.DeliveryMethod),
      DeliveryUrl: value.DeliveryUrl ?? undefined,
      Width: value.Width ?? undefined,
      Height: value.Height ?? undefined,
      BitRate: value.BitRate ?? undefined,
      ReferenceFrameRate: value.ReferenceFrameRate ?? undefined,
      AverageFrameRate: value.AverageFrameRate ?? undefined,
      RealFrameRate: value.RealFrameRate ?? undefined,
      ColorPrimaries: value.ColorPrimaries ?? undefined,
      ColorSpace: value.ColorSpace ?? undefined
    }
  }

  sourceInfo(value: MediaSourceInfo): MediaPlaybackSourceInfo {
    return {
      Id: value.Id ?? undefined,
      Name: value.Name ?? undefined,
      Container: value.Container ?? undefined,
      Protocol: this.protocol(value.Protocol),
      IsoType: value.IsoType ?? undefined,
      Path: value.Path ?? undefined,
      SupportsDirectPlay: value.SupportsDirectPlay ?? undefined,
      SupportsDirectStream: value.SupportsDirectStream ?? undefined,
      SupportsTranscoding: value.SupportsTranscoding ?? undefined,
      TranscodingUrl: value.TranscodingUrl ?? undefined,
      TranscodingSubProtocol: value.TranscodingSubProtocol ?? undefined,
      RunTimeTicks: value.RunTimeTicks ?? undefined,
      MediaStreams: (value.MediaStreams || []).map((stream: MediaStream): MediaPlaybackStream => this.stream(stream)),
      DefaultAudioStreamIndex: value.DefaultAudioStreamIndex ?? undefined,
      DefaultSubtitleStreamIndex: value.DefaultSubtitleStreamIndex ?? undefined
    }
  }

  queueItem(value: BaseItemDto): MediaPlaybackQueueItem | undefined {
    const id = value.Id || ''
    if (id.length === 0) {
      return undefined
    }
    const itemKind = this.itemKind(value.Type)
    const owner = new MediaRef(this.session.provider, this.session.accountScope, id, this.refKind(itemKind))
    return {
      ref: owner,
      playIndex: value.IndexNumber ?? Number.NaN,
      name: value.Name || '',
      seriesName: value.SeriesName || '',
      image: this.queueImage(value, owner),
      durationTicks: value.RunTimeTicks || 0,
      played: value.UserData?.Played || false,
      kind: itemKind
    }
  }

  private externalSubtitles(streams: MediaPlaybackStream[]): MediaPlaybackExternalSubtitle[] {
    const result: MediaPlaybackExternalSubtitle[] = []
    for (const stream of streams) {
      if (stream.Type !== MediaPlaybackStreamType.Subtitle ||
        stream.DeliveryMethod !== MediaPlaybackSubtitleDeliveryMethod.External ||
        stream.Index === undefined || !stream.DeliveryUrl) {
        continue
      }
      const mimeType = CodecHelpers.getSubtitleMimeType(stream.Codec)
      if (!mimeType) {
        continue
      }
      result.push({
        index: stream.Index,
        deliveryUrl: this.absoluteUrl(stream.DeliveryUrl),
        mimeType: mimeType,
        displayTitle: stream.DisplayTitle || '',
        language: stream.Language || Constants.LANGUAGE_UNDEFINED,
        headers: this.requestHeaders.slice()
      })
    }
    return result
  }

  private metadata(owner: MediaRef, item: BaseItemDto, sourceInfo: MediaSourceInfo): MediaPlaybackMetadata {
    const imageId = item.SeriesId || owner.id
    return {
      assetId: owner.id,
      title: item.SeriesName || item.Name || sourceInfo.Name || 'TITLE',
      subtitle: item.SeriesName ? (item.Name || '') : '',
      image: this.imageRef(owner, imageId, MediaImageKind.Primary, 'player-metadata-primary')
    }
  }

  private queueImage(item: BaseItemDto, owner: MediaRef): MediaImageRef | undefined {
    if (item.Type === BaseItemKind.Episode && item.ParentThumbItemId) {
      return this.imageRef(owner, item.ParentThumbItemId, MediaImageKind.Primary, 'player-queue-primary')
    }
    const imageId = item.Id || ''
    return imageId.length > 0 ? this.imageRef(owner, imageId, MediaImageKind.Primary,
      'player-queue-primary') : undefined
  }

  private imageRef(owner: MediaRef, imageId: string, kind: MediaImageKind,
    variant: string): MediaImageRef {
    return new MediaImageRef({
      owner: owner,
      kind: kind,
      url: this.absoluteUrl(this.imageApi.getItemImageUrlById(imageId, ImageType.Primary)),
      variant: variant,
      headers: this.requestHeaders
    })
  }

  private absoluteUrl(value: string): string {
    if (value.startsWith('http://') || value.startsWith('https://')) {
      return value
    }
    return value.startsWith('/') ? `${this.baseUrl}${value}` : `${this.baseUrl}/${value}`
  }

  private itemKind(value?: BaseItemKind): MediaPlaybackItemKind {
    switch (value) {
      case BaseItemKind.Movie:
        return MediaPlaybackItemKind.Movie
      case BaseItemKind.Series:
        return MediaPlaybackItemKind.Series
      case BaseItemKind.Season:
        return MediaPlaybackItemKind.Season
      case BaseItemKind.Episode:
        return MediaPlaybackItemKind.Episode
      case BaseItemKind.TvChannel:
        return MediaPlaybackItemKind.TvChannel
      case BaseItemKind.LiveTvChannel:
        return MediaPlaybackItemKind.LiveTvChannel
      case BaseItemKind.Video:
        return MediaPlaybackItemKind.Video
      default:
        return MediaPlaybackItemKind.Unknown
    }
  }

  private refKind(value?: MediaPlaybackItemKind): MediaRefKind {
    switch (value) {
      case MediaPlaybackItemKind.Movie:
        return MediaRefKind.Movie
      case MediaPlaybackItemKind.Series:
        return MediaRefKind.Series
      case MediaPlaybackItemKind.Season:
        return MediaRefKind.Season
      case MediaPlaybackItemKind.Episode:
        return MediaRefKind.Episode
      case MediaPlaybackItemKind.LiveTvChannel:
      case MediaPlaybackItemKind.TvChannel:
        return MediaRefKind.LiveChannel
      case MediaPlaybackItemKind.Video:
        return MediaRefKind.Video
      default:
        return MediaRefKind.Unknown
    }
  }

  private streamType(value?: MediaStreamType): MediaPlaybackStreamType | undefined {
    switch (value) {
      case MediaStreamType.Audio:
        return MediaPlaybackStreamType.Audio
      case MediaStreamType.Video:
        return MediaPlaybackStreamType.Video
      case MediaStreamType.Subtitle:
        return MediaPlaybackStreamType.Subtitle
      case MediaStreamType.EmbeddedImage:
        return MediaPlaybackStreamType.EmbeddedImage
      case MediaStreamType.Data:
        return MediaPlaybackStreamType.Data
      default:
        return undefined
    }
  }

  private protocol(value?: MediaProtocol): MediaPlaybackProtocol {
    switch (value) {
      case MediaProtocol.File:
        return MediaPlaybackProtocol.File
      case MediaProtocol.Http:
        return MediaPlaybackProtocol.Http
      case MediaProtocol.Rtmp:
        return MediaPlaybackProtocol.Rtmp
      case MediaProtocol.Rtsp:
        return MediaPlaybackProtocol.Rtsp
      case MediaProtocol.Udp:
        return MediaPlaybackProtocol.Udp
      case MediaProtocol.Rtp:
        return MediaPlaybackProtocol.Rtp
      case MediaProtocol.Ftp:
        return MediaPlaybackProtocol.Ftp
      default:
        return MediaPlaybackProtocol.Unknown
    }
  }

  private subtitleDelivery(value?: SubtitleDeliveryMethod): MediaPlaybackSubtitleDeliveryMethod | undefined {
    switch (value) {
      case SubtitleDeliveryMethod.Encode:
        return MediaPlaybackSubtitleDeliveryMethod.Encode
      case SubtitleDeliveryMethod.Embed:
        return MediaPlaybackSubtitleDeliveryMethod.Embed
      case SubtitleDeliveryMethod.External:
        return MediaPlaybackSubtitleDeliveryMethod.External
      case SubtitleDeliveryMethod.Hls:
        return MediaPlaybackSubtitleDeliveryMethod.Hls
      case SubtitleDeliveryMethod.Drop:
        return MediaPlaybackSubtitleDeliveryMethod.Drop
      default:
        return undefined
    }
  }

  private videoRange(value?: VideoRangeType): MediaPlaybackVideoRange {
    switch (value) {
      case VideoRangeType.Sdr:
        return MediaPlaybackVideoRange.Sdr
      case VideoRangeType.Hdr10:
        return MediaPlaybackVideoRange.Hdr10
      case VideoRangeType.Hdr10Plus:
        return MediaPlaybackVideoRange.Hdr10Plus
      case VideoRangeType.Hlg:
        return MediaPlaybackVideoRange.Hlg
      case VideoRangeType.Dovi:
        return MediaPlaybackVideoRange.Dovi
      case VideoRangeType.DoviWithHdr10:
        return MediaPlaybackVideoRange.DoviWithHdr10
      case VideoRangeType.DoviWithHlg:
        return MediaPlaybackVideoRange.DoviWithHlg
      case VideoRangeType.DoviWithSdr:
        return MediaPlaybackVideoRange.DoviWithSdr
      default:
        return MediaPlaybackVideoRange.Unknown
    }
  }
}
