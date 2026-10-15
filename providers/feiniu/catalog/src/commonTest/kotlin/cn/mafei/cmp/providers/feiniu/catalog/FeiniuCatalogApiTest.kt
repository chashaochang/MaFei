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
    fun itemsForwardLegacyHistoryTypesWatchedTagAndDatePlayedSort() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, """{"code":0,"msg":"","data":{"list":[],"total":0}}""")

        fixture.api.items(
            resolution = fixture.resolution,
            itemTypes = listOf("Movie", "Episode"),
            page = 1,
            pageSize = 10,
            sortColumn = "watched_ts",
            sortType = "DESC",
            watchedTag = "1",
        )

        val body = (fixture.transport.requests.single().body as HttpBody.Text).text
        assertTrue(body.contains("\"type\":[\"Movie\",\"Episode\"]"), body)
        assertTrue(body.contains("\"watched\":\"1\""), body)
        assertTrue(body.contains("\"sort_column\":\"watched_ts\""), body)
        assertTrue(body.contains("\"sort_type\":\"DESC\""), body)
        assertTrue(body.contains("\"page_size\":10"), body)
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
        fixture.api.favorites(
            fixture.resolution,
            page = 1,
            sortColumn = "sort_title",
            sortType = "ASC",
            itemTypes = listOf("Movie", "TV", "Episode", "Video"),
        )
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
        val favoriteBody = (fixture.transport.requests[1].body as HttpBody.Text).text
        assertTrue(favoriteBody.contains("\"sort_column\":\"sort_title\""), favoriteBody)
        assertTrue(favoriteBody.contains("\"sort_type\":\"ASC\""), favoriteBody)
        assertTrue(favoriteBody.contains("\"type\":[\"Movie\",\"TV\",\"Episode\",\"Video\"]"), favoriteBody)
        assertEquals(cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall.FeiniuMethod.Put.name, fixture.transport.requests[2].method.name)
        assertEquals(cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall.FeiniuMethod.Delete.name, fixture.transport.requests[6].method.name)
        assertEquals("tt0000001", detail.imdbId)
        assertEquals("child-1", detail.singleChildGuid)
        assertEquals("2024-01-02", detail.airDate)
        assertEquals("/p/summary.jpg", detail.poster)
        assertEquals(1000, detail.posterWidth)
        assertEquals(2, detail.logicType)
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

    @Test
    fun playHistoryUsesOfficialPlayListAndDecodesProgress() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            """{"code":0,"msg":"","data":[{"guid":"resume-1","title":"Resume","type":"Movie","duration":7200,"media_stream":{"progress":50.0,"current_time":3600.0}}]}""",
        )
        val history = fixture.api.playHistory(fixture.resolution)
        assertEquals("resume-1", history.single().guid)
        assertEquals(3600.0, history.single().mediaStream?.currentTimeSeconds)
        assertTrue(fixture.transport.requests.single().url.encodedPath.endsWith("/v/api/v1/play/list"))
    }

    @Test
    fun detailCompanionEndpointsMatchLegacyPathsAndShapes() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            """{"code":0,"data":{"list":[{"person_guid":"person-1","name":"演员甲","job":"Actor","profile_path":"/p.jpg"}],"total":1}}""",
        )
        fixture.transport.enqueueJson(
            200,
            """{"code":0,"data":{"guid":"person-1","name":"演员甲","original_name":"Actor A","profile":"/p.jpg","biography":"简介","is_favorite":1}}""",
        )
        fixture.transport.enqueueJson(
            200,
            """{"code":0,"data":{"list":[{"item_guid":"movie-1","title":"作品","type":"Movie","poster":"/m.jpg"}],"total":1}}""",
        )
        fixture.transport.enqueueJson(200, """{"code":0,"data":[{"id":7,"value":"剧情"}]}""")
        fixture.transport.enqueueJson(
            200,
            """{"code":0,"data":[{"media_guid":"media-1","item_guid":"movie-1","filename":"movie.mkv","file_path":"/video/movie.mkv","file_size":1024}]}""",
        )

        val people = fixture.api.people(fixture.resolution, "movie-1")
        val person = fixture.api.person(fixture.resolution, "person-1")
        val works = fixture.api.personItems(
            fixture.resolution,
            cn.mafei.cmp.providers.feiniu.core.FeiniuPersonItemListRequest(
                personGuid = "person-1",
                page = 1,
                pageSize = 10,
                job = "Actor",
                sortColumn = "sort_title",
                sortType = "ASC",
            ),
        )
        val genres = fixture.api.genres(fixture.resolution)
        val files = fixture.api.itemFiles(fixture.resolution, "movie-1")

        assertEquals("person-1", people.list.single().personGuid)
        assertEquals("Actor A", person.originalName)
        assertEquals("movie-1", works.list.single().itemGuid)
        assertEquals("剧情", genres.single().value)
        assertEquals("/video/movie.mkv", files.single().filePath)
        assertEquals(
            listOf(
                "/v/api/v1/person/list/movie-1",
                "/v/api/v1/person/person-1",
                "/v/api/v1/person/item/list",
                "/v/api/v1/tag/genres",
                "/v/api/v1/media/itemfile/movie-1",
            ),
            fixture.transport.requests.map { it.url.encodedPath },
        )
        assertEquals(cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall.FeiniuMethod.Post.name, fixture.transport.requests[0].method.name)
        val workBody = (fixture.transport.requests[2].body as HttpBody.Text).text
        assertTrue(workBody.contains("\"person_guid\":\"person-1\""), workBody)
        assertTrue(workBody.contains("\"job\":\"Actor\""), workBody)
        assertEquals("zh", fixture.transport.requests[3].url.queryParameters.first("lan"))
    }
}
