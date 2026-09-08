package cn.mafei.cmp.session

import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.data.repositories.session.SessionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-scoped services, not an Activity/ViewController-scoped persistence writer. */
class ApplicationRuntime(
    settings: SettingsStorage,
    secureStore: SecureCredentialStore,
    private val transport: HttpTransport,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val closeMutex = Mutex()
    var isClosed: Boolean = false
        private set
    val sessions = ApplicationSessions(
        SessionRepository(secureStore, settings), MediaRepository(SystemEpochClock),
        ConnectionCatalog(settings), DefaultProviderSessionFactory(transport), scope,
    )
    private val login = ProviderLogin(transport)

    /** Fresh login; ownership transfers to sessions only after admission. */
    suspend fun connect(provider: ProviderKind, baseUrl: String, username: String, password: String): ApplicationSession {
        check(!isClosed) { "Application is closed" }
        val authenticated = login.authenticate(provider, baseUrl, username, password)
        try {
            return sessions.activate(authenticated.connection, authenticated.binding)
        } catch (failure: Throwable) {
            // Admitted cancellation may already have committed. Never clear that SDK.
            if (sessions.state.value.session?.binding !== authenticated.binding) authenticated.binding.clear()
            throw failure
        }
    }

    suspend fun close() = withContext(NonCancellable) {
        closeMutex.withLock {
            if (isClosed) return@withLock
            try { sessions.close() }
            finally { isClosed = true; transport.close(); scope.cancel() }
        }
    }

    /** Desktop host awaits draining before exiting. Mobile roots live for the process. */
    fun requestClose(onClosed: () -> Unit = {}) {
        if (isClosed) { onClosed(); return }
        scope.launch { try { close() } finally { onClosed() } }
    }
}

/** Called only by platform main-thread entry points. Rotation does not create a second writer. */
internal object ProcessApplication {
    private var current: ApplicationRuntime? = null
    fun getOrCreate(create: () -> ApplicationRuntime): ApplicationRuntime {
        val existing = current
        if (existing != null && !existing.isClosed) return existing
        return create().also { current = it }
    }
}
