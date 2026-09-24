package cn.mafei.cmp.common.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiShape

enum class MaFeiLoadingStyle { Page, Inline, Button }

enum class MaFeiNoticeKind { Error, Warning, Info }

/** Product-owned loading mark. It deliberately has no Material progress semantics or surface. */
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(44.dp)
            .clearAndSetSemantics {
                if (refreshing) stateDescription = "正在刷新首页"
            },
        contentAlignment = Alignment.Center,
    ) {
        MaFeiRefreshIndicator(refreshing = refreshing)
    }
}
