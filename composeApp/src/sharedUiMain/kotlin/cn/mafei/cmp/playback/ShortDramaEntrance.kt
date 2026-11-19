package cn.mafei.cmp

import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.ShortDramaPreferences

internal val MediaItem.supportsShortDrama: Boolean
    get() = mediaType in setOf(MediaType.Series, MediaType.Season, MediaType.Episode, MediaType.Movie, MediaType.Video)

internal fun shortDramaMode(work: Boolean?, library: Boolean?, libraryName: String?): Boolean =
    work ?: library ?: (libraryName?.contains("短剧") == true)

internal class ShortDramaEntrance(
    private val browser: MediaBrowser,
    private val preferences: ShortDramaPreferences,
    private val scope: String,
    private val loadItem: suspend (MediaId) -> MediaItem,
) {
    suspend fun matches(item: MediaItem, libraryId: String?): Boolean {
        if (!item.supportsShortDrama) return false
        val workId = item.seriesId ?: item.id
        preferences.load(scope, false, workId.value)?.let { return it }
        val collection = item.collectionId?.value ?: libraryId ?: resolveCollection(item, workId)
        val library = browser.state.value.collections.firstOrNull { it.id.value == collection }
        if (collection == null) return false
        val stored = preferences.load(scope, true, collection)
        val short = shortDramaMode(null, stored, library?.name)
        if (stored == null && short) preferences.save(scope, true, collection, true)
        return short
    }

    private suspend fun resolveCollection(item: MediaItem, workId: MediaId): String? {
        val known = browser.state.value.collections.mapTo(mutableSetOf()) { it.id.value }
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

    suspend fun setLibrary(collection: MediaCollection, enabled: Boolean) =
        preferences.save(scope, true, collection.id.value, enabled)

    suspend fun libraryMode(collection: MediaCollection): Boolean =
        shortDramaMode(null, preferences.load(scope, true, collection.id.value), collection.name)

    suspend fun setWork(item: MediaItem, enabled: Boolean?) =
        preferences.save(scope, false, (item.seriesId ?: item.id).value, enabled)

    suspend fun workMode(item: MediaItem): Boolean? =
        preferences.load(scope, false, (item.seriesId ?: item.id).value)

    suspend fun target(item: MediaItem): MediaItem? {
        if (item.mediaType != MediaType.Series && item.mediaType != MediaType.Season) return item
        val series = item.seriesId ?: item.id
        val season = item.id.takeIf { item.mediaType == MediaType.Season }
        return browser.shortDramaTarget(series, season)
    }
}
