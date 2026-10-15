package cn.mafei.cmp.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.painterResource
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassMaterial
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.currentGlassStyle
import cn.mafei.cmp.common.design_system.MaFeiGlassDefaults
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.common.ui.mafeiDismissOnOutsidePointer
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerContentMode
import cn.mafei.cmp.player.api.PlayerDeviceControls
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.TrackKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.abs
import kotlin.math.roundToLong

private const val PlayerAutoHideDelayMs = 4_000L
private const val PlayerLongPressSpeed = 3f
private const val PlayerControlTransitionDurationMs = 180
private const val PlayerMenuCloseDurationMs = 200
private const val PlayerMenuOptionDurationMs = 130
private const val PlayerMenuOptionStaggerMs = 28
private const val PlayerEpisodeEntranceDurationMs = 180
private const val PlayerEpisodeRowStaggerMs = 50

/** Legacy `PLAYER_CLOCK_INTERVAL_MS`. */
private const val PlayerClockIntervalMs = 30_000L

private val PlayerControlMask = Color(0x99000000)
private val PlayerFullscreenControlMask = Color(0xB0000000)
private val PlayerGestureSeekSurface = Color(0x85000000)
/** 旧版 PLAYER_GESTURE_FEEDBACK_SURFACE_COLOR：亮度/音量/倍速胶囊比 seek 卡更透。 */
private val PlayerGestureSurface = Color(0x66000000)
private val PlayerGestureTrack = Color(0x66FFFFFF)
private val PlayerMenuText = Color(0xE6FFFFFF)
private val PlayerMenuDivider = Color(0x45FFFFFF)
private val PlayerMenuSelectedSurface = Color(0x542173DF)
private val PlayerInfoCardSurface = Color(0x7A101010)
private val PlayerInfoCardBorder = Color(0x1AFFFFFF)
private val PlayerInfoSectionTitle = Color(0xA3FFFFFF)
private val PlayerInfoEntryLabel = Color(0xADFFFFFF)
private val PlayerMenuGradient = Brush.horizontalGradient(
    0f to Color.Transparent,
    .42f to Color(0x8A000000),
    1f to Color(0xDC000000),
)
private val PlayerAccent = Color(0xFF2173DF)

private enum class PlayerChromePanel { None, More, AspectRatio, Speed, Audio, Subtitle, Episodes, Quality }

enum class PlayerChromeGlyph {
    More,
    Play,
    Pause,
    Retry,
    Fullscreen,
    ExitFullscreen,
    WindowFullscreen,
    WindowRestore,
    Lock,
    Unlock,
    Previous,
    Next,
    Info,
    Orientation,
    Brightness,
    Volume,
    Muted,
}

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
        when (playerBackStep(panel != PlayerChromePanel.None, playbackInfoVisible,
            fullscreen, requestFullscreen != null, windowedController && windowFullscreen)) {
            PlayerBackStep.ClosePanel -> closePanel()
            PlayerBackStep.ClosePlaybackInfo -> { playbackInfoVisible = false; showControlsTemporarily() }
            PlayerBackStep.ExitFullscreen -> requestFullscreen?.invoke(false)
            PlayerBackStep.ExitWindowFullscreen -> windowFullscreen = false
            PlayerBackStep.LeavePlayer -> onBack()
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
                // Legacy 「更多」 holds exactly 画面比例 and 播放信息; it appears
                // only when at least one of the two exists on this platform.
                showMenu = localCapabilities.contentMode || playbackFacts != null,
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
                    if (panel != PlayerChromePanel.None) closePanel()
                    else if (playbackInfoVisible) playbackInfoVisible = false
                    else onBack()
                },
                onMenu = { openPanel(PlayerChromePanel.More) },
                onMenuBounds = { panelAnchors[PlayerChromePanel.More] = it },
                showMenu = localCapabilities.contentMode || playbackFacts != null,
                onPictureInPicture = onPictureInPicture, onCasting = onCasting,
            )
        }
    }
}

