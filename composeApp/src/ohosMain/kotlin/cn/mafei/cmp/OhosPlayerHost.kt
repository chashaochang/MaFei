@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import cn.mafei.cmp.player.api.PlayerRuntimeInfo
import cn.mafei.cmp.player.api.PlayerRenderMode
import cn.mafei.cmp.player.api.PlayerPlaybackPreferences
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.TrackKind
import cn.mafei.cmp.player.runtime_ohos.OhosNativePlayerBridge
import cn.mafei.cmp.player.runtime_ohos.OhosNativePlayerEvent
import cn.mafei.cmp.player.runtime_ohos.OhosPlayerRuntime
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.readBytes
import androidx.compose.ui.graphics.toComposeImageBitmap
import cn.mafei.cmp.player.ui.PlayerVideoBackdropSource
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalNativeApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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
    val audioOnly: Boolean = false,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val trackId: Int = -1,
    /** mfplayer `PlayerContentMode` name; the ArkUI host maps it back to the enum. */
    val contentMode: String = "Fit",
    /** Native output path; applied by mfplayer on the next load. */
    val renderMode: String = PlayerRenderMode.Buffer.name,
    val useHardwareDecode: Boolean = true,
    val cacheSizeMb: Int = 1024,
    val engine: String = "Mpv",
    /** 亮度 0..1 / 音量步数，配合 action = setBrightness / setVolume。 */
    val level: Float = 0f,
    /** Non-zero only for a one-shot native frame preview request. */
    val previewRequestId: Long = 0L,
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
    val systemAction: String? = null,
    val systemValue: Double = 0.0,
    val pipActive: Boolean = false,
    val backgroundAudioActive: Boolean = false,
    val castingActive: Boolean = false,
    val engine: String? = null,
    val tracks: List<OhosPlayerTrackPayload> = emptyList(),
    /**
     * `event = "device"` 专用：ArkUI 侧的窗口亮度与媒体音量。这些值不挂在任何一次播放上，
     * 所以没有播放器实例时也会上报。
     */
    val brightness: Float? = null,
    val volume: Int? = null,
    val maxVolume: Int? = null,
    val previewRequestId: Long = 0L,
    val previewData: String? = null,
    val previewWidthPx: Int = 0,
    val previewHeightPx: Int = 0,
    val decodeMode: String? = null,
    val decoder: String? = null,
    val renderer: String? = null,
    val videoCodec: String? = null,
    val videoCodecProfile: String? = null,
    val videoPixelFormat: String? = null,
    val videoPrimaries: String? = null,
    val videoTransfer: String? = null,
    val outputPrimaries: String? = null,
    val outputTransfer: String? = null,
    val dynamicRange: String? = null,
    val videoWidthPx: Int? = null,
    val videoHeightPx: Int? = null,
    val videoBitRate: Int? = null,
    val audioBitRate: Int? = null,
    val audioSampleRateHz: Int? = null,
    val audioChannels: Int? = null,
    val renderedDropCount: Long? = null,
    val decoderDropCount: Long? = null,
    val delayedFrameCount: Long? = null,
    val downloadSpeedBytesPerSecond: Long? = null,
    val bufferedDurationMs: Long? = null,
    val networkSource: Boolean? = null,
    val configuredConnections: Int? = null,
    val activeConnections: Int? = null,
)

internal val ohosVideoBackdropSource = PlayerVideoBackdropSource()
private var ohosVideoBackdropRevision by mutableLongStateOf(0L)

