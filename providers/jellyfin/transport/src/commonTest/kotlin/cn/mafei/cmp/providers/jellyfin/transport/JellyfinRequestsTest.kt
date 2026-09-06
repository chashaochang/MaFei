package cn.mafei.cmp.providers.jellyfin.transport

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.wire.ItemField
import cn.mafei.cmp.providers.jellyfin.wire.ImageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinRequestsTest {

    private val base: HttpUrl = HttpUrl.parse("https://jellyfin.test")

    private fun requestUrlOf(request: cn.mafei.cmp.core.http_contract.HttpRequest): HttpUrl = request.url

    @Test
    fun publicSystemInfoBuildsAnonymousGet() {
        val request = JellyfinRequests.publicSystemInfo(base)
        assertEquals("https://jellyfin.test/System/Info/Public", requestUrlOf(request).toString())
        assertEquals(cn.mafei.cmp.core.http_contract.HttpMethod.Get, request.method)
    }

    @Test
    fun itemsQueryEncodesPaginationSortFilterAndSearchTerm() {
        val request = JellyfinRequests.items(
            base,
            JellyfinItemQuery(
                userId = "user-1",
                parentId = "parent-2",
                startIndex = 100,
                limit = 25,
                includeItemTypes = listOf("Movie", "Series"),
                fields = listOf(ItemField.People, ItemField.MediaSources),
                sortBy = listOf(JellyfinSortBy.DateCreated, JellyfinSortBy.SortName),
                sortOrder = JellyfinSortOrder.Descending,
                recursive = true,
                searchTerm = "bat man & robin 中文",
                genres = listOf("Sci-Fi", "Drama"),
                years = listOf(2023, 2024),
                filters = listOf(JellyfinItemFilter.IsUnplayed),
            ),
        )
        val url = requestUrlOf(request)
        val query = url.queryParameters

        assertEquals("user-1", query.first("UserId"))
        assertEquals("parent-2", query.first("ParentId"))
        assertEquals("100", query.first("StartIndex"))
        assertEquals("25", query.first("Limit"))
        assertEquals("Movie,Series", query.first("IncludeItemTypes"))
        assertEquals("People,MediaSources", query.first("Fields"))
        assertEquals("DateCreated,SortName", query.first("SortBy"))
        assertEquals("Descending", query.first("SortOrder"))
        assertEquals("true", query.first("Recursive"))
        assertEquals("bat man & robin 中文", query.first("SearchTerm"))
        assertEquals("Sci-Fi|Drama", query.first("Genres"))
        assertEquals("2023,2024", query.first("Years"))
        assertEquals("IsUnplayed", query.first("Filters"))
        // encoded on the wire, decoded on access
        val wire = url.toEncodedString()
        assertTrue(wire.contains("SearchTerm=bat%20man%20%26%20robin%20%E4%B8%AD%E6%96%87"), "wire form: " + wire)
    }

    @Test
    fun authenticateByNameEncodesProtocolBody() {
        val request = JellyfinRequests.authenticateByName(base, "ada@example.org", "p@ss\"word")
        val body = (request.body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(body.contains("\"Username\":\"ada@example.org\""))
        assertTrue(body.contains("\"Pw\":\"p@ss\\\"word\""))
        assertEquals("application/json", request.headers.first("content-type"))
    }

    @Test
    fun pathSegmentsEncodeUserAndItemIds() {
        val request = JellyfinRequests.itemById(base, "user/1", "item?2")
        assertEquals("https://jellyfin.test/Users/user%2F1/Items/item%3F2", requestUrlOf(request).toString())
    }

    @Test
    fun seasonsAndEpisodesBindShowEndpoints() {
        assertEquals(
            "https://jellyfin.test/Shows/s1/Seasons?UserId=u1",
            requestUrlOf(JellyfinRequests.seasons(base, "u1", "s1")).toString(),
        )
        assertEquals(
            "https://jellyfin.test/Shows/s1/Episodes?UserId=u1&SeasonId=season1&StartIndex=5&Limit=10&Fields=People",
            requestUrlOf(
                JellyfinRequests.episodes(base, "u1", "s1", seasonId = "season1", startIndex = 5, limit = 10, fields = listOf(ItemField.People)),
            ).toString(),
        )
    }

    @Test
    fun directStreamUrlCarriesStaticSourceIdAndToken() {
        val url = JellyfinRequests.directStreamUrl(
            base,
            itemId = "m1",
            mediaSourceId = "ms1",
            container = "mkv",
            playSessionId = "ps1",
            token = "tok",
        )
        assertEquals("https://jellyfin.test/Videos/m1/stream.mkv?Static=true&MediaSourceId=ms1&PlaySessionId=ps1&api_key=tok", url.toString())

        val audio = JellyfinRequests.directStreamUrl(base, itemId = "a1", token = null, audio = true)
        assertEquals("https://jellyfin.test/Audio/a1/stream?Static=true", audio.toString())
        assertNull(audio.queryParameters.first("api_key"))
    }

    @Test
    fun transcodingUrlIsAbsolutizedAndGetsApiKeyOnce() {
        val url = JellyfinRequests.absolutizeTranscodingUrl(
            base,
            "/videos/m1/master.m3u8?VideoCodec=h264&PlaySessionId=ps1",
            token = "tok",
        )
        assertEquals("https://jellyfin.test/videos/m1/master.m3u8?VideoCodec=h264&PlaySessionId=ps1&api_key=tok", url.toString())

        val alreadyHasKey = JellyfinRequests.absolutizeTranscodingUrl(base, "/videos/m1/master.m3u8?api_key=server", token = "tok")
        assertEquals("server", alreadyHasKey.queryParameters.first("api_key"))
        assertEquals(1, alreadyHasKey.queryParameters.values("api_key").size)

        val absolute = JellyfinRequests.absolutizeTranscodingUrl(base, "https://other.test/master.m3u8", token = null)
        assertEquals("https://other.test/master.m3u8", absolute.toString())
    }

    @Test
    fun imageUrlsCarrySizeTagAndOptionalToken() {
        assertEquals(
            "https://jellyfin.test/Items/m1/Images/Primary?maxWidth=300&tag=abc",
            JellyfinRequests.imageUrl(base, "m1", maxWidth = 300, tag = "abc").toString(),
        )
        assertEquals(
            "https://jellyfin.test/Items/m1/Images/Backdrop?api_key=tok",
            JellyfinRequests.imageUrl(base, "m1", imageType = ImageType.Backdrop, token = "tok").toString(),
        )
        assertEquals(
            "https://jellyfin.test/Users/u1/Images/Primary?tag=t1",
            JellyfinRequests.userImageUrl(base, "u1", tag = "t1").toString(),
        )
    }

    @Test
    fun playbackInfoBindsPostWithQueryAndJsonBody() {
        val request = JellyfinRequests.playbackInfo(
            base,
            userId = "u1",
            itemId = "m1",
            body = cn.mafei.cmp.providers.jellyfin.wire.PlaybackInfoRequestDto(
                userId = "u1",
                playSessionId = "ps1",
                autoOpenLiveStream = false,
            ),
        )
        val url = requestUrlOf(request)
        assertEquals("https://jellyfin.test/Items/m1/PlaybackInfo", url.toEncodedString().substringBefore('?'))
        assertEquals("u1", url.queryParameters.first("UserId"))
        assertEquals("false", url.queryParameters.first("AutoOpenLiveStream"))
        val body = (request.body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(body.contains("\"playSessionId\":\"ps1\""), body)
    }
}
