package cn.mafei.cmp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.session.*
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun StaticAppCanvas(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), content = content)
}

@Composable
internal fun PonyStartupLoading(exiting: Boolean) {
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "pony-startup")
    val frameProgress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(animation = tween(480, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "pony-frames",
        )
    val streakProgress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(720, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "pony-streaks",
        )
    val breathing by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
            label = "pony-breathing",
        )
    val exitProgress by animateFloatAsState(targetValue = if (exiting) 1f else 0f, animationSpec = tween(220, easing = LinearEasing), label = "pony-exit")
    val primary = MaterialTheme.colorScheme.primary
    val frames = remember { MaFeiLegacyDrawables.ponyLoadingFrames }
    val frame = frames[frameProgress.toInt().coerceIn(0, frames.lastIndex)]
    val imageYOffsetPx = with(density) { 18.dp.toPx() }
    val exitYOffsetPx = with(density) { 12.dp.toPx() }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp).graphicsLayer { alpha = 1f - exitProgress },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(250.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize().padding(horizontal = 22.dp)) {
                val lineY = size.height * 0.83f
                val lineGap = 52.dp.toPx()
                val lineHeight = 3.dp.toPx()
                val phase = streakProgress * lineGap
                repeat(5) { index ->
                    val lineWidth = (28 + (index % 3) * 10).dp.toPx()
                    val x = size.width - ((index * lineGap + phase) % (size.width + lineWidth))
                    val y = lineY + ((index % 2) * 7).dp.toPx()
                    drawRoundRect(
                        color = primary.copy(alpha = 0.16f + breathing * 0.08f),
                        topLeft = Offset(x, y),
                        size = Size(lineWidth, lineHeight),
                        cornerRadius = CornerRadius(lineHeight / 2f),
                    )
                }
            }
            Image(
                painter = painterResource(frame),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier.size(160.dp).graphicsLayer {
                        translationY = -imageYOffsetPx - exitProgress * exitYOffsetPx
                        val scale = 0.98f + breathing * 0.02f + exitProgress * 0.06f
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(text = "正在启动…", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
    }
}

@Composable
internal fun SessionMessage(message: String, busy: Boolean = false, onRetry: (() -> Unit)? = null, onCleanup: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message)
            if (busy) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Button)
                    Text("正在处理", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            onRetry?.let {
                Text(
                    "重试恢复",
                    Modifier.mafeiInteractive(it, enabled = !busy, radiusDp = 20f, label = "重试恢复").padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (busy) .45f else 1f),
                )
            }
            onCleanup?.let {
                Text(
                    "完成退出，重新登录",
                    Modifier.mafeiInteractive(it, enabled = !busy, radiusDp = 20f, label = "完成退出，重新登录").padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (busy) .45f else 1f),
                )
            }
        }
    }
}
