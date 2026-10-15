package cn.mafei.cmp.common.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.toBitmap
import okio.Path
import okio.Path.Companion.toPath

private var artworkDirectory: Path? = null
private val artworkDiskCache by lazy {
    DiskCache.Builder().directory(requireNotNull(artworkDirectory))
        .maxSizeBytes(ArtworkDiskCacheBytes).build()
}

/** The existing thin host provides its real private directory before mounting Compose. */
fun configureOhosArtworkCache(privateDirectory: String) {
    require(privateDirectory.startsWith('/') && privateDirectory.length > 1 && '\u0000' !in privateDirectory)
    artworkDirectory = privateDirectory.toPath() / "mafei-artwork-v1"
}

internal actual fun platformArtworkDiskCache(context: PlatformContext): DiskCache = artworkDiskCache
internal actual fun Image.toArtworkBitmap(): ImageBitmap = toBitmap().asComposeImageBitmap()
