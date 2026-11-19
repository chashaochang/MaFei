package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import cn.mafei.cmp.player.ui.PlayerSeekPreviewImage
import cn.mafei.cmp.player.ui.formatPlayerDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

internal fun shortDramaSeekPosition(fraction: Float, duration: Long): Long =
    (fraction.coerceIn(0f, 1f).toDouble() * duration.coerceAtLeast(0L)).roundToLong()

internal fun shortDramaSynopsis(work: String?, episode: String?): String? =
    sequenceOf(work, episode).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull()

internal class ShortDramaScrub {
    var target by mutableStateOf<Long?>(null)
        private set

    fun moveTo(position: Long) { target = position }

    fun finish(commit: Boolean): Long? = target.takeIf { commit }.also { target = null }
}

@Composable
internal fun ShortDramaPlaybackChrome(
    title: String,
    synopsis: String?,
    position: Long,
    duration: Long?,
    selectionLabel: String?,
    onDetails: (() -> Unit)?,
    onEpisodes: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubbing: (Boolean) -> Unit,
    loadPreview: suspend (Long) -> PlayerSeekPreviewFrame?,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    synopsisMaxHeight: Dp = 140.dp,
) {
    val total = duration?.takeIf { it > 0L }
    val scrub = remember { ShortDramaScrub() }
    val target = scrub.target
    val currentTotal by rememberUpdatedState(total)
    val currentSeek by rememberUpdatedState(onSeek)
    val currentScrubbing by rememberUpdatedState(onScrubbing)
    val currentLoadPreview by rememberUpdatedState(loadPreview)
    val endScrub: (Boolean) -> Unit = { commit ->
        if (scrub.target != null) currentScrubbing(false)
        scrub.finish(commit)?.let(currentSeek)
    }
    DisposableEffect(Unit) {
        onDispose { if (scrub.target != null) currentScrubbing(false) }
    }
    val preview by produceState<PlayerSeekPreviewFrame?>(null, target) {
        value = null
        val time = target ?: return@produceState
        delay(90)
        value = try {
            currentLoadPreview(time)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
    val shownPosition = target ?: position
    val progress = total?.let { (shownPosition.toDouble() / it).toFloat().coerceIn(0f, 1f) } ?: 0f
    Column(modifier.widthIn(max = 660.dp).fillMaxWidth().padding(horizontal = 18.dp)) {
        if (target != null && total != null) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (preview != null) {
                    PlayerSeekPreviewImage(preview!!, Modifier.width(160.dp).aspectRatio(16f / 9f))
                }
                Text(
                    "${formatPlayerDuration(shownPosition)} / ${formatPlayerDuration(total)}",
                    color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        } else if (!fullscreen) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 34.dp).then(
                    if (onDetails == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onDetails),
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 19.sp,
                    modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (onDetails != null) MaFeiIcon(MaFeiGlyph.Chevron, color = Color.White)
            }
            synopsis?.let {
                ShortDramaSynopsis(
                    it, TextStyle(color = Color(0xFFBBBCC1), fontSize = 13.sp, lineHeight = 19.sp),
                    expandedMaxHeight = synopsisMaxHeight,
                )
            }
        }
        // This hit region stays mounted while the track and controls above it change.
        Box(
            Modifier.fillMaxWidth().height(36.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { point ->
                            currentTotal?.let {
                                scrub.moveTo(shortDramaSeekPosition(point.x / size.width.coerceAtLeast(1), it))
                                currentScrubbing(true)
                            }
                        },
                        onDragEnd = { endScrub(true) },
                        onDragCancel = { endScrub(false) },
                    ) { change, _ ->
                        currentTotal?.let {
                            scrub.moveTo(shortDramaSeekPosition(change.position.x / size.width.coerceAtLeast(1), it))
                            change.consume()
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { point ->
                        currentTotal?.let { currentSeek(shortDramaSeekPosition(point.x / size.width.coerceAtLeast(1), it)) }
                    }
                }
                .semantics {
                    contentDescription = "播放进度"
                    if (total != null) {
                        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                        setProgress { currentSeek(shortDramaSeekPosition(it, total)); true }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (target == null) {
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color(0xFF48484A))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
                }
            } else {
                Box(Modifier.fillMaxWidth().height(15.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF48484A))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(Color(0xFFDADAE0)))
                }
            }
        }
        selectionLabel?.let { label ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF242426)).clickable(role = Role.Button, onClick = onEpisodes)
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(1f), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                MaFeiIcon(MaFeiGlyph.Chevron, Modifier.rotate(-90f), color = Color.White)
            }
        }
    }
}

@Composable
internal fun ShortDramaSynopsis(text: String, style: TextStyle, expandedMaxHeight: Dp = Dp.Unspecified) {
    var expanded by remember(text) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val truncated = remember(text, style, width, measurer) {
            measurer.measure(text, style, maxLines = 2, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = width)).hasVisualOverflow
        }
        Column {
            val showAll = expanded && truncated
            Row(verticalAlignment = Alignment.Bottom) {
                val body = if (showAll && expandedMaxHeight != Dp.Unspecified) {
                    Modifier.heightIn(max = expandedMaxHeight).verticalScroll(rememberScrollState())
                } else Modifier
                Text(
                    text, Modifier.weight(1f).then(body), style = style,
                    maxLines = if (showAll) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (truncated && !showAll) {
                    ShortDramaSynopsisAction("展开", Modifier.padding(start = 8.dp)) { expanded = true }
                }
            }
            if (showAll) {
                ShortDramaSynopsisAction("收起", Modifier.align(Alignment.End)) { expanded = false }
            }
        }
    }
}

@Composable
private fun ShortDramaSynopsisAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.sizeIn(minWidth = 44.dp, minHeight = 36.dp)
            .clickable(role = Role.Button, onClick = onClick).padding(bottom = 2.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