@Composable
private fun PlayerWindowHeader(
    title: String,
    endInset: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    topInset: androidx.compose.ui.unit.Dp,
    startInset: androidx.compose.ui.unit.Dp,
    backdrop: GlassBackdrop,
    windowControls: @Composable RowScope.() -> Unit,
    dragArea: @Composable (@Composable () -> Unit) -> Unit,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onMenuBounds: (Rect) -> Unit,
    showMenu: Boolean,
    onPictureInPicture: (() -> Unit)?,
    onCasting: (() -> Unit)?,
) {
    dragArea {
        Row(
            Modifier.fillMaxWidth().height(height).background(Color(0xFF141414))
                .padding(start = 16.dp + startInset, top = topInset, end = 12.dp + endInset).testTag("playerWindowHeader"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            windowControls()
            PlayerChromeButton(null, "返回", 44, 20, backdrop, onBack, transparent = true) {
                MaFeiIcon(MaFeiGlyph.Back, Modifier.size(20.dp), Color.White)
            }
            Text(title, Modifier.weight(1f), color = Color.White,
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onPictureInPicture != null) {
                PlayerChromeButton(null, "画中画", 44, 20, backdrop, onPictureInPicture, transparent = true) {
                    Image(painterResource(MaFeiLegacyDrawables.pictureInPicture), null, Modifier.size(20.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
                }
            }
            if (onCasting != null) {
                PlayerChromeButton(null, "投屏", 44, 20, backdrop, onCasting, transparent = true) {
                    Image(painterResource(MaFeiLegacyDrawables.wirelessProjection), null, Modifier.size(20.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
                }
            }
            if (showMenu) PlayerChromeButton(PlayerChromeGlyph.More, "更多设置", 44, 20,
                backdrop, onMenu, Modifier.onGloballyPositioned { onMenuBounds(it.boundsInRoot()) }, transparent = true)
        }
    }
}

@Composable
private fun PlayerTopChrome(
    title: String,
    fullscreen: Boolean,
    largeWindow: Boolean,
    safeStart: androidx.compose.ui.unit.Dp,
    safeTop: androidx.compose.ui.unit.Dp,
    safeEnd: androidx.compose.ui.unit.Dp,
    backdrop: GlassBackdrop,
    clockText: String,
    showMenu: Boolean,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onMenuBounds: (Rect) -> Unit,
    onPictureInPicture: (() -> Unit)?,
    onCasting: (() -> Unit)?,
) {
    val windowFeatures = LocalMaFeiWindowFeatures.current
    val windowControlEndInset = if (windowFeatures.windowedHost && !fullscreen) {
        windowFeatures.windowControlEndInsetDp.dp
    } else {
        0.dp
    }
    val windowControlTopInset = if (windowFeatures.windowedHost && !fullscreen) {
        windowFeatures.windowControlTopInsetDp.dp
    } else {
        0.dp
    }
    val topChromeInset = safeTop + windowControlTopInset
    Box(
        Modifier.fillMaxWidth()
            .height((when {
                largeWindow -> 104.dp
                fullscreen -> 96.dp
                else -> 88.dp
            }) + topChromeInset)
            .background(
                Brush.verticalGradient(
                    0f to (if (fullscreen) PlayerFullscreenControlMask else PlayerControlMask),
                    .5f to (if (fullscreen) PlayerFullscreenControlMask.copy(alpha = .48f)
                    else PlayerControlMask.copy(alpha = .42f)),
                    1f to Color.Transparent,
                ),
            ),
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(
                    // 非全屏返回按钮独立绘制在 x=12、宽 44 的位置；标题从同一
                    // 行的 68dp 起步，保证返回按钮右缘到标题左缘的间距固定。
                    start = if (fullscreen) safeStart + 20.dp else safeStart + 68.dp,
                    // HarmonyOS owns the native maximize/minimize/close area.
                    // Reserve its actual measured footprint instead of using a
                    // device-specific constant; the player title/menu stays
                    // readable in both floating and resized windows.
                    end = safeEnd + (if (largeWindow) 24.dp else 12.dp) +
                        windowControlEndInset,
                    top = topChromeInset + if (fullscreen) 16.dp else 10.dp,
                )
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (fullscreen) {
                PlayerChromeButton(
                    glyph = null,
                    label = "返回",
                    diameter = 44,
                    iconSize = 22,
                    backdrop = backdrop,
                    onClick = onBack,
                ) {
                    MaFeiIcon(MaFeiGlyph.Back, Modifier.size(22.dp), Color.White)
                }
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = if (largeWindow) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onPictureInPicture != null) {
                PlayerChromeButton(glyph = null, label = "画中画", diameter = 44, iconSize = 22,
                    backdrop = backdrop, onClick = onPictureInPicture) {
                    Image(painterResource(MaFeiLegacyDrawables.pictureInPicture), null,
                        Modifier.size(22.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
                }
            }
            if (onCasting != null) {
                PlayerChromeButton(glyph = null, label = "投屏", diameter = 44, iconSize = 22,
                    backdrop = backdrop, onClick = onCasting) {
                    Image(painterResource(MaFeiLegacyDrawables.wirelessProjection), null,
                        Modifier.size(22.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
                }
            }
            if (showMenu) {
                PlayerChromeButton(
                    glyph = PlayerChromeGlyph.More,
                    label = "更多设置",
                    diameter = 44,
                    iconSize = 22,
                    backdrop = backdrop,
                    onClick = onMenu,
                    modifier = Modifier.onGloballyPositioned { onMenuBounds(it.boundsInRoot()) },
                )
            }
            // 旧版把时间/电量放在「更多」之后的同一行，且只有全屏才有这一格。
            // 电量需要平台级状态源（ArkTS batteryInfo / Android BatteryManager），
            // 尚未接线，所以这里只画时钟；没有可用时钟时整格消失。
            if (fullscreen && clockText.isNotEmpty()) {
                Text(
                    clockText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun PlayerBottomChrome(
    live: Boolean,
    state: PlayerState,
    tracks: List<PlayerTrack>,
    presentation: NowPlayingPresentation,
    fullscreen: Boolean,
    largeWindow: Boolean,
    safeStart: androidx.compose.ui.unit.Dp,
    safeEnd: androidx.compose.ui.unit.Dp,
    safeBottom: androidx.compose.ui.unit.Dp,
    backdrop: GlassBackdrop,
    sliderPositionMs: Long,
    draggingSlider: Boolean,
    onDraggingChanged: (Boolean) -> Unit,
    onSliderPositionChanged: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    onFullscreenRequest: ((Boolean) -> Unit)?,
    tabletWindow: Boolean,
    landscapeWindow: Boolean,
    largeTextControls: Boolean,
    onNext: (() -> Unit)?,
    onPrevious: (() -> Unit)?,
    episodes: List<cn.mafei.cmp.core.domain.MediaItem>,
    qualityOptions: List<PlayerQualityOption>,
    onPanel: (PlayerChromePanel) -> Unit,
    onPanelBounds: (PlayerChromePanel, Rect) -> Unit,
    capabilities: PlayerCapabilities,
    danmakuEnabled: Boolean = false,
    onDanmakuToggle: (() -> Unit)? = null,
    windowedController: Boolean = false,
    windowFullscreen: Boolean = false,
    onWindowFullscreen: () -> Unit = {},
    deviceControls: PlayerDeviceControls = PlayerDeviceControls.Unsupported,
) {
    val audioTracks = tracks.filter { it.kind == TrackKind.Audio }
    val subtitleTracks = tracks.filter { it.kind == TrackKind.Subtitle }
    val duration = state.durationMs?.takeIf { it > 0L }
    val playbackOptions: @Composable RowScope.() -> Unit = {
        if (onDanmakuToggle != null) {
            PlayerChromeButton(glyph = null, label = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                diameter = if (windowedController) 44 else if (largeWindow) 42 else 30,
                iconSize = 20, backdrop = backdrop, transparent = true, onClick = onDanmakuToggle) {
                Image(painterResource(if (danmakuEnabled) MaFeiLegacyDrawables.danmakuOpen else MaFeiLegacyDrawables.danmakuClose),
                    null, Modifier.size(20.dp))
            }
        }
        fun anchor(target: PlayerChromePanel) = Modifier.onGloballyPositioned { onPanelBounds(target, it.boundsInRoot()) }
        if (capabilities.trackSelection && audioTracks.size > 1) {
            PlayerTextControl("音轨", largeTextControls, anchor(PlayerChromePanel.Audio)) { onPanel(PlayerChromePanel.Audio) }
        }
        if (capabilities.trackSelection && subtitleTracks.isNotEmpty()) {
            PlayerTextControl("字幕", largeTextControls, anchor(PlayerChromePanel.Subtitle)) { onPanel(PlayerChromePanel.Subtitle) }
        }
        if (!live && episodes.size > 1) {
            PlayerTextControl("选集", largeTextControls, anchor(PlayerChromePanel.Episodes)) { onPanel(PlayerChromePanel.Episodes) }
        }
        if (qualityOptions.isNotEmpty()) {
            PlayerTextControl("清晰度", largeTextControls, anchor(PlayerChromePanel.Quality)) { onPanel(PlayerChromePanel.Quality) }
        }
        if (capabilities.speed) {
            PlayerTextControl(if (abs(state.speed - 1f) < .01f) "倍速" else "${state.speed}x",
                largeTextControls, anchor(PlayerChromePanel.Speed)) { onPanel(PlayerChromePanel.Speed) }
        }
    }
    if (windowedController) {
        PlayerWindowController(
            safeStart, safeEnd, safeBottom,
            timeline = { timelineModifier -> if (!live) PlayerTimelineRow(state, sliderPositionMs, duration,
                presentation.canSeek, 12, onDraggingChanged, onSliderPositionChanged, onSeek, timelineModifier) },
            transport = {
                if (onPrevious != null) PlayerChromeButton(PlayerChromeGlyph.Previous, "上一集", 44, 20, backdrop, onPrevious, transparent = true)
                PlayerChromeButton(playbackGlyph(presentation), playbackLabel(presentation), 44, 22,
                    backdrop, if (presentation.showRetry) onRetry else onTogglePlay, transparent = true)
                if (onNext != null) PlayerChromeButton(PlayerChromeGlyph.Next, "下一集", 44, 20, backdrop, onNext, transparent = true)
            },
            options = playbackOptions,
            volume = { PlayerWindowVolume(deviceControls, backdrop) },
            screenModes = {
                PlayerChromeButton(
                    if (windowFullscreen && !fullscreen) PlayerChromeGlyph.WindowRestore else PlayerChromeGlyph.WindowFullscreen,
                    if (windowFullscreen && !fullscreen) "退出窗口内全屏" else "窗口内全屏", 44, 20,
                    backdrop, onWindowFullscreen, transparent = true,
                )
                if (onFullscreenRequest != null) PlayerChromeButton(
                    if (fullscreen) PlayerChromeGlyph.ExitFullscreen else PlayerChromeGlyph.Fullscreen,
                    if (fullscreen) "退出系统全屏" else "系统全屏", 44, 20,
                    backdrop, { onFullscreenRequest(!fullscreen) }, transparent = true,
                )
            },
        )
        return
    }
    // Wide windows keep the full player control set even before the user
    // enters explicit fullscreen. Fullscreen is an orientation/window-state
    // request; it must not also decide whether core playback controls exist.
    val extendedControls = fullscreen || largeWindow
    val maskHeight = when {
        largeWindow -> 164.dp
        fullscreen -> 132.dp
        else -> 106.dp
    }
    val bottomInset = maxOf(safeBottom, if (extendedControls) 10.dp else 10.dp)

    Box(
        Modifier.fillMaxWidth().height(maskHeight + safeBottom)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .46f to (if (fullscreen) PlayerFullscreenControlMask.copy(alpha = .48f)
                    else PlayerControlMask.copy(alpha = .42f)),
                    1f to (if (fullscreen) PlayerFullscreenControlMask else PlayerControlMask),
                ),
            ),
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(
                    start = safeStart + if (fullscreen) 20.dp else 8.dp,
                    end = safeEnd + if (fullscreen) 20.dp else 8.dp,
                    bottom = bottomInset,
                ),
            verticalArrangement = Arrangement.spacedBy(if (fullscreen) 2.dp else 0.dp),
        ) {
                if (extendedControls) {
                    if (!live) PlayerTimelineRow(
                        state = state,
                        positionMs = sliderPositionMs,
                        durationMs = duration,
                        enabled = presentation.canSeek,
                    thumbDiameter = 14,
                    onDraggingChanged = onDraggingChanged,
                    onPositionChanged = onSliderPositionChanged,
                    onSeek = onSeek,
                        modifier = Modifier.height(if (largeWindow) 34.dp else 28.dp),
                    )
                    Row(
                    modifier = Modifier.fillMaxWidth().height(if (largeWindow) 52.dp else 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (largeWindow) 8.dp else 4.dp),
                ) {
                    if (onPrevious != null) {
                        PlayerChromeButton(
                            glyph = PlayerChromeGlyph.Previous,
                            label = "上一集",
                            diameter = if (largeWindow) 42 else 30,
                            iconSize = if (largeWindow) 20 else 16,
                            backdrop = backdrop,
                            transparent = true,
                            onClick = onPrevious,
                        )
                    }
                    PlayerChromeButton(
                        glyph = playbackGlyph(presentation),
                        label = playbackLabel(presentation),
                        diameter = if (largeWindow) 48 else 30,
                        iconSize = if (largeWindow) 22 else 16,
                        backdrop = backdrop,
                        transparent = true,
                        onClick = if (presentation.showRetry) onRetry else onTogglePlay,
                    )
                    if (onNext != null) {
                        PlayerChromeButton(
                            glyph = PlayerChromeGlyph.Next,
                            label = "下一集",
                            diameter = if (largeWindow) 42 else 30,
                            iconSize = if (largeWindow) 20 else 16,
                            backdrop = backdrop,
                            transparent = true,
                            onClick = onNext,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    playbackOptions()
                    if (largeWindow && onFullscreenRequest != null) {
                        PlayerChromeButton(
                            glyph = if (tabletWindow) PlayerChromeGlyph.Orientation
                            else PlayerChromeGlyph.Fullscreen,
                            label = if (tabletWindow) {
                                if (landscapeWindow) "竖屏" else "横屏"
                            } else "全屏",
                            diameter = 42,
                            iconSize = 20,
                            backdrop = backdrop,
                            transparent = true,
                            onClick = {
                                onFullscreenRequest(
                                    if (tabletWindow) !landscapeWindow else !fullscreen,
                                )
                            },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onPrevious != null) {
                        PlayerChromeButton(
                            glyph = PlayerChromeGlyph.Previous,
                            label = "上一集",
                            diameter = 38,
                            iconSize = 18,
                            backdrop = backdrop,
                            transparent = true,
                            onClick = onPrevious,
                        )
                    }
                    PlayerChromeButton(
                        glyph = playbackGlyph(presentation),
                        label = playbackLabel(presentation),
                        diameter = 46,
                        iconSize = 24,
                        backdrop = backdrop,
                        transparent = true,
                        onClick = if (presentation.showRetry) onRetry else onTogglePlay,
                    )
                    if (onNext != null) {
                        PlayerChromeButton(
                            glyph = PlayerChromeGlyph.Next,
                            label = "下一集",
                            diameter = 38,
                            iconSize = 18,
                            backdrop = backdrop,
                            transparent = true,
                            onClick = onNext,
                        )
                    }
                    if (!live) {
                    Text(
                        formatPlayerDuration(if (draggingSlider) sliderPositionMs else state.positionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    LegacyPlayerSeekBar(
                        valueMs = sliderPositionMs,
                        bufferedMs = state.bufferedPositionMs,
                        totalMs = duration,
                        enabled = presentation.canSeek,
                        thumbDiameter = 16,
                        onDraggingChanged = onDraggingChanged,
                        onValueChanged = onSliderPositionChanged,
                        onSeek = onSeek,
                        modifier = Modifier.weight(1f).height(32.dp),
                    )
                    Text(
                        duration?.let(::formatPlayerDuration) ?: "--:--",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    } else Spacer(Modifier.weight(1f))
                    if (onFullscreenRequest != null) {
                        PlayerChromeButton(
                            glyph = if (tabletWindow) PlayerChromeGlyph.Orientation
                            else PlayerChromeGlyph.Fullscreen,
                            label = if (tabletWindow) {
                                if (landscapeWindow) "竖屏" else "横屏"
                            } else "全屏",
                            diameter = 44,
                            iconSize = 20,
                            backdrop = backdrop,
                            transparent = true,
                            onClick = {
                                onFullscreenRequest(
                                    if (tabletWindow) !landscapeWindow else !fullscreen,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerWindowVolume(deviceControls: PlayerDeviceControls, backdrop: GlassBackdrop) {
    val max = deviceControls.maxVolume()
    if (max <= 0 || deviceControls.volume() == null) return
    var volume by remember(deviceControls) { mutableIntStateOf(deviceControls.volume() ?: 0) }
    var volumeBeforeMute by remember(deviceControls) { mutableIntStateOf(max / 2) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(deviceControls, dragging) {
        if (!dragging) while (true) {
            deviceControls.refresh()
            volume = deviceControls.volume() ?: volume
            delay(500)
        }
    }
    fun setVolume(value: Long) {
        volume = value.toInt().coerceIn(0, max)
        deviceControls.setVolume(volume)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayerChromeButton(if (volume == 0) PlayerChromeGlyph.Muted else PlayerChromeGlyph.Volume,
            if (volume == 0) "取消静音" else "静音", 44, 20, backdrop, {
                if (volume > 0) { volumeBeforeMute = volume; setVolume(0) }
                else setVolume(volumeBeforeMute.coerceAtLeast(1).toLong())
            }, transparent = true)
        LegacyPlayerSeekBar(volume.toLong(), 0, max.toLong(), true, 10,
            onDraggingChanged = { dragging = it }, onValueChanged = ::setVolume, onSeek = ::setVolume,
            modifier = Modifier.width(64.dp).height(32.dp), label = "音量")
    }
}

@Composable
private fun PlayerTimelineRow(
    state: PlayerState,
    positionMs: Long,
    durationMs: Long?,
    enabled: Boolean,
    thumbDiameter: Int,
    onDraggingChanged: (Boolean) -> Unit,
    onPositionChanged: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(formatPlayerDuration(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
        LegacyPlayerSeekBar(
            valueMs = positionMs,
            bufferedMs = state.bufferedPositionMs,
            totalMs = durationMs,
            enabled = enabled && durationMs != null,
            thumbDiameter = thumbDiameter,
            onDraggingChanged = onDraggingChanged,
            onValueChanged = onPositionChanged,
            onSeek = onSeek,
            modifier = Modifier.weight(1f).height(28.dp),
        )
        Text(durationMs?.let(::formatPlayerDuration) ?: "--:--", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LegacyPlayerSeekBar(
    valueMs: Long,
    bufferedMs: Long,
    totalMs: Long?,
    enabled: Boolean,
    thumbDiameter: Int,
    onDraggingChanged: (Boolean) -> Unit,
    onValueChanged: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "播放进度",
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragValue by remember { mutableLongStateOf(valueMs) }
    var directManipulation by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val total = totalMs?.coerceAtLeast(1L) ?: 1L
    val valueFraction = (valueMs.toDouble() / total).toFloat().coerceIn(0f, 1f)
    val bufferedFraction = (bufferedMs.toDouble() / total).toFloat().coerceIn(0f, 1f)
    val displayedValueFraction by animateFloatAsState(
        targetValue = valueFraction,
        animationSpec = if (directManipulation) snap() else tween(300, easing = LinearEasing),
        label = "player-progress",
    )
    val displayedBufferedFraction by animateFloatAsState(
        targetValue = bufferedFraction,
        animationSpec = tween(420, easing = LinearEasing),
        label = "player-buffered-progress",
    )
    fun valueFor(x: Float): Long = if (size.width <= 0) valueMs else {
        (total.toDouble() * (x / size.width.toFloat()).coerceIn(0f, 1f)).roundToLong()
    }
    Canvas(
        modifier.onSizeChanged { size = it }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(valueMs.toFloat(), 0f..total.toFloat())
                if (enabled) setProgress { target ->
                    val value = target.toLong().coerceIn(0, total)
                    onValueChanged(value); onSeek(value); true
                }
            }
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = if (label == "音量") 1L else 5_000L
                val target = when (event.key) {
                    Key.DirectionLeft -> valueMs - step
                    Key.DirectionRight -> valueMs + step
                    Key.MoveHome -> 0L
                    Key.MoveEnd -> total
                    else -> return@onKeyEvent false
                }.coerceIn(0, total)
                onValueChanged(target); onSeek(target); true
            }
            .focusable(enabled, interactions)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(enabled, total, size) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    directManipulation = true
                    val target = valueFor(offset.x)
                    onValueChanged(target)
                    onSeek(target)
                    directManipulation = false
                }
            }
            .pointerInput(enabled, total, size) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        directManipulation = true
                        onDraggingChanged(true)
                        dragValue = valueFor(offset.x)
                        onValueChanged(dragValue)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragValue = valueFor(change.position.x)
                        onValueChanged(dragValue)
                    },
                    onDragCancel = {
                        directManipulation = false
                        onDraggingChanged(false)
                    },
                    onDragEnd = {
                        directManipulation = false
                        onDraggingChanged(false)
                        onSeek(dragValue)
                    },
                )
            },
    ) {
        val centerY = size.height / 2f
        val trackHeight = (if (focused) 6.dp else 4.dp).toPx()
        val radius = trackHeight / 2f
        drawRoundRect(
            color = Color(0x66FFFFFF),
            topLeft = Offset(0f, centerY - radius),
            size = Size(size.width.toFloat(), trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
        drawRoundRect(
            color = Color(0xA6FFFFFF),
            topLeft = Offset(0f, centerY - radius),
            size = Size(size.width * displayedBufferedFraction, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(0f, centerY - radius),
            size = Size(size.width * displayedValueFraction, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
        if (enabled && size.width > 0f) {
            val diameter = thumbDiameter.dp.toPx()
            val thumbX = displayedValueFraction * (size.width - diameter) + diameter / 2f
            drawCircle(Color.White, diameter / 2f, Offset(thumbX, centerY))
        }
    }
}

@Composable
private fun PlayerSidePanel(
    panel: PlayerChromePanel,
    closing: Boolean,
    state: PlayerState,
    tracks: List<PlayerTrack>,
    episodes: List<cn.mafei.cmp.core.domain.MediaItem>,
    episodeIndex: Int,
    qualityOptions: List<PlayerQualityOption>,
    fullscreen: Boolean,
    largeWindow: Boolean,
    anchoredMenu: Boolean,
    anchor: Rect?,
    showAspectRatio: Boolean,
    showPlaybackInfo: Boolean,
    backdrop: GlassBackdrop,
    safeEnd: androidx.compose.ui.unit.Dp,
    safeStart: androidx.compose.ui.unit.Dp,
    safeTop: androidx.compose.ui.unit.Dp,
    safeBottom: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onOpenPanel: (PlayerChromePanel) -> Unit,
    onSpeed: (Float) -> Unit,
    onSelectTrack: (TrackKind, String?) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectContentMode: (PlayerContentMode) -> Unit,
    onOpenPlaybackInfo: () -> Unit,
) {
    var entranceVisible by remember(panel) { mutableStateOf(false) }
    LaunchedEffect(panel) {
        delay(16)
        entranceVisible = true
    }
    // 选集面板按旧版取 330 宽（PLAYER_MENU_EPISODE_WIDTH），并带自己的标题区。
    val isEpisodePanel = panel == PlayerChromePanel.Episodes
    val isMorePanel = panel == PlayerChromePanel.More
    val (width, options) = when (panel) {
        PlayerChromePanel.Speed -> Pair(
            138.dp,
            listOf(3f, 2f, 1.5f, 1.25f, 1f, .75f).map { speed ->
                PlayerPanelOption(speed.toString(), "${speed}x", abs(state.speed - speed) < .01f)
            },
        )
        PlayerChromePanel.Audio -> Pair(
            220.dp,
            tracks.filter { it.kind == TrackKind.Audio }.map(PlayerTrack::toPanelOption),
        )
        PlayerChromePanel.Subtitle -> Pair(
            220.dp,
            tracks.filter { it.kind == TrackKind.Subtitle }.let { subtitleTracks ->
                listOf(PlayerPanelOption("", "关闭字幕", selected = subtitleTracks.none(PlayerTrack::selected))) +
                    subtitleTracks.map(PlayerTrack::toPanelOption)
            },
        )
        PlayerChromePanel.Episodes -> Pair(330.dp, emptyList())
        PlayerChromePanel.Quality -> Pair(
            176.dp,
            qualityOptions.map { option ->
                PlayerPanelOption(option.bitrate.toString(), option.label, option.selected)
            },
        )
        PlayerChromePanel.AspectRatio -> Pair(
            176.dp,
            playerContentModeChoices.map { mode ->
                PlayerPanelOption(mode.name, playerContentModeLabel(mode), selected = state.contentMode == mode)
            },
        )
        // 「更多」是两张入口行 + 分隔线，不是普通选项列表。
        PlayerChromePanel.More -> Pair(270.dp, emptyList())
        PlayerChromePanel.None -> return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scale = density.fontScale.coerceAtLeast(1f)
        val rowHeight = if (anchoredMenu) (52f * scale).dp else 44.dp
        val popupWidth = (when (panel) {
            PlayerChromePanel.Speed -> 216.dp
            PlayerChromePanel.Episodes -> 360.dp
            PlayerChromePanel.Audio, PlayerChromePanel.Subtitle -> 320.dp
            PlayerChromePanel.More -> 296.dp
            else -> 248.dp
        } * scale.coerceAtMost(1.3f)).coerceAtMost(
            (maxWidth - safeStart - safeEnd - 24.dp).coerceAtLeast(1.dp),
        )
        val optionCount = if (isMorePanel) listOf(showAspectRatio, showPlaybackInfo).count { it }
            else options.size
        val desiredHeight = if (isEpisodePanel) {
            40.dp + PlayerEpisodeRowHeight * minOf(episodes.size, 6) + 24.dp
        } else {
            rowHeight * optionCount.coerceAtLeast(1) + 24.dp + if (isMorePanel) 1.dp else 0.dp
        }
        val popupHeight = desiredHeight.coerceAtMost((420f * scale).dp).coerceAtMost(
            (maxHeight - safeTop - safeBottom - 24.dp).coerceAtLeast(1.dp),
        )
        val anchorRight = anchor?.let { with(density) { it.right.toDp() } } ?: (maxWidth - safeEnd - 16.dp)
        val anchorTop = anchor?.let { with(density) { it.top.toDp() } } ?: (safeTop + 16.dp)
        val anchorBottom = anchor?.let { with(density) { it.bottom.toDp() } } ?: (safeTop + 60.dp)
        val left = (anchorRight - popupWidth).coerceIn(
            safeStart + 12.dp,
            (maxWidth - safeEnd - 12.dp - popupWidth).coerceAtLeast(safeStart + 12.dp),
        )
        val top = (if ((anchorTop + anchorBottom) / 2 > maxHeight / 2) {
            anchorTop - 8.dp - popupHeight
        } else {
            anchorBottom + 8.dp
        }).coerceIn(
            safeTop + 12.dp,
            (maxHeight - safeBottom - 12.dp - popupHeight).coerceAtLeast(safeTop + 12.dp),
        )
        Box(
            Modifier.fillMaxSize()
                // The player menu is a same-layer overlay. Raw tap detection
                // keeps the outside hit region working on OHOS/CPF as well as
                // desktop, where a regular clickable can lose its up event to
                // the video surface below.
                .mafeiDismissOnOutsidePointer(onDismiss),
        )
        val content: @Composable () -> Unit = {
            Column(
                Modifier.fillMaxSize().padding(if (anchoredMenu) {
                    androidx.compose.foundation.layout.PaddingValues(12.dp)
                } else {
                    androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = safeEnd + 16.dp,
                        top = safeTop + if (fullscreen || largeWindow) 18.dp else 8.dp,
                        bottom = safeBottom + if (fullscreen || largeWindow) 18.dp else 8.dp,
                    )
                }),
            ) {
                when {
                isMorePanel -> PlayerMorePanel(
                    aspectRatioLabel = playerContentModeLabel(state.contentMode),
                    showAspectRatio = showAspectRatio,
                    showPlaybackInfo = showPlaybackInfo,
                    entranceVisible = entranceVisible,
                    closing = closing,
                    onAspectRatio = { onOpenPanel(PlayerChromePanel.AspectRatio) },
                    onPlaybackInfo = onOpenPlaybackInfo,
                    largeType = anchoredMenu,
                    rowHeight = rowHeight,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                isEpisodePanel -> {
                    Text(
                        "选集",
                        Modifier.padding(bottom = 12.dp)
                            .playerStaggeredEntrance(
                                visible = entranceVisible && !closing,
                                index = 0,
                                count = 1,
                                durationMs = PlayerEpisodeEntranceDurationMs,
                                staggerMs = 0,
                            ),
                        color = PlayerMenuText,
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = if (anchoredMenu) 18.sp else MaterialTheme.typography.titleSmall.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                    PlayerEpisodePanel(
                        episodes = episodes,
                        episodeIndex = episodeIndex,
                        entranceVisible = entranceVisible,
                        closing = closing,
                        onSelectEpisode = onSelectEpisode,
                        largeType = anchoredMenu,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                    else -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        itemsIndexed(options, key = { _, option -> option.id }) { index, option ->
                            PlayerPanelOptionRow(
                                option = option,
                                entranceVisible = entranceVisible && !closing,
                                index = index,
                                count = options.size,
                                largeType = anchoredMenu,
                                rowHeight = rowHeight,
                            ) {
                                when (panel) {
                                    PlayerChromePanel.Speed -> onSpeed(option.id.toFloat())
                                    PlayerChromePanel.Audio -> onSelectTrack(TrackKind.Audio, option.id)
                                    PlayerChromePanel.Subtitle -> onSelectTrack(TrackKind.Subtitle, option.id.ifEmpty { null })
                                    PlayerChromePanel.Quality -> onSelectQuality(option.id.toInt())
                                    PlayerChromePanel.AspectRatio ->
                                        onSelectContentMode(PlayerChromeModeById(option.id))
                                    PlayerChromePanel.Episodes,
                                    PlayerChromePanel.More,
                                    PlayerChromePanel.None -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
        if (anchoredMenu) {
            GlassSurface(
                backdrop = backdrop,
                spec = MaFeiGlassDefaults.spec(GlassRole.PlayerOverlay).copy(
                    tintAlpha = .90f,
                    cornerRadiusDp = 16f,
                    material = GlassMaterial.Frosted,
                ),
                // A lower-alpha style still needs enough luminance to separate
                // the menu from a pure-black video; blur alone cannot do that.
                tint = if (currentGlassStyle() == GlassStyle.Translucent) Color(0xFF747986)
                    else Color(0xFF30333B),
                modifier = Modifier.offset {
                    IntOffset(left.roundToPx(), top.roundToPx())
                }.width(popupWidth).height(popupHeight)
                    .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(16.dp))
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .testTag("playerMenuPanel"),
                content = content,
            )
        } else {
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .then(if (fullscreen || largeWindow) Modifier.width(width + safeEnd)
                        else Modifier.fillMaxWidth(.84f))
                    .fillMaxHeight()
                    .background(PlayerMenuGradient)
                    .testTag("playerMenuPanel"),
            ) { content() }
        }
    }
}

/**
 * 旧版「更多」面板（`PlayerChromePanel.More`）：画面比例入口 + 分隔线 + 播放信息入口。
 * 任一入口在当前平台不存在时只画存在的那个，绝不画一条点了没反应的死行。
 */
@Composable
private fun PlayerMorePanel(
    aspectRatioLabel: String,
    showAspectRatio: Boolean,
    showPlaybackInfo: Boolean,
    entranceVisible: Boolean,
    closing: Boolean,
    onAspectRatio: () -> Unit,
    onPlaybackInfo: () -> Unit,
    modifier: Modifier = Modifier,
    largeType: Boolean = false,
    rowHeight: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val count = listOf(showAspectRatio, showPlaybackInfo).count { it }
    Column(modifier) {
        if (showAspectRatio) {
            PlayerPanelOptionRow(
                option = PlayerPanelOption("aspect", "画面比例 · $aspectRatioLabel"),
                entranceVisible = entranceVisible && !closing,
                index = 0,
                count = count,
                largeType = largeType,
                rowHeight = rowHeight,
                onClick = onAspectRatio,
            )
        }
        if (showAspectRatio && showPlaybackInfo) {
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(PlayerMenuDivider),
            )
        }
        if (showPlaybackInfo) {
            PlayerPanelOptionRow(
                option = PlayerPanelOption("info", "播放信息"),
                entranceVisible = entranceVisible && !closing,
                index = if (showAspectRatio) 1 else 0,
                count = count,
                largeType = largeType,
                rowHeight = rowHeight,
                onClick = onPlaybackInfo,
            )
        }
    }
}

/** Panel option ids are the enum names, so the ratio table needs no side map. */
private fun PlayerChromeModeById(id: String): PlayerContentMode =
    playerContentModeChoices.firstOrNull { it.name == id }
        ?: PlayerContentMode.Fit

/**
 * 旧版 `PlaybackInfoCard`：盖住画面的一张技术信息卡（左对齐、72% 宽/76% 高，
 * 250~360dp 宽 clamp）。整层点击即关；卡片自身拦截点击，内部滚动照常。
 */
@Composable
private fun PlayerPlaybackInfoCard(
    sections: List<PlayerInfoSection>,
    onDismiss: () -> Unit,
    largeWindow: Boolean,
    modifier: Modifier = Modifier,
) {
    val dismissSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Clamp the viewport-relative width before applying a fixed constraint.
        // fillMaxWidth followed by widthIn would lock in 72% and ignore the cap.
        val cardWidth = if (largeWindow) {
            (maxWidth * .56f).coerceIn(360.dp, 560.dp)
        } else {
            (maxWidth * .72f).coerceIn(250.dp, 360.dp)
        }
        Box(
            Modifier.fillMaxSize()
                .clickable(
                    interactionSource = dismissSource,
                    indication = null,
                    onClickLabel = "关闭播放信息",
                    onClick = onDismiss,
                ),
        )
        Column(
            Modifier.width(cardWidth)
                .fillMaxHeight(if (largeWindow) .72f else .76f)
                .then(
                    if (largeWindow) Modifier.align(Alignment.Center)
                    else Modifier.padding(start = 32.dp, top = 16.dp, bottom = 16.dp),
                )
                .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x52000000), spotColor = Color(0x52000000))
                .clip(RoundedCornerShape(20.dp))
                .background(PlayerInfoCardSurface)
                .border(1.dp, PlayerInfoCardBorder, RoundedCornerShape(20.dp))
                .padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
                // 卡片内部自行拦截点击，避免点在卡片上被当成关闭。
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChromeGlyph(PlayerChromeGlyph.Info, Modifier.size(16.dp), Color.White)
                Text(
                    "视频信息",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            section.title,
                            color = PlayerInfoSectionTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        section.entries.forEach { entry ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Text(
                                    "${entry.label}：",
                                    Modifier.width(82.dp),
                                    color = PlayerInfoEntryLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    entry.value,
                                    Modifier.weight(1f),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = if (entry.label == "标题") 3 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 选集面板（旧版 `PlayerChromePanel.Episode`）：超过 12 集用 5 列网格快速跳集，
 * 否则用带缩略信息的行式列表，并定位到当前集。
 */
@Composable
private fun PlayerEpisodePanel(
    episodes: List<cn.mafei.cmp.core.domain.MediaItem>,
    episodeIndex: Int,
    entranceVisible: Boolean,
    closing: Boolean,
    onSelectEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier,
    largeType: Boolean = false,
) {
    if (episodes.isEmpty()) return
    if (episodes.size > PlayerEpisodeListMaxCount) {
        val gridState = rememberLazyGridState(
            initialFirstVisibleItemIndex = (episodeIndex - PlayerEpisodeGridColumns)
                .coerceAtLeast(0),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(PlayerEpisodeGridColumns),
            state = gridState,
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItemsIndexed(episodes, key = { _, episode -> episode.id.value }) { index, episode ->
                val selected = index == episodeIndex
                Box(
                    Modifier.height(PlayerEpisodeGridCellHeight)
                        .playerStaggeredEntrance(
                            visible = entranceVisible && !closing,
                            index = (index / PlayerEpisodeGridColumns).coerceAtMost(5),
                            count = 6,
                            durationMs = PlayerEpisodeEntranceDurationMs,
                            staggerMs = PlayerEpisodeRowStaggerMs,
                            offsetDp = 14,
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) PlayerAccent else PlayerEpisodeCellSurface)
                        .clickable { onSelectEpisode(index) }
                        .semantics { contentDescription = episodeLabel(episode, index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        episode.episodeNumber?.toString() ?: "${index + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = if (largeType) 16.sp else MaterialTheme.typography.labelMedium.fontSize,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    } else {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = episodeIndex.coerceAtLeast(0))
        LazyColumn(
            state = listState,
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(episodes, key = { _, episode -> episode.id.value }) { index, episode ->
                val selected = index == episodeIndex
                Row(
                    Modifier.fillMaxWidth().height(PlayerEpisodeRowHeight)
                        .playerStaggeredEntrance(
                            visible = entranceVisible && !closing,
                            index = abs(index - episodeIndex).coerceAtMost(5),
                            count = 6,
                            durationMs = PlayerEpisodeEntranceDurationMs,
                            staggerMs = PlayerEpisodeRowStaggerMs,
                            offsetDp = 14,
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) PlayerEpisodeSelectedSurface else Color.Transparent)
                        .clickable { onSelectEpisode(index) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        episode.episodeNumber?.toString() ?: "${index + 1}",
                        Modifier.width(28.dp),
                        color = if (selected) PlayerAccent else PlayerMenuText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            episode.title,
                            color = if (selected) PlayerAccent else PlayerMenuText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = if (largeType) 16.sp else MaterialTheme.typography.bodyMedium.fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        episode.runtimeMs?.takeIf { it > 0L }?.let { runtime ->
                            Text(
                                formatPlayerDuration(runtime),
                                color = PlayerMenuText.copy(alpha = .66f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                    if (episode.userData.isPlayed) {
                        Text(
                            "已看",
                            color = PlayerMenuText.copy(alpha = .66f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

private fun episodeLabel(episode: cn.mafei.cmp.core.domain.MediaItem, index: Int): String =
    episode.episodeNumber?.let { "第 $it 集" } ?: "第 ${index + 1} 集"

private const val PlayerEpisodeListMaxCount = 12
private const val PlayerEpisodeGridColumns = 5
private val PlayerEpisodeGridCellHeight = 42.dp
private val PlayerEpisodeRowHeight = 56.dp
private val PlayerEpisodeCellSurface = Color(0x33FFFFFF)
private val PlayerEpisodeSelectedSurface = Color(0x2E2173DF)

private data class PlayerPanelOption(
    val id: String,
    val label: String,
    val selected: Boolean = false,
)

private fun PlayerTrack.toPanelOption(): PlayerPanelOption = PlayerPanelOption(id, label, selected)

@Composable
private fun PlayerPanelOptionRow(
    option: PlayerPanelOption,
    entranceVisible: Boolean,
    index: Int,
    count: Int,
    largeType: Boolean = false,
    rowHeight: androidx.compose.ui.unit.Dp = 44.dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val surface = when {
        pressed -> PlayerMenuSelectedSurface.copy(alpha = .46f)
        hovered -> Color.White.copy(alpha = .10f)
        else -> Color.Transparent
    }
    Box(
        Modifier.fillMaxWidth().height(rowHeight)
            .playerStaggeredEntrance(
                visible = entranceVisible,
                index = index,
                count = count,
                durationMs = PlayerMenuOptionDurationMs,
                staggerMs = PlayerMenuOptionStaggerMs,
            )
            .clip(RoundedCornerShape(6.dp))
            .background(surface)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            option.label,
            color = if (option.selected && largeType) Color(0xFF79B8FF)
                else if (option.selected) PlayerAccent else PlayerMenuText,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = if (largeType) 16.sp else MaterialTheme.typography.bodyMedium.fontSize,
            lineHeight = if (largeType) 22.sp else MaterialTheme.typography.bodyMedium.lineHeight,
            fontWeight = if (option.selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Modifier.playerStaggeredEntrance(
    visible: Boolean,
    index: Int,
    count: Int,
    durationMs: Int,
    staggerMs: Int,
    offsetDp: Int = 12,
): Modifier {
    val safeCount = count.coerceAtLeast(1)
    val safeIndex = index.coerceIn(0, safeCount - 1)
    val delayMs = if (visible) safeIndex * staggerMs else (safeCount - 1 - safeIndex) * staggerMs
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = durationMs,
            delayMillis = delayMs,
            easing = FastOutSlowInEasing,
        ),
        label = "player-panel-item",
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * offsetDp.dp.toPx()
    }
}

private fun playerPanelContentExitDurationMs(
    panel: PlayerChromePanel,
    tracks: List<PlayerTrack>,
    qualityOptions: List<PlayerQualityOption>,
    showAspectRatio: Boolean,
    showPlaybackInfo: Boolean,
): Long {
    if (panel == PlayerChromePanel.Episodes) return PlayerMenuCloseDurationMs.toLong()
    val count = when (panel) {
        PlayerChromePanel.More -> listOf(showAspectRatio, showPlaybackInfo).count { it }
        PlayerChromePanel.AspectRatio -> playerContentModeChoices.size
        PlayerChromePanel.Speed -> 6
        PlayerChromePanel.Audio -> tracks.count { it.kind == TrackKind.Audio }
        PlayerChromePanel.Subtitle -> tracks.count { it.kind == TrackKind.Subtitle } + 1
        PlayerChromePanel.Quality -> qualityOptions.size
        PlayerChromePanel.Episodes,
        PlayerChromePanel.None -> 0
    }
    return if (count <= 0) {
        PlayerMenuCloseDurationMs.toLong()
    } else {
        ((count - 1) * PlayerMenuOptionStaggerMs + PlayerMenuOptionDurationMs).toLong()
    }
}

@Composable
private fun PlayerTextControl(
    text: String,
    largeWindow: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val surfaceAlpha = when {
        pressed -> .18f
        hovered -> .11f
        else -> 0f
    }
    Box(
        modifier.height(if (largeWindow) 48.dp else 36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = surfaceAlpha))
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (hovered || pressed) 1f else .92f),
            style = MaterialTheme.typography.labelMedium,
            fontSize = if (largeWindow) 16.sp else MaterialTheme.typography.labelMedium.fontSize,
        )
    }
}

@Composable
private fun PlayerSeekFeedback(
    target: Long,
    duration: Long,
    preview: PlayerSeekPreviewFrame?,
    modifier: Modifier = Modifier,
) {
    val progress = (target.toDouble() / duration.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(PlayerGestureSeekSurface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        preview?.let { PlayerSeekPreviewImage(it) }
        // 旧版写的是「当前 / 总时长」，只报当前值看不出还剩多少。
        Text(
            "${formatPlayerDuration(target)} / ${formatPlayerDuration(duration)}",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
        )
        Box(Modifier.width(146.dp).height(3.dp).clip(CircleShape).background(PlayerGestureTrack)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(PlayerAccent))
        }
    }
}

@Composable
private fun PlayerSeekPreviewImage(frame: PlayerSeekPreviewFrame) {
    val encodedKey = frame.encodedTile.contentHashCode()
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = encodedKey,
        key2 = frame.frameWidthPx,
        key3 = frame.frameHeightPx,
    ) {
        value = withContext(Dispatchers.Default) {
            try {
                frame.encodedTile.decodeToImageBitmap()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }
    }
    Box(
        Modifier.width(220.dp).aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = .36f)),
    ) {
        bitmap?.let { image ->
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset(
                        frame.tileColumn * frame.frameWidthPx,
                        frame.tileRow * frame.frameHeightPx,
                    ),
                    srcSize = IntSize(frame.frameWidthPx, frame.frameHeightPx),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
        }
    }
}

/**
 * 旧版 `player_chrome_gesture_level_feedback`：顶部居中的胶囊，图标 + 128×4 的级别条。
 * 亮度和音量共用这一格，只有图标不同。
 */
@Composable
private fun PlayerLevelFeedback(
    level: PlayerGestureLevel,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(RoundedCornerShape(21.dp)).background(PlayerGestureSurface)
            .padding(start = 14.dp, end = 16.dp).height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChromeGlyph(
            if (level == PlayerGestureLevel.Brightness) PlayerChromeGlyph.Brightness else PlayerChromeGlyph.Volume,
            Modifier.size(20.dp),
            Color.White,
        )
        Box(Modifier.width(128.dp).height(4.dp).clip(CircleShape).background(PlayerGestureTrack)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
        }
    }
}

/** 旧版 `player_chrome_gesture_speed_feedback`：两颗播放箭头 + 文案。 */
@Composable
private fun PlayerSpeedFeedback(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(17.dp)).background(PlayerGestureSurface)
            .padding(start = 12.dp, end = 14.dp).height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            ChromeGlyph(PlayerChromeGlyph.Play, Modifier.size(14.dp), Color.White)
            ChromeGlyph(PlayerChromeGlyph.Play, Modifier.size(14.dp), Color.White)
        }
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun PlayerError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xC9000000)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChromeGlyph(PlayerChromeGlyph.Retry, Modifier.size(34.dp), Color.White)
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier.height(40.dp).clip(CircleShape).background(PlayerAccent)
                .mafeiInteractive(onClick = onRetry, radiusDp = 20f, label = "重试")
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("重试", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PlayerChromeButton(
    glyph: PlayerChromeGlyph?,
    label: String,
    diameter: Int,
    iconSize: Int,
    backdrop: GlassBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transparent: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val buttonModifier = Modifier.size(diameter.dp).semantics { contentDescription = label }
    val buttonContent: @Composable () -> Unit = {
        if (content != null) content()
        else if (glyph != null) ChromeGlyph(glyph, Modifier.size(iconSize.dp), Color.White)
    }
    Box(modifier.size(diameter.dp).hoverable(interactionSource).pointerHoverIcon(PointerIcon.Hand)) {
    if (!transparent) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = buttonModifier,
            height = diameter.dp,
            horizontalPadding = ((diameter - iconSize) / 2f).dp,
        ) { buttonContent() }
    } else {
        Box(
            buttonModifier
                .clip(CircleShape)
                .border(if (focused) 1.dp else 0.dp, if (focused) Color.White.copy(alpha = .85f) else Color.Transparent, CircleShape)
                .background(
                    when {
                        pressed -> Color.White.copy(alpha = .18f)
                        hovered -> Color.White.copy(alpha = .10f)
                        else -> Color.Transparent
                    },
                )
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            buttonContent()
        }
    }
    PlayerPointerTooltip(label, hovered)
    }
}

@Composable
fun ChromeGlyph(glyph: PlayerChromeGlyph, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val stroke = size.minDimension * .09f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = stroke) {
            drawLine(
                color,
                Offset(size.width * x1, size.height * y1),
                Offset(size.width * x2, size.height * y2),
                width,
                StrokeCap.Round,
            )
        }
        when (glyph) {
            PlayerChromeGlyph.More -> {
                val radius = size.minDimension * .075f
                listOf(.24f, .5f, .76f).forEach { drawCircle(color, radius, Offset(size.width * it, size.height * .5f)) }
            }
            PlayerChromeGlyph.Play -> drawPath(Path().apply {
                moveTo(size.width * .3f, size.height * .16f)
                lineTo(size.width * .84f, size.height * .5f)
                lineTo(size.width * .3f, size.height * .84f)
                close()
            }, color)
            PlayerChromeGlyph.Pause -> {
                drawRoundRect(color, Offset(size.width * .22f, size.height * .16f), Size(size.width * .2f, size.height * .68f))
                drawRoundRect(color, Offset(size.width * .58f, size.height * .16f), Size(size.width * .2f, size.height * .68f))
            }
            PlayerChromeGlyph.Retry -> {
                drawArc(color, -55f, 285f, false, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawPath(Path().apply {
                    moveTo(size.width * .7f, size.height * .05f)
                    lineTo(size.width * .92f, size.height * .18f)
                    lineTo(size.width * .68f, size.height * .3f)
                    close()
                }, color)
            }
            PlayerChromeGlyph.Fullscreen -> {
                line(.12f, .38f, .12f, .12f); line(.12f, .12f, .38f, .12f)
                line(.62f, .12f, .88f, .12f); line(.88f, .12f, .88f, .38f)
                line(.12f, .62f, .12f, .88f); line(.12f, .88f, .38f, .88f)
                line(.62f, .88f, .88f, .88f); line(.88f, .62f, .88f, .88f)
            }
            PlayerChromeGlyph.ExitFullscreen -> {
                line(.12f, .38f, .38f, .38f); line(.38f, .38f, .38f, .12f)
                line(.62f, .12f, .62f, .38f); line(.62f, .38f, .88f, .38f)
                line(.12f, .62f, .38f, .62f); line(.38f, .62f, .38f, .88f)
                line(.62f, .88f, .62f, .62f); line(.62f, .62f, .88f, .62f)
            }
            PlayerChromeGlyph.WindowFullscreen, PlayerChromeGlyph.WindowRestore -> {
                drawRoundRect(color, Offset(size.width * .12f, size.height * .22f),
                    Size(size.width * .76f, size.height * .56f),
                    androidx.compose.ui.geometry.CornerRadius(size.width * .05f), style = Stroke(stroke))
                if (glyph == PlayerChromeGlyph.WindowFullscreen) {
                    line(.26f, .4f, .26f, .64f); line(.26f, .64f, .52f, .64f)
                    line(.48f, .36f, .74f, .36f); line(.74f, .36f, .74f, .60f)
                } else {
                    line(.24f, .48f, .44f, .48f); line(.44f, .48f, .44f, .3f)
                    line(.56f, .7f, .56f, .52f); line(.56f, .52f, .76f, .52f)
                }
            }
            PlayerChromeGlyph.Orientation -> {
                // Screen-rotation glyph: a small device frame makes this
                // distinct from the generic refresh/sync icon at a glance.
                drawRoundRect(
                    color,
                    topLeft = Offset(size.width * .36f, size.height * .27f),
                    size = Size(size.width * .28f, size.height * .46f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .05f),
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color,
                    startAngle = 35f,
                    sweepAngle = 220f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color,
                    startAngle = 215f,
                    sweepAngle = 220f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawPath(Path().apply {
                    moveTo(size.width * .80f, size.height * .22f)
                    lineTo(size.width * .90f, size.height * .20f)
                    lineTo(size.width * .86f, size.height * .30f)
                    close()
                }, color)
                drawPath(Path().apply {
                    moveTo(size.width * .20f, size.height * .78f)
                    lineTo(size.width * .10f, size.height * .80f)
                    lineTo(size.width * .14f, size.height * .70f)
                    close()
                }, color)
            }
            PlayerChromeGlyph.Lock, PlayerChromeGlyph.Unlock -> {
                drawRoundRect(
                    color,
                    Offset(size.width * .22f, size.height * .43f),
                    Size(size.width * .56f, size.height * .45f),
                    androidx.compose.ui.geometry.CornerRadius(size.width * .1f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawPath(Path().apply {
                    moveTo(size.width * (if (glyph == PlayerChromeGlyph.Unlock) .38f else .3f), size.height * .43f)
                    cubicTo(
                        size.width * (if (glyph == PlayerChromeGlyph.Unlock) .3f else .3f), size.height * .13f,
                        size.width * .7f, size.height * .13f,
                        size.width * .7f, size.height * .43f,
                    )
                }, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            PlayerChromeGlyph.Previous -> {
                drawPath(Path().apply {
                    moveTo(size.width * .82f, size.height * .18f)
                    lineTo(size.width * .34f, size.height * .5f)
                    lineTo(size.width * .82f, size.height * .82f)
                    close()
                }, color)
                line(.24f, .18f, .24f, .82f)
            }
            PlayerChromeGlyph.Next -> {
                drawPath(Path().apply {
                    moveTo(size.width * .18f, size.height * .18f)
                    lineTo(size.width * .66f, size.height * .5f)
                    lineTo(size.width * .18f, size.height * .82f)
                    close()
                }, color)
                line(.76f, .18f, .76f, .82f)
            }
            PlayerChromeGlyph.Brightness -> {
                drawCircle(color, size.minDimension * .2f, Offset(size.width * .5f, size.height * .5f))
                listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f).forEach { degrees ->
                    val radians = degrees * kotlin.math.PI.toFloat() / 180f
                    val dx = kotlin.math.cos(radians)
                    val dy = kotlin.math.sin(radians)
                    line(
                        .5f + dx * .32f, .5f + dy * .32f,
                        .5f + dx * .46f, .5f + dy * .46f,
                        stroke * .8f,
                    )
                }
            }
            PlayerChromeGlyph.Volume, PlayerChromeGlyph.Muted -> {
                drawPath(Path().apply {
                    moveTo(size.width * .12f, size.height * .38f)
                    lineTo(size.width * .3f, size.height * .38f)
                    lineTo(size.width * .52f, size.height * .18f)
                    lineTo(size.width * .52f, size.height * .82f)
                    lineTo(size.width * .3f, size.height * .62f)
                    lineTo(size.width * .12f, size.height * .62f)
                    close()
                }, color)
                if (glyph == PlayerChromeGlyph.Muted) {
                    line(.65f, .35f, .9f, .65f); line(.9f, .35f, .65f, .65f)
                } else drawArc(
                    color, -55f, 110f, false,
                    topLeft = Offset(size.width * .42f, size.height * .3f),
                    size = Size(size.width * .3f, size.height * .4f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (glyph != PlayerChromeGlyph.Muted) drawArc(
                    color, -55f, 110f, false,
                    topLeft = Offset(size.width * .5f, size.height * .2f),
                    size = Size(size.width * .42f, size.height * .6f),
                    style = Stroke(stroke * .8f, cap = StrokeCap.Round),
                )
            }
            PlayerChromeGlyph.Info -> {
                drawCircle(color, size.minDimension * .46f, Offset(size.width * .5f, size.height * .5f), style = Stroke(stroke))
                drawRoundRect(
                    color,
                    Offset(size.width * .47f, size.height * .43f),
                    Size(size.width * .06f, size.height * .3f),
                    androidx.compose.ui.geometry.CornerRadius(size.width * .03f),
                )
                drawCircle(color, size.minDimension * .045f, Offset(size.width * .5f, size.height * .32f))
            }
        }
    }
}

private fun playbackGlyph(presentation: NowPlayingPresentation): PlayerChromeGlyph = when {
    presentation.showRetry -> PlayerChromeGlyph.Retry
    presentation.isPlaying -> PlayerChromeGlyph.Pause
    else -> PlayerChromeGlyph.Play
}

private fun playbackLabel(presentation: NowPlayingPresentation): String = when {
    presentation.showRetry -> "重试"
    presentation.isPlaying -> "暂停"
    else -> "播放"
}
