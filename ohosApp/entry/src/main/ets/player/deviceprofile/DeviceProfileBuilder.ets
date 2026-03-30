import { DeviceProfile } from "sdk";
import {
  CodecProfile,
  CodecType,
  DirectPlayProfile,
  DlnaProfileType,
  ProfileConditionType,
  ProfileConditionValue,
  SubtitleDeliveryMethod,
  SubtitleProfile,
  TranscodingProfile
} from "sdk/src/main/ets/generated-client/models";
import { Constants } from "../../utils/Constants";
import { PlayerEngineType } from "../../entity/PlayerEngineType";

/**
 * Taken from Jellyfin Web:
 * https://github.com/jellyfin/jellyfin-web/blob/de690740f03c0568ba3061c4c586bd78b375d882/src/scripts/browserDeviceProfile.js#L276
 */
const MAX_STREAMING_BITRATE = 120000000

/**
 * Taken from Jellyfin Web:
 * https://github.com/jellyfin/jellyfin-web/blob/de690740f03c0568ba3061c4c586bd78b375d882/src/scripts/browserDeviceProfile.js#L372
 */
const MAX_STATIC_BITRATE = 100000000

/**
 * Taken from Jellyfin Web:
 * https://github.com/jellyfin/jellyfin-web/blob/de690740f03c0568ba3061c4c586bd78b375d882/src/scripts/browserDeviceProfile.js#L373
 */
const MAX_MUSIC_TRANSCODING_BITRATE = 384000

/**
 * List of PCM codecs supported by default
 */
const  PCM_CODECS: Array<string> = [
  "pcm_s8",
  "pcm_s16be",
  "pcm_s16le",
  "pcm_s24le",
  "pcm_s32le",
  "pcm_f32le",
  "pcm_alaw",
  "pcm_mulaw"
];

/**
 * IMPORTANT: Must have same length as SUPPORTED_CONTAINER_FORMATS,
 * as it maps the codecs to the containers with the same index!
 */
const  AVAILABLE_AUDIO_CODECS: Array<Array<string>> = [
// mp4
  ["mp1", "mp2", "mp3", "aac", "alac", "ac3", "opus"],

  // fmp4
  ["mp3", "aac", "ac3", "eac3"],

  // webm
  ["vorbis", "opus"],

  // mkv
  [...PCM_CODECS, "mp1", "mp2", "mp3", "aac", "vorbis", "opus",
    "flac", "alac", "ac3", "eac3", "dts", "mlp", "truehd"],

  // mp3
  ["mp3"],

  // ogg
  ["vorbis", "opus", "flac"],

  // wav
  PCM_CODECS,

  // mpegts
  [...PCM_CODECS, "mp1", "mp2", "mp3", "aac", "ac3",
    "eac3", "dts", "mlp", "truehd"],

  // flv
  ["mp3", "aac"],

  // aac
  ["aac"],

  // flac
  ["flac"],

  // 3gp
  ["3gpp", "aac", "flac"]
];
/**
 * List of container formats supported by default
 *
 * IMPORTANT: Don't change without updating AVAILABLE_VIDEO_CODECS and AVAILABLE_AUDIO_CODECS
 */
const SUPPORTED_CONTAINER_FORMATS: Array<string> = [
  "mp4",
  "fmp4",
  "webm",
  "mkv",
  "mp3",
  "ogg",
  "wav",
  "mpegts",
  "flv",
  "aac",
  "flac",
  "3gp"
];

const hevcExcludeDOVI: CodecProfile = {
  Type: CodecType.Video,
  Codec: 'hevc',
  Conditions: [
    { Condition: ProfileConditionType.Equals, Property: ProfileConditionValue.VideoBitDepth, Value: '8', IsRequired: true },
    { Condition: ProfileConditionType.NotEquals, Property: ProfileConditionValue.VideoRangeType, Value: 'DOVI', IsRequired: true },
    // 可选：再要求 SDR 或 Unknown
    // { Condition: ProfileConditionType.EqualsAny, Property: ProfileConditionValue.VideoRangeType, Value: 'SDR,Unknown', IsRequired: true },
    // 可选：排除 dvhe
    // { Condition: ProfileConditionType.NotEquals, Property: ProfileConditionValue.VideoCodecTag, Value: 'dvhe', IsRequired: true },
  ],
};

