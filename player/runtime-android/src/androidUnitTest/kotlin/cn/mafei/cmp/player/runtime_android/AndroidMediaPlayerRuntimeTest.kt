package cn.mafei.cmp.player.runtime_android

import cn.mafei.cmp.player.api.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

private class FakeAndroidEngine : AndroidPlayerEngine {
    val log = mutableListOf<String>()
    val surfaces = mutableListOf<Any?>()
    var listenerRef: AndroidPlayerEngineListener? = null
    private val sessions = AtomicInteger(0)
    override val session: Int get() = sessionValue
    @Volatile var sessionValue = 0
    @Volatile override var isPlaying = false
    @Volatile var positionMs = 0L
    @Volatile override var durationMs = 0L
    @Volatile var bufferedMs = 0L

    override val currentPositionMs: Long get() = positionMs
    override val bufferedPositionMs: Long get() = bufferedMs

    override fun setListener(listener: AndroidPlayerEngineListener?) { listenerRef = listener }
    override fun setSurface(surface: Any?) { log += "setSurface"; surfaces += surface }
    override fun setDataSource(url: String, headers: Map<String, String>) { log += "dataSource($url,${headers.keys.sorted()})" }
    override fun prepareAsync() { log += "prepareAsync" }
    override fun start() { log += "start" }
    override fun pause() { log += "pause" }
    override fun seekTo(positionMs: Long) { log += "seek($positionMs)" }
    override fun setPlaybackSpeed(speed: Float) { log += "speed($speed)" }
    override fun reset() { sessionValue = sessions.incrementAndGet(); log += "reset" }
    override fun release() { log += "release" }

    fun emitPrepared(duration: Long, session: Int = this.session) = listenerRef?.onPrepared(session, duration)
    fun emitEnded(session: Int = this.session) = listenerRef?.onCompletion(session)
    fun emitError(session: Int = this.session) = listenerRef?.onError(session, 1, 0)
    fun emitBuffering(buffering: Boolean, session: Int = this.session) = listenerRef?.onBuffering(session, buffering)
}

private class ManualPollScheduler : AndroidPollScheduler {
    val ticks = mutableListOf<() -> Unit>()
    var closed = false
    override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable {
        ticks += tick
        return AutoCloseable { closed = true }
    }
    fun tickAll() = ticks.forEach { it() }
}

class AndroidMediaPlayerRuntimeTest {

    private val engine = FakeAndroidEngine()
    private val scheduler = ManualPollScheduler()
    private val registry = AndroidSurfaceRegistry()
    private val events = mutableListOf<RuntimeEvent>()
    private val runtime = AndroidMediaPlayerRuntime(
        capabilities = PlayerCapabilities(trackSelection = false),
        engine = engine,
        pollScheduler = scheduler,
        surfaces = registry,
        pollPeriodMs = 500L,
    )

    private fun loadAndPrepare(url: String = "https://stream.test/a.m3u8"): PlaybackGeneration {
        runtime.bind { events += it }
        val generation = PlaybackGeneration()
        runtime.execute(RuntimeCommand.Load(generation, PlayableResource("res-1", url, mapOf("Authorization" to "tok"))))
        engine.emitPrepared(60_000)
        return generation
    }

    private fun eventKinds() = events.map { it::class.simpleName }

    @Test
    fun loadSetsDataSourceWithHeadersPreparesAndForwardsReady() {
        val generation = loadAndPrepare()
        assertTrue(engine.log.contains("reset"))
        assertTrue(engine.log.any { it.contains("dataSource(https://stream.test/a.m3u8,[Authorization])") }, engine.log.toString())
        assertTrue(engine.log.contains("prepareAsync"))
        val ready = events.filterIsInstance<RuntimeEvent.Ready>().single()
        assertTrue(ready.generation === generation)
        assertEquals(60_000L, ready.durationMs)
    }

    @Test
    fun staleSessionEventsAreDropped() {
        val first = loadAndPrepare()
        // Rapid re-load retires the old session.
        val second = PlaybackGeneration()
        runtime.execute(RuntimeCommand.Load(second, PlayableResource("res-2", "https://stream.test/b.mp3")))
        engine.emitPrepared(60_000, session = engine.session - 1) // stale session
        engine.emitEnded(session = engine.session - 1)
        // Only the pre-reload Ready survived; no stale Ended arrived.
        val readies = events.filterIsInstance<RuntimeEvent.Ready>()
        assertEquals(1, readies.size)
        assertEquals(emptyList(), events.filterIsInstance<RuntimeEvent.Ended>())
        // Current session still works.
        engine.emitPrepared(30_000)
        assertEquals(2, events.filterIsInstance<RuntimeEvent.Ready>().size)
        assertEquals(second, events.filterIsInstance<RuntimeEvent.Ready>().last().generation)
    }

    @Test
    fun pollDerivesPlayingPausedAndProgressFromEngineQueriesOnly() {
        val generation = loadAndPrepare()
        engine.emitBuffering(true)
        assertEquals("Buffering", eventKinds().last())
        engine.emitBuffering(false)
        // Not playing yet: poll reports Paused once, then Progress every tick.
        scheduler.tickAll()
        assertEquals(listOf("Paused", "Progress"), eventKinds().takeLast(2))
        engine.isPlaying = true
        scheduler.tickAll()
        assertEquals(listOf("Playing", "Progress"), eventKinds().takeLast(2))
        engine.positionMs = 1_500
        engine.bufferedMs = 30_000
        scheduler.tickAll()
        val progress = events.filterIsInstance<RuntimeEvent.Progress>().last()
        assertTrue(progress.generation === generation)
        assertEquals(1_500L, progress.positionMs)
        assertEquals(30_000L, progress.bufferedPositionMs)
    }

