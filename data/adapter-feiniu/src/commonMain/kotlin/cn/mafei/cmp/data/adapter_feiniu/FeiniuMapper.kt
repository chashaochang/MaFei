package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.AuthFailureKind
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.DomainError
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaExternalUrl
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaPerson
import cn.mafei.cmp.core.domain.MediaPlaybackTarget
import cn.mafei.cmp.core.domain.MediaSourceInfo
import cn.mafei.cmp.core.domain.MediaStreamInfo
import cn.mafei.cmp.core.domain.MediaStreamKind
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaUserData
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.NetworkUnreachableError
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ProtocolDecodingError
import cn.mafei.cmp.core.domain.ServerError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.domain.TimeoutError
import cn.mafei.cmp.core.domain.PersonKind
import cn.mafei.cmp.core.domain.PersonRef
import cn.mafei.cmp.providers.feiniu.core.FeiniuAuthenticationError
import cn.mafei.cmp.providers.feiniu.core.FeiniuAuthenticationExpiredError
import cn.mafei.cmp.providers.feiniu.core.FeiniuBusinessError
import cn.mafei.cmp.providers.feiniu.core.FeiniuConnectionError
import cn.mafei.cmp.providers.feiniu.core.FeiniuConnectionKind
import cn.mafei.cmp.providers.feiniu.core.FeiniuDecodeError
import cn.mafei.cmp.providers.feiniu.core.FeiniuError
import cn.mafei.cmp.providers.feiniu.core.FeiniuInvalidAddressError
import cn.mafei.cmp.providers.feiniu.core.FeiniuMediaItem
import cn.mafei.cmp.providers.feiniu.core.FeiniuMediaLibraryEntry
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemDetail
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemFile
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemStream
import cn.mafei.cmp.providers.feiniu.core.FeiniuPersonCredit
import cn.mafei.cmp.providers.feiniu.core.FeiniuPersonDetail
import cn.mafei.cmp.providers.feiniu.core.FeiniuPersonItem
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayInfoData
import cn.mafei.cmp.providers.feiniu.core.FeiniuOfficialStreamData
import cn.mafei.cmp.providers.feiniu.core.FeiniuRedirectRejectedError
import cn.mafei.cmp.providers.feiniu.core.FeiniuStreamFileInfo
import cn.mafei.cmp.providers.feiniu.core.FeiniuStreamListData
import cn.mafei.cmp.providers.feiniu.core.FeiniuSubtitleStream
import cn.mafei.cmp.providers.feiniu.core.FeiniuUnsupportedCapabilityError
import cn.mafei.cmp.providers.feiniu.core.FeiniuAudioStream
import cn.mafei.cmp.providers.feiniu.core.FeiniuVideoStream
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Feiniu-side contract mappings (04C). Pure functions of the frozen wire
 * shapes in providers/feiniu — no wire DTO escapes into `data:adapter-contract`
 * types half-translated. Legacy semantics this follows deliberately:
 * detail `posters`/`backdrops` strings are image paths passed through
 * verbatim (FeiniuVideoCatalogProvider.ets:744), and unknown item types
 * become [MediaType.Unknown] instead of failing the page.
 */
internal fun String?.toFeiniuMediaType(): MediaType = when (this?.trim()?.lowercase()) {
    "movie" -> MediaType.Movie
    "tv", "series" -> MediaType.Series
    "directory", "folder" -> MediaType.Folder
    "video" -> MediaType.Video
    "season" -> MediaType.Season
    "episode" -> MediaType.Episode
    // LiveChannel and unknown future types: MediaType has no live entry.
    else -> MediaType.Unknown
}

/** Legacy `playItemKind`: an episode number wins over an unreliable `type`. */
internal fun cn.mafei.cmp.providers.feiniu.core.FeiniuPlayItem.toFeiniuPlayMediaType(): MediaType =
    if ((episodeNumber ?: 0) > 0) MediaType.Episode else type.toFeiniuMediaType()

internal fun MediaType.toLibraryMediaTypes(): Set<MediaType> = when (this) {
    MediaType.Unknown -> emptySet()
    else -> setOf(this)
}

/**
 * Maps the two Feiniu hierarchy counters using the same precedence as the
 * legacy ArkUI catalog mapper.  Series and seasons are episode-bearing cards;
 * only a generic folder may fall back to a season count.
 */
private fun feiniuChildCount(
    mediaType: MediaType,
    localNumberOfEpisodes: Int?,
    numberOfEpisodes: Int?,
    localNumberOfSeasons: Int?,
    numberOfSeasons: Int?,
): Int? = when (mediaType) {
    MediaType.Series, MediaType.Season -> listOf(
        localNumberOfEpisodes,
        numberOfEpisodes,
    ).firstOrNull { (it ?: 0) > 0 }
    else -> listOf(
        localNumberOfEpisodes,
        numberOfEpisodes,
        localNumberOfSeasons,
        numberOfSeasons,
    ).firstOrNull { (it ?: 0) > 0 }
}

/** Single-category request mapping; combos cannot express on this endpoint. */
internal fun MediaType.toFeiniuCategory(): String = when (this) {
    MediaType.Movie -> "movie"
    MediaType.Series -> "tv"
    MediaType.Folder -> "directory"
    MediaType.Video -> "video"
    else -> throw ClientConfigError("Media type $this cannot be mapped to a Feiniu category filter")
}

/** Int-tagged genres and string decade tags have no wire literal evidence: refuse instead of guessing. */
internal fun throwUnsupportedFeiniuFilter(filter: String): Nothing =
    throw ClientConfigError("Feiniu '$filter' filter has no wire-format evidence; refusing instead of guessing")

/**
 * Parses Feiniu release dates (`yyyy-MM-dd...` prefix, ISO-like). The wire
 * type is an untyped string; anything not matching the date prefix maps to
 * null (never to a fabricated epoch).
 */
