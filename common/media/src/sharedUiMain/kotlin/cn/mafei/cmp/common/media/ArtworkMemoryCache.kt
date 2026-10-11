package cn.mafei.cmp.common.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.MediaImage
import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.size.Size
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.encodeUtf8
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

/** Account-owned Coil memory cache. Only image bytes, never credentials, enter the disk cache. */
class ArtworkMemoryCache internal constructor(
    private val context: PlatformContext,
    private val namespace: String? = null,
    private val diskCache: DiskCache? = null,
    private val maxWidthPx: Int = 960,
    private val maxEncodedBytes: Int = 4 * 1024 * 1024,
    private val maxSingleBytes: Long = 16L * 1024 * 1024,
    private val maxCacheBytes: Long = 64L * 1024 * 1024,
    private val nowEpochMs: () -> Long = SystemEpochClock::nowEpochMs,
) {
    init {
        require(maxWidthPx >= 96 && maxEncodedBytes > 0 && maxSingleBytes > 0 && maxCacheBytes >= maxSingleBytes)
    }

    internal val blurCache = ArtworkBlurCache()
    private val closed = MutableStateFlow(false)
    private val mutex = Mutex()
    private class LoadGate { val lock = Mutex(); var users = 0 }
    private val gates = mutableMapOf<String, LoadGate>()
    private val failures = linkedMapOf<String, TimeMark>()
    private val maxBitmapSide = sqrt(maxSingleBytes / 4.0).toInt().coerceAtLeast(1)
    private val loader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizeBytes(maxCacheBytes).build() }
        .diskCache(diskCache)
        .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(6))
        .decoderCoroutineContext(Dispatchers.Default.limitedParallelism(1))
        .components { add(ArtworkFetcher.Factory(maxEncodedBytes, nowEpochMs, ::checkOpen)) }
        .build()

    internal suspend fun load(
        image: MediaImage,
        targetWidthPx: Int,
        fetch: suspend (MediaImage, Int) -> ByteArray?,
    ): Image? {
        checkOpen()
        val width = targetWidthPx.coerceIn(96, maxWidthPx)
        val key = artworkCacheKey(namespace, image.url, width)
        loader.memoryCache?.get(MemoryCache.Key(key))?.image?.let { return it }
        // Coil owns decoding and LRU storage. This small gate preserves MaFei's
        // same-URL coalescing and negative cache across different requested sizes.
        val gate = mutex.withLock { gates.getOrPut(image.url) { LoadGate() }.also { it.users++ } }
        return try {
            gate.lock.withLock {
                checkOpen()
                loader.memoryCache?.get(MemoryCache.Key(key))?.image?.let { return@withLock it }
                if (hasRecentFailure(image.url)) return@withLock null
                val data = ArtworkRequest(image, width, fetch)
                val request = ImageRequest.Builder(context)
                    .data(data)
                    .memoryCacheKey(key)
                    .diskCacheKey(key)
                    .diskCachePolicy(if (namespace == null) CachePolicy.DISABLED else CachePolicy.ENABLED)
                    // CPF's NativeBuffer decoder reads the explicit target size.
                    // Bound height as well so tall photos cannot bypass the bitmap budget.
                    .size(width, maxBitmapSide)
                    .precision(Precision.INEXACT)
                    .maxBitmapSize(Size(maxBitmapSide, maxBitmapSide))
                    .build()
                var result = loader.execute(request)
                // A damaged disk entry is disposable. Retry the same provider
                // image once from transport before walking the fallback chain.
                if (result is ErrorResult && data.fromDisk) {
                    removeDiskEntry(key)
                    result = loader.execute(request.newBuilder().diskCachePolicy(CachePolicy.WRITE_ONLY).build())
                }
                currentCoroutineContext().ensureActive()
                checkOpen()
                val decoded = (result as? SuccessResult)?.image
                if (decoded == null || decoded.size > maxSingleBytes) {
                    loader.memoryCache?.remove(MemoryCache.Key(key))
                    removeDiskEntry(key)
                    if (decoded != null || data.hasImageData || (result as? ErrorResult)?.throwable is ArtworkUnavailable) {
                        recordFailure(image.url)
                    }
                    return@withLock null
                }
                mutex.withLock { failures.remove(image.url) }
                decoded
            }
        } finally {
            mutex.withLock {
                gate.users--
                if (gate.users == 0 && gates[image.url] === gate) gates.remove(image.url)
            }
        }
    }

    internal fun peek(candidates: List<MediaImage>, targetWidthPx: Int): Image? =
        peekInternal(candidates, targetWidthPx, false)

    internal fun peekAnyWidth(candidates: List<MediaImage>, targetWidthPx: Int): Image? =
        peekInternal(candidates, targetWidthPx, true)

    /** Keep the same bitmap identity for the existing blur-raster cache on route reentry. */
    internal fun bitmap(image: Image): ImageBitmap {
        val memory = loader.memoryCache
        val sourceKey = memory?.keys?.firstOrNull { !it.key.endsWith(":bitmap") && memory[it]?.image === image }
        val bitmapKey = sourceKey?.copy(key = "${sourceKey.key}:bitmap")
        if (bitmapKey != null && memory != null) (memory[bitmapKey]?.image as? BitmapArtworkImage)?.let { return it.bitmap }
        val bitmap = image.toArtworkBitmap()
        if (bitmapKey != null && memory != null) memory[bitmapKey] = MemoryCache.Value(BitmapArtworkImage(image, bitmap))
        return bitmap
    }

    private class BitmapArtworkImage(image: Image, val bitmap: ImageBitmap) : Image by image {
        // Charge both references: NativeBuffer artwork may need a software copy
        // for the blur consumer. Coil's own LRU owns/evicts this derived entry.
        override val size = image.size + bitmap.width.toLong() * bitmap.height * 4
    }

    private fun peekInternal(candidates: List<MediaImage>, targetWidthPx: Int, anyWidth: Boolean): Image? {
        if (closed.value) return null
        val memory = loader.memoryCache ?: return null
        val width = targetWidthPx.coerceIn(96, maxWidthPx)
        for (candidate in candidates) memory[MemoryCache.Key(artworkCacheKey(namespace, candidate.url, width))]
            ?.image?.let { return it }
        if (anyWidth) {
            for (candidate in candidates) {
                val prefix = artworkCachePrefix(namespace, candidate.url)
                val key = memory.keys.filter { it.key.startsWith(prefix) && !it.key.endsWith(":bitmap") }
                    .minByOrNull { abs((it.key.substringAfterLast(':').toIntOrNull() ?: Int.MAX_VALUE).toLong() - width) }
                if (key != null) memory[key]?.image?.let { return it }
            }
        }
        return null
    }

    internal fun requestedWidth(width: Int): Int =
        (if (width > 960) width else artworkWidthBucket(width)).coerceIn(96, maxWidthPx)

    internal fun close() {
        if (closed.compareAndSet(false, true)) loader.shutdown()
    }

    private fun checkOpen() {
        if (closed.value) throw CancellationException("Artwork owner left")
    }

    private fun removeDiskEntry(key: String) {
        try { diskCache?.remove(key) } catch (_: okio.IOException) { /* A cache failure is not a browsing failure. */ }
    }

    private suspend fun hasRecentFailure(url: String): Boolean = mutex.withLock {
        val failedAt = failures[url] ?: return@withLock false
        if (failedAt.elapsedNow() < 30.seconds) true else { failures.remove(url); false }
    }

    private suspend fun recordFailure(url: String) = mutex.withLock {
        failures.remove(url)
        failures[url] = TimeSource.Monotonic.markNow()
        while (failures.size > 128) failures.remove(failures.keys.first())
    }
}

