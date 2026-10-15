package cn.mafei.cmp.player.runtime_android

import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import java.util.concurrent.atomic.AtomicInteger



/**
 * Android video surface registry. `PlayerSurfaceHandle` is a deliberately
 * opaque identity (frozen API): platform view types stay OUT of commonMain by
 * keeping the `android.view.Surface`/`SurfaceHolder` mapping in this runtime
 * registry. The UI layer registers its surface and passes the returned handle
 * to the controller.
 */
class AndroidSurfaceRegistry {
    private val nextId = AtomicInteger(1)
    private val surfaces = HashMap<PlayerSurfaceHandle, Any>()

    /** Registers a `Surface` or `SurfaceHolder`; null detaches nothing, it is rejected. */
    fun register(surface: Any): PlayerSurfaceHandle {
        val handle = PlayerSurfaceHandle()
        synchronized(surfaces) { surfaces[handle] = surface }
        return handle
    }

    fun surfaceOf(handle: PlayerSurfaceHandle): Any? = synchronized(surfaces) { surfaces[handle] }

    fun unregister(handle: PlayerSurfaceHandle): Boolean = synchronized(surfaces) { surfaces.remove(handle) != null }
}
