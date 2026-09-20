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
import cn.mafei.cmp.core.domain.MediaPerson
import cn.mafei.cmp.core.domain.MediaSourceInfo
import cn.mafei.cmp.core.domain.MediaStreamInfo
import cn.mafei.cmp.core.domain.MediaStreamKind
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
import cn.mafei.cmp.providers.jellyfin.wire.MediaSourceInfoDto
import cn.mafei.cmp.providers.jellyfin.wire.MediaStreamDto
import cn.mafei.cmp.providers.jellyfin.wire.MediaStreamType
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
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.MediaStreams,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.ProductionYear,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.PremiereDate,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.DateCreated,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.Runtime,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.ItemCounts,
    cn.mafei.cmp.providers.jellyfin.wire.ItemField.ChildCount,
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
                cn.mafei.cmp.core.domain.MediaSortField.Default -> JellyfinSortBy.Default
                cn.mafei.cmp.core.domain.MediaSortField.Title -> JellyfinSortBy.Name
                cn.mafei.cmp.core.domain.MediaSortField.DateAdded -> JellyfinSortBy.DateCreated
                cn.mafei.cmp.core.domain.MediaSortField.DatePlayed -> JellyfinSortBy.DatePlayed
                cn.mafei.cmp.core.domain.MediaSortField.PremiereDate -> JellyfinSortBy.PremiereDate
                cn.mafei.cmp.core.domain.MediaSortField.CommunityRating -> JellyfinSortBy.CommunityRating
                cn.mafei.cmp.core.domain.MediaSortField.CriticRating -> JellyfinSortBy.CriticRating
                cn.mafei.cmp.core.domain.MediaSortField.ProductionYear -> JellyfinSortBy.ProductionYear
                cn.mafei.cmp.core.domain.MediaSortField.PlayCount -> JellyfinSortBy.PlayCount
                cn.mafei.cmp.core.domain.MediaSortField.Runtime -> JellyfinSortBy.Runtime
                cn.mafei.cmp.core.domain.MediaSortField.OfficialRating -> JellyfinSortBy.OfficialRating
                cn.mafei.cmp.core.domain.MediaSortField.Random -> JellyfinSortBy.Random
                cn.mafei.cmp.core.domain.MediaSortField.Album -> JellyfinSortBy.Album
                cn.mafei.cmp.core.domain.MediaSortField.Artist -> JellyfinSortBy.Artist
                cn.mafei.cmp.core.domain.MediaSortField.AlbumArtist -> JellyfinSortBy.AlbumArtist
                cn.mafei.cmp.core.domain.MediaSortField.AiredEpisodeOrder -> JellyfinSortBy.AiredEpisodeOrder
                cn.mafei.cmp.core.domain.MediaSortField.SeriesSortName -> JellyfinSortBy.SeriesSortName
                cn.mafei.cmp.core.domain.MediaSortField.Studio -> JellyfinSortBy.Studio
                cn.mafei.cmp.core.domain.MediaSortField.IsFavoriteOrLiked -> JellyfinSortBy.IsFavoriteOrLiked
                cn.mafei.cmp.core.domain.MediaSortField.DateLastContentAdded -> JellyfinSortBy.DateLastContentAdded
                cn.mafei.cmp.core.domain.MediaSortField.IsUnplayed -> JellyfinSortBy.IsUnplayed
                cn.mafei.cmp.core.domain.MediaSortField.IsPlayed -> JellyfinSortBy.IsPlayed
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
    imageUrl: (itemId: String, imageType: ImageType, tag: String?) -> String,
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
        seriesTitle = seriesName,
        seasonId = seasonId?.let(::MediaId),
        seasonTitle = seasonName,
        seasonNumber = when (mediaType) {
            MediaType.Episode -> parentIndexNumber
            MediaType.Season -> indexNumber
            else -> null
        },
        episodeNumber = if (mediaType == MediaType.Episode) indexNumber else null,
        childCount = (childCount ?: episodeCount)?.coerceAtLeast(0),
        indexNumber = if (mediaType == MediaType.Episode || mediaType == MediaType.Season) null else indexNumber,
        parentIndexNumber = if (mediaType == MediaType.Episode || mediaType == MediaType.Season) null else parentIndexNumber,
        year = productionYear,
        premiereEpochMs = premiereDate,
        dateAddedEpochMs = dateCreated,
        runtimeMs = runTimeTicks?.let(JellyfinTicks::toMilliseconds),
        communityRating = communityRating,
        officialRating = officialRating,
        genres = genres,
        tags = resolutionTags(),
        people = people.mapNotNull { person ->
            val personId = person.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            PersonRef(
                id = MediaId(personId),
                name = person.name.orEmpty(),
                role = person.role,
                personType = (person.type ?: WirePersonKind.Unknown).toPersonKind(),
                image = cn.mafei.cmp.core.domain.MediaImage(
                    ImageKind.Primary,
                    imageUrl(personId, ImageType.Primary, person.primaryImageTag),
                ),
            )
        },
        images = buildImages(id, imageUrl),
        userData = userData.let { data ->
            MediaUserData(
                isFavorite = data?.isFavorite ?: false,
                isPlayed = data?.played ?: false,
                playCount = data?.playCount,
                unplayedItemCount = data?.unplayedItemCount,
                playbackPositionMs = (data?.playbackPositionTicks ?: 0L).let(JellyfinTicks::toMilliseconds),
                playedPercentage = data?.playedPercentage,
                lastPlayedEpochMs = data?.lastPlayedDate,
            )
        },
        canPlay = mediaType.toPlayable(),
        mediaSources = this.mediaSources.mapIndexed { index, source ->
            source.toMediaSourceInfo().let { mapped ->
                if (mapped.id == null) mapped.copy(id = "$id:$index") else mapped
            }
        },
        externalUrls = externalUrls.mapNotNull { link ->
            val url = link.url?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            cn.mafei.cmp.core.domain.MediaExternalUrl(
                name = link.name?.trim().takeUnless { it.isNullOrEmpty() } ?: url,
                url = url,
            )
        },
    )
}

