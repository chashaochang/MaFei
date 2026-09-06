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

/**
 * P0 subset of Jellyfin `BaseItemDto` (OpenAPI 10.10.7 names). Everything the
 * server adds beyond this subset is ignored on decode (frozen MafeiJson
 * policy) and must be added here deliberately.
 */
@Serializable
data class BaseItemDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("OriginalTitle") val originalTitle: String? = null,
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
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("UserData") val userData: UserItemDataDto? = null,
    @SerialName("MediaSources") val mediaSources: List<MediaSourceInfoDto> = emptyList(),
)
