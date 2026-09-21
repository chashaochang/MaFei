@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.ArkUIView2
import androidx.compose.ui.napi.JsArray
import androidx.compose.ui.napi.JsEnv
import androidx.compose.ui.napi.JsObject
import androidx.compose.ui.napi.asInt
import androidx.compose.ui.napi.asLong
import androidx.compose.ui.napi.js
import androidx.compose.ui.napi.jsFunction
import androidx.compose.ui.napi.nApiValue
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import cn.mafei.cmp.player.runtime_ohos.OhosAvPlayerBridge
import cn.mafei.cmp.player.runtime_ohos.OhosPlayerRuntime
import cn.mafei.cmp.player.runtime_ohos.OhosPollScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The ArkTS mfplayer host is deliberately kept outside the shared player API.
 * The shared controller still owns the playback contract; this object only
 * translates commands to the CPF ArkUI interop surface and reports the native
 * player's live state back to the OHOS runtime.
 */
private data class OhosPlayerCommandState(
    val commandId: Long = 0L,
    val handle: Long = 0L,
    val action: String = "none",
    val url: String? = null,
    val headers: List<String> = emptyList(),
    val subtitleUrl: String? = null,
    val autoPlay: Boolean = false,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
)

private data class OhosPlayerSnapshot(
    val itemStatus: Int = 0,
    val timeControl: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val errorKind: Int = 0,
)

/** Single-player bridge state. All command calls are serialized by the player facade. */
private object OhosPlayerHostState {
    private val commandState = MutableStateFlow(OhosPlayerCommandState())
    private var nextHandle = 1L
    private var nextCommandId = 0L
    private var activeHandle = 0L
    private var snapshot = OhosPlayerSnapshot()

    val commands: StateFlow<OhosPlayerCommandState> = commandState

    fun create(): Long {
        val handle = nextHandle++
        activeHandle = handle
        snapshot = OhosPlayerSnapshot()
        return handle
    }

    fun load(handle: Long, url: String, headers: Map<String, String>, subtitleUrl: String?) {
        if (handle != activeHandle) return
        publish(
            action = "load",
            handle = handle,
            url = url,
            headers = headers.entries.map { (name, value) -> "$name: $value" },
            subtitleUrl = subtitleUrl,
            // The shared PlayerStateMachine owns the autoplay decision and
            // sends an explicit Play command after the native Ready event.
            autoPlay = false,
        )
        snapshot = OhosPlayerSnapshot()
    }

    fun play(handle: Long) = publishAction(handle, "play")
    fun pause(handle: Long) = publishAction(handle, "pause")
    fun stop(handle: Long) {
        if (handle != activeHandle) return
        publishAction(handle, "stop")
        snapshot = OhosPlayerSnapshot()
    }

    fun seek(handle: Long, positionMs: Long) {
        if (handle != activeHandle) return
        publish(handle = handle, action = "seek", positionMs = positionMs.coerceAtLeast(0L))
    }

    fun speed(handle: Long, speed: Float) {
        if (handle != activeHandle) return
        publish(handle = handle, action = "speed", speed = speed)
    }

    fun release(handle: Long) {
        if (handle != activeHandle) return
        publishAction(handle, "release")
        activeHandle = 0L
        snapshot = OhosPlayerSnapshot()
    }

    fun snapshot(handle: Long): OhosPlayerSnapshot =
        if (handle == activeHandle) snapshot else OhosPlayerSnapshot()

    fun reportState(data: JsObject) {
        val handle = data["handle"].asLong() ?: return
        if (handle != activeHandle) return
        snapshot = OhosPlayerSnapshot(
            itemStatus = data["itemStatus"].asInt() ?: 0,
            timeControl = data["timeControl"].asInt() ?: 0,
            positionMs = (data["positionMs"].asLong() ?: 0L).coerceAtLeast(0L),
            durationMs = (data["durationMs"].asLong() ?: 0L).coerceAtLeast(0L),
            bufferedMs = (data["bufferedMs"].asLong() ?: 0L).coerceAtLeast(0L),
            errorKind = data["errorKind"].asInt() ?: 0,
        )
    }

    private fun publishAction(handle: Long, action: String) {
        if (handle != activeHandle) return
        publish(handle = handle, action = action)
    }

    private fun publish(
        handle: Long,
        action: String,
        url: String? = commandState.value.url,
        headers: List<String> = commandState.value.headers,
        subtitleUrl: String? = commandState.value.subtitleUrl,
        autoPlay: Boolean = commandState.value.autoPlay,
        positionMs: Long = commandState.value.positionMs,
        speed: Float = commandState.value.speed,
    ) {
        nextCommandId += 1
        commandState.value = OhosPlayerCommandState(
            commandId = nextCommandId,
            handle = handle,
            action = action,
            url = url,
            headers = headers,
            subtitleUrl = subtitleUrl,
            autoPlay = autoPlay,
            positionMs = positionMs,
            speed = speed,
        )
    }
}

