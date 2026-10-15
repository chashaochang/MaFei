package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.PhotoInfo
import cn.mafei.cmp.core.serialization.formatIsoInstant
import cn.mafei.cmp.core.serialization.parseIsoInstantToEpochMs
import cn.mafei.cmp.providers.jellyfin.wire.BaseItemDto

internal fun BaseItemDto.toPhotoInfo(fullImageUrl: String): PhotoInfo {
    val file = path.orEmpty().substringAfterLast('/').substringAfterLast('\\').ifBlank { name.orEmpty() }
    fun validDate(raw: String?, epoch: Long?): String? = (raw ?: epoch?.let(::formatIsoInstant))?.trim()?.takeIf {
        it.length >= 10 && (it.take(4).toIntOrNull() ?: 0) > 1 && parseIsoInstantToEpochMs(it.take(10)) != null
    }
    val taken = validDate(premiereDateIso, premiereDate) ?: validDate(dateCreatedIso, dateCreated)
    return PhotoInfo(fileName = file, path = path.orEmpty(),
        format = container?.trim()?.takeIf(String::isNotEmpty)?.lowercase() ?: file.substringAfterLast('.', "").lowercase(),
        dateKey = taken?.take(10) ?: "unknown", dateTaken = taken, dateTakenEpochMs = taken?.let(::parseIsoInstantToEpochMs),
        width = width?.coerceAtLeast(0) ?: 0, height = height?.coerceAtLeast(0) ?: 0,
        fullImage = MediaImage(url = fullImageUrl), canDownload = canDownload == true,
        cameraMake = cameraMake, cameraModel = cameraModel, software = software,
        exposureTime = exposureTime?.takeIf(Double::isFinite), focalLength = focalLength?.takeIf(Double::isFinite),
        orientation = imageOrientation, aperture = aperture?.takeIf(Double::isFinite), shutterSpeed = shutterSpeed?.takeIf(Double::isFinite),
        latitude = latitude?.takeIf(Double::isFinite), longitude = longitude?.takeIf(Double::isFinite), altitude = altitude?.takeIf(Double::isFinite),
        isoSpeedRating = isoSpeedRating?.takeIf(Double::isFinite))
}
