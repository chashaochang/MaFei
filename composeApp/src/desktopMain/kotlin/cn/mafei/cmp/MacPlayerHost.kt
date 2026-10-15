package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import cn.mafei.cmp.player.runtime_macos.*
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Canvas
import javax.swing.JPanel

/** JAWT/CALayer owns only native video output; all chrome remains in Compose. */
internal class MacPlayerHost {
    private val registry = MacSurfaceRegistry()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    fun runtime() = createMacOsPlayerRuntime(registry)

    @Composable
    fun Surface(controller: PlayerController, modifier: Modifier) {
        val canvas = remember(controller) {
            object : Canvas() {
                var layer = 0L
                var handle: PlayerSurfaceHandle? = null
                override fun addNotify() {
                    super.addNotify()
                    isFocusable = false
                    background = java.awt.Color.BLACK
                    layer = MacVideoSurface.create(this)
                    if (layer != 0L) {
                        val registered = registry.register(layer); handle = registered
                        scope.launch { controller.attachSurface(registered) }
                    }
                }
                override fun removeNotify() {
                    handle?.let { registered -> scope.launch { controller.detachSurface(registered) }; registry.unregister(registered) }
                    handle = null
                    MacVideoSurface.destroy(layer); layer = 0
                    super.removeNotify()
                }
            }
        }
        SwingPanel(factory = { JPanel(BorderLayout()).apply { isFocusable = false; background = java.awt.Color.BLACK; add(canvas, BorderLayout.CENTER) } },
            modifier = modifier, background = androidx.compose.ui.graphics.Color.Black)
    }
}
