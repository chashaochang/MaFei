@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.ArkUIView2
import androidx.compose.ui.napi.JsArray
import androidx.compose.ui.napi.JsEnv
import androidx.compose.ui.napi.JsObject
import androidx.compose.ui.napi.js
import androidx.compose.ui.napi.nApiValue
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerContentMode
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerDeviceControls
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.TrackKind
import cn.mafei.cmp.player.runtime_ohos.OhosNativePlayerBridge
import cn.mafei.cmp.player.runtime_ohos.OhosNativePlayerEvent
import cn.mafei.cmp.player.runtime_ohos.OhosPlayerRuntime
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toKString
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalNativeApi
import platform.PerformanceAnalysisKit.HiLog.LOG_APP
import platform.PerformanceAnalysisKit.HiLog.LOG_INFO
import platform.PerformanceAnalysisKit.HiLog.OH_LOG_PrintMsg

private fun logOhosPlayer(message: String) {
    OH_LOG_PrintMsg(LOG_APP, LOG_INFO, 0x4D46u, "MaFeiPlayback", message)
}

private fun String.safeEndpoint(): String {
    val schemeEnd = indexOf("://")
    if (schemeEnd <= 0) return "relative"
    val authorityStart = schemeEnd + 3
    val authorityEnd = indexOf('/', authorityStart).let { if (it < 0) length else it }
    return substring(0, authorityEnd).substringBefore('?').substringBefore('#')
}

/** One low-frequency command/session update sent to the native video surface. */
private data class OhosPlayerCommandState(
    val commandId: Long = 0L,
    val handle: Long = 0L,
    val action: String = "none",
    val url: String? = null,
    val headers: List<String> = emptyList(),
    val subtitleUrl: String? = null,
    val allowInsecureTls: Boolean = false,
    val multiConnectionAllowed: Boolean = true,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val trackId: Int = -1,
    /** mfplayer `PlayerContentMode` name; the ArkUI host maps it back to the enum. */
    val contentMode: String = "Fit",
    /** 亮度 0..1 / 音量步数，配合 action = setBrightness / setVolume。 */
    val level: Float = 0f,
)

@Serializable
private data class OhosPlayerTrackPayload(
    val id: Int,
    val kind: String,
    val label: String,
    val language: String? = null,
    val selected: Boolean = false,
)

/** Owned JSON copied synchronously from N-API; no JS handle escapes the call. */
@Serializable
private data class OhosPlayerEventPayload(
    val handle: Long,
    val event: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val failure: String? = null,
    val tracks: List<OhosPlayerTrackPayload> = emptyList(),
    /**
     * `event = "device"` 专用：ArkUI 侧的窗口亮度与媒体音量。这些值不挂在任何一次播放上，
     * 所以没有播放器实例时也会上报。
     */
    val brightness: Float? = null,
    val volume: Int? = null,
    val maxVolume: Int? = null,
)

private object OhosPlayerHostState {
    private val json = Json { ignoreUnknownKeys = true }
    // This state is consumed only by the Compose surface and every command is
    // issued on the app UI thread. Keep it in the Compose snapshot system so a
    // newly published native-player command invalidates ArkUIView2 immediately.
    private var commandState by mutableStateOf(OhosPlayerCommandState())
    private var nextHandle = 1L
    private var nextCommandId = 0L
    private var activeHandle = 0L
    private var eventSink: ((OhosNativePlayerEvent) -> Unit)? = null
    private var systemBackHandler: (() -> Unit)? = null

    val command: OhosPlayerCommandState
        get() = commandState

    fun bind(sink: (OhosNativePlayerEvent) -> Unit) {
        eventSink = sink
    }

    fun unbind(sink: (OhosNativePlayerEvent) -> Unit) {
        if (eventSink === sink) eventSink = null
    }

    /**
     * 平台亮度/音量缓存：ArkTS 侧推送（挂载时 + 被请求时），Kotlin 侧写入时立刻回填。
     * 手势开始要的是同步读，而亮度/音量在 OHOS 只能经 ArkUI 拿到，所以这里缓存。
     * 代价：在播放页之外改过系统音量时，第一次手势的基准值可能滞后一次 —— 每次手势开始
     * 都会请求刷新，所以之后就是准的。
     */
    private var deviceBrightness: Float? = null
    private var deviceVolume: Int? = null
    private var deviceMaxVolume: Int = 0

    val brightnessValue: Float? get() = deviceBrightness
    val volumeValue: Int? get() = deviceVolume
    val maxVolumeValue: Int get() = deviceMaxVolume

    fun bindSystemBack(handler: () -> Unit) {
        systemBackHandler = handler
    }

