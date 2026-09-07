package cn.mafei.cmp.providers.feiniu.connection

import cn.mafei.cmp.providers.feiniu.core.FeiniuIdentity
import cn.mafei.cmp.providers.feiniu.core.FeiniuInvalidAddressError

/**
 * Parses the connect-screen input into an FNID or a direct base URL,
 * preserving the legacy normalization rules: relay-shaped inputs
 * (`{fnId}.5ddd.com`, `5ddd.com/{fnId}`, `{fnId}.fnos.net`) map to an FNID;
 * plain hosts get default ports (5666/5667) unless one is given.
 */
data object FeiniuAddressParser {

    enum class Kind { FnId, Direct }

    data class Parsed(val kind: Kind, val fnId: String, val directBaseUrl: String)

    fun parse(raw: String): Parsed {
        val original = raw.trim()
        if (original.isEmpty()) throw FeiniuInvalidAddressError("FNID or server address is required", raw)
        return if (isFnConnectLike(original)) {
            val fnId = extractFnId(original)
            if (fnId.isEmpty()) throw FeiniuInvalidAddressError("FNID is required", raw)
            Parsed(Kind.FnId, fnId, "")
        } else {
            Parsed(Kind.Direct, "", normalizeDirectBaseUrl(original))
        }
    }

    fun extractFnId(raw: String): String {
        val withoutScheme = raw.trim()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("wss://").removePrefix("ws://")
        if (withoutScheme.startsWith("5ddd.com/")) {
            return withoutScheme.substringAfter("5ddd.com/").substringBefore('/')
        }
        // host[:port][/path] -> host
        val host = withoutScheme.substringBefore('/').substringBefore(':')
        return when {
            host.endsWith(".fnos.net") -> host.removeSuffix(".fnos.net")
            host.endsWith(".5ddd.com") -> host.removeSuffix(".5ddd.com")
            else -> host
        }
    }

    private fun isFnConnectLike(input: String): Boolean {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return false
        if (isBareFnId(trimmed)) return true
        val withoutScheme = trimmed.replace(Regex("^[a-z]+://", RegexOption.IGNORE_CASE), "")
        return isRelayPath(withoutScheme) || isRelayDomain(withoutScheme) || isRelayPath(trimmed) || isRelayDomain(trimmed)
    }

    private fun isBareFnId(input: String): Boolean =
        input.isNotEmpty() && input.all { it.isLetterOrDigit() || it == '_' || it == '-' }

    private fun isRelayDomain(input: String): Boolean =
        Regex("(^|/)[^/]*\\.fnos\\.net(:\\d+)?($|/)", RegexOption.IGNORE_CASE).containsMatchIn(input) ||
            Regex("(^|/)[^/]*\\.5ddd\\.com(:\\d+)?($|/)", RegexOption.IGNORE_CASE).containsMatchIn(input)

    private fun isRelayPath(input: String): Boolean =
        Regex("^5ddd\\.com/[^/]+$", RegexOption.IGNORE_CASE).containsMatchIn(input)

    private fun normalizeDirectBaseUrl(value: String): String {
        if (value.contains('?') || value.contains('#')) {
            throw FeiniuInvalidAddressError("Server address must be an HTTP or HTTPS base URL", value)
        }
        val normalized = normalizeHttpBaseUrl(value)
        val authority = normalized.substringAfter("://").substringBefore('/')
        if (authority.isEmpty() || authority.contains('@') || authority.any { it == '\t' || it == '\n' || it == '\r' || it == ' ' }) {
            throw FeiniuInvalidAddressError("Server address host is invalid", value)
        }
        return normalized
    }

    /** Legacy normalization: scheme inference, ws(s)->http(s), default ports. */
    fun normalizeHttpBaseUrl(input: String, preferHttps: Boolean = false): String {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") ->
                normalizePrefixed(trimmed, preferHttps)
            trimmed.startsWith("ws://") || trimmed.startsWith("wss://") ->
                normalizePrefixed("${if (trimmed.startsWith("wss://")) "https://" else "http://"}${trimmed.substringAfter("://")}", preferHttps)
            else -> {
                val scheme = if (preferHttps) "https://" else "http://"
                if (hasExplicitPort(trimmed)) "$scheme$trimmed" else "$scheme$trimmed:${if (preferHttps) FeiniuIdentity.DEFAULT_HTTPS_PORT else FeiniuIdentity.DEFAULT_HTTP_PORT}"
            }
        }
    }

    private fun normalizePrefixed(input: String, preferHttps: Boolean): String {
        val scheme = if (input.startsWith("https://") || (preferHttps && !input.startsWith("http://"))) "https://" else "http://"
        val withoutScheme = input.substringAfter("://").trimEnd('/')
        if (isRelayPath(withoutScheme) || isRelayDomain(withoutScheme)) {
            val upgraded = if (scheme == "http://") "https://" else scheme
            return "$upgraded$withoutScheme"
        }
        return if (hasExplicitPort(withoutScheme)) {
            "$scheme$withoutScheme"
        } else {
            "$scheme$withoutScheme:${if (scheme == "https://") FeiniuIdentity.DEFAULT_HTTPS_PORT else FeiniuIdentity.DEFAULT_HTTP_PORT}"
        }
    }

    private fun hasExplicitPort(input: String): Boolean {
        val authority = input.substringBefore('/')
        val colon = authority.lastIndexOf(':')
        if (colon <= 0) return false
        val digits = authority.substring(colon + 1)
        return digits.isNotEmpty() && digits.all { it.isDigit() }
    }
}
