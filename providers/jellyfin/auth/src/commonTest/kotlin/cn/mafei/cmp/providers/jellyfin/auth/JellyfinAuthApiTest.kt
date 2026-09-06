package cn.mafei.cmp.providers.jellyfin.auth

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationExpiredError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
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

class JellyfinAuthApiTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private companion object {
        val base: HttpUrl = HttpUrl.parse(JellyfinFixtures.BASE_URL)
    }

    private class Fixture {
        val transport = FakeJellyfinTransport()
        val sessionState = InMemoryJellyfinSessionState()
        val client = JellyfinClient(
            JellyfinServerConfig(base, "MaFei", "Test", "device-1", "2.0.0"),
            transport,
            sessionState,
        )
        val api = DefaultJellyfinAuthApi(client)
    }

    @Test
    fun publicUsersDecodeWithTolerantOptionals() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.PUBLIC_USERS, base)

        val users = fixture.api.publicUsers()
        assertEquals(3, users.size)
        assertEquals("demo", users[0].name)
        assertEquals("1112131415161718", users[0].primaryImageTag)
        assertEquals(false, users[1].hasPassword)
        assertNull(users[2].primaryImageTag)
    }

    @Test
    fun authenticateByNameStoresSessionAndSendsPreLoginHeader() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.AUTHENTICATION_RESULT, base)

        val result = fixture.api.authenticateByName("demo", "correct-horse")
        assertEquals("a1b2c3d4e5f6a7b8a1b2c3d4e5f6a7b8", result.session.userId)
        assertEquals("0123456789abcdef0123456789abcdef", fixture.sessionState.current.value?.serverId)

        val sent = fixture.transport.requests.single()
        val header = sent.headers.first("Authorization")!!
        assertTrue(header.contains("Client=\"MaFei\""), header)
        // pre-login calls carry no Token part
        assertNull(header.split(", ").firstOrNull { it.startsWith("Token") }, header)
        val body = (sent.body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(body.contains("\"Username\":\"demo\""), body)
        assertTrue(body.contains("\"Pw\":\"correct-horse\""), body)
    }

    @Test
    fun failedLoginLeavesNoSessionBehind() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(401, JellyfinFixtures.PROBLEM_DETAILS_401, base)

        assertFailsWith<JellyfinAuthenticationError> {
            fixture.api.authenticateByName("demo", "wrong")
        }
        assertNull(fixture.sessionState.current.value)
    }

    @Test
    fun restoreSessionValidatesViaSystemInfo() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.SYSTEM_INFO, base)

        val info = fixture.api.restoreSession("a1b2c3d4e5f6a7b8a1b2c3d4e5f6a7b8", "restored-token")
        assertEquals("10.9.11", info.version)
        assertEquals("restored-token", fixture.sessionState.current.value?.accessToken)

        val header = fixture.transport.requests.single().headers.first("Authorization")!!
        assertTrue(header.contains("Token=\"restored-token\""), header)
    }

    @Test
    fun restoreSessionClearsStateOnExpiredToken() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(401, JellyfinFixtures.PROBLEM_DETAILS_401, base)

        assertFailsWith<JellyfinAuthenticationExpiredError> {
            fixture.api.restoreSession("u1", "expired-token")
        }
        assertNull(fixture.sessionState.current.value)
    }

    @Test
    fun logoutClearsSessionEvenOnFailure() = runTest {
        val fixture = Fixture()
        fixture.sessionState.set(cn.mafei.cmp.providers.jellyfin.core.JellyfinSession("u1", "tok"))
        fixture.transport.enqueueJson(500, "", base)

        assertFailsWith<cn.mafei.cmp.providers.jellyfin.core.JellyfinServerError> {
            fixture.api.logout()
        }
        assertNull(fixture.sessionState.current.value)
    }
}
