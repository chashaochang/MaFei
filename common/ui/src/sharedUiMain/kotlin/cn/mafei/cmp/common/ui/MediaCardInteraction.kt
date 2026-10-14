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
    private val revealActions: State<Boolean>,
    private val windowFocused: State<Boolean>,
) {
    internal var focusWithin by mutableStateOf(false)
    internal var pointerActivation by mutableStateOf(false)
    val hovered: Boolean get() = hover.value
    val focused: Boolean get() = focusWithin && !pointerActivation
    internal val pressed: Boolean get() = press.value
    val showActions: Boolean get() = windowFocused.value && (revealActions.value || focused)
}

@Composable
fun rememberMediaCardInteraction(): MediaCardInteraction {
    val source = remember { MutableInteractionSource() }
    val hover = source.collectIsHoveredAsState()
    val press = source.collectIsPressedAsState()
    var reveal by remember(source) { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(hover.value, windowFocused) {
        if (hover.value && windowFocused) {
            delay(80)
            reveal = true
        } else reveal = false
    }
    val revealState = rememberUpdatedState(reveal)
    val windowFocusState = rememberUpdatedState(windowFocused)
    return remember(source) { MediaCardInteraction(source, hover, press, revealState, windowFocusState) }
}

/** The whole card remains a target; only [mafeiArtworkFeedback] paints feedback. */
fun Modifier.mafeiMediaCardInteraction(
    interaction: MediaCardInteraction,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
): Modifier = this.composed {
    val raised = enabled && LocalWindowInfo.current.isWindowFocused &&
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
 * The complete cover rises while captions and the layout slot stay still. Shelf/grid owners
 * reserve clearance with [mediaArtworkHoverTopInset] and [mediaArtworkHoverSideInset].
 * Animation values are read in layer/draw phases; settled hover has no continuous frame work.
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

fun mediaArtworkGridHoverInset(contentWidth: Dp, columns: Int, gap: Dp, aspectRatio: Float = 2f / 3f): Dp {
    val count = columns.coerceAtLeast(1)
    val width = ((contentWidth - gap * (count - 1)) / count).coerceAtLeast(1.dp)
    return mediaArtworkHoverTopInset(width / aspectRatio)
}

/** Pair with equal LazyRow content padding so resting posters keep the heading's alignment. */
fun Modifier.mafeiArtworkShelfBleed(inset: Dp): Modifier = layout { measurable, constraints ->
    val edge = inset.roundToPx()
    val child = measurable.measure(constraints.offset(horizontal = edge * 2))
    layout((child.width - edge * 2).coerceAtLeast(0), child.height) { child.place(-edge, 0) }
}