    fun unbindSystemBack(handler: () -> Unit) {
        if (systemBackHandler === handler) systemBackHandler = null
    }

    fun create(): Long {
        val handle = nextHandle++
        activeHandle = handle
        logOhosPlayer("host create handle=$handle")
        return handle
    }

    fun load(
        handle: Long,
        url: String,
        headers: Map<String, String>,
        subtitleUrl: String?,
        allowInsecureTls: Boolean,
        multiConnectionAllowed: Boolean,
        startPositionMs: Long,
    ) {
        if (handle != activeHandle) {
            logOhosPlayer("host load rejected handle=$handle active=$activeHandle")
            return
        }
        logOhosPlayer(
            "host load handle=$handle endpoint=${url.safeEndpoint()} " +
                "headers=${headers.keys.sorted().joinToString(",")} subtitle=${subtitleUrl != null}",
        )
        publish(
            handle = handle,
            action = "load",
            url = url,
            headers = headers.entries.map { (name, value) -> "$name: $value" },
            subtitleUrl = subtitleUrl,
            allowInsecureTls = allowInsecureTls,
            multiConnectionAllowed = multiConnectionAllowed,
            positionMs = startPositionMs.coerceAtLeast(0L),
        )
    }

    fun play(handle: Long, pendingSeekPositionMs: Long?) {
        if (handle != activeHandle) return
        publish(
            handle = handle,
            action = "play",
            positionMs = pendingSeekPositionMs?.coerceAtLeast(0L) ?: 0L,
        )
    }
    fun pause(handle: Long) = publishAction(handle, "pause")
    fun stop(handle: Long) = publishAction(handle, "stop")

    fun seek(handle: Long, positionMs: Long) {
        if (handle != activeHandle) return
        publish(handle = handle, action = "seek", positionMs = positionMs.coerceAtLeast(0L))
    }

    fun speed(handle: Long, speed: Float) {
        if (handle != activeHandle) return
        publish(handle = handle, action = "speed", speed = speed)
    }

    fun selectTrack(handle: Long, kind: TrackKind, id: String?) {
        if (handle != activeHandle) return
        val trackId = when (kind) {
            TrackKind.Audio -> id?.toIntOrNull() ?: return
            TrackKind.Subtitle -> id?.toIntOrNull() ?: -1
            TrackKind.Video -> return
        }
        publish(
            handle = handle,
            action = if (kind == TrackKind.Audio) "selectAudio" else "selectSubtitle",
            trackId = trackId,
        )
    }

    /**
     * Picture fitting. Surface-scoped, so it only needs a live host: the mode is
     * carried on every published command and ArkTS keeps it as its own state,
     * which is what makes it survive the per-load player rebuilds.
     */
    fun contentMode(handle: Long, mode: PlayerContentMode) {
        if (handle != activeHandle) return
        publish(handle = handle, action = "contentMode", contentMode = mode.name)
    }

    /** 让 ArkTS 重新读一次窗口亮度与媒体音量并推上来（异步）。 */
    fun requestDeviceState() {
        publish(handle = activeHandle, action = "deviceState")
    }

    /**
     * 亮度只影响播放页窗口（与旧版 setWindowBrightness 一致），写完立刻回填缓存，
     * 这样连续手势从我们自己设的值继续加减。
     */
    fun setDeviceBrightness(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        deviceBrightness = clamped
        publish(handle = activeHandle, action = "setBrightness", level = clamped)
    }

    fun setDeviceVolume(steps: Int) {
        if (deviceMaxVolume <= 0) return
        val clamped = steps.coerceIn(0, deviceMaxVolume)
        deviceVolume = clamped
        publish(handle = activeHandle, action = "setVolume", level = clamped.toFloat())
    }

    fun release(handle: Long) {
        if (handle != activeHandle) return
        publishAction(handle, "release")
        activeHandle = 0L
    }

    fun handleSystemBack(): Boolean {
        systemBackHandler?.invoke()
        // Index calls this only while the player route is visible. During the
        // one-frame mount/unmount window, consume Back instead of finishing the app.
        return true
    }

    fun report(raw: String): Boolean {
        val payload = runCatching { json.decodeFromString<OhosPlayerEventPayload>(raw) }.getOrNull()
            ?: return false
        if (payload.event == "device") {
            // 设备状态与「哪个播放器实例」无关：还没 load 时也要收得到。
            deviceBrightness = payload.brightness?.let { reported ->
                if (reported >= 0f) reported else PlayerDeviceControls.DEFAULT_BRIGHTNESS
            }
            deviceVolume = payload.volume
            deviceMaxVolume = payload.maxVolume?.coerceAtLeast(0) ?: 0
            return true
        }
        if (payload.handle != activeHandle) return false
        eventSink?.invoke(payload.toNativeEvent() ?: return false)
        return true
    }

