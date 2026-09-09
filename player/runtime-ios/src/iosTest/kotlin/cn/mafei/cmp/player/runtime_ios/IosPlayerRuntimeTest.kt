package cn.mafei.cmp.player.runtime_ios

import cn.mafei.cmp.player.api.*
import cn.mafei.cmp.player.runtime_ios.IosPlayerRuntime.Companion.ERROR_NETWORK
import cn.mafei.cmp.player.runtime_ios.IosPlayerRuntime.Companion.ITEM_FAILED
import cn.mafei.cmp.player.runtime_ios.IosPlayerRuntime.Companion.ITEM_READY
import cn.mafei.cmp.player.runtime_ios.IosPlayerRuntime.Companion.TIME_PAUSED
import cn.mafei.cmp.player.runtime_ios.IosPlayerRuntime.Companion.TIME_PLAYING
import kotlin.test.*

/**
 * Translation-layer tests over a fake bridge. These run wherever the
 * ios target's tests can execute (iOS simulator/host) — on the current CI
 * they are written and kept green as documentation of the contract.
 */
class IosPlayerRuntimeTest {

    private class FakeIosBridge : IosAvPlayerBridge {
        val log = mutableListOf<String>()
        private val players = HashMap<Long, PlayerModel>()
        var nextHandle = 1L

        class PlayerModel {
            var url: String? = null
            var itemStatus = 0
            var timeControl = 0
            var errorKind = 0
            var positionMs = 0L
            var durationMs = 0L
            var bufferedMs = 0L
            var layer: Any? = null
            var stopped = false
            var released = false
        }

        val current: PlayerModel get() = players.getValue(1L)

        override fun create(): Long = nextHandle.also { players[it] = PlayerModel() }
        var rejectHeaders = false
        override fun loadUrl(handle: Long, url: String, headers: Map<String, String>) {
            if (rejectHeaders && headers.isNotEmpty()) throw UnsupportedOperationException("headers unsupported")
            players.getValue(handle).url = url
        }
        override fun play(handle: Long) { players.getValue(handle).timeControl = TIME_PLAYING }
        override fun pause(handle: Long) { players.getValue(handle).timeControl = TIME_PAUSED }
        override fun stop(handle: Long) { players.getValue(handle).stopped = true }
        override fun seek(handle: Long, positionMs: Long) { log += "seek($positionMs)" }
        override fun setSpeed(handle: Long, speed: Float) { log += "speed($speed)" }
        override fun attachLayer(handle: Long, layer: Any?) { players.getValue(handle).layer = layer }
        override fun detachLayer(handle: Long) { players.getValue(handle).layer = null }
        override fun positionMs(handle: Long): Long = players.getValue(handle).positionMs
        override fun durationMs(handle: Long): Long = players.getValue(handle).durationMs
        override fun bufferedMs(handle: Long): Long = players.getValue(handle).bufferedMs
        override fun timeControl(handle: Long): Int = players.getValue(handle).timeControl
        override fun itemStatus(handle: Long): Int = players.getValue(handle).itemStatus
        override fun errorKind(handle: Long): Int = players.getValue(handle).errorKind
        override fun releasePlayer(handle: Long) { players.getValue(handle).released = true }
    }

    private val bridge = FakeIosBridge()
    private val events = mutableListOf<RuntimeEvent>()
    private val runtime = IosPlayerRuntime(
        capabilities = PlayerCapabilities(trackSelection = false),
        bridge = bridge,
        surfaces = IosSurfaceRegistry(),
        pollScheduler = object : IosPollScheduler {
            override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable = AutoCloseable { }
        },
    )

    @Test
    fun loadPreparesAndEmitsReadyWithGeneration() {
        runtime.bind { events += it }
        val generation = PlaybackGeneration()
        runtime.execute(RuntimeCommand.Load(generation, PlayableResource("res-1", "https://stream.test/a.m3u8")))
        bridge.current.itemStatus = ITEM_READY
        bridge.current.durationMs = 60_000
        runtime.poll()
        val ready = events.filterIsInstance<RuntimeEvent.Ready>().single()
        assertTrue(ready.generation === generation)
        assertEquals(60_000L, ready.durationMs)
    }

    @Test
    fun failedItemMapsErrorsAndTerminalStateIsSticky() {
        runtime.bind { events += it }
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("res-1", "https://stream.test/a.mp4")))
        bridge.current.itemStatus = ITEM_FAILED
        bridge.current.errorKind = ERROR_NETWORK
        runtime.poll()
        assertEquals(PlayerFailure.Network, events.filterIsInstance<RuntimeEvent.Failed>().single().failure)
        val before = events.size
        runtime.poll()
        assertEquals(before, events.size)
    }

    @Test
    fun unknownDurationMediaBecomesReadyWithNullDuration() {
        runtime.bind { events += it }
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("res-live", "https://stream.test/live.m3u8")))
        bridge.current.itemStatus = ITEM_READY
        bridge.current.durationMs = 0
        runtime.poll()
        // Live/unknown-duration media must not stay in Loading forever.
        val ready = events.filterIsInstance<RuntimeEvent.Ready>().single()
        assertNull(ready.durationMs)
    }

    @Test
    fun headerIncapableEngineFailsAsUnsupportedMedia() {
        bridge.rejectHeaders = true
        runtime.bind { events += it }
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
        val runtime2 = IosPlayerRuntime(
            capabilities = PlayerCapabilities(),
            bridge = bridge,
            pollScheduler = object : IosPollScheduler {
                override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable = AutoCloseable { closed = true }
            },
        )
        runtime2.bind { }
        runtime2.execute(RuntimeCommand.Release)
        assertTrue(closed) // the timer is released, it cannot retain the runtime
    }

    @Test
    fun releaseDetachesSinkAndStopsPollEffects() {
        runtime.bind { events += it }
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("res-1", "https://stream.test/a.mp4")))
        runtime.execute(RuntimeCommand.Release)
        assertTrue(bridge.current.released)
        val before = events.size
        runtime.poll()
        assertEquals(before, events.size)
    }
}
