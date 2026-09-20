package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.AuthorizationError
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaFilter
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.data.adapter_contract.OffsetPageCursorCodec
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSession
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 二级链路数据缺口补齐的端点级测试：收藏/已看写接口、播放器相似条目、resume 分页、
 * 管理员删除与播放选项（版本/轨道）。
 */
class JellyfinAdapterSecondLevelTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private class Fixture(session: JellyfinSession? = JellyfinSession("u1", "fixture-token")) {
        val transport = FakeJellyfinTransport()
        val adapter = JellyfinMediaProviderAdapter(
            facade = cn.mafei.cmp.providers.jellyfin.facade.DefaultJellyfinFacade(
                config = JellyfinServerConfig(
                    baseUrl = HttpUrl.parse(JellyfinFixtures.BASE_URL),
                    clientName = "MaFei",
                    deviceName = "Test",
                    deviceId = "device-1",
                    appVersion = "2.0.0",
                ),
                transport = transport,
                sessionState = InMemoryJellyfinSessionState(session),
            ),
            serverId = ServerId("js-1"),
            accountId = AccountId("acc-1"),
            jellyfinUserId = "u1",
        )
    }

    private val similarJson =
        """[{"Name":"Other Movie","Id":"m9999","Type":"Movie","ProductionYear":2023,"UserData":{"IsFavorite":false}}]"""

    private fun resumePage(startIndex: Int, count: Int, total: Int): String =
        """{"Items":[${(1..count).joinToString(",") { i ->
            """{"Name":"Resume ${startIndex + i}","Id":"r${startIndex + i}000000000000","Type":"Movie","UserData":{"PlaybackPositionTicks":${i * 1000000000},"PlayedPercentage":${i * 10},"LastPlayedDate":"2023-05-0${(i % 9) + 1}T00:00:00Z"}}"""
        }}],"TotalRecordCount":$total,"StartIndex":$startIndex}"""

    // -------------------------------------------------------------- similar

    @Test
    fun similarReturnsItemsFromOfficialEndpoint() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, similarJson)
        val items = fixture.adapter.similar(MediaId("m0102030405060708"))
        assertEquals(1, items.size)
        assertEquals("Other Movie", items.first().title)
        val request = fixture.transport.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/Items/m0102030405060708/Similar"), request.url.encodedPath)
        assertTrue(request.url.queryParameters.first("userId") == "u1")
    }

    // -------------------------------------------------------- resume paging

    @Test
    fun resumeItemsPagsWithOffsetCursors() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, resumePage(0, 2, total = 4))
        fixture.transport.enqueueJson(200, resumePage(2, 2, total = 4))
        val first = fixture.adapter.resumeItems(PageRequest(pageSize = 2))
        assertEquals(2, first.items.size)
        val cursor = first.nextCursor
        assertTrue(cursor != null)
        assertEquals(4, first.totalEstimate)
        val second = fixture.adapter.resumeItems(PageRequest(cursor = cursor, pageSize = 2))
        assertEquals(2, second.items.size)
        assertNull(second.nextCursor)
        // startIndex 从游标里恢复（legacy resume 固定 0，这里支持真正的历史翻页）。
        assertEquals("2", fixture.transport.requests[1].url.queryParameters.first("startIndex"))
    }

    @Test
    fun resumeItemsForwardsTheLegacyMovieAndEpisodeConstraint() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, resumePage(0, 1, total = 1))

        fixture.adapter.resumeItems(
            PageRequest(
                pageSize = 10,
                filter = MediaFilter(mediaTypes = setOf(MediaType.Movie, MediaType.Episode)),
            ),
        )

        val request = fixture.transport.requests.single()
        assertEquals("Movie,Episode", request.url.queryParameters.first("includeItemTypes"))
    }

    // -------------------------------------------------------- user-data ops

    @Test
    fun deleteItemsSendsIdsAndMapsAuthorizationFailure() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(204, "")
        fixture.adapter.deleteItems(listOf(MediaId("m1"), MediaId("m2")))
        val request = fixture.transport.requests.single()
        assertEquals("Delete", request.method.name)
        assertTrue(request.url.encodedPath.endsWith("/Items"), request.url.encodedPath)
        assertEquals("m1,m2", request.url.queryParameters.first("ids"))

        // 服务器拒绝非管理员：403 → AuthorizationError（服务器是权威）。
        fixture.transport.enqueueJson(403, """{"title":"Forbidden"}""")
        val failure = runCatching {
            fixture.adapter.deleteItems(listOf(MediaId("m3")))
        }.exceptionOrNull()
        assertIs<AuthorizationError>(failure)
    }

    @Test
    fun canDeleteMediaProbesUserPolicyOnce() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, """{"Id":"u1","Name":"Admin","Policy":{"IsAdministrator":true}}""")
        assertTrue(fixture.adapter.canDeleteMedia())
        assertTrue(fixture.adapter.isAdministrator())
        assertTrue(fixture.adapter.canDeleteMedia())
        // 缓存生效：第二次不再发请求。
        assertEquals(1, fixture.transport.requests.size)
        val second = Fixture()
        second.transport.enqueueJson(200, """{"Id":"u1","Name":"User","Policy":{"IsAdministrator":false}}""")
        kotlin.test.assertFalse(second.adapter.canDeleteMedia())
    }

    // ------------------------------------------------- playback with options

    @Test
    fun preparePlaybackPinsMediaSourceAndSubtitle() = runTest {
        val fixture = Fixture()
        val sourceA = """{"Id":"src-a","Name":"1080p","Container":"mkv","Size":1000000,"SupportsDirectPlay":true,
            "MediaStreams":[{"Type":"Video","Index":0,"Width":1920,"Height":1080},
            {"Type":"Audio","Index":1,"Language":"chi","DisplayTitle":"国语"},
            {"Type":"Subtitle","Index":2,"Language":"eng","IsExternal":true,"DeliveryMethod":"External","DeliveryUrl":"/Videos/m1/src-a/Subtitles/2/0/srt"}]}"""
        val sourceB = """{"Id":"src-b","Name":"4K","Container":"mkv","Size":9000000,"SupportsDirectPlay":true,
            "MediaStreams":[{"Type":"Video","Index":0,"Width":3840,"Height":2160},
            {"Type":"Audio","Index":1,"Language":"eng"},
            {"Type":"Subtitle","Index":2,"Language":"chi","IsExternal":true,"DeliveryMethod":"External","DeliveryUrl":"/Videos/m1/src-b/Subtitles/2/0/srt"},
            {"Type":"Subtitle","Index":3,"Language":"eng","IsExternal":true,"DeliveryMethod":"External","DeliveryUrl":"/Videos/m1/src-b/Subtitles/3/0/srt"}]}"""
        val body = """{"MediaSources":[$sourceA,$sourceB],"PlaySessionId":"ps-1"}"""
        fixture.transport.enqueueJson(200, body)
        fixture.transport.enqueueJson(200, body)

        // 不带选项：默认协商选到 src-a，默认字幕轨 2。
        val defaultPrep = fixture.adapter.preparePlayback(MediaId("m1"))
        assertIs<PlaybackPreparation.Ready>(defaultPrep)
        assertTrue("mediaSourceId=src-a" in defaultPrep.streamUrl, defaultPrep.streamUrl)

        // 指定 src-b + 关闭字幕：流 URL 带 src-b，无字幕 URL。
        val pinned = fixture.adapter.preparePlayback(
            MediaId("m1"),
            PlaybackOptions(mediaSourceId = "src-b", subtitleStreamIndex = -1),
        )
        assertIs<PlaybackPreparation.Ready>(pinned)
        assertTrue("mediaSourceId=src-b" in pinned.streamUrl)
        assertNull(pinned.subtitleUrl)

        // 指定 src-b 的英文字幕轨（index 3）：字幕 URL 指向该轨。
        fixture.transport.enqueueJson(200, body)
        val withSubtitle = fixture.adapter.preparePlayback(
            MediaId("m1"),
            PlaybackOptions(mediaSourceId = "src-b", subtitleStreamIndex = 3),
        )
        assertIs<PlaybackPreparation.Ready>(withSubtitle)
        assertTrue("src-b/Subtitles/3" in withSubtitle.subtitleUrl.orEmpty())
    }
}
