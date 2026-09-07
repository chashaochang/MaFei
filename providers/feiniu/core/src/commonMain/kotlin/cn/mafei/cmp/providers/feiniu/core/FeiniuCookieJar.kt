package cn.mafei.cmp.providers.feiniu.core

/**
 * Feiniu cookie handling: cookies are a flat name=value set joined with
 * "; ", last-writer-wins, and the media token always mirrors the
 * `Trim-MC-token` cookie entry.
 */
data object FeiniuCookieJar {

    fun merge(existing: String, next: String): String {
        val map = linkedMapOf<String, String>()
        collect(map, existing)
        collect(map, next)
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun mergeToken(existing: String, token: String): String =
        if (token.isBlank()) normalize(existing) else merge(existing, "${FeiniuIdentity.TOKEN_COOKIE}=$token")

    fun extractToken(cookie: String): String? =
        cookie.split(';')
            .mapNotNull { segment ->
                val eq = segment.indexOf('=')
                if (eq <= 0) null else segment.substring(0, eq).trim() to segment.substring(eq + 1).trim()
            }
            .firstOrNull { it.first == FeiniuIdentity.TOKEN_COOKIE }
            ?.second

    /**
     * Flattens `Set-Cookie` header values into cookie pairs (first segment
     * before ';' only, attributes dropped), tolerating commas inside expires
     * dates by requiring `name=value` shaped segments.
     */
    fun fromSetCookieValues(values: List<String>): String {
        val pairs = mutableListOf<String>()
        for (value in values) {
            for (segment in splitSetCookie(value)) {
                val pair = cookiePair(segment)
                if (pair.isNotEmpty()) pairs += pair
            }
        }
        return pairs.joinToString("; ")
    }

    private fun cookiePair(segment: String): String {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return ""
        val firstPart = trimmed.split(';').first().trim()
        val eq = firstPart.indexOf('=')
        return if (eq > 0) firstPart else ""
    }

    private fun splitSetCookie(value: String): List<String> {
        val normalized = value.trim()
        if (normalized.isEmpty()) return emptyList()
        val segments = mutableListOf<String>()
        var start = 0
        var index = 0
        while (index < normalized.length) {
            if (normalized[index] == ',' && isBoundary(normalized, index + 1)) {
                segments += normalized.substring(start, index).trim()
                start = index + 1
            }
            index += 1
        }
        segments += normalized.substring(start).trim()
        return segments.filter { it.isNotEmpty() }
    }

    private fun isBoundary(text: String, startIndex: Int): Boolean {
        var index = startIndex
        while (index < text.length && text[index] == ' ') index += 1
        val nextSemicolon = text.indexOf(';', index)
        val nextComma = text.indexOf(',', index)
        var end = text.length
        if (nextSemicolon in index until end) end = nextSemicolon
        if (nextComma in index until end) end = nextComma
        val candidate = text.substring(index, end)
        val eq = candidate.indexOf('=')
        if (eq <= 0) return false
        val name = candidate.substring(0, eq).trim()
        return name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    private fun collect(map: LinkedHashMap<String, String>, cookieText: String) {
        for (segment in cookieText.split(';')) {
            val trimmed = segment.trim()
            if (trimmed.isEmpty()) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            val key = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
    }

    private fun normalize(value: String): String = merge(value, "")
}
