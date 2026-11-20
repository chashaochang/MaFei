package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.player.api.*
import cn.mafei.cmp.player.runtime_ios.IosAvPlayerBridge
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class IosPlayerHost(private val bridge: IosAvPlayerBridge) {
    private val engine = IosPlaybackEngine(bridge)
    fun createRuntime() = engine.createRuntime()

    @Composable
    fun Surface(controller: PlayerController, modifier: Modifier) {
        val screenAwakeOwner = remember { Any() }
        val keepScreenAwake = LocalVideoScreenAwake.current
        DisposableEffect(screenAwakeOwner, keepScreenAwake) {
            IosVideoScreenAwake.setRequested(screenAwakeOwner, keepScreenAwake)
            onDispose { IosVideoScreenAwake.setRequested(screenAwakeOwner, false) }
        }
        val latestActions by rememberUpdatedState(LocalPlatformPlaybackActions.current)
        val generation by remember(controller) { controller.state.map { it.generation }.distinctUntilChanged() }
            .collectAsState(controller.state.value.generation)
        DisposableEffect(controller, generation) {
            val actions = latestActions
            val binding = if (generation != null && actions != null && actions.generation === generation) {
                bridge.bindSystemControls(actions.title, actions.onNext != null, actions.onPrevious != null) handler@ { action, value ->
                    val current = latestActions ?: return@handler
                    if (current.generation !== generation || controller.state.value.generation !== generation) return@handler
                    when (action) {
                        "play" -> current.onPlay()
                        "pause" -> current.onPause()
                        "toggle" -> if (controller.state.value.phase == PlayerPhase.Playing) current.onPause() else current.onPlay()
                        "stop" -> current.onStop()
                        "seek" -> if (value.isFinite() && value >= 0) current.onSeek(value.toLong())
                        "next" -> current.onNext?.invoke()
                        "previous" -> current.onPrevious?.invoke()
                    }
                }
            } else 0L
            onDispose { if (binding != 0L) bridge.unbindSystemControls(binding) }
        }
        engine.Surface(controller, modifier)
    }
}
