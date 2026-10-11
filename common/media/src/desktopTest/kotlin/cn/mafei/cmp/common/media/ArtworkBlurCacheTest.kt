package cn.mafei.cmp.common.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class ArtworkBlurCacheTest {
    private val geometry = artworkBlurGeometry(IntSize(32, 32), IntSize(16, 16), 4f)

    @Test
    fun hitRequiresSameSourceAndGeometry() {
        val cache = ArtworkBlurCache()
        val source = ImageBitmap(16, 16)
        val result = ImageBitmap(32, 32)
        cache.put(source, geometry, result)
        assertSame(result, cache.get(source, geometry))
        assertNull(cache.get(ImageBitmap(16, 16), geometry))
        assertNull(cache.get(source, geometry.copy(radiusPx = 8f)))
        assertNull(cache.get(source, geometry.copy(rasterSize = IntSize(16, 32))))
        assertNull(ArtworkBlurCache().get(source, geometry), "Accounts must not share transformed artwork")
    }

    @Test
    fun byteBudgetIncludesRetainedSourceAndUsesLeastRecentlyUsedEviction() {
        val entryBytes = (16L * 16 + 32L * 32) * 4
        val cache = ArtworkBlurCache(maxBytes = entryBytes * 2)
        val a = ImageBitmap(16, 16)
        val b = ImageBitmap(16, 16)
        val c = ImageBitmap(16, 16)
        val resultA = ImageBitmap(32, 32)
        val resultB = ImageBitmap(32, 32)
        val resultC = ImageBitmap(32, 32)
        cache.put(a, geometry, resultA)
        cache.put(b, geometry, resultB)
        assertSame(resultA, cache.get(a, geometry))
        cache.put(c, geometry, resultC)
        assertNull(cache.get(b, geometry))
        assertSame(resultA, cache.get(a, geometry))
        assertSame(resultC, cache.get(c, geometry))
    }

    @Test
    fun replacementDoesNotDoubleCountAndOversizedImagesDoNotEvictUsefulEntries() {
        val entryBytes = (16L * 16 + 32L * 32) * 4
        val cache = ArtworkBlurCache(maxBytes = entryBytes * 2)
        val a = ImageBitmap(16, 16)
        val b = ImageBitmap(16, 16)
        val result = ImageBitmap(32, 32)
        val replacement = ImageBitmap(32, 32)
        cache.put(a, geometry, result)
        cache.put(b, geometry, result)
        cache.put(a, geometry, replacement)
        val oversized = ImageBitmap(128, 128)
        cache.put(oversized, geometry, result)
        assertNull(cache.get(oversized, geometry))
        assertSame(replacement, cache.get(a, geometry))
        assertSame(result, cache.get(b, geometry))
    }

    @Test
    fun entryLimitAlsoBoundsSmallImagesAndGeometryVariants() {
        val cache = ArtworkBlurCache(maxEntries = 2)
        val source = ImageBitmap(16, 16)
        val result = ImageBitmap(32, 32)
        cache.put(source, geometry, result)
        cache.put(source, geometry.copy(radiusPx = 8f), result)
        cache.put(source, geometry.copy(radiusPx = 12f), result)
        assertNull(cache.get(source, geometry))
        assertSame(result, cache.get(source, geometry.copy(radiusPx = 8f)))
        assertSame(result, cache.get(source, geometry.copy(radiusPx = 12f)))
    }
}