internal fun feiniuDateToEpochMsAndYear(value: String?): Pair<Long, Int>? {
    if (value.isNullOrBlank()) return null
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(value.trim()) ?: return null
    val year = match.groupValues[1].toInt()
    val month = match.groupValues[2].toInt()
    val day = match.groupValues[3].toInt()
    if (month !in 1..12 || day !in 1..31) return null
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val daysInMonth = when (month) {
        2 -> if (leap) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    if (day > daysInMonth) return null
    // Howard Hinnant's days_from_civil — pure Kotlin, no platform date APIs.
    val yy = if (month <= 2) year - 1 else year
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era * 146_097L + doe - 719_468L
    return (days * 86_400_000L) to year
}

internal fun FeiniuMediaLibraryEntry.toMediaCollection(
    serverId: ServerId,
    imageUrl: (imagePath: String) -> String,
): MediaCollection? {
    if (guid.isBlank()) return null
    val mediaType = category.toFeiniuMediaType()
    return MediaCollection(
        id = CollectionId(guid),
        serverId = serverId,
        provider = ProviderKind.Feiniu,
        name = title,
        mediaTypes = mediaType.toLibraryMediaTypes(),
        primaryImage = posters.firstOrNull { it.isNotBlank() }?.let { path ->
            cn.mafei.cmp.core.domain.MediaImage(ImageKind.Primary, imageUrl(path))
        },
    )
}

/**
 * Maps one list item; guid-less entries are dropped. `collectionId` is the
 * query context the item was fetched under, not a wire field.
 */
internal fun FeiniuMediaItem.toMediaItem(
    serverId: ServerId,
    imageUrl: (imagePath: String) -> String,
    collectionId: CollectionId? = null,
    genreNames: Map<Int, String> = emptyMap(),
): MediaItem? {
    if (guid.isBlank()) return null
    val mediaType = type.toFeiniuMediaType()
    val stream = mediaStream
    val runtimeMs = (durationSeconds ?: 0L).takeIf { it > 0 }?.let { it * 1000L }
        ?: runtimeMinutes?.takeIf { it > 0 }?.let { it * 60_000L }
    val premiere = listOf(releaseDate, airDate, firstAirDate, lastAirDate)
        .firstNotNullOfOrNull(::feiniuDateToEpochMsAndYear)
    val positionSeconds = maxOf(
        (epochSeconds ?: 0L).toDouble(),
        stream?.currentTimeSeconds?.takeIf(Double::isFinite) ?: 0.0,
    ).coerceAtLeast(0.0)
    val positionMs = (positionSeconds * 1000.0).toLong()
    val explicitPercentage = stream?.progress?.takeIf(Double::isFinite)?.coerceIn(0.0, 100.0)
    val isPlayed = (watched ?: 0) > 0
    val playedPercentage = when {
        explicitPercentage != null && explicitPercentage > 0.0 -> explicitPercentage
        positionMs > 0L && runtimeMs != null ->
            (positionMs.toDouble() * 100.0 / runtimeMs.toDouble()).coerceIn(0.0, 100.0)
        isPlayed -> 100.0
        else -> explicitPercentage
    }
    val primaryPath = stillPath?.takeIf(String::isNotBlank) ?: poster?.takeIf(String::isNotBlank)
    val seriesId = when (mediaType) {
        MediaType.Series -> guid
        MediaType.Season -> parentGuid?.trim()?.takeIf { it.isNotEmpty() && it != ancestorGuid?.trim() }
        else -> null
    }
    val seasonId = if (mediaType == MediaType.Episode) parentGuid?.trim()?.takeIf(String::isNotEmpty) else null
    val seriesTitle = tvTitle?.trim()?.takeIf(String::isNotEmpty)
        ?: parentTitle?.trim()?.takeIf(String::isNotEmpty)
    return MediaItem(
        id = MediaId(guid),
        serverId = serverId,
        provider = ProviderKind.Feiniu,
        title = title.takeIf(String::isNotBlank) ?: fileName.orEmpty(),
        mediaType = mediaType,
        overview = overview,
        collectionId = collectionId,
        parentId = parentGuid?.takeIf { it.isNotBlank() }?.let(::MediaId),
        seriesId = seriesId?.let(::MediaId),
        seriesTitle = seriesTitle,
        seasonId = seasonId?.let(::MediaId),
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        // The legacy Feiniu mapper exposes an episode count for TV/season
        // cards.  Do not let a series fall through to number_of_seasons: the
        // home card subtitle is expected to say “共 N 集”, not “共 N 季”.
        childCount = feiniuChildCount(
            mediaType = mediaType,
            localNumberOfEpisodes = localNumberOfEpisodes,
            numberOfEpisodes = numberOfEpisodes,
            localNumberOfSeasons = localNumberOfSeasons,
            numberOfSeasons = numberOfSeasons,
        ),
        year = premiere?.second,
        premiereEpochMs = premiere?.first,
        runtimeMs = runtimeMs,
        communityRating = voteAverage.feiniuRating(),
        genres = genres.mapNotNull { genreNames[it]?.trim()?.takeIf(String::isNotEmpty) }.distinct(),
        tags = feiniuMediaTags(stream),
        people = emptyList(), // wire carries no people list in P0
        images = buildList {
            primaryPath?.let { path ->
                add(cn.mafei.cmp.core.domain.MediaImage(ImageKind.Primary, imageUrl(path)))
                add(cn.mafei.cmp.core.domain.MediaImage(ImageKind.Backdrop, imageUrl(path)))
            }
            logos?.takeIf(String::isNotBlank)?.let { path ->
                add(cn.mafei.cmp.core.domain.MediaImage(ImageKind.Logo, imageUrl(path)))
            }
        },
        userData = MediaUserData(
            isFavorite = (isFavorite ?: 0) > 0,
            isPlayed = isPlayed,
            playCount = null, // no wire field
            playbackPositionMs = positionMs,
            playedPercentage = playedPercentage,
            lastPlayedEpochMs = watchedEpochSeconds?.takeIf { it > 0 }?.let { it * 1000L },
        ),
        canPlay = (canPlay ?: 0) > 0,
    )
}

/** Mechanical translation of FeiniuVideoCatalogMapper.mediaTags(). */
private fun feiniuMediaTags(stream: cn.mafei.cmp.providers.feiniu.core.FeiniuItemStream?): List<String> = buildList {
    stream?.resolutions.orEmpty().forEach { value ->
        val normalized = value.trim().uppercase()
        if (normalized.isNotEmpty() && normalized !in this) add(normalized)
    }
    (stream?.audioTypes.orEmpty() + stream?.colorRangeTypes.orEmpty()).forEach { value ->
        value.trim().takeIf { it.isNotEmpty() && it !in this }?.let(::add)
    }
}

internal fun cn.mafei.cmp.providers.feiniu.core.FeiniuPlayItem.toMediaItem(
    serverId: ServerId,
    imageUrl: (imagePath: String) -> String,
): MediaItem? {
    // `/play/list` uses top-level `ts` as the saved playback position in
    // seconds. It is not a creation timestamp. Real fnOS responses often omit
    // media_stream entirely, which previously turned every history entry into
    // a zero-progress item and made the whole Continue Watching shelf vanish.
    // This mirrors legacy FeiniuVideoCatalogMapper.playUserState().
    val positionSeconds = maxOf(
        epochSeconds?.toDouble() ?: 0.0,
        mediaStream?.currentTimeSeconds?.takeIf(Double::isFinite) ?: 0.0,
    ).coerceAtLeast(0.0)
    val duration = durationSeconds?.takeIf { it > 0L }?.toDouble()
    val explicitPercentage = mediaStream?.progress?.takeIf(Double::isFinite)?.coerceIn(0.0, 100.0)
    val percentage = when {
        explicitPercentage != null && explicitPercentage > 0.0 -> explicitPercentage
        positionSeconds > 0.0 && duration != null ->
            (positionSeconds * 100.0 / duration).coerceIn(0.0, 100.0)
        else -> explicitPercentage
    }
    val resolvedStream = (mediaStream ?: FeiniuItemStream()).copy(
        progress = percentage,
        currentTimeSeconds = positionSeconds.takeIf { it > 0.0 },
    )
    return FeiniuMediaItem(
        guid = guid,
        title = title,
        type = if (toFeiniuPlayMediaType() == MediaType.Episode) "Episode" else type,
        poster = stillPath?.takeIf { it.isNotBlank() } ?: poster,
        canPlay = canPlay ?: 1,
        parentGuid = parentGuid,
        parentTitle = parentTitle,
        ancestorGuid = ancestorGuid,
        ancestorName = ancestorName,
        ancestorCategory = ancestorCategory,
        tvTitle = tvTitle,
        watched = watched,
        watchedEpochSeconds = watchedEpochSeconds,
        isFavorite = isFavorite,
        // `ts` has already been consumed as playback position above.
        epochSeconds = null,
        durationSeconds = durationSeconds,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        mediaStream = resolvedStream,
    ).toMediaItem(serverId, imageUrl)
}

internal data class FeiniuDetailContext(
    val seriesId: String? = null,
    val seasonId: String? = null,
    val seriesTitle: String? = null,
    val seasonTitle: String? = null,
    val primaryImagePath: String? = null,
    val backdropImagePath: String? = null,
    val logoImagePath: String? = null,
)

/** Mechanical translation of the legacy Feiniu detail mapper. */
internal fun FeiniuItemDetail.toMediaItem(
    serverId: ServerId,
    imageUrl: (imagePath: String) -> String,
    people: List<FeiniuPersonCredit> = emptyList(),
    streams: FeiniuStreamListData = FeiniuStreamListData(),
    itemFiles: List<FeiniuItemFile> = emptyList(),
    playInfo: FeiniuPlayInfoData? = null,
    genreNames: Map<Int, String> = emptyMap(),
    context: FeiniuDetailContext = FeiniuDetailContext(),
): MediaItem? {
    val id = guid?.takeIf { it.isNotBlank() } ?: return null
    val mediaType = type.toFeiniuMediaType()
    val premiere = listOf(releaseDate, airDate, firstAirDate, lastAirDate)
        .firstNotNullOfOrNull(::feiniuDateToEpochMsAndYear)
    val durationMs = feiniuDurationMs(durationSeconds?.toDouble(), runtimeMinutes)
        .coerceAtLeast(feiniuDurationMs(playInfo?.item?.durationSeconds?.toDouble(), null))
        .takeIf { it > 0L }
    val positionSeconds = maxOf(
        playbackPositionSeconds?.toDouble() ?: 0.0,
        playInfo?.epochSeconds?.toDouble() ?: 0.0,
        mediaStream?.currentTimeSeconds?.takeIf(Double::isFinite) ?: 0.0,
    ).coerceAtLeast(0.0)
    val positionMs = (positionSeconds * 1000.0).toLong()
    val explicitPercentage = mediaStream?.progress?.takeIf(Double::isFinite)?.coerceIn(0.0, 100.0)
    val isPlayed = maxOf(isWatched ?: 0, watched ?: 0) > 0
    val playedPercentage = when {
        explicitPercentage != null && explicitPercentage > 0.0 -> explicitPercentage
        durationMs != null && durationMs > 0L && positionMs > 0L ->
            (positionMs.toDouble() * 100.0 / durationMs.toDouble()).coerceIn(0.0, 100.0)
        isPlayed -> 100.0
        else -> explicitPercentage
    }
    val primaryPath = stillPath?.takeIf { it.isNotBlank() }
        ?: poster?.takeIf { it.isNotBlank() }
        ?: posters?.takeIf { it.isNotBlank() }
        ?: context.primaryImagePath?.takeIf { it.isNotBlank() }
        ?: context.backdropImagePath?.takeIf { it.isNotBlank() }
    val backdropPath = backdrops?.takeIf { it.isNotBlank() }
        ?: context.backdropImagePath?.takeIf { it.isNotBlank() }
        ?: primaryPath
    val logoPath = logos?.takeIf { it.isNotBlank() }
        ?: context.logoImagePath?.takeIf { it.isNotBlank() }
    val mappedSources = feiniuMediaSources(this, streams, itemFiles, playInfo)
    return MediaItem(
        id = MediaId(id),
        serverId = serverId,
        provider = ProviderKind.Feiniu,
        title = title?.takeIf { it.isNotBlank() } ?: fileName.orEmpty(),
        mediaType = mediaType,
        originalTitle = originalTitle?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() },
        overview = overview,
        parentId = parentGuid?.trim()?.takeIf { it.isNotEmpty() }?.let(::MediaId),
        seriesId = context.seriesId?.trim()?.takeIf { it.isNotEmpty() }?.let(::MediaId),
        seriesTitle = context.seriesTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: tvTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: parentTitle?.trim()?.takeIf { it.isNotEmpty() },
        seasonId = context.seasonId?.trim()?.takeIf { it.isNotEmpty() }?.let(::MediaId),
        seasonTitle = context.seasonTitle?.trim()?.takeIf { it.isNotEmpty() },
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        childCount = feiniuChildCount(
            mediaType = mediaType,
            localNumberOfEpisodes = localNumberOfEpisodes,
            numberOfEpisodes = numberOfEpisodes,
            localNumberOfSeasons = localNumberOfSeasons,
            numberOfSeasons = numberOfSeasons,
        ),
        year = premiere?.second,
        premiereEpochMs = premiere?.first,
        runtimeMs = durationMs,
        communityRating = voteAverage.feiniuRating(),
        officialRating = contentRatings?.takeIf { it.isNotBlank() },
        genres = genres.mapNotNull { genreNames[it]?.trim()?.takeIf(String::isNotEmpty) }.distinct(),
        tags = buildList {
            addAll(feiniuMediaTags(mediaStream))
            productionCountries.map(String::trim).filter(String::isNotEmpty).forEach { if (it !in this) add(it) }
        },
        people = people.toPersonRefs(imageUrl),
        images = buildList {
            primaryPath?.let { add(MediaImage(ImageKind.Primary, imageUrl(it))) }
            backdropPath?.let { add(MediaImage(ImageKind.Backdrop, imageUrl(it))) }
            logoPath?.let { add(MediaImage(ImageKind.Logo, imageUrl(it))) }
        },
        userData = MediaUserData(
            isFavorite = (isFavorite ?: 0) > 0,
            isPlayed = isPlayed,
            playbackPositionMs = positionMs,
            playedPercentage = playedPercentage,
            lastPlayedEpochMs = watchedEpochSeconds?.takeIf { it > 0L }?.let { it * 1000L },
        ),
        canPlay = (canPlay ?: 1) > 0 && playError.isNullOrBlank(),
        mediaSources = mappedSources,
        externalUrls = imdbId?.trim()?.takeIf { it.isNotEmpty() }?.let { imdb ->
            listOf(MediaExternalUrl("IMDb", "https://www.imdb.com/title/$imdb"))
        }.orEmpty(),
        playbackTarget = playInfo.toMediaPlaybackTarget(),
    )
}

