package cn.mafei.cmp.player.runtime_macos

import cn.mafei.cmp.player.api.*
import cn.mafei.cmp.player.runtime_macos.MacOsAvPlayerRuntime.Companion.ERROR_DECODE
import cn.mafei.cmp.player.runtime_macos.MacOsAvPlayerRuntime.Companion.ERROR_NETWORK
import cn.mafei.cmp.player.runtime_macos.MacOsAvPlayerRuntime.Companion.ITEM_FAILED
import cn.mafei.cmp.player.runtime_macos.MacOsAvPlayerRuntime.Companion.ITEM_READY
import cn.mafei.cmp.player.runtime_macos.MacOsAvPlayerRuntime.Companion.TIME_BUFFERING
import cn.mafei.cmp.player.runtime_macos.MacOsAvPlayerRuntime.Companion.TIME_PAUSED
import cn.mafei.cmp.player.runtime_macos.MacOsAvPlayerRuntime.Companion.TIME_PLAYING
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

private class FakeAvPlayerBridge : AvPlayerBridge {
    val log = mutableListOf<String>()
    private val handles = AtomicInteger(0)
    val players = HashMap<Long, PlayerModel>()
    var nextHandle = 1L

    class PlayerModel {
        var url: String? = null
        var itemStatus = 0
        var timeControl = 0
        var errorKind = 0
        var positionMs = 0L
        var durationMs = 0L
        var bufferedMs = 0L
        var speed = 1.0f
        var layerPointer = 0L
        var stopped = false
        var released = false
    }

    val current: PlayerModel get() = players.getValue(1L)
    fun reset() { handles.set(0) }

    override fun create(): Long = nextHandle.also { players[it] = PlayerModel() }.also { nextHandle += 1 }
    var rejectHeaders = false
    val lastHeaders = mutableListOf<Map<String, String>>()
    override fun loadUrl(handle: Long, url: String, headers: Map<String, String>) {
        if (rejectHeaders && headers.isNotEmpty()) {
            throw UnsupportedOperationException("headers unsupported")
        }
        lastHeaders += headers
        players.getValue(handle).url = url
    }
    override fun play(handle: Long) { players.getValue(handle).timeControl = TIME_PLAYING }
    override fun pause(handle: Long) { players.getValue(handle).timeControl = TIME_PAUSED }
    override fun stop(handle: Long) { players.getValue(handle).stopped = true; players.getValue(handle).timeControl = TIME_PAUSED }
    override fun seek(handle: Long, positionMs: Long) { log += "seek($positionMs)" }
    override fun setSpeed(handle: Long, speed: Float) { players.getValue(handle).speed = speed }
    override fun attachLayer(handle: Long, layerPointer: Long) { players.getValue(handle).layerPointer = layerPointer }
    override fun detachLayer(handle: Long, layerPointer: Long) { players.getValue(handle).layerPointer = 0 }
    override fun positionMs(handle: Long): Long = players.getValue(handle).positionMs
    override fun durationMs(handle: Long): Long = players.getValue(handle).durationMs
    override fun bufferedMs(handle: Long): Long = players.getValue(handle).bufferedMs
    override fun timeControl(handle: Long): Int = players.getValue(handle).timeControl
    override fun itemStatus(handle: Long): Int = players.getValue(handle).itemStatus
    override fun errorKind(handle: Long): Int = players.getValue(handle).errorKind
    override fun releasePlayer(handle: Long) { players.getValue(handle).released = true }
}

class MacOsAvPlayerRuntimeTest {

    private val bridge = FakeAvPlayerBridge()
    private val surfaces = MacSurfaceRegistry()
    private val events = mutableListOf<RuntimeEvent>()
    private val manualScheduler = object : MacPollScheduler {
        val ticks = mutableListOf<() -> Unit>()
        override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable {
            ticks += tick
            return AutoCloseable { }
        }
    }
    private val runtime = MacOsAvPlayerRuntime(
        capabilities = PlayerCapabilities(trackSelection = false),
        bridge = bridge,
        surfaces = surfaces,
        pollScheduler = manualScheduler,
        dispatcher = Runnable::run,
    )

