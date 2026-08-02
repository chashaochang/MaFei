import { ImageUrlsApi } from 'sdk';
import { BaseItemDto, ImageType, SessionInfoDto, TranscodingInfo } from 'sdk/src/main/ets/generated-client/models';
import { ApiClient } from '../../../network/ApiClient';
import {
  MANAGEMENT_SESSION_RECENT_SECONDS,
  ManagementPlaybackKind,
  ManagementSessionItem
} from './ManagementSessionModels';

interface ManagementSessionTranscodingReasons {
  TranscodeReasons?: string[]
}

function managementSessionTranscodeReasons(transcoding: TranscodingInfo | undefined): string[] {
  if (!transcoding) {
    return []
  }
  const source: ManagementSessionTranscodingReasons =
    transcoding as Object as ManagementSessionTranscodingReasons
  return source.TranscodeReasons || []
}

export class ManagementSessionMapper {
  static filterRecent(items: SessionInfoDto[], nowMs: number = Date.now()): SessionInfoDto[] {
    const thresholdMs = MANAGEMENT_SESSION_RECENT_SECONDS * 1000
    return items.filter((item: SessionInfoDto) => {
      const activeAt = Date.parse(item.LastActivityDate || '')
      if (!Number.isFinite(activeAt) || nowMs - activeAt > thresholdMs) {
        return false
      }
      return item.NowPlayingItem !== undefined || (item.UserId || '').length > 0
    })
  }

  static mapMany(items: SessionInfoDto[], nowMs: number = Date.now()): ManagementSessionItem[] {
    return ManagementSessionMapper.filterRecent(items, nowMs)
      .map((item: SessionInfoDto): ManagementSessionItem => ManagementSessionMapper.map(item))
  }

  static map(session: SessionInfoDto): ManagementSessionItem {
    const result = new ManagementSessionItem()
    const media = session.NowPlayingItem
    const transcoding = session.TranscodingInfo
    result.id = session.Id || ''
    result.serverId = session.ServerId || ''
    result.deviceId = session.DeviceId || ''
    result.userId = session.UserId || ''
    result.userName = session.UserName || ''
    result.client = session.Client || ''
    result.deviceName = session.DeviceName || session.DeviceType || ''
    result.deviceType = session.DeviceType || ''
    result.applicationVersion = session.ApplicationVersion || ''
    result.remoteEndPoint = session.RemoteEndPoint || ''
    result.lastActivityDate = session.LastActivityDate || ''
    result.supportsMediaControl = session.SupportsMediaControl === true
    result.supportsRemoteControl = session.SupportsRemoteControl === true
    result.itemId = media?.Id || ''
    result.itemName = media?.Name || ''
    result.itemSubtitle = ManagementSessionMapper.mediaSubtitle(media)
    result.productionYear = media?.ProductionYear || 0
    result.imageItemId = ManagementSessionMapper.imageItemId(media)
    result.imageUrl = ManagementSessionMapper.imageUrl(media, result.imageItemId)
    result.runtimeTicks = media?.RunTimeTicks || 0
    result.positionTicks = session.PlayState?.PositionTicks || 0
    result.paused = session.PlayState?.IsPaused === true
    result.playbackKind = ManagementSessionMapper.playbackKind(transcoding, session.PlayState?.PlayMethod || '')
    result.transcodingPercent = Math.max(0, Math.min(100, transcoding?.CompletionPercentage || 0))
    result.bitrate = transcoding?.Bitrate || 0
    result.framerate = transcoding?.Framerate || 0
    result.container = transcoding?.Container || ''
    result.videoCodec = transcoding?.VideoCodec || ''
    result.audioCodec = transcoding?.AudioCodec || ''
    result.resolution = transcoding?.Width && transcoding?.Height ?
      `${transcoding.Width}x${transcoding.Height}` : ''
    result.transcodeReasons = managementSessionTranscodeReasons(transcoding)
    return result
  }

  static playbackKind(transcoding: TranscodingInfo | undefined, playMethod: string): ManagementPlaybackKind {
    if (transcoding && (transcoding.IsVideoDirect === true || !transcoding.VideoCodec) &&
      transcoding.IsAudioDirect === true) {
      return ManagementPlaybackKind.Remux
    }
    if (transcoding?.IsVideoDirect === true) {
      return ManagementPlaybackKind.DirectStream
    }
    if (playMethod === 'Transcode') {
      return ManagementPlaybackKind.Transcode
    }
    if (playMethod === 'DirectStream' || playMethod === 'DirectPlay') {
      return ManagementPlaybackKind.DirectPlay
    }
    return ManagementPlaybackKind.Idle
  }

  private static mediaSubtitle(media: BaseItemDto | undefined): string {
    if (!media) {
      return ''
    }
    const parts: string[] = []
    if (media.SeriesName) {
      parts.push(media.SeriesName)
      if (media.ParentIndexNumber !== undefined && media.IndexNumber !== undefined) {
        parts.push(`S${media.ParentIndexNumber} E${media.IndexNumber}`)
      }
    } else if (media.Album) {
      parts.push(media.Album)
    } else if (media.Artists && media.Artists.length > 0) {
      parts.push(media.Artists.join(', '))
    }
    return parts.join(' · ')
  }

  private static imageItemId(media: BaseItemDto | undefined): string {
    if (!media) {
      return ''
    }
    if (media.ImageTags && media.ImageTags['Primary']) {
      return media.Id || ''
    }
    return media.ParentThumbItemId || media.ParentPrimaryImageItemId || media.Id || ''
  }

  private static imageUrl(media: BaseItemDto | undefined, imageItemId: string): string {
    if (!media || !imageItemId) {
      return ''
    }
    const imageApi = new ImageUrlsApi()
    let imageType: ImageType = ImageType.Primary
    let tag: string = ''
    if (imageItemId === media.ParentThumbItemId) {
      imageType = ImageType.Thumb
      tag = media.ParentThumbImageTag || ''
    } else if (imageItemId === media.ParentPrimaryImageItemId) {
      tag = media.ParentPrimaryImageTag || ''
    } else if (imageItemId === media.Id) {
      tag = media.ImageTags?.[ImageType.Primary] || ''
    }
    const relativeUrl = tag ?
      imageApi.getItemImageUrlById(imageItemId, imageType, { tag: tag }) :
      imageApi.getItemImageUrlById(imageItemId, imageType)
    const basePath: string = ApiClient.isInitialized() ? ApiClient.Instance().basePath : ''
    return basePath + relativeUrl
  }
}
