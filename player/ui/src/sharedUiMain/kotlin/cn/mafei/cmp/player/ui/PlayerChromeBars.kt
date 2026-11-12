package cn.mafei.cmp.player.ui

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.painterResource
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerDeviceControls
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.TrackKind
import kotlinx.coroutines.delay
import kotlin.math.abs

private val PlayerControlMask = Color(0x99000000)

private val PlayerFullscreenControlMask = Color(0xB0000000)

internal val PlayerAccent = Color(0xFF2173DF)

@Composable
internal fun PlayerWindowHeader(
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
internal fun PlayerTopChrome(
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
    val windowControlEndInset = if (windowFeatures.windowedHost && !windowFeatures.windowFullscreen) {
        windowFeatures.windowControlEndInsetDp.dp
    } else {
        0.dp
    }
    val windowControlTopInset = if (windowFeatures.windowedHost && !windowFeatures.windowFullscreen) {
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
internal fun PlayerBottomChrome(
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
    onWindowFullscreen: (() -> Unit)? = null,
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
                if (onWindowFullscreen != null) PlayerChromeButton(
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
internal fun PlayerChromeButton(
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
