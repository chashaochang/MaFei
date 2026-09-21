package cn.mafei.cmp.player.runtime_ohos

import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import cn.mafei.cmp.player.api.PlayableResource
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import cn.mafei.cmp.player.api.UnsupportedPlayerCapability
import kotlin.concurrent.AtomicReference

/**
 * HarmonyOS engine bridge over the native AVPlayer (`media_avplayer` C API).
 *
 * STATUS: INTERFACE + TRANSLATION LAYER ONLY — the real engine implementation
 * belongs to the CPF/HarmonyOS thin host (XComponent surface + native
 * AVPlayer session, see docs/glm05-player-runtime.md §OHOS wiring). No real
 * engine evidence exists in this batch, so nothing here fakes readiness,
 * playing or progress: all events derive from live bridge queries.
 */
interface OhosAvPlayerBridge {
    fun create(): Long

    /**
     * Loads [url] with [headers]. The native AVPlayer media source accepts
     * HTTP headers (Feiniu needs token/cookie); implementations unable to
     * apply them MUST throw (the runtime turns that into
     * Failed(UnsupportedMedia)) instead of silently dropping them.
     */
    fun loadUrl(handle: Long, url: String, headers: Map<String, String>)

    /**
     * Loads a source and, when present, attaches one externally delivered
     * subtitle URL. The three-argument overload remains the compatibility
     * entry point for existing bridge implementations and tests.
     */
    fun loadUrl(handle: Long, url: String, headers: Map<String, String>, subtitleUrl: String?) {
        loadUrl(handle, url, headers)
    }
    fun play(handle: Long)
    fun pause(handle: Long)
    fun stop(handle: Long)
    fun seek(handle: Long, positionMs: Long)
    fun setSpeed(handle: Long, speed: Float)

    /** [surfaceId] is the XComponent surface id string provided by the host. */
    fun attachSurface(handle: Long, surfaceId: String?)
    fun detachSurface(handle: Long)
    fun positionMs(handle: Long): Long
    fun durationMs(handle: Long): Long
    fun bufferedMs(handle: Long): Long

    /** 0 paused, 1 buffering, 2 playing. */
    fun timeControl(handle: Long): Int

    /** 0 unknown, 1 prepared/ready, 2 failed. */
    fun itemStatus(handle: Long): Int

    /** 0 none, 1 network, 2 decode. */
    fun errorKind(handle: Long): Int
    fun releasePlayer(handle: Long)
}

/**
 * OHOS surface registry keyed by XComponent surface ids (opaque frozen
 * `PlayerSurfaceHandle` identity on the outside).
 */
class OhosSurfaceRegistry {
    private val surfaces = AtomicReference(emptyMap<PlayerSurfaceHandle, String>())

    fun register(surfaceId: String): PlayerSurfaceHandle {
        val handle = PlayerSurfaceHandle()
        while (true) {
            val current = surfaces.value
            if (surfaces.compareAndSet(current, current + (handle to surfaceId))) return handle
        }
    }

    fun surfaceIdOf(handle: PlayerSurfaceHandle): String? = surfaces.value[handle]

    fun unregister(handle: PlayerSurfaceHandle): Boolean {
        while (true) {
            val current = surfaces.value
            if (!current.containsKey(handle)) return false
            if (surfaces.compareAndSet(current, current - handle)) return true
        }
    }
}

/** Periodic engine polling scheduler; the host supplies a main-loop timer. */
interface OhosPollScheduler {
    fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable
}

/**
 * OHOS runtime translating the frozen contract onto [OhosAvPlayerBridge].
 * Same poll-driven, generation-disciplined translation as the other runtimes;
 * single-threaded by contract.
 */
class OhosPlayerRuntime(
    override val capabilities: PlayerCapabilities,
    private val bridge: OhosAvPlayerBridge,
    private val surfaces: OhosSurfaceRegistry = OhosSurfaceRegistry(),
    private val pollScheduler: OhosPollScheduler,
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
        val snapshot = state.value
        when (command) {
            is RuntimeCommand.Load -> load(command)
            is RuntimeCommand.Play -> if (snapshot.handles(command.generation)) bridge.play(snapshot.player)
            is RuntimeCommand.Pause -> if (snapshot.handles(command.generation)) bridge.pause(snapshot.player)
            is RuntimeCommand.Seek -> if (snapshot.handles(command.generation)) bridge.seek(snapshot.player, command.positionMs)
            is RuntimeCommand.Stop -> stop(command.generation)
            is RuntimeCommand.Speed -> if (snapshot.handles(command.generation)) bridge.setSpeed(snapshot.player, command.value)
            is RuntimeCommand.SelectTrack ->
                // No OHOS track negotiation in P0; refuse honestly.
                throw UnsupportedPlayerCapability("trackSelection")
            is RuntimeCommand.AttachSurface -> {
                val surfaceId = surfaces.surfaceIdOf(command.handle)
                state.compareAndSet(snapshot, snapshot.copy(attachedSurfaceId = surfaceId))
                if (snapshot.player != 0L && surfaceId != null) bridge.attachSurface(snapshot.player, surfaceId)
            }
            is RuntimeCommand.DetachSurface -> {
                if (snapshot.player != 0L) bridge.detachSurface(snapshot.player)
                state.compareAndSet(snapshot, snapshot.copy(attachedSurfaceId = null))
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
                // itemStatus READY/prepared alone means ready: live and
                // unknown-duration media carry duration 0 -> Ready(null).
                var updated = snapshot
                if (!snapshot.readyEmitted) {
                    updated = snapshot.copy(readyEmitted = true)
                    state.compareAndSet(snapshot, updated)
                    eventSink(RuntimeEvent.Ready(checkNotNull(updated.generation), bridge.durationMs(updated.player).takeIf { it > 0 }))
                }
                if (!updated.readyEmitted) return
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
                attachedSurfaceId = snapshot.attachedSurfaceId,
            ),
        )
        try {
            bridge.loadUrl(
                player,
                command.resource.url,
                command.resource.headers,
                command.resource.subtitleUrl,
            )
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
            RuntimeSnapshot(player = snapshot.player, attachedSurfaceId = snapshot.attachedSurfaceId),
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
        val attachedSurfaceId: String? = null,
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
