package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemDetail
import cn.mafei.cmp.providers.feiniu.core.FeiniuMediaItem
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayItem

internal fun FeiniuPlayItem.toRecommendationMediaItem(): FeiniuMediaItem = FeiniuMediaItem(
    guid = guid,
    title = title,
    type = if (toFeiniuPlayMediaType() == MediaType.Episode) "Episode" else type,
    poster = stillPath?.takeIf(String::isNotBlank) ?: poster,
    canPlay = canPlay,
    parentGuid = parentGuid,
    parentTitle = parentTitle,
    ancestorGuid = ancestorGuid,
    ancestorName = ancestorName,
    ancestorCategory = ancestorCategory,
    tvTitle = tvTitle,
    watched = watched,
    watchedEpochSeconds = watchedEpochSeconds,
    isFavorite = isFavorite,
    durationSeconds = durationSeconds,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    mediaStream = mediaStream,
)

internal fun FeiniuItemDetail.toRecommendationMediaItem(fallback: FeiniuMediaItem): FeiniuMediaItem =
    FeiniuMediaItem(
        guid = guid?.trim()?.takeIf(String::isNotEmpty) ?: fallback.guid,
        title = title?.takeIf(String::isNotBlank) ?: fallback.title,
        type = type?.takeIf(String::isNotBlank) ?: fallback.type,
        poster = poster?.takeIf(String::isNotBlank)
            ?: posters?.takeIf(String::isNotBlank)
            ?: stillPath?.takeIf(String::isNotBlank)
            ?: fallback.poster,
        canPlay = canPlay ?: fallback.canPlay,
        parentGuid = parentGuid?.takeIf(String::isNotBlank) ?: fallback.parentGuid,
        parentTitle = parentTitle?.takeIf(String::isNotBlank) ?: fallback.parentTitle,
        ancestorGuid = ancestorGuid?.takeIf(String::isNotBlank) ?: fallback.ancestorGuid,
        ancestorName = ancestorName?.takeIf(String::isNotBlank) ?: fallback.ancestorName,
        ancestorCategory = ancestorCategory?.takeIf(String::isNotBlank) ?: fallback.ancestorCategory,
        tvTitle = tvTitle?.takeIf(String::isNotBlank) ?: fallback.tvTitle,
        watched = watched ?: isWatched ?: fallback.watched,
        watchedEpochSeconds = watchedEpochSeconds ?: fallback.watchedEpochSeconds,
        isFavorite = isFavorite ?: fallback.isFavorite,
        overview = overview?.takeIf(String::isNotBlank) ?: fallback.overview,
        status = status?.takeIf(String::isNotBlank) ?: fallback.status,
        voteAverage = voteAverage?.takeIf(String::isNotBlank) ?: fallback.voteAverage,
        durationSeconds = durationSeconds ?: fallback.durationSeconds,
        runtimeMinutes = runtimeMinutes ?: fallback.runtimeMinutes,
        seasonNumber = seasonNumber ?: fallback.seasonNumber,
        episodeNumber = episodeNumber ?: fallback.episodeNumber,
        numberOfSeasons = numberOfSeasons ?: fallback.numberOfSeasons,
        numberOfEpisodes = numberOfEpisodes ?: fallback.numberOfEpisodes,
        localNumberOfSeasons = localNumberOfSeasons ?: fallback.localNumberOfSeasons,
        localNumberOfEpisodes = localNumberOfEpisodes ?: fallback.localNumberOfEpisodes,
        singleChildGuid = singleChildGuid?.takeIf(String::isNotBlank) ?: fallback.singleChildGuid,
        playItemGuid = playItemGuid?.takeIf(String::isNotBlank) ?: fallback.playItemGuid,
        mediaGuid = mediaGuid?.takeIf(String::isNotBlank) ?: fallback.mediaGuid,
        videoGuid = videoGuid?.takeIf(String::isNotBlank) ?: fallback.videoGuid,
        audioGuid = audioGuid?.takeIf(String::isNotBlank) ?: fallback.audioGuid,
        subtitleGuid = subtitleGuid?.takeIf(String::isNotBlank) ?: fallback.subtitleGuid,
        fileName = fileName?.takeIf(String::isNotBlank) ?: fallback.fileName,
        releaseDate = releaseDate?.takeIf(String::isNotBlank) ?: fallback.releaseDate,
        airDate = airDate?.takeIf(String::isNotBlank) ?: fallback.airDate,
        firstAirDate = firstAirDate?.takeIf(String::isNotBlank) ?: fallback.firstAirDate,
        lastAirDate = lastAirDate?.takeIf(String::isNotBlank) ?: fallback.lastAirDate,
        genres = genres.ifEmpty { fallback.genres },
        logos = logos?.takeIf(String::isNotBlank) ?: fallback.logos,
        stillPath = stillPath?.takeIf(String::isNotBlank) ?: fallback.stillPath,
        mediaStream = mediaStream ?: fallback.mediaStream,
    )

internal fun String?.isFeiniuLiveLibrary(): Boolean =
    this?.trim()?.lowercase() in setOf("live", "livetv", "iptv")