private class ComposeOhosPlayerBridge : OhosAvPlayerBridge {
    override fun create(): Long = OhosPlayerHostState.create()

    override fun loadUrl(handle: Long, url: String, headers: Map<String, String>) {
        loadUrl(handle, url, headers, null)
    }

    override fun loadUrl(
        handle: Long,
        url: String,
        headers: Map<String, String>,
        subtitleUrl: String?,
    ) = OhosPlayerHostState.load(handle, url, headers, subtitleUrl)

    override fun play(handle: Long) = OhosPlayerHostState.play(handle)
    override fun pause(handle: Long) = OhosPlayerHostState.pause(handle)
    override fun stop(handle: Long) = OhosPlayerHostState.stop(handle)
    override fun seek(handle: Long, positionMs: Long) = OhosPlayerHostState.seek(handle, positionMs)
    override fun setSpeed(handle: Long, speed: Float) = OhosPlayerHostState.speed(handle, speed)
    override fun attachSurface(handle: Long, surfaceId: String?) = Unit
    override fun detachSurface(handle: Long) = Unit

    override fun positionMs(handle: Long): Long = OhosPlayerHostState.snapshot(handle).positionMs
    override fun durationMs(handle: Long): Long = OhosPlayerHostState.snapshot(handle).durationMs
    override fun bufferedMs(handle: Long): Long = OhosPlayerHostState.snapshot(handle).bufferedMs
    override fun timeControl(handle: Long): Int = OhosPlayerHostState.snapshot(handle).timeControl
    override fun itemStatus(handle: Long): Int = OhosPlayerHostState.snapshot(handle).itemStatus
    override fun errorKind(handle: Long): Int = OhosPlayerHostState.snapshot(handle).errorKind
    override fun releasePlayer(handle: Long) = OhosPlayerHostState.release(handle)
}

private class MainPollScheduler : OhosPollScheduler {
    override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val job: Job = scope.launch {
            while (isActive) {
                delay(periodMs)
                tick()
            }
        }
        return AutoCloseable {
            job.cancel()
            scope.cancel()
        }
    }
}

fun createOhosPlayerRuntime(): PlayerRuntime = OhosPlayerRuntime(
    capabilities = PlayerCapabilities(
        seek = true,
        speed = true,
        // Track negotiation is kept disabled until the common OHOS bridge
        // grows a typed track snapshot. The old engine still receives the
        // selected subtitle URL supplied by the provider.
        trackSelection = false,
        backgroundPlayback = false,
    ),
    bridge = ComposeOhosPlayerBridge(),
    pollScheduler = MainPollScheduler(),
)

/**
 * ArkUI's mfplayer PlayerSurface is a video-only native surface. The Compose
 * NowPlayingScreen remains the sole control layer and is rendered above it.
 */
@Composable
fun OhosPlayerSurface(
    controller: PlayerController,
    modifier: Modifier,
) {
    val command by OhosPlayerHostState.commands.collectAsState()
    val scope = rememberCoroutineScope()
    val surfaceHandle = remember { PlayerSurfaceHandle() }
    val stateReporter = remember {
        jsFunction<Any, JsObject?, Unit> { state ->
            state?.let(OhosPlayerHostState::reportState)
            Unit
        }
    }
    val parameter = remember(command, stateReporter) {
        command.toJsParameter(stateReporter.jsValue)
    }

    LaunchedEffect(controller, surfaceHandle) {
        runCatching { controller.attachSurface(surfaceHandle) }
    }
    DisposableEffect(controller, surfaceHandle, stateReporter) {
        onDispose {
            stateReporter.dispose()
            scope.launch { runCatching { controller.detachSurface(surfaceHandle) } }
        }
    }

    ArkUIView2(
        name = "MaFeiPlayerSurface",
        modifier = modifier,
        parameter = parameter,
        background = Color.Transparent,
        interactive = false,
    )
}

private fun OhosPlayerCommandState.toJsParameter(onState: platform.ArkTS.ArkTS_Napi_NativeModule.napi_value?): JsObject {
    val headers = JsArray(JsEnv.createArray(this.headers.size))
    this.headers.forEachIndexed { index, value -> headers[index] = value.nApiValue() }
    return js {
        "commandId"(commandId)
        "handle"(handle)
        "action"(action)
        "url"(url ?: "")
        "headers"(headers.jsValue)
        "subtitleUrl"(subtitleUrl ?: "")
        "autoPlay"(autoPlay)
        "positionMs"(positionMs)
        "speed"(speed)
        "onState"(onState)
    }
}
