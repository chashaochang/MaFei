package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.ServerError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.adapter_contract.RefusalReason
import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSession
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val PLAYBACK_INFO_TRANSCODE_ONLY =
    """{"MediaSources":[{"Id":"ms0203040506070809","Protocol":"Http","Container":"ts","SupportsDirectPlay":false,"SupportsDirectStream":false,"SupportsTranscoding":true,"TranscodingUrl":"/videos/m0102030405060708/master.m3u8?PlaySessionId=session-0001","TranscodingSubProtocol":"hls","TranscodingContainer":"ts"}],"PlaySessionId":"session-0001"}"""

private const val PLAYBACK_INFO_DEAD_SOURCE =
    """{"MediaSources":[{"Id":"ms9","Protocol":"File","Path":"/media/movies/secret.mkv","SupportsDirectPlay":false,"SupportsDirectStream":false,"SupportsTranscoding":false}],"PlaySessionId":"session-0001"}"""

private const val PLAYBACK_INFO_CROSS_ORIGIN =
    """{"MediaSources":[{"Id":"ms3","SupportsDirectPlay":false,"SupportsDirectStream":false,"SupportsTranscoding":true,"TranscodingUrl":"https://evil.test/master.m3u8"}],"PlaySessionId":"p"}"""

private const val PLAYBACK_INFO_NO_SOURCES =
    """{"MediaSources":[],"PlaySessionId":"session-0001"}"""

class JellyfinAdapterPlaybackTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private class Fixture {
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
                sessionState = InMemoryJellyfinSessionState(JellyfinSession("u1", "fixture-token")),
            ),
            serverId = ServerId("js-1"),
            accountId = AccountId("acc-1"),
            jellyfinUserId = "u1",
        )
    }

    // ------------------------------------------------------------- prepare

    @Test
    fun directPlaySourceYieldsValidatedStreamUrl() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.PLAYBACK_INFO)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m0102030405060708")),
        )
        val stream = ready.streamUrl
        assertTrue(stream.startsWith("https://jellyfin.test/Videos/m0102030405060708/stream.mkv"), stream)
        assertTrue(stream.contains("static=true"), stream)
        assertTrue(stream.contains("mediaSourceId=ms0102030405060708"), stream)
        assertTrue(stream.contains("playSessionId=session-0001"), stream)
        assertTrue(stream.contains("api_key=fixture-token"), stream)
        // External subtitle track is absolutized; the session token rides along.
        assertEquals(
            "https://jellyfin.test/Videos/m0102030405060708/Subtitles/3/0/Stream.srt?api_key=fixture-token",
            ready.subtitleUrl,
        )
        assertEquals(cn.mafei.cmp.core.domain.PlaybackSessionId("session-0001"), ready.playSessionId)
        assertTrue(ready.headers.isEmpty())
        // The server-side file path never leaks into any URL.
        assertTrue(!ready.streamUrl.contains("/media/movies"))
        assertTrue(!ready.subtitleUrl!!.contains("/media/movies"))
    }

    @Test
    fun transcodeOnlySourceFallsBackToHlsUrl() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_TRANSCODE_ONLY)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m0102030405060708")),
        )
        assertTrue(ready.streamUrl.startsWith("https://jellyfin.test/videos/m0102030405060708/master.m3u8"), ready.streamUrl)
        assertTrue(ready.streamUrl.contains("api_key=fixture-token"), ready.streamUrl)
        assertEquals(cn.mafei.cmp.core.domain.PlaybackSessionId("session-0001"), ready.playSessionId)
    }

    @Test
    fun serverErrorCodeBecomesExplicitRefusal() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.PLAYBACK_INFO_ERROR)
        val refused = assertIs<PlaybackPreparation.Refused>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m0102030405060708")),
        )
        assertEquals(RefusalReason.ServerRefused, refused.reason)
        assertEquals(true, refused.detail?.contains("NoCompatibleStream"))
    }

    @Test
    fun emptyMediaSourcesRefuseWithNoPlayableSource() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_NO_SOURCES)
        val refused = assertIs<PlaybackPreparation.Refused>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m1")),
        )
        assertEquals(RefusalReason.NoPlayableSource, refused.reason)
    }

    @Test
    fun deadSourceRefusesWithNoPlayableSource() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_DEAD_SOURCE)
        val refused = assertIs<PlaybackPreparation.Refused>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m1")),
        )
        assertEquals(RefusalReason.NoPlayableSource, refused.reason)
    }

    @Test
    fun crossOriginTranscodingUrlIsRejectedNotTrusted() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_CROSS_ORIGIN)
        val refused = assertIs<PlaybackPreparation.Refused>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m1")),
        )
        assertEquals(RefusalReason.LinkRejected, refused.reason)
    }

    @Test
    fun playbackInfoTransportFailureSurfacesAsDomainError() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(500, """{"title":"Boom"}""")
        assertFailsWith<ServerError> {
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m1"))
        }
    }

    // -------------------------------------------------------------- report

    @Test
    fun reportRoutesStartProgressStopWithTickPositions() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.PLAYBACK_INFO)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m0102030405060708")),
        )
        repeat(3) { fixture.transport.enqueue(FakeJellyfinTransport.noContent(HttpUrl.parse(JellyfinFixtures.BASE_URL))) }

        fixture.adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Start, positionMs = 0L))
        fixture.adapter.reportPlayback(
            ready,
            PlaybackEvent(PlaybackEventKind.Progress, positionMs = 1_500L, isPaused = true),
        )
        fixture.adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Stop, positionMs = 120_000L))

        val (start, progress, stopped) = fixture.transport.requests.takeLast(3)
        assertEquals("https://jellyfin.test/Sessions/Playing", start.url.toString())
        assertEquals("https://jellyfin.test/Sessions/Playing/Progress", progress.url.toString())
        assertEquals("https://jellyfin.test/Sessions/Playing/Stopped", stopped.url.toString())

        fun bodyOf(request: cn.mafei.cmp.core.http_contract.HttpRequest): String =
            (request.body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text

        val startBody = bodyOf(start)
        assertTrue(startBody.contains("\"ItemId\":\"m0102030405060708\""), startBody)
        assertTrue(startBody.contains("\"PlaySessionId\":\"session-0001\""), startBody)
        assertTrue(startBody.contains("\"MediaSourceId\":\"ms0102030405060708\""), startBody)
        assertTrue(!startBody.contains("\"PositionTicks\""), startBody) // position 0 -> omitted

        val progressBody = bodyOf(progress)
        assertTrue(progressBody.contains("\"PositionTicks\":15000000"), progressBody) // 1_500ms * 10_000
        assertTrue(progressBody.contains("\"IsPaused\":true"), progressBody)

        val stoppedBody = bodyOf(stopped)
        assertTrue(stoppedBody.contains("\"PositionTicks\":1200000000"), stoppedBody)
    }

    @Test
    fun reportRejectsStreamsNotPreparedByThisInstance() = runTest {
        val fixture = Fixture()
        val foreign = PlaybackPreparation.Ready(
            streamUrl = "https://jellyfin.test/Videos/m1/stream",
            playSessionId = null,
            reportingContext = "{\"itemId\":\"m1\",\"mediaSourceId\":\"ms1\"}",
        )
        assertFailsWith<ClientConfigError> {
            fixture.adapter.reportPlayback(foreign, PlaybackEvent(PlaybackEventKind.Start))
        }
        assertEquals(0, fixture.transport.requests.size)
    }

    @Test
    fun reportFailureSurfacesAsDomainErrorAndCancellationPasses() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.PLAYBACK_INFO)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m0102030405060708")),
        )
        run {
            fixture.transport.enqueueJson(500, """{"title":"Boom"}""")
            assertFailsWith<ServerError> {
                fixture.adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Progress, positionMs = 1L))
            }
        }
        run {
            fixture.transport.handler = { throw CancellationException("scope cancelled") }
            assertFailsWith<CancellationException> {
                fixture.adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Stop, positionMs = 1L))
            }
        }
    }
}
