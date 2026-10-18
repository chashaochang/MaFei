package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaUserData

/** Reuse the same entity mutation rules for all documents in the one published model. */
internal fun MediaBrowserState.mapTabDocuments(
    transform: (MediaBrowserState) -> MediaBrowserState,
): Map<String, BrowserTabDocument> = tabDocuments.mapValues { (_, document) ->
    // A document has no nested Tab map. Transforming it cannot recurse into
    // another copy of the account-wide feeds or introduce a second store.
    transform(document.applyTo(MediaBrowserState())).captureTabDocument()
}

internal fun BrowserSeasonDocument.mediaItems(): List<MediaItem> =
    listOfNotNull(series, season, seriesPlayTarget, seasonPlayTarget) + seasons + episodes

internal fun BrowserSeasonDocument.replaceUserData(
    id: MediaId,
    transform: (MediaUserData) -> MediaUserData,
): BrowserSeasonDocument {
    fun MediaItem.replace() = if (this.id == id) copy(userData = transform(userData)) else this
    return copy(
        series = series.replace(),
        season = season?.replace(),
        seasons = seasons.map { it.replace() },
        episodes = episodes.map { it.replace() },
        seriesPlayTarget = seriesPlayTarget?.replace(),
        seasonPlayTarget = seasonPlayTarget?.replace(),
    )
}

internal fun BrowserSeasonDocument.removeMedia(ids: Set<MediaId>): BrowserSeasonDocument? {
    if (series.id in ids || seasonId in ids) return null
    return copy(
        seasons = seasons.filter { it.id !in ids },
        episodes = episodes.filter { it.id !in ids },
        seriesPlayTarget = seriesPlayTarget?.takeIf { it.id !in ids },
        seasonPlayTarget = seasonPlayTarget?.takeIf { it.id !in ids },
    )
}

internal fun MediaBrowserState.replacePersonFavorite(id: MediaId, favorite: Boolean): MediaBrowserState = copy(
    person = person?.let { if (it.id == id) it.copy(isFavorite = favorite) else it },
    tabDocuments = mapTabDocuments { it.replacePersonFavorite(id, favorite) },
)
