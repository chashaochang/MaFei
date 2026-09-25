package cn.mafei.cmp.common.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiShape
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton

enum class MaFeiLoadingStyle { Page, Inline, Button }

enum class MaFeiNoticeKind { Error, Warning, Info }

/** Shared activity mark; animated values are read only while drawing. */
@Composable
fun MaFeiLoadingIndicator(
    modifier: Modifier = Modifier,
    style: MaFeiLoadingStyle = MaFeiLoadingStyle.Inline,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val visualSize = when (style) {
        MaFeiLoadingStyle.Page -> 42.dp
        MaFeiLoadingStyle.Inline -> 28.dp
        MaFeiLoadingStyle.Button -> 18.dp
    }
    val transition = rememberInfiniteTransition(label = "mafei-loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_080, easing = LinearEasing), RepeatMode.Restart),
        label = "mafei-loading-phase",
    )
    Box(
        modifier.clearAndSetSemantics {
            contentDescription = "正在加载"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(visualSize)) {
            val stroke = (size.minDimension * .08f).coerceAtLeast(1.5f)
            val inset = stroke * 1.5f
            drawArc(
                color = tint.copy(alpha = .24f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = tint,
                startAngle = phase,
                sweepAngle = 96f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val dot = size.minDimension * .055f
            drawCircle(tint.copy(alpha = .62f), dot, center.copy(y = center.y - size.minDimension * .28f))
        }
    }
}

/** Determinate track used for playback, carousel, and other real progress values. */
@Composable
fun MaFeiProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.Black.copy(alpha = .34f),
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    val value = progress.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50)),
    ) {
        drawRect(trackColor)
        if (value > 0f) drawRect(progressColor, size = size.copy(width = size.width * value))
    }
}

/** Product-owned ring for a determinate or indeterminate refresh state. */
@Composable
fun MaFeiProgressRing(
    progress: Float?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = tint.copy(alpha = .22f),
) {
    val phase = if (progress == null) {
        rememberInfiniteTransition(label = "mafei-progress-ring").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1_080, easing = LinearEasing), RepeatMode.Restart),
            label = "mafei-progress-ring-phase",
        )
    } else null
    Canvas(modifier.size(22.dp)) {
        val stroke = (size.minDimension * .13f).coerceAtLeast(1.5f)
        val inset = stroke
        val ringSize = androidx.compose.ui.geometry.Size(
            size.width - inset * 2f,
            size.height - inset * 2f,
        )
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = ringSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val start = phase?.value ?: -90f
        val sweep = if (progress == null) 96f else progress.coerceIn(0f, 1f) * 360f
        drawArc(
            color = tint,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = ringSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Thin animated indicator for the page that is already visible and refreshing in the background. */
@Composable
fun MaFeiRefreshIndicator(
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = refreshing,
        modifier = modifier,
        enter = androidx.compose.animation.fadeIn(tween(140)),
        exit = androidx.compose.animation.fadeOut(tween(180)),
    ) {
        MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Button)
    }
}

/** Error/warning/info notice without Material's filled error container. */
@Composable
fun MaFeiInlineNotice(
    message: String,
    modifier: Modifier = Modifier,
    kind: MaFeiNoticeKind = MaFeiNoticeKind.Error,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val accent = when (kind) {
        MaFeiNoticeKind.Error -> MaterialTheme.colorScheme.error
        MaFeiNoticeKind.Warning -> Color(0xFFFFB74D)
        MaFeiNoticeKind.Info -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaFeiShape.field)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .56f))
            .drawBehind {
                drawRoundRect(accent.copy(alpha = .72f), size = size.copy(width = 2.dp.toPx()))
            }
            .padding(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(accent),
        )
        Text(
            message,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                Modifier.mafeiInteractive(onAction, radiusDp = 16f, label = actionLabel)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                color = accent,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** A non-interactive state lamp; callers may place it inside an existing glass action row. */
@Composable
fun MaFeiRefreshIndicatorButton(
    refreshing: Boolean,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    // Keep geometry, but dispose the animation and backdrop consumer when idle.
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedVisibility(
            visible = refreshing,
            enter = androidx.compose.animation.fadeIn(tween(140)),
            exit = androidx.compose.animation.fadeOut(tween(180)),
        ) {
            LiquidButton(
                onClick = null,
                backdrop = backdrop,
                isInteractive = false,
                modifier = Modifier.size(40.dp).clearAndSetSemantics {
                    if (refreshing) contentDescription = "正在刷新首页"
                },
                height = 40.dp,
                horizontalPadding = 8.dp,
            ) {
                MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Button, tint = Color.White)
            }
        }
    }
}

/**
 * Keep the existing nested-scroll engine (no visual widgets), render only MaFei feedback.
 * The phone home hero has its own stretch gesture and does not use this wrapper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaFeiPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorTopPadding: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val visible by remember(state, isRefreshing) {
        derivedStateOf { isRefreshing || state.distanceFraction > 0f }
    }
    Box(modifier.pullToRefresh(isRefreshing = isRefreshing, state = state, onRefresh = onRefresh)) {
        content()
        if (visible) Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = indicatorTopPadding)
                .size(36.dp)
                .graphicsLayer {
                    translationY = (state.distanceFraction * 48.dp.toPx()) - size.height
                    alpha = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
                }
                .clearAndSetSemantics {
                    contentDescription = if (isRefreshing) "正在刷新" else "下拉刷新"
                },
            contentAlignment = Alignment.Center,
        ) {
            MaFeiProgressRing(
                progress = if (isRefreshing) null else state.distanceFraction.coerceIn(0f, 1f),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