const HEVC_SAFE_VIDEO_RANGES = 'Unknown|SDR|HDR10|HDR10Plus|HLG'
const HEVC_MPV_DIRECT_PLAY_VIDEO_RANGES = `${HEVC_SAFE_VIDEO_RANGES}|DOVI|DOVIWithHDR10|DOVIWithHLG|DOVIWithSDR`

const avHevcVideoRangeProfile: CodecProfile = {
  Type: CodecType.Video,
  Codec: 'hevc',
  Conditions: [
    {
      Condition: ProfileConditionType.EqualsAny,
      Property: ProfileConditionValue.VideoRangeType,
      Value: HEVC_SAFE_VIDEO_RANGES,
      IsRequired: false,
    }
  ],
}

const mpvHevcVideoRangeProfile: CodecProfile = {
  Type: CodecType.Video,
  Codec: 'hevc',
  Conditions: [
    {
      Condition: ProfileConditionType.EqualsAny,
      Property: ProfileConditionValue.VideoRangeType,
      Value: HEVC_MPV_DIRECT_PLAY_VIDEO_RANGES,
      IsRequired: false,
    }
  ],
}

/***
 *
 */
export class DeviceProfileBuilder {
  private readonly avCodecProfiles: Array<CodecProfile> = [
    avHevcVideoRangeProfile
  ]

  private readonly mpvCodecProfiles: Array<CodecProfile> = [
    mpvHevcVideoRangeProfile
  ]

  private readonly avTranscodingProfiles: Array<TranscodingProfile> = [
    {
      Type: DlnaProfileType.Video,
      Container: "ts",
      VideoCodec: "h264",
      AudioCodec: "aac,mp3",
      Protocol: "hls",
      Conditions: [],
    },
    {
      Type: DlnaProfileType.Video,
      Container: "mp4",
      VideoCodec: "h264",
      AudioCodec: "aac,mp3",
      Protocol: "hls",
      Conditions: [],
    },
    {
      Type: DlnaProfileType.Audio,
      Container: "mp3",
      AudioCodec: "mp3",
      Protocol: "http",
      Conditions: [],
    },
  ]

  private readonly commonTranscodingProfiles: Array<TranscodingProfile> = [
    {
      Type: DlnaProfileType.Video,
      Container: "ts",
      VideoCodec: "h264",
      AudioCodec: "aac,mp3,ac3,eac3",
      Protocol: "hls",
      Conditions: [],
    },
    {
      Type: DlnaProfileType.Video,
      Container: "mp4",
      VideoCodec: "h264",
      AudioCodec: "aac,mp3,ac3,eac3",
      Protocol: "hls",
      Conditions: [],
    },
    {
      Type: DlnaProfileType.Audio,
      Container: "mp3",
      AudioCodec: "mp3",
      Protocol: "http",
      Conditions: [],
    },
  ]

  // OpenHarmony Media Kit documents AVPlayer playback support as:
  // video: mp4/mkv/ts + H.264/H.265, audio: m4a/aac/mp3/ogg/wav/amr, subtitles: srt/webvtt.
  private readonly avDirectPlayProfiles: Array<DirectPlayProfile> = [
    {
      Container: "mp4,mkv,ts",
      Type: DlnaProfileType.Video,
      VideoCodec: "h264,hevc",
      AudioCodec: "aac,mp3",
    },
    {
      Container: "m4a,aac",
      Type: DlnaProfileType.Audio,
      AudioCodec: "aac",
    },
    {
      Container: "mp3",
      Type: DlnaProfileType.Audio,
      AudioCodec: "mp3",
    },
    {
      Container: "ogg",
      Type: DlnaProfileType.Audio,
      AudioCodec: "vorbis",
    },
    {
      Container: "wav",
      Type: DlnaProfileType.Audio,
      AudioCodec: "pcm_s16le",
    },
    {
      Container: "amr",
      Type: DlnaProfileType.Audio,
      AudioCodec: "amr_nb,amr_wb",
    }
  ]

