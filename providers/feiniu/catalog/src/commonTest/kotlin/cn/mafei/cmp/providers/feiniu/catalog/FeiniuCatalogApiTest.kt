package cn.mafei.cmp.providers.feiniu.catalog

import cn.mafei.cmp.core.http_contract.HttpBody
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.testkit.FakeFeiniuTransport
import cn.mafei.cmp.providers.feiniu.testkit.FeiniuFixtures
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

class FeiniuCatalogApiTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private class Fixture {
        val transport = FakeFeiniuTransport()
        val client = FeiniuClient(
            transport = transport,
            sessionState = cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState(FeiniuCredentials("tok", "Trim-MC-token=tok")),
        )
        val api = FeiniuCatalogApi(client)
        val resolution = FeiniuHostResolution(FeiniuHostCandidate(FeiniuFixtures.BASE_URL, FeiniuRoute.Direct))
    }

    @Test
    fun mediaLibrariesDecodeWithUnknownFieldTolerance() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.MEDIA_DB_LIST)
        val libraries = fixture.api.mediaLibraries(fixture.resolution)
        assertEquals(3, libraries.size)
        assertEquals("电影", libraries[0].title)
        assertEquals("movie", libraries[0].category)
        assertEquals(listOf("/p/1.jpg"), libraries[0].posters)
        // unknown category "hologram" and vendorExtra field are tolerated
        assertEquals("hologram", libraries[2].category)
    }

    @Test
    fun itemsSendPascalJsonBodyWithSnakeCaseKeysAndAuthHeaders() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.ITEM_LIST)

        val page = fixture.api.items(fixture.resolution, ancestorGuid = "lib-0001", category = "movie", page = 2, pageSize = 36)
        assertEquals(2, page.total)
        assertEquals("Fixture Movie", page.list[0].title)
        assertEquals(true, page.list[0].isFavorite == 1)
        assertEquals(0.5, page.list[0].mediaStream?.progress)

        val sent = fixture.transport.requests.single()
        assertEquals("https://feiniu.test/v/api/v1/item/list", sent.url.toEncodedString())
        val body = (sent.body as HttpBody.Text).text
        assertTrue(body.contains("\"ancestor_guid\":\"lib-0001\""), body)
        assertTrue(body.contains("\"tags\":{\"type\":[\"Movie\"]}"), body)
        assertTrue(body.contains("\"page\":2"), body)
        assertEquals("tok", sent.headers.first("Authorization"))
        assertTrue(sent.headers.first("Cookie")!!.contains("Trim-MC-token=tok"))
        assertTrue(sent.headers.first("Authx")!!.startsWith("nonce="))
        assertEquals("android-media", sent.headers.first("X-Trim-Client"))
    }

    @Test
    fun searchFavoritesWatchedDetailSeasonsEpisodesBindOfficialPaths() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, """{"code":0,"msg":"","data":[]}""")            // search
        fixture.transport.enqueueJson(200, """{"code":0,"msg":"","data":{"list":[],"total":0}}""") // favorites
        repeat(4) { fixture.transport.enqueueJson(200, """{"code":0,"msg":"","data":[]}""") } // favorite/watched/seasons/episodes
        fixture.transport.enqueueJson(200, """{"code":0,"msg":"","data":[]}""")            // unmark watched
        fixture.transport.enqueueJson(200, FeiniuFixtures.ITEM_DETAIL)                     // detail

        fixture.api.search(fixture.resolution, "fixture")
        fixture.api.favorites(fixture.resolution, page = 1)
        fixture.api.markFavorite(fixture.resolution, "it-0001")
        fixture.api.markWatched(fixture.resolution, "it-0001")
        fixture.api.seasons(fixture.resolution, "it-0002")
        fixture.api.episodes(fixture.resolution, "it-0002")
        fixture.api.unmarkWatched(fixture.resolution, "it-0001")
        val detail = fixture.api.itemDetail(fixture.resolution, "it-0001")

        val paths = fixture.transport.requests.map { it.url.toEncodedString().substringBefore('?').substringAfter("test") }
        assertEquals(
            listOf(
                "/v/api/v1/search/list",
                "/v/api/v1/favorite/list",
                "/v/api/v1/item/favorite",
                "/v/api/v1/item/watched",
                "/v/api/v1/season/list/it-0002",
                "/v/api/v1/episode/list/it-0002",
                "/v/api/v1/item/watched",
                "/v/api/v1/item/it-0001",
            ),
            paths,
        )
        assertEquals(cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall.FeiniuMethod.Put.name, fixture.transport.requests[2].method.name)
        assertEquals(cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall.FeiniuMethod.Delete.name, fixture.transport.requests[6].method.name)
        assertEquals("tt0000001", detail.imdbId)
        assertEquals("Fixture Movie", detail.title)
    }

    @Test
    fun searchQueryCarriesQParameterAndAffectsAuthx() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.SEARCH_LIST)
        fixture.api.search(fixture.resolution, "bat man")
        val sent = fixture.transport.requests.single()
        assertEquals("bat man", sent.url.queryParameters.first("q"))
        assertTrue(sent.headers.first("Authx")!!.isNotEmpty())
    }
}
