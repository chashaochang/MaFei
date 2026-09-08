package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.DomainError
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaFilter
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaUserData
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.NetworkUnreachableError
import cn.mafei.cmp.core.domain.PersonKind
import cn.mafei.cmp.core.domain.PersonRef
import cn.mafei.cmp.core.domain.ProtocolDecodingError
import cn.mafei.cmp.core.domain.RateLimitedError
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.core.domain.ServerError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.domain.SortDirection
import cn.mafei.cmp.core.domain.TimeoutError
import cn.mafei.cmp.core.domain.UnsupportedServerVersionError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinConnectionError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinConnectionKind
import cn.mafei.cmp.providers.jellyfin.core.JellyfinError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinInvalidAddressError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationExpiredError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthorizationError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinBusinessError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinDecodeError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinNotFoundError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinRateLimitedError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinRedirectRejectedError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinTicks
import cn.mafei.cmp.providers.jellyfin.core.JellyfinUnsupportedCapabilityError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinUnsupportedServerVersionError
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinItemFilter
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinItemQuery
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinSortBy
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinSortOrder
import cn.mafei.cmp.providers.jellyfin.wire.BaseItemDto
import cn.mafei.cmp.providers.jellyfin.wire.ImageType
import cn.mafei.cmp.providers.jellyfin.wire.ItemKind
import cn.mafei.cmp.providers.jellyfin.wire.WirePersonKind

/**
 * Jellyfin-side contract mappings (04B). Everything here is a pure function
 * of the frozen wire shapes in providers/jellyfin — the adapter never lets a
 * wire DTO escape into `data:adapter-contract` types half-translated.
 */
/** Fields every catalog listing needs to fill a [MediaItem]. */
internal val JELLYFIN_LIST_FIELDS = listOf(
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.Overview,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.Genres,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.People,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.ProductionYear,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.PremiereDate,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.DateCreated,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.Runtime,
)

internal fun ItemKind.toMediaType(): MediaType = when (this) {
    ItemKind.Movie -> MediaType.Movie
    ItemKind.Series -> MediaType.Series
    ItemKind.Season -> MediaType.Season
    ItemKind.Episode -> MediaType.Episode
    ItemKind.MusicArtist -> MediaType.MusicArtist
    ItemKind.MusicAlbum -> MediaType.MusicAlbum
    ItemKind.Audio -> MediaType.Audio
    ItemKind.PhotoAlbum -> MediaType.PhotoAlbum
    ItemKind.Photo -> MediaType.Photo
    ItemKind.BoxSet -> MediaType.BoxSet
    ItemKind.Folder -> MediaType.Folder
    ItemKind.Video -> MediaType.Video
    // Views, persons, genres, live-TV and anything else is not a
    // browsable media object; Unknown beats failing the whole page.
    ItemKind.AggregateFolder,
    ItemKind.AudioBook,
    ItemKind.BasePluginFolder,
    ItemKind.Book,
    ItemKind.Channel,
    ItemKind.ChannelFolderItem,
    ItemKind.CollectionFolder,
    ItemKind.Genre,
    ItemKind.ManualPlaylistsFolder,
    ItemKind.LiveTvChannel,
    ItemKind.LiveTvProgram,
    ItemKind.UserView,
    ItemKind.MusicGenre,
    ItemKind.MusicVideo,
    ItemKind.Person,
    ItemKind.Playlist,
    ItemKind.PlaylistsFolder,
    ItemKind.Program,
    ItemKind.Recording,
    ItemKind.Studio,
    ItemKind.Trailer,
    ItemKind.TvChannel,
    ItemKind.TvProgram,
    ItemKind.UserRootFolder,
    ItemKind.Year,
    ItemKind.Unknown,
    -> MediaType.Unknown
}

/** Playable leaf types; folders and containers are browsable only. */
private val PLAYABLE_TYPES = setOf(MediaType.Movie, MediaType.Episode, MediaType.Audio, MediaType.Video)

