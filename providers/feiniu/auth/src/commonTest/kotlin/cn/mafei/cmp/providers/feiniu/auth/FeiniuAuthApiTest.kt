package cn.mafei.cmp.providers.feiniu.auth

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.feiniu.core.FeiniuAuthenticationError
import cn.mafei.cmp.providers.feiniu.core.FeiniuAuthenticationExpiredError
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.FeiniuRuntimeSource
import cn.mafei.cmp.providers.feiniu.testkit.FakeFeiniuTransport
import cn.mafei.cmp.providers.feiniu.testkit.FeiniuFixtures
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun runSuspendTest(block: suspend () -> Unit) {
    val result = block.startCoroutineUninterceptedOrReturn(NoopContinuation)
    check(result !== COROUTINE_SUSPENDED) { "test body suspended unexpectedly" }
}

private object NoopContinuation : Continuation<Unit> {
    override val context = EmptyCoroutineContext
    override fun resumeWith(result: Result<Unit>) {
        result.exceptionOrNull()?.let { throw it }
    }
}

/** Fixed clock/random so the Authx header is fully deterministic. */
private class FixedRuntime : FeiniuRuntimeSource {
    override fun nowEpochMs(): Long = 1_700_000_000_000L
    override fun random(): Double = 0.137137 // nonce 123455 (legacy truncation)
}

class FeiniuAuthApiTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private class Fixture {
        val transport = FakeFeiniuTransport()
        val resolution = FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Direct))
        val client = FeiniuClient(runtime = FixedRuntime(), transport = transport)
        val api = FeiniuAuthApi(client)
    }

    @Test
    fun loginStoresTokenAndSendsDeterministicHeaders() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.LOGIN_OK)

        val credentials = fixture.api.login(fixture.resolution, "demo", "pw")
        assertEquals("fixture-token-0000000000000001", credentials.token)

        val sent = fixture.transport.requests.single()
        assertEquals(HttpUrl.parse("https://feiniu.test/v/api/v1/login"), sent.url)
        val authx = sent.headers.first("Authx")!!
        assertTrue(authx.startsWith("nonce=") && authx.contains("timestamp="), authx)
        // PascalCase body with frozen app_name
        assertEquals(
            """{"username":"demo","password":"pw","app_name":"trimemedia-web"}""",
            (sent.body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text,
        )
        // anonymous login carries no Authorization/Cookie
        assertNull(sent.headers.first("Authorization"))
        assertNull(sent.headers.first("Cookie"))
    }

    @Test
    fun failedLoginSurfacesAsAuthenticationError() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.LOGIN_BAD_CREDENTIALS)

        val error = assertFailsWith<FeiniuAuthenticationError> {
            fixture.api.login(fixture.resolution, "demo", "wrong")
        }
        assertEquals("用户名或密码错误", error.message)
    }

    @Test
    fun restoreValidatesStoredCredentialsAndExpiresGracefully() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.USER_INFO, setCookies = listOf("Trim-MC-token=rotated; Path=/"))

        val info = fixture.api.restore(
            fixture.resolution,
            FeiniuCredentials(token = "stored-token", cookie = "sid=1"),
        )
        assertEquals("demo", info.username)
        // legacy semantics: on cookie-carried calls the STORED token wins over Set-Cookie
        assertEquals("stored-token", fixture.client.sessionState.snapshot().credentials.token)
        val sent = fixture.transport.requests.single()
        assertEquals("stored-token", sent.headers.first("Authorization"))
        assertTrue(sent.headers.first("Cookie")!!.contains("Trim-MC-token=stored-token"))
    }

    @Test
    fun expiredRestoreClearsSessionAndThrowsExpired() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.AUTH_EXPIRED)

        assertFailsWith<FeiniuAuthenticationExpiredError> {
            fixture.api.restore(fixture.resolution, FeiniuCredentials(token = "stale"))
        }
        assertTrue(fixture.client.sessionState.snapshot().credentials.token.isEmpty())
    }

    @Test
    fun logoutAlwaysClearsTheSession() = runTest {
        val fixture = Fixture()
        fixture.client.sessionState.set(FeiniuCredentials(token = "tok"))
        fixture.transport.enqueueNoContent()

        fixture.api.logout(fixture.resolution)
        assertTrue(fixture.client.sessionState.snapshot().credentials.token.isEmpty())
    }
}
