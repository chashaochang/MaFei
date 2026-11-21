package cn.mafei.cmp.common.glass.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import androidx.compose.material3.MaterialTheme
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.utils.DampedDragAnimation
import cn.mafei.cmp.common.glass.captureBackdrop
import cn.mafei.cmp.common.glass.rememberTransformedSample
import cn.mafei.cmp.common.glass.rememberCombinedSamples
import cn.mafei.cmp.common.glass.rememberBackdropCapture
import cn.mafei.cmp.common.glass.drawFrostedBackdrop
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.flow.collectLatest

object LiquidToggleDefaults {
    val trackWidth = 58.dp
    val trackHeight = 32.dp
    val thumbSize = 28.dp
    val thumbInset = (trackHeight - thumbSize) / 2
    val thumbTravel = trackWidth - thumbSize - thumbInset * 2
    val touchHeight = 44.dp
    const val pressedScale = 1.5f
}

/**
 * The toggle from the Android Liquid Glass demo, ported without replacing its
 * damped drag, lens, highlight, and inner-shadow behavior with a native switch.
 */
@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() >= .5f
    val accentColor = if (isLightTheme) Color(0xFF34C759) else Color(0xFF30D158)
    val trackColor = if (isLightTheme) {
        Color(0xFF787878).copy(alpha = .2f)
    } else {
        Color(0xFF787880).copy(alpha = .36f)
    }
    val selectedState by rememberUpdatedState(selected)
    val onSelectState by rememberUpdatedState(onSelect)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { LiquidToggleDefaults.thumbTravel.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    val dampedDragAnimation = remember(animationScope, dragWidth, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = LiquidToggleDefaults.pressedScale,
            consumePointerChanges = true,
            onDragStarted = { didDrag = false },
            onDragStopped = {
                if (didDrag) {
                    fraction = if (fraction >= 0.5f) 1f else 0f
                    onSelectState(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (selectedState()) 0f else 1f
                    onSelectState(fraction == 1f)
                }
            },
            onDragCancelled = {
                didDrag = false
                fraction = if (selectedState()) 1f else 0f
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) didDrag = dragAmount.x != 0f
                val delta = dragAmount.x / dragWidth
                fraction = if (isLtr) {
                    (fraction + delta).fastCoerceIn(0f, 1f)
                } else {
                    (fraction - delta).fastCoerceIn(0f, 1f)
                }
            },
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { value ->
            dampedDragAnimation.updateValue(value)
        }
    }
    val selectedValue = selected()
    LaunchedEffect(selectedValue) {
        val target = if (selectedValue) 1f else 0f
        if (target != fraction) {
            fraction = target
            dampedDragAnimation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberBackdropCapture()
    Box(
        modifier.size(LiquidToggleDefaults.trackWidth, LiquidToggleDefaults.touchHeight)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(selectedValue)
                onClick { onSelectState(!selectedState()); true }
            }
            .then(dampedDragAnimation.modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .captureBackdrop(trackBackdrop)
                .clip(CircleShape)
                .drawBehind {
                    val value = dampedDragAnimation.value
                    drawRect(lerp(trackColor, accentColor, value))
                }
                .size(LiquidToggleDefaults.trackWidth, LiquidToggleDefaults.trackHeight),
        )

        Box(
            Modifier
                .graphicsLayer {
                    val value = dampedDragAnimation.value
                    val padding = LiquidToggleDefaults.thumbInset.toPx()
                    translationX = if (isLtr) {
                        lerp(padding, padding + dragWidth, value)
                    } else {
                        lerp(-padding, -(padding + dragWidth), value)
                    }
                }
                .drawFrostedBackdrop(
                    backdrop = rememberCombinedSamples(
                        backdrop.layer,
                        rememberTransformedSample(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            scale(
                                lerp(2f / 3f, .75f, progress),
                                lerp(0f, .75f, progress),
                            ) { drawBackdrop() }
                        },
                    ),
                    shape = { CircleShape },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * .75f).fastCoerceIn(-.2f, .2f)
                        scaleY *= 1f - (velocity * .25f).fastCoerceIn(-.2f, .2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    },
                )
                .size(LiquidToggleDefaults.thumbSize),
        )
    }
}
