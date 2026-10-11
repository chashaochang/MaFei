package cn.mafei.cmp.player.runtime_macos

import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import cn.mafei.cmp.player.api.PlayableResource
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import java.util.concurrent.atomic.AtomicInteger

/**
 * Engine abstraction over AVFoundation so the command translation is
 * testable with a fake; production wiring is [JniAvPlayerBridge]. All values
 * are live engine queries — the runtime derives every event from them.
 */
interface AvPlayerBridge {
    fun create(): Long

    /**
     * Loads [url] with [headers]. Engines that cannot apply custom HTTP
     * headers MUST throw (the runtime turns that into Failed(UnsupportedMedia))
     * instead of silently playing an unauthenticated stream.
     */
    fun loadUrl(handle: Long, url: String, headers: Map<String, String>)
    fun play(handle: Long)
    fun pause(handle: Long)
    fun stop(handle: Long)
    fun seek(handle: Long, positionMs: Long)
    fun setSpeed(handle: Long, speed: Float)
    fun attachLayer(handle: Long, layerPointer: Long)

    /** Unbinds [layerPointer]; a destroyed surface must not keep the last frame or the player. */
    fun detachLayer(handle: Long, layerPointer: Long)
    fun positionMs(handle: Long): Long
    fun durationMs(handle: Long): Long
    fun bufferedMs(handle: Long): Long

    /** 0 paused, 1 waiting/buffering, 2 playing (AVPlayerTimeControlStatus). */
    fun timeControl(handle: Long): Int

    /** 0 unknown, 1 readyToPlay, 2 failed (AVPlayerItemStatus). */
    fun itemStatus(handle: Long): Int

    /** 0 none, 1 network, 2 decode. */
    fun errorKind(handle: Long): Int
    fun releasePlayer(handle: Long)
    fun tracks(handle: Long): List<cn.mafei.cmp.player.api.PlayerTrack> = emptyList()
    fun selectTrack(handle: Long, kind: cn.mafei.cmp.player.api.TrackKind, id: String?) {
        throw UnsupportedOperationException("Track selection unavailable")
    }
    fun contentMode(layer: Long, mode: cn.mafei.cmp.player.api.PlayerContentMode) {
        throw UnsupportedOperationException("Content mode unavailable")
    }
}

/** JNI wiring of [AvPlayerBridge]. Loads the bundled dylib on first use. */
class JniAvPlayerBridge : AvPlayerBridge {
    private val handles = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private val proxyIds = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private var proxy: AuthorizedMediaProxy? = null
    override fun create(): Long = NativeAvPlayer.let { it.ensureLoaded(); it.create().also(handles::add) }
    override fun loadUrl(handle: Long, url: String, headers: Map<String, String>) {
        retireSource(handle)
        val target = if (headers.isEmpty()) url else {
            val service = proxy ?: AuthorizedMediaProxy().also { proxy = it }
            val (id, local) = service.register(url, headers); proxyIds[handle] = id; local
        }
        NativeAvPlayer.loadUrl(handle, target)
    }
    override fun play(handle: Long) = NativeAvPlayer.play(handle)
    override fun pause(handle: Long) = NativeAvPlayer.pause(handle)
    override fun stop(handle: Long) { NativeAvPlayer.stop(handle); retireSource(handle) }
    override fun seek(handle: Long, positionMs: Long) = NativeAvPlayer.seek(handle, positionMs)
    override fun setSpeed(handle: Long, speed: Float) = NativeAvPlayer.setSpeed(handle, speed)
    override fun attachLayer(handle: Long, layerPointer: Long) = NativeAvPlayer.attachLayer(handle, layerPointer)
    override fun detachLayer(handle: Long, layerPointer: Long) = NativeAvPlayer.detachLayer(handle, layerPointer)
    override fun positionMs(handle: Long): Long = NativeAvPlayer.positionMs(handle)
    override fun durationMs(handle: Long): Long = NativeAvPlayer.durationMs(handle)
    override fun bufferedMs(handle: Long): Long = NativeAvPlayer.bufferedMs(handle)
    override fun timeControl(handle: Long): Int = NativeAvPlayer.timeControl(handle)
    override fun itemStatus(handle: Long): Int = NativeAvPlayer.itemStatus(handle)
    override fun errorKind(handle: Long): Int = NativeAvPlayer.errorKind(handle)
    override fun releasePlayer(handle: Long) {
        if (!handles.remove(handle)) return
        retireSource(handle); NativeAvPlayer.releasePlayer(handle)
        if (handles.isEmpty()) { proxy?.close(); proxy = null }
    }
    private fun retireSource(handle: Long) { proxyIds.remove(handle)?.let { proxy?.retire(it) } }
    override fun tracks(handle: Long) = decodeNativeTracks(NativeAvPlayer.tracks(handle))
    override fun selectTrack(handle: Long, kind: cn.mafei.cmp.player.api.TrackKind, id: String?) =
        NativeAvPlayer.selectTrack(handle, if (kind == cn.mafei.cmp.player.api.TrackKind.Audio) 0 else 1, id?.toIntOrNull() ?: -1)
    override fun contentMode(layer: Long, mode: cn.mafei.cmp.player.api.PlayerContentMode) = NativeAvPlayer.setContentMode(layer, mode.fills)
}

