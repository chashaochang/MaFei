package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaId

/** Music queries have different ownership and ordering than a generic folder listing. */
sealed interface MusicCatalogQuery {
    data class Albums(val libraryId: CollectionId) : MusicCatalogQuery
    data class Artists(val libraryId: CollectionId) : MusicCatalogQuery
    data class Tracks(val libraryId: CollectionId) : MusicCatalogQuery
    data class AlbumTracks(val albumId: MediaId) : MusicCatalogQuery
    data class ArtistAlbums(val artistId: MediaId) : MusicCatalogQuery
    data class ArtistPopularTracks(val artistId: MediaId) : MusicCatalogQuery
}
