package cn.mafei.cmp.player.runtime_ohos

import cn.mafei.cmp.player.api.*
import cn.mafei.cmp.player.runtime_ohos.OhosPlayerRuntime.Companion.ITEM_READY
import cn.mafei.cmp.player.runtime_ohos.OhosPlayerRuntime.Companion.TIME_PLAYING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Translation-layer tests over a fake bridge; they execute on an OHOS device
 * or emulator (Pending device window on current CI).
 */
class OhosPlayerRuntimeTest {

    private class FakeOhosBridge : OhosAvPlayerBridge {
        private val players = HashMap<Long, PlayerModel>()
        var nextHandle = 1L

        class PlayerModel {
            var url: String? = null
            var itemStatus = 0
            var timeControl = 0
            var positionMs = 0L
            var durationMs = 0L
            var surfaceId: String? = null
            var stopped = false
            var released = false
        }

        val current: PlayerModel get() = players.getValue(1L)

        var rejectHeaders = false
        override fun create(): Long = nextHandle.also { players[it] = PlayerModel() }
        override fun loadUrl(handle: Long, url: String, headers: Map<String, String>) {
            if (rejectHeaders && headers.isNotEmpty()) throw UnsupportedOperationException("headers unsupported")
            players.getValue(handle).url = url
        }
        override fun play(handle: Long) { players.getValue(handle).timeControl = TIME_PLAYING }
        override fun pause(handle: Long) { players.getValue(handle).timeControl = 0 }
        override fun stop(handle: Long) { players.getValue(handle).stopped = true }
        override fun seek(handle: Long, positionMs: Long) { players.getValue(handle).positionMs = positionMs }
        override fun setSpeed(handle: Long, speed: Float) { }
        override fun attachSurface(handle: Long, surfaceId: String?) { players.getValue(handle).surfaceId = surfaceId }
        override fun detachSurface(handle: Long) { players.getValue(handle).surfaceId = null }
        override fun positionMs(handle: Long): Long = players.getValue(handle).positionMs
        override fun durationMs(handle: Long): Long = players.getValue(handle).durationMs
        override fun bufferedMs(handle: Long): Long = 0
        override fun timeControl(handle: Long): Int = players.getValue(handle).timeControl
        override fun itemStatus(handle: Long): Int = players.getValue(handle).itemStatus
        override fun errorKind(handle: Long): Int = 0
        override fun releasePlayer(handle: Long) { players.getValue(handle).released = true }
    }

    @Test
    fun loadPreparesEmitsReadyAndAttachesRegisteredSurface() {
        val bridge = FakeOhosBridge()
        val surfaces = OhosSurfaceRegistry()
        val events = mutableListOf<RuntimeEvent>()
        val runtime = OhosPlayerRuntime(
            capabilities = PlayerCapabilities(),
            bridge = bridge,
            surfaces = surfaces,
            pollScheduler = object : OhosPollScheduler {
                override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable = AutoCloseable { }
            },
        )
        runtime.bind { events += it }
        val surfaceHandle = surfaces.register("surface-0001")
        val generation = PlaybackGeneration()
        runtime.execute(RuntimeCommand.AttachSurface(surfaceHandle))
        runtime.execute(RuntimeCommand.Load(generation, PlayableResource("res-1", "https://stream.test/a.mp4")))
        bridge.current.itemStatus = ITEM_READY
        bridge.current.durationMs = 60_000
        runtime.poll()
        assertEquals("surface-0001", bridge.current.surfaceId)
        assertEquals("https://stream.test/a.mp4", bridge.current.url)
        val ready = events.filterIsInstance<RuntimeEvent.Ready>().single()
        assertTrue(ready.generation === generation)
    }

    @Test
    fun unknownDurationMediaBecomesReadyWithNullDuration() {
        val bridge = FakeOhosBridge()
        val events = mutableListOf<RuntimeEvent>()
        val runtime = OhosPlayerRuntime(
            capabilities = PlayerCapabilities(),
            bridge = bridge,
            pollScheduler = object : OhosPollScheduler {
                override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable = AutoCloseable { }
            },
        )
        runtime.bind { events += it }
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("res-live", "https://stream.test/live.m3u8")))
        bridge.current.itemStatus = ITEM_READY
        bridge.current.durationMs = 0
        runtime.poll()
        // Live/unknown-duration media must not stay in Loading forever.
        assertNull(events.filterIsInstance<RuntimeEvent.Ready>().single().durationMs)
    }

    @Test
    fun headerIncapableEngineFailsAsUnsupportedMedia() {
        val bridge = FakeOhosBridge()
        val events = mutableListOf<RuntimeEvent>()
        val runtime = OhosPlayerRuntime(
            capabilities = PlayerCapabilities(),
            bridge = bridge,
            pollScheduler = object : OhosPollScheduler {
                override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable = AutoCloseable { }
            },
        )
        runtime.bind { events += it }
        bridge.rejectHeaders = true
        runtime.execute(
            RuntimeCommand.Load(
                PlaybackGeneration(),
                PlayableResource("res-1", "https://stream.test/a.m3u8", mapOf("Authorization" to "tok")),
            ),
        )
        assertEquals(PlayerFailure.UnsupportedMedia, events.filterIsInstance<RuntimeEvent.Failed>().single().failure)
        assertEquals(null, bridge.current.url)
    }

    @Test
    fun releaseClosesThePollTicket() {
        var closed = false
        val runtime2 = OhosPlayerRuntime(
            capabilities = PlayerCapabilities(),
            bridge = FakeOhosBridge(),
            pollScheduler = object : OhosPollScheduler {
                override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable = AutoCloseable { closed = true }
            },
        )
        runtime2.bind { }
        runtime2.execute(RuntimeCommand.Release)
        assertTrue(closed)
    }
}
