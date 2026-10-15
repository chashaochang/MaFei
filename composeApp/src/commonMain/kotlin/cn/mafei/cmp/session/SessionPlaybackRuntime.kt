package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackTrack
import cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind
import cn.mafei.cmp.data.repositories.playback.ProviderPlaybackCoordinator
import cn.mafei.cmp.data.repositories.playback.resolveNativeTrackId
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import cn.mafei.cmp.player.api.TrackKind
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
    private val runtime: PlayerRuntime,
) {
    val reportFailures get() = coordinator.reportFailures

    /** Total playback re-negotiations (expired link / auth refresh), for diagnostics. */
    val completedRenegotiations get() = coordinator.completedRenegotiations

    /** Provider metadata wins over opaque native track labels when available. */
    val tracks get() = coordinator.tracks

    /**
     * What the last negotiation actually produced: selected media source, how
     * the stream is served, which ceilings the provider offered and which one
     * is in effect. The chrome reports THIS instead of inferring anything from
     * its own last click, so a ceiling the provider could not serve reads as
     * 「自动」/「直连」 rather than as the label the user tapped.
     */
    val prepared get() = coordinator.prepared

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

    /**
     * Selects a track from the provider-declared list (its indexes are what the
     * chrome shows). Two routes, exactly like the legacy MFPlayerView:
     *
     * - [PlaybackTrack.renegotiate] → the provider must be asked again
     *   (external subtitle files, or any track on a transcoded stream).
     * - otherwise the track is muxed inside a byte-for-byte direct stream, so
     *   re-requesting the URL cannot change it: map the provider track onto the
     *   native runtime's track list and switch there.
     */
    suspend fun selectTrack(kind: TrackKind, id: String?): PlaybackGeneration? {
        val providerKind = when (kind) {
            TrackKind.Audio -> PlaybackTrackKind.Audio
            TrackKind.Subtitle -> PlaybackTrackKind.Subtitle
            TrackKind.Video -> null
        }
        val declared = providerKind?.let { target ->
            tracks.value.filter { it.kind == target }
        }.orEmpty()
        if (providerKind == null || declared.isEmpty()) {
            controller.selectTrack(kind, id)
            return controller.state.value.generation
        }
        val providerTrack = id?.let { selected -> declared.firstOrNull { it.index.toString() == selected } }
        // Subtitles-off (id == null) is a negotiation: the provider must drop
        // its external delivery URL. A track the provider never declared, or
        // one it cannot re-negotiate, belongs to the native runtime.
        val viaProvider = id == null || providerTrack?.renegotiate == true
        if (viaProvider) return coordinator.selectTrack(providerKind, id)
        val nativeId = resolveNativeTrackId(providerTrack, controller.state.value.tracks.filter { it.kind == kind })
            ?: return null
        controller.selectTrack(kind, nativeId)
        // 原生已经在播放器内部换了轨，但 provider 的选中态与当前选项还停在旧轨上：
        // 界面勾选（优先显示 provider 状态）和后续重新协商都得跟上这次切换。
        providerTrack?.let { coordinator.selectTrackLocally(providerKind, it.index) }
        return controller.state.value.generation
    }

    /** 清晰度档位（旧版 QualityOption.bitrate）：null = 自动/直连。 */
    suspend fun selectQuality(maxStreamingBitrate: Int?): PlaybackGeneration? =
        coordinator.selectQuality(maxStreamingBitrate)

    suspend fun stop() = coordinator.stop()

    /** Native fallback for providers without a server-side preview source. */
    suspend fun previewFrame(positionMs: Long): PlayerSeekPreviewFrame? =
        runtime.previewFrame(positionMs)

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
            return SessionPlaybackRuntime(controller, coordinator, provider, runtime)
        }
    }
}
