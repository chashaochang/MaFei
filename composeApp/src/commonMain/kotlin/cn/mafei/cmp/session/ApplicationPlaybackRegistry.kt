package cn.mafei.cmp.session

import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.player.api.PlayerRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns player identity, foreground delivery and retirement for the application's session stream. */
internal class ApplicationPlaybackRegistry(
    private val sessions: ApplicationSessions,
    private val scope: CoroutineScope,
    private val transport: HttpTransport,
    private val playerRuntimeFactory: ((ProviderKind) -> PlayerRuntime)?,
    private val playerPreferences: PlayerPreferencesStore,
    private val externalSubtitleOverlay: Boolean,
    private val isClosed: () -> Boolean,
) {
    /** Copy-on-write session playback map; guarded by [playbackMutex] + CAS. */
    private val playbackMutex = Mutex()
    private val playbacks = MutableStateFlow(emptyMap<ApplicationSession, SessionPlaybackRuntime>())
    private var hostForeground = true
    private var playbackObserver: Job? = null
    fun getOrCreate(session: ApplicationSession): SessionPlaybackRuntime? {
        check(!isClosed()) { "Application is closed" }
        val factory = playerRuntimeFactory ?: return null
        // Refuse sessions that were logged out or switched away: a stale
        // session must never obtain a fresh player bound to retired adapters.
        if (session !== sessions.state.value.session) {
            throw IllegalStateException("Session is no longer active")
        }
        while (true) {
            val current = playbacks.value
            current[session]?.let { return it }
            val created = SessionPlaybackRuntime.create(
                provider = session.selection.provider,
                adapter = session.binding.adapter,
                runtime = PreferenceApplyingPlayerRuntime(factory(session.selection.provider).let { engine ->
                    if (externalSubtitleOverlay) ExternalSubtitlePlayerRuntime(engine, transport, scope) else engine
                }) {
                    playerPreferences.state.value
                },
                scope = scope,
                nowMs = { cn.mafei.cmp.common.core.SystemEpochClock.nowEpochMs() },
                onStopReported = {
                    // Retired players still drain Stop; they must never invalidate a replacement account.
                    if (!isClosed() && sessions.state.value.session === session) {
                        sessions.media.markPlaybackChanged(session.media)
                    }
                },
            )
            if (playbacks.compareAndSet(current, current + (session to created))) {
                ensurePlaybackObserver()
                if (!hostForeground) scope.launch {
                    playbackMutex.withLock {
                        if (!hostForeground && playbacks.value[session] === created) created.controller.background()
                    }
                }
                return created
            }
            // CAS lost to a concurrent lifecycle change: release the loser
            // asynchronously so engines, timers and workers never leak.
            scope.launch(NonCancellable) {
                try { created.release() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { } // best-effort cleanup of a discarded instance
            }
        }
    }

    /** Host visibility only; never changes account state or destroys a music queue. */
    fun setHostForeground(foreground: Boolean) {
        if (isClosed() || hostForeground == foreground) return
        hostForeground = foreground
        scope.launch {
            playbackMutex.withLock {
                // A newer lifecycle event can supersede one still queued on the main thread.
                if (isClosed() || hostForeground != foreground) return@withLock
                for (playback in playbacks.value.values) {
                    if (foreground) playback.controller.foreground() else playback.controller.background()
                }
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

    suspend fun releaseAll() {
        playbackMutex.withLock {
            for ((_, runtime) in playbacks.value) runtime.release()
            playbacks.value = emptyMap()
        }
    }

    fun existing(session: ApplicationSession): SessionPlaybackRuntime? = playbacks.value[session]

    fun cancelObserver() {
        playbackObserver?.cancel()
        playbackObserver = null
    }
}