  private readonly ijkDirectPlayProfiles: Array<DirectPlayProfile> = [
    {
      Container: "mp4,m4v,mov,mkv,flv,ts,mpegts,3gp",
      Type: DlnaProfileType.Video,
      VideoCodec: "h264,hevc,mpeg4,mpeg2video,h263",
      AudioCodec: "aac,mp3,ac3,eac3,flac,opus,vorbis",
    },
    {
      Container: "mp3,aac,m4a,ogg,wav,flac",
      Type: DlnaProfileType.Audio,
      AudioCodec: "mp3,aac,flac,opus,vorbis,pcm_s16le",
    }
  ]

  private readonly mpvDirectPlayProfiles: Array<DirectPlayProfile> = [
    {
      Container: "mp4,m4v,mov,mkv,webm,ts,mpegts,flv,avi,3gp",
      Type: DlnaProfileType.Video,
      VideoCodec: "h264,hevc,mpeg4,mpeg2video,h263,vp8,vp9,av1,vc1,wmv3,theora",
      AudioCodec: [...PCM_CODECS, "mp1", "mp2", "mp3", "aac", "vorbis", "opus", "flac", "alac", "ac3", "eac3", "dts", "mlp", "truehd"].join(","),
    },
    {
      Container: "mp3,aac,m4a,ogg,opus,wav,flac,webm",
      Type: DlnaProfileType.Audio,
      AudioCodec: [...PCM_CODECS, "mp3", "aac", "vorbis", "opus", "flac", "alac", "ac3", "eac3", "dts"].join(","),
    }
  ]

  private readonly avSubtitleProfiles: Array<SubtitleProfile> = [
    {
      Format: 'srt',
      Method: SubtitleDeliveryMethod.External
    },
    {
      Format: 'vtt',
      Method: SubtitleDeliveryMethod.External
    }
  ]

  private readonly ijkSubtitleProfiles: Array<SubtitleProfile> = [
    {
      Format: 'ass',
      Method: SubtitleDeliveryMethod.Embed
    },
    {
      Format: 'srt',
      Method: SubtitleDeliveryMethod.Embed
    },
    {
      Format: 'vtt',
      Method: SubtitleDeliveryMethod.External
    }
  ]

  private readonly mpvSubtitleProfiles: Array<SubtitleProfile> = [
    {
      Format: 'ass',
      Method: SubtitleDeliveryMethod.Embed
    },
    {
      Format: 'ssa',
      Method: SubtitleDeliveryMethod.Embed
    },
    {
      Format: 'srt',
      Method: SubtitleDeliveryMethod.Embed
    },
    {
      Format: 'vtt',
      Method: SubtitleDeliveryMethod.Embed
    },
    {
      Format: 'pgs',
      Method: SubtitleDeliveryMethod.Embed
    },
    {
      Format: 'pgssub',
      Method: SubtitleDeliveryMethod.Embed
    }
  ]

  private createProfile(nameSuffix: string, directPlayProfiles: Array<DirectPlayProfile>,
    subtitleProfiles: Array<SubtitleProfile>,
    transcodingProfiles: Array<TranscodingProfile> = this.commonTranscodingProfiles,
    codecProfiles: Array<CodecProfile> = []): DeviceProfile {
    return {
      Name: `${Constants.APP_INFO_NAME}-${nameSuffix}`,
      DirectPlayProfiles: directPlayProfiles,
      TranscodingProfiles: transcodingProfiles,
      ContainerProfiles: [],
      CodecProfiles: codecProfiles,
      SubtitleProfiles: subtitleProfiles,
      MaxStreamingBitrate: MAX_STREAMING_BITRATE,
      MaxStaticBitrate: MAX_STATIC_BITRATE,
      MusicStreamingTranscodingBitrate: MAX_MUSIC_TRANSCODING_BITRATE,
    }
  }

  getDeviceProfile(playerEngineType: PlayerEngineType): DeviceProfile {
    if (playerEngineType === PlayerEngineType.AV) {
      return this.createProfile('avplayer', this.avDirectPlayProfiles, this.avSubtitleProfiles, this.avTranscodingProfiles,
        this.avCodecProfiles)
    }
    if (playerEngineType === PlayerEngineType.MPV) {
      return this.createProfile('mpv', this.mpvDirectPlayProfiles, this.mpvSubtitleProfiles, this.commonTranscodingProfiles,
        this.mpvCodecProfiles)
    }
    return this.createProfile('ijkplayer', this.ijkDirectPlayProfiles, this.ijkSubtitleProfiles, this.commonTranscodingProfiles,
      [hevcExcludeDOVI])
  }

