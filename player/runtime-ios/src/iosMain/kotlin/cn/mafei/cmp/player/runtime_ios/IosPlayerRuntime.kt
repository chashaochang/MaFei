package cn.mafei.cmp.player.runtime_ios

import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayableResource
import cn.mafei.cmp.player.api.UnsupportedPlayerCapability
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import kotlin.concurrent.AtomicReference

/** Periodic engine polling scheduler; production wiring uses a platform timer. */
interface IosPollScheduler {
    fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable
}

/**
 * iOS runtime translating the frozen contract onto [IosAvPlayerBridge].
 * Poll-driven: every RuntimeEvent is derived from live bridge queries, never
 * fabricated. Single-threaded by contract; generation discipline matches the
 * other runtimes (commands for retired generations are ignored).
 */
class IosPlayerRuntime(
    override val capabilities: PlayerCapabilities,
    private val bridge: IosAvPlayerBridge,
    private val surfaces: IosSurfaceRegistry = IosSurfaceRegistry(),
    private val pollScheduler: IosPollScheduler,
    private val pollPeriodMs: Long = 250L,
) : PlayerRuntime {

    private val sink = AtomicReference<((RuntimeEvent) -> Unit)?>(null)
    private val generation = AtomicReference<PlaybackGeneration?>(null)
    private val state = AtomicReference(RuntimeSnapshot())
    private val pollTicket = AtomicReference<AutoCloseable?>(null)

    override fun bind(eventSink: (RuntimeEvent) -> Unit) {
        check(sink.value == null) { "Runtime already bound" }
        sink.value = eventSink
        // Kept and closed on Release: an unclosed timer would retain the
        // runtime forever after the player is gone.
        pollTicket.value = pollScheduler.schedule(pollPeriodMs, ::poll)
    }

    override fun execute(command: RuntimeCommand) {
        if (state.value.released) return
        when (command) {
            is RuntimeCommand.Load -> load(command)
            is RuntimeCommand.Play -> {
                val snapshot = state.value
                if (snapshot.handles(command.generation)) bridge.play(snapshot.player)
            }
            is RuntimeCommand.Pause -> {
                val snapshot = state.value
                if (snapshot.handles(command.generation)) bridge.pause(snapshot.player)
            }
            is RuntimeCommand.Seek -> {
                val snapshot = state.value
                if (snapshot.handles(command.generation)) bridge.seek(snapshot.player, command.positionMs)
            }
            is RuntimeCommand.Stop -> stop(command.generation)
            is RuntimeCommand.Speed -> {
                val snapshot = state.value
                if (snapshot.handles(command.generation)) bridge.setSpeed(snapshot.player, command.value)
            }
            is RuntimeCommand.SelectTrack -> {
                val snapshot = state.value
                if (!capabilities.trackSelection) throw UnsupportedPlayerCapability("trackSelection")
                if (snapshot.handles(command.generation)) bridge.selectTrack(snapshot.player, command.kind, command.id)
            }
            is RuntimeCommand.ContentMode -> {
                if (!capabilities.contentMode) throw UnsupportedPlayerCapability("contentMode")
                val snapshot = state.value
                state.value = snapshot.copy(contentMode = command.mode)
                if (snapshot.player != 0L) bridge.contentMode(snapshot.player, command.mode)
            }
            is RuntimeCommand.AttachSurface -> {
                val snapshot = state.value
                val layer = surfaces.layerOf(command.handle)
                state.compareAndSet(snapshot, snapshot.copy(attachedLayer = layer))
                if (snapshot.player != 0L && layer != null) bridge.attachLayer(snapshot.player, layer)
            }
            is RuntimeCommand.DetachSurface -> {
                val snapshot = state.value
                if (snapshot.player != 0L) bridge.detachLayer(snapshot.player)
                state.compareAndSet(snapshot, snapshot.copy(attachedLayer = null))
            }
            RuntimeCommand.Release -> release()
        }
    }

    /** One engine poll; derives events only from live bridge state. */
    fun poll() {
        val snapshot = state.value
        if (snapshot.released || snapshot.generation == null || snapshot.ended) return
        val eventSink = sink.value ?: return
        when (bridge.itemStatus(snapshot.player)) {
            ITEM_FAILED -> {
                markEnded(snapshot)
                val failure = when (bridge.errorKind(snapshot.player)) {
                    ERROR_NETWORK -> PlayerFailure.Network
                    ERROR_DECODE -> PlayerFailure.Decode
                    else -> PlayerFailure.Engine
                }
                eventSink(RuntimeEvent.Failed(checkNotNull(snapshot.generation), failure))
            }
            ITEM_READY -> {
                // itemStatus READY alone means prepared: live and
                // unknown-duration media carry duration 0 -> Ready(null).
                var updated = snapshot
                if (!snapshot.readyEmitted) {
                    updated = snapshot.copy(readyEmitted = true)
                    state.compareAndSet(snapshot, updated)
                    eventSink(RuntimeEvent.Ready(checkNotNull(updated.generation), bridge.durationMs(updated.player).takeIf { it > 0 }))
                }
                if (!updated.readyEmitted) return
                val tracks = bridge.tracks(updated.player)
                if (tracks != updated.tracks) {
                    val next = updated.copy(tracks = tracks.toList())
                    state.compareAndSet(updated, next); updated = next
                    eventSink(RuntimeEvent.Tracks(checkNotNull(updated.generation), tracks))
                }
                val phase = when (bridge.timeControl(updated.player)) {
                    TIME_PLAYING -> PlayerPhase.Playing
                    TIME_BUFFERING -> PlayerPhase.Buffering
                    else -> {
                        val position = bridge.positionMs(updated.player)
                        val total = bridge.durationMs(updated.player)
                        if (total > 0 && position >= total - 250) PlayerPhase.Ended else PlayerPhase.Paused
                    }
                }
                if (phase != updated.lastPhase) {
                    val marked = updated.copy(lastPhase = phase)
                    state.compareAndSet(updated, marked)
                    updated = marked
                    when (phase) {
                        PlayerPhase.Playing -> eventSink(RuntimeEvent.Playing(checkNotNull(marked.generation)))
                        PlayerPhase.Buffering -> eventSink(RuntimeEvent.Buffering(checkNotNull(marked.generation)))
                        PlayerPhase.Ended -> {
                            markEnded(marked)
                            eventSink(RuntimeEvent.Ended(checkNotNull(marked.generation)))
                            return
                        }
                        else -> eventSink(RuntimeEvent.Paused(checkNotNull(marked.generation)))
                    }
                }
                eventSink(
                    RuntimeEvent.Progress(
                        checkNotNull(updated.generation),
                        bridge.positionMs(updated.player).coerceAtLeast(0),
                        bridge.bufferedMs(updated.player).coerceAtLeast(0),
                        bridge.durationMs(updated.player).takeIf { it > 0 },
                    ),
                )
            }
        }
    }

    private fun load(command: RuntimeCommand.Load) {
        val snapshot = state.value
        val player = if (snapshot.player == 0L) bridge.create() else snapshot.player
        bridge.stop(player) // stop the previous resource before loading the next
        generation.value = command.generation
        state.compareAndSet(
            snapshot,
            RuntimeSnapshot(
                player = player,
                generation = command.generation,
                attachedLayer = snapshot.attachedLayer,
                contentMode = snapshot.contentMode,
            ),
        )
        try {
            bridge.loadUrl(player, command.resource.url, command.resource.headers)
            snapshot.attachedLayer?.let { bridge.attachLayer(player, it) }
            if (capabilities.contentMode) bridge.contentMode(player, snapshot.contentMode)
            sink.value?.invoke(RuntimeEvent.EngineChanged(command.generation, cn.mafei.cmp.player.api.PlayerEngineType.System))
            val backgroundAudio = bridge.configureAudioOnly(player, command.resource.audioOnly)
            sink.value?.invoke(RuntimeEvent.BackgroundAudio(command.generation, command.resource.audioOnly && backgroundAudio))
        } catch (failure: UnsupportedOperationException) {
            markEnded()
            sink.value?.invoke(RuntimeEvent.Failed(command.generation, PlayerFailure.UnsupportedMedia))
        }
    }

    private fun stop(generation: PlaybackGeneration?) {
        val snapshot = state.value
        if (!snapshot.handles(generation)) return
        bridge.stop(snapshot.player)
        state.compareAndSet(
            snapshot,
            RuntimeSnapshot(player = snapshot.player, attachedLayer = snapshot.attachedLayer),
        )
    }

    private fun release() {
        val snapshot = state.value
        if (snapshot.released) return
        state.compareAndSet(snapshot, RuntimeSnapshot(released = true))
        pollTicket.value?.close()
        pollTicket.value = null
        if (snapshot.player != 0L) bridge.releasePlayer(snapshot.player)
        sink.value = null // contract: sink must not be retained after Release
    }

    private fun markEnded(snapshot: RuntimeSnapshot? = null) {
        val current = snapshot ?: state.value
        state.compareAndSet(current, current.copy(ended = true))
    }

    private data class RuntimeSnapshot(
        val player: Long = 0L,
        val generation: PlaybackGeneration? = null,
        val readyEmitted: Boolean = false,
        val ended: Boolean = false,
        val released: Boolean = false,
        val lastPhase: PlayerPhase? = null,
        val attachedLayer: Any? = null,
        val contentMode: cn.mafei.cmp.player.api.PlayerContentMode = cn.mafei.cmp.player.api.PlayerContentMode.Fit,
        val tracks: List<cn.mafei.cmp.player.api.PlayerTrack> = emptyList(),
    ) {
        fun handles(candidate: PlaybackGeneration?): Boolean = candidate != null && candidate === generation
    }

    internal companion object {
        const val TIME_PAUSED = 0
        const val TIME_BUFFERING = 1
        const val TIME_PLAYING = 2
        const val ITEM_READY = 1
        const val ITEM_FAILED = 2
        const val ERROR_NETWORK = 1
        const val ERROR_DECODE = 2
    }
}
