package cn.mafei.cmp.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.player.api.PlayerState
import kotlin.math.roundToLong

@Composable
internal fun PlayerTimelineRow(
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
internal fun LegacyPlayerSeekBar(
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
