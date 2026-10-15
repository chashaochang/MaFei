package cn.mafei.cmp.core.http_contract

/** `SameSite` cookie attribute. */
enum class CookieSameSite {
    Strict,
    Lax,
    None,
}

/**
 * Parsed `Set-Cookie` payload.
 *
 * Expiry resolution is pure: [expiresEpochMs] holds the raw `Expires` value
 * (when present and parseable); [maxAgeSeconds] keeps the raw `Max-Age`;
 * [receivedEpochMs] records when the header was observed. `Max-Age` wins over
 * `Expires` (RFC 6265) once it can be anchored to [receivedEpochMs]. This
 * module never reads a platform clock — pass `now` to [isExpired].
 */
data class Cookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expiresEpochMs: Long? = null,
    val maxAgeSeconds: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val sameSite: CookieSameSite? = null,
    val receivedEpochMs: Long? = null,
) {

    /** Absolute expiry instant, or `null` when the cookie is session-scoped. */
    val effectiveExpiresEpochMs: Long?
        get() = when {
            maxAgeSeconds != null && receivedEpochMs != null -> receivedEpochMs + maxAgeSeconds * 1000L
            else -> expiresEpochMs
        }

    fun isExpired(nowEpochMs: Long): Boolean =
        effectiveExpiresEpochMs?.let { nowEpochMs >= it } ?: false

    companion object {

        /**
         * Parses one `Set-Cookie` header value. Returns `null` when the value
         * carries no usable name=value pair. Unparsable attributes are
         * ignored, matching browser leniency.
         */
        fun parseSetCookie(header: String, nowEpochMs: Long): Cookie? {
            val segments = header.split(';')
            val first = segments.firstOrNull()?.trim() ?: return null
            val eq = first.indexOf('=')
            if (eq <= 0) return null
            val name = first.substring(0, eq).trim()
            if (name.isEmpty()) return null
            val value = first.substring(eq + 1).trim().removeSurrounding("\"")

            var domain: String? = null
            var path: String? = null
            var expires: Long? = null
            var maxAge: Long? = null
            var secure = false
            var httpOnly = false
            var sameSite: CookieSameSite? = null

            for (rawAttribute in segments.drop(1)) {
                val attribute = rawAttribute.trim()
                if (attribute.isEmpty()) continue
                val attrEq = attribute.indexOf('=')
                val attrName = if (attrEq == -1) attribute else attribute.substring(0, attrEq).trim()
                val attrValue = if (attrEq == -1) null else attribute.substring(attrEq + 1).trim()

                when (attrName.lowercase()) {
                    "domain" -> domain = attrValue?.removePrefix(".")?.takeIf { it.isNotEmpty() }
                    "path" -> path = attrValue?.takeIf { it.isNotEmpty() }
                    "expires" -> expires = HttpDate.parseOrNull(attrValue)
                    "max-age" -> maxAge = attrValue?.trim()?.toLongOrNull()
                    "secure" -> secure = true
                    "httponly" -> httpOnly = true
                    "samesite" -> sameSite = when (attrValue?.lowercase()) {
                        "strict" -> CookieSameSite.Strict
                        "lax" -> CookieSameSite.Lax
                        "none" -> CookieSameSite.None
                        else -> null
                    }
                }
            }

            return Cookie(
                name = name,
                value = value,
                domain = domain,
                path = path,
                expiresEpochMs = expires,
                maxAgeSeconds = maxAge,
                secure = secure,
                httpOnly = httpOnly,
                sameSite = sameSite,
                receivedEpochMs = nowEpochMs,
            )
        }
    }
}

/**
 * Parser for HTTP dates (RFC 1123 `Sun, 06 Nov 1994 08:49:37 GMT`), kept
 * pure-Kotlin and tolerant of the common legacy variants.
 */
internal object HttpDate {

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    fun parseOrNull(raw: String?): Long? {
        if (raw == null) return null
        val text = raw.trim()
        if (text.isEmpty()) return null

        val parts = text.split(Regex("\\s+"))
        if (parts.size < 5) return null

        // day is the first token that looks like a number (RFC 850 uses "Sunday, 06-Nov-94")
        var index = 0
        var day = parts.getOrNull(index)?.trimEnd(',')?.toIntOrNull()
        if (day == null) {
            index += 1
            day = parts.getOrNull(index)?.trimEnd(',')?.toIntOrNull()
        }
        if (day == null || day !in 1..31) return null

        val month = parts.getOrNull(index + 1)
            ?.takeWhile { it.isLetter() }
            ?.lowercase()
            ?.let { MONTHS[it] } ?: return null

        val year = parts.getOrNull(index + 2)?.trimEnd(',')?.toIntOrNull() ?: return null
        val normalizedYear = when {
            year >= 70 && year < 100 -> year + 1900
            year in 0..69 -> year + 2000
            else -> year
        }

        val timeParts = parts.getOrNull(index + 3)?.split(':') ?: return null
        if (timeParts.size != 3) return null
        val hour = timeParts[0].toIntOrNull() ?: return null
        val minute = timeParts[1].toIntOrNull() ?: return null
        val second = timeParts[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return null

        val epochDay = IsoDays.daysFromCivil(normalizedYear, month, day)
        return epochDay * 86_400_000L + hour * 3_600_000L + minute * 60_000L + second * 1_000L
    }
}

/** Days-from-civil algorithm (shared with core:serialization's ISO logic shape). */
internal object IsoDays {

    fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }
}
