package cn.mafei.cmp.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.MaFeiErrorDialog
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog
import cn.mafei.cmp.common.ui.LegacyTransientToast
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerContentMode
import cn.mafei.cmp.player.api.PlayerDeviceControls
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.TrackKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong

private const val PlayerAutoHideDelayMs = 4_000L

private const val PlayerLongPressSpeed = 3f

private const val PlayerControlTransitionDurationMs = 180

/** Legacy `PLAYER_CLOCK_INTERVAL_MS`. */
private const val PlayerClockIntervalMs = 30_000L

/**
 * CMP translation of the legacy ArkUI `PlayerChrome`.
 *
 * The video surface is the full-screen first layer. Controls retain the old
 * black masks, persistent portrait back button, 4-second auto hide,
 * landscape lock, 4dp timeline and runtime-backed speed/track actions.
 * Unsupported platform capabilities stay absent instead of being faked.
 */
@Composable
fun NowPlayingScreen(
    media: NowPlayingMedia,
    state: PlayerState,
    tracks: List<PlayerTrack> = state.tracks,
    capabilities: PlayerCapabilities,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onSelectTrack: (TrackKind, String?) -> Unit,
    preparing: Boolean = false,
    externalError: String? = null,
    videoSurface: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Explicit target: windowed hosts enter actual system fullscreen; ordinary
     * mobile hosts retain their player orientation policy. Window fill never
     * invokes this callback.
     */
    onFullscreenRequest: ((Boolean) -> Unit)? = null,
    /**
     * 是否处于全屏（旧版 `AppUIState.isFullScreen`：一个**独立状态**，不是「窗口宽 > 高」）。
     *
     * 用窗口横竖推导会出事：横屏平板本来宽就大于高，于是永远判成全屏 —— 返回键每次都只
     * 执行「退出全屏」，而平板退出全屏并不会变竖屏，结果**退不出播放页**。全屏与否只能由
     * 这一层显式持有（`App.kt` 的 `playerFullscreen`），宿主再据此旋转/隐藏系统栏。
     */
    fullscreen: Boolean = false,
    /** 播放队列（旧版 PlayerChrome 的 playList/currentPlayId）：选集面板与上一集/下一集。 */
    episodes: List<cn.mafei.cmp.core.domain.MediaItem> = emptyList(),
    episodeIndex: Int = 0,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onSelectEpisode: ((Int) -> Unit)? = null,
    /** 清晰度档位（旧版 QualityOption）：空 = 该provider不提供档位，按钮与面板都不出现。 */
    qualityOptions: List<PlayerQualityOption> = emptyList(),
    onSelectQuality: ((Int) -> Unit)? = null,
    /** 「播放信息」卡片的来源事实；null = 该条目没有可展示的技术信息，入口不出现。 */
    playbackFacts: PlayerPlaybackFacts? = null,
    onSelectContentMode: ((PlayerContentMode) -> Unit)? = null,
    onPictureInPicture: (() -> Unit)? = null,
    onCasting: (() -> Unit)? = null,
    danmaku: List<cn.mafei.cmp.player.api.DanmakuEntry> = emptyList(),
    /**
     * 平台亮度/音量（旧版左侧调亮度、右侧调音量）。默认不支持：两条竖向手势都不介入，
     * 也不会画出「改了但没生效」的反馈条。
     */
    deviceControls: PlayerDeviceControls =
        PlayerDeviceControls.Unsupported,
    /** Loads a real provider frame while the seek bar is being dragged. */
    loadSeekPreview: (suspend (Long) -> PlayerSeekPreviewFrame?)? = null,
    /** Host chrome is composed only in normal windowed playback. */
    hostWindowControls: @Composable RowScope.() -> Unit = {},
    hostWindowDragArea: @Composable (@Composable () -> Unit) -> Unit = { it() },
    onSubmitContentReport: PlayerContentReportSubmitter? = null,
) {
    val nativeBackdrop = LocalPlayerVideoBackdropSource.current
    val currentVideoMode by rememberUpdatedState(state.contentMode)
    val currentVideoArea by rememberUpdatedState(playerVideoArea(
        state.contentMode, playbackFacts?.video?.widthPx, playbackFacts?.video?.heightPx,
    ))
    val nativeRecorder: (ContentDrawScope.() -> Unit)? = remember(nativeBackdrop) {
        nativeBackdrop?.let { source ->
            {
                val frame = source.frame
                if (frame == null) {
                    drawContent()
                } else {
                    drawRect(Color.Black)
                    val area = currentVideoArea
                    val target = when {
                        area.oneToOnePixels != null -> Size(
                            area.oneToOnePixels.width.toFloat().coerceAtMost(size.width),
                            area.oneToOnePixels.height.toFloat().coerceAtMost(size.height),
                        )
                        area.aspectRatio != null -> {
                            val ratio = area.aspectRatio
                            if (size.width / size.height > ratio) Size(size.height * ratio, size.height)
                            else Size(size.width, size.width / ratio)
                        }
                        else -> size
                    }
                    val scale = if (currentVideoMode.fills) maxOf(target.width / frame.width, target.height / frame.height)
                        else minOf(target.width / frame.width, target.height / frame.height)
                    val width = (frame.width * scale).toInt().coerceAtLeast(1)
                    val height = (frame.height * scale).toInt().coerceAtLeast(1)
                    clipRect((size.width - target.width) / 2, (size.height - target.height) / 2,
                        (size.width + target.width) / 2, (size.height + target.height) / 2) {
                        drawImage(frame, dstOffset = IntOffset(((size.width - width) / 2).toInt(), ((size.height - height) / 2).toInt()),
                            dstSize = IntSize(width, height))
                    }
                }
            }
        }
    }
    val backdrop = rememberGlassBackdrop(nativeRecorder)
    val localCapabilities = if (state.casting) capabilities.copy(trackSelection = false, contentMode = false) else capabilities
    val availableQualities = if (state.casting) emptyList() else qualityOptions
    val presentation = state.toNowPlayingPresentation(if (media.live) capabilities.copy(seek = false) else capabilities)
    val safeArea = currentMaFeiSafeArea()
    val reportSubmitter = onSubmitContentReport ?: LocalPlayerContentReportSubmitter.current
    val hostFeatures = LocalMaFeiWindowFeatures.current
    val windowedController = hostFeatures.windowedHost
    val latestTogglePlay by rememberUpdatedState(onTogglePlay)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestSpeed by rememberUpdatedState(onSpeed)
    val latestPlaybackSpeed by rememberUpdatedState(state.speed)
    val latestFullscreen by rememberUpdatedState(fullscreen)
    val latestFullscreenRequest by rememberUpdatedState(onFullscreenRequest)
    val latestPresentation by rememberUpdatedState(presentation)
    val animationScope = rememberCoroutineScope()
    val playerFocusRequester = remember { FocusRequester() }

    var controlsVisible by remember(media.id, state.generation) { mutableStateOf(true) }
    var locked by remember(media.id, state.generation) { mutableStateOf(false) }
    var danmakuEnabled by remember(media.id) { mutableStateOf(false) }
    var panel by remember(media.id, state.generation) { mutableStateOf(PlayerChromePanel.None) }
    val panelAnchors = remember(media.id, state.generation) { mutableStateMapOf<PlayerChromePanel, Rect>() }
    var panelAnchor by remember(media.id, state.generation) { mutableStateOf<Rect?>(null) }
    var sceneOrigin by remember { mutableStateOf(Offset.Zero) }
    var panelContainerVisible by remember(media.id, state.generation) { mutableStateOf(false) }
    var panelClosing by remember(media.id, state.generation) { mutableStateOf(false) }
    var panelAnimationToken by remember(media.id, state.generation) { mutableIntStateOf(0) }
    var openPlaybackInfoAfterPanelClose by remember(media.id, state.generation) { mutableStateOf(false) }
    var autoHideGeneration by remember(media.id, state.generation) { mutableIntStateOf(0) }
    var sliderPositionMs by remember(media.id, state.generation) { mutableLongStateOf(state.positionMs) }
    var draggingSlider by remember(media.id, state.generation) { mutableStateOf(false) }
    var seekFeedbackMs by remember(media.id, state.generation) { mutableStateOf<Long?>(null) }
    var seekPreview by remember(media.id, state.generation) { mutableStateOf<PlayerSeekPreviewFrame?>(null) }
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    var longPressSpeedActive by remember(media.id, state.generation) { mutableStateOf(false) }
    var speedBeforeLongPress by remember(media.id, state.generation) { mutableFloatStateOf(1f) }
    var busyVisible by remember(media.id, state.generation) { mutableStateOf(false) }
    var lastPointerActivityMs by remember(media.id, state.generation) { mutableLongStateOf(0L) }
    var mouseInput by remember { mutableStateOf(false) }
    var windowFullscreen by remember(media.id) { mutableStateOf(false) }
    val controllerInteractions = remember { MutableInteractionSource() }
    val controlsHovered by controllerInteractions.collectIsHoveredAsState()
    // 竖向手势的亮度/音量反馈（旧版 PlayerGestureFeedback.Brightness/Volume）。
    var levelFeedback by remember(media.id, state.generation) { mutableStateOf<PlayerGestureFeedback.Level?>(null) }
    // 播放信息卡片是独立于侧栏面板的一层（旧版 isShowPlayBackInfo）：它盖住画面、
    // 清掉控件，并且参与返回层级。
    var playbackInfoVisible by remember(media.id, state.generation) { mutableStateOf(false) }
    var reportComposerVisible by remember(media.id, state.generation) { mutableStateOf(false) }
    var reportDescription by remember(media.id, state.generation) { mutableStateOf("") }
    var reportSubmitting by remember(media.id, state.generation) { mutableStateOf(false) }
    var reportFailure by remember(media.id, state.generation) { mutableStateOf<String?>(null) }
    var reportNotice by remember(media.id, state.generation) { mutableStateOf<String?>(null) }
    // 旧版 30 秒刷新的状态区时钟（只在全屏行里渲染）。
    var clockText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            clockText = cn.mafei.cmp.common.core.formatLocalClockTime(
                cn.mafei.cmp.common.core.SystemEpochClock.nowEpochMs(),
            )
            delay(PlayerClockIntervalMs)
        }
    }

    LaunchedEffect(media.id) {
        // 平台状态（亮度/音量）在部分平台靠事件推送：进页面先要一次，手势才有基准值。
        deviceControls.refresh()
    }

    LaunchedEffect(state.positionMs, draggingSlider) {
        if (!draggingSlider) sliderPositionMs = state.positionMs.coerceAtLeast(0L)
    }
    val previewLoader by rememberUpdatedState(loadSeekPreview)
    val previewBucket = sliderPositionMs / 250L
    LaunchedEffect(media.id, draggingSlider, previewBucket) {
        if (!draggingSlider || previewLoader == null) {
            if (!draggingSlider) seekPreview = null
            return@LaunchedEffect
        }
        delay(90)
        seekPreview = try {
            previewLoader?.invoke(sliderPositionMs)
        } catch (cancelled: CancellationException) {
            // A newer drag bucket superseded this request. Preserve structured
            // cancellation so stale network work cannot publish a frame.
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }
    LaunchedEffect(controlsVisible, panel, locked, state.phase, autoHideGeneration, controlsHovered, draggingSlider) {
        if (controlsVisible && panel == PlayerChromePanel.None && !locked &&
            !controlsHovered && !draggingSlider && state.phase == PlayerPhase.Playing) {
            delay(PlayerAutoHideDelayMs)
            controlsVisible = false
        }
    }
    LaunchedEffect(preparing, presentation.isBusy, state.generation) {
        if (preparing || presentation.isBusy) {
            // Avoid flashing a spinner when prepare/buffer completes inside one
            // or two frames; longer waits still receive a deliberate entrance.
            delay(160)
            busyVisible = true
        } else {
            busyVisible = false
        }
    }

    fun showControlsTemporarily() {
        if (!locked) {
            controlsVisible = true
            autoHideGeneration++
        }
    }

    fun openPanel(next: PlayerChromePanel) {
        if (next == PlayerChromePanel.None) return
        if (state.casting && next in listOf(PlayerChromePanel.Audio, PlayerChromePanel.Subtitle,
                PlayerChromePanel.Quality, PlayerChromePanel.AspectRatio)) return
        panelAnimationToken++
        panelAnchor = panelAnchors[next] ?: panelAnchor
        panel = next
        panelClosing = false
        panelContainerVisible = true
        controlsVisible = false
    }

    fun closePanel(openPlaybackInfo: Boolean = false) {
        if (panel == PlayerChromePanel.None || panelClosing) return
        openPlaybackInfoAfterPanelClose = openPlaybackInfo
        panelClosing = true
        panelAnimationToken++
        val token = panelAnimationToken
        val closingPanel = panel
        val contentExitMs = playerPanelContentExitDurationMs(
            panel = closingPanel,
            tracks = tracks,
            qualityOptions = qualityOptions,
            showAspectRatio = capabilities.contentMode,
            showPlaybackInfo = playbackFacts != null,
            showContentReport = reportSubmitter != null,
        )
        animationScope.launch {
            delay(contentExitMs)
            if (token != panelAnimationToken) return@launch
            panelContainerVisible = false
            delay(PlayerControlTransitionDurationMs.toLong())
            if (token != panelAnimationToken) return@launch
            panel = PlayerChromePanel.None
            panelClosing = false
            if (openPlaybackInfoAfterPanelClose) {
                openPlaybackInfoAfterPanelClose = false
                playbackInfoVisible = true
                controlsVisible = false
            } else {
                showControlsTemporarily()
            }
        }
    }

    fun submitContentReport() {
        val submit = reportSubmitter ?: return
        val description = reportDescription.trim().take(100)
        if (description.isBlank() || reportSubmitting) return
        reportSubmitting = true
        reportFailure = null
        animationScope.launch {
            try {
                val result = submit(
                    PlayerContentReportRequest(
                        title = media.title,
                        itemId = media.id,
                        description = description,
                    ),
                )
                when {
                    result == null -> reportFailure = "举报提交失败，请稍后重试"
                    result.submitted || result.alreadyReported -> {
                        reportComposerVisible = false
                        reportDescription = ""
                        reportNotice = result.message
                    }
                    else -> reportFailure = result.message
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                reportFailure = "举报提交失败，请检查网络后重试"
            } finally {
                reportSubmitting = false
            }
        }
    }

    val materialVisible = !state.pictureInPicture &&
        (panel != PlayerChromePanel.None || locked || controlsVisible || (!fullscreen && !windowedController))
    DisposableEffect(nativeBackdrop, materialVisible, media.id, state.generation) {
        nativeBackdrop?.sampling = materialVisible
        onDispose { nativeBackdrop?.sampling = false }
    }

    LaunchedEffect(state.pictureInPicture) {
        if (state.pictureInPicture) {
            if (windowedController && latestFullscreen) latestFullscreenRequest?.invoke(false)
            panelAnimationToken++
            panel = PlayerChromePanel.None
            panelContainerVisible = false
            panelClosing = false
            openPlaybackInfoAfterPanelClose = false
            playbackInfoVisible = false
            locked = false
            controlsVisible = false
        } else {
            showControlsTemporarily()
        }
    }

    val durationMs = state.durationMs?.takeIf { it > 0L }
    val requestFullscreen = onFullscreenRequest
    val handleBack: () -> Unit = {
        if (reportComposerVisible) {
            if (!reportSubmitting) reportComposerVisible = false
        } else {
            when (playerBackStep(panel != PlayerChromePanel.None, playbackInfoVisible,
                fullscreen, requestFullscreen != null, windowedController && windowFullscreen)) {
                PlayerBackStep.ClosePanel -> closePanel()
                PlayerBackStep.ClosePlaybackInfo -> { playbackInfoVisible = false; showControlsTemporarily() }
                PlayerBackStep.ExitFullscreen -> requestFullscreen?.invoke(false)
                PlayerBackStep.ExitWindowFullscreen -> windowFullscreen = false
                PlayerBackStep.LeavePlayer -> onBack()
            }
        }
    }
    fun fillWindow() {
        if (fullscreen) requestFullscreen?.invoke(false)
        windowFullscreen = if (fullscreen) true else !windowFullscreen
        showControlsTemporarily()
    }
    fun adjustVolume(delta: Int) {
        val max = deviceControls.maxVolume()
        val volume = deviceControls.volume() ?: return
        if (max > 0) deviceControls.setVolume((volume + delta).coerceIn(0, max))
        showControlsTemporarily()
    }
    BoxWithConstraints(
        modifier.background(Color.Black).playerKeyboardControls(
            onBack = handleBack,
            onTogglePlay = { if (!locked) { latestTogglePlay(); showControlsTemporarily() } },
            onSeekBy = if (!locked && presentation.canSeek && durationMs != null) {
                { delta -> latestSeek((state.positionMs + delta).coerceIn(0L, durationMs)); showControlsTemporarily() }
            } else null,
            onFullscreen = if (!locked && requestFullscreen != null) {{ requestFullscreen(!fullscreen) }} else null,
            onWindowFullscreen = if (!locked && windowedController) ::fillWindow else null,
            onVolumeBy = if (!locked && deviceControls.maxVolume() > 0) ::adjustVolume else null,
            onMute = if (!locked && deviceControls.maxVolume() > 0) {{
                val volume = deviceControls.volume() ?: 0
                deviceControls.setVolume(if (volume > 0) 0 else (deviceControls.maxVolume() / 2).coerceAtLeast(1))
                showControlsTemporarily()
            }} else null,
        ),
    ) {
        val immersive = fullscreen || (windowedController && windowFullscreen)
        val windowHeaderVisible = windowedController && !immersive
        val stackedNativeChrome = maxWidth < 700.dp && hostFeatures.windowControlTopInsetDp > 0f
        val headerContentTop = safeArea.top + if (stackedNativeChrome) hostFeatures.windowControlTopInsetDp.dp else 0.dp
        val windowHeaderHeight = if (windowHeaderVisible) headerContentTop + 52.dp else 0.dp
        val viewportTop = when {
            windowHeaderVisible -> windowHeaderHeight
            windowedController && !fullscreen -> safeArea.top + hostFeatures.windowControlTopInsetDp.dp
            else -> 0.dp
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = viewportTop)
            .onSizeChanged { sceneSize = it }.onGloballyPositioned { sceneOrigin = it.positionInRoot() }) {
        // The player used to switch to its richer control set only after the
        // explicit fullscreen request. On tablets and desktop the player page
        // already owns the whole window, so that left the wide layout with the
        // phone's minimal play/progress row and hid tracks, episodes, quality,
        // speed and the previous-item action. Keep the breakpoint geometric:
        // the same code works for a tablet, a foldable inner display, and a
        // resizable Mac window without checking a device or platform name.
        val largeWindow = useWideNowPlayingLayout(maxWidth.value, maxHeight.value)
        // Normal tablets use this control to rotate the player; desktop hosts
        // keep a real window-fullscreen action.
        val windowFeatures = LocalMaFeiWindowFeatures.current
        val windowControlTopInset = if (windowFeatures.windowedHost && !windowHeaderVisible && !immersive) {
            windowFeatures.windowControlTopInsetDp.dp
        } else {
            0.dp
        }
        val tabletWindow = !windowFeatures.desktopWindow && !windowFeatures.windowedHost &&
            minOf(maxWidth.value, maxHeight.value) >= 600f
        val landscapeWindow = maxWidth.value > maxHeight.value
        val anchoredMenus = windowedController || largeWindow && (tabletWindow || windowFeatures.desktopWindow ||
            windowFeatures.windowedHost || minOf(maxWidth.value, maxHeight.value) >= 600f)

        // Legacy PlayerPage.onBackPress priority: close the open menu/panel first,
        // then the playback-info card (the old page reports it as menu-visible
        // too), then leave fullscreen, and only then pop the player. Every back
        // entry (system back and both on-screen back buttons) must share this
        // ladder — route the chrome buttons through it too, or they shortcut layers.

        // The video AREA: the surface fills whatever shape the current ratio
        // asks for, and the page (black) shows through around it. 原始比例 uses
        // the source's 1:1 pixel size; the ratio modes the largest fitting
        // rectangle; everything else fills the page. The native side only ever
        // hears "crop or letterbox" (state.contentMode.fills).
        val videoArea = playerVideoArea(
            mode = state.contentMode,
            sourceWidthPx = playbackFacts?.video?.widthPx,
            sourceHeightPx = playbackFacts?.video?.heightPx,
        )
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val areaModifier = when {
            videoArea.oneToOnePixels != null -> Modifier.size(
                (videoArea.oneToOnePixels.width / density).dp,
                (videoArea.oneToOnePixels.height / density).dp,
            )
            videoArea.aspectRatio != null -> {
                val ratio = videoArea.aspectRatio
                if (maxWidth / maxHeight > ratio) Modifier.fillMaxHeight().aspectRatio(ratio)
                else Modifier.fillMaxWidth().aspectRatio(ratio)
            }
            else -> Modifier.fillMaxSize()
        }
        Box(
            Modifier.fillMaxSize().clipToBounds().captureGlassBackdrop(backdrop),
            contentAlignment = Alignment.Center,
        ) {
            Box(areaModifier) {
                videoSurface(Modifier.fillMaxSize())
                if (state.subtitleText.isNotBlank()) {
                    Text(
                        state.subtitleText,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = if (controlsVisible) 76.dp else 18.dp),
                        color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = if (fullscreen) 22.sp else 17.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 5f),
                        ),
                    )
                }
                if (danmakuEnabled && danmaku.isNotEmpty() && state.engineType == cn.mafei.cmp.player.api.PlayerEngineType.System) {
                    DanmakuOverlay(danmaku, state, fullscreen, Modifier.fillMaxSize().clipToBounds())
                }
            }
        }

        // The native PiP window owns its controls; only the video remains here.
        if (state.pictureInPicture) return@BoxWithConstraints
        PlayerNavigationBackHandler(handleBack)

        Box(
            Modifier.fillMaxSize()
                .focusRequester(playerFocusRequester)
                .focusable()
                .testTag("playerInputSurface")
                .pointerInput(media.id, state.generation, locked) {
                    // A desktop pointer should bring the chrome back without
                    // requiring a click. Touch and drag gestures keep their
                    // existing handlers below; this observer never consumes.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                mouseInput = event.changes.any { it.type == PointerType.Mouse }
                            }
                            if (event.type == PointerEventType.Move && !locked && event.changes.any { it.type == PointerType.Mouse }) {
                                val now = cn.mafei.cmp.common.core.SystemEpochClock.nowEpochMs()
                                if (now - lastPointerActivityMs >= 250L) {
                                    lastPointerActivityMs = now
                                    showControlsTemporarily()
                                }
                            }
                        }
                    }
                }
                .pointerInput(media.id, state.generation, locked) {
                    detectTapGestures(
                        onTap = {
                            if (!locked) {
                                if (mouseInput) {
                                    latestTogglePlay()
                                    showControlsTemporarily()
                                } else {
                                    controlsVisible = !controlsVisible
                                    if (controlsVisible) autoHideGeneration++
                                }
                            }
                        },
                        onDoubleTap = {
                            if (!locked) {
                                if (mouseInput && latestFullscreenRequest != null) latestFullscreenRequest?.invoke(!latestFullscreen)
                                else latestTogglePlay()
                                showControlsTemporarily()
                            }
                        },
                        onLongPress = {
                            if (!mouseInput && !locked && latestPresentation.isPlaying && capabilities.speed) {
                                speedBeforeLongPress = latestPlaybackSpeed
                                longPressSpeedActive = true
                                latestSpeed(PlayerLongPressSpeed)
                                controlsVisible = false
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (longPressSpeedActive) {
                                longPressSpeedActive = false
                                latestSpeed(speedBeforeLongPress)
                                showControlsTemporarily()
                            }
                        },
                    )
                }
                .pointerInput(media.id, state.generation, durationMs, locked, sceneSize) {
                    if (locked || sceneSize.width <= 0) return@pointerInput
                    val total = durationMs?.takeIf { it > 0L }
                    var accumulatedX = 0f
                    var accumulatedY = 0f
                    var dragAxis = 0
                    var startPosition = 0L
                    // 竖向手势的基准值在按下时取一次（旧版 onVerticalStart 同做法）：亮度按
                    // 「窗口亮度 -1 时用 0.5」兜底，音量取当时的步数。
                    var level: PlayerGestureLevel? = null
                    var startBrightness = PlayerDeviceControls.DEFAULT_BRIGHTNESS
                    var startVolume = 0
                    var maxVolume = 0
                    detectDragGestures(
                        onDragStart = dragStart@ { offset ->
                            if (mouseInput) return@dragStart
                            accumulatedX = 0f
                            accumulatedY = 0f
                            dragAxis = 0
                            startPosition = (state.positionMs).let { if (total != null) it.coerceIn(0L, total) else it.coerceAtLeast(0L) }
                            startBrightness = deviceControls.brightness()
                                ?: PlayerDeviceControls.DEFAULT_BRIGHTNESS
                            maxVolume = deviceControls.maxVolume()
                            startVolume = deviceControls.volume() ?: 0
                            // 平台值可能是事件推上来的：先要一次，让下一次手势拿到最新值。
                            deviceControls.refresh()
                            // 竖向只服务旧版那两条：左半屏=亮度、右半屏=音量。对不上的平台
                            // 直接不介入 —— 宁可不响应，也不画一条改了没效果的反馈。
                            val side = playerGestureLevelFor(offset.x, sceneSize.width.toFloat())
                            level = when (side) {
                                PlayerGestureLevel.Brightness ->
                                    if (deviceControls.brightness() != null) side else null
                                PlayerGestureLevel.Volume -> if (maxVolume > 0) side else null
                            }
                        },
                        onDrag = drag@ { change, dragAmount ->
                            if (change.type == PointerType.Mouse) return@drag
                            accumulatedX += dragAmount.x
                            accumulatedY += dragAmount.y
                            if (dragAxis == 0 && maxOf(abs(accumulatedX), abs(accumulatedY)) >= 4f) {
                                dragAxis = if (abs(accumulatedX) >= abs(accumulatedY)) 1 else 2
                                if (dragAxis == 2 && level == null) dragAxis = 3 // 竖向不可用时整条轴作废
                            }
                            if (dragAxis == 1 && total != null) {
                                change.consume()
                                val delta = total.toDouble() * accumulatedX / sceneSize.width.toDouble()
                                seekFeedbackMs = (startPosition + delta.roundToLong()).coerceIn(0L, total)
                            } else if (dragAxis == 2 && level != null) {
                                change.consume()
                                // 旧版竖直比例 = 手指位移 / 画面高度，向上为增。
                                val fraction = -accumulatedY / sceneSize.height.toFloat()
                                when (level) {
                                    PlayerGestureLevel.Brightness -> {
                                        val value = playerGestureBrightness(startBrightness, fraction)
                                        deviceControls.setBrightness(value)
                                        levelFeedback = PlayerGestureFeedback.Level(
                                            PlayerGestureLevel.Brightness,
                                            playerGestureLevelProgress(
                                                PlayerGestureLevel.Brightness, value, 0, 0,
                                            ),
                                        )
                                    }
                                    PlayerGestureLevel.Volume -> {
                                        val steps =
                                            playerGestureVolumeSteps(startVolume, maxVolume, fraction)
                                        deviceControls.setVolume(steps)
                                        levelFeedback = PlayerGestureFeedback.Level(
                                            PlayerGestureLevel.Volume,
                                            playerGestureLevelProgress(
                                                PlayerGestureLevel.Volume, 0f, steps, maxVolume,
                                            ),
                                        )
                                    }
                                    null -> Unit
                                }
                            }
                        },
                        onDragCancel = {
                            seekFeedbackMs = null
                            levelFeedback = null
                        },
                        onDragEnd = {
                            if (dragAxis == 1) seekFeedbackMs?.let(latestSeek)
                            if (dragAxis == 1) showControlsTemporarily()
                            seekFeedbackMs = null
                            levelFeedback = null
                            deviceControls.refresh()
                        },
                    )
                },
        )


        AnimatedVisibility(
            visible = !windowHeaderVisible && (controlsVisible || (anchoredMenus && panel != PlayerChromePanel.None)) && !locked,
            enter = fadeIn(tween(PlayerControlTransitionDurationMs)) +
                slideInVertically(tween(PlayerControlTransitionDurationMs)) { -24 },
            exit = fadeOut(tween(PlayerControlTransitionDurationMs)) +
                slideOutVertically(tween(PlayerControlTransitionDurationMs)) { -24 },
            modifier = Modifier.align(Alignment.TopCenter).hoverable(controllerInteractions),
        ) {
            PlayerTopChrome(
                title = media.title,
                fullscreen = immersive,
                largeWindow = largeWindow,
                safeStart = safeArea.start,
                safeTop = safeArea.top,
                safeEnd = safeArea.end,
                backdrop = backdrop,
                clockText = clockText,
                // Show 「更多」 only when this host supplies at least one action.
                showMenu = localCapabilities.contentMode || playbackFacts != null || reportSubmitter != null,
                onBack = handleBack,
                onMenu = { openPanel(PlayerChromePanel.More) },
                onMenuBounds = { panelAnchors[PlayerChromePanel.More] = it },
                onPictureInPicture = onPictureInPicture,
                onCasting = onCasting,
            )
        }

        // The non-fullscreen back control must follow the top chrome in hit-test
        // order: its full-width hover observer otherwise covers this button.
        if (!immersive && !windowHeaderVisible && !locked && (panel == PlayerChromePanel.None || anchoredMenus)) {
            PlayerChromeButton(
                glyph = null,
                label = "返回",
                diameter = 44,
                iconSize = 22,
                backdrop = backdrop,
                onClick = handleBack,
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(
                        start = 12.dp + safeArea.start,
                        top = 10.dp + safeArea.top + windowControlTopInset,
                    ),
            ) {
                MaFeiIcon(MaFeiGlyph.Back, Modifier.size(22.dp), Color.White)
            }
        }


        AnimatedVisibility(
            visible = (controlsVisible || (anchoredMenus && panel != PlayerChromePanel.None)) && !locked,
            enter = fadeIn(tween(PlayerControlTransitionDurationMs)) +
                slideInVertically(tween(PlayerControlTransitionDurationMs)) { 24 },
            exit = fadeOut(tween(PlayerControlTransitionDurationMs)) +
                slideOutVertically(tween(PlayerControlTransitionDurationMs)) { 24 },
            modifier = Modifier.align(Alignment.BottomCenter).hoverable(controllerInteractions),
        ) {
            PlayerBottomChrome(
                live = media.live,
                state = state,
                presentation = presentation,
                fullscreen = fullscreen,
                largeWindow = largeWindow,
                safeStart = safeArea.start,
                safeEnd = safeArea.end,
                safeBottom = safeArea.bottom,
                backdrop = backdrop,
                sliderPositionMs = sliderPositionMs,
                draggingSlider = draggingSlider,
                onDraggingChanged = { draggingSlider = it },
                onSliderPositionChanged = { sliderPositionMs = it },
                onSeek = {
                    sliderPositionMs = it
                    latestSeek(it)
                    showControlsTemporarily()
                },
                onTogglePlay = latestTogglePlay,
                onRetry = onRetry,
                onFullscreenRequest = onFullscreenRequest,
                tabletWindow = tabletWindow,
                landscapeWindow = landscapeWindow,
                largeTextControls = anchoredMenus,
                windowedController = windowedController,
                windowFullscreen = windowFullscreen,
                onWindowFullscreen = ::fillWindow,
                deviceControls = deviceControls,
                onNext = onNext,
                onPrevious = onPrevious,
                episodes = episodes,
                qualityOptions = availableQualities,
                onPanel = ::openPanel,
                onPanelBounds = { target, bounds -> panelAnchors[target] = bounds },
                capabilities = localCapabilities,
                tracks = tracks,
                danmakuEnabled = danmakuEnabled,
                onDanmakuToggle = if (danmaku.isNotEmpty() && state.engineType == cn.mafei.cmp.player.api.PlayerEngineType.System) {
                    { danmakuEnabled = !danmakuEnabled }
                } else null,
            )
        }

        AnimatedVisibility(
            visible = !windowedController && fullscreen && controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            PlayerChromeButton(
                glyph = PlayerChromeGlyph.Lock,
                label = "锁定",
                diameter = 44,
                iconSize = 22,
                backdrop = backdrop,
                onClick = {
                    locked = true
                    controlsVisible = false
                    panel = PlayerChromePanel.None
                    panelContainerVisible = false
                    panelClosing = false
                },
                modifier = Modifier.padding(start = safeArea.start + 20.dp),
            )
        }

        if (locked) {
            PlayerChromeButton(
                glyph = PlayerChromeGlyph.Unlock,
                label = "解锁",
                diameter = 52,
                iconSize = 24,
                backdrop = backdrop,
                onClick = {
                    locked = false
                    showControlsTemporarily()
                },
                modifier = Modifier.align(Alignment.CenterStart)
                    .padding(start = safeArea.start + if (fullscreen) 20.dp else 24.dp),
            )
        }

        // 旧版 PlayerGestureFeedback：三种反馈互斥，seek 居中、亮度/音量与倍速在顶部同一格。
        // 拖动进度条本身也算 Seek（旧版 onSliderChanged 同样置 feedback），所以这里把
        // 「拖条中」并进同一个来源。
        val seekFeedbackTarget = seekFeedbackMs ?: if (draggingSlider) sliderPositionMs else null
        if (seekFeedbackTarget != null) {
            PlayerSeekFeedback(
                target = seekFeedbackTarget,
                duration = durationMs ?: 1L,
                preview = seekPreview,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        val feedbackTopInset = (if (fullscreen) 60.dp else 32.dp) + safeArea.top
        levelFeedback?.let { feedback ->
            PlayerLevelFeedback(
                level = feedback.level,
                progress = feedback.progress,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = feedbackTopInset),
            )
        }
        if (longPressSpeedActive) {
            PlayerSpeedFeedback(
                text = playerSpeedFeedbackText(PlayerLongPressSpeed),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = feedbackTopInset),
            )
        }

        AnimatedVisibility(
            visible = busyVisible && state.phase != PlayerPhase.Error && externalError == null,
            enter = fadeIn(tween(160)) + scaleIn(tween(180), initialScale = .92f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 1.04f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            MaFeiLoadingIndicator(
                style = MaFeiLoadingStyle.Page,
                tint = Color.White,
            )
        }

        AnimatedVisibility(
            visible = presentation.showRetry || externalError != null,
            enter = fadeIn(tween(180)) + scaleIn(tween(200), initialScale = .94f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = .98f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            PlayerError(
                message = externalError ?: "视频播放失败",
                onRetry = onRetry,
            )
        }

        AnimatedVisibility(
            visible = panel != PlayerChromePanel.None && panelContainerVisible,
            enter = fadeIn(tween(PlayerControlTransitionDurationMs)) +
                slideInVertically(tween(PlayerControlTransitionDurationMs)) { 24 },
            exit = fadeOut(tween(PlayerControlTransitionDurationMs)) +
                slideOutVertically(tween(PlayerControlTransitionDurationMs)) { 24 },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (panel != PlayerChromePanel.None) {
                PlayerSidePanel(
                    panel = panel,
                    closing = panelClosing,
                    state = state,
                    tracks = tracks,
                    episodes = episodes,
                    episodeIndex = episodeIndex,
                    qualityOptions = availableQualities,
                    fullscreen = fullscreen,
                    largeWindow = largeWindow,
                    anchoredMenu = anchoredMenus,
                    anchor = (panelAnchors[if (panel == PlayerChromePanel.AspectRatio) PlayerChromePanel.More else panel]
                        ?: panelAnchor)?.translate(-sceneOrigin),
                    showAspectRatio = localCapabilities.contentMode,
                    showPlaybackInfo = playbackFacts != null,
                    showContentReport = reportSubmitter != null,
                    backdrop = backdrop,
                    safeEnd = safeArea.end,
                    safeStart = safeArea.start,
                    safeTop = safeArea.top + windowControlTopInset,
                    safeBottom = safeArea.bottom,
                    onDismiss = { closePanel() },
                    onOpenPanel = ::openPanel,
                    onSpeed = { value ->
                        latestSpeed(value)
                        closePanel()
                    },
                    onSelectTrack = { kind, id ->
                        onSelectTrack(kind, id)
                        closePanel()
                    },
                    onSelectEpisode = { index ->
                        onSelectEpisode?.invoke(index)
                        closePanel()
                    },
                    onSelectQuality = { bitrate ->
                        onSelectQuality?.invoke(bitrate)
                        closePanel()
                    },
                    onSelectContentMode = { mode ->
                        onSelectContentMode?.invoke(mode)
                        closePanel()
                    },
                    onOpenPlaybackInfo = { closePanel(openPlaybackInfo = true) },
                    onReportContent = {
                        reportFailure = null
                        reportNotice = null
                        reportDescription = ""
                        reportComposerVisible = true
                        closePanel()
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = playbackInfoVisible,
            enter = fadeIn(tween(PlayerControlTransitionDurationMs)) +
                slideInVertically(tween(PlayerControlTransitionDurationMs)) { 18 },
            exit = fadeOut(tween(PlayerControlTransitionDurationMs)) +
                slideOutVertically(tween(PlayerControlTransitionDurationMs)) { 18 },
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerPlaybackInfoCard(
                sections = buildPlayerInfoSections(playbackFacts, state, tracks),
                largeWindow = largeWindow,
                modifier = Modifier.padding(
                    start = safeArea.start,
                    top = safeArea.top + windowControlTopInset,
                    end = safeArea.end,
                    bottom = safeArea.bottom,
                ),
                onDismiss = {
                    playbackInfoVisible = false
                    showControlsTemporarily()
                },
            )
        }
        // BoxWithConstraints subcomposes this scene during measurement. The
        // focus request belongs here, after its focus target has been composed,
        // not in the parent composition before the subcomposition exists.
        LaunchedEffect(media.id, state.generation) {
            playerFocusRequester.requestFocus()
        }
        }
        if (windowHeaderVisible && !state.pictureInPicture) {
            PlayerWindowHeader(
                title = media.title, endInset = if (stackedNativeChrome) 0.dp else hostFeatures.windowControlEndInsetDp.dp,
                height = windowHeaderHeight, backdrop = backdrop, topInset = headerContentTop,
                startInset = safeArea.start,
                windowControls = hostWindowControls, dragArea = hostWindowDragArea,
                onBack = {
                    if (reportComposerVisible) {
                        if (!reportSubmitting) reportComposerVisible = false
                    } else if (panel != PlayerChromePanel.None) closePanel()
                    else if (playbackInfoVisible) playbackInfoVisible = false
                    else onBack()
                },
                onMenu = { openPanel(PlayerChromePanel.More) },
                onMenuBounds = { panelAnchors[PlayerChromePanel.More] = it },
                showMenu = localCapabilities.contentMode || playbackFacts != null || reportSubmitter != null,
                onPictureInPicture = onPictureInPicture, onCasting = onCasting,
            )
        }
        if (reportComposerVisible && reportSubmitter != null) {
            MaFeiGlassConfirmDialog(
                title = "举报内容",
                content = "请描述「${media.title}」存在的问题，便于我们核实处理。",
                confirmText = if (reportSubmitting) "提交中" else "提交",
                dismissText = "取消",
                confirmEnabled = reportDescription.isNotBlank() && !reportSubmitting,
                dismissEnabled = !reportSubmitting,
                onConfirm = ::submitContentReport,
                onDismiss = { if (!reportSubmitting) reportComposerVisible = false },
                backdrop = backdrop,
                bodyContent = {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BasicTextField(
                            value = reportDescription,
                            onValueChange = { reportDescription = it.take(100) },
                            modifier = Modifier.fillMaxWidth()
                                .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = .07f), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                                .height(96.dp)
                                .testTag("contentReportDescription"),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                            ),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (reportDescription.isBlank()) {
                                        Text(
                                            "请填写举报说明",
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                                            fontSize = 15.sp,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        Text(
                            "${reportDescription.length}/100",
                            Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.End,
                        )
                    }
                },
            )
        }
        MaFeiErrorDialog(
            message = reportFailure,
            backdrop = backdrop,
            busy = reportSubmitting,
            onRetry = ::submitContentReport,
            onDismiss = { reportFailure = null },
            title = "举报未提交",
        )
        LegacyTransientToast(
            message = reportNotice,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
