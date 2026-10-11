package cn.mafei.cmp.feature.photo

import cn.mafei.cmp.core.domain.PhotoInfo
import cn.mafei.cmp.common.core.formatLocalDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/** Legacy PhotoInfoSheet's precision and orientation labels. */
internal fun exifFixed(value: Double, places: Int): String {
    if (!value.isFinite()) return ""
    val scale = 10.0.pow(places)
    val rounded = floor(abs(value) * scale + .5).toLong().toString().padStart(places + 1, '0')
    val unsigned = if (places == 0) rounded else rounded.dropLast(places) + "." + rounded.takeLast(places)
    return (if (value < 0) "-" else "") + unsigned
}

internal fun photoOrientationLabel(raw: String?): String? {
    val rotation = when (raw?.lowercase()) {
        "rotate90", "transpose", "lefttop", "righttop", "5", "6" -> 90
        "rotate180", "bottomright", "3" -> 180
        "rotate270", "transverse", "rightbottom", "leftbottom", "7", "8" -> 270
        else -> 0
    }
    val mirrored = raw?.lowercase() in setOf("mirrorhorizontal", "mirrorvertical", "transpose", "transverse", "topright", "bottomleft", "lefttop", "rightbottom", "2", "4", "5", "7")
    return when {
        raw == null -> null
        mirrored && rotation != 0 -> "$rotation° · 镜像"
        mirrored -> "镜像"
        rotation != 0 -> "$rotation°"
        else -> "正常"
    }
}

internal fun photoDateText(photo: PhotoInfo, localFormat: (Long) -> String = { formatLocalDateTime(it) }): String? =
    photo.dateTakenEpochMs?.let(localFormat)?.takeIf(String::isNotBlank)
        ?: photo.dateTaken?.takeIf(String::isNotBlank) ?: photo.dateKey.takeUnless { it == "unknown" }

internal fun photoExifRows(photo: PhotoInfo): List<Pair<String, String?>> = listOf(
    "日期" to photoDateText(photo),
    "尺寸" to if (photo.width > 0 && photo.height > 0) "${photo.width} × ${photo.height}" else null,
    "文件名" to photo.fileName, "格式" to photo.format.uppercase(), "路径" to photo.path,
    "相机" to listOfNotNull(photo.cameraMake, photo.cameraModel).joinToString(" "), "软件" to photo.software,
    "曝光时间" to photo.exposureTime?.takeIf { it > 0 }?.let {
        if (it < 1) "1/${floor(1 / it + .5).toLong().coerceAtLeast(1)} s" else "${exifFixed(it, if (it < 10) 2 else 0)} s"
    },
    "焦距" to photo.focalLength?.takeIf { it > 0 }?.let { "${exifFixed(it, 1)} mm" },
    "光圈" to photo.aperture?.takeIf { it > 0 }?.let { "f/${exifFixed(it, 1)}" },
    "快门速度" to photo.shutterSpeed?.let { exifFixed(it, 2) },
    "ISO" to photo.isoSpeedRating?.takeIf { it > 0 }?.let { exifFixed(it, 0) },
    "位置" to if (photo.latitude != null && photo.longitude != null) "${exifFixed(photo.latitude!!, 5)}, ${exifFixed(photo.longitude!!, 5)}" +
        (photo.altitude?.let { " · ${exifFixed(it, 1)} m" } ?: "") else null,
    "方向" to photoOrientationLabel(photo.orientation),
).filter { !it.second.isNullOrBlank() }
