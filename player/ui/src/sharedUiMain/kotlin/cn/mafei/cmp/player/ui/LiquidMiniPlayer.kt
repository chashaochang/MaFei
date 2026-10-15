package cn.mafei.cmp.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidMiniBarSurface
import cn.mafei.cmp.common.ui.mafeiInteractive
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.min

/**
 * One continuously resizing player surface. [compactProgress] must come from
 * the caller's stable scroll/gesture distance; this component never adds a
 * second chasing animation on top of that source of truth.
 */
@Composable
fun LiquidMiniPlayer(
    media: MiniPlayerMedia,
    presentation: MiniPlayerPresentation,
    backdrop: GlassBackdrop,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    artwork: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    compactProgress: () -> Float = { 0f },
) {
    if (!presentation.isVisible) return

    var entered by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(media.id) { entered = true }
    val entrance = animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(180),
        label = "mini-player-entrance",
    )
    val smoothProgress = animateFloatAsState(
        targetValue = presentation.progress.coerceIn(0f, 1f),
        animationSpec = tween(360, easing = LinearEasing),
        label = "mini-player-progress",
    )
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() >= .5f
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val secondaryColor = contentColor.copy(alpha = .58f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryText = listOfNotNull(
        media.subtitle.takeIf { it.isNotBlank() },
        presentation.status.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    BoxWithConstraints(
        modifier.graphicsLayer {
            alpha = entrance.value
            translationY = (1f - entrance.value) * 8.dp.toPx()
        },
    ) {
        val progress = compactProgress().fastCoerceIn(0f, 1f)
        val compactWidth = min(maxWidth.value, 248f).dp
        val playerWidth = maxWidth + (compactWidth - maxWidth) * progress
        val albumArtSize = 44.dp + (40.dp - 44.dp) * progress

        LiquidMiniBarSurface(
            backdrop = backdrop,
            onClick = onOpen,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(playerWidth)
                .height(58.dp),
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(albumArtSize).clip(RoundedRectangle(14.dp))) {
                    artwork(Modifier.fillMaxSize())
                }
                Column(
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                ) {
                    Text(
                        media.title,
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        secondaryText,
                        color = secondaryColor,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(42.dp)
                        .clip(Capsule())
                        .mafeiInteractive(
                            onClick = onTogglePlay,
                            enabled = !presentation.isBusy || presentation.isPlaying,
                            radiusDp = 21f,
                            label = if (presentation.isPlaying) "暂停" else "播放",
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (presentation.isBusy && !presentation.isPlaying) {
                        BusyGlyph(contentColor)
                    } else {
                        PlayPauseGlyph(presentation.isPlaying, contentColor)
                    }
                }
            }
            Canvas(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(Capsule())
                    .semantics {
                        contentDescription = presentation.status
                        progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                            presentation.progress,
                            0f..1f,
                        )
                    },
            ) {
                drawRect(
                    color = primaryColor,
                    size = androidx.compose.ui.geometry.Size(
                        width = size.width * smoothProgress.value,
                        height = size.height,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PlayPauseGlyph(isPlaying: Boolean, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        if (isPlaying) {
            drawLine(
                color,
                Offset(size.width * .3f, size.height * .22f),
                Offset(size.width * .3f, size.height * .78f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(size.width * .7f, size.height * .22f),
                Offset(size.width * .7f, size.height * .78f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        } else {
            val path = Path().apply {
                moveTo(size.width * .3f, size.height * .18f)
                lineTo(size.width * .76f, size.height * .5f)
                lineTo(size.width * .3f, size.height * .82f)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun BusyGlyph(color: Color) {
    val transition = rememberInfiniteTransition(label = "mini-player-busy")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mini-player-busy-rotation",
    )
    Canvas(Modifier.size(22.dp)) {
        rotate(rotation) {
            drawArc(
                color = color,
                startAngle = -80f,
                sweepAngle = 250f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
