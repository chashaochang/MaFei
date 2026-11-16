package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.data.adapter_contract.PlaybackPreviewInfo
import cn.mafei.cmp.providers.jellyfin.wire.TrickplayInfoDto

internal fun <T> interleave(pages: List<List<T>>, limit: Int): List<T> {
    val result = ArrayList<T>(limit)
    var itemIndex = 0
    var appended = true
    while (result.size < limit && appended) {
        appended = false
        pages.forEach { page ->
            if (result.size < limit) {
                page.getOrNull(itemIndex)?.let { item ->
                    result += item
                    appended = true
                }
            }
        }
        itemIndex += 1
    }
    return result
}

internal fun TrickplayInfoDto.toPlaybackPreviewInfo(routeWidth: Int): PlaybackPreviewInfo? {
    val width = width ?: routeWidth
    val height = height ?: return null
    val interval = intervalMs ?: return null
    val count = thumbnailCount ?: return null
    val columns = tileWidth ?: return null
    val rows = tileHeight ?: return null
    if (width <= 0 || height <= 0 || interval <= 0L || count <= 0 || columns <= 0 || rows <= 0) return null
    return PlaybackPreviewInfo(width, height, interval, count, columns, rows)
}
