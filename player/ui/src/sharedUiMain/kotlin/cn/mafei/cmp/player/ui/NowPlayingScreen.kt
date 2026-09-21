package cn.mafei.cmp.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.TrackKind
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

private const val PlayerAutoHideDelayMs = 4_000L
private const val PlayerLongPressSpeed = 3f

private val PlayerControlSurface = Color(0x52000000)
private val PlayerControlMask = Color(0x3D000000)
private val PlayerFullscreenControlMask = Color(0x52000000)
private val PlayerGestureSeekSurface = Color(0x85000000)
private val PlayerGestureTrack = Color(0x66FFFFFF)
private val PlayerMenuText = Color(0xE6FFFFFF)
private val PlayerMenuSecondaryText = Color(0x99FFFFFF)
private val PlayerMenuDivider = Color(0x24FFFFFF)
private val PlayerMenuCell = Color(0x26000000)
private val PlayerMenuSelectedCell = Color(0x3DFFFFFF)
private val PlayerMenuMiddle = Color(0x8A000000)
private val PlayerMenuEnd = Color(0xDC000000)
private val PlayerAccent = Color(0xFF2173DF)

private enum class PlayerChromePanel { None, Speed, Audio, Subtitle }

private enum class PlayerChromeGlyph { More, Play, Pause, Retry, Fullscreen, Lock, Unlock, Next }

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
    capabilities: PlayerCapabilities,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onSelectTrack: (TrackKind, String?) -> Unit,
    videoSurface: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    onFullscreenToggle: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val backdrop = rememberGlassBackdrop()
    val presentation = state.toNowPlayingPresentation(capabilities)
    val safeArea = currentMaFeiSafeArea()
    val latestTogglePlay by rememberUpdatedState(onTogglePlay)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestSpeed by rememberUpdatedState(onSpeed)
    val latestPlaybackSpeed by rememberUpdatedState(state.speed)

    var controlsVisible by remember(media.id, state.generation) { mutableStateOf(true) }
    var locked by remember(media.id, state.generation) { mutableStateOf(false) }
    var panel by remember(media.id, state.generation) { mutableStateOf(PlayerChromePanel.None) }
    var autoHideGeneration by remember(media.id, state.generation) { mutableIntStateOf(0) }
    var sliderPositionMs by remember(media.id, state.generation) { mutableLongStateOf(state.positionMs) }
    var draggingSlider by remember(media.id, state.generation) { mutableStateOf(false) }
    var seekFeedbackMs by remember(media.id, state.generation) { mutableStateOf<Long?>(null) }
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    var longPressSpeedActive by remember(media.id, state.generation) { mutableStateOf(false) }
    var speedBeforeLongPress by remember(media.id, state.generation) { mutableFloatStateOf(1f) }
    var busyVisible by remember(media.id, state.generation) { mutableStateOf(false) }

    LaunchedEffect(state.positionMs, draggingSlider) {
        if (!draggingSlider) sliderPositionMs = state.positionMs.coerceAtLeast(0L)
    }
    LaunchedEffect(controlsVisible, panel, locked, state.phase, autoHideGeneration) {
        if (controlsVisible && panel == PlayerChromePanel.None && !locked && state.phase == PlayerPhase.Playing) {
            delay(PlayerAutoHideDelayMs)
            controlsVisible = false
        }
    }
    LaunchedEffect(presentation.isBusy, state.generation) {
        if (presentation.isBusy) {
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

    BoxWithConstraints(
        modifier.background(Color.Black).onSizeChanged { sceneSize = it },
    ) {
        val fullscreen = maxWidth > maxHeight
        val durationMs = state.durationMs?.takeIf { it > 0L }

        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
            videoSurface(Modifier.fillMaxSize())
        }

        Box(
            Modifier.fillMaxSize()
                .pointerInput(media.id, state.generation, locked) {
                    detectTapGestures(
                        onTap = {
                            if (!locked) {
                                controlsVisible = !controlsVisible
                                if (controlsVisible) autoHideGeneration++
                            }
                        },
                        onDoubleTap = {
                            if (!locked) {
                                latestTogglePlay()
                                showControlsTemporarily()
                            }
                        },
                        onLongPress = {
                            if (!locked && presentation.isPlaying && capabilities.speed) {
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
                    val total = durationMs ?: return@pointerInput
                    if (locked || sceneSize.width <= 0) return@pointerInput
                    var accumulatedX = 0f
                    var accumulatedY = 0f
                    var dragAxis = 0
                    var startPosition = 0L
                    detectDragGestures(
                        onDragStart = {
                            accumulatedX = 0f
                            accumulatedY = 0f
                            dragAxis = 0
                            startPosition = state.positionMs.coerceIn(0L, total)
                        },
                        onDrag = { change, dragAmount ->
                            accumulatedX += dragAmount.x
                            accumulatedY += dragAmount.y
                            if (dragAxis == 0 && maxOf(abs(accumulatedX), abs(accumulatedY)) >= 4f) {
                                dragAxis = if (abs(accumulatedX) >= abs(accumulatedY)) 1 else 2
                            }
                            if (dragAxis == 1) {
                                change.consume()
                                val delta = total.toDouble() * accumulatedX / sceneSize.width.toDouble()
                                seekFeedbackMs = (startPosition + delta.roundToLong()).coerceIn(0L, total)
                            }
                        },
                        onDragCancel = { seekFeedbackMs = null },
                        onDragEnd = {
                            if (dragAxis == 1) seekFeedbackMs?.let(latestSeek)
                            seekFeedbackMs = null
                            if (dragAxis == 1) showControlsTemporarily()
                        },
                    )
                },
        )

        if (!fullscreen && !locked && panel == PlayerChromePanel.None) {
            PlayerChromeButton(
                glyph = null,
                label = "返回",
                diameter = 44,
                iconSize = 22,
                backdrop = backdrop,
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(start = 12.dp + safeArea.start, top = 10.dp + safeArea.top),
            ) {
                MaFeiIcon(MaFeiGlyph.Back, Modifier.size(22.dp), Color.White)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn() + slideInVertically { -24 },
            exit = fadeOut() + slideOutVertically { -24 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlayerTopChrome(
                title = media.title,
                fullscreen = fullscreen,
                safeStart = safeArea.start,
                safeTop = safeArea.top,
                safeEnd = safeArea.end,
                backdrop = backdrop,
                // Legacy "more" only contains aspect ratio and playback info.
                // Neither contract exists in the CMP player API yet, so do not
                // remap this command to speed or track selection.
                showMenu = false,
                onBack = onBack,
                onMenu = {},
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn() + slideInVertically { 24 },
            exit = fadeOut() + slideOutVertically { 24 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerBottomChrome(
                state = state,
                presentation = presentation,
                fullscreen = fullscreen,
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
                onFullscreenToggle = onFullscreenToggle,
                onNext = onNext,
                onLock = { locked = true; controlsVisible = false; panel = PlayerChromePanel.None },
                onPanel = { panel = it; controlsVisible = true },
                capabilities = capabilities,
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

        seekFeedbackMs?.let { target ->
            PlayerSeekFeedback(target, durationMs ?: 1L, Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = busyVisible && state.phase != PlayerPhase.Error,
            enter = fadeIn(tween(160)) + scaleIn(tween(180), initialScale = .92f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 1.04f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        AnimatedVisibility(
            visible = presentation.showRetry,
            enter = fadeIn(tween(180)) + scaleIn(tween(200), initialScale = .94f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = .98f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            PlayerError(onRetry)
        }

        if (panel != PlayerChromePanel.None) {
            PlayerSidePanel(
                panel = panel,
                state = state,
                safeEnd = safeArea.end,
                onDismiss = {
                    panel = PlayerChromePanel.None
                    showControlsTemporarily()
                },
                onSpeed = { value ->
                    latestSpeed(value)
                    panel = PlayerChromePanel.None
                    showControlsTemporarily()
                },
                onSelectTrack = { kind, id ->
                    onSelectTrack(kind, id)
                    panel = PlayerChromePanel.None
                    showControlsTemporarily()
                },
            )
        }
    }
}

@Composable
private fun PlayerTopChrome(
    title: String,
    fullscreen: Boolean,
    safeStart: androidx.compose.ui.unit.Dp,
    safeTop: androidx.compose.ui.unit.Dp,
    safeEnd: androidx.compose.ui.unit.Dp,
    backdrop: GlassBackdrop,
    showMenu: Boolean,
    onBack: () -> Unit,
    onMenu: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth()
            .height((if (fullscreen) 96.dp else 88.dp) + safeTop)
            .background(
                Brush.verticalGradient(
                    0f to (if (fullscreen) PlayerFullscreenControlMask else PlayerControlMask),
                    1f to Color.Transparent,
                ),
            ),
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(
                    start = if (fullscreen) safeStart + 20.dp else safeStart + 68.dp,
                    end = safeEnd + 12.dp,
                    top = safeTop + if (fullscreen) 16.dp else 10.dp,
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showMenu) {
                PlayerChromeButton(
                    glyph = PlayerChromeGlyph.More,
                    label = "更多设置",
                    diameter = 44,
                    iconSize = 22,
                    backdrop = backdrop,
                    onClick = onMenu,
                )
            }
        }
    }
}

@Composable
private fun PlayerBottomChrome(
    state: PlayerState,
    presentation: NowPlayingPresentation,
    fullscreen: Boolean,
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
    onFullscreenToggle: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onLock: () -> Unit,
    onPanel: (PlayerChromePanel) -> Unit,
    capabilities: PlayerCapabilities,
) {
    val audioTracks = state.tracks.filter { it.kind == TrackKind.Audio }
    val subtitleTracks = state.tracks.filter { it.kind == TrackKind.Subtitle }
    val duration = state.durationMs?.takeIf { it > 0L }
    val maskHeight = if (fullscreen) 132.dp else 106.dp

    Box(
        Modifier.fillMaxWidth().height(maskHeight + safeBottom)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to (if (fullscreen) PlayerFullscreenControlMask else PlayerControlMask),
                ),
            ),
    ) {
        if (fullscreen) {
            PlayerChromeButton(
                glyph = PlayerChromeGlyph.Lock,
                label = "锁定",
                diameter = 44,
                iconSize = 22,
                backdrop = backdrop,
                onClick = onLock,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = safeStart + 20.dp),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(
                    start = safeStart + if (fullscreen) 20.dp else 8.dp,
                    end = safeEnd + if (fullscreen) 20.dp else 8.dp,
                    bottom = safeBottom + 14.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (fullscreen) 2.dp else 0.dp),
        ) {
            if (fullscreen) {
                PlayerTimelineRow(
                    state = state,
                    positionMs = sliderPositionMs,
                    durationMs = duration,
                    thumbDiameter = 14,
                    onDraggingChanged = onDraggingChanged,
                    onPositionChanged = onSliderPositionChanged,
                    onSeek = onSeek,
                    modifier = Modifier.height(28.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PlayerChromeButton(
                        glyph = playbackGlyph(presentation),
                        label = playbackLabel(presentation),
                        diameter = 30,
                        iconSize = 16,
                        backdrop = backdrop,
                        transparent = true,
                        onClick = if (presentation.showRetry) onRetry else onTogglePlay,
                    )
                    if (onNext != null) {
                        PlayerChromeButton(
                            glyph = PlayerChromeGlyph.Next,
                            label = "下一集",
                            diameter = 30,
                            iconSize = 16,
                            backdrop = backdrop,
                            transparent = true,
                            onClick = onNext,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (capabilities.trackSelection && audioTracks.size > 1) {
                        PlayerTextControl("音轨") { onPanel(PlayerChromePanel.Audio) }
                    }
                    if (capabilities.trackSelection && subtitleTracks.isNotEmpty()) {
                        PlayerTextControl("字幕") { onPanel(PlayerChromePanel.Subtitle) }
                    }
                    if (capabilities.speed) {
                        PlayerTextControl(if (abs(state.speed - 1f) < .01f) "倍速" else "${state.speed}x") {
                            onPanel(PlayerChromePanel.Speed)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerChromeButton(
                        glyph = playbackGlyph(presentation),
                        label = playbackLabel(presentation),
                        diameter = 46,
                        iconSize = 24,
                        backdrop = backdrop,
                        onClick = if (presentation.showRetry) onRetry else onTogglePlay,
                    )
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
                    if (onFullscreenToggle != null) {
                        PlayerChromeButton(
                            glyph = PlayerChromeGlyph.Fullscreen,
                            label = "全屏",
                            diameter = 40,
                            iconSize = 20,
                            backdrop = backdrop,
                            onClick = onFullscreenToggle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTimelineRow(
    state: PlayerState,
    positionMs: Long,
    durationMs: Long?,
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
            enabled = durationMs != null,
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
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragValue by remember { mutableLongStateOf(valueMs) }
    var directManipulation by remember { mutableStateOf(false) }
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
            .semantics { contentDescription = "播放进度" }
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
        val trackHeight = 4.dp.toPx()
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
    state: PlayerState,
    safeEnd: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onSpeed: (Float) -> Unit,
    onSelectTrack: (TrackKind, String?) -> Unit,
) {
    val (title, width, options) = when (panel) {
        PlayerChromePanel.Speed -> Triple(
            "播放速度",
            138.dp,
            listOf(3f, 2f, 1.5f, 1.25f, 1f, .75f).map { speed ->
                PlayerPanelOption(speed.toString(), "${speed}x", abs(state.speed - speed) < .01f)
            },
        )
        PlayerChromePanel.Audio -> Triple(
            "音轨",
            220.dp,
            state.tracks.filter { it.kind == TrackKind.Audio }.map(PlayerTrack::toPanelOption),
        )
        PlayerChromePanel.Subtitle -> Triple(
            "字幕",
            220.dp,
            listOf(PlayerPanelOption("", "关闭字幕")) +
                state.tracks.filter { it.kind == TrackKind.Subtitle }.map(PlayerTrack::toPanelOption),
        )
        PlayerChromePanel.None -> return
    }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onDismiss))
        Column(
            modifier = Modifier.width(width + safeEnd).fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        .42f to PlayerMenuMiddle,
                        1f to PlayerMenuEnd,
                    ),
                )
                .padding(start = 16.dp, end = safeEnd + 16.dp, top = 18.dp, bottom = 18.dp),
        ) {
            Text(
                title,
                modifier = Modifier.fillMaxWidth().height(38.dp),
                color = PlayerMenuText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(options, key = { it.id }) { option ->
                    PlayerPanelOptionRow(option) {
                        when (panel) {
                            PlayerChromePanel.Speed -> onSpeed(option.id.toFloat())
                            PlayerChromePanel.Audio -> onSelectTrack(TrackKind.Audio, option.id)
                            PlayerChromePanel.Subtitle -> onSelectTrack(TrackKind.Subtitle, option.id.ifEmpty { null })
                            PlayerChromePanel.None -> Unit
                        }
                    }
                }
            }
        }
    }
}

private data class PlayerPanelOption(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    val secondary: String? = null,
)

private fun PlayerTrack.toPanelOption(): PlayerPanelOption = PlayerPanelOption(id, label, secondary = language)

@Composable
private fun PlayerPanelOptionRow(option: PlayerPanelOption, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (option.selected) PlayerMenuSelectedCell else PlayerMenuCell)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(option.label, color = PlayerMenuText, style = MaterialTheme.typography.bodyMedium)
        option.secondary?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = PlayerMenuSecondaryText, style = MaterialTheme.typography.labelSmall)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(PlayerMenuDivider))
}

@Composable
private fun PlayerTextControl(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PlayerSeekFeedback(target: Long, duration: Long, modifier: Modifier = Modifier) {
    val progress = (target.toDouble() / duration.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(PlayerGestureSeekSurface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(formatPlayerDuration(target), color = Color.White, style = MaterialTheme.typography.labelLarge)
        Box(Modifier.width(146.dp).height(3.dp).clip(CircleShape).background(PlayerGestureTrack)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(PlayerAccent))
        }
    }
}

@Composable
private fun PlayerError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xC9000000)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChromeGlyph(PlayerChromeGlyph.Retry, Modifier.size(34.dp), Color.White)
        Text("视频播放失败", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier.height(40.dp).clip(CircleShape).background(PlayerAccent)
                .clickable(onClick = onRetry).padding(horizontal = 20.dp),
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
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.size(diameter.dp).semantics { contentDescription = label },
        surfaceColor = if (transparent) Color.Transparent else PlayerControlSurface,
        height = diameter.dp,
        horizontalPadding = ((diameter - iconSize) / 2f).dp,
    ) {
        if (content != null) content()
        else if (glyph != null) ChromeGlyph(glyph, Modifier.size(iconSize.dp), Color.White)
    }
}

@Composable
private fun ChromeGlyph(glyph: PlayerChromeGlyph, modifier: Modifier = Modifier, color: Color = Color.White) {
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
            PlayerChromeGlyph.Next -> {
                drawPath(Path().apply {
                    moveTo(size.width * .18f, size.height * .18f)
                    lineTo(size.width * .66f, size.height * .5f)
                    lineTo(size.width * .18f, size.height * .82f)
                    close()
                }, color)
                line(.76f, .18f, .76f, .82f)
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
