package cn.mafei.cmp.common.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/** One interaction group for artwork, captions and the artwork's quick actions. */
@Stable
class MediaCardInteraction internal constructor(
    internal val source: MutableInteractionSource,
    private val hover: State<Boolean>,
    private val press: State<Boolean>,
    internal val liftArtwork: Boolean,
    private val revealActions: State<Boolean>,
) {
    internal var focusWithin by mutableStateOf(false)
    internal var pointerActivation by mutableStateOf(false)
    val hovered: Boolean get() = hover.value
    val focused: Boolean get() = focusWithin && !pointerActivation
    internal val pressed: Boolean get() = press.value
    val showActions: Boolean get() = if (liftArtwork) revealActions.value || focused else hovered || focused
}

@Composable
fun rememberMediaCardInteraction(liftArtwork: Boolean = false): MediaCardInteraction {
    val source = remember { MutableInteractionSource() }
    val hover = source.collectIsHoveredAsState()
    val press = source.collectIsPressedAsState()
    var reveal by remember(source) { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(hover.value, windowFocused, liftArtwork) {
        if (liftArtwork && hover.value && windowFocused) {
            delay(80)
            reveal = true
        } else reveal = false
    }
    val revealState = rememberUpdatedState(reveal)
    return remember(source, liftArtwork) { MediaCardInteraction(source, hover, press, liftArtwork, revealState) }
}

/** The whole card remains a target; only [mafeiArtworkFeedback] paints feedback. */
fun Modifier.mafeiMediaCardInteraction(
    interaction: MediaCardInteraction,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
): Modifier = this.composed {
    val raised = interaction.liftArtwork && enabled && LocalWindowInfo.current.isWindowFocused &&
        (interaction.hovered || interaction.focused)
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(raised) {
        if (raised) settling = true else if (settling) { delay(220); settling = false }
    }
    this.zIndex(if (raised || settling) 1f else 0f)
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
}

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
    if (interaction.liftArtwork) return@composed this.liftedArtworkFeedback(interaction, shape, enabled)
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

/** Home sample: the complete cover rises; its captions and layout slot stay still. */
private fun Modifier.liftedArtworkFeedback(
    interaction: MediaCardInteraction,
    shape: Shape,
    enabled: Boolean,
): Modifier = composed {
    val active = enabled && LocalWindowInfo.current.isWindowFocused
    val hovered = active && interaction.hovered
    val focused = active && interaction.focused && !hovered
    val pressed = active && interaction.pressed
    val scale = animateFloatAsState(
        if (pressed) { if (hovered) 1.03f else .98f } else if (hovered) ArtworkLiftScale else 1f,
        tween(if (pressed) 90 else 200), label = "cover lift scale",
    )
    val lift = animateFloatAsState(if (hovered) 1f else 0f, tween(200), label = "cover elevation")
    val focus = animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "cover focus")
    val density = LocalDensity.current.density
    val focusColor = MaterialTheme.colorScheme.primary
    this.graphicsLayer {
        this.shape = shape
        scaleX = scale.value
        scaleY = scale.value
        transformOrigin = TransformOrigin(.5f, 1f)
        translationY = -2f * density * lift.value
        shadowElevation = 10f * density * lift.value
        ambientShadowColor = Color.Black.copy(alpha = .18f)
        spotShadowColor = Color.Black.copy(alpha = .32f)
        clip = false
    }.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val edge = Stroke(.75f * density)
        val focusStroke = Stroke(2f * density)
        onDrawWithContent {
            drawContent()
            if (lift.value > 0f) drawOutline(outline, Color.White, alpha = .22f * lift.value, style = edge)
            if (focus.value > 0f) drawOutline(outline, focusColor, alpha = focus.value, style = focusStroke)
        }
    }.graphicsLayer {
        this.shape = shape
        clip = true
    }
}

internal const val ArtworkLiftScale = 1.09f

fun mediaArtworkHoverTopInset(artworkHeight: Dp): Dp = artworkHeight * (ArtworkLiftScale - 1f) + 12.dp
fun mediaArtworkHoverSideInset(artworkWidth: Dp): Dp = artworkWidth * ((ArtworkLiftScale - 1f) / 2f) + 12.dp

/** Pair with equal LazyRow content padding so resting posters keep the heading's alignment. */
fun Modifier.mafeiArtworkShelfBleed(inset: Dp): Modifier = layout { measurable, constraints ->
    val edge = inset.roundToPx()
    val child = measurable.measure(constraints.offset(horizontal = edge * 2))
    layout((child.width - edge * 2).coerceAtLeast(0), child.height) { child.place(-edge, 0) }
}
