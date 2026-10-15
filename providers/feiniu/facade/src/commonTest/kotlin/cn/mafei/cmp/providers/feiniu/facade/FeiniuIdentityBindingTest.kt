package cn.mafei.cmp.providers.feiniu.facade

import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.providers.feiniu.auth.FeiniuAuthApi
import cn.mafei.cmp.providers.feiniu.catalog.FeiniuCatalogApi
import cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuSessionSnapshot
import cn.mafei.cmp.providers.feiniu.core.FeiniuSessionState
import cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.testkit.FakeFeiniuTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Mainline identity-binding regressions for 71955b2 (J1-J3), ported onto the
 * pinned-operation-context contract. Deterministic seams via delegate
 * overrides; no reflection into production state.
 */
class FeiniuIdentityBindingTest {

    private val resolution = FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Direct))

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private fun sampleRequest(): HttpRequest =
        HttpRequest.builder(cn.mafei.cmp.core.http_contract.HttpMethod.Get, HttpUrl.parse("https://feiniu.test/x")).build()

    private fun response(request: HttpRequest, body: String, cookie: String? = null) =
        HttpResponse(
            200,
            HttpHeaders.Builder().apply { if (cookie != null) add("Set-Cookie", cookie) }.build(),
            body.encodeToByteArray(), request.url, request.url,
        )

    // J1: a restore receipt binds the successful response to the ORIGINAL
    // identity — a preempting account is neither validated nor polluted.
    @Test
    fun restoreReceiptMustBindSuccessfulResponseCookieToOriginalIdentity() = runTest {
        val backing = InMemoryFeiniuSessionState()
        var switched = false
        val state = object : FeiniuSessionState by backing {
            override fun install(credentials: FeiniuCredentials): FeiniuSessionSnapshot {
                val receipt = backing.install(credentials)
                backing.install(FeiniuCredentials("new-account"))
                switched = true // preempt after install, before returning its exact receipt
                return receipt
            }
        }
        var sentToken: String? = null
        val client = FeiniuClient(sessionState = state, transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                sentToken = request.headers.first("Authorization")
                return response(request, """{"code":0,"data":{"username":"stored-user"}}""", "sid=stored-account-response; Path=/")
            }
        })
        FeiniuAuthApi(client).restore(resolution, FeiniuCredentials("stored-account"))
        assertTrue(switched)
        assertEquals("stored-account", sentToken)
        assertEquals("new-account", backing.snapshot().credentials.token)
        assertFalse(backing.snapshot().credentials.cookie.contains("sid=stored-account-response"),
            "Restore pinned old token but committed its response cookie to the new identity")
    }

    // J1: a logout whose context was captured before a switch pins the OLD
    // token for the server call and keeps its response off the new account.
    @Test
    fun logoutReceiptMustBindSuccessfulResponseCookieToOriginalIdentity() = runTest {
        val backing = InMemoryFeiniuSessionState(FeiniuCredentials("old-account"))
        var switched = false
        val state = object : FeiniuSessionState by backing {
            override fun snapshot(): FeiniuSessionSnapshot {
                val captured = backing.snapshot()
                if (!switched) {
                    switched = true
                    backing.install(FeiniuCredentials("new-account"))
                }
                return captured
            }
        }
        var sentToken: String? = null
        val client = FeiniuClient(sessionState = state, transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                sentToken = request.headers.first("Authorization")
                return response(request, """{"code":0}""", "sid=logout-response; Path=/")
            }
        })
        FeiniuAuthApi(client).logout(resolution)
        assertTrue(switched)
        assertEquals("old-account", sentToken)
        assertEquals("new-account", backing.snapshot().credentials.token)
        assertFalse(backing.snapshot().credentials.cookie.contains("sid=logout-response"),
            "Logout pinned old token but committed its response cookie to the new identity")
    }

    // J2: a same-identity cookie revision at the install CAS boundary must
    // re-base and retry, not discard a successful login.
    @Test
    fun sameIdentityCookieRevisionMustNotRejectSuccessfulLogin() = runTest {
        val backing = InMemoryFeiniuSessionState(FeiniuCredentials("old-account"))
        var armed = false
        var interrupted = false
        val state = object : FeiniuSessionState by backing {
            // J2 seam: at the login install boundary, apply one REAL
            // same-identity cookie update (identity unchanged, revision
            // moved) BEFORE delegating — the install must re-base, not fail.
            override fun installIdentityIfUnchanged(
                expectedIdentity: Long,
                credentials: FeiniuCredentials,
            ): FeiniuCredentials? {
                if (armed && !interrupted) {
                    interrupted = true
                    backing.update { it.copy(cookie = "sid=concurrent-refresh") }
                }
                return backing.installIdentityIfUnchanged(expectedIdentity, credentials)
            }
        }
        var sentToken: String? = null
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                armed = true
                sentToken = request.headers.first("Authorization")
                return response(request, """{"code":0,"token":"successful-login"}""")
            }
        }
        val client = FeiniuClient(sessionState = state, transport = transport)
        val result = runCatching { FeiniuAuthApi(client).login(resolution, "user", "pw") }
        assertTrue(interrupted, "CAS interleaving must actually execute")
        assertTrue(result.isSuccess,
            "Identity did not change, but successful login was discarded: ${result.exceptionOrNull()}")
        assertEquals("successful-login", backing.snapshot().credentials.token)
    }

    private fun response(request: HttpRequest, body: String) =
        HttpResponse(200, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)

    // J3: the public result carries THIS operation's credentials, never the
    // account that happened to be installed while the request was in flight.
    @Test
    fun oldOperationResultMustNotContainNewAccountsCredentials() = runTest {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account"))
        val client = FeiniuClient(sessionState = state, transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                assertEquals("old-account", request.headers.first("Authorization"))
                state.install(FeiniuCredentials("new-account"))
                return response(request, """{"code":0,"data":[]}""")
            }
        })
        val result = client.executeWithFallback(resolution, FeiniuApiCall(
            "/v/api/v1/item/list", FeiniuApiCall.FeiniuMethod.Get,
            authRequirement = FeiniuApiCall.AuthRequirement.Token,
        )) { _, _ -> "old-account-data" }
        assertEquals("new-account", state.snapshot().credentials.token)
        assertNotEquals("new-account", result.credentials.token,
            "Public result mixed old response data with the newly installed account credentials")
        assertEquals("old-account", result.credentials.token)
    }
}