/** Exact domain translation of legacy `FeiniuVideoCatalogMapper.playbackTarget`. */
private fun FeiniuPlayInfoData?.toMediaPlaybackTarget(): MediaPlaybackTarget? {
    val info = this ?: return null
    val id = info.guid?.trim()?.takeIf { it.isNotEmpty() }?.let(::MediaId)
    val season = info.item?.seasonNumber?.takeIf { it > 0 }
    val episode = info.item?.episodeNumber?.takeIf { it > 0 }
    if (id == null && season == null && episode == null) return null
    val positionMs = (info.epochSeconds ?: 0L).coerceAtLeast(0L) * 1_000L
    val durationMs = info.item?.durationSeconds?.takeIf { it > 0L }?.times(1_000L)
    val percentage = durationMs?.takeIf { it > 0L && positionMs > 0L }
        ?.let { (positionMs.toDouble() * 100.0 / it.toDouble()).coerceIn(0.0, 100.0) }
    return MediaPlaybackTarget(
        id = id,
        seasonNumber = season,
        episodeNumber = episode,
        playbackPositionMs = positionMs,
        playedPercentage = percentage,
        durationMs = durationMs,
    )
}

internal fun FeiniuPersonDetail.toMediaPerson(
    serverId: ServerId,
    imageUrl: (imagePath: String) -> String,
): MediaPerson? {
    val id = guid?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val displayName = name.orEmpty()
    return MediaPerson(
        id = MediaId(id),
        serverId = serverId,
        provider = ProviderKind.Feiniu,
        name = displayName,
        sortName = originalName?.takeIf { it.isNotBlank() } ?: displayName,
        overview = biography,
        image = profile?.trim()?.takeIf { it.isNotEmpty() }?.let { MediaImage(ImageKind.Primary, imageUrl(it)) },
        isFavorite = (isFavorite ?: 0) > 0,
    )
}

