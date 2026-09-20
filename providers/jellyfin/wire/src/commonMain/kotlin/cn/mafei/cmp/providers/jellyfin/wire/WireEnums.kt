package cn.mafei.cmp.providers.jellyfin.wire

import cn.mafei.cmp.core.serialization.UnknownTolerantEnumSerializer
import kotlinx.serialization.Serializable

/**
 * Jellyfin wire enums for the P0 surface, modeled on the official
 * jellyfin-sdk-kotlin v1.6.8 OpenAPI (Jellyfin 10.10.7). Every enum tolerates
 * unknown server-side additions by falling back to an `Unknown` constant.
 */
@Serializable(with = ItemKindSerializer::class)
enum class ItemKind {
    AggregateFolder,
    Movie,
    AudioBook,
    BasePluginFolder,
    Book,
    Series,
    Season,
    Episode,
    BoxSet,
    Channel,
    ChannelFolderItem,
    CollectionFolder,
    Folder,
    Genre,
    ManualPlaylistsFolder,
    LiveTvChannel,
    LiveTvProgram,
    UserView,
    MusicArtist,
    MusicAlbum,
    MusicGenre,
    Audio,
    MusicVideo,
    Person,
    PhotoAlbum,
    Photo,
    Video,
    Playlist,
    PlaylistsFolder,
    Program,
    Recording,
    Studio,
    Trailer,
    TvChannel,
    TvProgram,
    UserRootFolder,
    Year,
    Unknown,
}

data object ItemKindSerializer : UnknownTolerantEnumSerializer<ItemKind>(
    values = ItemKind.entries.toTypedArray(),
    unknownValue = ItemKind.Unknown,
) {
    override val serialName: String = "BaseItemKind"
}

@Serializable(with = WirePersonKindSerializer::class)
enum class WirePersonKind {
    Unknown,
    Actor,
    Director,
    Writer,
    Producer,
    Composer,
    GuestStar,
    Conductor,
    Lyricist,
    Arranger,
    Engineer,
    Mixer,
    Remixer,
    Creator,
    Artist,
    AlbumArtist,
    Author,
    Illustrator,
    Penciller,
    Inker,
    Colorist,
    Letterer,
    CoverArtist,
    Editor,
    Translator,
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
    Rtsp,
    Udp,
    Rtp,
    Ftp,
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

@Serializable(with = WireRepeatModeSerializer::class)
enum class WireRepeatMode {
    RepeatNone,
    RepeatAll,
    RepeatOne,
    Unknown,
}

data object WireRepeatModeSerializer : UnknownTolerantEnumSerializer<WireRepeatMode>(
    values = WireRepeatMode.entries.toTypedArray(),
    unknownValue = WireRepeatMode.Unknown,
) {
    override val serialName: String = "RepeatMode"
}

@Serializable(with = WirePlaybackOrderSerializer::class)
enum class WirePlaybackOrder {
    Default,
    Shuffle,
    Unknown,
}

data object WirePlaybackOrderSerializer : UnknownTolerantEnumSerializer<WirePlaybackOrder>(
    values = WirePlaybackOrder.entries.toTypedArray(),
    unknownValue = WirePlaybackOrder.Unknown,
) {
    override val serialName: String = "PlaybackOrder"
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
    Menu,
    Chapter,
    BoxRear,
    Profile,
}

/** Item fields requested via the official `fields` query parameter. */
enum class ItemField(val wireName: String) {
    Overview("Overview"),
    Genres("Genres"),
    People("People"),
    MediaStreams("MediaStreams"),
    MediaSources("MediaSources"),
    PrimaryImageAspectRatio("PrimaryImageAspectRatio"),
    ProductionYear("ProductionYear"),
    PremiereDate("PremiereDate"),
    DateCreated("DateCreated"),
    Runtime("Runtime"),
    ItemCounts("ItemCounts"),
    ChildCount("ChildCount"),
}

// ---------------------------------------------------------------------------
// DeviceProfile support enums (P0 subset)
// ---------------------------------------------------------------------------

@Serializable(with = DlnaProfileTypeSerializer::class)
enum class DlnaProfileType {
    Audio,
    Video,
    Photo,
    Subtitle,
    Lyric,
    Unknown,
}

data object DlnaProfileTypeSerializer : UnknownTolerantEnumSerializer<DlnaProfileType>(
    values = DlnaProfileType.entries.toTypedArray(),
    unknownValue = DlnaProfileType.Unknown,
) {
    override val serialName: String = "DlnaProfileType"
}

@Serializable(with = CodecTypeSerializer::class)
enum class CodecType {
    Video,
    VideoAudio,
    Audio,
    Subtitle,
    EmbeddedImage,
    Unknown,
}

data object CodecTypeSerializer : UnknownTolerantEnumSerializer<CodecType>(
    values = CodecType.entries.toTypedArray(),
    unknownValue = CodecType.Unknown,
) {
    override val serialName: String = "CodecType"
}

@Serializable(with = SubtitleDeliveryMethodSerializer::class)
enum class SubtitleDeliveryMethod {
    Encode,
    Embed,
    External,
    Hls,
    Drop,
    Unknown,
}

data object SubtitleDeliveryMethodSerializer : UnknownTolerantEnumSerializer<SubtitleDeliveryMethod>(
    values = SubtitleDeliveryMethod.entries.toTypedArray(),
    unknownValue = SubtitleDeliveryMethod.Unknown,
) {
    override val serialName: String = "SubtitleDeliveryMethod"
}

@Serializable(with = EncodingContextSerializer::class)
enum class EncodingContext {
    Streaming,
    Static,
    Unknown,
}

data object EncodingContextSerializer : UnknownTolerantEnumSerializer<EncodingContext>(
    values = EncodingContext.entries.toTypedArray(),
    unknownValue = EncodingContext.Unknown,
) {
    override val serialName: String = "EncodingContext"
}
