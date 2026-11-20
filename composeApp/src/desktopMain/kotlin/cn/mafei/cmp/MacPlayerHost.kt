package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import cn.mafei.cmp.player.runtime.createPlayerRuntime
import cn.mafei.cmp.player.runtime.usesSdkMacVideoSurface
import cn.mafei.cmp.player.runtime_macos.MacSurfaceRegistry
import cn.mafei.cmp.player.runtime_macos.MacVideoSurface
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Canvas
import javax.swing.JPanel

/** JAWT/CALayer owns only native video output; all chrome remains in Compose. */
internal class MacPlayerHost {
    private val registry = MacSurfaceRegistry()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    fun createRuntime() = createPlayerRuntime(registry)

    @Composable
    fun Surface(controller: PlayerController, modifier: Modifier) {
        if (usesSdkMacVideoSurface) {
            SdkSurface(controller, modifier)
            return
        }
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

    @Composable
    private fun SdkSurface(controller: PlayerController, modifier: Modifier) {
        val panel = remember(controller) {
            JPanel(BorderLayout()).apply { isFocusable = false; background = java.awt.Color.BLACK }
        }
        DisposableEffect(controller, panel) {
            val handle = registry.register(panel)
            val attach = scope.launch { controller.attachSurface(handle) }
            onDispose {
                scope.launch {
                    attach.cancelAndJoin()
                    try {
                        controller.detachSurface(handle)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IllegalStateException) {
                        // The owning playback session can retire before its Swing panel.
                    } finally {
                        registry.unregister(handle)
                    }
                }
            }
        }
        SwingPanel(factory = { panel }, modifier = modifier, background = androidx.compose.ui.graphics.Color.Black)
    }
}
