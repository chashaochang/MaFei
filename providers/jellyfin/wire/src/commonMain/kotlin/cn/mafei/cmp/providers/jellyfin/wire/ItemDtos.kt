package cn.mafei.cmp.providers.jellyfin.wire

import cn.mafei.cmp.core.serialization.IsoInstantEpochMsSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Subset of the Jellyfin user-item data (favorites/progress). */
@Serializable
data class UserItemDataDto(
    @SerialName("Rating") val rating: Double? = null,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null,
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long? = null,
    @SerialName("PlayCount") val playCount: Int? = null,
    @SerialName("IsFavorite") val isFavorite: Boolean? = null,
    @SerialName("Played") val played: Boolean? = null,
    @SerialName("LastPlayedDate") @Serializable(with = IsoInstantEpochMsSerializer::class) val lastPlayedDate: Long? = null,
)

/** Subset of the Jellyfin person reference inside a base item. */
@Serializable
data class PersonDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: WirePersonKind? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

/** Jellyfin `NameLongIdPair` (studios etc.). */
@Serializable
data class NameLongIdPairDto(
    @SerialName("Name") val name: String? = null,
    @SerialName("Id") val id: String? = null,
)

/** Jellyfin `ExternalUrl` (site name + link, e.g. IMDb/TMDB). */
@Serializable
data class ExternalUrlDto(
    @SerialName("Name") val name: String? = null,
    @SerialName("Url") val url: String? = null,
)

/** Jellyfin's per-media-source trickplay manifest (tile-sheet metadata). */
@Serializable
data class TrickplayInfoDto(
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("Interval") val intervalMs: Long? = null,
    @SerialName("ThumbnailCount") val thumbnailCount: Int? = null,
    @SerialName("TileWidth") val tileWidth: Int? = null,
    @SerialName("TileHeight") val tileHeight: Int? = null,
)

/**
 * P0 subset of Jellyfin `BaseItemDto` (OpenAPI 10.10.7 names). Everything the
 * server adds beyond this subset is ignored on decode (frozen MafeiJson
 * policy) and must be added here deliberately.
 */
@Serializable
data class BaseItemDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("SortName") val sortName: String? = null,
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("AlbumArtist") val albumArtist: String? = null,
    @SerialName("Artists") val artists: List<String> = emptyList(),
    @SerialName("ArtistItems") val artistItems: List<NameLongIdPairDto> = emptyList(),
    @SerialName("AlbumArtists") val albumArtists: List<NameLongIdPairDto> = emptyList(),
    @SerialName("AlbumPrimaryImageTag") val albumPrimaryImageTag: String? = null,
    @SerialName("Type") val type: ItemKind? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("People") val people: List<PersonDto> = emptyList(),
    @SerialName("Studios") val studios: List<NameLongIdPairDto> = emptyList(),
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("PremiereDate") @Serializable(with = IsoInstantEpochMsSerializer::class) val premiereDate: Long? = null,
    @SerialName("DateCreated") @Serializable(with = IsoInstantEpochMsSerializer::class) val dateCreated: Long? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("CumulativeRunTimeTicks") val cumulativeRunTimeTicks: Long? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("RecursiveItemCount") val recursiveItemCount: Int? = null,
    @SerialName("MovieCount") val movieCount: Int? = null,
    @SerialName("SeriesCount") val seriesCount: Int? = null,
    @SerialName("EpisodeCount") val episodeCount: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    @SerialName("ParentThumbItemId") val parentThumbItemId: String? = null,
    @SerialName("ParentThumbImageTag") val parentThumbImageTag: String? = null,
    @SerialName("ParentPrimaryImageItemId") val parentPrimaryImageItemId: String? = null,
    @SerialName("ParentPrimaryImageTag") val parentPrimaryImageTag: String? = null,
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropImageTags: List<String> = emptyList(),
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("UserData") val userData: UserItemDataDto? = null,
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto> = emptyList(),
    @SerialName("MediaSources") val mediaSources: List<MediaSourceInfoDto> = emptyList(),
    @SerialName("ExternalUrls") val externalUrls: List<ExternalUrlDto> = emptyList(),
    /** source id -> generated width -> tile manifest */
    @SerialName("Trickplay") val trickplay: Map<String, Map<String, TrickplayInfoDto>> = emptyMap(),
    @SerialName("ChannelNumber") val channelNumber: String? = null,
    @SerialName("CurrentProgram") val currentProgram: LiveProgramDto? = null,
    @SerialName("CanDownload") val canDownload: Boolean? = null,
    @SerialName("ParentId") val parentId: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("CameraMake") val cameraMake: String? = null,
    @SerialName("CameraModel") val cameraModel: String? = null,
    @SerialName("Software") val software: String? = null,
    @SerialName("ExposureTime") val exposureTime: Double? = null,
    @SerialName("FocalLength") val focalLength: Double? = null,
    @SerialName("ImageOrientation") val imageOrientation: String? = null,
    @SerialName("Aperture") val aperture: Double? = null,
    @SerialName("ShutterSpeed") val shutterSpeed: Double? = null,
    @SerialName("Latitude") val latitude: Double? = null,
    @SerialName("Longitude") val longitude: Double? = null,
    @SerialName("Altitude") val altitude: Double? = null,
    @SerialName("IsoSpeedRating") val isoSpeedRating: Double? = null,
    @kotlinx.serialization.Transient val premiereDateIso: String? = null,
    @kotlinx.serialization.Transient val dateCreatedIso: String? = null,
)

@Serializable
data class LiveProgramDto(
    @SerialName("Name") val name: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("StartDate") @Serializable(with = IsoInstantEpochMsSerializer::class) val startDate: Long? = null,
    @SerialName("EndDate") @Serializable(with = IsoInstantEpochMsSerializer::class) val endDate: Long? = null,
)

/** One category returned by GET /Movies/Recommendations. */
@Serializable
data class RecommendationDto(
    @SerialName("Items") val items: List<BaseItemDto> = emptyList(),
    @SerialName("RecommendationType") val recommendationType: String? = null,
    @SerialName("BaselineItemName") val baselineItemName: String? = null,
    @SerialName("CategoryId") val categoryId: String? = null,
)
