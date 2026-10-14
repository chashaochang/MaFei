package cn.mafei.cmp.common.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.isActive

private const val HoverLapNanos = 2_800_000_000f
private const val TrailFraction = .18f
private const val TrailSteps = 20

/** Only hovered artwork subscribes to frames; leaving freezes the rim for its fade-out. */
@Composable
internal fun rememberArtworkHoverPhase(active: Boolean): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val durationScale = coroutineContext[MotionDurationScale]
        if ((durationScale?.scaleFactor ?: 1f) <= 0f) return@LaunchedEffect
        var previous = withInfiniteAnimationFrameNanos { it }
        while (isActive) {
            withInfiniteAnimationFrameNanos { now ->
                val scale = durationScale?.scaleFactor ?: 1f
                if (scale > 0f) {
                    phase.floatValue = (phase.floatValue + (now - previous) / (HoverLapNanos * scale)) % 1f
                }
                previous = now
            }
            if ((durationScale?.scaleFactor ?: 1f) <= 0f) break
        }
    }
    return phase
}

/** Cached contour and paints. The light travels by path length, including rounded corners. */
internal class ArtworkHoverRim(outline: Outline, inset: Float, density: Float, accent: Color) {
    internal val contour = Path().apply {
        when (outline) {
            is Outline.Rectangle -> addRect(outline.rect, Path.Direction.Clockwise)
            is Outline.Rounded -> addRoundRect(outline.roundRect, Path.Direction.Clockwise)
            is Outline.Generic -> addPath(outline.path)
        }
        translate(Offset(inset, inset))
    }
    internal val measure = PathMeasure().apply { setPath(contour, forceClosed = true) }
    private val segment = Path()
    // Butt caps avoid bright dots where adjacent gradient segments meet.
    private val line = Stroke(1.5f * density, cap = StrokeCap.Butt)
    private val glow = Stroke(4f * density, cap = StrokeCap.Butt)
    private val headRadius = .75f * density
    private val colors = List(TrailSteps) { index ->
        lerp(accent, Color.White, .25f + .75f * index / (TrailSteps - 1))
    }

    fun draw(scope: DrawScope, phase: Float, opacity: Float) = with(scope) {
        val length = measure.length
        if (opacity <= 0f || length <= 0f) return@with
        val step = length * TrailFraction / TrailSteps
        val head = ((phase % 1f + 1f) % 1f) * length
        for (index in 0 until TrailSteps) {
            val from = head - (TrailSteps - index) * step
            segment.rewind()
            appendWrappedSegment(from, from + step, segment)
            val strength = (index + 1f) / TrailSteps
            val alpha = opacity * strength * strength
            drawPath(segment, colors[index], alpha = alpha * .18f, style = glow)
            drawPath(segment, colors[index], alpha = alpha, style = line)
        }
        drawCircle(Color.White, headRadius, measure.getPosition(head), alpha = opacity)
    }

    /** A trail crossing the contour's origin must remain continuous. */
    internal fun appendWrappedSegment(start: Float, end: Float, target: Path) {
        val length = measure.length
        if (length <= 0f || end <= start) return
        val from = ((start % length) + length) % length
        val to = from + (end - start).coerceAtMost(length)
        measure.getSegment(from, to.coerceAtMost(length), target)
        if (to > length) measure.getSegment(0f, to - length, target)
    }
}
