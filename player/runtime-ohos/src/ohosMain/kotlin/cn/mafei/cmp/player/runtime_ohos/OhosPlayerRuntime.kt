package cn.mafei.cmp.player.runtime_ohos

import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerContentMode
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.PlayerRuntimeInfo
import cn.mafei.cmp.player.api.PlayerRenderMode
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import cn.mafei.cmp.player.api.TrackKind
import kotlin.concurrent.AtomicReference

/**
 * Events owned by the ArkUI player page. They are pushed at state changes and
 * at the SDK's coarse progress interval; the KMP side never polls the player.
 */
sealed interface OhosNativePlayerEvent {
    val handle: Long

    data class Ready(
        override val handle: Long,
        val durationMs: Long?,
    ) : OhosNativePlayerEvent

    data class Playing(override val handle: Long) : OhosNativePlayerEvent
    data class Paused(override val handle: Long) : OhosNativePlayerEvent
    data class Buffering(override val handle: Long) : OhosNativePlayerEvent

    data class Progress(
        override val handle: Long,
        val positionMs: Long,
        val bufferedPositionMs: Long,
        val durationMs: Long?,
    ) : OhosNativePlayerEvent

    data class Tracks(
        override val handle: Long,
        val tracks: List<PlayerTrack>,
    ) : OhosNativePlayerEvent

    data class Diagnostics(
        override val handle: Long,
        val info: PlayerRuntimeInfo,
    ) : OhosNativePlayerEvent

    data class Ended(override val handle: Long) : OhosNativePlayerEvent

    data class Failed(
        override val handle: Long,
        val failure: PlayerFailure,
    ) : OhosNativePlayerEvent
}

/**
 * Coarse bridge implemented by the CPF/ArkUI host.
 *
 * [loadUrl] opens one complete native player session. Playback state is pushed
 * through [bind]; no temporary JS callback or N-API handle is retained and no
 * 250 ms command/state round-trip is used.
 */
interface OhosNativePlayerBridge {
    fun bind(eventSink: (OhosNativePlayerEvent) -> Unit)
    fun create(): Long
    fun loadUrl(
        handle: Long,
        url: String,
        headers: Map<String, String>,
        subtitleUrl: String?,
        allowInsecureTls: Boolean,
        multiConnectionAllowed: Boolean,
        startPositionMs: Long,
    )
    fun setRenderMode(mode: PlayerRenderMode)
    fun play(handle: Long, pendingSeekPositionMs: Long?)
    fun pause(handle: Long)
    fun stop(handle: Long)
    fun seek(handle: Long, positionMs: Long)
    suspend fun previewFrame(handle: Long, positionMs: Long): PlayerSeekPreviewFrame? = null
    fun setSpeed(handle: Long, speed: Float)
    fun selectTrack(handle: Long, kind: TrackKind, id: String?)

    /**
     * `mfplayer`'s `PlayerSurface(contentMode:)`. The ArkUI host owns the whole
     * native surface, so the mode is a surface property here rather than a
     * per-player one; the host must keep it applied across loads.
     */
    fun setContentMode(handle: Long, mode: PlayerContentMode)
    fun releasePlayer(handle: Long)
    fun unbind(eventSink: (OhosNativePlayerEvent) -> Unit)
}

/**
 * Event-driven OHOS runtime. The ArkUI page owns mfplayer and its complete UI;
 * this adapter only keeps the common controller/reporting contract informed.
 */