internal fun FeiniuPersonItem.toMediaItem(
    serverId: ServerId,
    imageUrl: (imagePath: String) -> String,
): MediaItem? {
    val resolvedId = itemGuid?.trim()?.takeIf { it.isNotEmpty() }
        ?: guid?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    return FeiniuMediaItem(
        guid = resolvedId,
        title = title.orEmpty(),
        type = type,
        poster = poster,
        canPlay = 1,
        watched = watched,
        isFavorite = isFavorite,
        voteAverage = voteAverage,
        releaseDate = releaseDate ?: firstAirDate,
        firstAirDate = firstAirDate,
        mediaStream = mediaStream,
    ).toMediaItem(serverId, imageUrl)
}

private fun List<FeiniuPersonCredit>.toPersonRefs(
    imageUrl: (imagePath: String) -> String,
): List<PersonRef> = sortedBy { it.order ?: Int.MAX_VALUE }.mapNotNull { person ->
    val id = listOf(person.personGuid, person.trimId, person.imdbId, person.name)
        .firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
        ?: return@mapNotNull null
    val name = person.name?.trim()?.takeIf(String::isNotEmpty)
        ?: person.originalName?.trim().orEmpty()
    PersonRef(
        id = MediaId(id),
        name = name,
        role = listOf(person.role, person.job, person.department, person.knownForDepartment)
            .firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) },
        personType = feiniuPersonKind(person.job ?: person.department ?: person.knownForDepartment),
        image = person.profilePath?.trim()?.takeIf(String::isNotEmpty)
            ?.let { MediaImage(ImageKind.Primary, imageUrl(it)) },
    )
}

private fun feiniuPersonKind(value: String?): PersonKind = when (value?.trim()?.lowercase()) {
    "actor" -> PersonKind.Actor
    "director" -> PersonKind.Director
    "screenplay", "writer" -> PersonKind.Writer
    "producer" -> PersonKind.Producer
    "composer" -> PersonKind.Composer
    "gueststar", "guest star" -> PersonKind.GuestStar
    else -> PersonKind.Unknown
}

/** `/stream/list` is authoritative; `/media/itemfile` only fills missing file fields. */
internal fun mergeFeiniuItemFiles(
    listed: FeiniuStreamListData,
    itemFiles: List<FeiniuItemFile>,
): FeiniuStreamListData {
    val files = listed.files.toMutableList()
    itemFiles.forEach { item ->
        val mediaGuid = (item.mediaGuid ?: item.itemGuid)?.trim().orEmpty()
        if (mediaGuid.isEmpty()) return@forEach
        val index = files.indexOfFirst { it.guid?.trim() == mediaGuid }
        if (index >= 0) {
            val current = files[index]
            files[index] = current.copy(
                fileName = current.fileName.nonBlankOr(item.fileName),
                path = current.path.nonBlankOr(item.filePath),
                sizeBytes = current.sizeBytes.nonZeroOr(item.sizeBytes).nonZeroOr(item.fileSizeBytes),
                createTime = current.createTime.nonZeroOr(item.createTime),
                updateTime = current.updateTime.nonZeroOr(item.updateTime),
                fileBirthTime = current.fileBirthTime.nonZeroOr(item.fileBirthTime),
                timestamp = current.timestamp.nonZeroOr(item.timestamp),
                durationSeconds = current.durationSeconds.nonZeroOr(item.durationSeconds),
            )
        } else {
            files += FeiniuStreamFileInfo(
                guid = mediaGuid,
                fileName = item.fileName,
                path = item.filePath,
                sizeBytes = item.sizeBytes.nonZeroOr(item.fileSizeBytes),
                createTime = item.createTime,
                updateTime = item.updateTime,
                fileBirthTime = item.fileBirthTime,
                timestamp = item.timestamp,
                durationSeconds = item.durationSeconds,
            )
        }
    }
    return listed.copy(files = files)
}

