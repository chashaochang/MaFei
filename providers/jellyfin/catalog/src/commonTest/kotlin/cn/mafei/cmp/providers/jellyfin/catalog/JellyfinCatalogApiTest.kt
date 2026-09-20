package cn.mafei.cmp.providers.jellyfin.catalog

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSession
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import cn.mafei.cmp.providers.jellyfin.wire.ItemKind
import cn.mafei.cmp.providers.jellyfin.wire.ItemField
import cn.mafei.cmp.providers.jellyfin.wire.WirePersonKind
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun runSuspendTest(block: suspend () -> Unit) {
    val result = block.startCoroutineUninterceptedOrReturn(NoopContinuation)
    check(result !== COROUTINE_SUSPENDED) { "test body suspended unexpectedly" }
}

private object NoopContinuation : Continuation<Unit> {
    override val context = EmptyCoroutineContext
    override fun resumeWith(result: Result<Unit>) {
        result.exceptionOrNull()?.let { throw it }
    }
}

class JellyfinCatalogApiTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private companion object {
        val base: HttpUrl = HttpUrl.parse(JellyfinFixtures.BASE_URL)
    }

    private class Fixture {
        val transport = FakeJellyfinTransport()
        val client = JellyfinClient(
            JellyfinServerConfig(base, "MaFei", "Test", "device-1", "2.0.0"),
            transport,
            InMemoryJellyfinSessionState(JellyfinSession("u1", "tok")),
        )
        val api = DefaultJellyfinCatalogApi(client)
    }

    @Test
    fun viewsTolerateUnknownCollectionTypeAndFutureFields() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.VIEWS, base)

        val views = fixture.api.views("u1")
        assertEquals(3, views.items.size)
        assertEquals("movies", views.items[0].collectionType)
        assertEquals(42, views.items[0].childCount)
        // unknown enum value and unknown fields survive decoding
        assertEquals("HolographicLibrary", views.items[2].collectionType)
        assertEquals(ItemKind.UserView, views.items[2].type)
    }

    @Test
    fun itemPageFallsBackToUnknownItemKind() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.ITEMS_PAGE, base)

        val page = fixture.api.items(
            cn.mafei.cmp.providers.jellyfin.transport.JellyfinItemQuery(userId = "u1", parentId = "v1", startIndex = 20, limit = 30),
        )
        assertEquals(3, page.totalRecordCount)
        assertEquals(ItemKind.Movie, page.items[0].type)
        assertEquals(true, page.items[0].userData?.isFavorite)
        assertEquals(ItemKind.Series, page.items[1].type)
        assertEquals(ItemKind.Unknown, page.items[2].type, "unknown server type must fall back, not fail")

        val sent = fixture.transport.requests.single()
        val query = sent.url.queryParameters
        assertEquals("20", query.first("startIndex"))
        assertEquals("30", query.first("limit"))
        assertEquals("v1", query.first("parentId"))
    }

    @Test
    fun homeEndpointsUseOfficialPathsAndLowerCamelQueries() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, """[{"Items":[],"RecommendationType":"SimilarToRecentlyPlayed"}]""", base)
        fixture.transport.enqueueJson(200, """{"Items":[],"TotalRecordCount":0,"StartIndex":0}""", base)
        fixture.transport.enqueueJson(200, """{"Items":[],"TotalRecordCount":0,"StartIndex":0}""", base)
        fixture.transport.enqueueJson(200, "[]", base)

        fixture.api.recommendations("u1", categoryLimit = 4, itemLimit = 4)
        fixture.api.resume("u1", limit = 12, includeItemTypes = listOf("Movie", "Episode", "Video"))
        fixture.api.nextUp("u1", limit = 24)
        fixture.api.latest("u1", limit = 50, includeItemTypes = listOf("Movie", "Series", "Episode", "Video"))

        val requests = fixture.transport.requests
        assertTrue(requests[0].url.encodedPath.endsWith("/Movies/Recommendations"))
        assertEquals("4", requests[0].url.queryParameters.first("categoryLimit"))
        assertTrue(requests[1].url.encodedPath.endsWith("/UserItems/Resume"))
        assertEquals("Movie,Episode,Video", requests[1].url.queryParameters.first("includeItemTypes"))
        assertTrue(requests[2].url.encodedPath.endsWith("/Shows/NextUp"))
        assertEquals("false", requests[2].url.queryParameters.first("enableResumable"))
        assertTrue(requests[3].url.encodedPath.endsWith("/Items/Latest"))
        assertEquals("true", requests[3].url.queryParameters.first("groupItems"))
    }

    @Test
    fun perSeriesNextUpKeepsTheDetailPageSelectionContract() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, """{"Items":[],"TotalRecordCount":0,"StartIndex":0}""", base)

        fixture.api.nextUp(
            userId = "u1",
            limit = 1,
            seriesId = "series-1",
            enableResumable = true,
            enableRewatching = true,
        )

        val query = fixture.transport.requests.single().url.queryParameters
        assertEquals("series-1", query.first("seriesId"))
        assertEquals("true", query.first("enableResumable"))
        assertEquals("true", query.first("enableRewatching"))
    }

    @Test
    fun itemDetailDecodesOffsetIsoDateAndPeople() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.SERIES_DETAIL, base)

        val item = fixture.api.item("u1", "s0102030405060708")
        assertEquals("Fixture Series", item.name)
        // 2023-04-01T20:00:00-05:00 == 2023-04-02T01:00:00Z
        assertEquals(1_680_397_200_000L, item.premiereDate)
        assertEquals(2, item.people.size)
        assertEquals(WirePersonKind.Director, item.people[0].type)
        assertEquals("1111111122222222", item.imageTags["Primary"])
    }

    @Test
    fun seasonsAndEpisodesBindPagedRequests() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.SEASONS, base)
        fixture.transport.enqueueJson(200, JellyfinFixtures.EPISODES, base)

        val seasons = fixture.api.seasons("u1", "s1", fields = listOf(ItemField.Overview))
        assertEquals(2, seasons.items.size)

        val episodes = fixture.api.episodes("u1", "s1", seasonId = "se1", startIndex = 0, limit = 50)
        assertEquals(2, episodes.items.size)
        assertEquals("Pilot", episodes.items[0].name)
        assertEquals(15_000L, episodes.items[0].runTimeTicks?.let { it / 1_000_000L }) // 1.5min in ticks sanity

        val episodeRequest = fixture.transport.requests[1]
        assertEquals("se1", episodeRequest.url.queryParameters.first("seasonId"))
        assertTrue(episodeRequest.url.encodedPath.contains("/Shows/s1/Episodes"))
    }
}
