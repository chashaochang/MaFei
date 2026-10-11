package com.kyant.backdrop.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.shadow.Shadow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EffectLayerRecordingTest {
    private val cache = EffectLayerRecording()
    private var recordings = 0

    private fun record(
        size: Size = Size(100f, 48f),
        density: Density = Density(1f),
        direction: LayoutDirection = LayoutDirection.Ltr,
        outline: Outline = Outline.Rectangle(Rect(0f, 0f, 100f, 48f)),
        shadow: Shadow = Shadow(),
        canReuse: Boolean = true
    ) = cache.recordIfChanged(size, density, direction, outline, shadow, canReuse) { recordings++ }

    @Test
    fun unchangedDecorationRecordsOnlyOnceAcrossBackgroundFrames() {
        repeat(120) { record() }
        assertEquals(1, recordings)
    }

    @Test
    fun geometryDensityAndDirectionInvalidateTheRecording() {
        val changes: List<() -> Unit> = listOf(
            { record(size = Size(101f, 48f)) },
            { record(density = Density(2f)) },
            { record(density = Density(1f, 1.5f)) },
            { record(direction = LayoutDirection.Rtl) },
            { record(outline = Outline.Rectangle(Rect(1f, 0f, 100f, 48f))) },
        )
        changes.forEach { change ->
            cache.clear()
            recordings = 0
            record()
            change()
            change()
            assertEquals(2, recordings)
        }
    }

    @Test
    fun themeAndAnimatedParametersInvalidateTheRecording() {
        listOf(
            Shadow(color = Color.White), Shadow(radius = 12.dp),
            Shadow(alpha = .5f), Shadow(blendMode = BlendMode.Plus),
        ).forEach { changed ->
            cache.clear()
            recordings = 0
            record()
            record(shadow = changed)
            record(shadow = changed)
            assertEquals(2, recordings)
        }
    }

    @Test
    fun detachedLayerCannotReusePreviousRecording() {
        record()
        cache.clear()
        record()
        assertEquals(2, recordings)
    }

    @Test
    fun customShaderRecordsEveryFrameAndInvalidatesOldKey() {
        record()
        repeat(3) { record(canReuse = false) }
        record()
        assertEquals(5, recordings)
    }

    @Test
    fun failedRecordingIsNotCached() {
        assertFailsWith<IllegalStateException> {
            cache.recordIfChanged(Size(100f, 48f), Density(1f), LayoutDirection.Ltr,
                Outline.Rectangle(Rect(0f, 0f, 100f, 48f)), Shadow()) { error("record failed") }
        }
        record()
        assertEquals(1, recordings)
    }

    @Test
    fun zeroAlphaSkipsOnlyNonDestructiveBlendModes() {
        assertTrue(isInvisibleEffect(0f, BlendMode.SrcOver))
        assertTrue(isInvisibleEffect(0f, BlendMode.Plus))
        assertFalse(isInvisibleEffect(.001f, BlendMode.SrcOver))
        assertFalse(isInvisibleEffect(0f, BlendMode.Clear))
        assertFalse(isInvisibleEffect(0f, BlendMode.Src))
        assertFalse(isInvisibleEffect(0f, BlendMode.DstIn))
    }
}
