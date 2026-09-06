package cn.mafei.cmp.providers.jellyfin.wire

import cn.mafei.cmp.core.serialization.IsoInstantEpochMsSerializer
import kotlinx.serialization.Serializable

/** Subset of the Jellyfin user-item data (favorites/progress). */
@Serializable
data class UserItemDataDto(
    val rating: Double? = null,
    val playedPercentage: Double? = null,
    val unplayedItemCount: Int? = null,
    val playbackPositionTicks: Long? = null,
    val playCount: Int? = null,
    val isFavorite: Boolean? = null,
    val played: Boolean? = null,
    @Serializable(with = IsoInstantEpochMsSerializer::class) val lastPlayedDate: Long? = null,
)

/** Subset of the Jellyfin person reference inside a base item. */
@Serializable
data class PersonDto(
    val name: String? = null,
    val role: String? = null,
    val type: WirePersonKind? = null,
    val primaryImageTag: String? = null,
)

/**
 * P0 subset of Jellyfin `BaseItemDto`. Everything the server adds beyond this
 * subset is ignored on decode (frozen MafeiJson policy) and must be added
 * here deliberately.
 */
@Serializable
data class BaseItemDto(
    val id: String? = null,
    val name: String? = null,
    val originalTitle: String? = null,
    val type: ItemKind? = null,
    val overview: String? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val genres: List<String> = emptyList(),
    val people: List<PersonDto> = emptyList(),
    val studios: List<NameLongIdPairDto> = emptyList(),
    val productionYear: Int? = null,
    @Serializable(with = IsoInstantEpochMsSerializer::class) val premiereDate: Long? = null,
    @Serializable(with = IsoInstantEpochMsSerializer::class) val dateCreated: Long? = null,
    val runTimeTicks: Long? = null,
    val childCount: Int? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonId: String? = null,
    val seasonName: String? = null,
    val collectionType: String? = null,
    val imageTags: Map<String, String> = emptyMap(),
    val backdropImageTags: List<String> = emptyList(),
    val userData: UserItemDataDto? = null,
    val mediaSources: List<MediaSourceInfoDto> = emptyList(),
)

/** Jellyfin `NameLongIdPair` (studios etc.). */
@Serializable
data class NameLongIdPairDto(
    val name: String? = null,
    val id: String? = null,
)
