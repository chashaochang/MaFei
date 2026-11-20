package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.runtime.createPlayerRuntime
import cn.mafei.cmp.player.runtime_ios.IosAvPlayerBridge
import cn.mafei.cmp.player.runtime_ios.IosSurfaceRegistry
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.UIKit.UIView

internal class IosPlaybackEngine(private val bridge: IosAvPlayerBridge) {
    private val surfaces = IosSurfaceRegistry()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun createRuntime() = createPlayerRuntime(bridge, surfaces, scope)

    @OptIn(ExperimentalForeignApi::class)
    @Composable
    fun Surface(controller: PlayerController, modifier: Modifier) {
        val view = remember(controller) { bridge.createVideoView() as UIView }
        val surface = remember(view) { surfaces.register(view.layer) }
        LaunchedEffect(controller, surface) { controller.attachSurface(surface) }
        DisposableEffect(controller, surface) {
            onDispose { scope.launch { controller.detachSurface(surface) }; surfaces.unregister(surface) }
        }
        UIKitView(factory = { view }, modifier = modifier)
    }
}
