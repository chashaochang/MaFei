package cn.mafei.cmp.player.facade

import cn.mafei.cmp.player.api.*
import cn.mafei.cmp.player.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes commands and callbacks; owns the injected runtime until release/scope cancellation.
 * Runtime calls and report sink must be non-blocking. Report sink should enqueue provider I/O
 * in the data layer and bind generations to in-memory reporting contexts; this module never
 * sees Provider DTOs. Sink failures are counted, never silently turned into playback errors.
 * [nowMs] should be a monotonic clock; backwards values trigger a fresh progress report.
 */
class DefaultPlayerController(
    private val runtime: PlayerRuntime,
    scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val reportSink: (PlayerReport) -> Unit = {},
    reportIntervalMs: Long = 10_000,
) : PlayerController {
    override val capabilities = runtime.capabilities
    private val machine = PlayerStateMachine(capabilities, reportIntervalMs)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(machine.state)
    override val state = mutableState.asStateFlow()
    private val failedReports = MutableStateFlow(0L)
    val reportDeliveryFailures = failedReports.asStateFlow()
    private val failedCommands = MutableStateFlow(0L)
    /** Includes failed stop/release attempts; Released means controller closed, not native disposal proof. */
    val runtimeCommandFailures = failedCommands.asStateFlow()
    private val events = Channel<RuntimeEvent>(Channel.UNLIMITED)
    private var closed = false
    private val eventJob: Job

    init {
        runtime.bind { events.trySend(it) }
        eventJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                for (event in events) mutex.withLock {
                    if (!closed) apply(machine.accept(event, nowMs()))
                }
            } finally {
                withContext(NonCancellable) { mutex.withLock { closeLocked() } }
            }
        }
    }

    override suspend fun load(queue: List<PlayableResource>, index: Int, resumePositionMs: Long, autoplay: Boolean): PlaybackGeneration = mutex.withLock {
        currentCoroutineContext().ensureActive(); check(!closed) { "Player released" }
        apply(machine.load(queue, index, resumePositionMs, autoplay))
        checkNotNull(machine.state.generation)
    }
    override suspend fun play() = command { play() }
    override suspend fun pause() = command { pause() }
    override suspend fun seek(positionMs: Long) = command { seek(positionMs) }
    override suspend fun stop() = command { stop() }
    override suspend fun speed(value: Float) = command { speed(value) }
    override suspend fun selectTrack(kind: TrackKind, id: String?) = command { selectTrack(kind, id) }
    override suspend fun attachSurface(handle: PlayerSurfaceHandle) = command { attachSurface(handle) }
    override suspend fun detachSurface(handle: PlayerSurfaceHandle) = command { detachSurface(handle) }
    override suspend fun background() = command { background() }
    override suspend fun foreground() = command { foreground() }
    override suspend fun cancelLoad(generation: PlaybackGeneration) = command { cancelLoad(generation) }

    override suspend fun release() {
        // Cleanup is deliberately non-cancellable and idempotent; never await the collector
        // while holding its mutex, including synchronous native callback paths.
        withContext(NonCancellable) { mutex.withLock { closeLocked() }; eventJob.cancelAndJoin() }
    }

    private suspend fun command(reduce: PlayerStateMachine.() -> List<PlayerEffect>) = mutex.withLock {
        currentCoroutineContext().ensureActive(); check(!closed) { "Player released" }
        apply(machine.reduce())
    }

    private fun closeLocked() {
        if (closed) return
        closed = true
        apply(machine.release())
        events.close()
    }

    private fun apply(effects: List<PlayerEffect>) {
        var engineFailed = false
        for (effect in effects) when (effect) {
            is PlayerEffect.Report -> deliver(effect.value)
            is PlayerEffect.Engine -> {
                // A failed seek/load must never be followed by play. Release still runs
                // after a stop failure so native resources can always be relinquished.
                if (engineFailed && effect.command !is RuntimeCommand.Release) continue
                try { runtime.execute(effect.command) } catch (_: Exception) {
                    failedCommands.value = failedCommands.value + 1
                    engineFailed = true
                }
            }
        }
        if (engineFailed && machine.state.phase != PlayerPhase.Released) {
            machine.state.generation?.let { generation ->
                for (effect in machine.accept(RuntimeEvent.Failed(generation, PlayerFailure.Engine), nowMs())) {
                    when (effect) {
                        is PlayerEffect.Report -> deliver(effect.value)
                        is PlayerEffect.Engine -> try { runtime.execute(effect.command) } catch (_: Exception) {
                            failedCommands.value = failedCommands.value + 1
                        }
                    }
                }
            }
        }
        mutableState.value = machine.state
    }

    private fun deliver(report: PlayerReport) {
        try { reportSink(report) } catch (_: Exception) { failedReports.value = failedReports.value + 1 }
    }
}
