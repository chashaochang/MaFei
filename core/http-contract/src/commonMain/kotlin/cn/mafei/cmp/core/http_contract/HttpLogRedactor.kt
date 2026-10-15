package cn.mafei.cmp.core.http_contract

/**
 * Sanitizes HTTP data before it reaches logs. Used by logging interceptors;
 * credential-bearing interceptors must route any diagnostics through this
 * interface so tokens, cookies and passwords never land in output.
 */
interface HttpLogRedactor {

    /** `true` when values of this header must be masked. */
    fun isSensitiveHeader(name: String): Boolean

    /** Returns a log-safe rendering of the header value. */
    fun redactHeader(name: String, value: String): String

    /** Returns a log-safe rendering of the URL (secrets in query strings masked). */
    fun redactUrl(url: HttpUrl): String
}

/**
 * Default redactor: masks every header in [HttpCredentialHeaders.ALL] plus
 * `Set-Cookie`, and any query parameter whose name hints at a token/key;
 * everything else passes through.
 */
data object DefaultHttpLogRedactor : HttpLogRedactor {

    private val sensitiveHeaders = HttpCredentialHeaders.ALL + HttpHeaders.SET_COOKIE.lowercase()

    private val sensitiveQueryNames = setOf(
        "api_key",
        "apikey",
        "token",
        "access_token",
        "auth",
        "authorization",
        "x-emby-token",
        "sign",
        "signature",
    )

    override fun isSensitiveHeader(name: String): Boolean = name.lowercase() in sensitiveHeaders

    override fun redactHeader(name: String, value: String): String {
        if (!isSensitiveHeader(name)) return value
        return if (value.isEmpty()) MASK else "$MASK(length=${value.length})"
    }

    override fun redactUrl(url: HttpUrl): String {
        val secretNames = url.queryParameters.names.filterTo(mutableSetOf()) { it.lowercase() in sensitiveQueryNames }
        if (secretNames.isEmpty()) return url.toString()

        val builder = url.newBuilder()
        for (name in secretNames) {
            builder.setQueryParameter(name, MASK)
        }
        return builder.build().toString()
    }

    private const val MASK = "██"
}
