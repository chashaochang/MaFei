package cn.mafei.cmp.providers.feiniu.core

import kotlinx.serialization.Serializable

/** Connection route of one host candidate. */
enum class FeiniuRoute { Local, Public, Relay, Direct }

/** Route preference from the connect screen. */
enum class FeiniuRoutePreference { Auto, Public, Relay, Speed }

/** One reachable base URL candidate. Relay candidates carry `mode=relay`. */
data class FeiniuHostCandidate(
    val baseUrl: String,
    val route: FeiniuRoute,
) {
    val requiresRelayCookie: Boolean get() = route == FeiniuRoute.Relay
}

/** Ordered candidate list: preferred first, then fallbacks. */
data class FeiniuHostResolution(
    val preferred: FeiniuHostCandidate,
    val fallbacks: List<FeiniuHostCandidate> = emptyList(),
) {
    val orderedCandidates: List<FeiniuHostCandidate> get() = listOf(preferred) + fallbacks
}

/** Probe outcome for one candidate. */
data class FeiniuProbeResult(
    val candidate: FeiniuHostCandidate,
    val reachable: Boolean,
    val elapsedMs: Long,
)

/** Envelope of POST https://5ddd.com/api/v1/fn/con. */
@Serializable
data class FeiniuConnectResolveEnvelope(
    val code: Int? = null,
    val msg: String? = null,
    val data: FeiniuResolveData? = null,
)
