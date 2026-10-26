package cn.mafei.cmp.player.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassMaterial
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.currentGlassStyle
import cn.mafei.cmp.common.design_system.MaFeiGlassDefaults
import cn.mafei.cmp.common.ui.mafeiDismissOnOutsidePointer
import cn.mafei.cmp.player.api.PlayerContentMode
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.TrackKind
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val PlayerMenuCloseDurationMs = 200

private const val PlayerMenuOptionDurationMs = 130

private const val PlayerMenuOptionStaggerMs = 28

internal val PlayerMenuText = Color(0xE6FFFFFF)

private val PlayerMenuDivider = Color(0x45FFFFFF)

private val PlayerMenuSelectedSurface = Color(0x542173DF)

private val PlayerMenuGradient = Brush.horizontalGradient(
    0f to Color.Transparent,
    .42f to Color(0x8A000000),
    1f to Color(0xDC000000),
)

internal enum class PlayerChromePanel { None, More, AspectRatio, Speed, Audio, Subtitle, Episodes, Quality }

@Composable
internal fun PlayerSidePanel(
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
    showContentReport: Boolean,
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
    onReportContent: () -> Unit,
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
        val optionCount = if (isMorePanel) listOf(showAspectRatio, showPlaybackInfo, showContentReport).count { it }
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
                    showContentReport = showContentReport,
                    entranceVisible = entranceVisible,
                    closing = closing,
                    onAspectRatio = { onOpenPanel(PlayerChromePanel.AspectRatio) },
                    onPlaybackInfo = onOpenPlaybackInfo,
                    onReportContent = onReportContent,
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
 * 「更多」面板：只显示当前宿主实际提供的播放器操作。
 * 任一入口在当前平台不存在时只画存在的那个，绝不画一条点了没反应的死行。
 */
@Composable
private fun PlayerMorePanel(
    aspectRatioLabel: String,
    showAspectRatio: Boolean,
    showPlaybackInfo: Boolean,
    showContentReport: Boolean,
    entranceVisible: Boolean,
    closing: Boolean,
    onAspectRatio: () -> Unit,
    onPlaybackInfo: () -> Unit,
    onReportContent: () -> Unit,
    modifier: Modifier = Modifier,
    largeType: Boolean = false,
    rowHeight: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val count = listOf(showAspectRatio, showPlaybackInfo, showContentReport).count { it }
    var index = 0
    Column(modifier) {
        if (showAspectRatio) {
            PlayerPanelOptionRow(
                option = PlayerPanelOption("aspect", "画面比例 · $aspectRatioLabel"),
                entranceVisible = entranceVisible && !closing,
                index = index++,
                count = count,
                largeType = largeType,
                rowHeight = rowHeight,
                onClick = onAspectRatio,
            )
        }
        if (showAspectRatio && (showPlaybackInfo || showContentReport)) {
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(PlayerMenuDivider),
            )
        }
        if (showPlaybackInfo) {
            PlayerPanelOptionRow(
                option = PlayerPanelOption("info", "播放信息"),
                entranceVisible = entranceVisible && !closing,
                index = index++,
                count = count,
                largeType = largeType,
                rowHeight = rowHeight,
                onClick = onPlaybackInfo,
            )
        }
        if (showPlaybackInfo && showContentReport) {
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(PlayerMenuDivider),
            )
        }
        if (showContentReport) {
            PlayerPanelOptionRow(
                option = PlayerPanelOption("report", "举报内容"),
                entranceVisible = entranceVisible && !closing,
                index = index,
                count = count,
                largeType = largeType,
                rowHeight = rowHeight,
                onClick = onReportContent,
            )
        }
    }
}

/** Panel option ids are the enum names, so the ratio table needs no side map. */
private fun PlayerChromeModeById(id: String): PlayerContentMode =
    playerContentModeChoices.firstOrNull { it.name == id }
        ?: PlayerContentMode.Fit

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
internal fun Modifier.playerStaggeredEntrance(
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

internal fun playerPanelContentExitDurationMs(
    panel: PlayerChromePanel,
    tracks: List<PlayerTrack>,
    qualityOptions: List<PlayerQualityOption>,
    showAspectRatio: Boolean,
    showPlaybackInfo: Boolean,
    showContentReport: Boolean = false,
): Long {
    if (panel == PlayerChromePanel.Episodes) return PlayerMenuCloseDurationMs.toLong()
    val count = when (panel) {
        PlayerChromePanel.More -> listOf(showAspectRatio, showPlaybackInfo, showContentReport).count { it }
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
