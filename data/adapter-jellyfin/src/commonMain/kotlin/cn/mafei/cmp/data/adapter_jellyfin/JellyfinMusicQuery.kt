package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.data.adapter_contract.MusicCatalogQuery
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinItemQuery
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinSortBy
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinSortOrder
import cn.mafei.cmp.providers.jellyfin.wire.ItemField

internal fun MusicCatalogQuery.libraryIdOrNull(): CollectionId? = when (this) {
    is MusicCatalogQuery.Albums -> libraryId
    is MusicCatalogQuery.Artists -> libraryId
    is MusicCatalogQuery.Tracks -> libraryId
    else -> null
}

/** Mechanical translation of MusicRepository/MusicQueryPlanner in the ArkTS reference. */
internal fun MusicCatalogQuery.toJellyfinQuery(userId: String, offset: Int, limit: Int): JellyfinItemQuery {
    val sort = when (this) {
        is MusicCatalogQuery.Tracks -> listOf(JellyfinSortBy.AlbumArtist, JellyfinSortBy.Album,
            JellyfinSortBy.ParentIndexNumber, JellyfinSortBy.IndexNumber)
        is MusicCatalogQuery.AlbumTracks -> listOf(JellyfinSortBy.ParentIndexNumber,
            JellyfinSortBy.IndexNumber, JellyfinSortBy.SortName)
        is MusicCatalogQuery.ArtistPopularTracks -> listOf(JellyfinSortBy.PlayCount, JellyfinSortBy.SortName)
        else -> listOf(JellyfinSortBy.SortName)
    }
    return JellyfinItemQuery(
        userId = userId,
        parentId = libraryIdOrNull()?.value,
        startIndex = offset,
        limit = limit,
        includeItemTypes = listOf(when (this) {
            is MusicCatalogQuery.Albums, is MusicCatalogQuery.ArtistAlbums -> "MusicAlbum"
            is MusicCatalogQuery.Artists -> "MusicArtist"
            else -> "Audio"
        }),
        recursive = true,
        fields = listOf(ItemField.ChildCount, ItemField.RecursiveItemCount, ItemField.CumulativeRunTimeTicks,
            ItemField.Overview, ItemField.ParentId, ItemField.PrimaryImageAspectRatio, ItemField.SortName),
        sortBy = sort,
        sortOrders = sort.mapIndexed { index, _ ->
            if (this is MusicCatalogQuery.ArtistPopularTracks && index == 0) JellyfinSortOrder.Descending
            else JellyfinSortOrder.Ascending
        },
        albumIds = if (this is MusicCatalogQuery.AlbumTracks) listOf(albumId.value) else emptyList(),
        albumArtistIds = if (this is MusicCatalogQuery.ArtistAlbums) listOf(artistId.value) else emptyList(),
        artistIds = if (this is MusicCatalogQuery.ArtistPopularTracks) listOf(artistId.value) else emptyList(),
    )
}
