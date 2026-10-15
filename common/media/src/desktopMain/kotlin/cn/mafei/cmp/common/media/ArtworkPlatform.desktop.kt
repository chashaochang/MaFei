package cn.mafei.cmp.common.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.toBitmap
import okio.Path.Companion.toPath

private val artworkDiskCache by lazy {
    DiskCache.Builder()
        .directory(System.getProperty("user.home").toPath() / "Library" / "Caches" / "cn.xiaobai.mafei" / "artwork-v1")
        .maxSizeBytes(ArtworkDiskCacheBytes).build()
}

internal fun platformArtworkDiskCache(context: PlatformContext): DiskCache = artworkDiskCache
internal fun Image.toArtworkBitmap(): ImageBitmap = toBitmap().asComposeImageBitmap()