    private fun OhosPlayerEventPayload.toNativeEvent(): OhosNativePlayerEvent? = when (event) {
        "ready" -> OhosNativePlayerEvent.Ready(handle, durationMs.takeIf { it > 0L })
        "playing" -> OhosNativePlayerEvent.Playing(handle)
        "paused" -> OhosNativePlayerEvent.Paused(handle)
        "buffering" -> OhosNativePlayerEvent.Buffering(handle)
        "progress" -> OhosNativePlayerEvent.Progress(
            handle = handle,
            positionMs = positionMs,
            bufferedPositionMs = bufferedMs,
            durationMs = durationMs.takeIf { it > 0L },
        )
        "tracks" -> OhosNativePlayerEvent.Tracks(
            handle,
            tracks.mapNotNull { track ->
                val kind = when (track.kind) {
                    "audio" -> TrackKind.Audio
                    "subtitle" -> TrackKind.Subtitle
                    else -> null
                } ?: return@mapNotNull null
                PlayerTrack(track.id.toString(), kind, track.label, track.language, track.selected)
            },
        )
        "ended" -> OhosNativePlayerEvent.Ended(handle)
        "error" -> OhosNativePlayerEvent.Failed(
            handle,
            when (failure) {
                "network" -> PlayerFailure.Network
                "decode" -> PlayerFailure.Decode
                "unsupported" -> PlayerFailure.UnsupportedMedia
                else -> PlayerFailure.Engine
            },
        )
        else -> null
    }

    private fun publishAction(handle: Long, action: String) {
        if (handle == activeHandle) publish(handle = handle, action = action)
    }

    private fun publish(
        handle: Long,
        action: String,
        url: String? = commandState.url,
        headers: List<String> = commandState.headers,
        subtitleUrl: String? = commandState.subtitleUrl,
        allowInsecureTls: Boolean = commandState.allowInsecureTls,
        multiConnectionAllowed: Boolean = commandState.multiConnectionAllowed,
        positionMs: Long = commandState.positionMs,
        speed: Float = commandState.speed,
        trackId: Int = commandState.trackId,
        contentMode: String = commandState.contentMode,
        level: Float = commandState.level,
    ) {
        nextCommandId += 1L
        logOhosPlayer("host publish command=$nextCommandId action=$action handle=$handle")
        val next = OhosPlayerCommandState(
            commandId = nextCommandId,
            handle = handle,
            action = action,
            url = url,
            headers = headers,
            subtitleUrl = subtitleUrl,
            allowInsecureTls = allowInsecureTls,
            multiConnectionAllowed = multiConnectionAllowed,
            positionMs = positionMs,
            speed = speed,
            trackId = trackId,
            contentMode = contentMode,
            level = level,
        )
        commandState = next
    }
}

