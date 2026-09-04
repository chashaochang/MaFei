package cn.mafei.cmp.core.http_contract

/**
 * Shared, platform-independent redirect resolution. Every transport
 * implementation (Android/iOS/macOS/OHOS) must drive redirect following
 * through this object so the behavior is identical on all targets, and so the
 * provider SDKs can rely on the documented semantics.
 *
 * Frozen contract:
 * - at most [DEFAULT_MAX_REDIRECTS] hops are followed (configurable); the
 *   budget covers the whole chain, not per hop;
 * - the request-level timeout covers the entire redirect chain;
 * - `303` turns the next request into a `GET` with an empty body and drops
 *   `Content-Type`/`Content-Length`; `301`/`302`/`307`/`308` preserve method
 *   and body (providers relying on preserved methods must cover them with
 *   tests, per the provider SDK design);
 * - `Location` is re-parsed and re-validated from scratch — a server-supplied
 *   string is never trusted blindly; locations carrying user info or
 *   unparseable references end the chain (`null`);
 * - HTTPS → HTTP downgrades are rejected with [HttpInsecureRedirectException];
 * - redirects crossing an origin strip every header in
 *   [HttpCredentialHeaders.ALL] (Authorization, Proxy-Authorization, Cookie,
 *   X-Emby-Authorization, X-Emby-Token, Trim-MC-token, ...);
 * - with [RedirectPolicy.FollowSameOrigin], cross-origin redirects end the
 *   chain (`null`); with [RedirectPolicy.DoNotFollow] nothing is followed;
 * - unrecognized 3xx statuses and missing locations end the chain (`null`)
 *   so the original response reaches the caller's error mapping.
 */
data object HttpRedirects {

    const val DEFAULT_MAX_REDIRECTS: Int = 4

    private val FOLLOWABLE_STATUSES = setOf(301, 302, 303, 307, 308)

    /**
     * Returns the request for the next hop, or `null` when the 3xx [response]
     * must be handed back to the caller unchanged.
     *
     * @param hopsCompleted redirects already followed for this call
     *        (the first redirect passes `0`).
     */
    fun nextRequest(
        previous: HttpRequest,
        response: HttpResponse,
        hopsCompleted: Int,
        maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
    ): HttpRequest? {
        if (previous.redirectPolicy == RedirectPolicy.DoNotFollow) return null
        // only well-defined redirect statuses are followable; anything else
        // (300/304/305/306/...) ends the chain and reaches the caller as-is
        if (response.statusCode !in FOLLOWABLE_STATUSES) return null

        if (hopsCompleted >= maxRedirects) {
            throw HttpRedirectLimitExceededException(
                url = response.effectiveUrl,
                maxRedirects = maxRedirects,
                lastStatusCode = response.statusCode,
            )
        }

        val location = response.headers.first("Location") ?: return null
        val nextUrl = resolveLocation(response.effectiveUrl, location) ?: return null

        if (previous.url.scheme == "https" && nextUrl.scheme != "https") {
            throw HttpInsecureRedirectException(from = response.effectiveUrl, to = nextUrl)
        }
        if (previous.redirectPolicy == RedirectPolicy.FollowSameOrigin && !sameOrigin(previous.url, nextUrl)) {
            return null
        }

        val redirectMethod = if (response.statusCode == 303) HttpMethod.Get else previous.method
        val builder = previous.newBuilder()
            .url(nextUrl)
            .method(redirectMethod)

        if (response.statusCode == 303) {
            builder.body(HttpBody.Empty)
            builder.removeHeader(HttpHeaders.CONTENT_TYPE)
            builder.removeHeader(HttpHeaders.CONTENT_LENGTH)
        }

        if (!sameOrigin(previous.url, nextUrl)) {
            // credentials never follow across origins
            for (name in HttpCredentialHeaders.ALL) {
                builder.removeHeader(name)
            }
        }

        return builder.build()
    }

    /**
     * Resolves a `Location` header value against the URL that produced the
     * redirect. Supports:
     * - absolute URLs and protocol-relative (`//host/...`) references;
     * - root-relative (`/path`) and directory-relative (`item`, `./item`,
     *   `../up`) references — literal `.` and `..` dot segments are removed
     *   per RFC 3986 §5.2.4 and clamped at the root;
     * - query-only (`?page=2`) and fragment-only (`#section`) references,
     *   which keep the current path (and query, for fragment references).
     *
     * Returns `null` for anything unparseable — including references carrying
     * user info — instead of guessing.
     */
    fun resolveLocation(base: HttpUrl, location: String): HttpUrl? {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return null

        val origin = base.scheme + "://" + authorityOf(base)
        val candidate = when {
            trimmed.contains("://") -> return HttpUrl.parseOrNull(trimmed)
            trimmed.startsWith("//") -> base.scheme + ":" + trimmed
            trimmed.startsWith("/") -> origin + trimmed // root-relative
            trimmed.startsWith("?") -> origin + base.encodedPath + trimmed
            trimmed.startsWith("#") -> origin + base.encodedPath + baseQueryString(base) + trimmed
            else -> {
                // path-relative references resolve against the current directory,
                // then literal dot segments are removed
                val directory = base.encodedPath.substringBeforeLast('/')
                val prefix = origin + if (directory.isEmpty()) "/" else "$directory/"
                prefix + trimmed
            }
        }
        val resolved = HttpUrl.parseOrNull(candidate) ?: return null
        return if (isPathReference(trimmed)) resolved.withoutDotSegments() else resolved
    }

    private fun isPathReference(location: String): Boolean =
        !location.contains("://") && !location.startsWith("//")

    private fun baseQueryString(base: HttpUrl): String {
        val query = base.queryParameters.toEncodedString()
        return if (query.isEmpty()) "" else "?$query"
    }

    private fun HttpUrl.withoutDotSegments(): HttpUrl {
        val normalized = removeDotSegments(encodedPath)
        if (normalized == encodedPath) return this
        return newBuilder().encodedPath(normalized).build()
    }

    /**
     * RFC 3986 §5.2.4 restricted to literal `.`/`..` segments, clamped at the
     * root. Only applies to path-relative redirect targets; empty segments
     * (`//`) collapse in the process.
     */
    private fun removeDotSegments(path: String): String {
        val segments = path.split('/').filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        for (segment in segments) {
            when (segment) {
                "." -> {}
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
                else -> out.add(segment)
            }
        }
        var result = out.joinToString("/", prefix = "/")
        val endsAsDirectory = path.endsWith("/") ||
            segments.lastOrNull() == "." ||
            segments.lastOrNull() == ".."
        if (endsAsDirectory && !result.endsWith("/")) result += "/"
        return result
    }

    private fun authorityOf(url: HttpUrl): String = buildString {
        if (url.host.contains(':')) append('[').append(url.host).append(']') else append(url.host)
        if (url.port != HttpUrl.defaultPort(url.scheme)) append(':').append(url.port)
    }

    private fun sameOrigin(a: HttpUrl, b: HttpUrl): Boolean =
        a.scheme == b.scheme && a.host == b.host && a.port == b.port
}