private object OhosPlayerHostState {
    private val json = Json { ignoreUnknownKeys = true }
    // This state is consumed only by the Compose surface and every command is
    // issued on the app UI thread. Keep it in the Compose snapshot system so a
    // newly published native-player command invalidates ArkUIView2 immediately.
    private var commandState by mutableStateOf(OhosPlayerCommandState())
    private var nextHandle = 1L
    private var nextCommandId = 0L
    private var nextPreviewRequestId = 0L
    private var activeHandle = 0L
    private var eventSink: ((OhosNativePlayerEvent) -> Unit)? = null
    private var systemBackHandler: (() -> Unit)? = null
    private var systemActionHandler: ((String, Double) -> Unit)? = null
    private var playing = false
    private var positionMs = 0L
    private var durationMs = 0L
    private val pendingPreviewRequests = mutableMapOf<Long, CompletableDeferred<PlayerSeekPreviewFrame?>>()

    val command: OhosPlayerCommandState
        get() = commandState

    fun bindSystemActions(handler: (String, Double) -> Unit) { systemActionHandler = handler }
    fun unbindSystemActions(handler: (String, Double) -> Unit) {
        if (systemActionHandler === handler) systemActionHandler = null
    }

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
        ohosVideoBackdropRevision++
        ohosVideoBackdropSource.frame = null
        playing = false
        positionMs = 0L
        durationMs = 0L
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
        audioOnly: Boolean,
        startPositionMs: Long,
        preferences: PlayerPlaybackPreferences,
    ) {
        if (handle != activeHandle) {
            logOhosPlayer("host load rejected handle=$handle active=$activeHandle")
            return
        }
        ohosVideoBackdropRevision++
        ohosVideoBackdropSource.frame = null
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
            audioOnly = audioOnly,
            positionMs = startPositionMs.coerceAtLeast(0L),
            useHardwareDecode = preferences.useHardwareDecode,
            cacheSizeMb = preferences.cacheSizeMb,
            engine = preferences.engine.name,
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
    fun pictureInPicture() { if (activeHandle != 0L) publishAction(activeHandle, "pip") }
    fun casting() { if (activeHandle != 0L) publishAction(activeHandle, "cast") }
    fun stop(handle: Long) = publishAction(handle, "stop")

    fun seek(handle: Long, positionMs: Long) {
        if (handle != activeHandle) return
        publish(handle = handle, action = "seek", positionMs = positionMs.coerceAtLeast(0L))
    }

    suspend fun previewFrame(handle: Long, positionMs: Long): PlayerSeekPreviewFrame? {
        if (handle == 0L || handle != activeHandle) return null
        val requestId = ++nextPreviewRequestId
        val result = CompletableDeferred<PlayerSeekPreviewFrame?>()
        pendingPreviewRequests[requestId] = result
        publish(
            handle = handle,
            action = "preview",
            positionMs = positionMs.coerceAtLeast(0L),
            previewRequestId = requestId,
        )
        return try {
            // A native extractor must never hold the seek gesture indefinitely.
            withTimeoutOrNull(1_500L) { result.await() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            pendingPreviewRequests.remove(requestId)
        }
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

    fun renderMode(mode: PlayerRenderMode) {
        if (activeHandle == 0L) {
            publish(handle = 0L, action = "renderMode", renderMode = mode.name)
        } else {
            publish(handle = activeHandle, action = "renderMode", renderMode = mode.name)
        }
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
        ohosVideoBackdropRevision++
        ohosVideoBackdropSource.frame = null
        pendingPreviewRequests.values.forEach { request -> request.complete(null) }
        pendingPreviewRequests.clear()
    }

    fun acceptsBackdrop(handle: Long, revision: Long): Boolean =
        handle != 0L && handle == activeHandle && revision == ohosVideoBackdropRevision && ohosVideoBackdropSource.sampling

    fun handleSystemBack(): Boolean {
        systemBackHandler?.invoke()
        // Index calls this only while the player route is visible. During the
        // one-frame mount/unmount window, consume Back instead of finishing the app.
        return true
    }

    /**
     * ArkUI receives physical keyboard events before CPF's Compose key node on
     * OHOS PC. Keep a small host fallback for the player controls so keyboard
     * playback never depends on the focus bridge being available on a given
     * CPF renderer/device combination.
     */
    fun handleKey(keyCode: Int, action: Int): Boolean {
        if (activeHandle == 0L) return false
        // Consume the key-up as well; otherwise Harmony's window manager may
        // interpret Escape/Enter after the player has already handled it.
        if (action != 0) return true
        when (keyCode) {
            OhosKeyCode.Space,
            OhosKeyCode.Enter -> {
                if (playing) {
                    pause(activeHandle)
                } else {
                    play(activeHandle, null)
                }
                playing = !playing
                return true
            }
            OhosKeyCode.Left -> {
                seek(activeHandle, (positionMs - 10_000L).coerceAtLeast(0L))
                return true
            }
            OhosKeyCode.Right -> {
                val end = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                seek(activeHandle, (positionMs + 10_000L).coerceAtMost(end))
                return true
            }
            OhosKeyCode.Escape -> return handleSystemBack()
            else -> return false
        }
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
        if (payload.event == "preview") {
            val request = pendingPreviewRequests.remove(payload.previewRequestId) ?: return false
            val frame = payload.previewData
                ?.let(::decodeBase64)
                ?.takeIf { it.isNotEmpty() }
                ?.let { bytes ->
                    runCatching {
                        PlayerSeekPreviewFrame(
                            encodedTile = bytes,
                            frameWidthPx = payload.previewWidthPx,
                            frameHeightPx = payload.previewHeightPx,
                            tileColumn = 0,
                            tileRow = 0,
                        )
                    }.getOrNull()
                }
            request.complete(frame)
            return true
        }
        if (payload.handle != activeHandle) return false
        if (payload.event == "systemAction") {
            val action = payload.systemAction ?: return false
            if (!payload.systemValue.isFinite()) return false
            systemActionHandler?.invoke(action, payload.systemValue) ?: return false
            return true
        }
        when (payload.event) {
            "playing" -> playing = true
            "paused", "ended", "error" -> playing = false
            "progress" -> {
                positionMs = payload.positionMs.coerceAtLeast(0L)
                durationMs = payload.durationMs.coerceAtLeast(0L)
            }
            "ready" -> durationMs = payload.durationMs.coerceAtLeast(0L)
        }
        eventSink?.invoke(payload.toNativeEvent() ?: return false)
        return true
    }

    private fun OhosPlayerEventPayload.toNativeEvent(): OhosNativePlayerEvent? = when (event) {
        "ready" -> OhosNativePlayerEvent.Ready(handle, durationMs.takeIf { it > 0L })
        "playing" -> OhosNativePlayerEvent.Playing(handle)
        "paused" -> OhosNativePlayerEvent.Paused(handle)
        "buffering" -> OhosNativePlayerEvent.Buffering(handle)
        "pip" -> OhosNativePlayerEvent.PictureInPicture(handle, pipActive)
        "backgroundAudio" -> OhosNativePlayerEvent.BackgroundAudio(handle, backgroundAudioActive)
        "casting" -> OhosNativePlayerEvent.Casting(handle, castingActive)
        "engine" -> engine?.let { value -> cn.mafei.cmp.player.api.PlayerEngineType.entries.firstOrNull { it.name == value } }
            ?.let { OhosNativePlayerEvent.EngineChanged(handle, it) }
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
        "diagnostics" -> OhosNativePlayerEvent.Diagnostics(
            handle,
            PlayerRuntimeInfo(
                decodeMode = decodeMode,
                decoder = decoder,
                renderer = renderer,
                videoCodec = videoCodec,
                videoCodecProfile = videoCodecProfile,
                videoPixelFormat = videoPixelFormat,
                videoPrimaries = videoPrimaries,
                videoTransfer = videoTransfer,
                outputPrimaries = outputPrimaries,
                outputTransfer = outputTransfer,
                dynamicRange = dynamicRange,
                videoWidthPx = videoWidthPx,
                videoHeightPx = videoHeightPx,
                videoBitRate = videoBitRate,
                audioBitRate = audioBitRate,
                audioSampleRateHz = audioSampleRateHz,
                audioChannels = audioChannels,
                renderedDropCount = renderedDropCount,
                decoderDropCount = decoderDropCount,
                delayedFrameCount = delayedFrameCount,
                downloadSpeedBytesPerSecond = downloadSpeedBytesPerSecond,
                bufferedDurationMs = bufferedDurationMs,
                networkSource = networkSource,
                configuredConnections = configuredConnections,
                activeConnections = activeConnections,
            ),
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
        audioOnly: Boolean = commandState.audioOnly,
        positionMs: Long = commandState.positionMs,
        speed: Float = commandState.speed,
        trackId: Int = commandState.trackId,
        contentMode: String = commandState.contentMode,
        renderMode: String = commandState.renderMode,
        useHardwareDecode: Boolean = commandState.useHardwareDecode,
        cacheSizeMb: Int = commandState.cacheSizeMb,
        engine: String = commandState.engine,
        level: Float = commandState.level,
        previewRequestId: Long = commandState.previewRequestId,
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
            audioOnly = audioOnly,
            positionMs = positionMs,
            speed = speed,
            trackId = trackId,
            contentMode = contentMode,
            renderMode = renderMode,
            useHardwareDecode = useHardwareDecode,
            cacheSizeMb = cacheSizeMb,
            engine = engine,
            level = level,
            previewRequestId = previewRequestId,
        )
        commandState = next
    }
}

private object OhosKeyCode {
    const val Left = 2014
    const val Right = 2015
    const val Space = 2050
    const val Enter = 2054
    const val Escape = 2070
}

/** Stable ArkTS -> Kotlin low-frequency event entry point. */
@OptIn(ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@CName("UpdateOhosPlayerState")
fun UpdateOhosPlayerState(payload: CPointer<ByteVar>?): Boolean {
    val raw = payload?.toKString() ?: return false
    return OhosPlayerHostState.report(raw)
}

/** Copies a bounded frame before returning; no N-API buffer or native pointer is retained. */
@OptIn(ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@CName("UpdateOhosPlayerBackdrop")
fun UpdateOhosPlayerBackdrop(handle: Long, revision: Long, width: Int, height: Int,
    alphaType: Int, pixels: CPointer<ByteVar>?, byteCount: Int): Boolean {
    if (!OhosPlayerHostState.acceptsBackdrop(handle, revision) || pixels == null ||
        width !in 1..256 || height !in 1..256 || byteCount != width * height * 4) return false
    return try {
        val alpha = when (alphaType) {
            1 -> ColorAlphaType.OPAQUE
            2 -> ColorAlphaType.PREMUL
            3 -> ColorAlphaType.UNPREMUL
            else -> return false
        }
        val info = ImageInfo(width, height, ColorType.BGRA_8888, alpha)
        val image = Image.makeRaster(info, pixels.readBytes(byteCount), width * 4)
        try { ohosVideoBackdropSource.frame = image.toComposeImageBitmap() } finally { image.close() }
        true
    } catch (_: Exception) { false }
}

/** HarmonyOS host fallback so a system back gesture can never finish the app while the player is open. */
@OptIn(ExperimentalNativeApi::class)
@CName("HandleOhosPlayerBack")
fun HandleOhosPlayerBack(): Boolean = OhosPlayerHostState.handleSystemBack()

/** ArkUI host fallback for physical PC keyboard input. */
@OptIn(ExperimentalNativeApi::class)
@CName("HandleOhosPlayerKey")
fun HandleOhosPlayerKey(keyCode: Int, action: Int): Boolean =
    OhosPlayerHostState.handleKey(keyCode, action)

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
        audioOnly: Boolean,
        startPositionMs: Long,
        preferences: PlayerPlaybackPreferences,
    ) {
        logOhosPlayer("bridge loadUrl handle=$handle endpoint=${url.safeEndpoint()}")
        OhosPlayerHostState.load(
            handle,
            url,
            headers,
            subtitleUrl,
            allowInsecureTls,
            multiConnectionAllowed,
            audioOnly,
            startPositionMs,
            preferences,
        )
    }
    override fun play(handle: Long, pendingSeekPositionMs: Long?) =
        OhosPlayerHostState.play(handle, pendingSeekPositionMs)
    override fun pause(handle: Long) = OhosPlayerHostState.pause(handle)
    override fun stop(handle: Long) = OhosPlayerHostState.stop(handle)
    override fun seek(handle: Long, positionMs: Long) = OhosPlayerHostState.seek(handle, positionMs)
    override suspend fun previewFrame(handle: Long, positionMs: Long): PlayerSeekPreviewFrame? =
        OhosPlayerHostState.previewFrame(handle, positionMs)
    override fun setSpeed(handle: Long, speed: Float) = OhosPlayerHostState.speed(handle, speed)
    override fun selectTrack(handle: Long, kind: TrackKind, id: String?) =
        OhosPlayerHostState.selectTrack(handle, kind, id)
    override fun setContentMode(handle: Long, mode: PlayerContentMode) =
        OhosPlayerHostState.contentMode(handle, mode)
    override fun setRenderMode(mode: PlayerRenderMode) = OhosPlayerHostState.renderMode(mode)
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
        isoDirectPlay = true,
        playbackPreferences = true,
        engineSelection = true,
    ),
    bridge = ComposeOhosPlayerBridge(),
)

/**
 * HarmonyOS keeps only mfplayer's native video surface in ArkUI. All visible
 * chrome, gestures and menus remain in the shared CMP player above this node.
 */
@Composable
@OptIn(ExperimentalEncodingApi::class)
fun OhosPlayerSurface(
    controller: PlayerController,
    onBack: () -> Unit,
    onExitFullscreen: () -> Unit,
    fullscreen: Boolean,
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
    val latestSystemActions by rememberUpdatedState(LocalPlatformPlaybackActions.current)
    val artworkGeneration = latestSystemActions?.generation
    val artworkMediaId = latestSystemActions?.mediaId
    val artworkLoader = latestSystemActions?.loadArtwork
    var artworkData by remember(artworkGeneration, artworkMediaId, artworkLoader) { mutableStateOf("") }
    LaunchedEffect(artworkGeneration, artworkMediaId, artworkLoader) {
        if (artworkGeneration == null || artworkLoader == null) return@LaunchedEffect
        try {
            val bytes = artworkLoader() ?: return@LaunchedEffect
            if (controller.state.value.generation === artworkGeneration && latestSystemActions?.mediaId == artworkMediaId) {
                artworkData = Base64.encode(bytes)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { logOhosPlayer("system artwork unavailable") }
    }

    DisposableEffect(controller) {
        val handler: (String, Double) -> Unit = handler@ { action, value ->
            val actions = latestSystemActions ?: return@handler
            if (actions.generation == null || actions.generation !== controller.state.value.generation) return@handler
            when (action) {
                "play" -> actions.onPlay()
                "pause" -> actions.onPause()
                "stop" -> actions.onStop()
                "seek" -> if (value >= 0) actions.onSeek(value.toLong())
                "speed" -> if (value > 0 && value <= 4) actions.onSpeed(value.toFloat())
                "next" -> actions.onNext?.invoke()
                "previous" -> actions.onPrevious?.invoke()
            }
        }
        OhosPlayerHostState.bindSystemActions(handler)
        onDispose { OhosPlayerHostState.unbindSystemActions(handler) }
    }

    DisposableEffect(controller, surfaceHandle) {
        scope.launch { runCatching { controller.attachSurface(surfaceHandle) } }
        onDispose {
            scope.launch { runCatching { controller.detachSurface(surfaceHandle) } }
        }
    }

    BoxWithConstraints(modifier) {
        // A landscape window is not necessarily fullscreen (notably on a
        // tablet in freeform mode). The shared player owns the explicit
        // fullscreen state, so back must use that state instead of guessing
        // from the current bounds.
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
            parameter = command.toJsParameter(latestSystemActions, artworkData),
            background = Color.Black,
            interactive = false,
        )
    }
}

private fun OhosPlayerCommandState.toJsParameter(actions: PlatformPlaybackActions?, artworkData: String): JsObject {
    val headerValues = JsArray(JsEnv.createArray(headers.size))
    headers.forEachIndexed { index, value -> headerValues[index] = value.nApiValue() }
    return js {
        "commandId"(commandId.nApiValue())
        "backdropRevision"(ohosVideoBackdropRevision.nApiValue())
        "backdropSampling"(ohosVideoBackdropSource.sampling)
        "handle"(handle.nApiValue())
        "action"(action)
        "mediaId"(actions?.mediaId.orEmpty())
        "title"(actions?.title.orEmpty())
        "artworkData"(artworkData)
        "canNext"(actions?.onNext != null)
        "canPrevious"(actions?.onPrevious != null)
        "url"(url ?: "")
        "headers"(headerValues.jsValue)
        "subtitleUrl"(subtitleUrl ?: "")
        "allowInsecureTls"(allowInsecureTls)
        "multiConnectionAllowed"(multiConnectionAllowed)
        "audioOnly"(audioOnly)
        "positionMs"(positionMs.nApiValue())
        "speed"(speed)
        "trackId"(trackId)
        "contentMode"(contentMode)
        "renderMode"(renderMode)
        "useHardwareDecode"(useHardwareDecode)
        "cacheSizeMb"(cacheSizeMb)
        "engine"(engine)
        "level"(level)
        "previewRequestId"(previewRequestId.nApiValue())
    }
}

private fun decodeBase64(value: String): ByteArray? {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val table = IntArray(256) { -1 }
    alphabet.forEachIndexed { index, char -> table[char.code] = index }
    val clean = value.filterNot { it == '=' || it.isWhitespace() }
    if (clean.isEmpty() || clean.length % 4 == 1) return null
    val output = ByteArray((clean.length * 6) / 8)
    var accumulator = 0
    var bits = 0
    var offset = 0
    for (char in clean) {
        val digit = char.code.takeIf { it < table.size }?.let { table[it] } ?: return null
        if (digit < 0) return null
        accumulator = (accumulator shl 6) or digit
        bits += 6
        if (bits >= 8) {
            bits -= 8
            output[offset++] = (accumulator shr bits).toByte()
        }
    }
    return output.copyOf(offset)
}

/**
 * 播放页竖向手势的亮度/音量实现：值由 ArkTS 推送，写入回给 ArkUI 宿主执行。
 * 读不到（ArkTS 没上报）时返回 null / 0，手势那一侧就不介入。
 */
internal object OhosPlayerWindowControls : cn.mafei.cmp.player.api.PlayerWindowControls {
    override val pictureInPictureSupported = true
    override fun pictureInPicture() = OhosPlayerHostState.pictureInPicture()
    override val castingSupported = true
    override fun casting() = OhosPlayerHostState.casting()
}

internal object OhosDeviceControls : PlayerDeviceControls {
    override fun brightness(): Float? = OhosPlayerHostState.brightnessValue
    override fun setBrightness(value: Float) = OhosPlayerHostState.setDeviceBrightness(value)
    override fun volume(): Int? = OhosPlayerHostState.volumeValue
    override fun maxVolume(): Int = OhosPlayerHostState.maxVolumeValue
    override fun setVolume(steps: Int) = OhosPlayerHostState.setDeviceVolume(steps)
    override fun refresh() = OhosPlayerHostState.requestDeviceState()
}