    private fun loadAndPrepare(url: String = "https://stream.test/a.m3u8"): PlaybackGeneration {
        runtime.bind { events += it }
        val generation = PlaybackGeneration()
        runtime.execute(RuntimeCommand.Load(generation, PlayableResource("res-1", url)))
        bridge.current.itemStatus = ITEM_READY
        bridge.current.durationMs = 60_000
        runtime.poll()
        return generation
    }

    private fun eventKinds() = events.map { it::class.simpleName }

    @Test
    fun loadCreatesPlayerThenReadyCarriesDuration() {
        val generation = loadAndPrepare()
        assertEquals("https://stream.test/a.m3u8", bridge.current.url)
        val ready = events.filterIsInstance<RuntimeEvent.Ready>().single()
        assertTrue(ready.generation === generation)
        assertEquals(60_000L, ready.durationMs)
    }

    @Test
    fun loadStopsThePreviousResourceBeforeReplacing() {
        loadAndPrepare()
        val firstModel = bridge.current
        assertTrue(firstModel.stopped)
    }

    @Test
    fun pollDerivesPlayingBufferingPausedAndProgressFromQueriesOnly() {
        loadAndPrepare()
        bridge.current.timeControl = TIME_PLAYING
        runtime.poll()
        assertTrue(eventKinds().takeLast(2) == listOf("Playing", "Progress"), eventKinds().toString())

        bridge.current.timeControl = TIME_BUFFERING
        runtime.poll()
        assertEquals(listOf("Buffering", "Progress"), eventKinds().takeLast(2))

        bridge.current.timeControl = TIME_PAUSED
        bridge.current.positionMs = 1_500
        bridge.current.bufferedMs = 30_000
        runtime.poll()
        assertEquals(listOf("Paused", "Progress"), eventKinds().takeLast(2))
        val progress = events.filterIsInstance<RuntimeEvent.Progress>().last()
        assertEquals(1_500L, progress.positionMs)
        assertEquals(30_000L, progress.bufferedPositionMs)
    }

    @Test
    fun reachingTheEndWhilePausedEmitsEndedOnce() {
        loadAndPrepare()
        bridge.current.durationMs = 60_000
        bridge.current.positionMs = 60_000
        runtime.poll()
        assertEquals("Ended", eventKinds().last())
        val countAfterFirst = events.size
        runtime.poll()
        assertEquals(countAfterFirst, events.size) // terminal: no repeat events
    }

    @Test
    fun failedItemMapsNetworkAndDecodeErrors() {
        loadAndPrepare()
        bridge.current.itemStatus = ITEM_FAILED
        bridge.current.errorKind = ERROR_NETWORK
        runtime.poll()
        assertEquals(PlayerFailure.Network, events.filterIsInstance<RuntimeEvent.Failed>().single().failure)

        val events2 = mutableListOf<RuntimeEvent>()
        val runtime2 = MacOsAvPlayerRuntime(
            capabilities = PlayerCapabilities(trackSelection = false),
            bridge = bridge,
            surfaces = MacSurfaceRegistry(),
            dispatcher = Runnable::run,
        )
        runtime2.bind { events2 += it }
        val gen2 = PlaybackGeneration()
        runtime2.execute(RuntimeCommand.Load(gen2, PlayableResource("res-2", "https://stream.test/b.mp3")))
        val secondPlayer = bridge.nextHandle - 1 // handle created for runtime2
        bridge.players.getValue(secondPlayer).itemStatus = ITEM_FAILED
        bridge.players.getValue(secondPlayer).errorKind = ERROR_DECODE
        runtime2.poll()
        assertEquals(PlayerFailure.Decode, events2.filterIsInstance<RuntimeEvent.Failed>().single().failure)
    }

    @Test
    fun foreignGenerationCommandsAreIgnored() {
        val generation = loadAndPrepare()
        bridge.log.clear()
        runtime.execute(RuntimeCommand.Play(PlaybackGeneration()))
        runtime.execute(RuntimeCommand.Seek(PlaybackGeneration(), 1_000))
        assertEquals(emptyList(), bridge.log)
        runtime.execute(RuntimeCommand.Seek(generation, 5_000))
        assertTrue(bridge.log.contains("seek(5000)"))
    }

    @Test
    fun surfaceAttachUsesRegistryAndDetachClears() {
        loadAndPrepare()
        val layer = surfaces.register(4242L)
        runtime.execute(RuntimeCommand.AttachSurface(layer))
        assertEquals(4242L, bridge.current.layerPointer)
        runtime.execute(RuntimeCommand.DetachSurface(layer))
        assertEquals(0L, bridge.current.layerPointer)
    }

