package cn.mafei.cmp

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.session.SessionSelection
import cn.mafei.cmp.feature.connect.ConnectionHistoryItem
import cn.mafei.cmp.session.*
import kotlinx.coroutines.*
import kotlin.test.*

class SignedOutAccountStateTest {
    private class Accounts : AccountAccess {
        val server = Server(ServerId("server"), ProviderKind.Jellyfin, "Fixture", "https://media.test")
        val connection = SavedConnection(server, Account(AccountId("a"), server.id, server.provider, "a"), "a", "a".repeat(32))
        override val activeSelection: SessionSelection? = null
        var reads = 0
        var load: suspend () -> List<SavedConnection> = { listOf(connection) }
        var switch: suspend () -> Unit = { error("credentials unavailable") }
        var connectAction: suspend () -> Unit = {}
        var mayDelete = true
        override suspend fun savedConnections(): List<SavedConnection> { reads++; return load() }
        override suspend fun savedPasswordKeys(connections: List<SavedConnection>) = setOf(connection.credentialKey)
        override suspend fun savedPassword(connection: SavedConnection) = "remembered"
        override suspend fun switchAccount(connection: SavedConnection) = switch()
        override suspend fun deleteSavedConnection(connection: SavedConnection) = mayDelete
        override suspend fun connect(provider: ProviderKind, baseUrl: String, username: String, password: String,
            rememberPassword: Boolean) = connectAction()
        fun item() = ConnectionHistoryItem(connection.credentialKey, server.provider, server.id, server.name,
            connection.account.displayName, server.baseUrl, isCurrent = false)
    }

    @Test fun concurrentRefreshIsDeduplicatedAndCancellationIsNotAnError() = runBlocking<Unit> {
        val accounts = Accounts()
        val entered = CompletableDeferred<Unit>()
        accounts.load = { entered.complete(Unit); awaitCancellation() }
        val job = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + job)
        val state = SignedOutAccountState(accounts, scope, { false }, {}, {})
        state.loadHistory(); state.loadHistory()
        entered.await()
        assertEquals(1, accounts.reads)
        assertTrue(state.historyLoading)
        job.cancelAndJoin()
        assertFalse(state.historyLoading)
        assertNull(state.historyError)
    }

    @Test fun successfulDeletionKeepsTheRemainingRowsWithoutReloading() = runBlocking<Unit> {
        val accounts = Accounts()
        val scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined)
        val state = SignedOutAccountState(accounts, scope, { false }, {}, {})
        state.loadHistory()
        state.deleteAccount(accounts.item())
        assertEquals(1, accounts.reads)
        assertTrue(state.historyConnections.isEmpty())
        assertTrue(state.rememberedPasswordKeys.isEmpty())
        assertFalse(state.historyLoading)
        assertNull(state.deletingAccountKey)
    }

    @Test fun activeAccountRefusalPreservesHistoryAndPasswordMarker() = runBlocking<Unit> {
        val accounts = Accounts().apply { mayDelete = false }
        val state = SignedOutAccountState(accounts, CoroutineScope(coroutineContext + Dispatchers.Unconfined), { false }, {}, {})
        state.loadHistory(); state.deleteAccount(accounts.item())
        assertEquals(listOf(accounts.connection), state.historyConnections)
        assertTrue(accounts.connection.credentialKey in state.rememberedPasswordKeys)
        assertNotNull(state.historyError)
    }

    @Test fun failedRestoreReturnsLoginPrefillButCancelledRestoreDoesNot() = runBlocking<Unit> {
        val accounts = Accounts()
        var busy = false
        val state = SignedOutAccountState(accounts, CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            { busy }, { busy = it }, {})
        state.loadHistory()
        var prefill: Pair<SavedConnection, String?>? = null
        state.select(accounts.item()) { c, p -> prefill = c to p }
        assertEquals(accounts.connection to "remembered", prefill)
        assertFalse(busy)
        prefill = null
        accounts.switch = { throw CancellationException("flow removed") }
        state.select(accounts.item()) { c, p -> prefill = c to p }
        assertNull(prefill)
        assertFalse(busy)
        assertNull(state.historyError)
    }

    @Test fun connectRejectsDuplicateSubmitAndCancellationReleasesBusy() = runBlocking<Unit> {
        val accounts = Accounts()
        val entered = CompletableDeferred<Unit>()
        var calls = 0
        accounts.connectAction = { calls++; entered.complete(Unit); awaitCancellation() }
        val job = SupervisorJob()
        var busy = false
        var error: String? = null
        val state = SignedOutAccountState(accounts, CoroutineScope(coroutineContext + job),
            { busy }, { busy = it }, { error = it })
        repeat(2) { state.connect(ProviderKind.Jellyfin, "https://media.test", "a", "test", true) }
        entered.await()
        assertEquals(1, calls)
        job.cancelAndJoin()
        assertFalse(busy)
        assertNull(error)
    }
}