/**
 * Maps one Jellyfin media source (a playable file / version) and its elementary
 * streams. The wire DTO already carried this for every item; only the poster
 * resolution tags consumed the video stream before, so the legacy
 * 「文件媒体信息」 block had nothing to render.
 */
internal fun MediaSourceInfoDto.toMediaSourceInfo(): MediaSourceInfo = MediaSourceInfo(
    id = id?.takeIf { it.isNotBlank() },
    name = name?.takeIf { it.isNotBlank() },
    path = path?.takeIf { it.isNotBlank() },
    container = container?.takeIf { it.isNotBlank() },
    sizeBytes = size,
    runTimeMs = runTimeTicks?.let(JellyfinTicks::toMilliseconds),
    isRemote = isRemote ?: false,
    defaultAudioIndex = defaultAudioStreamIndex,
    defaultSubtitleIndex = defaultSubtitleStreamIndex,
    streams = mediaStreams.map { it.toMediaStreamInfo() },
)

internal fun MediaStreamDto.toMediaStreamInfo(): MediaStreamInfo = MediaStreamInfo(
    index = index,
    kind = when (type) {
        MediaStreamType.Video -> MediaStreamKind.Video
        MediaStreamType.Audio -> MediaStreamKind.Audio
        MediaStreamType.Subtitle -> MediaStreamKind.Subtitle
        else -> MediaStreamKind.Other
    },
    codec = codec?.takeIf { it.isNotBlank() },
    profile = profile?.takeIf { it.isNotBlank() },
    level = level,
    language = language?.takeIf { it.isNotBlank() },
    title = title?.takeIf { it.isNotBlank() },
    displayTitle = displayTitle?.takeIf { it.isNotBlank() },
    isDefault = isDefault ?: false,
    isForced = isForced ?: false,
    isExternal = isExternal ?: false,
    channels = channels,
    channelLayout = channelLayout?.takeIf { it.isNotBlank() },
    sampleRateHz = sampleRate,
    bitRate = bitRate,
    bitDepth = bitDepth,
    referenceFrames = refFrames,
    widthPx = width,
    heightPx = height,
    aspectRatio = aspectRatio?.takeIf { it.isNotBlank() },
    frameRate = averageFrameRate ?: realFrameRate,
    interlaced = isInterlaced,
    videoRangeType = videoRangeType?.takeIf { it.isNotBlank() },
    dolbyVisionProfile = dolbyVisionProfile,
    colorPrimaries = colorPrimaries?.takeIf { it.isNotBlank() },
    colorSpace = colorSpace?.takeIf { it.isNotBlank() },
    colorTransfer = colorTransfer?.takeIf { it.isNotBlank() },
    pixelFormat = pixelFormat?.takeIf { it.isNotBlank() },
)

/** Mechanical mapping of the legacy Jellyfin person detail contract. */
internal fun BaseItemDto.toMediaPerson(
    serverId: ServerId,
    imageUrl: (itemId: String, imageType: ImageType, tag: String?) -> String,
): MediaPerson? {
    val personId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val displayName = name.orEmpty()
    return MediaPerson(
        id = MediaId(personId),
        serverId = serverId,
        provider = cn.mafei.cmp.core.domain.ProviderKind.Jellyfin,
        name = displayName,
        sortName = sortName?.takeIf { it.isNotBlank() } ?: displayName,
        overview = overview,
        image = cn.mafei.cmp.core.domain.MediaImage(
            ImageKind.Primary,
            imageUrl(personId, ImageType.Primary, imageTags[ImageType.Primary.name]),
        ),
        isFavorite = userData?.isFavorite ?: false,
        movieCount = movieCount?.takeIf { it >= 0 },
        seriesCount = seriesCount?.takeIf { it >= 0 },
        episodeCount = episodeCount?.takeIf { it >= 0 },
    )
}

