package cn.mafei.cmp.player.runtime_ohos

import cn.mafei.cmp.player.api.*
import cn.mafei.cmp.player.runtime_ohos.OhosPlayerRuntime.Companion.ITEM_READY
import cn.mafei.cmp.player.runtime_ohos.OhosPlayerRuntime.Companion.TIME_PLAYING
import kotlin.test.Test
import kotlin.test.assertEquals
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

        override fun create(): Long = nextHandle.also { players[it] = PlayerModel() }
        override fun loadUrl(handle: Long, url: String) { players.getValue(handle).url = url }
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
}
