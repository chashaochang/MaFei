package cn.mafei.cmp.providers.jellyfin.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One track (video/audio/subtitle/...) inside a media source. */
@Serializable
data class MediaStreamDto(
    @SerialName("Index") val index: Int? = null,
    @SerialName("Type") val type: MediaStreamType? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Profile") val profile: String? = null,
    @SerialName("Level") val level: Double? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("IsDefault") val isDefault: Boolean? = null,
    @SerialName("IsExternal") val isExternal: Boolean? = null,
    @SerialName("IsForced") val isForced: Boolean? = null,
    @SerialName("IsTextSubtitleStream") val isTextSubtitleStream: Boolean? = null,
    @SerialName("SupportsExternalStream") val supportsExternalStream: Boolean? = null,
    @SerialName("DeliveryUrl") val deliveryUrl: String? = null,
    @SerialName("DeliveryMethod") val deliveryMethod: SubtitleDeliveryMethod? = null,
    @SerialName("ChannelLayout") val channelLayout: String? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("SampleRate") val sampleRate: Int? = null,
    @SerialName("BitRate") val bitRate: Int? = null,
    @SerialName("BitDepth") val bitDepth: Int? = null,
    @SerialName("RefFrames") val refFrames: Int? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("AverageFrameRate") val averageFrameRate: Double? = null,
    @SerialName("RealFrameRate") val realFrameRate: Double? = null,
    @SerialName("AspectRatio") val aspectRatio: String? = null,
    @SerialName("IsInterlaced") val isInterlaced: Boolean? = null,
    @SerialName("VideoRangeType") val videoRangeType: String? = null,
    @SerialName("DvProfile") val dolbyVisionProfile: Int? = null,
    @SerialName("ColorPrimaries") val colorPrimaries: String? = null,
    @SerialName("ColorSpace") val colorSpace: String? = null,
    @SerialName("ColorTransfer") val colorTransfer: String? = null,
    @SerialName("PixelFormat") val pixelFormat: String? = null,
)

/**
 * Jellyfin `MediaSourceInfo` — one playable representation of an item.
 * [liveStreamId] applies to live/RTSP sources that must be opened via
 * PlaybackInfo before streaming.
 */
@Serializable
data class MediaSourceInfoDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("Protocol") val protocol: MediaProtocol? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("IsRemote") val isRemote: Boolean? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("RequiresOpening") val requiresOpening: Boolean? = null,
    @SerialName("RequiresClosing") val requiresClosing: Boolean? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean? = null,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean? = null,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("DefaultAudioStreamIndex") val defaultAudioStreamIndex: Int? = null,
    @SerialName("DefaultSubtitleStreamIndex") val defaultSubtitleStreamIndex: Int? = null,
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto> = emptyList(),
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("TranscodingSubProtocol") val transcodingSubProtocol: String? = null,
    @SerialName("TranscodingContainer") val transcodingContainer: String? = null,
)

/** P0 subset of the OpenAPI `DeviceProfile` sent with PlaybackInfo. */
@Serializable
data class DeviceProfileDto(
    @SerialName("Name") val name: String? = null,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int? = null,
    @SerialName("MaxStaticBitrate") val maxStaticBitrate: Int? = null,
    @SerialName("MusicStreamingTranscodingBitrate") val musicStreamingTranscodingBitrate: Int? = null,
    @SerialName("DirectPlayProfiles") val directPlayProfiles: List<DirectPlayProfileDto> = emptyList(),
    @SerialName("TranscodingProfiles") val transcodingProfiles: List<TranscodingProfileDto> = emptyList(),
    @SerialName("SubtitleProfiles") val subtitleProfiles: List<SubtitleProfileDto> = emptyList(),
    @SerialName("CodecProfiles") val codecProfiles: List<CodecProfileDto> = emptyList(),
)

@Serializable
data class DirectPlayProfileDto(
    @SerialName("Container") val container: String? = null,
    @SerialName("AudioCodec") val audioCodec: String? = null,
    @SerialName("VideoCodec") val videoCodec: String? = null,
    @SerialName("Type") val type: DlnaProfileType? = null,
)

