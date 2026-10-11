package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cn.mafei.cmp.player.api.*
import cn.mafei.cmp.player.runtime_ios.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import platform.UIKit.UIView

/** AVFoundation is implemented by Swift; no dependency on the incomplete CPF AVFoundation klib. */
internal class IosPlayerHost(private val bridge: IosAvPlayerBridge) {
    private val surfaces = IosSurfaceRegistry()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    fun runtime() = IosPlayerRuntime(
        PlayerCapabilities(seek = true, speed = true, trackSelection = true, contentMode = true), bridge, surfaces,
        object : IosPollScheduler {
            override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable {
                val job = scope.launch { while (isActive) { delay(periodMs); tick() } }
                return AutoCloseable { job.cancel() }
            }
        },
    )

    @OptIn(ExperimentalForeignApi::class)
    @Composable
    fun Surface(controller: PlayerController, modifier: Modifier) {
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
        val view = remember(controller) { bridge.createVideoView() as UIView }
        val surface = remember(view) { surfaces.register(view.layer) }
        LaunchedEffect(controller, surface) { controller.attachSurface(surface) }
        DisposableEffect(controller, surface) {
            onDispose { scope.launch { controller.detachSurface(surface) }; surfaces.unregister(surface) }
        }
        UIKitView(factory = { view }, modifier = modifier)
    }
}
