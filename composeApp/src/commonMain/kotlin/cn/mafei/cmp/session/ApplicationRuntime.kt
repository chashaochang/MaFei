package cn.mafei.cmp.session

import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.data.repositories.session.SessionRepository
import cn.mafei.cmp.player.api.PlayerRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-scoped services, not an Activity/ViewController-scoped persistence writer. */
class ApplicationRuntime(
    settings: SettingsStorage,
    secureStore: SecureCredentialStore,
    private val transport: HttpTransport,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    /**
     * Platform player engine factory (Android/iOS/macOS/OHOS runtime). When
     * absent, [playback] returns null and the player UI cannot start — an
     * explicit configuration gap, never a faked player.
     */
    private val playerRuntimeFactory: ((ProviderKind) -> PlayerRuntime)? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val closeMutex = Mutex()
    var isClosed: Boolean = false
        private set

    /** Copy-on-write session playback map; guarded by [playbackMutex] + CAS. */
    private val playbackMutex = Mutex()
    private val playbacks = MutableStateFlow(emptyMap<ApplicationSession, SessionPlaybackRuntime>())
    private var playbackObserver: Job? = null
    val sessions = ApplicationSessions(
        SessionRepository(secureStore, settings), MediaRepository(SystemEpochClock),
        ConnectionCatalog(settings), DefaultProviderSessionFactory(transport), scope,
    )
    private val login = ProviderLogin(transport)
    private val artwork = SessionArtwork(transport)

    suspend fun loadArtwork(session: ApplicationSession, image: cn.mafei.cmp.core.domain.MediaImage): ByteArray? {
        if (isClosed || sessions.state.value.session !== session) return null
        return artwork.load(session.connection, session.binding, image).takeIf { sessions.state.value.session === session }
    }

    /** Fresh login; ownership transfers to sessions only after admission. */
    suspend fun connect(provider: ProviderKind, baseUrl: String, username: String, password: String): ApplicationSession {
        check(!isClosed) { "Application is closed" }
        val authenticated = login.authenticate(provider, baseUrl, username, password)
        try {
            return sessions.activate(authenticated.connection, authenticated.binding)
        } catch (failure: Throwable) {
            // Admitted cancellation may already have committed. Never clear that SDK.
            if (sessions.state.value.session?.binding !== authenticated.binding) authenticated.binding.clear()
            // Authentication errors occurred before this block. A backend's cause/message
            // can contain sensitive data; retain only the stable, non-secret category.
            if (failure is Exception && failure !is CancellationException) {
                throw ConnectionSaveException((failure as? CredentialAccessException)?.kind)
            }
            throw failure
        }
    }

    /**
     * Player entry for an ACTIVE session. The same session reuses the same
     * runtime; logout/account-switch releases the retired instance through
     * the session-state observer, and [close] releases everything. Returns
     * null when no platform engine factory was configured.
     */
    fun playback(session: ApplicationSession): SessionPlaybackRuntime? {
        check(!isClosed) { "Application is closed" }
        val factory = playerRuntimeFactory ?: return null
        while (true) {
            val current = playbacks.value
            current[session]?.let { return it }
            val created = SessionPlaybackRuntime.create(
                provider = session.selection.provider,
                adapter = session.binding.adapter,
                runtime = factory(session.selection.provider),
                scope = scope,
                nowMs = { cn.mafei.cmp.common.core.SystemEpochClock.nowEpochMs() },
            )
            if (playbacks.compareAndSet(current, current + (session to created))) {
                ensurePlaybackObserver()
                return created
            }
        }
    }

    private fun ensurePlaybackObserver() {
        if (playbackObserver != null) return
        playbackObserver = scope.launch {
            sessions.state.collect { state ->
                playbackMutex.withLock {
                    for ((session, runtime) in playbacks.value) {
                        if (session !== state.session) {
                            playbacks.value -= session
                            runtime.release()
                        }
                    }
                }
            }
        }
    }

    private suspend fun releaseAllPlaybacks() {
        playbackMutex.withLock {
            for ((_, runtime) in playbacks.value) runtime.release()
            playbacks.value = emptyMap()
        }
    }

    suspend fun close() = withContext(NonCancellable) {
        closeMutex.withLock {
            if (isClosed) return@withLock
            try {
                releaseAllPlaybacks()
                playbackObserver?.cancel()
                playbackObserver = null
                sessions.close()
            } finally {
                isClosed = true; transport.close(); scope.cancel()
            }
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
