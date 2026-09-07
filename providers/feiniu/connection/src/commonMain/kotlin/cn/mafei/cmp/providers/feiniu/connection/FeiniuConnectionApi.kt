package cn.mafei.cmp.providers.feiniu.connection

import cn.mafei.cmp.core.http_contract.HttpHeaders
import kotlin.coroutines.cancellation.CancellationException
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.feiniu.core.DefaultFeiniuRuntimeSource
import cn.mafei.cmp.providers.feiniu.core.FeiniuConnectionError
import cn.mafei.cmp.providers.feiniu.core.FeiniuConnectResolveEnvelope
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuIdentity
import cn.mafei.cmp.providers.feiniu.core.FeiniuProbeResult
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoutePreference
import cn.mafei.cmp.providers.feiniu.core.FeiniuRuntimeSource
import cn.mafei.cmp.providers.feiniu.core.FeiniuSigner
import kotlinx.serialization.SerializationException

/**
 * Connection flow: input parsing, FNID resolution via the 5ddd connect
 * service (with relay fallback), reachability probing and route ranking.
 * Produces the [FeiniuHostResolution] consumed by the media client.
 */
class FeiniuConnectionApi(
    private val transport: HttpTransport,
    private val runtime: FeiniuRuntimeSource = DefaultFeiniuRuntimeSource(),
    private val p2pEnabled: Boolean = true,
) {

    /**
     * Resolves [rawInput] (FNID or direct address) into a probed, ranked
     * resolution. Connection-level failures fall back to the relay domains,
     * matching the legacy behavior of always having a connectable path.
     */
    suspend fun resolve(rawInput: String, preference: FeiniuRoutePreference = FeiniuRoutePreference.Auto): FeiniuHostResolution {
        val parsed = FeiniuAddressParser.parse(rawInput)
        if (parsed.kind == FeiniuAddressParser.Kind.Direct) {
            val candidate = FeiniuHostCandidate(parsed.directBaseUrl, FeiniuRoute.Direct)
            val probes = listOf(probe(candidate))
            return assemble(parsed.fnId, candidates = listOf(candidate), probes = probes, preference = preference)
        }

        val fnId = parsed.fnId
        val data = try {
            resolveFnIdRemote(fnId)
        } catch (e: FeiniuConnectionError) {
            val fallback = FeiniuCandidateBuilder.relayFallbackCandidates(fnId)
            if (fallback.isNotEmpty()) {
                val probes = fallback.map { probe(it) }
                return assemble(fnId, fallback, probes, preference)
            }
            throw e
        }
        val resolveData = data.data
        if (data.code != 0 || resolveData == null) {
            throw FeiniuConnectionError(
                cn.mafei.cmp.providers.feiniu.core.FeiniuConnectionKind.Unknown,
                data.msg?.takeIf { it.isNotBlank() } ?: "FNID resolution failed",
            )
        }
        val candidates = FeiniuCandidateBuilder.applyP2pPreference(FeiniuCandidateBuilder.build(resolveData), p2pEnabled)
        if (candidates.isEmpty()) {
            val fallback = FeiniuCandidateBuilder.relayFallbackCandidates(fnId)
            val probes = fallback.map { probe(it) }
            return assemble(fnId, fallback, probes, preference)
        }
        val probes = candidates.map { probe(it) }
        return assemble(fnId, candidates, probes, preference)
    }

    private fun assemble(
        fnId: String,
        candidates: List<FeiniuHostCandidate>,
        probes: List<FeiniuProbeResult>,
        preference: FeiniuRoutePreference,
    ): FeiniuHostResolution {
        val reachable = probes.filter { it.reachable }.sortedWith(
            compareBy<FeiniuProbeResult> { FeiniuCandidateBuilder.routePriority(it.candidate.route, preference) }
                .thenBy { it.elapsedMs },
        )
        val preferred = reachable.firstOrNull()?.candidate
            ?: probes.minByOrNull {
                FeiniuCandidateBuilder.unreachableRoutePriority(it.candidate.route, preference) * 1_000_000L + it.elapsedMs
            }?.candidate
            ?: candidates.first()

        val fallbacks = mutableListOf<FeiniuHostCandidate>()
        fun append(candidate: FeiniuHostCandidate) {
            if (candidate.baseUrl == preferred.baseUrl) return
            if (fallbacks.any { it.baseUrl == candidate.baseUrl }) return
            fallbacks += candidate
        }
        probes.sortedWith(
            compareByDescending<FeiniuProbeResult> { it.reachable }
                .thenBy { it.elapsedMs }
                .thenBy { FeiniuCandidateBuilder.remotePriority(it.candidate.route) },
        ).forEach { append(it.candidate) }
        FeiniuCandidateBuilder.sortedByRemotePriority(candidates).forEach { append(it) }

        return FeiniuHostResolution(preferred = preferred, fallbacks = fallbacks)
    }

    private suspend fun resolveFnIdRemote(fnId: String): FeiniuConnectResolveEnvelope {
        val body = """{"fnId":"$fnId"}"""
        val url = HttpUrl.parseOrNull(FeiniuIdentity.FN_CONNECT_URL)
            ?: throw FeiniuConnectionError(cn.mafei.cmp.providers.feiniu.core.FeiniuConnectionKind.Unknown, "FNID connect URL is invalid")
        val timestamp = runtime.nowEpochMs().toString()
        val request = HttpRequest.builder(HttpMethod.Post, url)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setHeader(HttpHeaders.ACCEPT, "application/json")
            .setHeader("fn-sign", FeiniuSigner.connectFnSign(fnId, timestamp))
            .setHeader(
                "Authx",
                FeiniuSigner.authx(
                    path = FeiniuIdentity.FN_CONNECT_PATH,
                    nonce = FeiniuSigner.nonceFrom(runtime.random()),
                    timestamp = timestamp,
                    bodyText = body,
                    variant = FeiniuSigner.Variant.Connect,
                ),
            )
            .body(cn.mafei.cmp.core.http_contract.HttpBody.Text(body))
            .timeoutMillis(15_000)
            .build()

        val response = try {
            transport.execute(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw FeiniuConnectionError(cn.mafei.cmp.providers.feiniu.core.FeiniuConnectionKind.Unknown, e.message, e)
        }
        if (response.statusCode !in 200..299) {
            throw FeiniuConnectionError(
                cn.mafei.cmp.providers.feiniu.core.FeiniuConnectionKind.Unknown,
                "FNID resolve returned HTTP ${response.statusCode}",
            )
        }
        return try {
            MafeiJson.Default.decodeFromString(FeiniuConnectResolveEnvelope.serializer(), response.bodyText())
        } catch (e: SerializationException) {
            throw cn.mafei.cmp.providers.feiniu.core.FeiniuDecodeError("FNID resolve response is not valid JSON", e)
        } catch (e: IllegalArgumentException) {
            throw cn.mafei.cmp.providers.feiniu.core.FeiniuDecodeError("FNID resolve response is not valid JSON", e)
        }
    }

    /** GET {base}/license/v1/device/baseInfo — 2xx-4xx counts as reachable. */
    suspend fun probe(candidate: FeiniuHostCandidate): FeiniuProbeResult {
        val url = HttpUrl.parseOrNull(candidate.baseUrl.trimEnd('/') + FeiniuIdentity.PROBE_PATH)
            ?: return FeiniuProbeResult(candidate, reachable = false, elapsedMs = 0)
        val request = HttpRequest.builder(HttpMethod.Get, url)
            .setHeader(HttpHeaders.ACCEPT, "application/json,text/plain,*/*")
            .timeoutMillis(FeiniuIdentity.PROBE_READ_TIMEOUT_MS)
            .build()
        val started = runtime.nowEpochMs()
        val reachable = try {
            val response = transport.execute(request)
            response.statusCode in 200..499
        } catch (_: Throwable) {
            false
        }
        return FeiniuProbeResult(candidate, reachable, runtime.nowEpochMs() - started)
    }
}
