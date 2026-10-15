package cn.mafei.cmp.common.media

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import cn.mafei.cmp.common.ui.TextPoster
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import coil3.Image as CoilImage
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import kotlinx.coroutines.*

@Composable
fun MediaArtwork(
    image: MediaImage?,
    title: String,
    cache: ArtworkMemoryCache,
    fetch: suspend (MediaImage, Int) -> ByteArray?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: (@Composable (Modifier) -> Unit)? = null,
    targetWidthPx: Int? = null,
    warmStartImages: List<MediaImage> = emptyList(),
    revealDurationMillis: Int = DefaultArtworkRevealMillis,
    bitmapContent: (@Composable (ImageBitmap, Modifier) -> Unit)? = null,
) = MediaArtwork(
    images = listOfNotNull(image),
    title = title,
    cache = cache,
    fetch = fetch,
    modifier = modifier,
    contentScale = contentScale,
    fallback = fallback,
    targetWidthPx = targetWidthPx,
    warmStartImages = warmStartImages,
    revealDurationMillis = revealDurationMillis,
    bitmapContent = bitmapContent,
)

/**
 * Loads the first usable artwork candidate.
 *
 * Jellyfin can advertise a canonical Primary route that returns 404 while a
 * parent Thumb or Backdrop is valid. Retrying that same URL cannot recover the
 * card, so every retry round walks the complete provider-ordered candidate
 * chain before waiting.
 */
@Composable
fun MediaArtwork(
    images: List<MediaImage>,
    title: String,
    cache: ArtworkMemoryCache,
    fetch: suspend (MediaImage, Int) -> ByteArray?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: (@Composable (Modifier) -> Unit)? = null,
    targetWidthPx: Int? = null,
    warmStartImages: List<MediaImage> = emptyList(),
    revealDurationMillis: Int = DefaultArtworkRevealMillis,
    bitmapContent: (@Composable (ImageBitmap, Modifier) -> Unit)? = null,
) {
    val candidates = remember(images) {
        images.filter { it.url.isNotBlank() }.distinctBy { it.url }
    }
    val warmCandidates = remember(warmStartImages) {
        warmStartImages.filter { it.url.isNotBlank() }.distinctBy { it.url }
    }
    if (targetWidthPx != null) {
        MediaArtworkContent(
            candidates = candidates,
            warmCandidates = warmCandidates,
            title = title,
            cache = cache,
            fetch = fetch,
            modifier = modifier,
            contentScale = contentScale,
            fallback = fallback,
            requestedWidthPx = remember(targetWidthPx, cache) { cache.requestedWidth(targetWidthPx) },
            revealDurationMillis = revealDurationMillis,
            bitmapContent = bitmapContent,
        )
    } else {
        BoxWithConstraints(modifier) {
            val density = LocalDensity.current
            val requestedWidthPx = remember(maxWidth, density, cache) {
                val measured = if (maxWidth.value.isFinite() && maxWidth.value > 0f) {
                    with(density) { maxWidth.roundToPx() }
                } else {
                    DefaultArtworkWidthPx
                }
                cache.requestedWidth(measured)
            }
            MediaArtworkContent(
                candidates = candidates,
                warmCandidates = warmCandidates,
                title = title,
                cache = cache,
                fetch = fetch,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                fallback = fallback,
                requestedWidthPx = requestedWidthPx,
                revealDurationMillis = revealDurationMillis,
                bitmapContent = bitmapContent,
            )
        }
    }
}

@Composable
private fun MediaArtworkContent(
    candidates: List<MediaImage>,
    warmCandidates: List<MediaImage>,
    title: String,
    cache: ArtworkMemoryCache,
    fetch: suspend (MediaImage, Int) -> ByteArray?,
    modifier: Modifier,
    contentScale: ContentScale,
    fallback: (@Composable (Modifier) -> Unit)?,
    requestedWidthPx: Int,
    revealDurationMillis: Int,
    bitmapContent: (@Composable (ImageBitmap, Modifier) -> Unit)?,
) {
    val cachedBitmap = remember(candidates, cache, requestedWidthPx) {
        cache.peek(candidates, requestedWidthPx)
    }
    val warmBitmap = remember(candidates, warmCandidates, cache, requestedWidthPx, cachedBitmap) {
        if (cachedBitmap == null) {
            cache.peekAnyWidth(warmCandidates, requestedWidthPx)
                ?: cache.peekAnyWidth(candidates, requestedWidthPx)
        } else {
            null
        }
    }
    val initialBitmap = cachedBitmap ?: warmBitmap
    val bitmap by produceState<CoilImage?>(
        initialBitmap,
        candidates,
        cache,
        requestedWidthPx,
        warmCandidates,
    ) {
        value = initialBitmap
        // A warm bitmap is only the first frame. Still request the target-size
        // image so a 360px home poster does not permanently become a blurry
        // 720px detail hero.
        if (cachedBitmap == null && candidates.isNotEmpty()) {
            // Match the legacy retry budget while allowing a known-good
            // alternate image to win immediately after a 404/decode miss.
            var destinationLoaded = false
            for (attempt in 0..ArtworkRetryCount) {
                for (candidate in candidates) {
                    val resolved = try {
                        cache.load(candidate, requestedWidthPx, fetch)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null // Broken artwork must not break browsing.
                    }
                    // Keep the warm source image visible while the destination
                    // request is missing or malformed. Only a real decoded
                    // destination replaces it; a failed request must not turn
                    // the already useful first frame back into TextPoster.
                    if (resolved != null) {
                        value = resolved
                        destinationLoaded = true
                        break
                    }
                }
                if (destinationLoaded || attempt == ArtworkRetryCount) break
                delay(ArtworkRetryDelayMs * (attempt + 1))
            }
        }
    }
    val loaded = bitmap
    // A recycled card whose bitmap is already in the memory cache should not
    // replay the reveal animation. On a fast scroll dozens of cache hits can
    // otherwise schedule Crossfade work in the same frame as the lazy layout.
    // Keep the reveal only for a real placeholder -> decoded-image transition.
    if (cachedBitmap != null) {
        ArtworkContent(loaded, title, fallback, modifier, contentScale, bitmapContent, cache)
    } else {
        Crossfade(
            targetState = loaded,
            modifier = modifier,
            animationSpec = tween(revealDurationMillis),
            label = "artwork-reveal",
        ) { artwork ->
            ArtworkContent(artwork, title, fallback, Modifier.fillMaxSize(), contentScale, bitmapContent, cache)
        }
    }
}

