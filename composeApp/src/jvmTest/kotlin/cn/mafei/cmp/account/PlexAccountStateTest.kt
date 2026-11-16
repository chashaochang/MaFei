package cn.mafei.cmp

import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.data.repositories.session.SessionSelection
import cn.mafei.cmp.feature.connect.PlexConnectPhase
import cn.mafei.cmp.session.*
import kotlinx.coroutines.*
import kotlin.test.*

class PlexAccountStateTest {
    private class Accounts : AccountAccess {
        val fixture = PlexTestTransport()
        override val activeSelection: SessionSelection? = null
        var starts = 0
        var connections = 0
        var begin: suspend () -> PlexLoginChallenge = { fixture.challenge() }
        var await: suspend (PlexLoginChallenge) -> PlexLoginServers = { fixture.servers() }
        var connectAction: suspend () -> Unit = {}
        override suspend fun beginPlexLogin(): PlexLoginChallenge { starts++; return begin() }
        override suspend fun awaitPlexServers(challenge: PlexLoginChallenge) = await(challenge)
        override suspend fun connectPlex(servers: PlexLoginServers, serverId: String) {
            assertEquals("server-1", serverId)
            connections++
            connectAction()
        }
        override suspend fun savedConnections() = emptyList<SavedConnection>()
        override suspend fun savedPasswordKeys(connections: List<SavedConnection>) = emptySet<String>()
        override suspend fun savedPassword(connection: SavedConnection): String? = null
        override suspend fun switchAccount(connection: SavedConnection) = Unit
        override suspend fun deleteSavedConnection(connection: SavedConnection) = false
        override suspend fun connect(provider: ProviderKind, baseUrl: String, username: String, password: String, rememberPassword: Boolean) =
            error("Password flow must not be used")
    }

    @Test fun browserFailureStillAllowsPollingAndEmptyServersProduceModalFeedbackState() = runBlocking<Unit> {
        val accounts = Accounts()
        val release = CompletableDeferred<Unit>()
        accounts.await = { release.await(); accounts.fixture.servers() }
        var failure: String? = null
        val state = PlexAccountState(accounts, CoroutineScope(coroutineContext + Dispatchers.Unconfined), { false }, {}, { failure = it })
        state.authorize { error("No browser") }
        assertEquals(PlexConnectPhase.Waiting, state.ui.phase)
        assertEquals("无法打开 Plex 授权页面，请检查默认浏览器", failure)
        var url: String? = null
        state.reopen { url = it }
        assertTrue(assertNotNull(url).startsWith("https://app.plex.tv/auth#?"))
        accounts.fixture.emptyServers = true
        release.complete(Unit)
        yield()
        assertEquals(PlexConnectPhase.Idle, state.ui.phase)
        assertEquals("此 Plex 账号下没有可连接的影视服务器", failure)
    }

    @Test fun cancelledGenerationCannotReplaceANewerServerSelectionEvenIfTransportCompletesLate() = runBlocking<Unit> {
        val accounts = Accounts()
        val first = CompletableDeferred<Unit>()
        var awaits = 0
        accounts.await = {
            if (++awaits == 1) withContext(NonCancellable) { first.await() }
            accounts.fixture.servers()
        }
        var failure: String? = null
        val state = PlexAccountState(accounts, CoroutineScope(coroutineContext + Dispatchers.Unconfined), { false }, {}, { failure = it })
        state.authorize {}
        assertEquals(PlexConnectPhase.Waiting, state.ui.phase)
        state.cancel()
        state.authorize {}
        assertEquals(PlexConnectPhase.Choosing, state.ui.phase)
        first.complete(Unit)
        yield()
        assertEquals(PlexConnectPhase.Choosing, state.ui.phase)
        assertEquals(listOf("server-1"), state.ui.servers.map { it.id })
        assertNull(failure)
        state.cancel()
    }

    @Test fun connectionDeduplicatesSubmissionsAndRestoresChoicesAfterFailureForRetry() = runBlocking<Unit> {
        val accounts = Accounts()
        val release = CompletableDeferred<Unit>()
        accounts.connectAction = { release.await(); error("fixture failure") }
        var busy = false
        var failure: String? = null
        val state = PlexAccountState(accounts, CoroutineScope(coroutineContext + Dispatchers.Unconfined), { busy }, { busy = it }, { failure = it })
        state.authorize {}
        state.connect("unknown")
        assertEquals(0, accounts.connections)
        state.connect("server-1")
        state.connect("server-1")
        assertEquals(1, accounts.connections)
        assertTrue(busy)
        assertEquals(PlexConnectPhase.Connecting, state.ui.phase)
        state.authorize {}
        assertEquals(1, accounts.starts)
        release.complete(Unit)
        yield()
        assertFalse(busy)
        assertNotNull(failure)
        assertEquals(PlexConnectPhase.Choosing, state.ui.phase)
        accounts.connectAction = {}
        var connected = false
        state.connect("server-1") { connected = true }
        assertTrue(connected)
        assertNull(failure)
        assertFalse(busy)
        assertEquals(PlexConnectPhase.Idle, state.ui.phase)
    }

    @Test fun cancellationReleasesBusyAndDoesNotNavigateWhenConnectionCompletesLate() = runBlocking<Unit> {
        val accounts = Accounts()
        val release = CompletableDeferred<Unit>()
        accounts.connectAction = { withContext(NonCancellable) { release.await() } }
        var busy = false
        var connected = false
        var failure: String? = null
        val state = PlexAccountState(accounts, CoroutineScope(coroutineContext + Dispatchers.Unconfined), { busy }, { busy = it }, { failure = it })
        state.authorize {}
        state.connect("server-1") { connected = true }
        assertTrue(busy)
        state.cancel()
        assertFalse(busy)
        release.complete(Unit)
        yield()
        assertFalse(connected)
        assertNull(failure)
        assertEquals(PlexConnectPhase.Idle, state.ui.phase)
    }
}
