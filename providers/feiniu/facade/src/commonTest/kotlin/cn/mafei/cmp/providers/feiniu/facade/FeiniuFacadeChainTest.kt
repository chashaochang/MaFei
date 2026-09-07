package cn.mafei.cmp.providers.feiniu.facade

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoutePreference
import cn.mafei.cmp.providers.feiniu.core.FeiniuRuntimeSource
import cn.mafei.cmp.providers.feiniu.testkit.FakeFeiniuTransport
import cn.mafei.cmp.providers.feiniu.testkit.FeiniuFixtures
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun runSuspendTest(block: suspend () -> Unit) {
    val result = block.startCoroutineUninterceptedOrReturn(NoopContinuation)
    check(result !== COROUTINE_SUSPENDED) { "test body suspended unexpectedly" }
}

private object NoopContinuation : Continuation<Unit> {
    override val context = EmptyCoroutineContext
    override fun resumeWith(result: Result<Unit>) {
        result.exceptionOrNull()?.let { throw it }
    }
}

private class FixedRuntime : FeiniuRuntimeSource {
    override fun nowEpochMs(): Long = 1_700_000_000_000L
    override fun random(): Double = 0.5
}

class FeiniuFacadeChainTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private class Fixture {
        val transport = FakeFeiniuTransport()
        val facade = DefaultFeiniuFacade(transport = transport, sessionState = cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState())
    }

    /**
     * Frozen chain 1: address parsing -> FNID resolve (faked connect) ->
     * probe -> username/password login.
     */
    @Test
    fun chain1_routeSelectionToLogin() = runTest {
        val fixture = Fixture()
        // connect resolve + probe + login
        fixture.transport.enqueueJson(200, FeiniuFixtures.FN_CONNECT.replace("fnos gala.invalid", "fnos.net"), url = "https://5ddd.com/api/v1/fn/con")
        repeat(6) { fixture.transport.enqueueNoContent() }
        fixture.transport.enqueueJson(200, FeiniuFixtures.LOGIN_OK)

        val resolution = fixture.facade.connection.resolve("testfnid", FeiniuRoutePreference.Auto)
        assertEquals("http://192.168.1.10:5666", resolution.preferred.baseUrl)
        assertEquals(FeiniuRoute.Local, resolution.preferred.route)

        val credentials = fixture.facade.auth.login(resolution, "demo", "pw")
        assertEquals("fixture-token-0000000000000001", credentials.token)
        assertEquals(credentials.token, fixture.facade.credentials.value.token)

        // the login request must have gone to the preferred (LAN) host
        assertEquals(
            "http://192.168.1.10:5666/v/api/v1/login",
            fixture.transport.requests.last().url.toEncodedString(),
        )
    }

    /** Frozen chain 2: session restore from stored cookie/token. */
    @Test
    fun chain2_sessionRestoreViaStoredCookie() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.USER_INFO, setCookies = listOf("Trim-MC-token=rotated-token; Path=/"))
        fixture.transport.enqueueJson(200, FeiniuFixtures.MEDIA_DB_LIST)
        fixture.transport.enqueueJson(200, FeiniuFixtures.ITEM_LIST)

        val resolution = FeiniuHostResolution(FeiniuHostCandidate(FeiniuFixtures.BASE_URL, FeiniuRoute.Direct))
        val info = fixture.facade.auth.restore(
            resolution,
            FeiniuCredentials(token = "stored", cookie = "sid=1; Trim-MC-token=stored"),
        )
        assertEquals("demo", info.username)

        val libraries = fixture.facade.catalog.mediaLibraries(resolution)
        assertEquals(3, libraries.size)
        assertEquals("电影", libraries[0].title)

        val page = fixture.facade.catalog.items(resolution, ancestorGuid = libraries[0].guid, category = "movie")
        assertEquals(2, page.total)
        assertEquals("Fixture Movie", page.list[0].title)
        assertTrue(page.list[0].isFavorite == 1)

        // authenticated calls carried the rotated token
        val last = fixture.transport.requests.last()
        assertEquals("stored", last.headers.first("Authorization"))
        assertTrue(last.headers.first("Cookie")!!.contains("Trim-MC-token=stored"))
    }

    /** Frozen chain 3: catalog -> PlaybackInfo -> play link -> progress record. */
    @Test
    fun chain3_catalogToPlaybackResourceToProgress() = runTest {
        val fixture = Fixture()
        fixture.facade.client.sessionState.set(FeiniuCredentials(token = "tok-1", cookie = "Trim-MC-token=tok-1"))
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_PLAY)
        fixture.transport.enqueueJson(200, FeiniuFixtures.ITEM_DETAIL)

        val resolution = FeiniuHostResolution(FeiniuHostCandidate(FeiniuFixtures.BASE_URL, FeiniuRoute.Direct))
        val playInfo = fixture.facade.playback.playInfo(resolution, itemGuid = "it-0001")
        assertEquals("md-0001", playInfo.mediaGuid)

        val session = fixture.facade.playback.play(
            resolution,
            FeiniuPlayPlayRequest(
                mediaGuid = "md-0001",
                videoGuid = "vd-0001",
                videoEncoder = "h264",
                resolution = "1080p",
                bitrate = 8_000_000,
                startTimestamp = 0,
                audioEncoder = "aac",
                audioGuid = "au-0001",
                subtitleGuid = "sb-0001",
                channels = 2,
                forcedSdr = 0,
            ),
        )
        assertEquals("/v/api/v1/stream/fixture?sign=abc", session.data.playLink)
        // R10: the session carries the actual serving host for link resolution
        assertEquals(FeiniuFixtures.BASE_URL, session.baseUrl)
        assertEquals(FeiniuRoute.Direct, session.route)

        // asset helpers resolve the (expiring) relative link and subtitles
        assertEquals(
            "https://feiniu.test/v/api/v1/stream/fixture?sign=abc",
            session.resolvePlayLink(baseUrlFallback = FeiniuFixtures.BASE_URL),
        )
        assertEquals(
            "https://feiniu.test/v/api/v1/subtitle/dl/sb-0001",
            session.resolveSubtitleLink(baseUrlFallback = FeiniuFixtures.BASE_URL),
        )
        val headers = fixture.facade.assets.playbackHeaders(FeiniuCredentials("tok-1", "Trim-MC-token=tok-1"), relayRoute = false)
        assertEquals("tok-1", headers["Authorization"])
        assertTrue(headers["Cookie"]!!.contains("Trim-MC-token=tok-1"))
        assertTrue(headers.containsKey("cookie"))

        // progress record
        fixture.transport.enqueueJson(200, """{"code":0,"msg":""}""")
        fixture.facade.playback.record(
            resolution,
            cn.mafei.cmp.providers.feiniu.core.FeiniuPlayRecordRequest(
                itemGuid = "it-0001",
                mediaGuid = "md-0001",
                videoGuid = "vd-0001",
                audioGuid = "au-0001",
                subtitleGuid = "sb-0001",
                resolution = "1080p",
                bitrate = 8_000_000,
                timestampSeconds = 1_700_000_000,
                durationSeconds = 7_200,
                playLink = session.data.playLink,
            ),
        )
        val recordBody = (fixture.transport.requests.last().body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(recordBody.contains("\"item_guid\":\"it-0001\""), recordBody)
        assertTrue(recordBody.contains("\"ts\":1700000000"), recordBody)
    }
}
