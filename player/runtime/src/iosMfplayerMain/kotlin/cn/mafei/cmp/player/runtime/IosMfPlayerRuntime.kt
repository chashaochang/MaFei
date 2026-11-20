package cn.mafei.cmp.player.runtime

import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import cn.mafei.cmp.player.api.RuntimeEvent
import cn.mafei.cmp.player.runtime_ios.IosAvPlayerBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mfplayer.cmp.CommandResult
import org.mfplayer.cmp.PlayerEngine
import org.mfplayer.cmp.PlayerOptions
import org.mfplayer.cmp.PlayerPhase
import org.mfplayer.cmp.PlayerSource
import org.mfplayer.cmp.ios.IosPlayerAdapter

class IosMfPlayerSurfaces {
    class Surface internal constructor(val handle: PlayerSurfaceHandle) {
        internal val native = MutableStateFlow<IosPlayerAdapter?>(null)
        val adapter: StateFlow<IosPlayerAdapter?> get() = native
    }

    private val surfaces = mutableMapOf<PlayerSurfaceHandle, Surface>()

    fun register(): Surface = Surface(PlayerSurfaceHandle()).also { surfaces[it.handle] = it }

    fun unregister(surface: Surface) {
        surfaces.remove(surface.handle)
        surface.native.value = null
    }

    internal fun attach(handle: PlayerSurfaceHandle, adapter: IosPlayerAdapter) {
        surfaces[handle]?.native?.value = adapter
    }

    internal fun detach(handle: PlayerSurfaceHandle, adapter: IosPlayerAdapter) {
        surfaces[handle]?.native?.compareAndSet(adapter, null)
    }
}

fun createPlayerRuntime(bridge: IosAvPlayerBridge, surfaces: IosMfPlayerSurfaces): PlayerRuntime {
    var engine: IosMfPlayerEngine? = null
    val runtime = MfPlayerRuntime(
        capabilities = PlayerCapabilities(contentMode = true),
        createEngine = { IosMfPlayerEngine(IosPlayerAdapter(), bridge).also { engine = it } },
        nativeExternalSubtitles = false, // MaFei's authenticated sidecar overlay owns these.
        surface = { player, handle, attach ->
            val adapter = (player as IosMfPlayerEngine).adapter
            if (attach) surfaces.attach(handle, adapter) else surfaces.detach(handle, adapter)
        },
    )
    return object : PlayerRuntime by runtime {
        override fun bind(eventSink: (RuntimeEvent) -> Unit) = runtime.bind { event ->
            val native = engine
            if (event is RuntimeEvent.EngineChanged && native?.backgroundAudio == true) {
                eventSink(RuntimeEvent.BackgroundAudio(event.generation, true))
            }
            native?.update(event)
            eventSink(event)
        }
    }
}

private class IosMfPlayerEngine(
    val adapter: IosPlayerAdapter,
    private val bridge: IosAvPlayerBridge,
) : PlayerEngine by adapter {
    private var token = 0L
    private var audioOnly = false
    val backgroundAudio: Boolean get() = audioOnly && token != 0L

    override fun load(source: PlayerSource, options: PlayerOptions): CommandResult {
        clear()
        val result = adapter.load(source, options)
        if (result is CommandResult.Accepted) {
            token = bridge.beginSdkPlayback(options.audioOnly)
            audioOnly = options.audioOnly
        }
        return result
    }

    override fun stop(): CommandResult = adapter.stop().also { clear() }

    override fun release() {
        try { adapter.release() } finally { clear() }
    }

    fun update(event: RuntimeEvent) {
        if (event is RuntimeEvent.Failed) clear()
        if (token == 0L || event !is RuntimeEvent.Progress && event !is RuntimeEvent.Playing &&
            event !is RuntimeEvent.Paused && event !is RuntimeEvent.Ready && event !is RuntimeEvent.Ended) return
        val state = adapter.snapshot.value
        bridge.updateSdkPlayback(token, state.positionMs, state.durationMs ?: 0L,
            if (state.phase == PlayerPhase.PLAYING) state.playbackSpeed.toFloat() else 0f)
    }

    private fun clear() {
        if (token != 0L) bridge.endSdkPlayback(token)
        token = 0L
        audioOnly = false
    }
}
