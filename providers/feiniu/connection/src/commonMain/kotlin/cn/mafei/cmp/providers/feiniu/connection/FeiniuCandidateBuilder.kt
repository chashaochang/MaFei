package cn.mafei.cmp.providers.feiniu.connection

import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuIdentity
import cn.mafei.cmp.providers.feiniu.core.FeiniuResolveData
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoutePreference

/**
 * Builds ordered host candidates from FNID resolve data (frozen legacy
 * ordering: LAN IPv4, public IPv4 http+https, public IPv6 http+https,
 * relay), applies the p2p preference and ranks by route preference.
 */
data object FeiniuCandidateBuilder {

    fun build(data: FeiniuResolveData): List<FeiniuHostCandidate> {
        val httpPort = data.port?.httpPort?.takeIf { it > 0 } ?: FeiniuIdentity.DEFAULT_HTTP_PORT
        val httpsPort = data.port?.httpsPort?.takeIf { it > 0 } ?: FeiniuIdentity.DEFAULT_HTTPS_PORT
        val candidates = mutableListOf<FeiniuHostCandidate>()

        fun append(baseUrl: String, route: FeiniuRoute) {
            val normalized = baseUrl.trim().trimEnd('/')
            if (normalized.isEmpty()) return
            if (candidates.any { it.baseUrl == normalized }) return
            candidates += FeiniuHostCandidate(normalized, route)
        }

        data.lanIpv4.forEach { ip -> if (ip.isNotBlank()) append("http://${ip.trim()}:$httpPort", FeiniuRoute.Local) }
        data.publicIpv4.forEach { ip ->
            if (ip.isBlank()) return@forEach
            append("http://${ip.trim()}:$httpPort", FeiniuRoute.Public)
            append("https://${ip.trim()}:$httpsPort", FeiniuRoute.Public)
        }
        data.publicIpv6.forEach { ip ->
            if (ip.isBlank()) return@forEach
            append("http://[${ip.trim()}]:$httpPort", FeiniuRoute.Public)
            append("https://[${ip.trim()}]:$httpsPort", FeiniuRoute.Public)
        }
        data.relayHosts.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { relay ->
            append(
                when {
                    relay.endsWith(":443") -> "https://${relay.removeSuffix(":443")}"
                    relay.contains(':') -> "https://$relay"
                    else -> "https://$relay:$httpsPort"
                },
                FeiniuRoute.Relay,
            )
        }
        return candidates
    }

    /** Without p2p, public candidates are dropped unless nothing else remains. */
    fun applyP2pPreference(candidates: List<FeiniuHostCandidate>, p2pEnabled: Boolean): List<FeiniuHostCandidate> {
        if (p2pEnabled) return candidates
        val filtered = candidates.filter { it.route != FeiniuRoute.Public }
        return filtered.ifEmpty { candidates }
    }

    /** Stable fallback order independent of probe results. */
    fun sortedByRemotePriority(candidates: List<FeiniuHostCandidate>): List<FeiniuHostCandidate> =
        candidates.sortedBy { remotePriority(it.route) }

    fun routePriority(route: FeiniuRoute, preference: FeiniuRoutePreference): Int = when (preference) {
        FeiniuRoutePreference.Public -> when (route) {
            FeiniuRoute.Public -> 0
            FeiniuRoute.Local -> 1
            else -> 2
        }
        FeiniuRoutePreference.Relay -> when (route) {
            FeiniuRoute.Relay -> 0
            FeiniuRoute.Public -> 1
            else -> 2
        }
        else -> when (route) {
            FeiniuRoute.Local -> 0
            FeiniuRoute.Public -> 1
            else -> 2
        }
    }

    fun unreachableRoutePriority(route: FeiniuRoute, preference: FeiniuRoutePreference): Int = when (preference) {
        FeiniuRoutePreference.Public -> when (route) {
            FeiniuRoute.Public -> 0
            FeiniuRoute.Relay -> 1
            else -> 2
        }
        else -> remotePriority(route)
    }

    fun remotePriority(route: FeiniuRoute): Int = when (route) {
        FeiniuRoute.Relay -> 0
        FeiniuRoute.Public -> 1
        FeiniuRoute.Direct -> 2
        FeiniuRoute.Local -> 3
    }

    fun relayFallbackCandidates(fnId: String): List<FeiniuHostCandidate> {
        val normalized = FeiniuAddressParser.extractFnId(fnId)
        if (normalized.isEmpty()) return emptyList()
        return listOf(
            FeiniuHostCandidate("https://$normalized.5ddd.com", FeiniuRoute.Relay),
            FeiniuHostCandidate("https://$normalized.fnos.net", FeiniuRoute.Relay),
        )
    }
}
