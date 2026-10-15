package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.data.adapter_contract.MusicCatalogQuery
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinRequests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JellyfinMusicQueryTest {
    private fun params(query: MusicCatalogQuery) = JellyfinRequests.items(
        HttpUrl.parse("https://example.test/jellyfin"), query.toJellyfinQuery("user", 40, 40),
    ).url.queryParameters

    @Test fun librarySectionsUseLegacyTypesAndMultiKeyOrder() {
        val library = CollectionId("library")
        for ((query, type, sort) in listOf(
            Triple(MusicCatalogQuery.Albums(library), "MusicAlbum", "SortName"),
            Triple(MusicCatalogQuery.Artists(library), "MusicArtist", "SortName"),
            Triple(MusicCatalogQuery.Tracks(library), "Audio", "AlbumArtist,Album,ParentIndexNumber,IndexNumber"),
        )) {
            val p = params(query)
            assertEquals("library", p.first("parentId"))
            assertEquals(type, p.first("includeItemTypes"))
            assertEquals(sort, p.first("sortBy"))
            assertEquals(sort.split(',').joinToString(",") { "Ascending" }, p.first("sortOrder"))
            assertEquals("40", p.first("startIndex"))
            assertEquals("true", p.first("recursive"))
            assertEquals("ChildCount,RecursiveItemCount,CumulativeRunTimeTicks,Overview,ParentId,PrimaryImageAspectRatio,SortName", p.first("fields"))
        }
    }

    @Test fun detailQueriesUseAlbumAndArtistOwnershipNotParentFolders() {
        val album = params(MusicCatalogQuery.AlbumTracks(MediaId("album")))
        assertEquals("album", album.first("albumIds"))
        assertNull(album.first("parentId"))
        assertEquals("ParentIndexNumber,IndexNumber,SortName", album.first("sortBy"))
        val albums = params(MusicCatalogQuery.ArtistAlbums(MediaId("artist")))
        assertEquals("artist", albums.first("albumArtistIds"))
        assertNull(albums.first("artistIds"))
        val popular = params(MusicCatalogQuery.ArtistPopularTracks(MediaId("artist")))
        assertEquals("artist", popular.first("artistIds"))
        assertEquals("PlayCount,SortName", popular.first("sortBy"))
        assertEquals("Descending,Ascending", popular.first("sortOrder"))
    }
}
