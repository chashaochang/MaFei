package cn.mafei.cmp.providers.feiniu.core

import cn.mafei.cmp.core.http_contract.HttpBody
import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTimeoutException
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.http_contract.HttpTransportException
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.http_contract.TransportFailureKind
import cn.mafei.cmp.core.serialization.MafeiJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/** Outcome of one candidate execution: decoded envelope plus credential updates. */
class FeiniuCallResult<T>(
    val value: T,
    val candidate: FeiniuHostCandidate,
    val credentials: FeiniuCredentials,
)

/**
 * Feiniu media API execution pipeline:
 * - builds fnOS headers (Authx, X-Trim-Client*, UA, Authorization/Cookie);
 * - follows redirects MANUALLY (max 4, 303 -> GET + empty body, HTTPS
 *   downgrade refused, user-info targets refused, cross-origin targets must
 *   be trusted candidates), clearing transient cookies across origins;
 * - validates the {code,msg} envelope and maps failures onto [FeiniuError];
 * - syncs rotated tokens/cookies (Set-Cookie) back into the session state.
 *
 * Only connection failures escape per-candidate execution so the caller can
 * fall back to the next host (provider SDK design rule).
 */
class FeiniuClient(
    val runtime: FeiniuRuntimeSource = DefaultFeiniuRuntimeSource(),
    private val transport: HttpTransport,
    val sessionState: FeiniuSessionState = InMemoryFeiniuSessionState(),
) {

    /** Runs [call] across the ordered candidates, honoring the fallback rules. */
    suspend fun <T> executeWithFallback(
        resolution: FeiniuHostResolution,
        call: FeiniuApiCall,
        decode: (envelope: FeiniuEnvelope, credentials: FeiniuCredentials) -> T,
    ): FeiniuCallResult<T> {
        var lastError: Throwable? = null
        for (candidate in resolution.orderedCandidates) {
            try {
                return executeOnCandidate(candidate, resolution, call, decode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FeiniuConnectionError) {
                lastError = e
            }
        }
        throw lastError ?: FeiniuConnectionError(FeiniuConnectionKind.Unknown, "All Feiniu host candidates failed")
    }

    /** Convenience over [executeWithFallback]: unwraps the result value. */
    suspend fun <T> execute(
        resolution: FeiniuHostResolution,
        call: FeiniuApiCall,
        decode: (envelope: FeiniuEnvelope, credentials: FeiniuCredentials) -> T,
    ): T = executeWithFallback(resolution, call, decode).value

    suspend fun <T> executeOnCandidate(
        candidate: FeiniuHostCandidate,
        resolution: FeiniuHostResolution,
        call: FeiniuApiCall,
        decode: (envelope: FeiniuEnvelope, credentials: FeiniuCredentials) -> T,
    ): FeiniuCallResult<T> {
        val credentials = sessionState.current.value
        if (call.authRequirement == FeiniuApiCall.AuthRequirement.Token && !credentials.hasToken) {
            throw FeiniuAuthenticationError("Feiniu media session is not authorized")
        }

        var activeCandidate = candidate
        var transientCookie = ""
        var url = candidate.baseUrl.trimEnd('/') + call.path + appendQuery(call.queryParameters)
        var method = call.method.toHttpMethod()
        var bodyText = call.effectiveBody()

        for (redirectCount in 0..FeiniuIdentity.MAX_REDIRECTS) {
            val requestUrl = HttpUrl.parseOrNull(url)
                ?: throw FeiniuInvalidAddressError("Feiniu request URL is invalid: $url", input = url)
            val request = buildRequest(requestUrl, method, bodyText, call, credentials, transientCookie, activeCandidate)
            val response = executeTransport(request)

            transientCookie = mergeTransient(transientCookie, response)

            if (response.statusCode in 300..399) {
                val location = response.headers.first("location")?.trim().orEmpty()
                if (location.isEmpty()) {
                    throw FeiniuRedirectRejectedError("Feiniu redirect ${response.statusCode} is missing a Location")
                }
                val redirectUrl = resolveRedirect(url, location)
                    ?: throw FeiniuRedirectRejectedError("Feiniu redirect location is invalid: $location")
                requireTrustedRedirect(url, redirectUrl, resolution)
                if (!isSameOrigin(url, redirectUrl)) {
                    transientCookie = "" // credentials never follow across origins
                }
                val redirectBase = baseUrlOf(redirectUrl)
                if (redirectBase == null) {
                    throw FeiniuRedirectRejectedError("Feiniu redirect target has no usable base URL: $redirectUrl")
                }
                activeCandidate = FeiniuHostCandidate(redirectBase, routeFor(redirectBase, activeCandidate.route, resolution))
                if (response.statusCode == 303) {
                    method = HttpMethod.Get
                    bodyText = ""
                }
                url = redirectUrl
                continue
            }

            if (response.statusCode !in 200..299) {
                if (response.statusCode == 401 || response.statusCode == 403) {
                    throw if (call.authRequirement == FeiniuApiCall.AuthRequirement.Token) {
                        FeiniuAuthenticationExpiredError("Feiniu request rejected with HTTP ${response.statusCode}")
                    } else {
                        FeiniuAuthenticationError("Feiniu request rejected with HTTP ${response.statusCode}")
                    }
                }
                throw FeiniuConnectionError(
                    FeiniuConnectionKind.Unknown,
                    "Feiniu request returned HTTP ${response.statusCode}",
                )
            }

            val envelope = if (response.bodyText().isBlank()) {
                // 204/empty-body success (e.g. logout) — protocol treats it as code=0
                FeiniuEnvelope(code = 0, msg = "")
            } else {
                try {
                    MafeiJson.Default.decodeFromString(FeiniuEnvelope.serializer(), response.bodyText())
                } catch (e: SerializationException) {
                    throw FeiniuDecodeError("Feiniu response is not a valid JSON envelope", e)
                } catch (e: IllegalArgumentException) {
                    throw FeiniuDecodeError("Feiniu response is not a valid JSON envelope", e)
                }
            }
            requireEnvelopeCode(envelope, call)

            val updatedCredentials = credentialsFromResponse(transientCookie, call, credentials)
            sessionState.update { updatedCredentials }
            val value = try {
                decode(envelope, updatedCredentials)
            } catch (e: SerializationException) {
                throw FeiniuDecodeError("Feiniu response data does not match the expected shape", e)
            } catch (e: IllegalArgumentException) {
                throw FeiniuDecodeError("Feiniu response data does not match the expected shape", e)
            }
            // A decode that produced fresh credentials (login/auth family) wins
            // over the generic merge so the session reflects the server token.
            val finalCredentials = (value as? FeiniuCredentials) ?: updatedCredentials
            if (finalCredentials !== updatedCredentials) {
                sessionState.set(finalCredentials)
            }
            return FeiniuCallResult(value, activeCandidate, finalCredentials)
        }
        throw FeiniuRedirectRejectedError("Feiniu request exceeded the redirect limit of ${FeiniuIdentity.MAX_REDIRECTS}")
    }

    private suspend fun executeTransport(request: HttpRequest): HttpResponse =
        try {
            transport.execute(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpTimeoutException) {
            throw FeiniuConnectionError(FeiniuConnectionKind.Timeout, "Feiniu request timed out: ${e.message}", e)
        } catch (e: HttpTransportException) {
            val kind = when (e.kind) {
                TransportFailureKind.Dns -> FeiniuConnectionKind.Dns
                TransportFailureKind.Connect -> FeiniuConnectionKind.Connect
                TransportFailureKind.Tls -> FeiniuConnectionKind.Tls
                TransportFailureKind.SocketWrite -> FeiniuConnectionKind.Unknown
                TransportFailureKind.Unknown -> FeiniuConnectionKind.Unknown
            }
            throw FeiniuConnectionError(kind, e.message, e)
        }

    private fun buildRequest(
        url: HttpUrl,
        method: HttpMethod,
        bodyText: String,
        call: FeiniuApiCall,
        credentials: FeiniuCredentials,
        transientCookie: String,
        candidate: FeiniuHostCandidate,
    ): HttpRequest {
        val builder = HttpRequest.builder(method, url)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
            .setHeader(HttpHeaders.ACCEPT, "application/json")
            .setHeader("User-Agent", FeiniuIdentity.USER_AGENT)
            .setHeader("X-Trim-Client", FeiniuIdentity.CLIENT)
            .setHeader("X-Trim-Client-Version", FeiniuIdentity.CLIENT_VERSION)
            .setHeader(
                "Authx",
                FeiniuSigner.authx(
                    path = url.encodedPath,
                    nonce = FeiniuSigner.nonceFrom(runtime.random()),
                    timestamp = runtime.nowEpochMs().toString(),
                    bodyText = bodyText,
                    queryParameters = call.queryParameters,
                ),
            )
        var cookie = credentials.cookie
        if (call.authRequirement == FeiniuApiCall.AuthRequirement.Token && credentials.hasToken) {
            builder.setHeader(HttpHeaders.AUTHORIZATION, credentials.token)
            cookie = FeiniuCookieJar.mergeToken(cookie, credentials.token)
        }
        if (transientCookie.isNotEmpty()) {
            cookie = FeiniuCookieJar.merge(cookie, transientCookie)
        }
        if (candidate.requiresRelayCookie) {
            cookie = FeiniuCookieJar.merge(cookie, FeiniuIdentity.RELAY_COOKIE)
        }
        if (cookie.isNotEmpty()) {
            builder.setHeader(HttpHeaders.COOKIE, cookie)
        }
        if (bodyText.isNotEmpty()) {
            builder.body(HttpBody.Text(bodyText))
        }
        builder.timeoutMillis(call.readTimeoutMs)
        return builder.build()
    }

    private fun isCredentialResponse(message: String): Boolean {
        val normalized = message.trim().lowercase()
        return normalized.contains("用户名或密码错误") ||
            (normalized.contains("username") && normalized.contains("password"))
    }

    private fun isAuthExpiredCode(code: Int, message: String?): Boolean {
        if (code == 401 || code == 403 || code == 100401 || code == 100403) return true
        val normalized = message?.trim()?.lowercase() ?: return false
        if (normalized.isEmpty()) return false
        return listOf(
            "unauthorized", "invalid token", "token invalid", "token expired",
            "登录过期", "token失效", "授权已失效", "鉴权失败",
        ).any { normalized.contains(it) }
    }

    private fun requireEnvelopeCode(envelope: FeiniuEnvelope, call: FeiniuApiCall) {
        val code = envelope.code
            ?: throw FeiniuDecodeError("Feiniu envelope is missing a numeric code")
        if (code == 0) return
        val message = envelope.msg?.trim()?.takeIf { it.isNotEmpty() } ?: "Feiniu request failed ($code)"
        if (call.path == "/v/api/v1/login" && isCredentialResponse(message)) {
            throw FeiniuAuthenticationError(message)
        }
        if (isAuthExpiredCode(code, envelope.msg)) {
            throw if (call.authRequirement == FeiniuApiCall.AuthRequirement.Token) {
                FeiniuAuthenticationExpiredError("Feiniu media authorization expired")
            } else {
                FeiniuAuthenticationError(message)
            }
        }
        throw FeiniuBusinessError(code, message)
    }

    private fun credentialsFromResponse(
        transientCookie: String,
        call: FeiniuApiCall,
        previous: FeiniuCredentials,
    ): FeiniuCredentials {
        val normalized = transientCookie.trim()
        if (normalized.isEmpty()) return previous
        val replaceExisting = call.authRequirement == FeiniuApiCall.AuthRequirement.None
        val baseCookie = if (replaceExisting) "" else previous.cookie
        val merged = FeiniuCookieJar.merge(baseCookie, normalized)
        val token = if (replaceExisting) {
            FeiniuCookieJar.extractToken(merged).orEmpty()
        } else {
            previous.token.ifEmpty { FeiniuCookieJar.extractToken(merged).orEmpty() }
        }
        return FeiniuCredentials(token = token, cookie = merged).normalized()
    }

    private fun mergeTransient(existing: String, response: HttpResponse): String {
        val setCookies = response.headers.values("set-cookie")
        if (setCookies.isEmpty()) return existing
        return FeiniuCookieJar.merge(existing, FeiniuCookieJar.fromSetCookieValues(setCookies))
    }

    private companion object {
        fun appendQuery(parameters: List<Pair<String, String>>): String {
            if (parameters.isEmpty()) return ""
            val parts = parameters
                .filter { it.first.isNotEmpty() }
                .joinToString("&") { (name, value) ->
                    "${percentEncode(name)}=${percentEncode(value)}"
                }
            return if (parts.isEmpty()) "" else "?$parts"
        }

        fun percentEncode(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val b = byte.toInt() and 0xFF
                val c = b.toChar()
                if (c.isLetterOrDigit() || c == '-' || c == '.' || c == '_' || c == '~') {
                    append(c)
                } else {
                    append('%')
                    append("0123456789ABCDEF"[b ushr 4])
                    append("0123456789ABCDEF"[b and 0xF])
                }
            }
        }

        fun resolveRedirect(currentUrl: String, location: String): String? {
            val base = HttpUrl.parseOrNull(currentUrl) ?: return null
            // RFC 3986 reference resolution from the frozen GLM-01 contract
            val resolved = cn.mafei.cmp.core.http_contract.HttpRedirects.resolveLocation(base, location.trim())
                ?: return null
            return resolved.toEncodedString()
        }

        fun requireTrustedRedirect(currentUrl: String, redirectUrl: String, resolution: FeiniuHostResolution) {
            val current = HttpUrl.parseOrNull(currentUrl)
            val target = HttpUrl.parseOrNull(redirectUrl)
            if (current == null || target == null) {
                throw FeiniuRedirectRejectedError("Feiniu redirect location is invalid")
            }
            if (current.scheme == "https" && target.scheme != "https") {
                throw FeiniuRedirectRejectedError("Feiniu refused an HTTPS downgrade redirect")
            }
            if (isSameOrigin(currentUrl, redirectUrl)) return
            // http -> https on the same host is an upgrade and therefore trusted
            if (current.scheme == "http" && target.scheme == "https" &&
                current.host == target.host && current.port == target.port
            ) {
                return
            }
            val trusted = resolution.orderedCandidates.any { candidate ->
                val parsed = HttpUrl.parseOrNull(candidate.baseUrl)
                parsed != null && parsed.scheme == target.scheme &&
                    parsed.host == target.host && parsed.port == target.port
            }
            if (!trusted) {
                throw FeiniuRedirectRejectedError("Feiniu refused an untrusted cross-origin redirect")
            }
        }

        fun isSameOrigin(left: String, right: String): Boolean {
            val a = HttpUrl.parseOrNull(left)
            val b = HttpUrl.parseOrNull(right)
            return a != null && b != null && a.scheme == b.scheme && a.host == b.host && a.port == b.port
        }

        fun baseUrlOf(url: String): String? {
            val parsed = HttpUrl.parseOrNull(url) ?: return null
            val defaultPort = HttpUrl.defaultPort(parsed.scheme)
            val host = if (parsed.host.contains(':')) "[${parsed.host}]" else parsed.host
            val portText = if (parsed.port == defaultPort) "" else ":${parsed.port}"
            return "${parsed.scheme}://$host$portText"
        }

        fun routeFor(baseUrl: String, fallback: FeiniuRoute, resolution: FeiniuHostResolution): FeiniuRoute {
            val parsed = HttpUrl.parseOrNull(baseUrl) ?: return fallback
            resolution.orderedCandidates.firstOrNull { candidate ->
                val candidateUrl = HttpUrl.parseOrNull(candidate.baseUrl)
                candidateUrl != null && candidateUrl.host == parsed.host && candidateUrl.port == parsed.port
            }?.let { return it.route }
            val relayHost = parsed.host == "fnos.net" || parsed.host.endsWith(".fnos.net") ||
                parsed.host == "5ddd.com" || parsed.host.endsWith(".5ddd.com")
            return if (relayHost) FeiniuRoute.Relay else fallback
        }
    }
}

private fun FeiniuApiCall.FeiniuMethod.toHttpMethod(): HttpMethod = when (this) {
    FeiniuApiCall.FeiniuMethod.Get -> HttpMethod.Get
    FeiniuApiCall.FeiniuMethod.Post -> HttpMethod.Post
    FeiniuApiCall.FeiniuMethod.Put -> HttpMethod.Put
    FeiniuApiCall.FeiniuMethod.Delete -> HttpMethod.Delete
}

private fun FeiniuApiCall.effectiveBody(): String = when {
    bodyText.isNotEmpty() -> bodyText
    method == FeiniuApiCall.FeiniuMethod.Post -> "{}"
    else -> ""
}
