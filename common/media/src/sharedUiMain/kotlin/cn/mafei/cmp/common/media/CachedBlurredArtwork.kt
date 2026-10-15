package cn.mafei.cmp.common.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.roundToInt

/** Static cover atmosphere only. Navigation glass must keep sampling the live scene. */
@Composable
fun CachedBlurredArtwork(
    source: ImageBitmap,
    radius: Dp,
    modifier: Modifier = Modifier,
    cache: ArtworkMemoryCache? = null,
) {
    if (radius <= 0.dp) {
        Image(source, null, modifier, contentScale = ContentScale.Crop)
        return
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val geometry = remember(maxWidth, maxHeight, density, radius, source) {
            with(density) {
                artworkBlurGeometry(
                    IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()),
                    IntSize(source.width, source.height),
                    radius.toPx(),
                )
            }
        }
        // A completed carousel cycle or returning route can reuse the raster without
        // briefly installing a full-window live blur while the coroutine starts.
        key(source, geometry, cache) {
            val hit = remember { cache?.blurCache?.get(source, geometry) }
            if (hit != null) {
                Image(hit, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            } else {
                BlurredArtworkMiss(source, geometry, radius, cache)
            }
        }
    }
}

@Composable
private fun BlurredArtworkMiss(
    source: ImageBitmap,
    geometry: ArtworkBlurGeometry,
    radius: Dp,
    cache: ArtworkMemoryCache?,
) {
    val layer = rememberGraphicsLayer()
    val blurred by produceState<ImageBitmap?>(null, layer) {
        cache?.blurCache?.get(source, geometry)?.let {
            value = it
            return@produceState
        }
        layer.renderEffect = BlurEffect(geometry.radiusPx, geometry.radiusPx, TileMode.Clamp)
        layer.record(Density(1f), LayoutDirection.Ltr, geometry.rasterSize) {
            drawImage(source, dstOffset = geometry.imageOffset, dstSize = geometry.imageSize)
        }
        try {
            val result = layer.toImageBitmap()
            currentCoroutineContext().ensureActive()
            cache?.blurCache?.put(source, geometry, result)
            value = result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Failed snapshots are not cached, so a later visit can retry.
            value = null
        }
    }
    val cached = blurred
    if (cached != null) {
        Image(cached, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    } else {
        // Fallback is still the original blur, not an unblurred flash.
        Image(source, null, Modifier.fillMaxSize().blur(radius), contentScale = ContentScale.Crop)
    }
}

internal data class ArtworkBlurGeometry(
    val rasterSize: IntSize,
    val imageSize: IntSize,
    val imageOffset: IntOffset,
    val radiusPx: Float,
)

internal fun artworkBlurGeometry(viewport: IntSize, source: IntSize, radiusPx: Float): ArtworkBlurGeometry {
    val width = viewport.width.coerceAtLeast(1)
    val height = viewport.height.coerceAtLeast(1)
    // Blur removes high-frequency detail. Rasterize its result once at a bounded
    // resolution; scaling the blur radius preserves the same apparent softness.
    val scale = minOf(1f, 512f / max(width, height))
    val raster = IntSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    val crop = max(raster.width.toFloat() / source.width, raster.height.toFloat() / source.height)
    val image = IntSize((source.width * crop).roundToInt(), (source.height * crop).roundToInt())
    return ArtworkBlurGeometry(
        raster,
        image,
        IntOffset((raster.width - image.width) / 2, (raster.height - image.height) / 2),
        (radiusPx * scale).coerceAtLeast(.01f),
    )
}
