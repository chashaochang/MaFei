package cn.mafei.cmp

import android.content.Context
import android.graphics.Color
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import cn.mafei.cmp.player.runtime_android.AndroidSurfaceRegistry
import cn.mafei.cmp.player.runtime_android.createAndroidPlayerRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-owned Android player host.
 *
 * The runtime and the Compose [SurfaceView] must use the exact same registry.
 * Keeping this host process-scoped also prevents an Activity recreation from
 * replacing the registry underneath the still-live [ApplicationRuntime].
 */
internal class AndroidPlayerHost private constructor(context: Context) {
    private val applicationContext = context.applicationContext
    private val surfaces = AndroidSurfaceRegistry()
    private val surfaceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun createRuntime(): PlayerRuntime = createAndroidPlayerRuntime(applicationContext, surfaces)

    @Composable
    fun Surface(
        controller: PlayerController,
        modifier: Modifier = Modifier,
    ) {
        val latestController = rememberUpdatedState(controller)
        val binding = remember {
            AndroidSurfaceBinding(
                registry = surfaces,
                scope = surfaceScope,
                controller = { latestController.value },
            )
        }
        DisposableEffect(binding) {
            onDispose { binding.release() }
        }
        AndroidView(
            factory = { viewContext ->
                SurfaceView(viewContext).apply {
                    setBackgroundColor(Color.BLACK)
                    holder.addCallback(binding)
                }
            },
            modifier = modifier,
            onRelease = { view ->
                view.holder.removeCallback(binding)
                binding.release()
            },
        )
    }

    private class AndroidSurfaceBinding(
        private val registry: AndroidSurfaceRegistry,
        private val scope: CoroutineScope,
        private val controller: () -> PlayerController,
    ) : SurfaceHolder.Callback {
        private var handle: PlayerSurfaceHandle? = null

        override fun surfaceCreated(holder: SurfaceHolder) {
            release()
            val registered = registry.register(holder)
            handle = registered
            scope.launch {
                try {
                    controller().attachSurface(registered)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    registry.unregister(registered)
                    if (handle === registered) handle = null
                }
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            release()
        }

        fun release() {
            val registered = handle ?: return
            handle = null
            scope.launch {
                try {
                    controller().detachSurface(registered)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The session may already be released; registry cleanup is still required.
                } finally {
                    registry.unregister(registered)
                }
            }
        }
    }

    companion object {
        @Volatile
        private var current: AndroidPlayerHost? = null

        fun getOrCreate(context: Context): AndroidPlayerHost = current ?: synchronized(this) {
            current ?: AndroidPlayerHost(context).also { current = it }
        }
    }
}
