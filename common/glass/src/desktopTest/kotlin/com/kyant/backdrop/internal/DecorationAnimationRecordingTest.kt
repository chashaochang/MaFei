package com.kyant.backdrop.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.highlight.sameHighlightDrawingParameters
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.sameShadowDrawingParameters
import kotlin.test.Test
import kotlin.test.assertEquals

class DecorationAnimationRecordingTest {
    private val size = Size(100f, 48f)
    private val density = Density(1f)
    private val outline = Outline.Rectangle(Rect(0f, 0f, size.width, size.height))

    @Test
    fun highlightFadeAndBlendChangesKeepOneRecordingForEachBuiltInStyle() {
        val styles = listOf(HighlightStyle.Plain, HighlightStyle.Default, HighlightStyle.Ambient)
        styles.forEach { style ->
            val cache = EffectLayerRecording(::sameHighlightDrawingParameters)
            var recordings = 0
            repeat(120) { frame ->
                val blend = if (frame % 2 == 0) BlendMode.SrcOver else BlendMode.Plus
                val frameStyle = when (style) {
                    is HighlightStyle.Plain -> style.copy(blendMode = blend)
                    is HighlightStyle.Default -> style.copy(blendMode = blend)
                    else -> style
                }
                cache.recordIfChanged(size, density, LayoutDirection.Ltr, outline,
                    Highlight(alpha = frame / 119f, style = frameStyle)) { recordings++ }
            }
            assertEquals(1, recordings, "Fading a fixed decoration must not redraw its contents")
        }
    }

    @Test
    fun highlightGeometryColorAndLightingStillInvalidateContents() {
        val cache = EffectLayerRecording(::sameHighlightDrawingParameters)
        var recordings = 0
        val changes = listOf(
            Highlight(),
            Highlight(width = 2.dp),
            Highlight(width = 2.dp, blurRadius = 3.dp),
            Highlight(style = HighlightStyle.Default(color = Color.Red)),
            Highlight(style = HighlightStyle.Default(angle = 90f)),
            Highlight(style = HighlightStyle.Default(angle = 90f, falloff = 2f)),
            Highlight(style = HighlightStyle.Ambient(intensity = .8f)),
        )
        changes.forEach { parameters ->
            repeat(2) {
                cache.recordIfChanged(size, density, LayoutDirection.Ltr, outline, parameters) { recordings++ }
            }
        }
        assertEquals(changes.size, recordings)
    }

    @Test
    fun shadowFadeAndBlendChangesReuseContentsButGeometryAndColorDoNot() {
        val cache = EffectLayerRecording(::sameShadowDrawingParameters)
        var recordings = 0
        repeat(120) { frame ->
            cache.recordIfChanged(size, density, LayoutDirection.Ltr, outline,
                Shadow(alpha = frame / 119f,
                    blendMode = if (frame % 2 == 0) BlendMode.SrcOver else BlendMode.Plus)) { recordings++ }
        }
        assertEquals(1, recordings)
        listOf(
            Shadow(radius = 8.dp),
            Shadow(offset = DpOffset(2.dp, 6.dp)),
            Shadow(color = Color.Red),
        ).forEach { parameters ->
            repeat(2) {
                cache.recordIfChanged(size, density, LayoutDirection.Ltr, outline, parameters) { recordings++ }
            }
        }
        assertEquals(4, recordings)
    }
}
