package cn.mafei.cmp.providers.feiniu.facade

import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.providers.feiniu.auth.FeiniuAuthApi
import cn.mafei.cmp.providers.feiniu.catalog.FeiniuCatalogApi
import cn.mafei.cmp.providers.feiniu.core.FeiniuAuthenticationExpiredError
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuDecodeError
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.FeiniuSessionState
import cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState
import cn.mafei.cmp.providers.feiniu.playback.FeiniuPlaybackApi
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayRequest
import cn.mafei.cmp.providers.feiniu.testkit.FakeFeiniuTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Final review regressions for 040d170 (F1-F4). All deterministic staged
 * interleavings; the real-thread atomicity case lives in desktopTest.
 */
class FeiniuFinalReviewRegressionsTest {

    private val resolution = FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Direct))

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private fun response(request: HttpRequest, body: String, status: Int = 200) =
        HttpResponse(status, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)

    // F1-2: an ordinary same-identity response must not invalidate an in-flight login
    @Test
    fun ordinaryResponseDoesNotInvalidateInFlightLogin() = runTest {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account"))
        lateinit var client: FeiniuClient
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                if (request.url.encodedPath.endsWith("/login")) {
                    // Polling request completes while login is awaiting its response.
                    FeiniuCatalogApi(client).search(resolution, "fixture")
                    return response(request, """{"code":0,"token":"new-account"}""")
                }
                return response(request, """{"code":0,"data":[]}""")
            }
        }
        client = FeiniuClient(transport = transport, sessionState = state)
        val loggedIn = FeiniuAuthApi(client).login(resolution, "new-user", "pw")
        assertEquals("new-account", loggedIn.token)
        assertEquals("new-account", state.snapshot().credentials.token,
            "Successful login must agree with facade credential state")
    }

    // F1-3: a stale restore 401 must not clear a switched-in account
    @Test
    fun staleRestore401CannotClearNewAccount() = runTest {
        val state = InMemoryFeiniuSessionState()
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                state.set(FeiniuCredentials("new-account")) // account switched before old response
                return response(request, """{"code":401}""", status = 401)
            }
        }
        val client = FeiniuClient(transport = transport, sessionState = state)
        assertFailsWith<FeiniuAuthenticationExpiredError> {
            FeiniuAuthApi(client).restore(resolution, FeiniuCredentials("old-account"))
        }
        assertEquals("new-account", state.snapshot().credentials.token)
    }

    // F2
    @Test
    fun newLoginWithoutSetCookieCannotInheritOldSid() = runTest {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account", "sid=old-session"))
        val transport = FakeFeiniuTransport()
        transport.enqueueJson(200, """{"code":0,"token":"new-account"}""")
        val client = FeiniuClient(transport = transport, sessionState = state)
        val result = FeiniuAuthApi(client).login(resolution, "new-user", "pw")
        assertNull(transport.requests.single().headers.first("Cookie"))
        assertFalse(result.cookie.contains("sid=old-session"), "Old account Cookie must not be persisted into new account")
        assertEquals("Trim-MC-token=new-account", result.cookie)
    }

    // F4
    @Test
    fun protocolRelativePlayLinkResolvesAgainstActualOrigin() {
        val session = cn.mafei.cmp.providers.feiniu.playback.FeiniuPlaybackSession(
            cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayData(playLink = "//feiniu.test/stream/movie.m3u8"),
            "https://feiniu.test", FeiniuRoute.Direct, false,
        )
        assertEquals("https://feiniu.test/stream/movie.m3u8", session.resolvePlayLink("https://fallback.test"))
    }

    // F3
    @Test
    fun playbackNegotiationCannotSucceedWithoutData() = runTest {
        val transport = FakeFeiniuTransport()
        transport.enqueueJson(200, """{"code":0}""")
        val client = FeiniuClient(transport = transport,
            sessionState = InMemoryFeiniuSessionState(FeiniuCredentials("t")))
        assertFailsWith<FeiniuDecodeError> {
            FeiniuPlaybackApi(client).play(resolution,
                FeiniuPlayPlayRequest("md", "vd", "h264", "1080p", 8000000, 0, "aac", "au", "sb", 2, 0))
        }
    }

    // F1 companion: same-account concurrent responses merge cookies without loss
    @Test
    fun sameAccountConcurrentResponsesMergeCookies() = runTest {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("t", "Trim-MC-token=t"))
        var first = true
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                val cookie = if (first) "sid=one" else "theme=dark"
                first = false
                return HttpResponse(
                    200, HttpHeaders.EMPTY, """{"code":0,"data":[]}""".encodeToByteArray(),
                    request.url, request.url,
                    // modeled via body only in this fake; Set-Cookie modeled below
                ).let { base ->
                    HttpResponse(
                        base.statusCode,
                        cn.mafei.cmp.core.http_contract.HttpHeaders.Builder().add("Set-Cookie", "$cookie; Path=/").build(),
                        base.bodyBytes, base.requestUrl, base.effectiveUrl,
                    )
                }
            }
        }
        val client = FeiniuClient(transport = transport, sessionState = state)
        FeiniuCatalogApi(client).search(resolution, "a")
        FeiniuCatalogApi(client).search(resolution, "b")

        val cookie = state.snapshot().credentials.cookie
        assertTrue(cookie.contains("Trim-MC-token=t"), cookie)
        assertTrue(cookie.contains("sid=one"), "first response cookie must survive: $cookie")
        assertTrue(cookie.contains("theme=dark"), "second response cookie must survive: $cookie")
    }

    // F1 companion: a late logout must not clear an account switched in mid-flight
    @Test
    fun lateLogoutCannotClearNewAccount() = runTest {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("outgoing"))
        val client = FeiniuClient(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse {
                    state.set(FeiniuCredentials("new-account")) // login completes during logout
                    return response(request, """{"code":0}""")
                }
            },
            sessionState = state,
        )
        FeiniuAuthApi(client).logout(resolution)
        assertEquals("new-account", state.snapshot().credentials.token)
    }
}
