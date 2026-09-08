package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.providers.feiniu.core.*
import cn.mafei.cmp.providers.feiniu.facade.DefaultFeiniuFacade
import cn.mafei.cmp.providers.feiniu.testkit.FakeFeiniuTransport
import cn.mafei.cmp.providers.feiniu.testkit.FeiniuFixtures
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class FeiniuMainlineRegressionTest {
    @Test fun pageCursorBindsPageSizeAndRejectsOverflow() {
        val cursor = FeiniuPagePosition().next(50, 1)
        assertEquals(FeiniuPagePosition(2, 1), FeiniuPagePosition.decode(PageRequest(cursor, 50)))
        assertFailsWith<ClientConfigError> { FeiniuPagePosition.decode(PageRequest(cursor, 25)) }
        assertFailsWith<ClientConfigError> { FeiniuPagePosition.decode(PageRequest(PageCursor("v1:fnpage:2147483648:50:1"))) }
        assertFailsWith<ClientConfigError> { FeiniuPagePosition(Int.MAX_VALUE, 1).next(50, 1) }
    }

    @Test fun sdkPinnedPlaybackKeepsOriginalCredentialsAcrossReplacement() = runSuspendTest {
        val transport = FakeFeiniuTransport()
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account-token"))
        val facade = DefaultFeiniuFacade(transport, state)
        val pinned = state.snapshot()
        state.install(FeiniuCredentials("new-account-token"))
        transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        facade.playback.playInfo(resolution, "it-0001", pinnedOperation = pinned)
        assertEquals("old-account-token", transport.requests.single().headers.first("Authorization"))
        assertEquals("new-account-token", state.snapshot().credentials.token)
    }

    private val resolution = FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Local))
    private fun adapter(transport: HttpTransport, state: FeiniuSessionState = InMemoryFeiniuSessionState(FeiniuCredentials("fixture-token"))) =
        FeiniuMediaProviderAdapter(DefaultFeiniuFacade(transport, state), ServerId("s"), AccountId("a"), resolution)

    @Test fun cappedPagesCanConsumeTheirOwnCursor() = runSuspendTest {
        val transport = FakeFeiniuTransport()
        val adapter = adapter(transport)
        var cursor: PageCursor? = null
        for (page in 1..3) {
            transport.enqueueJson(200, """{"code":0,"data":{"list":[{"guid":"m$page","title":"Movie","type":"Movie"}],"total":3}}""")
            val result = adapter.items(null, PageRequest(pageSize = 50, cursor = cursor))
            assertTrue((transport.requests.last().body as HttpBody.Text).text.contains("\"page\":$page"))
            cursor = result.nextCursor
            if (page < 3) assertNotNull(cursor) else assertNull(cursor)
        }
    }

    @Test fun favoritesCannotSilentlyDropSort() = runSuspendTest {
        val transport = FakeFeiniuTransport()
        transport.enqueueJson(200, """{"code":0,"data":{"list":[]}}""")
        assertFailsWith<ClientConfigError> {
            adapter(transport).items(null, PageRequest(filter = MediaFilter(favoritesOnly = true), sort = MediaSort(MediaSortField.DateAdded, SortDirection.Ascending)))
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun invalidCalendarDaysAreNotNormalized() {
        for (date in listOf("2023-02-29", "2024-02-30", "2024-04-31")) assertNull(feiniuDateToEpochMsAndYear(date), date)
        assertNotNull(feiniuDateToEpochMsAndYear("2024-02-29"))
        assertEquals(0L, feiniuDateToEpochMsAndYear("1970-01-01")?.first)
    }

    private fun switchingTransport(state: FeiniuSessionState, switchAt: Int, onCall: () -> Unit): HttpTransport {
        var calls = 0
        return object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                calls++; onCall()
                if (calls == switchAt) state.install(FeiniuCredentials("new-account-token"))
                val body = if (calls == 1) FeiniuFixtures.PLAY_INFO else FeiniuFixtures.PLAY_PLAY
                return HttpResponse(200, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)
            }
        }
    }

    @Test fun identitySwitchAfterInfoStopsSecondNegotiation() = runSuspendTest {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account-token"))
        var calls = 0
        val adapter = adapter(switchingTransport(state, 1) { calls++ }, state)
        assertFailsWith<CancellationException> { adapter.preparePlayback(MediaId("it-0001")) }
        assertEquals(1, calls)
    }

    @Test fun identitySwitchAfterPlayCannotReturnMixedCredentials() = runSuspendTest {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account-token"))
        val adapter = adapter(switchingTransport(state, 2) {}, state)
        assertFailsWith<CancellationException> { adapter.preparePlayback(MediaId("it-0001")) }
    }

    @Test fun oldReadyCannotReportUnderNewIdentity() = runSuspendTest {
        val transport = FakeFeiniuTransport()
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account-token"))
        val adapter = adapter(transport, state)
        transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        transport.enqueueJson(200, FeiniuFixtures.PLAY_PLAY)
        val ready = assertIs<PlaybackPreparation.Ready>(adapter.preparePlayback(MediaId("it-0001")))
        state.install(FeiniuCredentials("new-account-token"))
        transport.enqueueJson(200, """{"code":0}""")
        assertFailsWith<CancellationException> { adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Progress, 1000)) }
        assertEquals(2, transport.requests.size)
    }
}