@Composable
private fun ArtworkContent(
    artwork: CoilImage?,
    title: String,
    fallback: (@Composable (Modifier) -> Unit)?,
    modifier: Modifier,
    contentScale: ContentScale,
    bitmapContent: (@Composable (ImageBitmap, Modifier) -> Unit)?,
    cache: ArtworkMemoryCache,
) {
    Box(modifier) {
        if (artwork == null) {
            if (fallback == null) TextPoster(title, Modifier.fillMaxSize())
            else fallback(Modifier.fillMaxSize())
        } else if (bitmapContent != null) {
            // Ordinary posters draw Coil's native image directly. Only the
            // existing blurred-background consumer needs a software bitmap.
            val bitmap = remember(artwork, cache) { cache.bitmap(artwork) }
            bitmapContent(bitmap, Modifier.fillMaxSize())
        } else {
            val context = LocalPlatformContext.current
            Image(
                painter = remember(artwork, context) { artwork.asPainter(context) },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

fun MediaItem.posterArtworkCandidates(): List<MediaImage> = artworkCandidates(
    ImageKind.Primary,
    ImageKind.Thumb,
    ImageKind.Backdrop,
)

fun MediaItem.landscapeArtworkCandidates(): List<MediaImage> = artworkCandidates(
    ImageKind.Backdrop,
    ImageKind.Thumb,
    ImageKind.Primary,
)

/**
 * Exact `VideoDetailViewModel.applyDetail` / `SeasonDetailViewModel.applyDetail`
 * artwork rule from the ArkUI client: the immersive hero uses the item's
 * backdrop and falls back directly to its primary image. A thumbnail is not a
 * valid intermediate fallback on these pages.
 */
fun MediaItem.legacyDetailBackdropArtworkCandidates(): List<MediaImage> =
    exactArtworkCandidates(ImageKind.Backdrop, ImageKind.Primary)

/** Exact ArkUI `BaseItemCard` / season-poster source: primary image only. */
fun MediaItem.legacyPrimaryArtworkCandidates(): List<MediaImage> =
    exactArtworkCandidates(ImageKind.Primary)

/**
 * Exact ArkUI season-card / episode-card source: primary first, then backdrop.
 * This intentionally differs from the reusable landscape candidate list.
 */
fun MediaItem.legacyPrimaryThenBackdropArtworkCandidates(): List<MediaImage> =
    exactArtworkCandidates(ImageKind.Primary, ImageKind.Backdrop)

/**
 * 把一个 item 的横版图按目标档位预载进内存缓存。
 *
 * 轮播背景的翻页跟手序列会在拖动瞬间把相邻页组合进视口，模糊层的 Crossfade 也会在
 * settle 时切到新 item —— 两者的图片都走「进组合才开始加载」，加载完成前是透明的，
 * 叠加起来就是翻页瞬间露出的黑色空窗。对轮播的全部 item 预载同档位位图后，翻页时
 * 缓存必命中，空窗不再出现。
 */
suspend fun prefetchLandscapeArtwork(
    item: MediaItem,
    cache: ArtworkMemoryCache,
    fetch: suspend (MediaImage, Int) -> ByteArray?,
    targetWidthPx: Int = 960,
) = prefetchArtwork(item.landscapeArtworkCandidates(), cache, fetch, targetWidthPx)

/** Prefetch an explicitly ordered artwork chain without changing its fallback semantics. */
suspend fun prefetchArtwork(
    images: List<MediaImage>,
    cache: ArtworkMemoryCache,
    fetch: suspend (MediaImage, Int) -> ByteArray?,
    targetWidthPx: Int = 960,
) {
    val requested = cache.requestedWidth(targetWidthPx)
    val candidates = images
        .filter { it.url.isNotBlank() }
        .distinctBy { it.url }
    for (candidate in candidates) {
        val loaded = try {
            cache.load(candidate, requested, fetch)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (loaded != null) return
    }
}

private fun MediaItem.artworkCandidates(vararg preferredKinds: ImageKind): List<MediaImage> = buildList {
    preferredKinds.forEach { kind -> addAll(images.filter { it.kind == kind }) }
    addAll(images)
}.filter { it.url.isNotBlank() }.distinctBy { it.url }

private fun MediaItem.exactArtworkCandidates(vararg kinds: ImageKind): List<MediaImage> = buildList {
    kinds.forEach { kind -> addAll(images.filter { it.kind == kind }) }
}.filter { it.url.isNotBlank() }.distinctBy { it.url }

internal fun artworkWidthBucket(widthPx: Int): Int = when {
    widthPx <= 160 -> 160
    widthPx <= 240 -> 240
    widthPx <= 360 -> 360
    widthPx <= 480 -> 480
    widthPx <= 720 -> 720
    else -> 960
}

private const val DefaultArtworkWidthPx = 480
private const val DefaultArtworkRevealMillis = 180
private const val ArtworkRetryCount = 2
private const val ArtworkRetryDelayMs = 300L
