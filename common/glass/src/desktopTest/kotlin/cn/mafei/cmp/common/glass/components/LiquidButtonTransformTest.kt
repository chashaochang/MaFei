package cn.mafei.cmp.common.glass.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.kyant.backdrop.internal.InverseLayerScope
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.test.Test
import kotlin.test.assertEquals

class LiquidButtonTransformTest {
    @Test
    fun idleButtonResetsThePressedTransform() {
        val scope = InverseLayerScope().apply { size = Size(120f, 48f) }
        scope.applyLiquidButtonPressTransform(.6f, Offset(12f, -8f))
        scope.applyLiquidButtonPressTransform(0f, Offset.Zero)
        assertEquals(0f, scope.translationX)
        assertEquals(0f, scope.translationY)
        assertEquals(1f, scope.scaleX)
        assertEquals(1f, scope.scaleY)
    }

    @Test
    fun idleButtonBeforeMeasurementHasNoInvalidTransform() {
        val scope = InverseLayerScope().apply { size = Size.Zero }
        scope.applyLiquidButtonPressTransform(0f, Offset.Zero)
        assertEquals(0f, scope.translationX)
        assertEquals(0f, scope.translationY)
        assertEquals(1f, scope.scaleX)
        assertEquals(1f, scope.scaleY)
    }

    @Test
    fun activePressAndReleaseKeepTheOriginalFormula() {
        for (size in listOf(Size(120f, 48f), Size(48f, 120f))) {
            for (progress in listOf(0f, .5f, 1f)) {
                for (offset in listOf(Offset.Zero, Offset(12f, -8f))) {
                    val scope = InverseLayerScope().apply { this.size = size; density = 2f }
                    scope.applyLiquidButtonPressTransform(progress, offset)
                    val scale = 1f + (8f / size.height) * progress
                    val maxOffset = size.minDimension
                    val maxDragScale = 8f / size.height
                    val angle = atan2(offset.y, offset.x)
                    assertEquals(maxOffset * tanh(.05f * offset.x / maxOffset), scope.translationX, .000001f)
                    assertEquals(maxOffset * tanh(.05f * offset.y / maxOffset), scope.translationY, .000001f)
                    assertEquals(scale + maxDragScale * abs(cos(angle) * offset.x / size.maxDimension) *
                        (size.width / size.height).coerceAtMost(1f), scope.scaleX, .000001f)
                    assertEquals(scale + maxDragScale * abs(sin(angle) * offset.y / size.maxDimension) *
                        (size.height / size.width).coerceAtMost(1f), scope.scaleY, .000001f)
                }
            }
        }
    }
}
