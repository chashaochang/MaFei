package cn.mafei.cmp.common.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Image
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.toBitmap
import okio.Path.Companion.toOkioPath

private var artworkDiskCache: DiskCache? = null

// Called by the shared remember factory on the UI thread. All account loaders
// share one disk owner; multiple DiskCache instances must not open the same path.
internal actual fun platformArtworkDiskCache(context: PlatformContext): DiskCache =
    artworkDiskCache ?: DiskCache.Builder()
        .directory(context.applicationContext.cacheDir.toOkioPath() / "mafei-artwork-v1")
        .maxSizeBytes(ArtworkDiskCacheBytes).build().also { artworkDiskCache = it }

internal actual fun Image.toArtworkBitmap(): ImageBitmap = toBitmap().asImageBitmap()
