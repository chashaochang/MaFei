package cn.mafei.cmp.providers.feiniu.playback

import cn.mafei.cmp.core.http_contract.HttpRedirects
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayInfoData
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayData
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayRecordRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.FeiniuSessionSnapshot
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
    /**
     * Absolute [FeiniuPlayPlayData.playLink]; play links expire, never
     * persist. Resolved with the frozen RFC 3986 reference resolution against
     * the ACTUAL serving origin ([baseUrl]); unparseable, non-HTTP(S),
     * user-info-bearing or cross-origin targets are rejected (`null`) rather
     * than returned as wrong success URLs (F4).
     */
    fun resolvePlayLink(baseUrlFallback: String): String? =
        data.playLink?.let { absolutize(it, baseUrlFallback) }

    /** Absolute [FeiniuPlayPlayData.subtitleLink], same rules as [resolvePlayLink]. */
    fun resolveSubtitleLink(baseUrlFallback: String): String? =
        data.subtitleLink?.let { absolutize(it, baseUrlFallback) }

    private fun absolutize(link: String, baseUrlFallback: String): String? {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) return null
        val primary = baseUrl.trimEnd('/')
        val base = HttpUrl.parseOrNull(primary)
            ?: HttpUrl.parseOrNull(baseUrlFallback.trimEnd('/'))
            ?: return null
        val resolved = HttpRedirects.resolveLocation(base, trimmed) ?: return null
        // P0 contract: resources are served by the negotiated server origin;
        // cross-origin targets are refused instead of silently trusted.
        val sameOrigin = resolved.scheme == base.scheme && resolved.host == base.host && resolved.port == base.port
        if (!sameOrigin) return null
        return resolved.toEncodedString()
    }
}

/**
 * Feiniu playback surface: play info (resume/current-track negotiation),
 * play/play (stream link with server-side expiry), progress recording, the
 * direct-range URL for the player and the stream/list track catalog.
 * Actual playback belongs to the player runtimes (GLM-05).
 * The optional pinnedOperation on info/play/record binds a multi-step
 * adapter operation to its captured identity. Callers still reject stale
 * results against the authoritative state; a pinned receipt is not a
 * credential persistence source. Omission preserves existing SDK behavior.
 */
class FeiniuPlaybackApi(private val client: FeiniuClient) {

    /** Current playable item/media selection plus playback config. */
    suspend fun playInfo(
        resolution: FeiniuHostResolution,
        itemGuid: String,
        mediaGuid: String? = null,
        pinnedOperation: FeiniuSessionSnapshot? = null,
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
                pinnedOperation = pinnedOperation,
            ),
        ) { envelope, _ ->
            // F3: play negotiation endpoints require `data` (design §6.7) —
            // a missing payload is a decode failure, not an empty success.
            val data = envelope.data
                ?: throw cn.mafei.cmp.providers.feiniu.core.FeiniuDecodeError("Feiniu play info is missing data")
            MafeiJson.Default.decodeFromJsonElement(FeiniuPlayInfoData.serializer(), data)
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
        pinnedOperation: FeiniuSessionSnapshot? = null,
    ): FeiniuPlaybackSession {
        val executed = client.executeWithResult(
            resolution,
            FeiniuApiCall(
                path = "/v/api/v1/play/play",
                method = FeiniuApiCall.FeiniuMethod.Post,
                bodyText = MafeiJson.Default.encodeToString(FeiniuPlayPlayRequest.serializer(), request),
                authRequirement = FeiniuApiCall.AuthRequirement.Token,
                readTimeoutMs = 60_000,
                pinnedOperation = pinnedOperation,
            ),
        ) { envelope, _ ->
            val data = envelope.data
                ?: throw cn.mafei.cmp.providers.feiniu.core.FeiniuDecodeError("Feiniu play negotiation is missing data")
            MafeiJson.Default.decodeFromJsonElement(FeiniuPlayPlayData.serializer(), data)
        }
        return FeiniuPlaybackSession(
            data = executed.value,
            baseUrl = executed.candidate.baseUrl,
            route = executed.candidate.route,
            requiresRelayCookie = executed.candidate.requiresRelayCookie,
        )
    }

    /** Records playback progress (position/duration in seconds per protocol). */
    suspend fun record(resolution: FeiniuHostResolution, request: FeiniuPlayRecordRequest, pinnedOperation: FeiniuSessionSnapshot? = null) {
        client.execute(
            resolution,
            FeiniuApiCall(
                path = "/v/api/v1/play/record",
                method = FeiniuApiCall.FeiniuMethod.Post,
                bodyText = MafeiJson.Default.encodeToString(FeiniuPlayRecordRequest.serializer(), request),
                authRequirement = FeiniuApiCall.AuthRequirement.Token,
                pinnedOperation = pinnedOperation,
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
            // stream/list tolerates a missing payload with an empty catalog
            // (legacy getStreamList behavior); an object payload must decode.
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
