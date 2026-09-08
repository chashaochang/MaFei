package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.AuthFailureKind
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.DomainError
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaUserData
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.NetworkUnreachableError
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ProtocolDecodingError
import cn.mafei.cmp.core.domain.ServerError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.domain.TimeoutError
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
import cn.mafei.cmp.providers.feiniu.core.FeiniuRedirectRejectedError
import cn.mafei.cmp.providers.feiniu.core.FeiniuUnsupportedCapabilityError

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
    "tv" -> MediaType.Series
    "directory" -> MediaType.Folder
    "video" -> MediaType.Video
    "season" -> MediaType.Season
    "episode" -> MediaType.Episode
    // LiveChannel and unknown future types: MediaType has no live entry.
    else -> MediaType.Unknown
}

internal fun MediaType.toLibraryMediaTypes(): Set<MediaType> = when (this) {
    MediaType.Unknown -> emptySet()
    else -> setOf(this)
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
): MediaItem? {
    if (guid.isBlank()) return null
    val mediaType = type.toFeiniuMediaType()
    val stream = mediaStream
    val runtimeMs = (durationSeconds ?: 0L).takeIf { it > 0 }?.let { it * 1000L }
        ?: runtimeMinutes?.takeIf { it > 0 }?.let { it * 60_000L }
    val premiere = feiniuDateToEpochMsAndYear(releaseDate) ?: feiniuDateToEpochMsAndYear(firstAirDate)
    return MediaItem(
        id = MediaId(guid),
        serverId = serverId,
        provider = ProviderKind.Feiniu,
        title = title,
        mediaType = mediaType,
        overview = overview,
        collectionId = collectionId,
        parentId = parentGuid?.takeIf { it.isNotBlank() }?.let(::MediaId),
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        year = premiere?.second,
        premiereEpochMs = premiere?.first,
        dateAddedEpochMs = epochSeconds?.takeIf { it > 0 }?.let { it * 1000L },
        runtimeMs = runtimeMs,
        communityRating = voteAverage?.trim()?.toDoubleOrNull(),
        genres = emptyList(), // wire carries genre tags only as an Int request filter
        people = emptyList(), // wire carries no people list in P0
        images = listOfNotNull(
            poster?.takeIf { it.isNotBlank() }?.let { path ->
                cn.mafei.cmp.core.domain.MediaImage(ImageKind.Primary, imageUrl(path))
            },
        ),
        userData = MediaUserData(
            isFavorite = (isFavorite ?: 0) > 0,
            isPlayed = (watched ?: 0) > 0,
            playCount = null, // no wire field
            playbackPositionMs = stream?.currentTimeSeconds?.takeIf { it > 0.0 }?.let { (it * 1000).toLong() } ?: 0L,
            playedPercentage = stream?.progress,
            lastPlayedEpochMs = watchedEpochSeconds?.takeIf { it > 0 }?.let { it * 1000L },
        ),
        canPlay = (canPlay ?: 0) > 0,
    )
}

/**
 * Maps the P0 detail payload. It carries no user state — the adapter never
 * invents one (repositories merge list-item state on top). `canPlay` reflects
 * the server's own `play_error` flag.
 */
internal fun FeiniuItemDetail.toMediaItem(serverId: ServerId, imageUrl: (imagePath: String) -> String): MediaItem? {
    val id = guid?.takeIf { it.isNotBlank() } ?: return null
    val premiere = feiniuDateToEpochMsAndYear(releaseDate) ?: feiniuDateToEpochMsAndYear(firstAirDate)
    return MediaItem(
        id = MediaId(id),
        serverId = serverId,
        provider = ProviderKind.Feiniu,
        title = title ?: "",
        mediaType = type.toFeiniuMediaType(),
        originalTitle = originalTitle,
        overview = overview,
        year = premiere?.second,
        premiereEpochMs = premiere?.first,
        communityRating = voteAverage?.trim()?.toDoubleOrNull(),
        officialRating = contentRatings?.takeIf { it.isNotBlank() },
        images = buildList {
            posters?.takeIf { it.isNotBlank() }?.let { add(cn.mafei.cmp.core.domain.MediaImage(ImageKind.Primary, imageUrl(it))) }
            backdrops?.takeIf { it.isNotBlank() }?.let { add(cn.mafei.cmp.core.domain.MediaImage(ImageKind.Backdrop, imageUrl(it))) }
        },
        canPlay = playError.isNullOrBlank(),
    )
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
