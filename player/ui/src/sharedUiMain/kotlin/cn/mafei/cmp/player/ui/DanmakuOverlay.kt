package cn.mafei.cmp.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.player.api.*
import kotlinx.coroutines.isActive

@Composable
internal fun DanmakuOverlay(entries: List<DanmakuEntry>, state: PlayerState, fullscreen: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 256)
    var position by remember(state.generation) { mutableLongStateOf(state.positionMs) }
    LaunchedEffect(state.generation, state.phase, state.positionMs, state.speed) {
        position = state.positionMs
        if (state.phase == PlayerPhase.Playing) {
            val anchor = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { now -> position = state.positionMs + ((now - anchor) / 1_000_000.0 * state.speed).toLong() }
            }
        }
    }
    Canvas(modifier) {
        val scale = if (fullscreen) 1f else .8f
        val style = TextStyle(fontSize = (26 * .8f * scale).sp, shadow = Shadow(Color.Black, Offset.Zero, 3f))
        for ((index, entry) in activeDanmaku(entries, position)) {
            val text = measurer.measure(entry.text, style, maxLines = 1, softWrap = false)
            val progress = ((position - entry.timeMs) / 6000f).coerceIn(0f, 1f)
            val lineHeight = text.size.height + 4.dp.toPx()
            val lane = index % minOf(11, (size.height / lineHeight).toInt().coerceAtLeast(1))
            val x = when (entry.mode) {
                4, 5 -> (size.width - text.size.width) / 2
                6 -> -text.size.width + (size.width + text.size.width) * progress
                else -> size.width - (size.width + text.size.width) * progress
            }
            val y = if (entry.mode == 4) size.height - (lane + 1) * lineHeight else lane * lineHeight
            drawText(text, color = Color(entry.color or 0xFF000000.toInt()), topLeft = Offset(x, y))
        }
    }
}
