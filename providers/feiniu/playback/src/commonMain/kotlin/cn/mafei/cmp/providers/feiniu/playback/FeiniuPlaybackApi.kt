package cn.mafei.cmp.providers.feiniu.playback

import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayInfoData
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayData
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayRecordRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.FeiniuStreamListData

/**
 * A successful play/play negotiation together with the host context that
 * served it (R10): relative `play_link`/`subtitle_link` values must resolve
 * against [baseUrl] — the candidate that ACTUALLY answered, which can differ
 * from the preferred host after fallback — and relay routes require the
 * `mode=relay` cookie on the player request.
 */
data class FeiniuPlaybackSession(
    val data: FeiniuPlayPlayData,
    val baseUrl: String,
    val route: FeiniuRoute,
    val requiresRelayCookie: Boolean,
) {
    /** Absolute [FeiniuPlayPlayData.playLink]; play links expire, never persist. */
    fun resolvePlayLink(baseUrlFallback: String): String? =
        data.playLink?.let { absolutize(it, baseUrlFallback) }

    /** Absolute [FeiniuPlayPlayData.subtitleLink]. */
    fun resolveSubtitleLink(baseUrlFallback: String): String? =
        data.subtitleLink?.let { absolutize(it, baseUrlFallback) }

    private fun absolutize(link: String, baseUrlFallback: String): String {
        if (link.contains("://")) return link
        val base = baseUrl.trimEnd('/').ifEmpty { baseUrlFallback.trimEnd('/') }
        return if (link.startsWith("/")) base + link else "$base/$link"
    }
}

/**
 * Feiniu playback surface: play info (resume/current-track negotiation),
 * play/play (stream link with server-side expiry), progress recording, the
 * direct-range URL for the player and the stream/list track catalog.
 * Actual playback belongs to the player runtimes (GLM-05).
 */
class FeiniuPlaybackApi(private val client: FeiniuClient) {

    /** Current playable item/media selection plus playback config. */
    suspend fun playInfo(
        resolution: FeiniuHostResolution,
        itemGuid: String,
        mediaGuid: String? = null,
    ): FeiniuPlayInfoData {
        val body = buildString {
            append("{\"item_guid\":\"")
            append(escapeJson(itemGuid.trim()))
            append('"')
            if (!mediaGuid.isNullOrBlank()) {
                append(",\"media_guid\":\"")
                append(escapeJson(mediaGuid.trim()))
                append('"')
            }
            append('}')
        }
        return client.execute(
            resolution,
            FeiniuApiCall(
                path = "/v/api/v1/play/info",
                method = FeiniuApiCall.FeiniuMethod.Post,
                bodyText = body,
                authRequirement = FeiniuApiCall.AuthRequirement.Token,
            ),
        ) { envelope, _ ->
            envelope.data?.let {
                MafeiJson.Default.decodeFromJsonElement(FeiniuPlayInfoData.serializer(), it)
            } ?: FeiniuPlayInfoData()
        }
    }

    /**
     * Opens the actual stream and returns the session context (R10). The
     * returned [FeiniuPlaybackSession.data.playLink] is a server-issued,
     * EXPIRING URL — never persist it; re-open when stale.
     */
    suspend fun play(
        resolution: FeiniuHostResolution,
        request: FeiniuPlayPlayRequest,
    ): FeiniuPlaybackSession {
        val executed = client.executeWithResult(
            resolution,
            FeiniuApiCall(
                path = "/v/api/v1/play/play",
                method = FeiniuApiCall.FeiniuMethod.Post,
                bodyText = MafeiJson.Default.encodeToString(FeiniuPlayPlayRequest.serializer(), request),
                authRequirement = FeiniuApiCall.AuthRequirement.Token,
                readTimeoutMs = 60_000,
            ),
        ) { envelope, _ ->
            envelope.data?.let {
                MafeiJson.Default.decodeFromJsonElement(FeiniuPlayPlayData.serializer(), it)
            } ?: FeiniuPlayPlayData()
        }
        return FeiniuPlaybackSession(
            data = executed.value,
            baseUrl = executed.candidate.baseUrl,
            route = executed.candidate.route,
            requiresRelayCookie = executed.candidate.requiresRelayCookie,
        )
    }

    /** Records playback progress (position/duration in seconds per protocol). */
    suspend fun record(resolution: FeiniuHostResolution, request: FeiniuPlayRecordRequest) {
        client.execute(
            resolution,
            FeiniuApiCall(
                path = "/v/api/v1/play/record",
                method = FeiniuApiCall.FeiniuMethod.Post,
                bodyText = MafeiJson.Default.encodeToString(FeiniuPlayRecordRequest.serializer(), request),
                authRequirement = FeiniuApiCall.AuthRequirement.Token,
            ),
        ) { _, _ -> true }
    }

    /**
     * GET /v/api/v1/stream/list/{guid} — the track catalog (files, video/
     * audio/subtitle streams) used to prepare playback (P0 per design §6.6).
     * Track fields stay as raw JSON elements until real-device fixtures
     * justify typing them.
     */
    suspend fun streamList(
        resolution: FeiniuHostResolution,
        guid: String,
        beforePlay: Int? = null,
    ): FeiniuStreamListData =
        client.execute(
            resolution,
            FeiniuApiCall(
                path = "/v/api/v1/stream/list/${guid.trim()}",
                method = FeiniuApiCall.FeiniuMethod.Get,
                queryParameters = beforePlay?.let { listOf("before_play" to it.toString()) }.orEmpty(),
                authRequirement = FeiniuApiCall.AuthRequirement.Token,
            ),
        ) { envelope, _ ->
            envelope.data?.let {
                MafeiJson.Default.decodeFromJsonElement(FeiniuStreamListData.serializer(), it)
            } ?: FeiniuStreamListData()
        }

    /** Absolute direct-range stream URL (`/v/api/v1/media/range/{mediaGuid}`). */
    fun directRangeUrl(baseUrl: String, mediaGuid: String, byteOffset: Long = -1): String {
        val base = baseUrl.trimEnd('/')
        val range = if (byteOffset > 0) "?range=bytes=$byteOffset-" else ""
        return "$base/v/api/v1/media/range/${mediaGuid.trim()}$range"
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}
