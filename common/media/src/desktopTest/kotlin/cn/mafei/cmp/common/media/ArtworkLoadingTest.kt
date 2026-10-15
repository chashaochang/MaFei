package cn.mafei.cmp.common.media

import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaImage
import coil3.PlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertNull

class ArtworkLoadingTest {
    private val image = MediaImage(ImageKind.Primary, "https://images.test/cover?tag=v1")

    @Test
    fun decodeUsesTheRequestedWidthInsteadOfAllocatingTheFullSource() = runBlocking {
        val cache = ArtworkMemoryCache(PlatformContext.INSTANCE)
        val bitmap = assertNotNull(cache.load(image, 240) { _, width ->
            assertEquals(240, width)
            png(1_200, 600)
        })
        assertEquals(240, bitmap.width)
        assertEquals(120, bitmap.height)
    }

    @Test
    fun aRepeatedRequestReusesTheDecodedBitmap() = runBlocking {
        val cache = ArtworkMemoryCache(PlatformContext.INSTANCE)
        var requests = 0
        val fetch: suspend (MediaImage, Int) -> ByteArray? = { _, _ -> requests++; png(120, 60) }
        val first = assertNotNull(cache.load(image, 240, fetch))
        assertSame(first, cache.load(image, 240, fetch))
        assertSame(first, cache.peek(listOf(image), 240))
        assertEquals(1, requests)
    }

    @Test
    fun cancellationDoesNotPoisonTheCandidateForTheNextRequest() = runBlocking {
        val cache = ArtworkMemoryCache(PlatformContext.INSTANCE)
        assertFailsWith<CancellationException> {
            cache.load(image, 240) { _, _ -> throw CancellationException("route left") }
        }
        assertNotNull(cache.load(image, 240) { _, _ -> png(120, 60) })
        Unit
    }

    @Test
    fun transientTransportFailuresRemainRetryable() = runBlocking {
        val cache = ArtworkMemoryCache(PlatformContext.INSTANCE)
        assertNull(cache.load(image, 240) { _, _ -> throw IOException("Temporary network failure") })
        assertNotNull(cache.load(image, 240) { _, _ -> png(120, 60) })
        Unit
    }

    @Test
    fun blurConsumersReuseTheSameBitmapAcrossCompositions() = runBlocking {
        val cache = ArtworkMemoryCache(PlatformContext.INSTANCE)
        val loaded = assertNotNull(cache.load(image, 240) { _, _ -> png(120, 60) })
        val bitmap = cache.bitmap(loaded)
        assertSame(bitmap, cache.bitmap(assertNotNull(cache.peek(listOf(image), 240))))
        assertSame(loaded, cache.peekAnyWidth(listOf(image), 960))
    }
}

internal fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().use { output ->
    check(ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output))
    output.toByteArray()
}
