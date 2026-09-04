package cn.mafei.cmp.core.http_contract

/**
 * Immutable, fully-resolved HTTP(S) URL.
 *
 * - [scheme] and [host] are normalized to lower case.
 * - [encodedPath] always starts with `/` and is stored percent-encoded.
 * - [queryParameters] stores *decoded* name/value pairs and re-encodes on render.
 * - Embedded user info (`user:pass@host`) is rejected; credentials belong in
 *   headers (see `AuthorizationRequestInterceptor`), never in URLs.
 */
class HttpUrl private constructor(
    val scheme: String,
    val host: String,
    val port: Int,
    val encodedPath: String,
    val queryParameters: QueryParameters = QueryParameters.EMPTY,
    val fragment: String? = null,
) {

    val isHttps: Boolean get() = scheme == "https"

    /** Path split into decoded segments. */
    val pathSegments: List<String>
        get() = encodedPath.split('/').filter { it.isNotEmpty() }.mapNotNull { segment ->
            PercentCodec.decode(segment)
        }

    fun toEncodedString(): String = buildString {
        append(scheme)
        append("://")
        if (host.contains(':')) append('[').append(host).append(']') else append(host)
        if (port != defaultPort(scheme)) append(':').append(port)
        append(encodedPath)
        val query = queryParameters.toEncodedString()
        if (query.isNotEmpty()) append('?').append(query)
        if (fragment != null) append('#').append(PercentCodec.encode(fragment))
    }

    override fun toString(): String = toEncodedString()

    fun newBuilder(): Builder = Builder(
        scheme = scheme,
        host = host,
        port = port,
        encodedPath = encodedPath,
        queryParameters = queryParameters,
        fragment = fragment,
    )

    override fun equals(other: Any?): Boolean =
        other is HttpUrl &&
            scheme == other.scheme &&
            host == other.host &&
            port == other.port &&
            encodedPath == other.encodedPath &&
            queryParameters == other.queryParameters &&
            fragment == other.fragment

    override fun hashCode(): Int {
        var result = scheme.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + port
        result = 31 * result + encodedPath.hashCode()
        result = 31 * result + queryParameters.hashCode()
        result = 31 * result + (fragment?.hashCode() ?: 0)
        return result
    }

    class Builder(
        scheme: String,
        host: String,
        private var port: Int = -1,
        private var encodedPath: String = "/",
        private var queryParameters: QueryParameters = QueryParameters.EMPTY,
        private var fragment: String? = null,
    ) {
        private var normalizedScheme = normalizeScheme(scheme)
        private var normalizedHost = normalizeHost(host)

        init {
            validateHost(host)
        }

        fun scheme(value: String): Builder = apply { normalizedScheme = normalizeScheme(value) }

        fun host(value: String): Builder = apply {
            validateHost(value)
            normalizedHost = normalizeHost(value)
        }

        fun port(value: Int): Builder = apply {
            require(value in 1..65535) { "port must be within 1..65535 but was $value" }
            port = value
        }

        /** Raw percent-encoded path, e.g. `/a%20b`. Replaces any previously set path. */
        fun encodedPath(value: String): Builder = apply {
            encodedPath = normalizeEncodedPath(value)
        }

        /** One decoded segment; encoded on build. */
        fun addPathSegment(segment: String): Builder = apply {
            val current = encodedPath.trimEnd('/')
            val encoded = PercentCodec.encode(segment)
            encodedPath = if (current.isEmpty()) "/$encoded" else "$current/$encoded"
        }

        /** Slash-separated decoded segments; each encoded on build. */
        fun addPathSegments(segments: String): Builder = apply {
            segments.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
        }

        fun addQueryParameter(name: String, value: String): Builder = apply {
            require(name.isNotEmpty()) { "query parameter name must not be empty" }
            queryParameters = queryParameters + (name to value)
        }

        fun addQueryParameterIfNotBlank(name: String, value: String?): Builder = apply {
            if (!value.isNullOrBlank()) addQueryParameter(name, value)
        }

        fun setQueryParameter(name: String, value: String): Builder = apply {
            require(name.isNotEmpty()) { "query parameter name must not be empty" }
            queryParameters = QueryParameters.of(name to value) + queryParameters.without(name)
        }

        fun removeQueryParameter(name: String): Builder = apply {
            queryParameters = queryParameters.without(name)
        }

        fun queryParameters(build: QueryParameters.Builder.() -> Unit): Builder = apply {
            queryParameters = QueryParameters.Builder().apply(build).build()
        }

        fun fragment(value: String?): Builder = apply { fragment = value }

        fun build(): HttpUrl = HttpUrl(
            scheme = normalizedScheme,
            host = normalizedHost,
            port = if (port == -1) defaultPort(normalizedScheme) else port,
            encodedPath = encodedPath,
            queryParameters = queryParameters,
            fragment = fragment,
        )
    }

    companion object {
        const val HTTP: String = "http"
        const val HTTPS: String = "https"

        fun defaultPort(scheme: String): Int = if (scheme == HTTPS) 443 else 80

        /** Parses an absolute HTTP(S) URL; throws [IllegalArgumentException] when malformed. */
        fun parse(raw: String): HttpUrl = parseOrNull(raw)
            ?: throw IllegalArgumentException("Malformed HTTP URL: $raw")

        /** Like [parse] but returns `null` instead of throwing. */
        fun parseOrNull(raw: String): HttpUrl? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            val schemeEnd = trimmed.indexOf("://")
            if (schemeEnd <= 0) return null
            val scheme = normalizeSchemeOrNull(trimmed.substring(0, schemeEnd)) ?: return null

            val rest = trimmed.substring(schemeEnd + 3)
            if (rest.isEmpty()) return null

            // authority ends at the first '/', '?' or '#'
            val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
            val authority = if (authorityEnd == -1) rest else rest.substring(0, authorityEnd)
            if (authority.isEmpty()) return null

            val afterAuthority = if (authorityEnd == -1) "" else rest.substring(authorityEnd)

            // fragment
            var working = afterAuthority
            var fragment: String? = null
            val fragmentStart = working.indexOf('#')
            if (fragmentStart != -1) {
                fragment = PercentCodec.decode(working.substring(fragmentStart + 1)) ?: return null
                working = working.substring(0, fragmentStart)
            }

            // query
            var query = QueryParameters.EMPTY
            val queryStart = working.indexOf('?')
            if (queryStart != -1) {
                query = parseQuery(working.substring(queryStart + 1)) ?: return null
                working = working.substring(0, queryStart)
            }

            val path = if (working.isEmpty()) "/" else normalizeEncodedPath(working)
            if (!PercentCodec.hasValidPercentEscapes(path)) return null

            // authority: [ipv6] or host, optional :port, no userinfo
            val (host, port) = parseAuthority(authority) ?: return null

            return HttpUrl(
                scheme = scheme,
                host = normalizeHost(host),
                port = port ?: defaultPort(scheme),
                encodedPath = path,
                queryParameters = query,
                fragment = fragment,
            )
        }

        private fun parseAuthority(authority: String): Pair<String, Int?>? {
            if (authority.contains('@')) return null // user info rejected by contract

            return if (authority.startsWith('[')) {
                val closing = authority.indexOf(']')
                if (closing == -1) return null
                val host = authority.substring(1, closing)
                if (host.isEmpty()) return null
                val tail = authority.substring(closing + 1)
                val port = when {
                    tail.isEmpty() -> null // no port, valid
                    tail.startsWith(':') && tail.length > 1 ->
                        tail.substring(1).toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
                    else -> return null
                }
                if (!isHostValid(host)) return null
                normalizeHost(host) to port
            } else {
                val colon = authority.lastIndexOf(':')
                when {
                    colon == -1 -> {
                        if (!isHostValid(authority)) return null
                        normalizeHost(authority) to null
                    }
                    else -> {
                        val host = authority.substring(0, colon)
                        // a bare IPv6 address without brackets is ambiguous, not a host
                        if (host.isEmpty() || host.contains(':')) return null
                        val portText = authority.substring(colon + 1)
                        if (portText.isEmpty()) return null
                        val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
                        if (!isHostValid(host)) return null
                        normalizeHost(host) to port
                    }
                }
            }
        }

        private fun normalizeScheme(value: String): String =
            requireNotNull(normalizeSchemeOrNull(value)) { "scheme must be http or https but was '$value'" }

        private fun normalizeSchemeOrNull(value: String): String? {
            val lowered = value.lowercase()
            return if (lowered == HTTP || lowered == HTTPS) lowered else null
        }

        private fun normalizeHost(value: String): String = value.lowercase()

        /**
         * Shared host validation for parser and builder: hosts must be
         * non-empty and free of `/`, `?`, `#`, whitespace and control
         * characters. `:` is allowed only where the grammar puts it (the
         * authority/port split or bracketed IPv6 literals), not inside the
         * plain-host rule itself.
         */
        private fun isHostValid(host: String): Boolean =
            host.isNotEmpty() &&
                host.none { it == '/' || it == '?' || it == '#' } &&
                host.none { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F }

        private fun validateHost(host: String) {
            require(isHostValid(host)) { "host is empty or contains reserved, whitespace or control characters: '$host'" }
        }

        /**
         * Guarantees a leading `/`; everything else (including trailing
         * slashes and duplicate segments) is preserved verbatim because
         * servers may treat `/a/` and `/a` differently.
         */
        private fun normalizeEncodedPath(value: String): String {
            val withSlash = if (value.startsWith('/')) value else "/$value"
            return if (withSlash == "/") "/" else withSlash
        }

        private fun parseQuery(raw: String): QueryParameters? {
            if (raw.isEmpty()) return QueryParameters.EMPTY
            val builder = QueryParameters.Builder()
            for (pair in raw.split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                val name = if (eq == -1) pair else pair.substring(0, eq)
                val value = if (eq == -1) "" else pair.substring(eq + 1)
                val decodedName = PercentCodec.decode(name, plusAsSpace = true) ?: return null
                if (decodedName.isEmpty()) return null
                val decodedValue = PercentCodec.decode(value, plusAsSpace = true) ?: return null
                builder.add(decodedName, decodedValue)
            }
            return builder.build()
        }
    }
}

