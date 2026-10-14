package cn.mafei.cmp.common.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/** One interaction group for artwork, captions and the artwork's quick actions. */
@Stable
class MediaCardInteraction internal constructor(
    internal val source: MutableInteractionSource,
    private val hover: State<Boolean>,
    private val press: State<Boolean>,
) {
    internal var focusWithin by mutableStateOf(false)
    internal var pointerActivation by mutableStateOf(false)
    val hovered: Boolean get() = hover.value
    val focused: Boolean get() = focusWithin && !pointerActivation
    internal val pressed: Boolean get() = press.value
    val showActions: Boolean get() = hovered || focused
}

@Composable
fun rememberMediaCardInteraction(): MediaCardInteraction {
    val source = remember { MutableInteractionSource() }
    val hover = source.collectIsHoveredAsState()
    val press = source.collectIsPressedAsState()
    return remember(source) { MediaCardInteraction(source, hover, press) }
}

/** The whole card remains a target; only [mafeiArtworkFeedback] paints feedback. */
fun Modifier.mafeiMediaCardInteraction(
    interaction: MediaCardInteraction,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
): Modifier = this
    .pointerInput(interaction) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) interaction.pointerActivation = true
            }
        }
    }
    .onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown) interaction.pointerActivation = false
        false
    }
    .onFocusChanged {
        interaction.focusWithin = it.hasFocus
        if (!it.hasFocus) interaction.pointerActivation = false
    }
    .focusGroup()
    .mafeiInteractive(
        onClick = onClick,
        enabled = enabled,
        label = label,
        onLongClick = onLongClick,
        visualFeedback = false,
        interactionSource = interaction.source,
    )

/**
 * Image-only lift and rim. Captions never move, gain a background or enter the
 * focus outline. This reacts to input, not a desktop/device classification.
 * Animation values are read in layer/draw phases; no backdrop or blur per card.
 * Also owns the artwork clip so callers do not add a second clipping layer.
 */
fun Modifier.mafeiArtworkFeedback(
    interaction: MediaCardInteraction,
    shape: Shape,
    enabled: Boolean = true,
): Modifier = composed {
    val hovered = enabled && interaction.hovered
    val focused = enabled && interaction.focused && !hovered
    val pressed = enabled && interaction.pressed
    val lift = animateFloatAsState(if (hovered || focused) 1f else 0f, tween(160), label = "artwork lift")
    val scale = animateFloatAsState(
        if (pressed) .985f else if (hovered) 1.018f else 1f,
        tween(if (pressed) 80 else 160), label = "artwork scale",
    )
    val focus = animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "artwork focus")
    val density = LocalDensity.current.density
    val focusColor = MaterialTheme.colorScheme.primary
    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        translationY = -2f * density * lift.value
        shadowElevation = 4f * density * lift.value
        this.shape = shape
        clip = true
        ambientShadowColor = Color.Black.copy(alpha = .18f)
        spotShadowColor = Color.Black.copy(alpha = .28f)
    }.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val rim = Brush.linearGradient(
            listOf(Color.White.copy(alpha = .85f), Color.White.copy(alpha = .30f), Color.White.copy(alpha = .12f)),
            start = Offset.Zero, end = Offset(size.width, size.height),
        )
        onDrawWithContent {
            drawContent()
            if (lift.value > 0f) drawOutline(outline, rim, alpha = lift.value, style = Stroke(1.5f * density))
            if (focus.value > 0f) drawOutline(outline, focusColor, alpha = focus.value, style = Stroke(4f * density))
        }
    }
}
