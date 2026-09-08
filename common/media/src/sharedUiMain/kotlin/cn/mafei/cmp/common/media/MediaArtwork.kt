package cn.mafei.cmp.common.media

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import cn.mafei.cmp.common.ui.TextPoster
import cn.mafei.cmp.core.domain.MediaImage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.compose.resources.decodeToImageBitmap

/** One account/screen lifetime. Small memory-only cache; no token-bearing disk cache. */
class ArtworkMemoryCache {
    private val mutex = Mutex()
    private val permits = Semaphore(4)
    private val images = linkedMapOf<String, ImageBitmap>()
    private var bytes = 0L

    suspend fun load(image: MediaImage, fetch: suspend (MediaImage) -> ByteArray?): ImageBitmap? = permits.withPermit {
        mutex.withLock { images[image.url] }?.let { return@withPermit it }
        val encoded = fetch(image) ?: return@withPermit null
        if (encoded.size > 4 * 1024 * 1024) return@withPermit null
        val decoded = withContext(Dispatchers.Default) { encoded.decodeToImageBitmap() }
        val size = decoded.width.toLong() * decoded.height * 4
        if (size > 12L * 1024 * 1024) return@withPermit null
        mutex.withLock {
            images.remove(image.url)?.let { bytes -= it.width.toLong() * it.height * 4 }
            while (images.isNotEmpty() && bytes + size > 24L * 1024 * 1024) {
                val oldest = images.remove(images.keys.first())!!
                bytes -= oldest.width.toLong() * oldest.height * 4
            }
            images[image.url] = decoded
            bytes += size
        }
        decoded
    }
}

@Composable
fun MediaArtwork(
    image: MediaImage?,
    title: String,
    cache: ArtworkMemoryCache,
    fetch: suspend (MediaImage) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(null, image, cache) {
        value = null
        if (image != null) {
            try { value = cache.load(image, fetch) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = null } // Broken artwork must not break browsing.
        }
    }
    val loaded = bitmap
    if (loaded == null) TextPoster(title, modifier)
    else Image(loaded, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}