    @Test
    fun unsupportedTrackSelectionThrows() {
        loadAndPrepare()
        assertFailsWith<UnsupportedPlayerCapability> {
            runtime.execute(RuntimeCommand.SelectTrack(PlaybackGeneration(), TrackKind.Audio, "a1"))
        }
    }

    @Test
    fun releaseIsIdempotentStopsTheEngineAndDetachesSink() {
        loadAndPrepare()
        runtime.execute(RuntimeCommand.Release)
        assertTrue(bridge.current.released)
        val eventsBefore = events.size
        runtime.execute(RuntimeCommand.Release)
        runtime.execute(RuntimeCommand.Play(PlaybackGeneration()))
        assertEquals(eventsBefore, events.size)
        runtime.poll() // no-op after release
        assertEquals(eventsBefore, events.size)
    }

    @Test
    fun unknownDurationMediaStillBecomesReadyWithNullDuration() {
        runtime.bind { events += it }
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("res-live", "https://stream.test/live.m3u8")))
        bridge.current.itemStatus = ITEM_READY
        bridge.current.durationMs = 0 // live / unknown duration
        runtime.poll()
        // itemStatus READY alone means prepared: live media must NOT stay in
        // Loading forever, and the API allows Ready(durationMs = null).
        val ready = events.filterIsInstance<RuntimeEvent.Ready>().single()
        assertNull(ready.durationMs)
        // Normal paused/progress reporting continues afterwards.
        assertEquals("Paused", eventKinds().takeLast(2).first())
        assertEquals("Progress", eventKinds().last())
    }

    @Test
    fun headersAreForwardedToTheBridge() {
        runtime.bind { events += it }
        runtime.execute(
            RuntimeCommand.Load(
                PlaybackGeneration(),
                PlayableResource("res-1", "https://stream.test/a.m3u8", mapOf("Authorization" to "tok", "Cookie" to "Trim-MC-token=tok")),
            ),
        )
        assertEquals(
            listOf(mapOf("Authorization" to "tok", "Cookie" to "Trim-MC-token=tok")),
            bridge.lastHeaders,
        )
    }

    @Test
    fun headerIncapableEngineFailsLoudlyAsUnsupportedMedia() {
        bridge.rejectHeaders = true
        runtime.bind { events += it }
        runtime.execute(
            RuntimeCommand.Load(
                PlaybackGeneration(),
                PlayableResource("res-1", "https://stream.test/a.m3u8", mapOf("Authorization" to "tok")),
            ),
        )
        val failed = events.filterIsInstance<RuntimeEvent.Failed>().single()
        assertEquals(PlayerFailure.UnsupportedMedia, failed.failure)
        assertEquals(null, bridge.current.url) // nothing was loaded behind the refusal
    }

    @Test
    fun detachUnbindsTheExactLayerOnTheEngine() {
        loadAndPrepare()
        val layer = surfaces.register(4242L)
        runtime.execute(RuntimeCommand.AttachSurface(layer))
        bridge.current.layerPointer = 4242L
        runtime.execute(RuntimeCommand.DetachSurface(layer))
        assertEquals(0L, bridge.current.layerPointer) // engine unbinds the layer
    }

    @Test
    fun doubleReleaseWithTheRealDispatcherDoesNotThrow() {
        // Production dispatcher (real executor): the pre-fix path enqueued
        // Release and a SECOND release() threw RejectedExecutionException on
        // the shut-down queue instead of being a no-op.
        val runtime2 = MacOsAvPlayerRuntime(
            capabilities = PlayerCapabilities(trackSelection = false),
            bridge = bridge,
            surfaces = MacSurfaceRegistry(),
        ) // default dispatcher + default poll scheduler (both really shut down)
        runtime2.bind { }
        runtime2.execute(RuntimeCommand.Release)
        runtime2.execute(RuntimeCommand.Release) // must be a no-op, never throw
        runtime2.execute(RuntimeCommand.Play(PlaybackGeneration())) // no enqueue either
    }

    @Test
    fun noEventsBeforeLoad() {
        runtime.bind { events += it }
        runtime.poll()
        assertEquals(emptyList(), events)
    }
}
