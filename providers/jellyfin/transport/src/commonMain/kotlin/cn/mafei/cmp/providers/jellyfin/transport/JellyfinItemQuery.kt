package cn.mafei.cmp.providers.jellyfin.transport

import cn.mafei.cmp.providers.jellyfin.wire.ItemField

/** Canonical Jellyfin sort fields (wire names for the `SortBy` parameter). */
enum class JellyfinSortBy(val wireName: String) {
    SortName("SortName"),
    DateCreated("DateCreated"),
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
    IsPlayed("IsPlayed"),
    IsUnplayed("IsUnplayed"),
    IsFavorite("IsFavorite"),
    IsNotFavorite("IsNotFavorite"),
    IsResumable("IsResumable"),
}

/**
 * Parameters of the GET /Items endpoint, kept Jellyfin-shaped: translating
 * `core:domain` page/sort/filter contracts is the data adapter's job (GLM-04).
 */
data class JellyfinItemQuery(
    val userId: String,
    val parentId: String? = null,
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