/**
 * macOS video surface registry. `PlayerSurfaceHandle` is opaque (frozen API);
 * this registry maps handles to native `AVPlayerLayer*` pointers owned by the
 * UI layer. Video compositing itself is a UI concern (Pending); audio needs
 * no layer.
 */
class MacSurfaceRegistry {
    private val nextId = AtomicInteger(1)
    private val layers = HashMap<PlayerSurfaceHandle, Long>()

    /** Registers a native AVPlayerLayer pointer (0 rejects). */
    fun register(layerPointer: Long): PlayerSurfaceHandle {
        require(layerPointer != 0L) { "layerPointer must be a live AVPlayerLayer*" }
        val handle = PlayerSurfaceHandle()
        synchronized(layers) { layers[handle] = layerPointer }
        return handle
    }

    fun layerOf(handle: PlayerSurfaceHandle): Long = synchronized(layers) { layers[handle] ?: 0L }

    fun unregister(handle: PlayerSurfaceHandle): Boolean = synchronized(layers) { layers.remove(handle) != null }
}

/**
 * macOS runtime over AVFoundation through [AvPlayerBridge]. Poll-driven:
 * every RuntimeEvent is derived from live bridge queries, never fabricated.
 * Commands AND polls are serialized onto one internal [dispatcher] thread, so
 * state fields need no synchronization and the facade's calling thread never
 * races the poll loop.
 */
/** Periodic engine polling scheduler; production wiring uses a background executor. */
interface MacPollScheduler {
    fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable
}

/** Default JVM scheduler; single-threaded so polls stay ordered. */
class ScheduledPollScheduler : MacPollScheduler {
    override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable {
        val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "macos-avplayer-poll").apply { isDaemon = true }
        }
        val future = executor.scheduleAtFixedRate({
            try { tick() } catch (_: Throwable) {}
        }, periodMs, periodMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        return AutoCloseable { future.cancel(false); executor.shutdown() }
    }
}

