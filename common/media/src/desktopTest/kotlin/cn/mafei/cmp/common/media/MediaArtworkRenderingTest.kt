package cn.mafei.cmp.common.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.core.domain.MediaImage
import coil3.PlatformContext
import org.junit.After
import org.junit.Rule
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaArtworkRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val cache = ArtworkMemoryCache(PlatformContext.INSTANCE)
    private val missing = MediaImage(url = "https://images.test/missing")
    private val cover = MediaImage(url = "https://images.test/red")

    @After fun releaseOwner() { cache.close() }

    @Test
    fun nativeCoilPainterDisplaysTheFirstUsableCandidate() {
        val requests = CopyOnWriteArrayList<String>()
        compose.setContent {
            MediaArtwork(listOf(missing, cover), "Cover", cache,
                fetch = { image, _ -> requests += image.url; if (image == cover) redPng() else null },
                modifier = Modifier.size(80.dp, 40.dp).background(Color.Black).testTag("art"),
                targetWidthPx = 240, revealDurationMillis = 0)
        }
        compose.waitUntil(5_000) { cache.peek(listOf(cover), 240) != null }
        compose.waitForIdle()
        assertRedCenter()
        assertEquals(listOf(missing.url, cover.url), requests.toList())
    }

    @Test
    fun decodedCoilImageStillFeedsTheExistingBlurredBackdrop() {
        compose.setContent {
            MediaArtwork(cover, "Cover", cache, { _, _ -> redPng() },
                modifier = Modifier.size(80.dp, 40.dp).background(Color.White).testTag("art"),
                targetWidthPx = 240, revealDurationMillis = 0,
                bitmapContent = { bitmap, modifier -> CachedBlurredArtwork(bitmap, 8.dp, modifier, cache) })
        }
        compose.waitUntil(5_000) { cache.peek(listOf(cover), 240) != null }
        compose.waitForIdle()
        assertRedCenter()
    }

    private fun assertRedCenter() {
        val pixels = compose.onNodeWithTag("art").captureToImage().toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue(center.red > .95f && center.green < .05f && center.blue < .05f)
    }

    private fun redPng(): ByteArray = ByteArrayOutputStream().use { output ->
        val image = BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until image.width) for (y in 0 until image.height) image.setRGB(x, y, 0xFF0000)
        check(ImageIO.write(image, "png", output))
        output.toByteArray()
    }
}