/** Exact thresholds and two-badge cap used by the HarmonyOS catalog mapper. */
private fun BaseItemDto.resolutionTags(): List<String> {
    val streams = if (mediaStreams.isNotEmpty()) mediaStreams else mediaSources.flatMap { it.mediaStreams }
    return buildList {
        streams.forEach { stream ->
            val width = stream.width ?: 0
            val height = stream.height ?: 0
            val tag = when {
                width >= 3800 || height >= 2000 -> "4K"
                width >= 2500 || height >= 1400 -> "1440"
                width >= 1800 || height >= 1000 -> "1080"
                width >= 1200 || height >= 700 -> "720"
                width >= 700 || height >= 400 -> "480"
                else -> null
            }
            if (tag != null && tag !in this && size < 2) add(tag)
        }
    }
}

private fun BaseItemDto.buildImages(
    itemId: String,
    imageUrl: (itemId: String, imageType: ImageType, tag: String?) -> String,
): List<cn.mafei.cmp.core.domain.MediaImage> {
    val images = buildList {
        fun addImage(kind: ImageKind, ownerId: String?, imageType: ImageType, tag: String?) {
            val normalizedId = ownerId?.takeIf { it.isNotBlank() } ?: return
            add(cn.mafei.cmp.core.domain.MediaImage(kind, imageUrl(normalizedId, imageType, tag)))
        }

        // HarmonyOS did not assume that an Episode owns display-ready art.
        // Posters use the series image; landscape shelves prefer parent Thumb,
        // then parent Backdrop, then the series Primary image.
        if (type == ItemKind.Episode) {
            addImage(ImageKind.Primary, seriesId, ImageType.Primary, seriesPrimaryImageTag)
            when {
                !parentThumbItemId.isNullOrBlank() -> addImage(
                    ImageKind.Backdrop,
                    parentThumbItemId,
                    ImageType.Thumb,
                    parentThumbImageTag,
                )
                !parentBackdropItemId.isNullOrBlank() -> addImage(
                    ImageKind.Backdrop,
                    parentBackdropItemId,
                    ImageType.Backdrop,
                    parentBackdropImageTags.firstOrNull(),
                )
                !seriesId.isNullOrBlank() -> addImage(
                    ImageKind.Backdrop,
                    seriesId,
                    ImageType.Primary,
                    seriesPrimaryImageTag,
                )
            }
        } else {
            // Match the original HarmonyOS mapper: every identified
            // non-episode first uses its own Primary route, even when
            // /Items/Latest omits ImageTags. The tag is a cache revision, not
            // proof that the image exists. Runtime fallback handles a real
            // 404 by trying the remaining inherited/thumbnail candidates.
            addImage(
                ImageKind.Primary,
                itemId,
                ImageType.Primary,
                imageTags[ImageType.Primary.name],
            )
            if (!parentPrimaryImageItemId.isNullOrBlank()) {
                addImage(
                    ImageKind.Primary,
                    parentPrimaryImageItemId,
                    ImageType.Primary,
                    parentPrimaryImageTag,
                )
            }
        }

        // The legacy mapper treats ParentThumbItemId as the preferred
        // thumbnail for every summary type, not only Episode. /Items/Latest
        // frequently uses that inherited image for grouped videos.
        if (type != ItemKind.Episode) {
            if (!parentThumbItemId.isNullOrBlank()) {
                addImage(ImageKind.Thumb, parentThumbItemId, ImageType.Thumb, parentThumbImageTag)
            }
            imageTags[ImageType.Thumb.name]?.let { tag ->
                addImage(ImageKind.Thumb, itemId, ImageType.Thumb, tag)
            }
        }

        listOf(
            ImageType.Banner,
            ImageType.Logo,
            ImageType.Art,
        ).forEach { imageType ->
            imageTags[imageType.name]?.let { tag ->
                imageType.toImageKind()?.let { kind -> addImage(kind, itemId, imageType, tag) }
            }
        }
        backdropImageTags.forEach { tag ->
            addImage(ImageKind.Backdrop, itemId, ImageType.Backdrop, tag)
        }
        if (type != ItemKind.Episode && !parentBackdropItemId.isNullOrBlank()) {
            parentBackdropImageTags.forEach { tag ->
                addImage(ImageKind.Backdrop, parentBackdropItemId, ImageType.Backdrop, tag)
            }
        }
    }
    return images.distinctBy { it.kind to it.url }
}

/** Maps one view item to a [MediaCollection]; identity-less entries are dropped. */
internal fun BaseItemDto.toMediaCollection(
    serverId: ServerId,
    imageUrl: (itemId: String, imageType: ImageType, tag: String?) -> String,
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
        itemCount = childCount?.takeIf { it >= 0 },
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
