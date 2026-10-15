package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLatestQueryTest {
    private fun collection(type: MediaType) = MediaCollection(
        CollectionId("library"), ServerId("server"), ProviderKind.Jellyfin, "媒体库",
        mediaTypes = setOf(type),
    )

    @Test fun moreKeepsTheShelfsMediaTypesAndNewestFirstOrder() {
        for (type in listOf(MediaType.Movie, MediaType.Series, MediaType.Audio, MediaType.Photo)) {
            val request = collection(type).homeLatestRequest(PageRequest(
                sort = MediaSort(MediaSortField.Title, SortDirection.Ascending),
                filter = MediaFilter(favoritesOnly = true),
            ))
            assertEquals(MediaSort(MediaSortField.DateAdded, SortDirection.Descending), request.sort)
            assertEquals(MediaFilter(mediaTypes = setOf(type)), request.filter)
        }
    }

    @Test fun morePreservesItsPagingCursorAndPageSize() {
        val page = PageRequest(cursor = PageCursor("v1:off:30"), pageSize = 60)
        val request = collection(MediaType.Movie).homeLatestRequest(page)
        assertEquals(page.cursor, request.cursor)
        assertEquals(page.pageSize, request.pageSize)
    }

    @Test fun defaultRequestMatchesTheHomeShelf() {
        val request = collection(MediaType.Series).homeLatestRequest()
        assertEquals(30, request.pageSize)
        assertEquals(null, request.cursor)
    }
}