/** Legacy stable ordering: sort_num first, then display name; preferred media is promoted to index zero. */
internal fun preferFeiniuMediaFile(data: FeiniuStreamListData, preferredMediaGuid: String?): FeiniuStreamListData {
    val files = data.files.sortedWith { left, right ->
        val leftSort = left.sortNumber
        val rightSort = right.sortNumber
        when {
            leftSort != null && rightSort != null && leftSort != rightSort -> leftSort.compareTo(rightSort)
            leftSort != null && rightSort == null -> -1
            leftSort == null && rightSort != null -> 1
            else -> (left.fileName ?: left.path ?: left.guid.orEmpty())
                .compareTo(right.fileName ?: right.path ?: right.guid.orEmpty())
        }
    }.toMutableList()
    val preferred = preferredMediaGuid?.trim().orEmpty()
    if (preferred.isNotEmpty()) {
        val index = files.indexOfFirst { it.guid?.trim() == preferred }
        if (index > 0) files.add(0, files.removeAt(index))
    }
    return data.copy(files = files)
}

internal fun feiniuDetailMediaGuids(
    detail: FeiniuItemDetail,
    data: FeiniuStreamListData,
    preferredMediaGuid: String?,
): List<String> = buildList {
    fun append(value: String?) {
        value?.trim()?.takeIf { it.isNotEmpty() && it !in this }?.let(::add)
    }
    data.files.forEach { append(it.guid) }
    data.videoStreams.forEach { append(it.mediaGuid) }
    data.audioStreams.forEach { append(it.mediaGuid) }
    data.subtitleStreams.forEach { append(it.mediaGuid) }
    append(preferredMediaGuid)
    append(detail.mediaGuid)
}

/** Official `/stream` data wins field-by-field and preserves missing values from the list endpoints. */
internal fun mergeFeiniuOfficialStreams(
    detail: FeiniuItemDetail? = null,
    listed: FeiniuStreamListData,
    entries: List<Pair<String, FeiniuOfficialStreamData>>,
): FeiniuStreamListData {
    val files = listed.files.toMutableList()
    var videos = listed.videoStreams
    var audios = listed.audioStreams
    var subtitles = listed.subtitleStreams
    entries.forEach { (mediaGuid, data) ->
        val qualities = data.directLinkQualities.ifEmpty { data.qualities }
        val officialFile = data.fileStream?.let { file ->
            file.copy(
                guid = file.guid?.trim()?.takeIf(String::isNotEmpty) ?: mediaGuid,
                directLinkQualities = qualities.ifEmpty { file.directLinkQualities },
            )
        }
        if (officialFile != null) {
            val index = files.indexOfFirst { it.guid?.trim() == mediaGuid }
            if (index >= 0) files[index] = mergeFeiniuFile(files[index], officialFile) else files += officialFile
        } else if (files.none { it.guid?.trim() == mediaGuid }) {
            files += FeiniuStreamFileInfo(
                guid = mediaGuid,
                fileName = detail?.fileName,
                path = "",
                sizeBytes = 0,
                directLinkQualities = qualities,
            )
        }
        data.videoStream?.let { stream ->
            videos = mergeFeiniuVideoStream(
                videos,
                stream.copy(mediaGuid = stream.mediaGuid?.trim()?.takeIf(String::isNotEmpty) ?: mediaGuid),
            )
        }
        data.audioStreams.forEach { stream ->
            audios = mergeFeiniuAudioStream(
                audios,
                stream.copy(mediaGuid = stream.mediaGuid?.trim()?.takeIf(String::isNotEmpty) ?: mediaGuid),
            )
        }
        data.subtitleStreams.forEach { stream ->
            subtitles = mergeFeiniuSubtitleStream(
                subtitles,
                stream.copy(mediaGuid = stream.mediaGuid?.trim()?.takeIf(String::isNotEmpty) ?: mediaGuid),
            )
        }
    }
    return FeiniuStreamListData(files, videos, audios, subtitles)
}

private fun mergeFeiniuFile(current: FeiniuStreamFileInfo, replacement: FeiniuStreamFileInfo) = replacement.copy(
    guid = replacement.guid.nonBlankOr(current.guid),
    path = replacement.path.nonBlankOr(current.path),
    fileName = replacement.fileName.nonBlankOr(current.fileName),
    sizeBytes = replacement.sizeBytes.nonZeroOr(current.sizeBytes),
    timestamp = replacement.timestamp.nonZeroOr(current.timestamp),
    type = replacement.type ?: current.type,
    sortNumber = replacement.sortNumber ?: current.sortNumber,
    canPlay = replacement.canPlay ?: current.canPlay,
    playError = replacement.playError.nonBlankOr(current.playError),
    createTime = replacement.createTime.nonZeroOr(current.createTime),
    updateTime = replacement.updateTime.nonZeroOr(current.updateTime),
    fileBirthTime = replacement.fileBirthTime.nonZeroOr(current.fileBirthTime),
    progressThumbHashDir = replacement.progressThumbHashDir.nonBlankOr(current.progressThumbHashDir),
    durationSeconds = replacement.durationSeconds.nonZeroOr(current.durationSeconds),
    directLinkQualities = replacement.directLinkQualities.ifEmpty { current.directLinkQualities },
)

private fun mergeFeiniuVideoStream(values: List<FeiniuVideoStream>, replacement: FeiniuVideoStream): List<FeiniuVideoStream> {
    val result = values.toMutableList()
    val index = result.indexOfFirst { value ->
        val replacementGuid = replacement.guid?.trim().orEmpty()
        (replacementGuid.isNotEmpty() && value.guid?.trim() == replacementGuid) ||
            (value.mediaGuid?.trim() == replacement.mediaGuid?.trim() &&
                (value.index ?: 0) == (replacement.index ?: 0))
    }
    if (index < 0) return result + replacement
    val current = result[index]
    result[index] = replacement.copy(
        mediaGuid = replacement.mediaGuid.nonBlankOr(current.mediaGuid),
        guid = replacement.guid.nonBlankOr(current.guid),
        title = replacement.title.nonBlankOr(current.title),
        index = replacement.index ?: current.index,
        resolutionType = replacement.resolutionType.nonBlankOr(current.resolutionType),
        colorRangeType = replacement.colorRangeType.nonBlankOr(current.colorRangeType),
        colorRange = replacement.colorRange.nonBlankOr(current.colorRange),
        codecName = replacement.codecName.nonBlankOr(current.codecName),
        codecType = replacement.codecType.nonBlankOr(current.codecType),
        profile = replacement.profile.nonBlankOr(current.profile),
        width = replacement.width ?: current.width,
        height = replacement.height ?: current.height,
        codedWidth = replacement.codedWidth ?: current.codedWidth,
        codedHeight = replacement.codedHeight ?: current.codedHeight,
        displayAspectRatio = replacement.displayAspectRatio.nonBlankOr(current.displayAspectRatio),
        pixelFormat = replacement.pixelFormat.nonBlankOr(current.pixelFormat),
        level = replacement.level ?: current.level,
        colorSpace = replacement.colorSpace.nonBlankOr(current.colorSpace),
        colorTransfer = replacement.colorTransfer.nonBlankOr(current.colorTransfer),
        colorPrimaries = replacement.colorPrimaries.nonBlankOr(current.colorPrimaries),
        dolbyVisionProfile = replacement.dolbyVisionProfile ?: current.dolbyVisionProfile,
        bitsPerRawSample = replacement.bitsPerRawSample ?: current.bitsPerRawSample,
        wrapper = replacement.wrapper.nonBlankOr(current.wrapper),
        durationSeconds = replacement.durationSeconds ?: current.durationSeconds,
        bitRate = replacement.bitRate ?: current.bitRate,
        fps = replacement.fps ?: current.fps,
        frameRate = replacement.frameRate ?: current.frameRate,
        averageFrameRate = replacement.averageFrameRate ?: current.averageFrameRate,
        rawFrameRate = replacement.rawFrameRate ?: current.rawFrameRate,
        progressive = replacement.progressive ?: current.progressive,
        bitDepth = replacement.bitDepth ?: current.bitDepth,
        referenceFrames = replacement.referenceFrames ?: current.referenceFrames,
        referenceFramesFallback = replacement.referenceFramesFallback ?: current.referenceFramesFallback,
        rotation = replacement.rotation ?: current.rotation,
        isBluray = replacement.isBluray ?: current.isBluray,
    )
    return result
}