internal fun MediaType.toPlayable(): Boolean = this in PLAYABLE_TYPES

internal fun String?.toCollectionMediaTypes(): Set<MediaType> = when (this?.trim()?.lowercase()) {
    "movies" -> setOf(MediaType.Movie)
    "tvshows" -> setOf(MediaType.Series)
    "music" -> setOf(MediaType.MusicArtist, MediaType.MusicAlbum)
    "photos" -> setOf(MediaType.PhotoAlbum, MediaType.Photo)
    "boxsets" -> setOf(MediaType.BoxSet)
    "musicvideos" -> setOf(MediaType.Video)
    // homevideos/books/playlists and unknown future types: unconstrained.
    else -> emptySet()
}

internal fun MediaType.toIncludeItemTypes(): String = when (this) {
    MediaType.Movie -> "Movie"
    MediaType.Series -> "Series"
    MediaType.Season -> "Season"
    MediaType.Episode -> "Episode"
    MediaType.MusicArtist -> "MusicArtist"
    MediaType.MusicAlbum -> "MusicAlbum"
    MediaType.Audio -> "Audio"
    MediaType.PhotoAlbum -> "PhotoAlbum"
    MediaType.Photo -> "Photo"
    MediaType.BoxSet -> "BoxSet"
    MediaType.Folder -> "Folder"
    MediaType.Video -> "Video"
    // The wire query cannot express "items of unknown type"; a filter
    // containing Unknown would silently widen the query.
    MediaType.Unknown -> throw ClientConfigError("Unknown media type cannot be mapped to a Jellyfin includeItemTypes filter")
}

internal fun MediaSort?.toQuerySort(): Pair<List<JellyfinSortBy>, JellyfinSortOrder> {
    val sortBy = this?.let { sort ->
        listOf(
            when (sort.field) {
                // SeasonNumber/EpisodeNumber sort by the positional fields,
                // mirroring Jellyfin season/episode numbering semantics.
                cn.mafei.cmp.core.domain.MediaSortField.Title -> JellyfinSortBy.SortName
                cn.mafei.cmp.core.domain.MediaSortField.DateAdded -> JellyfinSortBy.DateCreated
                cn.mafei.cmp.core.domain.MediaSortField.PremiereDate -> JellyfinSortBy.PremiereDate
                cn.mafei.cmp.core.domain.MediaSortField.CommunityRating -> JellyfinSortBy.CommunityRating
                cn.mafei.cmp.core.domain.MediaSortField.Runtime -> JellyfinSortBy.Runtime
                cn.mafei.cmp.core.domain.MediaSortField.SeasonNumber -> JellyfinSortBy.ParentIndexNumber
                cn.mafei.cmp.core.domain.MediaSortField.EpisodeNumber -> JellyfinSortBy.IndexNumber
            },
        )
    } ?: listOf(JellyfinSortBy.SortName)
    val sortOrder = when (this?.direction) {
        null, SortDirection.Ascending -> JellyfinSortOrder.Ascending
        SortDirection.Descending -> JellyfinSortOrder.Descending
    }
    return sortBy to sortOrder
}

internal fun MediaFilter.toQueryFilters(): List<JellyfinItemFilter> = buildList {
    if (favoritesOnly) add(JellyfinItemFilter.IsFavorite)
    if (playedOnly) add(JellyfinItemFilter.IsPlayed)
    if (unplayedOnly) add(JellyfinItemFilter.IsUnplayed)
}

internal fun ImageType.toImageKind(): ImageKind? = when (this) {
    ImageType.Primary -> ImageKind.Primary
    ImageType.Backdrop -> ImageKind.Backdrop
    ImageType.Banner -> ImageKind.Banner
    ImageType.Thumb -> ImageKind.Thumb
    ImageType.Logo -> ImageKind.Logo
    ImageType.Art -> ImageKind.Art
    ImageType.Disc,
    ImageType.Box,
    ImageType.Screenshot,
    ImageType.Menu,
    ImageType.Chapter,
    ImageType.BoxRear,
    ImageType.Profile,
    -> null
}