  getLiveTvProfile(): DeviceProfile{
    return {
      "MaxStreamingBitrate": 120000000,
      "MaxStaticBitrate": 100000000,
      "MusicStreamingTranscodingBitrate": 384000,
      "DirectPlayProfiles": [
        {
          "Container": "webm",
          "Type": "Video",
          "VideoCodec": "vp8,vp9,av1",
          "AudioCodec": "vorbis,opus"
        },
        {
          "Container": "mp4,m4v",
          "Type": "Video",
          "VideoCodec": "h264,hevc,vp9,av1",
          "AudioCodec": "aac,mp3,mp2,opus,flac,vorbis"
        },
        {
          "Container": "mov",
          "Type": "Video",
          "VideoCodec": "h264",
          "AudioCodec": "aac,mp3,mp2,opus,flac,vorbis"
        },
        {
          "Container": "opus",
          "Type": "Audio"
        },
        {
          "Container": "webm",
          "AudioCodec": "opus",
          "Type": "Audio"
        },
        {
          "Container": "ts",
          "AudioCodec": "mp3",
          "Type": "Audio"
        },
        {
          "Container": "mp3",
          "Type": "Audio"
        },
        {
          "Container": "aac",
          "Type": "Audio"
        },
        {
          "Container": "m4a",
          "AudioCodec": "aac",
          "Type": "Audio"
        },
        {
          "Container": "m4b",
          "AudioCodec": "aac",
          "Type": "Audio"
        },
        {
          "Container": "flac",
          "Type": "Audio"
        },
        {
          "Container": "webma",
          "Type": "Audio"
        },
        {
          "Container": "webm",
          "AudioCodec": "webma",
          "Type": "Audio"
        },
        {
          "Container": "wav",
          "Type": "Audio"
        },
        {
          "Container": "ogg",
          "Type": "Audio"
        },
        {
          "Container": "hls",
          "Type": "Video",
          "VideoCodec": "av1,hevc,h264,vp9",
          "AudioCodec": "aac,mp2,opus,flac"
        },
        {
          "Container": "hls",
          "Type": "Video",
          "VideoCodec": "h264",
          "AudioCodec": "aac,mp3,mp2"
        }
      ],
      "TranscodingProfiles": [
        {
          "Container": "mp4",
          "Type": "Audio",
          "AudioCodec": "aac",
          "Context": "Streaming",
          "Protocol": "hls",
          "MaxAudioChannels": "2",
          "BreakOnNonKeyFrames": true,
          "EnableAudioVbrEncoding": true
        },
        {
          "Container": "aac",
          "Type": "Audio",
          "AudioCodec": "aac",
          "Context": "Streaming",
          "Protocol": "http",
          "MaxAudioChannels": "2"
        },
        {
          "Container": "mp3",
          "Type": "Audio",
          "AudioCodec": "mp3",
          "Context": "Streaming",
          "Protocol": "http",
          "MaxAudioChannels": "2"
        },
        {
          "Container": "opus",
          "Type": "Audio",
          "AudioCodec": "opus",
          "Context": "Streaming",
          "Protocol": "http",
          "MaxAudioChannels": "2"
        },
        {
          "Container": "wav",
          "Type": "Audio",
          "AudioCodec": "wav",
          "Context": "Streaming",
          "Protocol": "http",
          "MaxAudioChannels": "2"
        },
        {
          "Container": "opus",
          "Type": "Audio",
          "AudioCodec": "opus",
          "Context": "Static",
          "Protocol": "http",
          "MaxAudioChannels": "2"
        },
        {
          "Container": "mp3",
          "Type": "Audio",
          "AudioCodec": "mp3",
          "Context": "Static",
          "Protocol": "http",
          "MaxAudioChannels": "2"
        },
        {
          "Container": "aac",
          "Type": "Audio",
          "AudioCodec": "aac",
          "Context": "Static",
          "Protocol": "http",
          "MaxAudioChannels": "2"
        },
        {
          "Container": "wav",
          "Type": "Audio",
          "AudioCodec": "wav",
          "Context": "Static",
          "Protocol": "http",
          "MaxAudioChannels": "2"
        },
        {
          "Container": "mp4",
          "Type": "Video",
          "AudioCodec": "aac,mp2,opus,flac",
          "VideoCodec": "av1,hevc,h264,vp9",
          "Context": "Streaming",
          "Protocol": "hls",
          "MaxAudioChannels": "2",
          "BreakOnNonKeyFrames": true
        },
        {
          "Container": "ts",
          "Type": "Video",
          "AudioCodec": "aac,mp3,mp2",
          "VideoCodec": "h264",
          "Context": "Streaming",
          "Protocol": "hls",
          "MaxAudioChannels": "2",
          "BreakOnNonKeyFrames": true
        }
      ],
      "ContainerProfiles": [],
      "CodecProfiles": [
        {
          "Type": "VideoAudio",
          "Codec": "aac",
          "Conditions": [
            {
              "Condition": "Equals",
              "Property": "IsSecondaryAudio",
              "Value": "false",
              "IsRequired": false
            }
          ]
        },
        {
          "Type": "VideoAudio",
          "Conditions": [
            {
              "Condition": "Equals",
              "Property": "IsSecondaryAudio",
              "Value": "false",
              "IsRequired": false
            }
          ]
        },
        {
          "Type": "Video",
          "Codec": "h264",
          "Conditions": [
            {
              "Condition": "NotEquals",
              "Property": "IsAnamorphic",
              "Value": "true",
              "IsRequired": false
            },
            {
              "Condition": "EqualsAny",
              "Property": "VideoProfile",
              "Value": "high|main|baseline|constrained baseline|high 10",
              "IsRequired": false
            },
            {
              "Condition": "EqualsAny",
              "Property": "VideoRangeType",
              "Value": "SDR",
              "IsRequired": false
            },
            {
              "Condition": "LessThanEqual",
              "Property": "VideoLevel",
              "Value": "52",
              "IsRequired": false
            },
            {
              "Condition": "NotEquals",
              "Property": "IsInterlaced",
              "Value": "true",
              "IsRequired": false
            }
          ]
        },
        {
          "Type": "Video",
          "Codec": "hevc",
          "Conditions": [
            {
              "Condition": "NotEquals",
              "Property": "IsAnamorphic",
              "Value": "true",
              "IsRequired": false
            },
            {
              "Condition": "EqualsAny",
              "Property": "VideoProfile",
              "Value": "main|main 10",
              "IsRequired": false
            },
            {
              "Condition": "EqualsAny",
              "Property": "VideoRangeType",
              "Value": "SDR|HDR10|HDR10Plus|HLG",
              "IsRequired": false
            },
            {
              "Condition": "LessThanEqual",
              "Property": "VideoLevel",
              "Value": "186",
              "IsRequired": false
            },
            {
              "Condition": "NotEquals",
              "Property": "IsInterlaced",
              "Value": "true",
              "IsRequired": false
            }
          ]
        },
        {
          "Type": "Video",
          "Codec": "vp9",
          "Conditions": [
            {
              "Condition": "EqualsAny",
              "Property": "VideoRangeType",
              "Value": "SDR|HDR10|HDR10Plus|HLG",
              "IsRequired": false
            }
          ]
        },
        {
          "Type": "Video",
          "Codec": "av1",
          "Conditions": [
            {
              "Condition": "NotEquals",
              "Property": "IsAnamorphic",
              "Value": "true",
              "IsRequired": false
            },
            {
              "Condition": "EqualsAny",
              "Property": "VideoProfile",
              "Value": "main",
              "IsRequired": false
            },
            {
              "Condition": "EqualsAny",
              "Property": "VideoRangeType",
              "Value": "SDR|HDR10|HDR10Plus|HLG",
              "IsRequired": false
            },
            {
              "Condition": "LessThanEqual",
              "Property": "VideoLevel",
              "Value": "19",
              "IsRequired": false
            }
          ]
        }
      ],
      "SubtitleProfiles": [
        {
          "Format": "vtt",
          "Method": "External"
        },
        {
          "Format": "ass",
          "Method": "External"
        },
        {
          "Format": "ssa",
          "Method": "External"
        }
      ],
    }
  }

}
