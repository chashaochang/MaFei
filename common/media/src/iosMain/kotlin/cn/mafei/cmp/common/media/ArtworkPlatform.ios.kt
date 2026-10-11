@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.common.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.toBitmap
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private val artworkDiskCache by lazy {
    val directory = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
    DiskCache.Builder().directory(directory.toPath() / "mafei-artwork-v1")
        .maxSizeBytes(ArtworkDiskCacheBytes).build()
}

internal actual fun platformArtworkDiskCache(context: PlatformContext): DiskCache = artworkDiskCache
internal actual fun Image.toArtworkBitmap(): ImageBitmap = toBitmap().asComposeImageBitmap()
