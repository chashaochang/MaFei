package cn.mafei.cmp.player.runtime_android

import cn.mafei.cmp.player.api.*

/**
 * Command translation between the frozen [PlayerRuntime] contract and an
 * [AndroidPlayerEngine] (in production `android.media.MediaPlayer`). Every
 * emitted event is derived from an engine callback or a live engine query —
 * the runtime never fabricates ready/playing/progress on its own.
 *
 * Threading: [execute] and engine callbacks must arrive on the engine's
 * required thread (the main looper in production, [AndroidPollScheduler]
 * included); the runtime itself is single-threaded by contract.
 *
 * Generation discipline: events for a retired engine session are dropped
 * before they can reach the sink, so an old player can never be mistaken for
 * the current load. Rapid re-load resets the engine and bumps the session.
 */
class AndroidMediaPlayerRuntime(
    override val capabilities: PlayerCapabilities,
    private val engine: AndroidPlayerEngine,
    private val pollScheduler: AndroidPollScheduler,
    private val surfaces: AndroidSurfaceRegistry = AndroidSurfaceRegistry(),
    private val pollPeriodMs: Long = 500L,
) : PlayerRuntime {

    init { require(pollPeriodMs > 0) }

    private var sink: ((RuntimeEvent) -> Unit)? = null
    private var session = 0
    private var generation: PlaybackGeneration? = null
    private var preparedEmitted = false
    private var ended = false
    private var released = false
    private var lastPhase: PlayerPhase? = null
    private var pendingSurface: Any? = null
    private var attachedSurface: Any? = null
    private var pollTicket: AutoCloseable? = null

    override fun bind(eventSink: (RuntimeEvent) -> Unit) {
        check(sink == null) { "Runtime already bound" }
        sink = eventSink
        engine.setListener(EngineListener())
        pollTicket = pollScheduler.schedule(pollPeriodMs, ::poll)
    }

    override fun execute(command: RuntimeCommand) {
        if (released) return
        when (command) {
            is RuntimeCommand.Load -> load(command)
            is RuntimeCommand.Play -> if (owns(command.generation)) engine.start()
            is RuntimeCommand.Pause -> if (owns(command.generation)) engine.pause()
            is RuntimeCommand.Seek -> if (owns(command.generation)) engine.seekTo(command.positionMs)
            is RuntimeCommand.Stop -> stop(command.generation)
            is RuntimeCommand.Speed -> if (owns(command.generation)) engine.setPlaybackSpeed(command.value)
            is RuntimeCommand.SelectTrack ->
                // android.media.MediaPlayer track selection is device-dependent;
                // honesty first: unsupported instead of a silently ignored command.
                throw UnsupportedPlayerCapability("trackSelection")
            is RuntimeCommand.AttachSurface -> {
                // Always forwarded immediately: attaching before preparation is
                // legal for MediaPlayer and must not silently wait.
                pendingSurface = surfaces.surfaceOf(command.handle)
                engine.setSurface(pendingSurface)
                attachedSurface = pendingSurface
            }
            is RuntimeCommand.DetachSurface -> {
                pendingSurface = null
                attachedSurface = null
                engine.setSurface(null)
            }
            RuntimeCommand.Release -> release()
        }
    }

    /** One engine poll: derives phase/progress events from live engine queries only. */
    fun poll() {
        if (released || generation == null || ended || sink == null) return
        val duration = engine.durationMs
        if (!preparedEmitted && duration > 0) {
            // Fallback for engines whose onPrepared raced a poll; still engine-derived.
            emitPrepared(duration)
        }
        if (!preparedEmitted) return
        val phase = if (engine.isPlaying) PlayerPhase.Playing else PlayerPhase.Paused
        if (phase != lastPhase) {
            lastPhase = phase
            emit(
                if (phase == PlayerPhase.Playing) RuntimeEvent.Playing(generation!!)
                else RuntimeEvent.Paused(generation!!),
            )
        }
        sink?.invoke(
            RuntimeEvent.Progress(generation!!, engine.currentPositionMs.coerceAtLeast(0), engine.bufferedPositionMs.coerceAtLeast(0)),
        )
    }

    private fun load(command: RuntimeCommand.Load) {
        session++
        generation = command.generation
        preparedEmitted = false
        ended = false
        lastPhase = null
        engine.reset()
        engine.setDataSource(command.resource.url, command.resource.headers)
        pendingSurface?.let { surface ->
            engine.setSurface(surface)
            attachedSurface = surface
        } ?: run { attachedSurface = null }
        engine.prepareAsync()
    }

    private fun stop(generation: PlaybackGeneration?) {
        if (!owns(generation)) return
        engine.reset()
        this.generation = null
        preparedEmitted = false
        ended = false
        lastPhase = null
    }

    private fun release() {
        if (released) return
        released = true
        engine.setListener(null)
        engine.release()
        pollTicket?.close()
        pollTicket = null
        generation = null
        sink = null // contract: sink must not be retained after Release
    }

    private fun owns(generation: PlaybackGeneration?): Boolean = generation != null && generation === this.generation

    private fun emitPrepared(duration: Long) {
        preparedEmitted = true
        emit(RuntimeEvent.Ready(checkNotNull(generation), duration.takeIf { it > 0 }))
    }

    private fun emit(event: RuntimeEvent) {
        sink?.invoke(event)
    }

    private inner class EngineListener : AndroidPlayerEngineListener {
        override fun onPrepared(session: Int, durationMs: Long) {
            if (released || session != this@AndroidMediaPlayerRuntime.session || preparedEmitted) return
            emitPrepared(durationMs)
        }

        override fun onCompletion(session: Int) {
            if (released || session != this@AndroidMediaPlayerRuntime.session || ended) return
            ended = true
            emit(RuntimeEvent.Ended(checkNotNull(generation)))
        }

        override fun onError(session: Int, what: Int, extra: Int) {
            if (released || session != this@AndroidMediaPlayerRuntime.session) return
            ended = true
            emit(RuntimeEvent.Failed(checkNotNull(generation), PlayerFailure.Decode))
        }

        override fun onBuffering(session: Int, buffering: Boolean) {
            if (released || session != this@AndroidMediaPlayerRuntime.session || !preparedEmitted) return
            if (buffering) emit(RuntimeEvent.Buffering(checkNotNull(generation)))
            // Buffering end re-classifies via the next poll (isPlaying query).
        }
    }
}