internal fun WirePersonKind.toPersonKind(): PersonKind = when (this) {
    WirePersonKind.Actor -> PersonKind.Actor
    WirePersonKind.Director -> PersonKind.Director
    WirePersonKind.Writer -> PersonKind.Writer
    WirePersonKind.Producer -> PersonKind.Producer
    WirePersonKind.Composer -> PersonKind.Composer
    WirePersonKind.GuestStar -> PersonKind.GuestStar
    WirePersonKind.Unknown,
    WirePersonKind.Conductor,
    WirePersonKind.Lyricist,
    WirePersonKind.Arranger,
    WirePersonKind.Engineer,
    WirePersonKind.Mixer,
    WirePersonKind.Remixer,
    WirePersonKind.Creator,
    WirePersonKind.Artist,
    WirePersonKind.AlbumArtist,
    WirePersonKind.Author,
    WirePersonKind.Illustrator,
    WirePersonKind.Penciller,
    WirePersonKind.Inker,
    WirePersonKind.Colorist,
    WirePersonKind.Letterer,
    WirePersonKind.CoverArtist,
    WirePersonKind.Editor,
    WirePersonKind.Translator,
    -> PersonKind.Unknown
}

/**
 * Maps one wire item to a [MediaItem], or returns null when the item has
 * no identity (dropped instead of failing the page — a null id cannot be
 * cached, re-fetched or played).
 *
 * Jellyfin number semantics: an Episode's season is [BaseItemDto.parentIndexNumber]
 * and its episode number is [BaseItemDto.indexNumber]; a Season's own
 * number is its [BaseItemDto.indexNumber]. [collectionId] is the query
 * context the item was fetched under, not a wire field.
 */
internal fun BaseItemDto.toMediaItem(
    serverId: ServerId,
    imageUrl: (itemId: String, imageType: ImageType, tag: String) -> String,
    collectionId: CollectionId? = null,
): MediaItem? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    val mediaType = (type ?: ItemKind.Unknown).toMediaType()
    return MediaItem(
        id = MediaId(id),
        serverId = serverId,
        provider = cn.mafei.cmp.core.domain.ProviderKind.Jellyfin,
        title = name ?: "",
        mediaType = mediaType,
        originalTitle = originalTitle,
        overview = overview,
        collectionId = collectionId,
        seriesId = seriesId?.let(::MediaId),
        seasonId = seasonId?.let(::MediaId),
        seasonNumber = when (mediaType) {
            MediaType.Episode -> parentIndexNumber
            MediaType.Season -> indexNumber
            else -> null
        },
        episodeNumber = if (mediaType == MediaType.Episode) indexNumber else null,
        indexNumber = if (mediaType == MediaType.Episode || mediaType == MediaType.Season) null else indexNumber,
        parentIndexNumber = if (mediaType == MediaType.Episode || mediaType == MediaType.Season) null else parentIndexNumber,
        year = productionYear,
        premiereEpochMs = premiereDate,
        dateAddedEpochMs = dateCreated,
        runtimeMs = runTimeTicks?.let(JellyfinTicks::toMilliseconds),
        communityRating = communityRating,
        officialRating = officialRating,
        genres = genres,
        people = people.mapNotNull { person ->
            person.name?.let { name ->
                PersonRef(
                    name = name,
                    role = person.role,
                    personType = (person.type ?: WirePersonKind.Unknown).toPersonKind(),
                    image = null, // person images would need a per-person URL call; P0 omits them
                )
            }
        },
        images = buildImages(id, imageUrl),
        userData = userData.let { data ->
            MediaUserData(
                isFavorite = data?.isFavorite ?: false,
                isPlayed = data?.played ?: false,
                playCount = data?.playCount,
                playbackPositionMs = (data?.playbackPositionTicks ?: 0L).let(JellyfinTicks::toMilliseconds),
                playedPercentage = data?.playedPercentage,
                lastPlayedEpochMs = data?.lastPlayedDate,
            )
        },
        canPlay = mediaType.toPlayable(),
    )
}

