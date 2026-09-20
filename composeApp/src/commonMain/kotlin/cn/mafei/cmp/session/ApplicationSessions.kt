package cn.mafei.cmp.session

import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.data.repositories.MediaSession
import cn.mafei.cmp.data.repositories.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ApplicationSessionStatus { NotStarted, Restoring, SignedOut, Ready, NeedsAttention, Closed }

/**
 * 保存的账号存在，但它的凭据已不可恢复（被系统清理、卸载重装、或当初就没
 * 落盘）——这不是"切换失败"，而是"需要重新登录"：调用方应把用户带到该
 * 服务器的登录页并预填地址/用户名，而不是笼统报错。继承
 * [IllegalStateException] 以保持既有 `error("Saved account requires login")`
 * 语义对老调用方/测试兼容。
 */
class SavedAccountRequiresLoginException(val selection: SessionSelection) :
    IllegalStateException("Saved account requires login")

data class ApplicationSessionState(
    val status: ApplicationSessionStatus = ApplicationSessionStatus.NotStarted,
    val selection: SessionSelection? = null,
    /** Runtime-only handle; changes even when the same saved account is reopened. */
    val session: ApplicationSession? = null,
)

/** An in-memory, reference-owned handle. Never persist this object. */
class ApplicationSession internal constructor(
    val selection: SessionSelection,
    val media: MediaSession,
    internal val credentials: SessionHandle,
    internal val binding: ProviderSessionBinding,
    val connection: SavedConnection,
) {
    internal var observation: Job? = null
}

/**
 * One application root owns this coordinator, SDK instances, storage writers and
 * observer scope. Starting/restoring does not validate a token with a real server.
 * Switching drains the old observer before clearing its SDK; it preserves that
 * account's saved login. Explicit logout revokes persistence as well.
 */
