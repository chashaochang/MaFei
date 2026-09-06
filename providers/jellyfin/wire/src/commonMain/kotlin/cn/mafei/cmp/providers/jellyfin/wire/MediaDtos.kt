package cn.mafei.cmp.providers.jellyfin.wire

import kotlinx.serialization.Serializable

/** One track (video/audio/subtitle/...) inside a media source. */
@Serializable
data class MediaStreamDto(
    val index: Int? = null,
    val type: MediaStreamType? = null,
    val codec: String? = null,
    val language: String? = null,
    val displayTitle: String? = null,
    val title: String? = null,
    val isDefault: Boolean? = null,
    val isExternal: Boolean? = null,
    val isForced: Boolean? = null,
    val channelLayout: String? = null,
    val channels: Int? = null,
    val sampleRate: Int? = null,
    val bitRate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val averageFrameRate: Double? = null,
    val aspectRatio: String? = null,
)

/** Jellyfin `MediaSourceInfo` — one playable representation of an item. */
@Serializable
data class MediaSourceInfoDto(
    val id: String? = null,
    val name: String? = null,
    val path: String? = null,
    val protocol: MediaProtocol? = null,
    val container: String? = null,
    val size: Long? = null,
    val isRemote: Boolean? = null,
    val supportsDirectPlay: Boolean? = null,
    val supportsDirectStream: Boolean? = null,
    val supportsTranscoding: Boolean? = null,
    val runTimeTicks: Long? = null,
    val defaultAudioStreamIndex: Int? = null,
    val defaultSubtitleStreamIndex: Int? = null,
    val mediaStreams: List<MediaStreamDto> = emptyList(),
    val directStreamUrl: String? = null,
    val transcodingUrl: String? = null,
    val transcodingSubProtocol: String? = null,
    val transcodingContainer: String? = null,
)

/** POST /Items/{itemId}/PlaybackInfo request body (P0 subset). */
@Serializable
data class PlaybackInfoRequestDto(
    val userId: String? = null,
    val playSessionId: String? = null,
    val maxStreamingBitrate: Long? = null,
    val startTimeTicks: Long? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val autoOpenLiveStream: Boolean? = null,
    val mediaSourceId: String? = null,
    val isPlayback: Boolean? = null,
)

/** POST /Items/{itemId}/PlaybackInfo response. */
@Serializable
data class PlaybackInfoResponseDto(
    val mediaSources: List<MediaSourceInfoDto> = emptyList(),
    val playSessionId: String? = null,
    val errorCode: PlaybackErrorCode? = null,
)

/** POST /Sessions/Playing (start) body. */
@Serializable
data class PlaybackStartInfoDto(
    val itemId: String,
    val playSessionId: String? = null,
    val mediaSourceId: String? = null,
    val positionTicks: Long? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val canSeek: Boolean? = null,
    val isMuted: Boolean? = null,
    val isPaused: Boolean? = null,
    val playMethod: WirePlayMethod? = null,
    val repeatMode: String? = null,
)

/** POST /Sessions/Playing/Progress body. */
@Serializable
data class PlaybackProgressInfoDto(
    val itemId: String,
    val playSessionId: String? = null,
    val mediaSourceId: String? = null,
    val positionTicks: Long? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val isPaused: Boolean? = null,
    val isMuted: Boolean? = null,
    val playMethod: WirePlayMethod? = null,
    val repeatMode: String? = null,
)

/** POST /Sessions/Playing/Stopped body. */
@Serializable
data class PlaybackStopInfoDto(
    val itemId: String,
    val playSessionId: String? = null,
    val mediaSourceId: String? = null,
    val positionTicks: Long? = null,
    val nextMediaType: String? = null,
)
