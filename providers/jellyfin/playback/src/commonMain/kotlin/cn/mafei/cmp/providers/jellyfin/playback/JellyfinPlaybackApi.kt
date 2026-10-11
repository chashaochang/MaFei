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
    suspend fun stopEncodingProcess(playSessionId: String)
    /** Legacy Jellyfin danmu plugin; null means the plugin did not publish a document. */
    suspend fun danmakuXml(itemId: String): String? = null

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
    override suspend fun stopEncodingProcess(playSessionId: String) {
        require(playSessionId.isNotBlank())
        val url = client.config.baseUrl.newBuilder().addPathSegments("Videos/ActiveEncodings")
            .addQueryParameter("deviceId", client.config.deviceId)
            .addQueryParameter("playSessionId", playSessionId).build()
        val request = cn.mafei.cmp.core.http_contract.HttpRequest.builder(
            cn.mafei.cmp.core.http_contract.HttpMethod.Delete, url).timeoutMillis(5_000).build()
        client.execute(request) { /* legacy stopEncodingProcess, 204 */ }
    }
    override suspend fun danmakuXml(itemId: String): String? {
        val plugin = client.config.baseUrl.newBuilder().addPathSegments("api/danmu").addPathSegment(itemId).build()
        val request = cn.mafei.cmp.core.http_contract.HttpRequest.builder(cn.mafei.cmp.core.http_contract.HttpMethod.Get, plugin)
            .readPolicy(cn.mafei.cmp.core.http_contract.BodyReadPolicy.BufferedAtMost(64 * 1024L))
            .redirectPolicy(cn.mafei.cmp.core.http_contract.RedirectPolicy.FollowSameOrigin).timeoutMillis(12_000).build()
        val url = client.execute(request) { response ->
            val body = cn.mafei.cmp.core.serialization.MafeiJson.Default.parseToJsonElement(response.bodyText())
            val value = (body as? kotlinx.serialization.json.JsonObject)?.get("url") as? kotlinx.serialization.json.JsonPrimitive
            value?.content?.let(HttpUrl::parseOrNull)
        } ?: return null
        // The old plugin's second request is anonymous, including same-server URLs.
        val document = cn.mafei.cmp.core.http_contract.HttpRequest.builder(cn.mafei.cmp.core.http_contract.HttpMethod.Get, url)
            .attribute(cn.mafei.cmp.providers.jellyfin.core.JellyfinRequestMode.ATTRIBUTE,
                cn.mafei.cmp.providers.jellyfin.core.JellyfinRequestMode.ANONYMOUS)
            .readPolicy(cn.mafei.cmp.core.http_contract.BodyReadPolicy.BufferedAtMost(2 * 1024 * 1024L))
            .redirectPolicy(cn.mafei.cmp.core.http_contract.RedirectPolicy.FollowSameOrigin).timeoutMillis(12_000).build()
        return client.execute(document) { it.bodyText().takeIf(String::isNotBlank) }
    }

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
