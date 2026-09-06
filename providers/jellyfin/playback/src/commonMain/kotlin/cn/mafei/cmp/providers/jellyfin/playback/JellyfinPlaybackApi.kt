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
     * sources (transcode/direct decisions live server-side). Errors surface
     * as [cn.mafei.cmp.providers.jellyfin.core.JellyfinError]; a
     * [cn.mafei.cmp.providers.jellyfin.wire.PlaybackErrorCode] in a 200
     * response is reported inside the DTO, not thrown.
     */
    suspend fun playbackInfo(
        userId: String,
        itemId: String,
        playSessionId: String? = null,
        maxStreamingBitrate: Long? = null,
        autoOpenLiveStream: Boolean = false,
        mediaSourceId: String? = null,
        startTimeTicks: Long? = null,
    ): PlaybackInfoResponseDto

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
        playSessionId: String?,
        maxStreamingBitrate: Long?,
        autoOpenLiveStream: Boolean,
        mediaSourceId: String?,
        startTimeTicks: Long?,
    ): PlaybackInfoResponseDto {
        val body = PlaybackInfoRequestDto(
            userId = userId,
            playSessionId = playSessionId,
            maxStreamingBitrate = maxStreamingBitrate,
            startTimeTicks = startTimeTicks,
            autoOpenLiveStream = autoOpenLiveStream,
            mediaSourceId = mediaSourceId,
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
}
