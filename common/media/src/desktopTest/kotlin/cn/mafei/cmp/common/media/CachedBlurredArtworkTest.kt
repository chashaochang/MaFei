package cn.mafei.cmp.common.media

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import coil3.PlatformContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class CachedBlurredArtworkTest {
    @get:org.junit.Rule
    val compose = createComposeRule()

    @Test
    fun revisitingCoverAndReenteringRouteReuseRasterButAccountChangeDoesNot() {
        val first = splitImage(Color.Red, Color.Blue)
        val second = splitImage(Color.Green, Color.Green)
        val source = mutableStateOf(first)
        val visible = mutableStateOf(true)
        val accountCache = mutableStateOf(ArtworkMemoryCache(PlatformContext.INSTANCE))
        lateinit var geometry: ArtworkBlurGeometry
        compose.setContent {
            with(LocalDensity.current) {
                geometry = artworkBlurGeometry(
                    IntSize(160.dp.roundToPx(), 80.dp.roundToPx()), IntSize(80, 40), 12.dp.toPx(),
                )
            }
            if (visible.value) {
                CachedBlurredArtwork(source.value, 12.dp, Modifier.size(160.dp, 80.dp), accountCache.value)
            }
        }
        compose.waitForIdle()
        lateinit var original: ImageBitmap
        compose.runOnIdle { original = assertNotNull(accountCache.value.blurCache.get(first, geometry)) }
        compose.runOnIdle { source.value = second }
        compose.waitForIdle()
        compose.runOnIdle { source.value = first }
        compose.waitForIdle()
        compose.runOnIdle { assertSame(original, accountCache.value.blurCache.get(first, geometry)) }
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertSame(original, accountCache.value.blurCache.get(first, geometry)) }
        compose.runOnIdle { accountCache.value = ArtworkMemoryCache(PlatformContext.INSTANCE) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNotSame(original, assertNotNull(accountCache.value.blurCache.get(first, geometry)))
        }
    }

    @Test
    fun cachedResultIsDisplayedDirectlyWithoutRegeneratingIt() {
        val source = splitImage(Color.Red, Color.Red)
        val cache = ArtworkMemoryCache(PlatformContext.INSTANCE)
        compose.setContent {
            with(LocalDensity.current) {
                val geometry = artworkBlurGeometry(
                    IntSize(160.dp.roundToPx(), 80.dp.roundToPx()), IntSize(80, 40), 12.dp.toPx(),
                )
                cache.blurCache.put(source, geometry, splitImage(Color.Green, Color.Green))
            }
            CachedBlurredArtwork(source, 12.dp, Modifier.size(160.dp, 80.dp).testTag("hit"), cache)
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("hit").captureToImage().toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue(center.green > .95f && center.red < .05f, "A hit must bypass rasterization")
    }

    @Test
    fun snapshotKeepsBlurAndUpdatesOnCoverAndViewportChanges() {
        val source = mutableStateOf(splitImage(Color.Red, Color.Blue))
        val width = mutableStateOf(160.dp)
        compose.setContent {
            CachedBlurredArtwork(source.value, 12.dp, Modifier.size(width.value, 80.dp).testTag("ambient"))
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("ambient").captureToImage().toPixelMap()
        val middle = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue(middle.red > .2f && middle.blue > .2f, "The cached result must actually blur the cover: $middle, ${pixels.width}x${pixels.height}")
        assertTrue(pixels[2, pixels.height / 2].red > .9f)
        compose.runOnIdle {
            source.value = splitImage(Color.Green, Color.Green)
            width.value = 80.dp
        }
        compose.waitForIdle()
        val updated = compose.onNodeWithTag("ambient").captureToImage().toPixelMap()
        val center = updated[updated.width / 2, updated.height / 2]
        assertTrue(center.green > .95f && center.red < .05f && center.blue < .05f)
        assertTrue(updated.width < pixels.width, "Viewport changes must not retain the previous raster geometry")
    }

    private fun splitImage(left: Color, right: Color): ImageBitmap = ImageBitmap(80, 40).also {
        val canvas = Canvas(it)
        canvas.drawRect(Rect(0f, 0f, 40f, 40f), Paint().apply { color = left })
        canvas.drawRect(Rect(40f, 0f, 80f, 40f), Paint().apply { color = right })
    }
}
