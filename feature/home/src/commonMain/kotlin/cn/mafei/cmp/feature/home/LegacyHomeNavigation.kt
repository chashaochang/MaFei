package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType

/**
 * Target carried by the legacy ArkUI home page when it opened VideoDetailPage.
 *
 * This is a direct translation of `HomeTab.openHeroDetails`: a recommended
 * episode opens its owning series, while a movie/series opens itself. The
 * media-name text is route context rendered in the detail metadata; it is not
 * inferred from whichever library happens to be selected later.
 */
data class LegacyHomeDetailTarget(
    val mediaId: MediaId,
    val mediaName: String?,
)

fun MediaItem.legacyHomeHeroDetailTarget(): LegacyHomeDetailTarget {
    val seriesTarget = mediaType == MediaType.Episode && seriesId != null
    val targetId = if (seriesTarget) checkNotNull(seriesId) else id
    val mediaName = when (mediaType) {
        MediaType.Movie -> "电影"
        MediaType.Series, MediaType.Season, MediaType.Episode -> "电视节目"
        else -> null
    }
    return LegacyHomeDetailTarget(targetId, mediaName)
}

/**
 * Direct translation of `HomeViewModel.mapHomeFeedItem`: movies use the fixed
 * type label, other latest items keep the library name and fall back to the
 * legacy aggregate label when no library context is available.
 */
fun MediaItem.legacyHomeLatestMediaName(collections: List<MediaCollection>): String =
    if (mediaType == MediaType.Movie) {
        "电影"
    } else {
        collectionId?.let { id -> collections.firstOrNull { it.id == id }?.name }
            ?.takeIf { it.isNotBlank() }
            ?: "最近添加"
    }
