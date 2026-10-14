package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.repositories.*

internal fun MediaItem.toPlaybackTarget(): MediaPlaybackTarget =
    MediaPlaybackTarget(
        id = id,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        playbackPositionMs = userData.playbackPositionMs,
        playedPercentage = userData.playedPercentage,
        durationMs = runtimeMs,
    )

/**
 * Resolves the old ArkUI playback-target rules against real episode rows. Series detail uses target -> first unwatched -> first; season detail inserts "episode
 * with partial progress" before the first-unwatched fallback.
 */
internal fun resolveEpisodePlaybackTarget(episodes: List<MediaItem>, target: MediaPlaybackTarget?, preferProgressFallback: Boolean): MediaItem? {
    if (episodes.isEmpty()) return null
    var targetIndex = target?.id?.let { id -> episodes.indexOfFirst { it.id == id } } ?: -1
    if (targetIndex < 0 && target?.episodeNumber != null) {
        targetIndex =
            episodes.indexOfFirst { episode ->
                episode.episodeNumber == target.episodeNumber && (target.seasonNumber == null || episode.seasonNumber == target.seasonNumber)
            }
    }
    val matchedTarget = targetIndex >= 0
    if (targetIndex < 0 && preferProgressFallback) {
        targetIndex =
            episodes.indexOfFirst { episode ->
                val percentage = episodePlaybackPercentage(episode)
                percentage > 0.0 && percentage < 100.0
            }
    }
    if (targetIndex < 0) targetIndex = episodes.indexOfFirst { !it.userData.isPlayed }
    if (targetIndex < 0) targetIndex = 0
    val selected = episodes[targetIndex]
    if (!matchedTarget || target == null) return selected
    val targetPercentage = target.playedPercentage ?: 0.0
    if (target.playbackPositionMs <= 0L && targetPercentage <= 0.0) return selected
    return selected.copy(
        runtimeMs = target.durationMs?.takeIf { it > 0L } ?: selected.runtimeMs,
        userData = selected.userData.copy(playbackPositionMs = target.playbackPositionMs.coerceAtLeast(0L), playedPercentage = target.playedPercentage),
    )
}

internal fun episodePlaybackPercentage(item: MediaItem): Double {
    val explicit = item.userData.playedPercentage?.coerceIn(0.0, 100.0) ?: 0.0
    if (explicit > 0.0) return explicit
    val duration = item.runtimeMs ?: 0L
    return if (item.userData.playbackPositionMs > 0L && duration > 0L) {
        (item.userData.playbackPositionMs.toDouble() * 100.0 / duration.toDouble()).coerceIn(0.0, 100.0)
    } else {
        0.0
    }
}

/** Preserve stable shelf inputs so an in-place home refresh stays local. */
internal fun HomeFeed.mergeUnchangedSections(fresh: HomeFeed): HomeFeed {
    val recommendations = recommendations.takeIf { it == fresh.recommendations } ?: fresh.recommendations
    val resume = resume.takeIf { it == fresh.resume } ?: fresh.resume
    val nextUp = nextUp.takeIf { it == fresh.nextUp } ?: fresh.nextUp
    val latest = latest.takeIf { it == fresh.latest } ?: fresh.latest
    return if (recommendations === this.recommendations && resume === this.resume && nextUp === this.nextUp && latest === this.latest) {
        this
    } else {
        HomeFeed(recommendations = recommendations, resume = resume, nextUp = nextUp, latest = latest)
    }
}

internal fun MediaBrowserState.replaceUserData(id: MediaId, userData: MediaUserData): MediaBrowserState {
    return replaceUserData(id) { userData }
}

internal fun MediaBrowserState.replaceUserData(id: MediaId, transform: (MediaUserData) -> MediaUserData): MediaBrowserState {
    fun List<MediaItem>.replace() = map { item -> if (item.id == id) item.copy(userData = transform(item.userData)) else item }
    fun List<PlaybackHistoryItem>.replaceHistory() = map { item ->
        if (item.media.id == id) item.copy(media = item.media.copy(userData = transform(item.media.userData))) else item
    }
    return copy(
        items = items.replace(),
        favoriteItems = favoriteItems.replace(),
        searchItems = searchItems.replace(),
        detail = detail?.let { item -> if (item.id == id) item.copy(userData = transform(item.userData)) else item },
        selectedSeasonDetail = selectedSeasonDetail?.let { item -> if (item.id == id) item.copy(userData = transform(item.userData)) else item },
        detailPlayTarget = detailPlayTarget?.let { item -> if (item.id == id) item.copy(userData = transform(item.userData)) else item },
        selectedSeasonPlayTarget = selectedSeasonPlayTarget?.let { item -> if (item.id == id) item.copy(userData = transform(item.userData)) else item },
        detailSeasons = detailSeasons.replace(),
        detailEpisodes = detailEpisodes.replace(),
        homeFeed =
            homeFeed.copy(
                recommendations = homeFeed.recommendations.replace(),
                resume = homeFeed.resume.replace(),
                nextUp = homeFeed.nextUp.replace(),
                latest = homeFeed.latest.replace(),
            ),
        homeLatestByCollection = homeLatestByCollection.mapValues { (_, media) -> media.replace() },
        historyItems = historyItems.replaceHistory(),
    )
}