/** Shared lifecycle/initialization for all media pages, including the full-resolution photo viewer. */
@Composable
fun rememberArtworkMemoryCache(
    owner: Any,
    namespace: String,
    maxWidthPx: Int = 960,
    maxEncodedBytes: Int = 4 * 1024 * 1024,
    maxSingleBytes: Long = 16L * 1024 * 1024,
    maxCacheBytes: Long = 64L * 1024 * 1024,
): ArtworkMemoryCache {
    val context = LocalPlatformContext.current
    val cache = remember(owner, namespace, context, maxWidthPx, maxEncodedBytes, maxSingleBytes, maxCacheBytes) {
        ArtworkMemoryCache(context, namespace, platformArtworkDiskCache(context),
            maxWidthPx, maxEncodedBytes, maxSingleBytes, maxCacheBytes)
    }
    DisposableEffect(cache) { onDispose { cache.close() } }
    return cache
}

internal fun artworkCacheKey(namespace: String?, url: String, width: Int): String =
    "${artworkCachePrefix(namespace, url)}$width"

private fun artworkCachePrefix(namespace: String?, url: String): String =
    "mafei-artwork-v1:${"${namespace.orEmpty()}\n$url".encodeUtf8().sha256().hex()}:"

internal const val ArtworkDiskCacheBytes = 128L * 1024 * 1024
