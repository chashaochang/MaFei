package cn.mafei.cmp.common.media

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtworkBlurGeometryTest {
    @Test
    fun phoneBlurKeepsCropAndApparentRadiusWithBoundedRaster() {
        val result = artworkBlurGeometry(IntSize(1280, 2832), IntSize(720, 405), 112f)
        assertEquals(512, result.rasterSize.height)
        assertTrue(result.rasterSize.width in 231..232)
        assertTrue(result.imageSize.width > result.rasterSize.width)
        assertEquals(result.rasterSize.height, result.imageSize.height)
        assertTrue(result.imageOffset.x < 0)
        assertEquals(112f * 512f / 2832f, result.radiusPx, .001f)
    }

    @Test
    fun wideWindowCropsVerticallyAndRecomputesRadius() {
        val result = artworkBlurGeometry(IntSize(2560, 1440), IntSize(400, 600), 100f)
        assertEquals(IntSize(512, 288), result.rasterSize)
        assertEquals(512, result.imageSize.width)
        assertTrue(result.imageOffset.y < 0)
        assertEquals(20f, result.radiusPx)
    }

    @Test
    fun smallTargetsAreNotUpscaled() {
        val result = artworkBlurGeometry(IntSize(120, 240), IntSize(100, 100), 12f)
        assertEquals(IntSize(120, 240), result.rasterSize)
        assertEquals(12f, result.radiusPx)
        assertEquals(IntSize(240, 240), result.imageSize)
    }
}
