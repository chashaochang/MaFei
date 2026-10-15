package cn.mafei.cmp.common.media

import cn.mafei.cmp.core.domain.MediaImage
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.Buffer
import okio.IOException

/** Runtime-only provider callback; never serialized or handed to the default URL fetcher. */
internal class ArtworkRequest(
    val image: MediaImage,
    val width: Int,
    val fetch: suspend (MediaImage, Int) -> ByteArray?,
) {
    var fromDisk = false
    var hasImageData = false
    override fun toString(): String = "ArtworkRequest(redacted)"
}

internal class ArtworkFetcher private constructor(
    private val data: ArtworkRequest,
    private val options: Options,
    private val disk: DiskCache?,
    private val maxEncodedBytes: Int,
    private val nowEpochMs: () -> Long,
    private val checkOwner: () -> Unit,
    private val networkPermits: Semaphore,
) : Fetcher {
    override suspend fun fetch(): SourceFetchResult {
        currentCoroutineContext().ensureActive()
        checkOwner()
        data.fromDisk = false
        data.hasImageData = false
        val key = options.diskCacheKey
        if (disk != null && key != null && options.diskCachePolicy.readEnabled) {
            val snapshot = cachedSnapshot(key)
            if (snapshot != null) {
                try {
                    currentCoroutineContext().ensureActive()
                    checkOwner()
                    data.fromDisk = true
                    data.hasImageData = true
                    return SourceFetchResult(ImageSource(snapshot.data, disk.fileSystem, key, snapshot), null, DataSource.DISK)
                } catch (failure: Throwable) {
                    snapshot.close()
                    throw failure
                }
            }
        }
        val bytes = networkPermits.withPermit { data.fetch(data.image, data.width) }
        currentCoroutineContext().ensureActive()
        checkOwner()
        if (bytes == null || bytes.isEmpty() || bytes.size > maxEncodedBytes) throw ArtworkUnavailable()
        data.hasImageData = true
        if (disk != null && key != null && options.diskCachePolicy.writeEnabled) {
            writeSnapshot(key, bytes)?.let { snapshot ->
                return SourceFetchResult(ImageSource(snapshot.data, disk.fileSystem, key, snapshot), null, DataSource.NETWORK)
            }
        }
        return SourceFetchResult(ImageSource(Buffer().write(bytes), options.fileSystem), null, DataSource.NETWORK)
    }

    private fun cachedSnapshot(key: String): DiskCache.Snapshot? {
        val cache = disk ?: return null
        var snapshot: DiskCache.Snapshot? = null
        try {
            snapshot = cache.openSnapshot(key) ?: return null
            val savedAt = cache.fileSystem.read(snapshot.metadata) { readUtf8().toLongOrNull() }
            val size = cache.fileSystem.metadata(snapshot.data).size
            val age = savedAt?.let { nowEpochMs() - it }
            if (age != null && age in 0 until ArtworkDiskTtlMs && size != null && size in 1..maxEncodedBytes.toLong()) {
                return snapshot
            }
        } catch (_: IOException) {
            // An unavailable/cache-corrupt file must fall back to the provider.
        }
        snapshot?.close()
        try { cache.remove(key) } catch (_: IOException) { }
        return null
    }

    private suspend fun writeSnapshot(key: String, bytes: ByteArray): DiskCache.Snapshot? {
        val cache = disk ?: return null
        var editor: DiskCache.Editor? = null
        var committed = false
        try {
            editor = cache.openEditor(key) ?: return null
            cache.fileSystem.write(editor.metadata) { writeUtf8(nowEpochMs().toString()) }
            cache.fileSystem.write(editor.data) { write(bytes) }
            currentCoroutineContext().ensureActive()
            checkOwner()
            val snapshot = editor.commitAndOpenSnapshot()
            committed = true
            return snapshot
        } catch (_: IOException) {
            return null // Disk capacity/permissions cannot prevent a valid network image displaying.
        } finally {
            if (!committed) try { editor?.abort() } catch (_: IOException) { }
        }
    }

    class Factory(
        private val maxEncodedBytes: Int,
        private val nowEpochMs: () -> Long,
        private val checkOwner: () -> Unit,
    ) : Fetcher.Factory<ArtworkRequest> {
        private val networkPermits = Semaphore(6)
        override fun create(data: ArtworkRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtworkFetcher(data, options, imageLoader.diskCache, maxEncodedBytes, nowEpochMs, checkOwner, networkPermits)
    }
}

internal class ArtworkUnavailable : IOException("Artwork unavailable")
internal const val ArtworkDiskTtlMs = 12L * 60 * 60 * 1000