internal fun MediaBrowserState.userDataFor(id: MediaId): MediaUserData? {
    fun List<MediaItem>.find() = firstOrNull { it.id == id }?.userData
    return items.find()
        ?: favoriteItems.find()
        ?: searchItems.find()
        ?: detail?.takeIf { it.id == id }?.userData
        ?: selectedSeasonDetail?.takeIf { it.id == id }?.userData
        ?: detailPlayTarget?.takeIf { it.id == id }?.userData
        ?: selectedSeasonPlayTarget?.takeIf { it.id == id }?.userData
        ?: detailSeasons.find()
        ?: detailEpisodes.find()
        ?: homeFeed.recommendations.find()
        ?: homeFeed.resume.find()
        ?: homeFeed.nextUp.find()
        ?: homeFeed.latest.find()
        ?: homeLatestByCollection.values.asSequence().flatMap { it.asSequence() }.firstOrNull { it.id == id }?.userData
        ?: historyItems.firstOrNull { it.media.id == id }?.media?.userData
}

internal fun MediaBrowserState.containsMedia(id: MediaId): Boolean =
    detail?.id == id || selectedSeasonDetail?.id == id || detailSeasons.any { it.id == id } || detailEpisodes.any { it.id == id }

/** 删除成功后的状态修剪：所有列表里被删条目一并移除，总数收紧。 */
internal fun MediaBrowserState.removeMedia(ids: Set<MediaId>): MediaBrowserState {
    fun List<MediaItem>.prune() = filter { it.id !in ids }
    fun List<PlaybackHistoryItem>.pruneHistory() = filter { it.media.id !in ids }
    return copy(
        items = items.prune(),
        favoriteItems = favoriteItems.prune(),
        searchItems = searchItems.prune(),
        detailEpisodes = detailEpisodes.prune(),
        detailSeasons = detailSeasons.prune(),
        selectedSeasonDetail = selectedSeasonDetail?.takeIf { it.id !in ids },
        detailPlayTarget = detailPlayTarget?.takeIf { it.id !in ids },
        selectedSeasonPlayTarget = selectedSeasonPlayTarget?.takeIf { it.id !in ids },
        personMovies = personMovies.prune(),
        personSeries = personSeries.prune(),
        personEpisodes = personEpisodes.prune(),
        historyItems = historyItems.pruneHistory(),
        totalEstimate = totalEstimate?.let { total -> (total - items.count { it.id in ids }).coerceAtLeast(0) },
        favoriteTotalEstimate = favoriteTotalEstimate?.let { total -> (total - favoriteItems.count { it.id in ids }).coerceAtLeast(0) },
        homeFeed =
            homeFeed.copy(
                recommendations = homeFeed.recommendations.prune(),
                resume = homeFeed.resume.prune(),
                nextUp = homeFeed.nextUp.prune(),
                latest = homeFeed.latest.prune(),
            ),
        homeLatestByCollection = homeLatestByCollection.mapValues { (_, media) -> media.prune() },
    )
}

internal fun MediaSourceInfo.legacyDefaultAudioStreamIndex(): Int? =
    defaultAudioIndex?.let { preferred -> audioStreams.firstOrNull { it.index == preferred }?.index }
        ?: audioStreams.firstOrNull { it.isDefault }?.index
        ?: audioStreams.firstOrNull()?.index

internal fun MediaSourceInfo.legacyDefaultSubtitleStreamIndex(): Int =
    when (defaultSubtitleIndex) {
        -1 -> -1
        else ->
            defaultSubtitleIndex?.let { preferred -> subtitleStreams.firstOrNull { it.index == preferred }?.index }
                ?: subtitleStreams.firstOrNull { it.isDefault }?.index
                ?: -1
    }
