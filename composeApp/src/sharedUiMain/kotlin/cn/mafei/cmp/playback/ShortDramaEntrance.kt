package cn.mafei.cmp

import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType

internal val MediaItem.supportsShortDrama: Boolean
    get() = mediaType in setOf(MediaType.Series, MediaType.Season, MediaType.Episode, MediaType.Movie, MediaType.Video)

internal class ShortDramaEntrance(
    private val collections: () -> List<MediaCollection>,
    private val loadItem: suspend (MediaId) -> MediaItem,
    private val loadTarget: suspend (MediaId, MediaId?) -> MediaItem?,
) {
    suspend fun matches(item: MediaItem, libraryId: String?): Boolean {
        if (!item.supportsShortDrama) return false
        val workId = item.seriesId ?: item.id
        val collection = item.collectionId?.value ?: libraryId ?: resolveCollection(item, workId)
        val library = collections().firstOrNull { it.id.value == collection }
        return library?.name?.contains("短剧") == true
    }

    private suspend fun resolveCollection(item: MediaItem, workId: MediaId): String? {
        val known = collections().mapTo(mutableSetOf()) { it.id.value }
        if (item.id == workId && item.parentId?.value in known) return item.parentId?.value
        var next = workId
        val visited = mutableSetOf<MediaId>()
        while (visited.size < 12 && visited.add(next)) {
            val resource = loadItem(next)
            resource.collectionId?.let { return it.value }
            next = resource.parentId ?: return null
            if (next.value in known) return next.value
        }
        return null
    }

    suspend fun target(item: MediaItem): MediaItem? {
        if (item.mediaType != MediaType.Series && item.mediaType != MediaType.Season) return item
        val series = item.seriesId ?: item.id
        val season = item.id.takeIf { item.mediaType == MediaType.Season }
        return loadTarget(series, season)
    }
}
