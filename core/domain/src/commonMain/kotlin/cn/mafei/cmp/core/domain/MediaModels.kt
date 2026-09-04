package cn.mafei.cmp.core.domain

import kotlinx.serialization.Serializable

/**
 * Reference to an image resource. The url is opaque to the domain: providers
 * decide whether it is absolute or needs a token appended at render time.
 */
@Serializable
data class MediaImage(
    val kind: ImageKind = ImageKind.Primary,
    val url: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
)

/**
 * Per-user state attached to a media item (favorites, watch progress).
 * Providers fold their user-data concepts into this stable shape.
 */
@Serializable
data class MediaUserData(
    val isFavorite: Boolean = false,
    val isPlayed: Boolean = false,
    val playCount: Int? = null,
    val playbackPositionMs: Long = 0L,
    val playedPercentage: Double? = null,
    val lastPlayedEpochMs: Long? = null,
) {
    val hasProgress: Boolean get() = playbackPositionMs > 0L
}

/** A person credited on a media item. */
@Serializable
data class PersonRef(
    val name: String,
    val role: String? = null,
    val personType: PersonKind = PersonKind.Actor,
    val image: MediaImage? = null,
)

/**
 * The stable, provider-neutral description of a browsable media object.
 *
 * This model must never embed provider wire DTOs (e.g. Jellyfin
 * `BaseItemDto` or FNOS catalog payloads); adapters translate those into
 * these fields. Optional numeric positional data uses [seasonNumber] /
 * [episodeNumber] and the generic [indexNumber]/[parentIndexNumber] pair for
 * other hierarchies (tracks, photos, chapters).
 */
@Serializable
data class MediaItem(
    val id: MediaId,
    val serverId: ServerId,
    val provider: ProviderKind,
    val title: String,
    val mediaType: MediaType = MediaType.Unknown,
    val originalTitle: String? = null,
    val overview: String? = null,
    val collectionId: CollectionId? = null,
    val parentId: MediaId? = null,
    val seriesId: MediaId? = null,
    val seasonId: MediaId? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val year: Int? = null,
    val premiereEpochMs: Long? = null,
    val dateAddedEpochMs: Long? = null,
    val runtimeMs: Long? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val genres: List<String> = emptyList(),
    val people: List<PersonRef> = emptyList(),
    val images: List<MediaImage> = emptyList(),
    val userData: MediaUserData = MediaUserData(),
    val canPlay: Boolean = false,
) {
    val primaryImage: MediaImage? get() = images.firstOrNull { it.kind == ImageKind.Primary } ?: images.firstOrNull()
}

/**
 * A server-side library ("view") grouping media items, used for the home
 * screen shelves.
 */
@Serializable
data class MediaCollection(
    val id: CollectionId,
    val serverId: ServerId,
    val provider: ProviderKind,
    val name: String,
    val mediaTypes: Set<MediaType> = emptySet(),
    val primaryImage: MediaImage? = null,
)
