package cn.mafei.cmp.data.repositories.session

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.core.storage_api.SettingsStorage
import kotlinx.serialization.Serializable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Non-sensitive current selection; never holds SDK credentials or URLs. */
@Serializable
data class SessionSelection(val provider: ProviderKind, val serverId: ServerId, val accountId: AccountId)

/**
 * Recovery material only. The composition boundary must still install a real
 * SDK session, then select/observe its live authoritative source. The recorded
 * generation/revision describe the old process; do not assign them to the SDK's
 * new live generation counters or treat this object as an active SessionHandle.
 */
class RestoredSession internal constructor(val selection: SessionSelection, val snapshot: SessionCredentialSnapshot) {
    override fun toString() = "RestoredSession(credentials=redacted)"
}

/** Projected secure-store values, not a new Provider credential model. */
class SessionCredentialSnapshot(
    val identityGeneration: Long,
    val revision: Long,
    secrets: Map<CredentialAlias, SecureSecret>,
) {
    val secrets: Map<CredentialAlias, SecureSecret> = secrets.toMap()
    init { require(identityGeneration >= 0 && revision >= 0) }
    override fun toString() = "SessionCredentialSnapshot(identityGeneration=$identityGeneration, revision=$revision, secrets=redacted)"
}

/**
 * [current] must project the authoritative state synchronously, not an asynchronously
 * collected mirror or a request receipt. [updates] only wakes persistence; queued
 * values are deliberately not trusted for writes.
 */
interface AuthoritativeCredentialSource {
    fun current(): SessionCredentialSnapshot
    val updates: Flow<SessionCredentialSnapshot>
}

/**
 * Directly consumes an SDK's existing StateFlow without adding SDK dependencies.
 * For Feiniu pass facade.credentials, projecting identityGeneration/revision and
 * snapshot.credentials into AuthToken/CookieText. Never pass FeiniuCallResult.
 * The projection must be pure and must not log the SDK snapshot (its toString
 * can contain secrets). Alias allocation belongs to the composition boundary.
 */
class StateFlowCredentialSource<T>(
    private val authoritative: StateFlow<T>,
    private val project: (T) -> SessionCredentialSnapshot,
) : AuthoritativeCredentialSource {
    override fun current() = project(authoritative.value)
    override val updates: Flow<SessionCredentialSnapshot> = authoritative.map(project)
}

enum class SessionPersistenceStatus { SignedOut, Ready, Writing, CleanupRequired }

data class SessionRepositoryState(
    val selection: SessionSelection? = null,
    val status: SessionPersistenceStatus = SessionPersistenceStatus.SignedOut,
    val persistedRevision: Long? = null,
)

/** Opaque, reference-owned lease. Re-selecting even the same account revokes it. */
class SessionHandle internal constructor(
    val selection: SessionSelection,
    internal val aliases: Set<CredentialAlias>,
    internal val source: AuthoritativeCredentialSource,
    internal val identityGeneration: Long,
) {
    internal var persistedRevision: Long = -1
}

/**
 * One process-local writer for the secure aliases managed by this repository.
 * All callers using those aliases MUST share this instance; migration/other
 * writers must run before it starts. This is not a cross-process transaction.
 *
 * Selection/persistence acquire the mutex cancellably and recheck cancellation
 * after admission. Only their actual storage critical section is NonCancellable.
 * Writes and deletion drain under the same mutex. Once admitted, a mutation
 * completes (or reports cleanup failure) even if its
 * caller cancels; cancellation is not proof of rollback. In particular logout
 * queues deletion behind any accepted write, so a late save cannot resurrect it.
 * Platform operations must eventually return: a permanently hung backend also
 * blocks logout; a timeout cannot make an uncooperative write safe to abandon.
 *
 * Failed partial writes revoke selection, delete the entire owned alias set,
 * and retain failed deletions as a barrier to further selection. No Ready state
 * is exposed until every save/delete succeeds. When [settings] is supplied, a
 * non-secret Pending/Committed index gates whole-group recovery after process
 * interruption. Interrupted writes fail closed (requiring login), rather than
 * recovering a mixed Token/Cookie pair. This is not cross-store atomicity or
 * cross-process locking: one repository must own both aliases and the index.
 * Without settings this retains its original process-local-only behavior.
 */
