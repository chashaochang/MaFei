package cn.mafei.cmp.providers.jellyfin.playback

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinRequests
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackInfoRequestDto
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackInfoResponseDto

/**
 * Stable playback-preparation surface: PlaybackInfo negotiation plus stream
 * URL building for direct play, direct stream and (server-defined) HLS
 * transcoding. Actual playback belongs to the player runtimes (GLM-05).
 */
interface JellyfinPlaybackApi {

    /**
     * Opens a playback session for [itemId] and returns the server's media
     * sources (transcode/direct decisions live server-side). [deviceProfile]
     * describes what this client can decode (P0 subset of the OpenAPI
     * DeviceProfile); it is a different mechanism from the
     * Sessions/Capabilities/Full reporting, which stays out of P0. Errors
     * surface as [cn.mafei.cmp.providers.jellyfin.core.JellyfinError]; a
     * [cn.mafei.cmp.providers.jellyfin.wire.PlaybackErrorCode] in a 200
     * response is reported inside the DTO, not thrown.
     */
    suspend fun playbackInfo(
        userId: String,
        itemId: String,
        maxStreamingBitrate: Int? = null,
        autoOpenLiveStream: Boolean = false,
        mediaSourceId: String? = null,
        startTimeTicks: Long? = null,
        liveStreamId: String? = null,
        deviceProfile: cn.mafei.cmp.providers.jellyfin.wire.DeviceProfileDto? = null,
        maxAudioChannels: Int? = null,
        enableDirectPlay: Boolean? = null,
        enableDirectStream: Boolean? = null,
        enableTranscoding: Boolean? = null,
        allowVideoStreamCopy: Boolean? = null,
        allowAudioStreamCopy: Boolean? = null,
        alwaysBurnInSubtitleWhenTranscoding: Boolean? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackInfoResponseDto

    /** Absolutizes a track DeliveryUrl with the same same-origin rules as [transcodingUrl]. */
    fun deliveryUrl(deliveryUrl: String): HttpUrl

    /** Direct/static stream URL (`/Videos/{id}/stream` or `/Audio/{id}/stream`). */
    fun directStreamUrl(
        itemId: String,
        mediaSourceId: String? = null,
        container: String? = null,
        playSessionId: String? = null,
        audio: Boolean = false,
    ): HttpUrl

    /**
     * Absolutizes a server-provided transcoding URL (relative HLS path) and
     * appends `api_key` when a session token exists and is not embedded yet.
     */
    fun transcodingUrl(transcodingUrl: String): HttpUrl
}

class DefaultJellyfinPlaybackApi(
    private val client: JellyfinClient,
) : JellyfinPlaybackApi {

    override suspend fun playbackInfo(
        userId: String,
        itemId: String,
        maxStreamingBitrate: Int?,
        autoOpenLiveStream: Boolean,
        mediaSourceId: String?,
        startTimeTicks: Long?,
        liveStreamId: String?,
        deviceProfile: cn.mafei.cmp.providers.jellyfin.wire.DeviceProfileDto?,
        maxAudioChannels: Int?,
        enableDirectPlay: Boolean?,
        enableDirectStream: Boolean?,
        enableTranscoding: Boolean?,
        allowVideoStreamCopy: Boolean?,
        allowAudioStreamCopy: Boolean?,
        alwaysBurnInSubtitleWhenTranscoding: Boolean?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ): PlaybackInfoResponseDto {
        val body = PlaybackInfoRequestDto(
            userId = userId,
            maxStreamingBitrate = maxStreamingBitrate,
            startTimeTicks = startTimeTicks,
            autoOpenLiveStream = autoOpenLiveStream,
            mediaSourceId = mediaSourceId,
            liveStreamId = liveStreamId,
            deviceProfile = deviceProfile,
            maxAudioChannels = maxAudioChannels,
            enableDirectPlay = enableDirectPlay,
            enableDirectStream = enableDirectStream,
            enableTranscoding = enableTranscoding,
            allowVideoStreamCopy = allowVideoStreamCopy,
            allowAudioStreamCopy = allowAudioStreamCopy,
            alwaysBurnInSubtitleWhenTranscoding = alwaysBurnInSubtitleWhenTranscoding,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        )
        return client.executeDecoding(
            JellyfinRequests.playbackInfo(client.config.baseUrl, userId, itemId, body),
            PlaybackInfoResponseDto.serializer(),
        )
    }

    override fun directStreamUrl(
        itemId: String,
        mediaSourceId: String?,
        container: String?,
        playSessionId: String?,
        audio: Boolean,
    ): HttpUrl =
        JellyfinRequests.directStreamUrl(
            base = client.config.baseUrl,
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            container = container,
            playSessionId = playSessionId,
            token = client.sessionState.current.value?.accessToken,
            audio = audio,
        )

    override fun transcodingUrl(transcodingUrl: String): HttpUrl =
        JellyfinRequests.absolutizeTranscodingUrl(
            base = client.config.baseUrl,
            transcodingUrl = transcodingUrl,
            token = client.sessionState.current.value?.accessToken,
        )

    override fun deliveryUrl(deliveryUrl: String): HttpUrl =
        JellyfinRequests.absolutizeDeliveryUrl(
            base = client.config.baseUrl,
            deliveryUrl = deliveryUrl,
            token = client.sessionState.current.value?.accessToken,
        )
}
