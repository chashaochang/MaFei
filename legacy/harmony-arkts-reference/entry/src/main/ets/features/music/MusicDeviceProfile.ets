import {
  DeviceProfile,
  DirectPlayProfile,
  DlnaProfileType,
  MediaStreamProtocol,
  ProfileCondition,
  TranscodingProfile
} from 'sdk/src/main/ets/generated-client/models'
import { Constants } from '../../utils/Constants'

export class MusicDeviceProfile {
  static create(): DeviceProfile {
    const aacDirectPlay: DirectPlayProfile = {
      Type: DlnaProfileType.Audio,
      Container: 'm4a,aac',
      AudioCodec: 'aac'
    }
    const mp3DirectPlay: DirectPlayProfile = {
      Type: DlnaProfileType.Audio,
      Container: 'mp3',
      AudioCodec: 'mp3'
    }
    const transcodeConditions: ProfileCondition[] = []
    const mp3Transcode: TranscodingProfile = {
      Type: DlnaProfileType.Audio,
      Container: 'mp3',
      AudioCodec: 'mp3',
      Protocol: MediaStreamProtocol.Http,
      Conditions: transcodeConditions
    }
    const profile: DeviceProfile = {
      Name: `${Constants.APP_INFO_NAME}-music-avplayer`,
      DirectPlayProfiles: [aacDirectPlay, mp3DirectPlay],
      TranscodingProfiles: [mp3Transcode],
      ContainerProfiles: [],
      CodecProfiles: [],
      SubtitleProfiles: [],
      MusicStreamingTranscodingBitrate: 384000
    }
    return profile
  }
}
