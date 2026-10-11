package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.providers.jellyfin.wire.CodecProfileDto
import cn.mafei.cmp.providers.jellyfin.wire.CodecType
import cn.mafei.cmp.providers.jellyfin.wire.DeviceProfileDto
import cn.mafei.cmp.providers.jellyfin.wire.DirectPlayProfileDto
import cn.mafei.cmp.providers.jellyfin.wire.DlnaProfileType
import cn.mafei.cmp.providers.jellyfin.wire.ProfileConditionDto
import cn.mafei.cmp.providers.jellyfin.wire.SubtitleDeliveryMethod
import cn.mafei.cmp.providers.jellyfin.wire.SubtitleProfileDto
import cn.mafei.cmp.providers.jellyfin.wire.TranscodingProfileDto

/** Mirrors legacy player/deviceprofile/DeviceProfileBuilder.ets's MPV profile. */
internal val LegacyMpvDeviceProfile = DeviceProfileDto(
    name = "MaFei-mpv",
    maxStreamingBitrate = 120_000_000,
    maxStaticBitrate = 100_000_000,
    musicStreamingTranscodingBitrate = 384_000,
    directPlayProfiles = listOf(
        DirectPlayProfileDto(container = "iso", type = DlnaProfileType.Video),
        DirectPlayProfileDto(
            container = "mp4,m4v,mov,mkv,webm,ts,mpegts,flv,avi,3gp",
            type = DlnaProfileType.Video,
            videoCodec = "h264,hevc,mpeg4,mpeg2video,h263,vp8,vp9,av1,vc1,wmv3,theora",
            audioCodec = "pcm_s8,pcm_s16be,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le,pcm_alaw,pcm_mulaw," +
                "mp1,mp2,mp3,aac,vorbis,opus,flac,alac,ac3,eac3,dts,mlp,truehd",
        ),
        DirectPlayProfileDto(
            container = "mp3,aac,m4a,ogg,opus,wav,flac,webm",
            type = DlnaProfileType.Audio,
            audioCodec = "pcm_s8,pcm_s16be,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le,pcm_alaw,pcm_mulaw," +
                "mp3,aac,vorbis,opus,flac,alac,ac3,eac3,dts",
        ),
    ),
    transcodingProfiles = listOf(
        TranscodingProfileDto(
            type = DlnaProfileType.Video, container = "ts", videoCodec = "h264",
            audioCodec = "aac,mp3,ac3,eac3", protocol = "hls",
        ),
        TranscodingProfileDto(
            type = DlnaProfileType.Video, container = "mp4", videoCodec = "h264",
            audioCodec = "aac,mp3,ac3,eac3", protocol = "hls",
        ),
        TranscodingProfileDto(type = DlnaProfileType.Audio, container = "mp3", audioCodec = "mp3", protocol = "http"),
    ),
    subtitleProfiles = listOf("ass", "ssa", "srt", "vtt", "pgs", "pgssub").map {
        SubtitleProfileDto(format = it, method = SubtitleDeliveryMethod.Embed)
    },
    codecProfiles = listOf(CodecProfileDto(
        type = CodecType.Video,
        codec = "hevc",
        conditions = listOf(ProfileConditionDto(
            condition = "EqualsAny", property = "VideoRangeType",
            value = "Unknown|SDR|HDR10|HDR10Plus|HLG|DOVI|DOVIWithHDR10|DOVIWithHLG|DOVIWithSDR",
            isRequired = false,
        )),
    )),
)
