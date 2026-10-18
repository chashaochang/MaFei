package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ProviderLoginAddressFallbackTest {
    private class Transport(private val respond: suspend (HttpRequest) -> HttpResponse) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        var closed = false
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return respond(request)
        }
        override fun close() { closed = true }
    }

    private fun reply(request: HttpRequest, body: String, status: Int = 200) =
        HttpResponse(status, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)

    private fun unreachable(request: HttpRequest): Nothing =
        throw HttpTransportException(request.url, TransportFailureKind.Connect, "fixture unavailable")

    private fun HttpRequest.isLogin() = url.encodedPath.endsWith("/Users/AuthenticateByName")
    private fun HttpRequest.isProbe() = url.encodedPath.endsWith("/System/Info/Public")

    private fun assertAnonymousProbe(request: HttpRequest) {
        assertTrue(request.isProbe())
        assertEquals(HttpMethod.Get, request.method)
        assertEquals(HttpBody.Empty, request.body)
        assertNull(request.headers.first("Cookie"))
        assertNull(request.headers.first("X-Emby-Token"))
        assertFalse(request.headers.first("Authorization").orEmpty().contains("Token="))
        assertFalse(request.url.toEncodedString().contains("fixture-password"))
    }

    @Test fun explicitUrlWithoutPortStillLogsInDirectlyWhenItWorks() = runBlocking<Unit> {
        val transport = Transport { request ->
            reply(request, if (request.isLogin()) LOGIN else PUBLIC_INFO)
        }
        val result = ProviderLogin(transport).authenticate(ProviderKind.Jellyfin,
            "https://media.test/jellyfin", "demo", "fixture-password")
        assertEquals(listOf("/jellyfin/Users/AuthenticateByName", "/jellyfin/System/Info"),
            transport.requests.map { it.url.encodedPath })
        assertTrue(transport.requests.all { it.url.port == 443 })
        assertEquals("https://media.test/jellyfin", result.connection.server.baseUrl)
        result.binding.clear()
        assertFalse(transport.closed)
    }

    @Test fun unavailableExplicitHttpDefaultPortProbesBeforeUsingConventionalPort() = runBlocking<Unit> {
        val transport = Transport { request ->
            when {
                request.isLogin() && request.url.port == 80 -> unreachable(request)
                request.isProbe() -> {
                    assertAnonymousProbe(request)
                    if (request.url.scheme == "http" && request.url.port == 8096) reply(request, PUBLIC_INFO)
                    else unreachable(request)
                }
                request.isLogin() && request.url.port == 8096 -> reply(request, LOGIN)
                else -> error("unexpected request")
            }
        }
        val result = ProviderLogin(transport).authenticate(ProviderKind.Jellyfin,
            "http://media.test/jellyfin", "demo", "fixture-password")
        assertEquals(listOf(80, 8096), transport.requests.filter { it.isLogin() }.map { it.url.port })
        assertTrue(transport.requests.first().isLogin())
        assertTrue(transport.requests.drop(1).dropLast(1).all { it.isProbe() })
        assertTrue(transport.requests.all { it.url.encodedPath.startsWith("/jellyfin/") })
        assertEquals("http://media.test:8096/jellyfin", result.connection.server.baseUrl)
        assertEquals("Fallback Server", result.connection.server.name)
        assertEquals("10.10.7", result.connection.server.serverVersion)
        result.binding.clear()
        assertFalse(transport.closed)
    }

    @Test fun explicitHttpsFallbackNeverDowngradesAndPreservesEncodedBasePath() = runBlocking<Unit> {
        val transport = Transport { request ->
            assertEquals("https", request.url.scheme)
            when {
                request.isLogin() && request.url.port == 443 -> unreachable(request)
                request.isProbe() -> {
                    assertAnonymousProbe(request)
                    if (request.url.port == 8920) reply(request, PUBLIC_INFO) else unreachable(request)
                }
                request.isLogin() && request.url.port == 8920 -> reply(request, LOGIN)
                else -> error("unexpected request")
            }
        }
        val result = ProviderLogin(transport).authenticate(ProviderKind.Jellyfin,
            "https://[2001:db8::1]/media%20library", "demo", "fixture-password")
        assertEquals(listOf(443, 8096, 8920, 8920), transport.requests.map { it.url.port })
        assertTrue(transport.requests.all { it.url.encodedPath.startsWith("/media%20library/") })
        assertEquals("https://[2001:db8::1]:8920/media%20library", result.connection.server.baseUrl)
        result.binding.clear()
    }

    @Test fun explicitPortsIncludingProtocolDefaultsNeverTriggerDiscovery() = runBlocking<Unit> {
        for (address in listOf("http://media.test:8096/base", "https://media.test:8920/base",
            "http://media.test:80/base", "https://[2001:db8::1]:443/base")) {
            val transport = Transport { unreachable(it) }
            assertFailsWith<NetworkUnreachableError> {
                ProviderLogin(transport).authenticate(ProviderKind.Jellyfin, address, "demo", "fixture-password")
            }
            assertTrue(transport.requests.single().isLogin())
            assertFalse(transport.closed)
        }
    }

    @Test fun serverResponsesAndMalformedAuthenticationNeverTriggerPortDiscovery() = runBlocking<Unit> {
        for ((status, body, expectedRequests) in listOf(
            Triple(401, "{}", 1), Triple(403, "{}", 1), Triple(404, "{}", 1), Triple(429, "{}", 1), Triple(500, "{}", 1),
            Triple(200, "not-json", 1),
            // The existing login flow reads same-origin system metadata before
            // binding admission rejects an otherwise decoded empty token.
            Triple(200, """{"User":{"Id":"u"},"AccessToken":""}""", 2),
        )) {
            val transport = Transport { reply(it, body, status) }
            val failure = assertFailsWith<DomainError> {
                ProviderLogin(transport).authenticate(ProviderKind.Jellyfin, "https://media.test", "demo", "fixture-password")
            }
            when (status) {
                200 -> assertIs<ProtocolDecodingError>(failure)
                401 -> assertIs<AuthenticationError>(failure)
                403 -> assertIs<AuthorizationError>(failure)
                429 -> assertIs<RateLimitedError>(failure)
                500 -> assertIs<ServerError>(failure)
            }
            assertEquals(expectedRequests, transport.requests.size, "HTTP $status request count")
            assertEquals(1, transport.requests.count { it.isLogin() })
            assertTrue(transport.requests.first().isLogin())
            assertTrue(transport.requests.drop(1).all { it.url.encodedPath == "/System/Info" })
            assertTrue(transport.requests.none { it.isProbe() }, "HTTP $status must not start discovery")
            assertTrue(transport.requests.all { it.url.scheme == "https" && it.url.host == "media.test" && it.url.port == 443 })
        }
    }

    @Test fun rejectedLoginAtVerifiedFallbackDoesNotTryFurtherServers() = runBlocking<Unit> {
        val transport = Transport { request ->
            when {
                request.isLogin() && request.url.port == 443 -> unreachable(request)
                request.isProbe() -> { assertAnonymousProbe(request); reply(request, PUBLIC_INFO) }
                request.isLogin() -> reply(request, "{}", 401)
                else -> error("unexpected request")
            }
        }
        assertFailsWith<AuthenticationError> {
            ProviderLogin(transport).authenticate(ProviderKind.Jellyfin, "https://media.test", "demo", "fixture-password")
        }
        assertEquals(listOf(443, 8096, 8096), transport.requests.map { it.url.port })
        assertEquals(1, transport.requests.count { it.isProbe() })
    }

    @Test fun exhaustedDiscoveryPreservesOriginalTimeoutCategory() = runBlocking<Unit> {
        val transport = Transport { request ->
            if (request.isLogin()) throw HttpTimeoutException(request.url, 100)
            assertAnonymousProbe(request)
            unreachable(request)
        }
        assertFailsWith<TimeoutError> {
            ProviderLogin(transport).authenticate(ProviderKind.Jellyfin, "https://media.test", "demo", "fixture-password")
        }
        assertEquals(1, transport.requests.count { it.isLogin() })
        assertEquals(2, transport.requests.count { it.isProbe() })
    }

    @Test fun cancellationDuringFallbackDoesNotAuthenticateAnotherCandidate() = runBlocking<Unit> {
        val transport = Transport { request ->
            if (request.isLogin()) unreachable(request)
            assertAnonymousProbe(request)
            throw CancellationException("cancel fixture discovery")
        }
        assertFailsWith<CancellationException> {
            ProviderLogin(transport).authenticate(ProviderKind.Jellyfin, "https://media.test", "demo", "fixture-password")
        }
        assertEquals(2, transport.requests.size)
        assertEquals(1, transport.requests.count { it.isLogin() })
        assertFalse(transport.closed)
    }

    private companion object {
        const val PUBLIC_INFO = """{"ProductName":"Jellyfin Server","ServerName":"Fallback Server","Version":"10.10.7","Id":"server-id"}"""
        const val LOGIN = """{"User":{"Id":"remote-user","Name":"Demo"},"AccessToken":"fixture-token","ServerId":"server-id"}"""
    }
}
