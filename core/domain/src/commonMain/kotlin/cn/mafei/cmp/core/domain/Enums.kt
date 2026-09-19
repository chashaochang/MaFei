package cn.mafei.cmp.core.domain

import kotlinx.serialization.Serializable

/**
 * Which backend family a server, account or media object belongs to.
 *
 * Providers are strictly separated: Jellyfin and Feiniu (FNOS) adapters must
 * never share wire models, and new provider kinds require a contract review.
 */
@Serializable
enum class ProviderKind {
    Jellyfin,
    Feiniu,
}

/**
 * Provider-neutral media classification. Adapters map vendor-specific item
 * types onto these values; anything unmapped becomes [Unknown] instead of
 * failing the whole catalog load.
 */
@Serializable
enum class MediaType {
    Movie,
    Series,
    Season,
    Episode,
    MusicArtist,
    MusicAlbum,
    Audio,
    PhotoAlbum,
    Photo,
    BoxSet,
    Folder,
    Video,
    Unknown,
}

/** Semantic role of an image attached to media or accounts. */
@Serializable
enum class ImageKind {
    Primary,
    Backdrop,
    Banner,
    Thumb,
    Logo,
    Art,
}

/** Role a person has in relation to a media item. */
@Serializable
enum class PersonKind {
    Actor,
    Director,
    Writer,
    Producer,
    Composer,
    GuestStar,
    Unknown,
}

/** Ordering direction used by [MediaSort]. */
@Serializable
enum class SortDirection {
    Ascending,
    Descending,
}

/** Canonical sortable fields; providers map these to their native sort keys. */
@Serializable
enum class MediaSortField {
    Title,
    DateAdded,
    DatePlayed,
    PremiereDate,
    CommunityRating,
    Runtime,
    SeasonNumber,
    EpisodeNumber,
}