@Serializable
data class TranscodingProfileDto(
    @SerialName("Container") val container: String? = null,
    @SerialName("Type") val type: DlnaProfileType? = null,
    @SerialName("VideoCodec") val videoCodec: String? = null,
    @SerialName("AudioCodec") val audioCodec: String? = null,
    @SerialName("Protocol") val protocol: String? = null,
)

@Serializable
data class SubtitleProfileDto(
    @SerialName("Format") val format: String? = null,
    @SerialName("Method") val method: SubtitleDeliveryMethod? = null,
)

@Serializable
data class CodecProfileDto(
    @SerialName("Type") val type: CodecType? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Conditions") val conditions: List<ProfileConditionDto> = emptyList(),
)

@Serializable
data class ProfileConditionDto(
    @SerialName("Condition") val condition: String? = null,
    @SerialName("Property") val property: String? = null,
    @SerialName("Value") val value: String? = null,
    @SerialName("IsRequired") val isRequired: Boolean? = null,
)

/** POST /Items/{itemId}/PlaybackInfo request body (P0 subset). */
@Serializable
data class PlaybackInfoRequestDto(
    @SerialName("UserId") val userId: String? = null,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int? = null,
    @SerialName("StartTimeTicks") val startTimeTicks: Long? = null,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerialName("MaxAudioChannels") val maxAudioChannels: Int? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("DeviceProfile") val deviceProfile: DeviceProfileDto? = null,
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean? = null,
    @SerialName("EnableDirectStream") val enableDirectStream: Boolean? = null,
    @SerialName("EnableTranscoding") val enableTranscoding: Boolean? = null,
    @SerialName("AllowVideoStreamCopy") val allowVideoStreamCopy: Boolean? = null,
    @SerialName("AllowAudioStreamCopy") val allowAudioStreamCopy: Boolean? = null,
    @SerialName("AutoOpenLiveStream") val autoOpenLiveStream: Boolean? = null,
    @SerialName("AlwaysBurnInSubtitleWhenTranscoding") val alwaysBurnInSubtitleWhenTranscoding: Boolean? = null,
)

/** POST /Items/{itemId}/PlaybackInfo response. */
@Serializable
data class PlaybackInfoResponseDto(
    @SerialName("MediaSources") val mediaSources: List<MediaSourceInfoDto> = emptyList(),
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("ErrorCode") val errorCode: PlaybackErrorCode? = null,
)

/**
 * POST /Sessions/Playing body. This is intentionally the subset currently
 * produced by MaFei; nullable/defaulted fields keep incremental player reports
 * possible while their wire names and enum types follow OpenAPI 10.10.7.
 */
@Serializable
data class PlaybackStartInfoDto(
    @SerialName("ItemId") val itemId: String,
    @SerialName("SessionId") val sessionId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long? = null,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerialName("CanSeek") val canSeek: Boolean? = null,
    @SerialName("IsMuted") val isMuted: Boolean? = null,
    @SerialName("IsPaused") val isPaused: Boolean? = null,
    @SerialName("PlayMethod") val playMethod: WirePlayMethod? = null,
    @SerialName("RepeatMode") val repeatMode: WireRepeatMode? = null,
    @SerialName("PlaybackOrder") val playbackOrder: WirePlaybackOrder? = null,
)

/** P0 subset of POST /Sessions/Playing/Progress, aligned with OpenAPI 10.10.7. */
@Serializable
data class PlaybackProgressInfoDto(
    @SerialName("ItemId") val itemId: String,
    @SerialName("SessionId") val sessionId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long? = null,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerialName("IsPaused") val isPaused: Boolean? = null,
    @SerialName("IsMuted") val isMuted: Boolean? = null,
    @SerialName("PlayMethod") val playMethod: WirePlayMethod? = null,
    @SerialName("RepeatMode") val repeatMode: WireRepeatMode? = null,
    @SerialName("PlaybackOrder") val playbackOrder: WirePlaybackOrder? = null,
)

/** POST /Sessions/Playing/Stopped body. */
@Serializable
data class PlaybackStopInfoDto(
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("ItemId") val itemId: String,
    @SerialName("SessionId") val sessionId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long? = null,
    @SerialName("NextMediaType") val nextMediaType: String? = null,
)