private fun mergeFeiniuAudioStream(values: List<FeiniuAudioStream>, replacement: FeiniuAudioStream): List<FeiniuAudioStream> {
    val result = values.toMutableList()
    val index = result.indexOfFirst { value ->
        val replacementGuid = replacement.guid?.trim().orEmpty()
        (replacementGuid.isNotEmpty() && value.guid?.trim() == replacementGuid) ||
            (value.mediaGuid?.trim() == replacement.mediaGuid?.trim() &&
                (value.index ?: 0) == (replacement.index ?: 0))
    }
    if (index < 0) return result + replacement
    val current = result[index]
    result[index] = replacement.copy(
        mediaGuid = replacement.mediaGuid.nonBlankOr(current.mediaGuid),
        guid = replacement.guid.nonBlankOr(current.guid),
        index = replacement.index ?: current.index,
        title = replacement.title.nonBlankOr(current.title),
        audioType = replacement.audioType.nonBlankOr(current.audioType),
        codecName = replacement.codecName.nonBlankOr(current.codecName),
        codecType = replacement.codecType.nonBlankOr(current.codecType),
        language = replacement.language.nonBlankOr(current.language),
        channels = replacement.channels ?: current.channels,
        profile = replacement.profile.nonBlankOr(current.profile),
        sampleRate = replacement.sampleRate ?: current.sampleRate,
        isDefault = replacement.isDefault ?: current.isDefault,
        channelLayout = replacement.channelLayout.nonBlankOr(current.channelLayout),
        durationSeconds = replacement.durationSeconds ?: current.durationSeconds,
        bitRate = replacement.bitRate ?: current.bitRate,
        isFake = replacement.isFake ?: current.isFake,
    )
    return result
}

private fun mergeFeiniuSubtitleStream(
    values: List<FeiniuSubtitleStream>,
    replacement: FeiniuSubtitleStream,
): List<FeiniuSubtitleStream> {
    val result = values.toMutableList()
    val index = result.indexOfFirst { value ->
        val replacementGuid = replacement.guid?.trim().orEmpty()
        (replacementGuid.isNotEmpty() && value.guid?.trim() == replacementGuid) ||
            (value.mediaGuid?.trim() == replacement.mediaGuid?.trim() &&
                (value.index ?: 0) == (replacement.index ?: 0))
    }
    if (index < 0) return result + replacement
    val current = result[index]
    result[index] = replacement.copy(
        mediaGuid = replacement.mediaGuid.nonBlankOr(current.mediaGuid),
        guid = replacement.guid.nonBlankOr(current.guid),
        index = replacement.index ?: current.index,
        title = replacement.title.nonBlankOr(current.title),
        codecName = replacement.codecName.nonBlankOr(current.codecName),
        codecType = replacement.codecType.nonBlankOr(current.codecType),
        format = replacement.format.nonBlankOr(current.format),
        language = replacement.language.nonBlankOr(current.language),
        forced = replacement.forced ?: current.forced,
        isDefault = replacement.isDefault ?: current.isDefault,
        isExternal = replacement.isExternal ?: current.isExternal,
        trimId = replacement.trimId.nonBlankOr(current.trimId),
        sourceId = replacement.sourceId.nonBlankOr(current.sourceId),
        source = replacement.source.nonBlankOr(current.source),
        createTime = replacement.createTime ?: current.createTime,
        updateTime = replacement.updateTime ?: current.updateTime,
        extraFile = replacement.extraFile ?: current.extraFile,
        isBitmap = replacement.isBitmap ?: current.isBitmap,
        fileSizeBytes = replacement.fileSizeBytes ?: current.fileSizeBytes,
    )
    return result
}

private fun String?.nonBlankOr(fallback: String?): String? = this?.takeIf { it.isNotBlank() } ?: fallback
private fun Long?.nonZeroOr(fallback: Long?): Long? = this?.takeIf { it != 0L } ?: fallback
private fun Double?.nonZeroOr(fallback: Double?): Double? = this?.takeIf { it != 0.0 } ?: fallback

