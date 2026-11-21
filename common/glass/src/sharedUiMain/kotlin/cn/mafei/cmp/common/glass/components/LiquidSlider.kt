package cn.mafei.cmp.common.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.utils.DampedDragAnimation
import cn.mafei.cmp.common.glass.captureBackdrop
import cn.mafei.cmp.common.glass.rememberTransformedSample
import cn.mafei.cmp.common.glass.rememberCombinedSamples
import cn.mafei.cmp.common.glass.rememberBackdropCapture
import cn.mafei.cmp.common.glass.drawFrostedBackdrop
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    onValueChangeFinished: ((Float) -> Unit)? = null,
) {
    // 判定必须跟随**应用**主题，不是系统外观：应用已经只有深色一套（浅色主题已砍），
    // 之前用 `!isSystemInDarkTheme()` 会在系统处于浅色时把滑杆画成浅色配色（蓝 0xFF0088FF
    // + 浅轨道），与周围深色界面脱节。这里与其它玻璃组件统一用配色方案自身的亮度。
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() >= .5f
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberBackdropCapture()

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val change by rememberUpdatedState(onValueChange)
        val finished by rememberUpdatedState(onValueChangeFinished)
        val currentValue by rememberUpdatedState(value)
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope, valueRange, trackWidth, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = { didDrag = false },
                onDragStopped = {
                    if (didDrag) {
                        val position = currentValue().coerceIn(valueRange)
                        change(position)
                        finished?.invoke(position)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    change(
                        if (isLtr) (targetValue + delta).coerceIn(valueRange)
                        else (targetValue - delta).coerceIn(valueRange)
                    )
                }
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentValue() }
                .collectLatest { value ->
                    if (dampedDragAnimation.targetValue != value) {
                        dampedDragAnimation.updateValue(value)
                    }
                }
        }

        Box(Modifier.captureBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(trackColor)
                    .pointerInput(dampedDragAnimation) {
                        detectTapGestures { position ->
                            val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                            val targetValue =
                                (if (isLtr) valueRange.start + delta
                                else valueRange.endInclusive - delta)
                                    .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(targetValue)
                            change(targetValue)
                            finished?.invoke(targetValue)
                        }
                    }
                    .height(6f.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(CircleShape)
                    .background(accentColor)
                    .height(6f.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .then(dampedDragAnimation.modifier)
                .drawFrostedBackdrop(
                    backdrop = rememberCombinedSamples(
                        backdrop.layer,
                        rememberTransformedSample(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { CircleShape },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
