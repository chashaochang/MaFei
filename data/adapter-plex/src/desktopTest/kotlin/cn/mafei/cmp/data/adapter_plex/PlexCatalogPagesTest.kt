package cn.mafei.cmp.data.adapter_plex

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.plex.core.PlexClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class PlexCatalogPagesTest {
    private class Transport : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        var rows = mapOf<String, List<JsonElement>>()
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val query = request.url.queryParameters
            val all = rows[query.first("type")].orEmpty()
            val start = query.first("X-Plex-Container-Start")!!.toInt()
            val size = query.first("X-Plex-Container-Size")!!.toInt()
            val body = buildJsonObject {
                putJsonObject("MediaContainer") {
                    put("totalSize", all.size)
                    put("Metadata", JsonArray(all.drop(start).take(size)))
                }
            }.toString()
            return HttpResponse(200, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)
        }
        override fun close() = Unit
    }

    private val transport = Transport()
    private val adapter = PlexMediaProviderAdapter(
        PlexClient(HttpUrl.parse("https://media.test/plex"), "device-fixture", "token-fixture", transport),
        ServerId("server-fixture"), AccountId("account-fixture"),
    )
    private val history = PageRequest(
        pageSize = 2, sort = MediaSort(MediaSortField.DatePlayed, SortDirection.Descending),
        filter = MediaFilter(mediaTypes = setOf(MediaType.Movie, MediaType.Episode), playedOnly = true),
    )

    @Test fun watchedHistoryMergesTwoSortedTypesAcrossPagesWithoutLosingEitherPosition() = runBlocking {
        transport.rows = mapOf(
            "1" to listOf(row("1", "movie", 900), row("3", "movie", 700), row("5", "movie", 500)),
            "4" to listOf(row("2", "episode", 800), row("4", "episode", 600), row("6", "episode", 400)),
        )
        val first = adapter.items(null, history)
        val second = adapter.items(null, history.copy(cursor = assertNotNull(first.nextCursor)))
        val third = adapter.items(null, history.copy(cursor = assertNotNull(second.nextCursor)))
        assertEquals(listOf("1", "2", "3", "4", "5", "6"),
            (first.items + second.items + third.items).map { it.id.value })
        assertEquals(6, first.totalEstimate)
        assertNull(third.nextCursor)
        assertEquals(listOf("0", "0", "1", "1", "2", "2"),
            transport.requests.map { it.url.queryParameters.first("X-Plex-Container-Start") })
        transport.requests.forEach {
            assertEquals("0", it.url.queryParameters.first("unwatched"))
            assertEquals("lastViewedAt:desc", it.url.queryParameters.first("sort"))
        }
    }

    @Test fun oneExhaustedTypeDoesNotTruncateTheRemainingType() = runBlocking {
        transport.rows = mapOf(
            "1" to listOf(row("1", "movie", 900)),
            "4" to listOf(row("2", "episode", 800), row("3", "episode", 700), row("4", "episode", 600)),
        )
        val first = adapter.items(null, history)
        val second = adapter.items(null, history.copy(cursor = assertNotNull(first.nextCursor)))
        assertEquals(listOf("1", "2", "3", "4"), (first.items + second.items).map { it.id.value })
        assertNull(second.nextCursor)
    }

    @Test fun invalidRawRowsAdvanceOffsetsAndCannotHideANewerHeadBehindTheFetchedWindow() = runBlocking {
        transport.rows = mapOf(
            "1" to listOf(JsonNull, JsonPrimitive("invalid"), row("1", "movie", 900)),
            "4" to listOf(row("2", "episode", 800)),
        )
        val first = adapter.items(null, history)
        assertTrue(first.items.isEmpty())
        val second = adapter.items(null, history.copy(cursor = assertNotNull(first.nextCursor)))
        assertEquals(listOf("1", "2"), second.items.map { it.id.value })
        assertEquals(listOf("0", "0", "2", "0"),
            transport.requests.map { it.url.queryParameters.first("X-Plex-Container-Start") })
        assertNull(second.nextCursor)
    }

    @Test fun defaultMergeRequestsTitleOrderAndBindsItsCursorToTheQueryAndCollection() = runBlocking {
        transport.rows = mapOf(
            "1" to listOf(row("1", "movie", 0, "A"), row("3", "movie", 0, "C")),
            "4" to listOf(row("2", "episode", 0, "B")),
        )
        val request = history.copy(sort = null)
        val first = adapter.items(null, request)
        assertEquals(listOf("1", "2"), first.items.map { it.id.value })
        assertTrue(transport.requests.all { it.url.queryParameters.first("sort") == "titleSort:asc" })
        val next = request.copy(cursor = assertNotNull(first.nextCursor))
        val before = transport.requests.size
        assertFailsWith<ClientConfigError> { adapter.items(CollectionId("5"), next) }
        assertFailsWith<ClientConfigError> { adapter.items(null, next.copy(sort = history.sort)) }
        assertFailsWith<ClientConfigError> {
            adapter.items(null, next.copy(filter = request.filter.copy(playedOnly = false)))
        }
        assertEquals(before, transport.requests.size)
        assertEquals(listOf("3"), adapter.items(null, next).items.map { it.id.value })
    }

    private fun row(id: String, type: String, viewed: Long, title: String = "Fixture $id"): JsonElement =
        MafeiJson.Default.parseToJsonElement("""{"ratingKey":"$id","type":"$type","title":"$title",
            "titleSort":"$title","lastViewedAt":$viewed,"viewCount":1}""")
}