private fun feiniuMediaSources(
    detail: FeiniuItemDetail,
    streamData: FeiniuStreamListData,
    itemFiles: List<FeiniuItemFile>,
    playInfo: FeiniuPlayInfoData?,
): List<MediaSourceInfo> {
    val preferredGuid = playInfo?.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
        ?: detail.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
    val normalizedData = preferFeiniuMediaFile(mergeFeiniuItemFiles(streamData, itemFiles), preferredGuid)
    val files = normalizedData.files.toMutableList()
    if (files.isEmpty()) {
        val mediaGuids = buildList {
            fun addGuid(value: String?) {
                value?.trim()?.takeIf { it.isNotEmpty() && it !in this }?.let(::add)
            }
            normalizedData.videoStreams.forEach { addGuid(it.mediaGuid) }
            normalizedData.audioStreams.forEach { addGuid(it.mediaGuid) }
            normalizedData.subtitleStreams.forEach { addGuid(it.mediaGuid) }
            addGuid(playInfo?.mediaGuid)
            addGuid(detail.mediaGuid)
            if (isEmpty() && (normalizedData.videoStreams.isNotEmpty() || normalizedData.audioStreams.isNotEmpty() ||
                    normalizedData.subtitleStreams.isNotEmpty())) addGuid(detail.guid)
        }
        mediaGuids.forEachIndexed { index, guid ->
            files += FeiniuStreamFileInfo(
                guid = guid,
                fileName = detail.fileName ?: "版本 ${index + 1}",
                canPlay = detail.canPlay,
                playError = detail.playError,
            )
        }
    }
    val orderedFiles = preferFeiniuMediaFile(normalizedData.copy(files = files), preferredGuid).files
    return orderedFiles.mapIndexed { index, file ->
        val mediaGuid = file.guid?.trim().orEmpty()
        val singleFileFallback = orderedFiles.size == 1
        val videos = normalizedData.videoStreams.forMedia(mediaGuid, singleFileFallback) { it.mediaGuid }
        val audios = normalizedData.audioStreams.forMedia(mediaGuid, singleFileFallback) { it.mediaGuid }
        val subtitles = normalizedData.subtitleStreams.forMedia(mediaGuid, singleFileFallback) { it.mediaGuid }
        val primaryVideo = videos.firstOrNull()
        MediaSourceInfo(
            id = mediaGuid.takeIf(String::isNotEmpty) ?: "${detail.guid}:media:$index",
            name = file.fileName?.trim()?.takeIf(String::isNotEmpty)
                ?: file.path?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf(String::isNotEmpty)
                ?: "版本 ${index + 1}",
            path = file.path?.trim()?.takeIf(String::isNotEmpty),
            container = primaryVideo?.wrapper?.trim()?.takeIf(String::isNotEmpty)
                ?: feiniuContainer(file.fileName ?: file.path),
            sizeBytes = file.sizeBytes?.takeIf { it > 0L },
            runTimeMs = maxOf(
                feiniuDurationMs(detail.durationSeconds?.toDouble(), detail.runtimeMinutes),
                feiniuDurationMs(file.durationSeconds, null),
                feiniuDurationMs(primaryVideo?.durationSeconds, null),
            ).takeIf { it > 0L },
            fileCreatedEpochMs = feiniuTimestampMs(file.fileBirthTime ?: file.createTime ?: file.updateTime),
            dateAddedEpochMs = feiniuTimestampMs(file.createTime ?: file.updateTime ?: file.timestamp),
            sortOrder = file.sortNumber,
            playable = file.canPlay?.let { it > 0 },
            playError = file.playError?.trim()?.takeIf(String::isNotEmpty),
            defaultVideoIndex = videos.selectedIndex(playInfo?.videoGuid),
            defaultAudioIndex = audios.selectedIndex(playInfo?.audioGuid),
            defaultSubtitleIndex = if (playInfo?.subtitleGuid == "_no_display_") -1
                else subtitles.selectedIndex(playInfo?.subtitleGuid),
            streams = buildList {
                addAll(videos.map(FeiniuVideoStream::toDomainStream))
                addAll(audios.map(FeiniuAudioStream::toDomainStream))
                addAll(subtitles.map(FeiniuSubtitleStream::toDomainStream))
            },
        )
    }
}

private inline fun <T> List<T>.forMedia(
    mediaGuid: String,
    singleFileFallback: Boolean,
    crossinline guid: (T) -> String?,
): List<T> {
    val scoped = if (mediaGuid.isNotEmpty()) filter { guid(it)?.trim() == mediaGuid } else emptyList()
    return if (scoped.isNotEmpty() || !singleFileFallback) scoped else this
}

private fun <T> List<T>.selectedIndex(preferredGuid: String?): Int? where T : Any {
    val normalized = preferredGuid?.trim().orEmpty()
    fun guid(value: T): String? = when (value) {
        is FeiniuVideoStream -> value.guid
        is FeiniuAudioStream -> value.guid
        is FeiniuSubtitleStream -> value.guid
        else -> null
    }
    fun index(value: T): Int? = when (value) {
        is FeiniuVideoStream -> value.index
        is FeiniuAudioStream -> value.index
        is FeiniuSubtitleStream -> value.index
        else -> null
    }
    fun isDefault(value: T): Boolean = when (value) {
        is FeiniuAudioStream -> (value.isDefault ?: 0) > 0
        is FeiniuSubtitleStream -> (value.isDefault ?: 0) > 0
        else -> false
    }
    if (normalized.isNotEmpty()) firstOrNull { guid(it)?.trim() == normalized }?.let { return index(it) }
    return firstOrNull(::isDefault)?.let(::index)
}

private fun FeiniuVideoStream.toDomainStream(): MediaStreamInfo = MediaStreamInfo(
    index = index ?: 0,
    kind = MediaStreamKind.Video,
    codec = codecName?.trim()?.takeIf { it.isNotEmpty() }
        ?: codecType?.trim()?.takeIf { it.isNotEmpty() },
    profile = profile?.trim()?.takeIf { it.isNotEmpty() },
    level = level.feiniuNumber(),
    title = title?.trim()?.takeIf { it.isNotEmpty() },
    displayTitle = feiniuVideoDisplayTitle(),
    bitRate = bitRate?.takeIf { it > 0 },
    bitDepth = bitDepth?.takeIf { it > 0 } ?: bitsPerRawSample.feiniuNumber()?.toInt(),
    referenceFrames = referenceFrames?.takeIf { it > 0 }
        ?: referenceFramesFallback?.takeIf { it > 0 },
    widthPx = width?.takeIf { it > 0 },
    heightPx = height?.takeIf { it > 0 },
    codedWidthPx = codedWidth?.takeIf { it > 0 },
    codedHeightPx = codedHeight?.takeIf { it > 0 },
    aspectRatio = displayAspectRatio?.trim()?.takeIf { it.isNotEmpty() },
    frameRate = averageFrameRate.feiniuNumber() ?: frameRate.feiniuNumber() ?: rawFrameRate.feiniuNumber() ?: fps.feiniuNumber(),
    rotation = rotation,
    interlaced = progressive?.let { it <= 0 },
    resolutionType = resolutionType?.trim()?.takeIf { it.isNotEmpty() },
    videoRangeType = colorRangeType?.trim()?.takeIf { it.isNotEmpty() }
        ?: colorRange?.trim()?.takeIf { it.isNotEmpty() },
    dolbyVisionProfile = dolbyVisionProfile?.takeIf { it > 0 },
    colorPrimaries = colorPrimaries?.trim()?.takeIf { it.isNotEmpty() },
    colorSpace = colorSpace?.trim()?.takeIf { it.isNotEmpty() },
    colorTransfer = colorTransfer?.trim()?.takeIf { it.isNotEmpty() },
    pixelFormat = pixelFormat?.trim()?.takeIf { it.isNotEmpty() },
    isBluray = isBluray,
)

