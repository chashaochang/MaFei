package cn.mafei.cmp.providers.jellyfin.discovery

import cn.mafei.cmp.core.http_contract.HttpUrl

/**
 * Candidate expansion used by the original HarmonyOS connection flow.
 *
 * A scheme-less LAN address starts as HTTP, then gains an HTTPS alternative.
 * When no port is present, the protocol defaults and Jellyfin's conventional
 * HTTP/HTTPS ports (8096/8920) are also tried. An explicit scheme and port is
 * treated as an exact address.
 */
object JellyfinAddressCandidates {
    const val HTTP_PORT: Int = 8096
    const val HTTPS_PORT: Int = 8920

    fun fromInput(input: String): List<HttpUrl> {
        val raw = input.trim()
        if (raw.isEmpty() || raw.any { it.code < 32 || it.code == 127 } || '?' in raw || '#' in raw) {
            return emptyList()
        }
        val hasScheme = raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        if (!hasScheme && !looksLikeServerAddress(raw)) return emptyList()

        val parsed = HttpUrl.parseOrNull(if (hasScheme) raw else "http://$raw") ?: return emptyList()
        if (hasScheme && hasExplicitPort(raw)) return listOf(parsed)

        val candidates = buildList {
            add(parsed)
            if (parsed.scheme == HttpUrl.HTTP) add(parsed.withScheme(HttpUrl.HTTPS))

            toList().forEach { candidate ->
                if (candidate.port == HttpUrl.defaultPort(candidate.scheme)) {
                    if (candidate.scheme == HttpUrl.HTTP) {
                        add(candidate.withPort(HTTP_PORT))
                    } else {
                        add(candidate.withPort(HTTP_PORT))
                        add(candidate.withPort(HTTPS_PORT))
                    }
                }
            }
        }

        return candidates
            .distinctBy(HttpUrl::toEncodedString)
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<HttpUrl>> { score(it.value) }.thenBy { it.index })
            .map { it.value }
    }

    private fun looksLikeServerAddress(raw: String): Boolean {
        val authority = raw.substringBefore('/')
        return raw == "localhost" || '.' in authority || ':' in authority || raw.startsWith('[')
    }

    private fun hasExplicitPort(raw: String): Boolean {
        val authority = raw.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.startsWith('[')) {
            val closingBracket = authority.indexOf(']')
            return closingBracket >= 0 && authority.getOrNull(closingBracket + 1) == ':' &&
                authority.substring(closingBracket + 2).toIntOrNull() != null
        }
        val separator = authority.lastIndexOf(':')
        return separator > 0 && authority.substring(separator + 1).toIntOrNull() != null
    }

    private fun score(url: HttpUrl): Int {
        var score = if (url.isHttps) 5 else -5
        score += when (url.port) {
            HTTP_PORT -> 2
            HTTPS_PORT -> -1
            else -> 0
        }
        if (url.port == HttpUrl.defaultPort(url.scheme)) score += 3
        return score
    }

    private fun HttpUrl.withScheme(scheme: String): HttpUrl = newBuilder()
        .scheme(scheme)
        .port(if (port == HttpUrl.defaultPort(this.scheme)) HttpUrl.defaultPort(scheme) else port)
        .build()

    private fun HttpUrl.withPort(port: Int): HttpUrl = newBuilder().port(port).build()
}