class MacOsAvPlayerRuntime(
    override val capabilities: PlayerCapabilities,
    private val bridge: AvPlayerBridge,
    private val surfaces: MacSurfaceRegistry = MacSurfaceRegistry(),
    private val pollScheduler: MacPollScheduler = ScheduledPollScheduler(),
    private val pollPeriodMs: Long = 250L,
    private val dispatcher: java.util.concurrent.Executor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "macos-avplayer-runtime").apply { isDaemon = true }
    },
) : PlayerRuntime {

    // All state below is confined to [dispatcher]: commands AND polls are
    // serialized onto one queue, so no field needs volatile/synchronized.
    private var sink: ((RuntimeEvent) -> Unit)? = null
    private var player: Long = 0
    private var generation: PlaybackGeneration? = null
    private var readyEmitted = false
    private var ended = false
    private val released = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastPhase: PlayerPhase? = null
    private var attachedLayer: Long = 0
    private var tracks = emptyList<cn.mafei.cmp.player.api.PlayerTrack>()
    private var contentMode = cn.mafei.cmp.player.api.PlayerContentMode.Fit
    private var pollTicket: AutoCloseable? = null

    override fun bind(eventSink: (RuntimeEvent) -> Unit) {
        check(sink == null) { "Runtime already bound" }
        sink = eventSink
        pollTicket = pollScheduler.schedule(pollPeriodMs) { dispatcher.execute(::poll) }
    }

    override fun execute(command: RuntimeCommand) {
        // Release claims idempotence BEFORE enqueueing: after the dispatcher
        // is shut down, enqueueing would throw RejectedExecutionException and
        // a second release() would not be a no-op.
        if (command is RuntimeCommand.Release) {
            if (!released.compareAndSet(false, true)) return
            dispatcher.execute { releaseOnQueue() }
            return
        }
        if (released.get()) return
        // Keep capability failures synchronous and consistent with the other
        // runtimes. The remaining commands are dispatched asynchronously and
        // therefore cannot surface an exception to the caller.
        if (command is RuntimeCommand.SelectTrack && !capabilities.trackSelection) {
            throw cn.mafei.cmp.player.api.UnsupportedPlayerCapability("trackSelection")
        }
        // AVPlayerLayer gravity is not exposed through the bundled ObjC bridge,
        // and the desktop host attaches no video surface at all, so the picture
        // fitting stays unavailable instead of silently doing nothing.
        if (command is RuntimeCommand.ContentMode && !capabilities.contentMode) {
            throw cn.mafei.cmp.player.api.UnsupportedPlayerCapability("contentMode")
        }
        try {
            dispatcher.execute { executeOnQueue(command) }
        } catch (rejected: java.util.concurrent.RejectedExecutionException) {
            // Release may win the race between the flag check above and
            // enqueueing. Once released, dropping a late command is the
            // contract; it must not escape as a lifecycle error.
            if (!released.get()) throw rejected
        }
    }

    private fun executeOnQueue(command: RuntimeCommand) {
        if (released.get()) return
        when (command) {
            is RuntimeCommand.Load -> load(command)
            is RuntimeCommand.Play -> if (owns(command.generation)) bridge.play(player)
            is RuntimeCommand.Pause -> if (owns(command.generation)) bridge.pause(player)
            is RuntimeCommand.Seek -> if (owns(command.generation)) bridge.seek(player, command.positionMs)
            is RuntimeCommand.Stop -> stop(command.generation)
            is RuntimeCommand.Speed -> if (owns(command.generation)) bridge.setSpeed(player, command.value)
            is RuntimeCommand.SelectTrack -> if (owns(command.generation)) bridge.selectTrack(player, command.kind, command.id)
            is RuntimeCommand.ContentMode -> {
                contentMode = command.mode
                if (attachedLayer != 0L) bridge.contentMode(attachedLayer, command.mode)
            }
            is RuntimeCommand.AttachSurface -> {
                val layer = surfaces.layerOf(command.handle)
                if (player != 0L && layer != 0L) bridge.attachLayer(player, layer)
                attachedLayer = layer
                if (layer != 0L && capabilities.contentMode) bridge.contentMode(layer, contentMode)
            }
            is RuntimeCommand.DetachSurface -> {
                if (player != 0L) bridge.detachLayer(player, attachedLayer)
                attachedLayer = 0
            }
            // Release is claimed in execute() and runs via releaseOnQueue();
            // this branch is unreachable but keeps the sealed when exhaustive.
            RuntimeCommand.Release -> Unit
        }
    }

    /** One engine poll; derives events only from live bridge state. */
    fun poll() {
        if (released.get() || generation == null || sink == null) return
        if (ended) return
        when (bridge.itemStatus(player)) {
            ITEM_FAILED -> {
                ended = true
                val failure = when (bridge.errorKind(player)) {
                    ERROR_NETWORK -> PlayerFailure.Network
                    ERROR_DECODE -> PlayerFailure.Decode
                    else -> PlayerFailure.Engine
                }
                emit(RuntimeEvent.Failed(checkNotNull(generation), failure))
                return
            }
            ITEM_READY -> {
                // itemStatus READY alone means prepared: live and
                // unknown-duration media carry duration 0 -> Ready(null).
                if (!readyEmitted) {
                    readyEmitted = true
                    emit(RuntimeEvent.Ready(checkNotNull(generation), bridge.durationMs(player).takeIf { it > 0 }))
                }
                val phase = when (bridge.timeControl(player)) {
                    TIME_PLAYING -> PlayerPhase.Playing
                    TIME_BUFFERING -> PlayerPhase.Buffering
                    else -> {
                        // Playing may have finished silently; detect that from
                        // position vs duration instead of trusting timeControl.
                        val position = bridge.positionMs(player)
                        val total = bridge.durationMs(player)
                        if (total > 0 && position >= total - 250) PlayerPhase.Ended else PlayerPhase.Paused
                    }
                }
                if (phase != lastPhase) {
                    lastPhase = phase
                    when (phase) {
                        PlayerPhase.Playing -> emit(RuntimeEvent.Playing(checkNotNull(generation)))
                        PlayerPhase.Buffering -> emit(RuntimeEvent.Buffering(checkNotNull(generation)))
                        PlayerPhase.Ended -> {
                            ended = true
                            emit(RuntimeEvent.Ended(checkNotNull(generation)))
                            return
                        }
                        else -> emit(RuntimeEvent.Paused(checkNotNull(generation)))
                    }
                }
                val latestTracks = bridge.tracks(player)
                if (latestTracks != tracks) { tracks = latestTracks.toList(); emit(RuntimeEvent.Tracks(checkNotNull(generation), tracks)) }
                emit(
                    RuntimeEvent.Progress(
                        checkNotNull(generation),
                        bridge.positionMs(player).coerceAtLeast(0),
                        bridge.bufferedMs(player).coerceAtLeast(0),
                        bridge.durationMs(player).takeIf { it > 0 },
                    ),
                )
            }
        }
    }

    private fun load(command: RuntimeCommand.Load) {
        if (player == 0L) player = bridge.create()
        bridge.stop(player) // stop the previous resource before loading the next
        generation = command.generation
        readyEmitted = false
        ended = false
        lastPhase = null
        try {
            bridge.loadUrl(player, command.resource.url, command.resource.headers)
        } catch (failure: UnsupportedOperationException) {
            ended = true
            emit(RuntimeEvent.Failed(checkNotNull(generation), PlayerFailure.UnsupportedMedia))
            return
        }
        if (attachedLayer != 0L) bridge.attachLayer(player, attachedLayer)
        if (attachedLayer != 0L && capabilities.contentMode) bridge.contentMode(attachedLayer, contentMode)
        tracks = emptyList()
        emit(RuntimeEvent.EngineChanged(command.generation, cn.mafei.cmp.player.api.PlayerEngineType.System))
    }

    private fun stop(generation: PlaybackGeneration?) {
        if (!owns(generation)) return
        bridge.stop(player)
        this.generation = null
        readyEmitted = false
        ended = false
        lastPhase = null
    }

    private fun releaseOnQueue() {
        pollTicket?.close()
        pollTicket = null
        if (player != 0L) bridge.releasePlayer(player)
        generation = null
        sink = null // contract: sink must not be retained after Release
        (dispatcher as? java.util.concurrent.ExecutorService)?.shutdown()
    }

    private fun owns(generation: PlaybackGeneration?): Boolean = generation != null && generation === this.generation

    private fun emit(event: RuntimeEvent) {
        sink?.invoke(event)
    }

    internal companion object {
        const val TIME_PAUSED = 0
        const val TIME_BUFFERING = 1
        const val TIME_PLAYING = 2
        const val ITEM_UNKNOWN = 0
        const val ITEM_READY = 1
        const val ITEM_FAILED = 2
        const val ERROR_NONE = 0
        const val ERROR_NETWORK = 1
        const val ERROR_DECODE = 2
    }
}

/**
 * Production entry point for the macOS AVPlayer engine. Video output needs an
 * AVPlayerLayer composed by the UI layer (register via MacSurfaceRegistry);
 * audio works without a layer. JNI link verification: JniAvPlayerBridgeTest.
 */
fun createMacOsPlayerRuntime(surfaces: MacSurfaceRegistry = MacSurfaceRegistry()): PlayerRuntime = MacOsAvPlayerRuntime(
    capabilities = cn.mafei.cmp.player.api.PlayerCapabilities(
        seek = true,
        speed = true,
        trackSelection = true,
        contentMode = true,
        backgroundPlayback = false,
    ),
    bridge = JniAvPlayerBridge(),
    surfaces = surfaces,
)
