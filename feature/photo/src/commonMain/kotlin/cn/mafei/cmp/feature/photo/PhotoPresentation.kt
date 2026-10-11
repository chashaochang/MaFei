package cn.mafei.cmp.feature.photo

import cn.mafei.cmp.core.domain.MediaItem
import kotlin.math.max
import kotlin.math.min

fun photoDateGroups(items: List<MediaItem>): List<Pair<String, List<MediaItem>>> = items
    .groupBy { it.photo?.dateKey ?: "unknown" }.entries
    .sortedWith(compareBy<Map.Entry<String, List<MediaItem>>> { it.key == "unknown" }.thenByDescending { it.key })
    .map { it.key to it.value }

data class PhotoTransform(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f)

/** Bounds use the fitted picture, not the whole viewport's letterbox. */
fun clampPhotoTransform(scale: Float, x: Float, y: Float, viewportWidth: Float, viewportHeight: Float,
    imageWidth: Float, imageHeight: Float): PhotoTransform {
    val zoom = scale.takeIf(Float::isFinite)?.coerceIn(1f, 5f) ?: 1f
    if (zoom == 1f || viewportWidth <= 0 || viewportHeight <= 0) return PhotoTransform(zoom)
    val width = imageWidth.takeIf { it > 0f } ?: viewportWidth
    val height = imageHeight.takeIf { it > 0f } ?: viewportHeight
    val fit = min(viewportWidth / width, viewportHeight / height)
    val boundX = max(0f, (width * fit * zoom - viewportWidth) / 2)
    val boundY = max(0f, (height * fit * zoom - viewportHeight) / 2)
    return PhotoTransform(zoom, x.takeIf(Float::isFinite)?.coerceIn(-boundX, boundX) ?: 0f,
        y.takeIf(Float::isFinite)?.coerceIn(-boundY, boundY) ?: 0f)
}

enum class PhotoSaveResult { Saved, Cancelled, Failed }
interface PhotoSaver {
    val supported: Boolean
    suspend fun save(download: cn.mafei.cmp.core.domain.MediaDownload): PhotoSaveResult
}

object UnsupportedPhotoSaver : PhotoSaver {
    override val supported = false
    override suspend fun save(download: cn.mafei.cmp.core.domain.MediaDownload): PhotoSaveResult = PhotoSaveResult.Failed
}
