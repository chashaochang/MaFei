package cn.mafei.cmp.common.media

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image
import coil3.PlatformContext
import coil3.disk.DiskCache

internal expect fun platformArtworkDiskCache(context: PlatformContext): DiskCache
internal expect fun Image.toArtworkBitmap(): ImageBitmap
