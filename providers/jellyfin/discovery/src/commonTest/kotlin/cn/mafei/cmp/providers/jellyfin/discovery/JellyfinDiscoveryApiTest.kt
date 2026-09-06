package cn.mafei.cmp.providers.jellyfin.discovery

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.core.JellyfinUnsupportedServerVersionError
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

class JellyfinDiscoveryApiTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private val base: HttpUrl = HttpUrl.parse(JellyfinFixtures.BASE_URL)

    private fun newApi(vararg responses: Pair<Int, String>): Pair<JellyfinDiscoveryApi, FakeJellyfinTransport> {
        val transport = FakeJellyfinTransport()
        responses.forEach { (status, body) -> transport.enqueueJson(status, body, base) }
        val client = JellyfinClient(
            JellyfinServerConfig(base, "MaFei", "Test", "device-1", "2.0.0"),
            transport,
            InMemoryJellyfinSessionState(),
        )
        return DefaultJellyfinDiscoveryApi(client) to transport
    }

    @Test
    fun publicServerInfoDecodesFixture() = runTest {
        val (api, transport) = newApi(200 to JellyfinFixtures.PUBLIC_SYSTEM_INFO_10_9)
        val info = api.publicServerInfo(base)

        assertEquals("TestServer", info.serverName)
        assertEquals("10.9.11", info.version)
        assertEquals("0123456789abcdef0123456789abcdef", info.id)
        assertEquals(true, info.startupWizardCompleted)
        assertEquals("https://jellyfin.test/System/Info/Public", transport.requests.single().url.toString())
    }

    @Test
    fun verifiedServerInfoAcceptsSupportedVersion() = runTest {
        val (api, _) = newApi(200 to JellyfinFixtures.PUBLIC_SYSTEM_INFO_10_9)
        val verified = api.verifiedServerInfo(base)
        assertEquals("10.9.11", verified.info.version)
    }

    @Test
    fun verifiedServerInfoRejectsOldServers() = runTest {
        val (api, _) = newApi(200 to JellyfinFixtures.PUBLIC_SYSTEM_INFO_TOO_OLD)
        val error = assertFailsWith<JellyfinUnsupportedServerVersionError> {
            api.verifiedServerInfo(base)
        }
        assertEquals("10.7.7", error.actualVersion)
        assertEquals("10.8.0", error.minimumSupported)
    }
}
