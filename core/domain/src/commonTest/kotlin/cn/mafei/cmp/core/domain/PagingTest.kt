package cn.mafei.cmp.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class PagingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun emptyPageHasNoMore() {
        val page = Page.empty<String>()
        assertTrue(page.items.isEmpty())
        assertTrue(page.isEmpty)
        assertFalse(page.hasMore)
        assertNull(page.nextCursor)
    }

    @Test
    fun pageWithCursorHasMore() {
        val page = Page(items = listOf("a", "b"), nextCursor = PageCursor("next-1"), totalEstimate = 20)
        assertFalse(page.isEmpty)
        assertTrue(page.hasMore)
        assertEquals("next-1", page.nextCursor?.value)
    }

    @Test
    fun pageRoundTripsThroughJson() {
        val page = Page(
            items = listOf(
                MediaItem(id = MediaId("m-1"), serverId = ServerId("srv-1"), provider = ProviderKind.Jellyfin, title = "One"),
                MediaItem(id = MediaId("m-2"), serverId = ServerId("srv-1"), provider = ProviderKind.Jellyfin, title = "Two"),
            ),
            nextCursor = PageCursor("cursor-9"),
            totalEstimate = 42,
        )

        val encoded = json.encodeToString(Page.serializer(MediaItem.serializer()), page)
        val decoded = json.decodeFromString(Page.serializer(MediaItem.serializer()), encoded)
        assertEquals(page, decoded)
    }

    @Test
    fun pageDecodesOmittedOptionalFields() {
        val decoded = json.decodeFromString(
            Page.serializer(String.serializer()),
            """{"items":["x"]}""",
        )
        assertEquals(listOf("x"), decoded.items)
        assertFalse(decoded.hasMore)
    }

    @Test
    fun pageCursorSerializesAsPlainString() {
        val encoded = json.encodeToString(PageCursor.serializer(), PageCursor("abc"))
        assertEquals("\"abc\"", encoded)
        assertEquals(PageCursor("abc"), json.decodeFromString(PageCursor.serializer(), encoded))
    }

    @Test
    fun pageRequestDefaultsPageSize() {
        assertEquals(50, PageRequest().pageSize)
        assertEquals(1, PageRequest(pageSize = 1).pageSize)
    }

    @Test
    fun pageRequestRejectsIllegalPageSize() {
        assertFailsWith<IllegalArgumentException> { PageRequest(pageSize = 0) }
        assertFailsWith<IllegalArgumentException> { PageRequest(pageSize = -5) }
        assertFailsWith<IllegalArgumentException> { PageRequest(pageSize = 501) }
    }

    @Test
    fun mediaFilterReportsEmptiness() {
        assertTrue(MediaFilter().isEmpty)
        assertFalse(MediaFilter(mediaTypes = setOf(MediaType.Movie)).isEmpty)
        assertFalse(MediaFilter(searchTerm = "batman").isEmpty)
        assertFalse(MediaFilter(favoritesOnly = true).isEmpty)
    }

    @Test
    fun mediaSortAndFilterRoundTrip() {
        val request = PageRequest(
            cursor = PageCursor("c"),
            pageSize = 25,
            sort = MediaSort(field = MediaSortField.DateAdded, direction = SortDirection.Descending),
            filter = MediaFilter(
                mediaTypes = setOf(MediaType.Series),
                genres = setOf("Drama"),
                years = setOf(2023),
                favoritesOnly = true,
            ),
        )
        val encoded = json.encodeToString(PageRequest.serializer(), request)
        assertEquals(request, json.decodeFromString(PageRequest.serializer(), encoded))
    }
}