/**
 * Ordered query parameters supporting multiple values per name, stored decoded.
 */
class QueryParameters private constructor(
    private val entries: List<Pair<String, String>>,
) : Iterable<Pair<String, String>> {

    val size: Int get() = entries.size
    val names: Set<String> get() = entries.mapTo(mutableSetOf()) { it.first }

    fun first(name: String): String? = entries.firstOrNull { it.first == name }?.second

    fun values(name: String): List<String> = entries.filter { it.first == name }.map { it.second }

    fun contains(name: String): Boolean = entries.any { it.first == name }

    /** Returns a copy without every value of [name]. */
    fun without(name: String): QueryParameters = QueryParameters(entries.filterNot { it.first == name })

    /** Appends [entry] with the same name validation as [Builder.add] (empty names rejected). */
    operator fun plus(entry: Pair<String, String>): QueryParameters {
        require(entry.first.isNotEmpty()) { "query parameter name must not be empty" }
        return QueryParameters(entries + entry)
    }

    operator fun plus(other: QueryParameters): QueryParameters = QueryParameters(entries + other.entries)

    fun toEncodedString(): String = entries.joinToString("&") { (name, value) ->
        PercentCodec.encode(name) + "=" + PercentCodec.encode(value)
    }

    fun toMap(): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        for ((name, value) in entries) {
            result.getOrPut(name) { mutableListOf() }.add(value)
        }
        return result
    }

    override fun iterator(): Iterator<Pair<String, String>> = entries.iterator()

    override fun equals(other: Any?): Boolean = other is QueryParameters && other.entries == entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = toEncodedString()

    class Builder {
        private val entries = mutableListOf<Pair<String, String>>()

        /** Appends a value, preserving multi-value semantics. */
        fun add(name: String, value: String): Builder = apply {
            entries += requireValidName(name) to value
        }

        /** Replaces all existing values of [name] with a single [value]. */
        fun set(name: String, value: String): Builder = apply {
            removeAll(name)
            entries += requireValidName(name) to value
        }

        fun addIfNotBlank(name: String, value: String?): Builder = apply {
            if (!value.isNullOrBlank()) add(name, value)
        }

        fun remove(name: String): Builder = apply { removeAll(name) }

        fun clear(): Builder = apply { entries.clear() }

        fun build(): QueryParameters = QueryParameters(entries.toList())

        private fun removeAll(name: String) {
            entries.removeAll { it.first == name }
        }

        private fun requireValidName(name: String): String {
            require(name.isNotEmpty()) { "query parameter name must not be empty" }
            return name
        }
    }

    companion object {
        val EMPTY: QueryParameters = QueryParameters(emptyList())

        fun of(vararg entries: Pair<String, String>): QueryParameters =
            Builder().apply { entries.forEach { add(it.first, it.second) } }.build()

        operator fun invoke(build: Builder.() -> Unit): QueryParameters = Builder().apply(build).build()

        fun builder(): Builder = Builder()
    }
}
