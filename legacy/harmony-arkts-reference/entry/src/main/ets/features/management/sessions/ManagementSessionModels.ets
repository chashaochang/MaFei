import { ManagementErrorCode, ManagementPermissionState, ManagementSectionStatus } from '../ManagementModels';

export const MANAGEMENT_SESSION_POLL_INTERVAL_MS: number = 2000
export const MANAGEMENT_SESSION_RECENT_SECONDS: number = 95

export enum ManagementPlaybackKind {
  Idle,
  DirectPlay,
  DirectStream,
  Remux,
  Transcode
}

export enum ManagementSessionAction {
  None,
  PlayPause,
  Stop,
  Message
}

export class ManagementSessionItem {
  id: string = ''
  serverId: string = ''
  deviceId: string = ''
  userId: string = ''
  userName: string = ''
  client: string = ''
  deviceName: string = ''
  deviceType: string = ''
  applicationVersion: string = ''
  remoteEndPoint: string = ''
  lastActivityDate: string = ''
  supportsMediaControl: boolean = false
  supportsRemoteControl: boolean = false
  itemId: string = ''
  itemName: string = ''
  itemSubtitle: string = ''
  productionYear: number = 0
  imageItemId: string = ''
  imageUrl: string = ''
  runtimeTicks: number = 0
  positionTicks: number = 0
  paused: boolean = false
  playbackKind: ManagementPlaybackKind = ManagementPlaybackKind.Idle
  transcodingPercent: number = 0
  bitrate: number = 0
  framerate: number = 0
  container: string = ''
  videoCodec: string = ''
  audioCodec: string = ''
  resolution: string = ''
  transcodeReasons: string[] = []

  get hasMedia(): boolean {
    return this.itemId.length > 0
  }

  get controllable(): boolean {
    return this.serverId.length > 0 && this.supportsRemoteControl
  }
}

export class ManagementSessionPresentation {
  static formatActivityDate(value: string): string {
    if (!value) {
      return ''
    }
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
      return value
    }
    const month = (date.getMonth() + 1).toString().padStart(2, '0')
    const day = date.getDate().toString().padStart(2, '0')
    const hour = date.getHours().toString().padStart(2, '0')
    const minute = date.getMinutes().toString().padStart(2, '0')
    return `${month}-${day} ${hour}:${minute}`
  }

  static transcodeReasonResourceName(reason: string): string {
    switch (reason) {
      case 'ContainerNotSupported':
      case 'ContainerBitrateExceedsLimit':
        return 'management_session_transcode_reason_container'
      case 'VideoCodecNotSupported':
      case 'VideoProfileNotSupported':
      case 'VideoLevelNotSupported':
      case 'VideoResolutionNotSupported':
      case 'VideoBitDepthNotSupported':
      case 'VideoFramerateNotSupported':
      case 'RefFramesNotSupported':
      case 'AnamorphicVideoNotSupported':
      case 'InterlacedVideoNotSupported':
      case 'VideoBitrateNotSupported':
      case 'VideoRangeTypeNotSupported':
      case 'VideoCodecTagNotSupported':
        return 'management_session_transcode_reason_video'
      case 'AudioCodecNotSupported':
      case 'AudioIsExternal':
      case 'SecondaryAudioNotSupported':
      case 'AudioChannelsNotSupported':
      case 'AudioProfileNotSupported':
      case 'AudioSampleRateNotSupported':
      case 'AudioBitDepthNotSupported':
      case 'AudioBitrateNotSupported':
        return 'management_session_transcode_reason_audio'
      case 'SubtitleCodecNotSupported':
        return 'management_session_transcode_reason_subtitle'
      case 'UnknownVideoStreamInfo':
      case 'UnknownAudioStreamInfo':
        return 'management_session_transcode_reason_stream_info'
      case 'DirectPlayError':
        return 'management_session_transcode_reason_direct_play'
      default:
        return 'management_session_transcode_reason_other'
    }
  }
}

export class ManagementSessionActionResult {
  ok: boolean = false
  errorCode: ManagementErrorCode = ManagementErrorCode.None
  message: string = ''
}

export class ManagementSessionViewState {
  permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  status: ManagementSectionStatus = ManagementSectionStatus.Loading
  errorCode: ManagementErrorCode = ManagementErrorCode.None
  message: string = ''
}
