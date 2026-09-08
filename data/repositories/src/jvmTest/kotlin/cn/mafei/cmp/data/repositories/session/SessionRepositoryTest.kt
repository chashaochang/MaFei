package cn.mafei.cmp.data.repositories.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.secure_credential_api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlin.test.*

class SessionRepositoryTest {
    private val token = CredentialAlias("test/token")
    private val cookie = CredentialAlias("test/cookie")
    private fun snapshot(generation: Long = 1, revision: Long = 1, value: String = "a") =
        SessionCredentialSnapshot(generation, revision, mapOf(token to SecureSecret.AuthToken("Bearer", value)))
    private fun selection(id: String = "a") = SessionSelection(ProviderKind.Feiniu, ServerId("s"), AccountId(id))
    private fun source(flow: MutableStateFlow<SessionCredentialSnapshot>) = StateFlowCredentialSource(flow) { it }
    private class Store : SecureCredentialStore {
        val values = mutableMapOf<CredentialAlias, SecureSecret>()
        var beforeSave: suspend (CredentialAlias) -> Unit = {}
        var failDelete = false
        override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) { beforeSave(alias); values[alias] = secret }
        override suspend fun read(alias: CredentialAlias) = values[alias]
        override suspend fun delete(alias: CredentialAlias): Boolean {
            if (failDelete) throw CredentialAccessException(CredentialFailureKind.StorageFailure)
            return values.remove(alias) != null
        }
        override suspend fun exists(alias: CredentialAlias) = alias in values
        override suspend fun listAliases(prefix: String?) = values.keys.filter { prefix == null || it.value.startsWith(prefix) }
        override suspend fun deleteAll(prefix: String?): Int = error("must delete only owned aliases")
    }

    @Test fun newerRevisionWinsAndOldHandleCannotOverwriteSameAlias() = runBlocking {
        val store = Store(); val repo = SessionRepository(store)
        val a = MutableStateFlow(snapshot()); val old = repo.select(selection(), setOf(token), source(a))
        a.value = snapshot(revision = 2, value = "new"); assertTrue(repo.persistLatest(old))
        a.value = snapshot(revision = 1, value = "old"); assertFalse(repo.persistLatest(old))
        val b = MutableStateFlow(snapshot(value = "b"))
        repo.select(selection("b"), setOf(token), source(b))
        assertFalse(repo.persistLatest(old)); assertEquals("b", (store.values[token] as SecureSecret.AuthToken).token)
    }

    @Test fun sameAccountNewGenerationRevokesOldHandleAndDifferentAliasIsIsolated() = runBlocking {
        val store = Store(); val repo = SessionRepository(store)
        val a = MutableStateFlow(snapshot()); val old = repo.select(selection(), setOf(token), source(a))
        val b = MutableStateFlow(SessionCredentialSnapshot(2, 2, mapOf(cookie to SecureSecret.CookieText("b"))))
        val fresh = repo.select(selection(), setOf(cookie), source(b))
        assertFalse(repo.logout(old)); assertFalse(repo.persistLatest(old))
        assertNull(store.values[token]); assertEquals(selection(), repo.state.value.selection)
        assertTrue(repo.logout(fresh)); assertTrue(store.values.isEmpty())
    }

    @Test fun logoutWaitsForInflightWriteAndCannotBeCancelledIntoTokenRevival() = runBlocking {
        withTimeout(5000) {
            val store = Store(); val repo = SessionRepository(store)
            val flow = MutableStateFlow(snapshot()); val handle = repo.select(selection(), setOf(token), source(flow))
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            store.beforeSave = { entered.complete(Unit); release.await() }
            flow.value = snapshot(revision = 2)
            val writer = launch { repo.persistLatest(handle) }; entered.await()
            val logout = launch(start = CoroutineStart.UNDISPATCHED) { repo.logout(handle) }
            logout.cancel(); writer.cancel(); release.complete(Unit)
            writer.join(); logout.join()
            assertTrue(store.values.isEmpty()); assertNull(repo.state.value.selection)
        }
    }

    @Test fun failedPartialWriteIsRevokedAndCleanupFailureBlocksNextSelectionUntilRetry() = runBlocking {
        val store = Store(); val repo = SessionRepository(store)
        val flow = MutableStateFlow(SessionCredentialSnapshot(1, 1, mapOf(token to SecureSecret.AuthToken("Bearer", "a"), cookie to SecureSecret.CookieText("a"))))
        store.beforeSave = { if (it == cookie) { store.failDelete = true; throw CredentialAccessException(CredentialFailureKind.StorageFailure) } }
        assertFailsWith<CredentialAccessException> { repo.select(selection(), setOf(token, cookie), source(flow)) }
        assertNull(repo.state.value.selection); assertEquals(SessionPersistenceStatus.CleanupRequired, repo.state.value.status)
        assertFailsWith<CredentialAccessException> { repo.select(selection("b"), setOf(token), source(MutableStateFlow(snapshot(value = "b")))) }
        store.failDelete = false; store.beforeSave = {}
        repo.retryCleanup(); assertTrue(store.values.isEmpty())
        repo.select(selection("b"), setOf(token), source(MutableStateFlow(snapshot(value = "b"))))
        assertEquals("b", (store.values[token] as SecureSecret.AuthToken).token)
    }

    @Test fun sourceIdentityChangeRequiresExplicitReselectionAndDeletesOldCredentials() = runBlocking {
        val store = Store(); val repo = SessionRepository(store); val flow = MutableStateFlow(snapshot())
        val handle = repo.select(selection(), setOf(token), source(flow))
        flow.value = snapshot(generation = 2, revision = 2, value = "other")
        assertFalse(repo.persistLatest(handle)); assertTrue(store.values.isEmpty()); assertNull(repo.state.value.selection)
    }

    @Test fun observationConsumesAuthoritativeCurrentInsteadOfQueuedOldSnapshot() = runBlocking {
        withTimeout(5000) {
            val store = Store(); val repo = SessionRepository(store); val flow = MutableStateFlow(snapshot())
            val handle = repo.select(selection(), setOf(token), source(flow))
            val observer = launch(start = CoroutineStart.UNDISPATCHED) { repo.observe(handle) }
            flow.value = snapshot(revision = 2, value = "rotated")
            yield(); assertEquals("rotated", (store.values[token] as SecureSecret.AuthToken).token)
            observer.cancelAndJoin(); repo.logout(handle)
            flow.value = snapshot(revision = 3, value = "late"); assertTrue(store.values.isEmpty())
        }
    }

    @Test fun queuedAccountSwitchDrainsOldWriteBeforeInstallingSharedAlias() = runBlocking {
        withTimeout(5000) {
            val store = Store(); val repo = SessionRepository(store); val flow = MutableStateFlow(snapshot())
            val old = repo.select(selection(), setOf(token), source(flow))
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            store.beforeSave = { entered.complete(Unit); release.await() }
            flow.value = snapshot(revision = 2, value = "late-old")
            val writer = launch { repo.persistLatest(old) }; entered.await()
            val switch = async(start = CoroutineStart.UNDISPATCHED) {
                repo.select(selection("b"), setOf(token), source(MutableStateFlow(snapshot(value = "b"))))
            }
            release.complete(Unit); writer.join(); switch.await()
            assertEquals("b", (store.values[token] as SecureSecret.AuthToken).token)
            assertFalse(repo.logout(old)); assertEquals(selection("b"), repo.state.value.selection)
        }
    }

    @Test fun cancelledBeforeSelectionDoesNotRemoveExistingSession() = runBlocking {
        val store = Store(); val repo = SessionRepository(store)
        repo.select(selection(), setOf(token), source(MutableStateFlow(snapshot())))
        val job = launch(start = CoroutineStart.LAZY) {
            repo.select(selection("b"), setOf(token), source(MutableStateFlow(snapshot(value = "b"))))
        }
        job.cancelAndJoin()
        assertEquals(selection(), repo.state.value.selection)
        assertEquals("a", (store.values[token] as SecureSecret.AuthToken).token)
    }

    @Test fun storeCancellationRevokesPartialWriteAndNeverPublishesReady() = runBlocking {
        val store = Store(); val repo = SessionRepository(store)
        store.beforeSave = { throw CancellationException("backend cancelled") }
        assertFailsWith<CancellationException> {
            repo.select(selection(), setOf(token), source(MutableStateFlow(snapshot())))
        }
        assertNull(repo.state.value.selection); assertTrue(store.values.isEmpty())
        assertEquals(SessionPersistenceStatus.SignedOut, repo.state.value.status)
    }

    @Test fun removedSecretDeletesOnlyOwnedAliasAndMalformedProjectionRevokes() = runBlocking {
        val store = Store(); val repo = SessionRepository(store)
        store.values[cookie] = SecureSecret.CookieText("unrelated")
        val flow = MutableStateFlow(snapshot()); val handle = repo.select(selection(), setOf(token), source(flow))
        flow.value = SessionCredentialSnapshot(1, 2, emptyMap())
        assertTrue(repo.persistLatest(handle)); assertNull(store.values[token]); assertNotNull(store.values[cookie])
        flow.value = SessionCredentialSnapshot(1, 3, mapOf(cookie to SecureSecret.CookieText("unexpected")))
        assertFailsWith<IllegalArgumentException> { repo.persistLatest(handle) }
        assertNull(repo.state.value.selection)
        assertEquals(SecureSecret.CookieText("unrelated"), store.values[cookie])
    }

    @Test fun foreignRepositoryHandleCannotDeleteOrWrite() = runBlocking {
        val store = Store(); val first = SessionRepository(store); val second = SessionRepository(Store())
        val handle = first.select(selection(), setOf(token), source(MutableStateFlow(snapshot())))
        assertFalse(second.logout(handle)); assertFalse(second.persistLatest(handle))
        assertNotNull(store.values[token])
        Unit
    }

    @Test fun switchingAccountPreservesOtherAccountCredentialsWithDistinctAliases() = runBlocking {
        val store = Store(); val repo = SessionRepository(store)
        val old = repo.select(selection(), setOf(token), source(MutableStateFlow(snapshot())))
        val next = MutableStateFlow(SessionCredentialSnapshot(1, 1, mapOf(cookie to SecureSecret.CookieText("account-b"))))
        val fresh = repo.select(selection("b"), setOf(cookie), source(next))
        assertEquals("a", (store.values[token] as SecureSecret.AuthToken).token)
        assertFalse(repo.logout(old))
        repo.logout(fresh)
        assertNotNull(store.values[token]); assertNull(store.values[cookie])
    }

    @Test fun identityChangedWhileSaveSuspendsIsRevokedBeforePersistenceReturns() = runBlocking {
        withTimeout(5000) {
            val store = Store(); val repo = SessionRepository(store); val flow = MutableStateFlow(snapshot())
            val handle = repo.select(selection(), setOf(token), source(flow))
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            store.beforeSave = { entered.complete(Unit); release.await() }
            flow.value = snapshot(revision = 2)
            val pending = async { repo.persistLatest(handle) }; entered.await()
            val statuses = mutableListOf<SessionPersistenceStatus>()
            val observer = launch(Dispatchers.Unconfined) { repo.state.collect { statuses.add(it.status) } }
            flow.value = snapshot(generation = 2, revision = 3, value = "new-login")
            release.complete(Unit)
            assertFalse(pending.await()); assertTrue(store.values.isEmpty()); assertNull(repo.state.value.selection)
            observer.cancelAndJoin()
            assertFalse(SessionPersistenceStatus.Ready in statuses, "Changed identity must not publish Ready even transiently")
        }
    }

    @Test fun failedLogoutRevokesLeaseAndRetryRemovesResidualCredential() = runBlocking {
        val store = Store(); val repo = SessionRepository(store); val flow = MutableStateFlow(snapshot())
        val handle = repo.select(selection(), setOf(token), source(flow))
        store.failDelete = true
        assertFailsWith<CredentialAccessException> { repo.logout(handle) }
        assertEquals(SessionPersistenceStatus.CleanupRequired, repo.state.value.status)
        flow.value = snapshot(revision = 2, value = "must-not-write")
        assertFalse(repo.persistLatest(handle)); assertNull(repo.state.value.selection)
        store.failDelete = false; repo.retryCleanup()
        assertTrue(store.values.isEmpty()); assertEquals(SessionPersistenceStatus.SignedOut, repo.state.value.status)
    }

    @Test fun cancelledQueuedSelectionCannotReplaceCurrentAccountOrWriteCredentials() = runBlocking {
        withTimeout(5000) {
            val store = Store(); val repo = SessionRepository(store); val flow = MutableStateFlow(snapshot())
            val handle = repo.select(selection(), setOf(token), source(flow))
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            store.beforeSave = { entered.complete(Unit); release.await() }
            flow.value = snapshot(revision = 2, value = "updated-a")
            val pending = launch { repo.persistLatest(handle) }; entered.await()
            val selectionB = launch(start = CoroutineStart.UNDISPATCHED) {
                repo.select(selection("b"), setOf(cookie), source(MutableStateFlow(
                    SessionCredentialSnapshot(1, 1, mapOf(cookie to SecureSecret.CookieText("must-not-write-b")))
                )))
            }
            selectionB.cancel()
            release.complete(Unit); pending.join(); selectionB.join()
            assertEquals(selection(), repo.state.value.selection)
            assertEquals("updated-a", (store.values[token] as SecureSecret.AuthToken).token)
            assertNull(store.values[cookie])
        }
    }

    @Test fun cancelledQueuedPersistenceDoesNotStartAnotherSave() = runBlocking {
        withTimeout(5000) {
            val store = Store(); val repo = SessionRepository(store); val flow = MutableStateFlow(snapshot())
            val handle = repo.select(selection(), setOf(token), source(flow))
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            var saves = 0
            store.beforeSave = { saves++; entered.complete(Unit); release.await() }
            flow.value = snapshot(revision = 2, value = "admitted")
            val first = launch { repo.persistLatest(handle) }; entered.await()
            flow.value = snapshot(revision = 3, value = "queued-cancelled")
            val queued = launch(start = CoroutineStart.UNDISPATCHED) { repo.persistLatest(handle) }
            // Queue cancellation completes without waiting for the active save.
            queued.cancelAndJoin()
            release.complete(Unit); first.join()
            assertEquals(1, saves)
            assertEquals("admitted", (store.values[token] as SecureSecret.AuthToken).token)
            assertEquals(2L, repo.state.value.persistedRevision)
        }
    }
}
