package cn.mafei.cmp.common.media

import androidx.compose.foundation.Image
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

/** One account/screen lifetime. Small memory-only cache; no token-bearing disk cache. */
class ArtworkMemoryCache {
    private val mutex = Mutex()
    // Network latency must not occupy the scarce image-decoder slots. Keeping
    // these budgets separate lets a home viewport fetch several thumbnails at
    // once while still protecting Compose from a burst of bitmap decoding.
    private val networkPermits = Semaphore(MaxConcurrentArtworkFetches)
    private val decodePermits = Semaphore(MaxConcurrentArtworkDecodes)
    private val images = linkedMapOf<String, ImageBitmap>()
    private val keyLocks = mutableMapOf<String, Mutex>()
    private var bytes = 0L

    suspend fun load(
        image: MediaImage,
        targetWidthPx: Int,
        fetch: suspend (MediaImage, Int) -> ByteArray?,
    ): ImageBitmap? {
        val width = targetWidthPx.coerceIn(MinArtworkWidthPx, MaxArtworkWidthPx)
        val key = "${image.url}#w=$width"
        cached(key)?.let { return it }
        // Multiple visible consumers (hero + ambient continuation) share one
        // request/decode. If the first caller is cancelled, the next one can
        // safely retry after the per-key mutex is released.
        val keyLock = mutex.withLock { keyLocks.getOrPut(key) { Mutex() } }
        return keyLock.withLock {
            cached(key)?.let { return@withLock it }
            val encoded = networkPermits.withPermit { fetch(image, width) }
                ?: return@withLock null
            if (encoded.size > MaxEncodedArtworkBytes) return@withLock null
            val decoded = decodePermits.withPermit {
                withContext(Dispatchers.Default) { encoded.decodeToImageBitmap() }
            }
            val size = decoded.width.toLong() * decoded.height * 4
            if (size > MaxSingleArtworkBytes) return@withLock null
            store(key, decoded, size)
            decoded
        }
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
) = MediaArtwork(
    images = listOfNotNull(image),
    title = title,
    cache = cache,
    fetch = fetch,
    modifier = modifier,
    contentScale = contentScale,
    fallback = fallback,
    targetWidthPx = targetWidthPx,
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
) {
    val candidates = remember(images) {
        images.filter { it.url.isNotBlank() }.distinctBy { it.url }
    }
    if (targetWidthPx != null) {
        MediaArtworkContent(
            candidates = candidates,
            title = title,
            cache = cache,
            fetch = fetch,
            modifier = modifier,
            contentScale = contentScale,
            fallback = fallback,
            requestedWidthPx = remember(targetWidthPx) { artworkWidthBucket(targetWidthPx) },
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
                title = title,
                cache = cache,
                fetch = fetch,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                fallback = fallback,
                requestedWidthPx = requestedWidthPx,
            )
        }
    }
}

@Composable
private fun MediaArtworkContent(
    candidates: List<MediaImage>,
    title: String,
    cache: ArtworkMemoryCache,
    fetch: suspend (MediaImage, Int) -> ByteArray?,
    modifier: Modifier,
    contentScale: ContentScale,
    fallback: (@Composable (Modifier) -> Unit)?,
    requestedWidthPx: Int,
) {
    val bitmap by produceState<ImageBitmap?>(null, candidates, cache, requestedWidthPx) {
        value = null
        if (candidates.isNotEmpty()) {
            // Match the legacy retry budget while allowing a known-good
            // alternate image to win immediately after a 404/decode miss.
            for (attempt in 0..ArtworkRetryCount) {
                for (candidate in candidates) {
                    try {
                        value = cache.load(candidate, requestedWidthPx, fetch)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        value = null // Broken artwork must not break browsing.
                    }
                    if (value != null) break
                }
                if (value != null || attempt == ArtworkRetryCount) break
                delay(ArtworkRetryDelayMs * (attempt + 1))
            }
            if (value == null) {
                // 临时诊断打点：图片加载失败定位用，定位完成后整体删除。
                println(
                    "[MaFeiTiming] artwork gave up: title=$title " +
                        candidates.joinToString("|") { c -> c.url.substringBefore("?").substringAfterLast("/") },
                )
            }
        }
    }
    val loaded = bitmap
    if (loaded == null) {
        if (fallback == null) TextPoster(title, modifier)
        else fallback(modifier)
    } else {
        Image(
            loaded,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
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
) {
    val requested = artworkWidthBucket(targetWidthPx)
    val candidates = item.landscapeArtworkCandidates()
        .filter { it.url.isNotBlank() }
        .distinctBy { it.url }
    for (candidate in candidates) {
        val loaded = runCatching { cache.load(candidate, requested, fetch) }.getOrNull()
        if (loaded != null) return
    }
}

private fun MediaItem.artworkCandidates(vararg preferredKinds: ImageKind): List<MediaImage> = buildList {
    preferredKinds.forEach { kind -> addAll(images.filter { it.kind == kind }) }
    addAll(images)
}.filter { it.url.isNotBlank() }.distinctBy { it.url }

private fun artworkWidthBucket(widthPx: Int): Int = when {
    widthPx <= 160 -> 160
    widthPx <= 240 -> 240
    widthPx <= 360 -> 360
    widthPx <= 480 -> 480
    widthPx <= 720 -> 720
    else -> 960
}

private const val MinArtworkWidthPx = 96
private const val MaxArtworkWidthPx = 960
private const val DefaultArtworkWidthPx = 480
private const val ArtworkRetryCount = 2
private const val ArtworkRetryDelayMs = 300L
private const val MaxEncodedArtworkBytes = 4 * 1024 * 1024
private const val MaxSingleArtworkBytes = 16L * 1024 * 1024
private const val MaxArtworkCacheBytes = 64L * 1024 * 1024
private const val MaxConcurrentArtworkFetches = 6
private const val MaxConcurrentArtworkDecodes = 2