    @Test
    fun engineErrorSurfacesAsFailedDecode() {
        val generation = loadAndPrepare()
        engine.emitError()
        val failed = events.filterIsInstance<RuntimeEvent.Failed>().single()
        assertTrue(failed.generation === generation)
        assertEquals(PlayerFailure.Decode, failed.failure)
    }

    @Test
    fun commandsCarryingForeignGenerationsAreIgnored() {
        loadAndPrepare()
        engine.log.clear()
        runtime.execute(RuntimeCommand.Play(PlaybackGeneration()))
        runtime.execute(RuntimeCommand.Seek(PlaybackGeneration(), 1_000))
        assertEquals(emptyList(), engine.log)
        runtime.execute(RuntimeCommand.Play(events.filterIsInstance<RuntimeEvent.Ready>().single().generation))
        assertTrue(engine.log.contains("start"))
    }

    @Test
    fun stopResetsTheEngineAndClearsPolling() {
        val generation = loadAndPrepare()
        runtime.execute(RuntimeCommand.Stop(generation))
        assertTrue(engine.log.contains("reset"))
        val before = events.size
        scheduler.tickAll()
        assertEquals(before, events.size) // no generation left: poll is a no-op
    }

    @Test
    fun surfaceAttachIsForwardedImmediatelyWithTheRegisteredObject() {
        // Mid-load attach: the command arrives BEFORE preparation completes.
        runtime.bind { events += it }
        val generation = PlaybackGeneration()
        runtime.execute(RuntimeCommand.Load(generation, PlayableResource("res-1", "https://stream.test/a.m3u8")))
        assertTrue(!events.any { it is RuntimeEvent.Ready }) // not prepared yet

        val midLoadSurface = Any()
        val midLoadHandle = registry.register(midLoadSurface)
        engine.surfaces.clear()
        runtime.execute(RuntimeCommand.AttachSurface(midLoadHandle))
        assertTrue(engine.log.contains("setSurface"), engine.log.toString())
        assertEquals(listOf<Any?>(midLoadSurface), engine.surfaces) // the registered object itself

        engine.emitPrepared(60_000)
        // No duplicate surface call on preparation.
        assertEquals(1, engine.surfaces.size)

        // Re-attach replaces the surface with the newly registered object.
        val replacement = Any()
        engine.surfaces.clear()
        runtime.execute(RuntimeCommand.AttachSurface(registry.register(replacement)))
        assertEquals(listOf<Any?>(replacement), engine.surfaces)

        // Detach forwards an explicit null.
        engine.surfaces.clear()
        runtime.execute(RuntimeCommand.DetachSurface(midLoadHandle))
        assertEquals(listOf<Any?>(null), engine.surfaces)
    }

    @Test
    fun surfaceAttachedBeforeLoadIsAppliedDuringLoad() {
        val surface = Any()
        val handle = registry.register(surface)
        runtime.bind { events += it }
        engine.surfaces.clear()
        // Attach arrives before any load: remembered as pending…
        runtime.execute(RuntimeCommand.AttachSurface(handle))
        // …and the load path RE-applies the pending surface before prepareAsync.
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("res-1", "https://stream.test/a.m3u8")))
        // attach + load replay, both carrying the exact registered object.
        assertEquals(listOf<Any?>(surface, surface), engine.surfaces)
    }

    @Test
    fun releaseIsIdempotentStopsPollingAndDetachesSink() {
        loadAndPrepare()
        runtime.execute(RuntimeCommand.Release)
        assertTrue(engine.log.contains("release"))
        val commandsAfterRelease = engine.log.size
        runtime.execute(RuntimeCommand.Release)
        runtime.execute(RuntimeCommand.Play(PlaybackGeneration()))
        assertEquals(commandsAfterRelease, engine.log.size) // no engine calls after release
        assertTrue(scheduler.closed)
        val before = events.size
        engine.emitPrepared(1_000) // listener detached
        assertEquals(before, events.size)
        // Poll is a no-op after release.
        scheduler.tickAll()
        assertEquals(before, events.size)
    }

    @Test
    fun unsupportedTrackSelectionThrowsInsteadOfBeingSwallowed() {
        loadAndPrepare()
        assertFailsWith<UnsupportedPlayerCapability> {
            runtime.execute(RuntimeCommand.SelectTrack(PlaybackGeneration(), TrackKind.Audio, "a1"))
        }
    }

    @Test
    fun speedIsForwardedForTheCurrentGeneration() {
        val generation = loadAndPrepare()
        engine.log.clear()
        runtime.execute(RuntimeCommand.Speed(generation, 1.5f))
        assertTrue(engine.log.contains("speed(1.5)"))
    }

    @Test
    fun pollWithoutGenerationNeverTouchesTheSink() {
        runtime.bind { events += it }
        scheduler.tickAll()
        assertEquals(emptyList(), events)
    }
}