class SessionRepository(
    private val store: SecureCredentialStore,
    settings: SettingsStorage? = null,
    private val reportStage: (String) -> Unit = {},
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(SessionRepositoryState())
    val state: StateFlow<SessionRepositoryState> = mutableState.asStateFlow()
    private var active: SessionHandle? = null
    private val ownedAliases = mutableSetOf<CredentialAlias>()
    private val accountIndex = settings?.let(::SessionAccountIndex)
    private var cleanupSelection: SessionSelection? = null

    /** Committed metadata only; missing secure material is rejected by restore, not fabricated. */
    suspend fun savedAccounts(): List<SessionSelection> = mutex.withLock {
        accountIndex?.read()?.accounts.orEmpty().filter { it.committed && it.populatedAliases.isNotEmpty() }.map { it.selection }
    }

    /** Never falls back to another saved account after logout. Does not publish Ready. */
    suspend fun restoreCurrent(): RestoredSession? = mutex.withLock {
        val index = accountIndex?.read() ?: return@withLock null
        restore(index.accounts.find { it.selection == index.current })
    }

    suspend fun restoreAccount(selection: SessionSelection): RestoredSession? = mutex.withLock {
        restore(accountIndex?.read()?.accounts?.find { it.selection == selection })
    }

    /**
     * Deletes the committed credential group for an inactive saved account.
     * The durable index is removed only after every indexed secret is gone, so
     * an interrupted deletion can be retried without losing ownership data.
     */
    suspend fun removeSavedAccount(selection: SessionSelection): Boolean = withContext(NonCancellable) {
        mutex.withLock {
            check(active?.selection != selection) { "Cannot remove the active account" }
            val index = accountIndex ?: return@withLock false
            val entry = index.read().accounts.firstOrNull { it.selection == selection } ?: return@withLock false
            var failure: Throwable? = null
            for (value in entry.aliases) {
                try {
                    store.delete(CredentialAlias(value))
                } catch (error: Throwable) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                }
            }
            if (failure != null) throw failure
            index.remove(selection)
            true
        }
    }

    private suspend fun restore(entry: SessionAccountIndex.Entry?): RestoredSession? {
        if (entry == null || !entry.committed || entry.populatedAliases.isEmpty()) return null
        val secrets = mutableMapOf<CredentialAlias, SecureSecret>()
        for (value in entry.populatedAliases) {
            val alias = CredentialAlias(value)
            secrets[alias] = store.read(alias) ?: return null
        }
        return RestoredSession(entry.selection, SessionCredentialSnapshot(entry.identityGeneration, entry.revision, secrets))
    }

    /**
     * Revokes the prior write lease, not another account's saved login. Distinct
     * account aliases survive switching; re-selecting the same account replaces
     * its previous alias set. Stable provider/server/account-scoped aliases are
     * recommended. Even if aliases are reused, old handles cannot delete them.
     * Persistent mode rejects aliases already owned by another indexed account.
     */
    suspend fun select(
        selection: SessionSelection,
        aliases: Set<CredentialAlias>,
        source: AuthoritativeCredentialSource,
    ): SessionHandle {
        require(aliases.isNotEmpty())
        currentCoroutineContext().ensureActive()
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                reportStage("select-source")
                val snapshot = source.current()
                require(aliases.containsAll(snapshot.secrets.keys)) { "Snapshot uses unowned credential aliases" }
                if (accountIndex != null) {
                    // Drain a previous failed cleanup, but do not revoke a healthy
                    // active account before durable admission/collision checks.
                    if (active == null && (ownedAliases.isNotEmpty() || cleanupSelection != null)) revokeAndClean()
                    reportStage("index-begin")
                    val durableAliases = accountIndex.begin(selection, aliases, snapshot)
                    if (active?.selection != selection) ownedAliases.clear()
                    ownedAliases.addAll(durableAliases)
                } else if (active != null && active?.selection != selection) {
                    // Completed credentials belong to that saved account, not
                    // to its now-revoked active lease. Do not log it out merely
                    // because the user selects another account.
                    active = null
                    ownedAliases.clear()
                    mutableState.value = SessionRepositoryState()
                } else {
                    // Also retries any failed cleanup before a new write.
                    revokeAndClean()
                }
                val handle = SessionHandle(selection, aliases.toSet(), source, snapshot.identityGeneration)
                ownedAliases.addAll(handle.aliases)
                active = handle
                cleanupSelection = selection
                // A concurrent SDK identity change cannot be silently rebound to this account.
                reportStage("initial-write")
                if (!write(handle, snapshot, alreadyPending = accountIndex != null)) {
                    throw IllegalStateException("Credential identity changed during selection")
                }
                handle
            }
        }
    }

    /** False means obsolete lease/revision or revoked SDK identity, never a successful write. */
    suspend fun persistLatest(handle: SessionHandle): Boolean {
        currentCoroutineContext().ensureActive()
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                if (active !== handle) return@withContext false
                val snapshot = handle.source.current()
                if (snapshot.identityGeneration != handle.identityGeneration) {
                    revokeAndClean()
                    return@withContext false
                }
                if (snapshot.revision <= handle.persistedRevision) return@withContext false
                write(handle, snapshot)
            }
        }
    }

    /** Caller owns the collection Job; no hidden scope or fire-and-forget persistence. */
    suspend fun observe(handle: SessionHandle) {
        handle.source.updates.collect { persistLatest(handle) }
    }

    /**
     * End the active lease and disable automatic startup restore, but retain
     * the committed credential group for an explicit account-history login.
     * Without a persistent account index there is no safe way to recover that
     * group, so process-local repositories fall back to destructive logout.
     */
    suspend fun signOut(handle: SessionHandle): Boolean = withContext(NonCancellable) {
        mutex.withLock {
            if (active !== handle) return@withLock false
            val index = accountIndex
            if (index == null) {
                revokeAndClean()
            } else {
                reportStage("index-clear-current")
                index.clearCurrent(handle.selection)
                active = null
                ownedAliases.clear()
                cleanupSelection = null
                mutableState.value = SessionRepositoryState()
            }
            true
        }
    }

    /** Stale/foreign handles cannot delete a new account, including alias reuse. */
    suspend fun logout(handle: SessionHandle): Boolean = withContext(NonCancellable) {
        mutex.withLock {
            if (active !== handle) return@withLock false
            revokeAndClean()
            true
        }
    }

    /** Retry a failed revocation without reviving its former handle or selection. */
    suspend fun retryCleanup() = withContext(NonCancellable) {
        mutex.withLock {
            check(active == null) { "Cannot retry cleanup while a session is active" }
            revokeAndClean()
        }
    }

    private suspend fun write(handle: SessionHandle, snapshot: SessionCredentialSnapshot, alreadyPending: Boolean = false): Boolean {
        try {
            require(handle.aliases.containsAll(snapshot.secrets.keys)) { "Snapshot uses unowned credential aliases" }
        } catch (failure: IllegalArgumentException) {
            // Invalid live projection revokes its lease, as before persistence
            // was added. Do not confuse it with a failed durable admission.
            try { revokeAndClean() } catch (cleanupFailure: Throwable) { failure.addSuppressed(cleanupFailure) }
            throw failure
        }
        // Failed admission leaves the previous whole committed group untouched.
        // Do not place this inside the partial-write cleanup handler.
        if (!alreadyPending) accountIndex?.begin(handle.selection, handle.aliases, snapshot)
        try {
            mutableState.value = SessionRepositoryState(handle.selection, SessionPersistenceStatus.Writing, handle.persistedRevision.takeIf { it >= 0 })
            reportStage("secret-save")
            for (alias in ownedAliases) {
                val secret = snapshot.secrets[alias]
                if (secret == null) store.delete(alias) else store.save(alias, secret)
            }
            // A save may suspend while the SDK logs out/switches identity. Do
            // not publish even a transient Ready for that revoked identity.
            if (handle.source.current().identityGeneration != handle.identityGeneration) {
                reportStage("identity-changed-before-commit")
                revokeAndClean()
                return false
            }
            reportStage("index-commit")
            accountIndex?.commit(handle.selection, handle.aliases, snapshot)
            if (handle.source.current().identityGeneration != handle.identityGeneration) {
                reportStage("identity-changed-after-commit")
                revokeAndClean()
                return false
            }
            ownedAliases.retainAll(handle.aliases)
            handle.persistedRevision = snapshot.revision
            mutableState.value = SessionRepositoryState(handle.selection, SessionPersistenceStatus.Ready, snapshot.revision)
            reportStage("ready")
            return true
        } catch (failure: Throwable) {
            reportStage("write-failed-${failure::class.simpleName ?: "Exception"}")
            try { revokeAndClean() } catch (cleanupFailure: Throwable) { failure.addSuppressed(cleanupFailure) }
            throw failure
        }
    }

    private suspend fun revokeAndClean() {
        active = null
        mutableState.value = SessionRepositoryState(status = if (ownedAliases.isEmpty() && cleanupSelection == null) SessionPersistenceStatus.SignedOut else SessionPersistenceStatus.CleanupRequired)
        // A failed marker write is not completed logout. Preserve its identity
        // for retryCleanup; delete nothing while restart could still admit it.
        cleanupSelection?.let { accountIndex?.remove(it) }
        cleanupSelection = null
        var failure: Throwable? = null
        for (alias in ownedAliases.toList()) {
            try {
                store.delete(alias)
                ownedAliases.remove(alias)
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        if (failure != null) throw failure
        mutableState.value = SessionRepositoryState()
    }
}