class ApplicationSessions(
    private val credentials: SessionRepository,
    val media: MediaRepository,
    private val catalog: ConnectionCatalog,
    private val factory: ProviderSessionFactory,
    private val scope: CoroutineScope,
    private val reportFailure: (stage: String, failure: Throwable) -> Unit = { _, _ -> },
    private val beforeExplicitLogout: suspend () -> Unit = {},
) {
    private val mutex = Mutex()
    private var active: ApplicationSession? = null
    private val mutableState = MutableStateFlow(ApplicationSessionState())
    val state: StateFlow<ApplicationSessionState> = mutableState.asStateFlow()

    /** Idempotent startup; a failure is visible and can be explicitly retried. */
    suspend fun start(): ApplicationSession? = mutex.withLock {
        checkOpen()
        if (active != null || state.value.status == ApplicationSessionStatus.SignedOut) return@withLock active
        mutableState.value = ApplicationSessionState(ApplicationSessionStatus.Restoring)
        try {
            val restored = credentials.restoreCurrent()
            if (restored == null) {
                mutableState.value = ApplicationSessionState(ApplicationSessionStatus.SignedOut)
                null
            } else restore(restored)
        } catch (cancelled: CancellationException) {
            if (active == null) mutableState.value = ApplicationSessionState()
            throw cancelled
        } catch (failure: Exception) {
            reportFailure("startup-restore", failure)
            mutableState.value = ApplicationSessionState(ApplicationSessionStatus.NeedsAttention)
            null
        }
    }

    suspend fun switchAccount(selection: SessionSelection): ApplicationSession = mutex.withLock {
        checkOpen()
        val restored = credentials.restoreAccount(selection)
            ?: throw SavedAccountRequiresLoginException(selection)
        restore(restored)
    }

    /** Non-secret account history; individual entries may require re-authentication. */
    suspend fun savedConnections(): List<SavedConnection> = mutex.withLock {
        checkOpen()
        // History is non-secret connection metadata. Explicit logout revokes
        // automatic restoration, but must not erase the account from the
        // user's history screen.
        catalog.all().sortedWith(
            compareByDescending<SavedConnection> { it.account.lastActiveEpochMs ?: Long.MIN_VALUE }
                .thenBy { it.account.displayName }
                .thenBy { it.server.name },
        )
    }

    /** Deletes an inactive account from both the restorable index and history. */
    suspend fun deleteSavedConnection(
        connection: SavedConnection,
        deleteRememberedPassword: suspend () -> Unit,
    ): Boolean = withContext(NonCancellable) {
        mutex.withLock {
            checkOpen()
            if (active?.selection == connection.selection) return@withLock false
            val stored = catalog.find(connection.selection) ?: return@withLock false
            require(stored.credentialKey == connection.credentialKey) {
                "Saved account credential namespace does not match"
            }
            deleteRememberedPassword()
            credentials.removeSavedAccount(stored.selection)
            catalog.remove(stored)
        }
    }

    /** Called after a connect/login flow has built an authoritative SDK binding. */
    suspend fun activate(connection: SavedConnection, binding: ProviderSessionBinding): ApplicationSession = mutex.withLock {
        checkOpen()
        validate(connection, binding)
        try {
            try {
                catalog.save(connection)
            } catch (failure: Throwable) {
                reportFailure("login-catalog-save", failure)
                throw failure
            }
            try {
                attach(connection, binding)
            } catch (failure: Throwable) {
                reportFailure("login-session-save", failure)
                throw failure
            }
        } catch (failure: Throwable) {
            if (active?.binding !== binding) binding.clear()
            throw failure
        }
    }

    /** Stale screen/old account handles cannot log out the newly selected account. */
    suspend fun logout(session: ApplicationSession): Boolean = withContext(NonCancellable) {
        mutex.withLock {
            if (active !== session) return@withLock false
            // Persist the user's intent before clearing automatic restoration.
            // The saved credential remains available only through an explicit
            // account-history selection.
            beforeExplicitLogout()
            detach(session)
            try {
                val signedOut = if (session.connection.rememberLogin) {
                    credentials.signOut(session.credentials)
                } else {
                    credentials.logout(session.credentials)
                }
                mutableState.value = ApplicationSessionState(ApplicationSessionStatus.SignedOut)
                signedOut
            } catch (failure: Throwable) {
                mutableState.value = ApplicationSessionState(ApplicationSessionStatus.NeedsAttention)
                throw failure
            }
        }
    }

    suspend fun retryCleanup() = mutex.withLock {
        checkOpen()
        check(active == null)
        credentials.retryCleanup()
        mutableState.value = ApplicationSessionState(ApplicationSessionStatus.SignedOut)
    }

    /** Shutdown, not logout. Drain accepted credential writes before root transport closes. */
    suspend fun close() = withContext(NonCancellable) {
        mutex.withLock {
            if (state.value.status == ApplicationSessionStatus.Closed) return@withLock
            active?.let { detach(it) }
            mutableState.value = ApplicationSessionState(ApplicationSessionStatus.Closed)
        }
    }

    private suspend fun restore(restored: RestoredSession): ApplicationSession {
        val connection = catalog.find(restored.selection) ?: error("Saved connection metadata is unavailable")
        val binding = factory.restore(connection, restored.snapshot.secrets)
        try { return attach(connection, binding) }
        catch (failure: Throwable) { if (active?.binding !== binding) binding.clear(); throw failure }
    }

    private suspend fun attach(connection: SavedConnection, binding: ProviderSessionBinding): ApplicationSession {
        validate(connection, binding)
        require(active?.binding !== binding) { "A fresh SDK binding is required for each activation" }
        currentCoroutineContext().ensureActive()
        // Drain admitted transitions even if the UI that requested one disappears.
        return withContext(NonCancellable) {
            active?.let { detach(it) }
            var lease: SessionHandle? = null
            try {
                lease = credentials.select(connection.selection, binding.aliases, binding.source)
                val session = ApplicationSession(connection.selection, media.open(binding.adapter), lease, binding, connection)
                active = session
                mutableState.value = ApplicationSessionState(ApplicationSessionStatus.Ready, session.selection, session)
                session.observation = scope.launch { observe(session) }
                session
            } catch (failure: Throwable) {
                lease?.let { try { credentials.logout(it) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) } }
                binding.clear()
                mutableState.value = ApplicationSessionState(ApplicationSessionStatus.NeedsAttention)
                throw failure
            }
        }
    }

    private suspend fun observe(session: ApplicationSession) {
        try {
            session.binding.source.updates.collect {
                credentials.persistLatest(session.credentials)
                if (credentials.state.value.status != SessionPersistenceStatus.Ready) {
                    invalidateFromObserver(session)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { invalidateFromObserver(session) }
    }

    private suspend fun invalidateFromObserver(session: ApplicationSession) {
        mutex.withLock {
            if (active !== session) return@withLock
            // Never join/cancel this coroutine while holding a lock it needs.
            active = null
            media.close(session.media)
            session.binding.clear()
            try { credentials.logout(session.credentials) } catch (_: Exception) { /* retryCleanup owns failures */ }
            mutableState.value = ApplicationSessionState(ApplicationSessionStatus.NeedsAttention)
        }
        currentCoroutineContext().cancel()
    }

    private suspend fun detach(session: ApplicationSession) {
        session.observation?.cancelAndJoin()
        media.close(session.media)
        session.binding.clear()
        if (active === session) active = null
    }

    private fun validate(connection: SavedConnection, binding: ProviderSessionBinding) {
        require(binding.adapter.provider == connection.server.provider && binding.adapter.serverId == connection.server.id &&
            binding.adapter.accountId == connection.account.id && binding.aliases == connection.aliases) { "SDK binding does not match saved account" }
    }
    private fun checkOpen() = check(state.value.status != ApplicationSessionStatus.Closed) { "Application sessions are closed" }
}
