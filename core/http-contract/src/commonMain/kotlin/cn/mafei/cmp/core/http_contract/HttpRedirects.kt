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
     * redirect, per RFC 3986 §5 (reference resolution):
     * - absolute URLs and network-path (`//host/...`) references;
     * - root-relative (`/path`) and directory-relative (`item`, `./item`,
     *   `../up`) references — literal `.` and `..` dot segments are removed
     *   per §5.2.4 and clamped at the root;
     * - query-only (`?page=2`) and fragment-only (`#section`) references keep
     *   the base path **verbatim**: dot-segment removal applies only to the
     *   path contributed by the reference itself, never to the base path.
     *
     * Empty path segments are preserved (`/a//b` stays `/a//b`).
     * Returns `null` for anything unparseable — including references carrying
     * user info — instead of guessing.
     */
    fun resolveLocation(base: HttpUrl, location: String): HttpUrl? {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return null

        val origin = base.scheme + "://" + authorityOf(base)
        var candidate: String
        // RFC 5.3 component-wise: only references that contribute a path get
        // their dot segments removed; the base path is copied verbatim.
        var referenceContributesPath: Boolean

        when {
            trimmed.contains("://") -> return HttpUrl.parseOrNull(trimmed)
            trimmed.startsWith("//") -> {
                candidate = base.scheme + ":" + trimmed
                referenceContributesPath = true
            }
            trimmed.startsWith("/") -> {
                candidate = origin + trimmed
                referenceContributesPath = true
            }
            trimmed.startsWith("?") -> {
                candidate = origin + base.encodedPath + trimmed
                referenceContributesPath = false
            }
            trimmed.startsWith("#") -> {
                candidate = origin + base.encodedPath + baseQueryString(base) + trimmed
                referenceContributesPath = false
            }
            else -> {
                // path-relative references merge against the current directory
                val directory = base.encodedPath.substringBeforeLast('/')
                val prefix = origin + if (directory.isEmpty()) "/" else "$directory/"
                candidate = prefix + trimmed
                referenceContributesPath = true
            }
        }
        val resolved = HttpUrl.parseOrNull(candidate) ?: return null
        return if (referenceContributesPath) resolved.withDotSegmentsRemoved() else resolved
    }

    private fun baseQueryString(base: HttpUrl): String {
        val query = base.queryParameters.toEncodedString()
        return if (query.isEmpty()) "" else "?$query"
    }

    private fun HttpUrl.withDotSegmentsRemoved(): HttpUrl {
        val normalized = removeDotSegments(encodedPath)
        if (normalized == encodedPath) return this
        return newBuilder().encodedPath(normalized).build()
    }

    /**
     * RFC 3986 §5.2.4 over literal `.`/`..` segments, operating on the full
     * segment list so empty segments (`//`) are preserved verbatim. `..`
     * pops the last segment and clamps silently at the root; a trailing `.`
     * or `..` leaves a directory marker (trailing slash).
     */
    private fun removeDotSegments(path: String): String {
        val segments = path.split('/') // absolute path: first element is always ""
        val out = mutableListOf<String>()
        for (index in 1 until segments.size) {
            when (val segment = segments[index]) {
                "." -> if (index == segments.size - 1) out.add("")
                ".." -> {
                    if (out.isNotEmpty()) out.removeAt(out.size - 1)
                    if (index == segments.size - 1) out.add("")
                }
                else -> out.add(segment)
            }
        }
        return "/" + out.joinToString("/")
    }

    private fun authorityOf(url: HttpUrl): String = buildString {
        if (url.host.contains(':')) append('[').append(url.host).append(']') else append(url.host)
        if (url.port != HttpUrl.defaultPort(url.scheme)) append(':').append(url.port)
    }

    private fun sameOrigin(a: HttpUrl, b: HttpUrl): Boolean =
        a.scheme == b.scheme && a.host == b.host && a.port == b.port
}
