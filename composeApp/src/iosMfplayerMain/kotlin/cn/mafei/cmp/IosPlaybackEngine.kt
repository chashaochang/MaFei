package cn.mafei.cmp

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.runtime.IosMfPlayerSurfaces
import cn.mafei.cmp.player.runtime.createPlayerRuntime
import cn.mafei.cmp.player.runtime_ios.IosAvPlayerBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mfplayer.cmp.ios.IosPlayerVideoSurface

internal class IosPlaybackEngine(private val bridge: IosAvPlayerBridge) {
    private val surfaces = IosMfPlayerSurfaces()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun createRuntime() = createPlayerRuntime(bridge, surfaces)

    @Composable
    fun Surface(controller: PlayerController, modifier: Modifier) {
        val surface = remember(controller) { surfaces.register() }
        val adapter by surface.adapter.collectAsState()
        LaunchedEffect(controller, surface) { controller.attachSurface(surface.handle) }
        DisposableEffect(controller, surface) {
            onDispose {
                scope.launch { controller.detachSurface(surface.handle) }
                surfaces.unregister(surface)
            }
        }
        val player = adapter
        if (player == null) Box(modifier) else IosPlayerVideoSurface(player, modifier)
    }
}