private fun FeiniuAudioStream.toDomainStream(): MediaStreamInfo = MediaStreamInfo(
    index = index ?: 0,
    kind = MediaStreamKind.Audio,
    codec = codecName?.trim()?.takeIf { it.isNotEmpty() }
        ?: codecType?.trim()?.takeIf { it.isNotEmpty() },
    profile = profile?.trim()?.takeIf { it.isNotEmpty() },
    language = language?.trim()?.takeIf { it.isNotEmpty() },
    title = title?.trim()?.takeIf { it.isNotEmpty() },
    displayTitle = feiniuAudioDisplayTitle(),
    isDefault = (isDefault ?: 0) > 0,
    channels = channels?.takeIf { it > 0 },
    channelLayout = channelLayout?.trim()?.takeIf { it.isNotEmpty() },
    sampleRateHz = sampleRate.feiniuNumber()?.toInt(),
    bitRate = bitRate?.takeIf { it > 0 },
    audioType = audioType?.trim()?.takeIf { it.isNotEmpty() },
    isFake = isFake,
)

private fun FeiniuSubtitleStream.toDomainStream(): MediaStreamInfo = MediaStreamInfo(
    index = index ?: 0,
    kind = MediaStreamKind.Subtitle,
    codec = codecName?.trim()?.takeIf { it.isNotEmpty() }
        ?: codecType?.trim()?.takeIf { it.isNotEmpty() }
        ?: format?.trim()?.takeIf { it.isNotEmpty() },
    language = language?.trim()?.takeIf { it.isNotEmpty() },
    title = title?.trim()?.takeIf { it.isNotEmpty() },
    displayTitle = feiniuSubtitleDisplayTitle(),
    isDefault = (isDefault ?: 0) > 0,
    isForced = (forced ?: 0) > 0,
    isExternal = (isExternal ?: 0) > 0 || (extraFile ?: 0) > 0,
    source = source?.trim()?.takeIf { it.isNotEmpty() }
        ?: sourceId?.trim()?.takeIf { it.isNotEmpty() },
    fileSizeBytes = fileSizeBytes?.takeIf { it > 0L },
)

private fun FeiniuVideoStream.feiniuVideoDisplayTitle(): String {
    title?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    resolutionType?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val validWidth = width?.takeIf { it > 0 }
    val validHeight = height?.takeIf { it > 0 }
    if (validWidth != null && validHeight != null) return "$validWidth × $validHeight"
    return codecName?.trim()?.takeIf(String::isNotEmpty)
        ?: codecType?.trim()?.takeIf(String::isNotEmpty)
        ?: "视频"
}

internal fun FeiniuAudioStream.feiniuAudioDisplayTitle(): String {
    title?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val parts = buildList {
        language?.trim()?.takeIf(String::isNotEmpty)?.let { add(it) }
        (codecName?.trim()?.takeIf(String::isNotEmpty)
            ?: codecType?.trim()?.takeIf(String::isNotEmpty))
            ?.uppercase()
            ?.let { add(it) }
        channels?.takeIf { it > 0 }?.let { add("${it}声道") }
    }
    return if (parts.isNotEmpty()) parts.joinToString(" · ") else "音轨"
}

internal fun FeiniuSubtitleStream.feiniuSubtitleDisplayTitle(): String {
    title?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val parts = buildList {
        language?.trim()?.takeIf(String::isNotEmpty)?.let { add(it) }
        (codecName?.trim()?.takeIf(String::isNotEmpty)
            ?: codecType?.trim()?.takeIf(String::isNotEmpty)
            ?: format?.trim()?.takeIf(String::isNotEmpty))
            ?.uppercase()
            ?.let { add(it) }
        if ((isExternal ?: 0) > 0 || (extraFile ?: 0) > 0) add("外挂")
    }
    return if (parts.isNotEmpty()) parts.joinToString(" · ") else "字幕"
}

/** Legacy mapper accepted only finite, positive ratings. */
private fun String?.feiniuRating(): Double? = this
    ?.trim()
    ?.toDoubleOrNull()
    ?.takeIf { it.isFinite() && it > 0.0 }

private fun JsonElement?.feiniuNumber(): Double? {
    val primitive = this as? JsonPrimitive ?: return null
    val raw = primitive.content.trim()
    val separator = raw.indexOf('/')
    if (separator > 0 && separator < raw.lastIndex) {
        val numerator = raw.substring(0, separator).toDoubleOrNull() ?: return null
        val denominator = raw.substring(separator + 1).toDoubleOrNull() ?: return null
        return (numerator / denominator).takeIf { denominator != 0.0 && it.isFinite() && it > 0.0 }
    }
    return (primitive.doubleOrNull ?: raw.toDoubleOrNull())?.takeIf { it.isFinite() && it > 0.0 }
}

private fun feiniuDurationMs(seconds: Double?, runtimeMinutes: Int?): Long = when {
    seconds != null && seconds > 0.0 -> (seconds * 1000.0).toLong()
    runtimeMinutes != null && runtimeMinutes > 0 -> runtimeMinutes * 60_000L
    else -> 0L
}

private fun feiniuTimestampMs(value: Long?): Long? = value?.takeIf { it > 0L }?.let {
    if (it < 100_000_000_000L) it * 1000L else it
}

private fun feiniuContainer(path: String?): String? {
    val source = path?.trim()?.substringBefore('?').orEmpty()
    val slash = maxOf(source.lastIndexOf('/'), source.lastIndexOf('\\'))
    val dot = source.lastIndexOf('.')
    return source.substring(dot + 1).lowercase().takeIf { dot > slash && it.isNotEmpty() }
}

/**
 * Translates the closed Feiniu SDK taxonomy onto the frozen [DomainError]
 * hierarchy. Note branch order: the expired subclass must be tested before
 * its open authentication parent. Cancellation never reaches this function.
 */
internal fun FeiniuError.toDomainError(): DomainError = when (this) {
    is FeiniuAuthenticationExpiredError -> AuthenticationError(AuthFailureKind.TokenExpired, message, this)
    is FeiniuAuthenticationError -> AuthenticationError(AuthFailureKind.InvalidCredentials, message, this)
    is FeiniuConnectionError -> if (kind == FeiniuConnectionKind.Timeout) {
        TimeoutError(message = message, cause = this)
    } else {
        NetworkUnreachableError(message, this)
    }
    is FeiniuBusinessError -> ServerError(
        statusCode = null, // envelope codes are not HTTP statuses
        message = "Feiniu business error $code${message?.let { ": $it" }.orEmpty()}",
        cause = this,
    )
    is FeiniuDecodeError -> ProtocolDecodingError(message, this)
    is FeiniuRedirectRejectedError -> ProtocolDecodingError(message, this)
    is FeiniuInvalidAddressError -> ClientConfigError(message, this)
    is FeiniuUnsupportedCapabilityError -> ClientConfigError(message, this)
}