private fun BaseItemDto.buildImages(
    itemId: String,
    imageUrl: (itemId: String, imageType: ImageType, tag: String) -> String,
): List<cn.mafei.cmp.core.domain.MediaImage> = buildList {
    imageTags.forEach { (key, tag) ->
        val type = ImageType.entries.firstOrNull { it.name == key } ?: return@forEach
        type.toImageKind()?.let { kind -> add(cn.mafei.cmp.core.domain.MediaImage(kind, imageUrl(itemId, type, tag))) }
    }
    backdropImageTags.forEach { tag ->
        add(cn.mafei.cmp.core.domain.MediaImage(ImageKind.Backdrop, imageUrl(itemId, ImageType.Backdrop, tag)))
    }
}

/** Maps one view item to a [MediaCollection]; identity-less entries are dropped. */
internal fun BaseItemDto.toMediaCollection(
    serverId: ServerId,
    imageUrl: (itemId: String, imageType: ImageType, tag: String) -> String,
): MediaCollection? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    return MediaCollection(
        id = CollectionId(id),
        serverId = serverId,
        provider = cn.mafei.cmp.core.domain.ProviderKind.Jellyfin,
        name = name ?: "",
        mediaTypes = collectionType.toCollectionMediaTypes(),
        primaryImage = imageTags[ImageType.Primary.name]?.let { tag ->
            cn.mafei.cmp.core.domain.MediaImage(ImageKind.Primary, imageUrl(id, ImageType.Primary, tag))
        },
    )
}

/**
 * Translates the closed Jellyfin SDK error taxonomy onto the frozen
 * [DomainError] hierarchy. [CancellationException] never reaches this
 * function — callers rethrow it before translating — and unexpected non-SDK
 * exceptions propagate untouched instead of being disguised as domain errors.
 */
internal fun JellyfinError.toDomainError(): DomainError = when (this) {
    is JellyfinInvalidAddressError -> ClientConfigError(message, this)
    is JellyfinConnectionError -> if (kind == JellyfinConnectionKind.Timeout) {
        TimeoutError(message = message, cause = this)
    } else {
        NetworkUnreachableError(message, this)
    }
    is JellyfinAuthenticationError -> cn.mafei.cmp.core.domain.AuthenticationError(
        cn.mafei.cmp.core.domain.AuthFailureKind.InvalidCredentials,
        message,
        this,
    )
    is JellyfinAuthenticationExpiredError -> cn.mafei.cmp.core.domain.AuthenticationError(
        cn.mafei.cmp.core.domain.AuthFailureKind.TokenExpired,
        message,
        this,
    )
    is JellyfinAuthorizationError -> cn.mafei.cmp.core.domain.AuthorizationError(message, this)
    is JellyfinNotFoundError -> ResourceNotFoundError(message = message, cause = this)
    is JellyfinRateLimitedError -> RateLimitedError(retryAfterMs, message, this)
    is JellyfinServerError -> ServerError(statusCode, message, this)
    // 4xx beyond the typed branches: a server-side refusal of this request.
    is JellyfinBusinessError -> ServerError(statusCode, message, this)
    is JellyfinDecodeError -> ProtocolDecodingError(message, this)
    is JellyfinRedirectRejectedError -> ProtocolDecodingError(message, this)
    is JellyfinUnsupportedCapabilityError -> ClientConfigError(message, this)
    is JellyfinUnsupportedServerVersionError -> UnsupportedServerVersionError(actualVersion, minimumSupported, this)
}
