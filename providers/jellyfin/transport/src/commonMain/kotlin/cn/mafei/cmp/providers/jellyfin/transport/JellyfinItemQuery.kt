package cn.mafei.cmp.providers.jellyfin.transport

import cn.mafei.cmp.providers.jellyfin.wire.ItemField

/** Canonical Jellyfin sort fields (wire names for the `SortBy` parameter). */
enum class JellyfinSortBy(val wireName: String) {
    SortName("SortName"),
    DateCreated("DateCreated"),
    DatePlayed("DatePlayed"),
    PremiereDate("PremiereDate"),
    CommunityRating("CommunityRating"),
    Runtime("Runtime"),
    ParentIndexNumber("ParentIndexNumber"),
    IndexNumber("IndexNumber"),
}

enum class JellyfinSortOrder(val wireName: String) {
    Ascending("Ascending"),
    Descending("Descending"),
}

enum class JellyfinItemFilter(val wireName: String) {
    IsFolder("IsFolder"),
    IsNotFolder("IsNotFolder"),
    IsPlayed("IsPlayed"),
    IsUnplayed("IsUnplayed"),
    IsFavorite("IsFavorite"),
    IsResumable("IsResumable"),
    Likes("Likes"),
    Dislikes("Dislikes"),
    IsFavoriteOrLikes("IsFavoriteOrLikes"),
}

/**
 * Parameters of the GET /Items endpoint, kept Jellyfin-shaped: translating
 * `core:domain` page/sort/filter contracts is the data adapter's job (GLM-04).
 */
data class JellyfinItemQuery(
    val userId: String,
    val parentId: String? = null,
    val personIds: List<String> = emptyList(),
    val startIndex: Int = 0,
    val limit: Int = 50,
    val includeItemTypes: List<String> = emptyList(),
    val fields: List<ItemField> = emptyList(),
    val sortBy: List<JellyfinSortBy> = listOf(JellyfinSortBy.SortName),
    val sortOrder: JellyfinSortOrder = JellyfinSortOrder.Ascending,
    val recursive: Boolean? = null,
    val searchTerm: String? = null,
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val filters: List<JellyfinItemFilter> = emptyList(),
    val imageTypeLimit: Int = 1,
)
