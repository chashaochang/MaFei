package cn.mafei.cmp.providers.jellyfin.wire

import cn.mafei.cmp.core.serialization.UnknownTolerantEnumSerializer
import kotlinx.serialization.Serializable

/**
 * Jellyfin wire enums for the P0 surface. Every enum tolerates unknown
 * server-side additions by falling back to an `Unknown` constant so a newer
 * server can never break an older client (see the frozen GLM-01 contract).
 */
@Serializable(with = ItemKindSerializer::class)
enum class ItemKind {
    Movie,
    Series,
    Season,
    Episode,
    BoxSet,
    CollectionFolder,
    Folder,
    UserView,
    MusicArtist,
    MusicAlbum,
    Audio,
    MusicVideo,
    PhotoAlbum,
    Photo,
    Video,
    Playlist,
    Program,
    Channel,
    Unknown,
}

data object ItemKindSerializer : UnknownTolerantEnumSerializer<ItemKind>(
    values = ItemKind.entries.toTypedArray(),
    unknownValue = ItemKind.Unknown,
) {
    override val serialName: String = "ItemKind"
}

@Serializable(with = WirePersonKindSerializer::class)
enum class WirePersonKind {
    Actor,
    Director,
    Writer,
    Producer,
    Composer,
    GuestStar,
    Conductor,
    Lyricist,
    Arranger,
    Unknown,
}

data object WirePersonKindSerializer : UnknownTolerantEnumSerializer<WirePersonKind>(
    values = WirePersonKind.entries.toTypedArray(),
    unknownValue = WirePersonKind.Unknown,
) {
    override val serialName: String = "PersonKind"
}

@Serializable(with = MediaStreamTypeSerializer::class)
enum class MediaStreamType {
    Video,
    Audio,
    Subtitle,
    Data,
    EmbeddedImage,
    Lyric,
    Unknown,
}

data object MediaStreamTypeSerializer : UnknownTolerantEnumSerializer<MediaStreamType>(
    values = MediaStreamType.entries.toTypedArray(),
    unknownValue = MediaStreamType.Unknown,
) {
    override val serialName: String = "MediaStreamType"
}

@Serializable(with = MediaProtocolSerializer::class)
enum class MediaProtocol {
    File,
    Http,
    Rtmp,
    Rtp,
    Ftp,
    Rtsp,
    Unknown,
}

data object MediaProtocolSerializer : UnknownTolerantEnumSerializer<MediaProtocol>(
    values = MediaProtocol.entries.toTypedArray(),
    unknownValue = MediaProtocol.Unknown,
) {
    override val serialName: String = "MediaProtocol"
}

/** How the client is (or is about to be) consuming the stream. */
@Serializable(with = WirePlayMethodSerializer::class)
enum class WirePlayMethod {
    Transcode,
    DirectPlay,
    DirectStream,
    Unknown,
}

data object WirePlayMethodSerializer : UnknownTolerantEnumSerializer<WirePlayMethod>(
    values = WirePlayMethod.entries.toTypedArray(),
    unknownValue = WirePlayMethod.Unknown,
) {
    override val serialName: String = "PlayMethod"
}

@Serializable(with = PlaybackErrorCodeSerializer::class)
enum class PlaybackErrorCode {
    NotAllowed,
    NoCompatibleStream,
    RateLimitExceeded,
    Unknown,
}

data object PlaybackErrorCodeSerializer : UnknownTolerantEnumSerializer<PlaybackErrorCode>(
    values = PlaybackErrorCode.entries.toTypedArray(),
    unknownValue = PlaybackErrorCode.Unknown,
) {
    override val serialName: String = "PlaybackErrorCode"
}

/** Image types used when building image URLs (also an OpenAPI enum). */
enum class ImageType {
    Primary,
    Art,
    Backdrop,
    Banner,
    Logo,
    Thumb,
    Disc,
    Box,
    Screenshot,
}

/** Item fields requested via the `Fields` query parameter. */
enum class ItemField(val wireName: String) {
    Overview("Overview"),
    Genres("Genres"),
    People("People"),
    MediaSources("MediaSources"),
    PrimaryImageAspectRatio("PrimaryImageAspectRatio"),
    ProductionYear("ProductionYear"),
    PremiereDate("PremiereDate"),
    DateCreated("DateCreated"),
    Runtime("Runtime"),
    ChildCount("ChildCount"),
}