/** Stable ArkTS -> Kotlin low-frequency event entry point. */
@OptIn(ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@CName("UpdateOhosPlayerState")
fun UpdateOhosPlayerState(payload: CPointer<ByteVar>?): Boolean {
    val raw = payload?.toKString() ?: return false
    return OhosPlayerHostState.report(raw)
}

/** HarmonyOS host fallback so a system back gesture can never finish the app while the player is open. */
@OptIn(ExperimentalNativeApi::class)
@CName("HandleOhosPlayerBack")
fun HandleOhosPlayerBack(): Boolean = OhosPlayerHostState.handleSystemBack()

private class ComposeOhosPlayerBridge : OhosNativePlayerBridge {
    override fun bind(eventSink: (OhosNativePlayerEvent) -> Unit) = OhosPlayerHostState.bind(eventSink)
    override fun create(): Long = OhosPlayerHostState.create()
    override fun loadUrl(
        handle: Long,
        url: String,
        headers: Map<String, String>,
        subtitleUrl: String?,
        allowInsecureTls: Boolean,
        multiConnectionAllowed: Boolean,
        startPositionMs: Long,
    ) {
        logOhosPlayer("bridge loadUrl handle=$handle endpoint=${url.safeEndpoint()}")
        OhosPlayerHostState.load(
            handle,
            url,
            headers,
            subtitleUrl,
            allowInsecureTls,
            multiConnectionAllowed,
            startPositionMs,
        )
    }
    override fun play(handle: Long, pendingSeekPositionMs: Long?) =
        OhosPlayerHostState.play(handle, pendingSeekPositionMs)
    override fun pause(handle: Long) = OhosPlayerHostState.pause(handle)
    override fun stop(handle: Long) = OhosPlayerHostState.stop(handle)
    override fun seek(handle: Long, positionMs: Long) = OhosPlayerHostState.seek(handle, positionMs)
    override fun setSpeed(handle: Long, speed: Float) = OhosPlayerHostState.speed(handle, speed)
    override fun selectTrack(handle: Long, kind: TrackKind, id: String?) =
        OhosPlayerHostState.selectTrack(handle, kind, id)
    override fun setContentMode(handle: Long, mode: PlayerContentMode) =
        OhosPlayerHostState.contentMode(handle, mode)
    override fun releasePlayer(handle: Long) = OhosPlayerHostState.release(handle)
    override fun unbind(eventSink: (OhosNativePlayerEvent) -> Unit) =
        OhosPlayerHostState.unbind(eventSink)
}

fun createOhosPlayerRuntime(): PlayerRuntime = OhosPlayerRuntime(
    capabilities = PlayerCapabilities(
        seek = true,
        speed = true,
        trackSelection = true,
        backgroundPlayback = false,
        // mfplayer's PlayerSurface takes PlayerContentMode directly.
        contentMode = true,
    ),
    bridge = ComposeOhosPlayerBridge(),
)

/**
 * HarmonyOS keeps only mfplayer's native video surface in ArkUI. All visible
 * chrome, gestures and menus remain in the shared CMP player above this node.
 */
@Composable
fun OhosPlayerSurface(
    controller: PlayerController,
    onBack: () -> Unit,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val command = OhosPlayerHostState.command
    SideEffect {
        logOhosPlayer("surface compose command=${command.commandId} action=${command.action}")
    }
    val scope = rememberCoroutineScope()
    val surfaceHandle = remember { PlayerSurfaceHandle() }
    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnExitFullscreen by rememberUpdatedState(onExitFullscreen)

    DisposableEffect(controller, surfaceHandle) {
        scope.launch { runCatching { controller.attachSurface(surfaceHandle) } }
        onDispose {
            scope.launch { runCatching { controller.detachSurface(surfaceHandle) } }
        }
    }

    BoxWithConstraints(modifier) {
        val fullscreen = maxWidth > maxHeight
        val systemBackHandler = remember(fullscreen) {
            {
                if (fullscreen) latestOnExitFullscreen() else latestOnBack()
            }
        }
        DisposableEffect(systemBackHandler) {
            OhosPlayerHostState.bindSystemBack(systemBackHandler)
            onDispose { OhosPlayerHostState.unbindSystemBack(systemBackHandler) }
        }

        // Match CPF's official CameraPreview interop pattern: each command
        // produces a new parameter object, which ArkUIView2 forwards to ArkTS.
        ArkUIView2(
            name = "MaFeiPlayerSurface",
            modifier = Modifier.fillMaxSize(),
            parameter = command.toJsParameter(),
            background = Color.Black,
            interactive = false,
        )
    }
}

private fun OhosPlayerCommandState.toJsParameter(): JsObject {
    val headerValues = JsArray(JsEnv.createArray(headers.size))
    headers.forEachIndexed { index, value -> headerValues[index] = value.nApiValue() }
    return js {
        "commandId"(commandId.nApiValue())
        "handle"(handle.nApiValue())
        "action"(action)
        "url"(url ?: "")
        "headers"(headerValues.jsValue)
        "subtitleUrl"(subtitleUrl ?: "")
        "allowInsecureTls"(allowInsecureTls)
        "multiConnectionAllowed"(multiConnectionAllowed)
        "positionMs"(positionMs.nApiValue())
        "speed"(speed)
        "trackId"(trackId)
        "contentMode"(contentMode)
        "level"(level)
    }
}

/**
 * 播放页竖向手势的亮度/音量实现：值由 ArkTS 推送，写入回给 ArkUI 宿主执行。
 * 读不到（ArkTS 没上报）时返回 null / 0，手势那一侧就不介入。
 */
internal object OhosDeviceControls : PlayerDeviceControls {
    override fun brightness(): Float? = OhosPlayerHostState.brightnessValue
    override fun setBrightness(value: Float) = OhosPlayerHostState.setDeviceBrightness(value)
    override fun volume(): Int? = OhosPlayerHostState.volumeValue
    override fun maxVolume(): Int = OhosPlayerHostState.maxVolumeValue
    override fun setVolume(steps: Int) = OhosPlayerHostState.setDeviceVolume(steps)
    override fun refresh() = OhosPlayerHostState.requestDeviceState()
}