class OhosPlayerRuntime(
    override val capabilities: PlayerCapabilities,
    private val bridge: OhosNativePlayerBridge,
) : PlayerRuntime {

    private var renderMode = PlayerRenderMode.Buffer

    override fun setRenderMode(mode: PlayerRenderMode) {
        renderMode = mode
        val handle = state.value.handle
        if (handle != 0L && !state.value.released) bridge.setRenderMode(mode)
    }

    override suspend fun previewFrame(positionMs: Long): PlayerSeekPreviewFrame? {
        val snapshot = state.value
        if (snapshot.released || snapshot.handle == 0L) return null
        return bridge.previewFrame(snapshot.handle, positionMs.coerceAtLeast(0L))
    }

    private val sink = AtomicReference<((RuntimeEvent) -> Unit)?>(null)
    private val state = AtomicReference(RuntimeSnapshot())
    private val nativeEventSink: (OhosNativePlayerEvent) -> Unit = ::onNativeEvent

    override fun bind(eventSink: (RuntimeEvent) -> Unit) {
        check(sink.compareAndSet(null, eventSink)) { "Runtime already bound" }
        bridge.bind(nativeEventSink)
    }

    override fun execute(command: RuntimeCommand) {
        val snapshot = state.value
        if (snapshot.released) return
        when (command) {
            is RuntimeCommand.Load -> load(command)
            is RuntimeCommand.Play -> if (snapshot.handles(command.generation)) {
                bridge.play(snapshot.handle, snapshot.pendingSeekPositionMs)
                if (snapshot.pendingSeekPositionMs != null) {
                    state.compareAndSet(snapshot, snapshot.copy(pendingSeekPositionMs = null))
                }
            }
            is RuntimeCommand.Pause -> if (snapshot.handles(command.generation)) bridge.pause(snapshot.handle)
            is RuntimeCommand.Seek -> if (snapshot.handles(command.generation)) {
                bridge.seek(snapshot.handle, command.positionMs)
                state.compareAndSet(snapshot, snapshot.copy(pendingSeekPositionMs = command.positionMs))
            }
            is RuntimeCommand.Stop -> stop(command.generation)
            is RuntimeCommand.Speed -> if (snapshot.handles(command.generation)) {
                bridge.setSpeed(snapshot.handle, command.value)
            }
            is RuntimeCommand.SelectTrack -> if (snapshot.handles(command.generation)) {
                bridge.selectTrack(snapshot.handle, command.kind, command.id)
            }
            // Surface-scoped: the ArkUI page may not have a player yet, so this
            // is forwarded for any live handle instead of a generation.
            is RuntimeCommand.ContentMode -> if (snapshot.handle != 0L) {
                bridge.setContentMode(snapshot.handle, command.mode)
            }
            is RuntimeCommand.AttachSurface,
            is RuntimeCommand.DetachSurface -> Unit // The native ArkUI page owns its surface.
            RuntimeCommand.Release -> release()
        }
    }

    private fun load(command: RuntimeCommand.Load) {
        val previous = state.value
        val handle = if (previous.handle == 0L) bridge.create() else previous.handle
        if (previous.handle != 0L) bridge.stop(previous.handle)
        state.value = RuntimeSnapshot(handle = handle, generation = command.generation)
        try {
            bridge.setRenderMode(renderMode)
            bridge.loadUrl(
                handle = handle,
                url = command.resource.url,
                headers = command.resource.headers,
                subtitleUrl = command.resource.subtitleUrl,
                allowInsecureTls = command.resource.allowInsecureTls,
                multiConnectionAllowed = command.resource.multiConnectionAllowed,
                startPositionMs = command.startPositionMs,
            )
        } catch (_: UnsupportedOperationException) {
            sink.value?.invoke(RuntimeEvent.Failed(command.generation, PlayerFailure.UnsupportedMedia))
        }
    }

    private fun stop(generation: PlaybackGeneration?) {
        val snapshot = state.value
        if (!snapshot.handles(generation)) return
        bridge.stop(snapshot.handle)
        state.value = RuntimeSnapshot(handle = snapshot.handle)
    }

    private fun release() {
        val snapshot = state.value
        if (snapshot.released) return
        state.value = RuntimeSnapshot(released = true)
        if (snapshot.handle != 0L) bridge.releasePlayer(snapshot.handle)
        bridge.unbind(nativeEventSink)
        sink.value = null
    }

    private fun onNativeEvent(event: OhosNativePlayerEvent) {
        val snapshot = state.value
        val generation = snapshot.generation
        if (snapshot.released || generation == null || event.handle != snapshot.handle) return
        if (event is OhosNativePlayerEvent.Progress && snapshot.pendingSeekPositionMs != null) {
            state.compareAndSet(snapshot, snapshot.copy(pendingSeekPositionMs = null))
        }
        val runtimeEvent = when (event) {
            is OhosNativePlayerEvent.Ready -> RuntimeEvent.Ready(
                generation,
                event.durationMs?.takeIf { it > 0L },
            )
            is OhosNativePlayerEvent.Playing -> RuntimeEvent.Playing(generation)
            is OhosNativePlayerEvent.Paused -> RuntimeEvent.Paused(generation)
            is OhosNativePlayerEvent.Buffering -> RuntimeEvent.Buffering(generation)
            is OhosNativePlayerEvent.Progress -> RuntimeEvent.Progress(
                generation,
                event.positionMs.coerceAtLeast(0L),
                event.bufferedPositionMs.coerceAtLeast(0L),
                event.durationMs?.takeIf { it > 0L },
            )
            is OhosNativePlayerEvent.Tracks -> RuntimeEvent.Tracks(generation, event.tracks)
            is OhosNativePlayerEvent.Diagnostics -> RuntimeEvent.Diagnostics(generation, event.info)
            is OhosNativePlayerEvent.Ended -> RuntimeEvent.Ended(generation)
            is OhosNativePlayerEvent.Failed -> RuntimeEvent.Failed(generation, event.failure)
        }
        sink.value?.invoke(runtimeEvent)
    }

    private data class RuntimeSnapshot(
        val handle: Long = 0L,
        val generation: PlaybackGeneration? = null,
        val pendingSeekPositionMs: Long? = null,
        val released: Boolean = false,
    ) {
        fun handles(candidate: PlaybackGeneration?): Boolean =
            candidate != null && candidate === generation && handle != 0L
    }
}
