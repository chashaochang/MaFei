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
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** One account/screen lifetime. Small memory-only cache; no token-bearing disk cache. */
class ArtworkMemoryCache {
    private class UrlLoadGate {
        val lock = Mutex()
        var users: Int = 0
    }

    private val mutex = Mutex()
    // Network latency must not occupy the scarce image-decoder slots. Keeping
    // these budgets separate lets a home viewport fetch several thumbnails at
    // once while still protecting Compose from a burst of bitmap decoding.
    private val networkPermits = Semaphore(MaxConcurrentArtworkFetches)
    private val decodePermits = Semaphore(MaxConcurrentArtworkDecodes)
    private val images = linkedMapOf<String, ImageBitmap>()
    private val urlLoadGates = mutableMapOf<String, UrlLoadGate>()
    private val failedUrls = linkedMapOf<String, TimeMark>()
    private var bytes = 0L

    suspend fun load(
        image: MediaImage,
        targetWidthPx: Int,
        fetch: suspend (MediaImage, Int) -> ByteArray?,
    ): ImageBitmap? {
        val width = targetWidthPx.coerceIn(MinArtworkWidthPx, MaxArtworkWidthPx)
        val key = artworkCacheKey(image.url, width)
        cached(key)?.let { return it }
        // Multiple visible consumers (hero + ambient continuation) share one
        // request/decode. Lock by URL rather than width so a missing image does
        // not fan out into simultaneous 160/240/960px 404s.
        val gate = mutex.withLock {
            urlLoadGates.getOrPut(image.url) { UrlLoadGate() }.also { it.users += 1 }
        }
        return try {
            gate.lock.withLock {
                cached(key)?.let { return@withLock it }
                if (hasRecentFailure(image.url)) return@withLock null
                val encoded = networkPermits.withPermit { fetch(image, width) }
                    ?: run {
                        recordFailure(image.url)
                        return@withLock null
                    }
                if (encoded.size > MaxEncodedArtworkBytes) {
                    recordFailure(image.url)
                    return@withLock null
                }
                val decoded = try {
                    decodePermits.withPermit {
                        withContext(Dispatchers.Default) { encoded.decodeToImageBitmap() }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    recordFailure(image.url)
                    return@withLock null
                }
                val size = decoded.width.toLong() * decoded.height * 4
                if (size > MaxSingleArtworkBytes) {
                    recordFailure(image.url)
                    return@withLock null
                }
                clearFailure(image.url)
                store(key, decoded, size)
                decoded
            }
        } finally {
            mutex.withLock {
                gate.users -= 1
                if (gate.users == 0 && urlLoadGates[image.url] === gate) {
                    urlLoadGates.remove(image.url)
                }
            }
        }
    }

    /**
     * Fast UI-thread cache probe used only to avoid a one-frame placeholder
     * flash when a recycled card already has its bitmap in memory. A contended
     * cache simply falls back to the normal suspending load path.
     */
    internal fun peek(candidates: List<MediaImage>, targetWidthPx: Int): ImageBitmap? {
        return peekInternal(candidates, targetWidthPx, allowOtherWidth = false)
    }

    /**
     * Warm-start probe for a destination that requests a different size than
     * the source card. A home poster may already have the exact same URL at
     * 360/480px while a detail hero asks for 720px. Reusing that bitmap for the
     * first frame avoids replacing the shared-transition image with TextPoster;
     * the normal load still runs afterwards to fill the requested-size entry.
     */
    internal fun peekAnyWidth(candidates: List<MediaImage>, targetWidthPx: Int): ImageBitmap? {
        return peekInternal(candidates, targetWidthPx, allowOtherWidth = true)
    }

    private fun peekInternal(
        candidates: List<MediaImage>,
        targetWidthPx: Int,
        allowOtherWidth: Boolean,
    ): ImageBitmap? {
        if (!mutex.tryLock()) return null
        return try {
            val width = targetWidthPx.coerceIn(MinArtworkWidthPx, MaxArtworkWidthPx)
            for (candidate in candidates) {
                val key = artworkCacheKey(candidate.url, width)
                val hit = images.remove(key) ?: continue
                images[key] = hit
                return hit
            }
            if (allowOtherWidth) {
                for (candidate in candidates) {
                    val prefix = "${candidate.url}#w="
                    val key = images.keys
                        .asSequence()
                        .filter { it.startsWith(prefix) }
                        .minByOrNull { cachedWidth(it, width) }
                        ?: continue
                    val hit = images.remove(key) ?: continue
                    images[key] = hit
                    return hit
                }
            }
            null
        } finally {
            mutex.unlock()
        }
    }

    private fun cachedWidth(key: String, requestedWidth: Int): Int {
        val width = key.substringAfter("#w=", "").toIntOrNull() ?: return Int.MAX_VALUE
        return abs(width - requestedWidth)
    }

    private suspend fun cached(key: String): ImageBitmap? = mutex.withLock {
        // Reinsertion promotes a hit to the MRU end of the linked map.
        images.remove(key)?.also { images[key] = it }
    }

    private suspend fun store(key: String, decoded: ImageBitmap, size: Long) {
        mutex.withLock {
            images.remove(key)?.let { bytes -= it.width.toLong() * it.height * 4 }
            while (images.isNotEmpty() && bytes + size > MaxArtworkCacheBytes) {
                val oldest = images.remove(images.keys.first())!!
                bytes -= oldest.width.toLong() * oldest.height * 4
            }
            images[key] = decoded
            bytes += size
        }
    }

    private suspend fun hasRecentFailure(url: String): Boolean = mutex.withLock {
        val failedAt = failedUrls[url] ?: return@withLock false
        if (failedAt.elapsedNow() < ArtworkFailureTtl) {
            true
        } else {
            failedUrls.remove(url)
            false
        }
    }

    private suspend fun recordFailure(url: String) {
        mutex.withLock {
            // Refresh insertion order and keep malformed/missing catalog URLs
            // from accumulating for the complete account session.
            failedUrls.remove(url)
            failedUrls[url] = TimeSource.Monotonic.markNow()
            while (failedUrls.size > MaxFailedArtworkUrls) {
                failedUrls.remove(failedUrls.keys.first())
            }
        }
    }

    private suspend fun clearFailure(url: String) {
        mutex.withLock { failedUrls.remove(url) }
    }
}

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
            requestedWidthPx = remember(targetWidthPx) { artworkWidthBucket(targetWidthPx) },
            revealDurationMillis = revealDurationMillis,
        )
    } else {
        BoxWithConstraints(modifier) {
            val density = LocalDensity.current
            val requestedWidthPx = remember(maxWidth, density) {
                val measured = if (maxWidth.value.isFinite() && maxWidth.value > 0f) {
                    with(density) { maxWidth.roundToPx() }
                } else {
                    DefaultArtworkWidthPx
                }
                artworkWidthBucket(measured)
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
    val bitmap by produceState<ImageBitmap?>(
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
        ArtworkContent(loaded, title, fallback, modifier, contentScale)
    } else {
        Crossfade(
            targetState = loaded,
            modifier = modifier,
            animationSpec = tween(revealDurationMillis),
            label = "artwork-reveal",
        ) { artwork ->
            ArtworkContent(artwork, title, fallback, Modifier.fillMaxSize(), contentScale)
        }
    }
}

@Composable
private fun ArtworkContent(
    artwork: ImageBitmap?,
    title: String,
    fallback: (@Composable (Modifier) -> Unit)?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    Box(modifier) {
        if (artwork == null) {
            if (fallback == null) TextPoster(title, Modifier.fillMaxSize())
            else fallback(Modifier.fillMaxSize())
        } else {
            Image(
                artwork,
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
    val requested = artworkWidthBucket(targetWidthPx)
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

private fun artworkWidthBucket(widthPx: Int): Int = when {
    widthPx <= 160 -> 160
    widthPx <= 240 -> 240
    widthPx <= 360 -> 360
    widthPx <= 480 -> 480
    widthPx <= 720 -> 720
    else -> 960
}

private fun artworkCacheKey(url: String, widthPx: Int): String = "$url#w=$widthPx"

private const val MinArtworkWidthPx = 96
private const val MaxArtworkWidthPx = 960
private const val DefaultArtworkWidthPx = 480
private const val DefaultArtworkRevealMillis = 180
private const val ArtworkRetryCount = 2
private const val ArtworkRetryDelayMs = 300L
private val ArtworkFailureTtl = 30.seconds
private const val MaxEncodedArtworkBytes = 4 * 1024 * 1024
private const val MaxSingleArtworkBytes = 16L * 1024 * 1024
private const val MaxArtworkCacheBytes = 64L * 1024 * 1024
private const val MaxFailedArtworkUrls = 128
private const val MaxConcurrentArtworkFetches = 6
// Image decoding is CPU- and allocation-heavy on CPF/OHOS. Two simultaneous
// 720/960px decodes regularly completed during the same scroll frame on tablet,
// producing a visible cold-start hitch even though the work ran on Default.
// Keep network I/O concurrent, but serialize decode/publication pressure.
private const val MaxConcurrentArtworkDecodes = 1
