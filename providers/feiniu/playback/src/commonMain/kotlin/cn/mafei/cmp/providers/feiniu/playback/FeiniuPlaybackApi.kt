package cn.mafei.cmp.providers.feiniu.playback

import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayInfoData
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayData
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayRecordRequest

/**
 * Feiniu playback surface: play info (resume/current-track negotiation),
 * play/play (stream link with server-side expiry), progress recording and
 * the direct-range URL for the player. Actual playback belongs to the
 * player runtimes (GLM-05).
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
     * Opens the actual stream. The returned [FeiniuPlayPlayData.playLink] is
     * a server-issued, EXPIRING URL — never persist it; re-open when stale.
     */
    suspend fun play(
        resolution: FeiniuHostResolution,
        request: FeiniuPlayPlayRequest,
    ): FeiniuPlayPlayData =
        client.execute(
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
