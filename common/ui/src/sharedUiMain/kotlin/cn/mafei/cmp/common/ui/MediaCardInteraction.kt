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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

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
 * Fixed artwork contour with a slight inner zoom and clockwise hover rim. Captions never move,
 * gain a background or enter the focus outline. This reacts to input, not device classification.
 * Animation values are read in layer/draw phases; no backdrop or blur per card.
 * The outer clip stays inside the layout bounds even at a list edge. Only the inner content layer
 * scales, preserving child action hit transforms without moving the contour into adjacent items.
 */
fun Modifier.mafeiArtworkFeedback(
    interaction: MediaCardInteraction,
    shape: Shape,
    enabled: Boolean = true,
): Modifier = composed {
    val active = enabled && LocalWindowInfo.current.isWindowFocused
    val hovered = active && interaction.hovered
    val focused = active && interaction.focused && !hovered
    val pressed = active && interaction.pressed
    val scale = animateFloatAsState(
        if (pressed) 1.008f else if (hovered) 1.018f else 1f,
        tween(if (pressed) 80 else 160), label = "artwork scale",
    )
    val focus = animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "artwork focus")
    val hover = animateFloatAsState(if (hovered) 1f else 0f, tween(160), label = "artwork hover rim")
    val phase = rememberArtworkHoverPhase(hovered)
    val density = LocalDensity.current.density
    val focusColor = MaterialTheme.colorScheme.primary
    this.graphicsLayer {
        this.shape = shape
        clip = true
    }.drawWithCache {
        // Include the full 4dp glow in the fixed contour, including the first/last list items.
        val inset = 2f * density
        val rim = ArtworkHoverRim(
            outline = shape.createOutline(
                Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)),
                layoutDirection, this,
            ),
            inset = inset, density = density, accent = focusColor,
        )
        val hoverStroke = Stroke(density)
        val focusStroke = Stroke(2f * density)
        onDrawWithContent {
            drawContent()
            if (hover.value > 0f) {
                drawPath(rim.contour, Color.White, alpha = .16f * hover.value, style = hoverStroke)
                rim.draw(this, phase.value, hover.value)
            }
            if (focus.value > 0f) drawPath(rim.contour, focusColor, alpha = focus.value, style = focusStroke)
        }
    }.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
