package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.repositories.playback.ProviderPlaybackCoordinator
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.facade.DefaultPlayerController
import kotlinx.coroutines.CoroutineScope

/**
 * Non-UI composition root for playback inside one [ApplicationSession]:
 * binds the session's provider adapter to a [DefaultPlayerController] through
 * the [ProviderPlaybackCoordinator], so prepare->load and Start/Progress/Stop
 * reporting stay on the SAME adapter instance. The platform engine is
 * supplied by the host as [runtime] (Android/iOS/macOS/OHOS runtimes).
 *
 * The UI only sees [controller] state and the play/stop/release conveniences;
 * wire DTOs and transports never cross this boundary.
 */
class SessionPlaybackRuntime private constructor(
    val controller: PlayerController,
    private val coordinator: ProviderPlaybackCoordinator,
    private val provider: ProviderKind,
) {
    val reportFailures get() = coordinator.reportFailures

    /** Total playback re-negotiations (expired link / auth refresh), for diagnostics. */
    val completedRenegotiations get() = coordinator.completedRenegotiations

    /** Synchronous observation hook; playback failures never crash the controller. */
    var onFailureObserved: ((cn.mafei.cmp.data.repositories.playback.PlaybackReportFailure) -> Unit)?
        get() = coordinator.onFailureObserved
        set(value) { coordinator.onFailureObserved = value }

    /** Prepares via the session's adapter (with user-directed options) and loads the result into the player. */
    suspend fun play(
        itemId: MediaId,
        resumePositionMs: Long = 0,
        autoplay: Boolean = true,
        options: PlaybackOptions = PlaybackOptions(),
    ): PlaybackGeneration? = coordinator.play(provider, itemId, resumePositionMs, autoplay, options)

    suspend fun stop() = coordinator.stop()

    /** Releases player and coordinator. Idempotent; no report afterwards. */
    suspend fun release() = coordinator.release()

    companion object {
        fun create(
            provider: ProviderKind,
            adapter: MediaProviderAdapter,
            runtime: PlayerRuntime,
            scope: CoroutineScope,
            nowMs: () -> Long,
            reportIntervalMs: Long = 10_000,
        ): SessionPlaybackRuntime {
            val coordinator = ProviderPlaybackCoordinator(
                adapterFor = { adapter },
                scope = scope,
            )
            val controller = DefaultPlayerController(
                runtime = runtime,
                scope = scope,
                nowMs = nowMs,
                reportSink = coordinator.reportSink,
                reportIntervalMs = reportIntervalMs,
            )
            coordinator.attachController(controller)
            return SessionPlaybackRuntime(controller, coordinator, provider)
        }
    }
}
