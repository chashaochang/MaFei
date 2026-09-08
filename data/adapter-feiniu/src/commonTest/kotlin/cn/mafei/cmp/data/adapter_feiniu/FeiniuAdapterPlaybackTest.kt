package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.ServerError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.http_contract.HttpBody
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.adapter_contract.RefusalReason
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState
import cn.mafei.cmp.providers.feiniu.facade.DefaultFeiniuFacade
import cn.mafei.cmp.providers.feiniu.testkit.FakeFeiniuTransport
import cn.mafei.cmp.providers.feiniu.testkit.FeiniuFixtures
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val PLAY_NO_LINK =
    """{"code":0,"msg":"","data":{"media_guid":"md-0001","is_subtitle_external":1}}"""

private const val PLAY_CROSS_ORIGIN_LINK =
    """{"code":0,"msg":"","data":{"media_guid":"md-0001","play_link":"https://evil.test/stream?sign=x","subtitle_link":"/v/api/v1/subtitle/dl/sb-0001"}}"""

private fun bodyOf(request: HttpRequest): String = (request.body as? HttpBody.Text)?.text ?: ""

class FeiniuAdapterPlaybackTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private class Fixture(route: FeiniuRoute = FeiniuRoute.Local) {
        val transport = FakeFeiniuTransport()
        val adapter = FeiniuMediaProviderAdapter(
            facade = DefaultFeiniuFacade(
                transport = transport,
                sessionState = InMemoryFeiniuSessionState(FeiniuCredentials("fixture-token", "Trim-MC-token=fixture-token")),
            ),
            serverId = ServerId("fn-1"),
            accountId = AccountId("acc-1"),
            resolution = FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", route)),
        )

        suspend fun prepareReady(itemGuid: String = "it-0001"): PlaybackPreparation.Ready {
            transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
            transport.enqueueJson(200, FeiniuFixtures.PLAY_PLAY)
            return assertIs<PlaybackPreparation.Ready>(adapter.preparePlayback(MediaId(itemGuid)))
        }
    }

    // ------------------------------------------------------------- prepare

    @Test
    fun negotiationYieldsValidatedAbsoluteStreamAndSubtitle() = runTest {
        val fixture = Fixture()
        val ready = fixture.prepareReady()
        // Relative play_link resolves against the serving origin (R10/F4).
        assertEquals("https://feiniu.test/v/api/v1/stream/fixture?sign=abc", ready.streamUrl)
        assertEquals("https://feiniu.test/v/api/v1/subtitle/dl/sb-0001", ready.subtitleUrl)
        assertEquals(null, ready.playSessionId) // Feiniu issues no play session id
        // The player must attach Authorization + token cookie (lower-case mirror included).
        assertEquals("fixture-token", ready.headers["Authorization"])
        assertEquals(true, ready.headers["Cookie"]!!.contains("Trim-MC-token=fixture-token"))
        assertEquals(ready.headers["Cookie"], ready.headers["cookie"])
        // The reporting context carries the negotiation handles (never URLs re-parsed).
        val context = ready.reportingContext.orEmpty()
        assertTrue(context.contains("\"itemGuid\":\"it-0001\""), context)
        assertTrue(context.contains("\"mediaGuid\":\"md-0001\""), context)
        assertTrue(context.contains("\"videoGuid\":\"vd-0001\""), context)
        assertTrue(context.contains("\"audioGuid\":\"au-0001\""), context)
        assertTrue(context.contains("\"subtitleGuid\":\"sb-0001\""), context)
        assertTrue(context.contains("\"durationSeconds\":7200"), context)
        assertTrue(context.contains("\"playLink\":\"https://feiniu.test/v/api/v1/stream/fixture?sign=abc\""), context)
    }

    @Test
    fun relayRoutesAttachTheRelayCookieHeader() = runTest {
        val fixture = Fixture(route = FeiniuRoute.Relay)
        val ready = fixture.prepareReady()
        assertEquals(true, ready.headers["Cookie"]!!.contains("mode=relay"))
    }

    @Test
    fun missingPlayLinkIsAnExplicitRefusal() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, PLAY_NO_LINK)
        val refused = assertIs<PlaybackPreparation.Refused>(fixture.adapter.preparePlayback(MediaId("it-0001")))
        assertEquals(RefusalReason.LinkMissing, refused.reason)
    }

    @Test
    fun crossOriginPlayLinkIsRejectedNotTrusted() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, PLAY_CROSS_ORIGIN_LINK)
        val refused = assertIs<PlaybackPreparation.Refused>(fixture.adapter.preparePlayback(MediaId("it-0001")))
        assertEquals(RefusalReason.LinkRejected, refused.reason)
    }

    @Test
    fun playNegotiationFailureSurfacesAsDomainError() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(500, """{"code":500,"msg":"boom"}""")
        assertFailsWith<ServerError> { fixture.adapter.preparePlayback(MediaId("it-0001")) }
    }

    // -------------------------------------------------------------- report

    @Test
    fun reportTranslatesMillisecondsIntoTheFeiniuRecordPayload() = runTest {
        val fixture = Fixture()
        val ready = fixture.prepareReady()
        repeat(2) { fixture.transport.enqueueJson(200, """{"code":0,"msg":""}""") }

        fixture.adapter.reportPlayback(
            ready,
            PlaybackEvent(PlaybackEventKind.Progress, positionMs = 1_500L, durationMs = 7_200_000L),
        )
        fixture.adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Stop, positionMs = 120_000L))

        val (progress, stop) = fixture.transport.requests.takeLast(2)
        assertTrue(progress.url.encodedPath.endsWith("/v/api/v1/play/record"), progress.url.encodedPath)
        val progressBody = bodyOf(progress)
        assertTrue(progressBody.contains("\"item_guid\":\"it-0001\""), progressBody)
        assertTrue(progressBody.contains("\"media_guid\":\"md-0001\""), progressBody)
        assertTrue(progressBody.contains("\"video_guid\":\"vd-0001\""), progressBody)
        assertTrue(progressBody.contains("\"audio_guid\":\"au-0001\""), progressBody)
        assertTrue(progressBody.contains("\"subtitle_guid\":\"sb-0001\""), progressBody)
        // Position ms -> record seconds (legacy FeiniuVideoPlaybackProvider:739).
        assertTrue(progressBody.contains("\"ts\":1"), progressBody)
        assertTrue(progressBody.contains("\"duration\":7200"), progressBody) // event duration wins
        assertTrue(progressBody.contains("\"play_link\":\"https://feiniu.test/v/api/v1/stream/fixture?sign=abc\""), progressBody)

        val stopBody = bodyOf(stop)
        // Stop without an event duration falls back to the negotiated duration.
        assertTrue(stopBody.contains("\"ts\":120"), stopBody)
        assertTrue(stopBody.contains("\"duration\":7200"), stopBody)
    }

    @Test
    fun zeroPositionIsNotReportedFollowingLegacyProtocol() = runTest {
        val fixture = Fixture()
        val ready = fixture.prepareReady()
        val requestsAfterPrepare = fixture.transport.requests.size
        // Legacy skips reports with ts <= 0 (FeiniuVideoPlaybackProvider:752).
        fixture.adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Progress, positionMs = 0L))
        assertEquals(requestsAfterPrepare, fixture.transport.requests.size) // no report request added
    }

    @Test
    fun reportRejectsStreamsNotPreparedByThisInstance() = runTest {
        val fixture = Fixture()
        val foreign = PlaybackPreparation.Ready(
            streamUrl = "https://feiniu.test/v/api/v1/stream/foreign",
            reportingContext = "{\"itemGuid\":\"x\",\"mediaGuid\":\"y\",\"videoGuid\":\"v\",\"audioGuid\":\"a\",\"subtitleGuid\":\"s\",\"resolution\":\"\",\"bitrate\":0,\"durationSeconds\":1,\"playLink\":\"https://feiniu.test/s\"}",
        )
        assertFailsWith<ClientConfigError> {
            fixture.adapter.reportPlayback(foreign, PlaybackEvent(PlaybackEventKind.Start, positionMs = 1_000L))
        }
        assertEquals(0, fixture.transport.requests.size)
    }

    @Test
    fun reportFailureSurfacesAsDomainError() = runTest {
        val fixture = Fixture()
        val ready = fixture.prepareReady()
        fixture.transport.enqueueJson(500, """{"code":500,"msg":"boom"}""")
        assertFailsWith<ServerError> {
            fixture.adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Progress, positionMs = 1_000L))
        }
    }

    @Test
    fun reportCancellationPassesThroughUntouched() = runTest {
        // Serves the two negotiation requests, then cancels every further one,
        // so ownership and the wire call happen on the SAME adapter instance.
        var calls = 0
        val scripted = listOf(FeiniuFixtures.PLAY_INFO, FeiniuFixtures.PLAY_PLAY)
        val transport = object : cn.mafei.cmp.core.http_contract.HttpTransport {
            override suspend fun execute(request: HttpRequest): cn.mafei.cmp.core.http_contract.HttpResponse {
                calls++
                if (calls <= scripted.size) {
                    return cn.mafei.cmp.core.http_contract.HttpResponse(
                        statusCode = 200,
                        headers = cn.mafei.cmp.core.http_contract.HttpHeaders.of(
                            cn.mafei.cmp.core.http_contract.HttpHeaders.CONTENT_TYPE to "application/json",
                        ),
                        bodyBytes = scripted[calls - 1].encodeToByteArray(),
                        requestUrl = cn.mafei.cmp.core.http_contract.HttpUrl.parse("https://feiniu.test"),
                        effectiveUrl = cn.mafei.cmp.core.http_contract.HttpUrl.parse("https://feiniu.test"),
                    )
                }
                throw CancellationException("scope cancelled")
            }
        }
        val adapter = FeiniuMediaProviderAdapter(
            facade = DefaultFeiniuFacade(
                transport = transport,
                sessionState = InMemoryFeiniuSessionState(FeiniuCredentials("fixture-token", "Trim-MC-token=fixture-token")),
            ),
            serverId = ServerId("fn-1"),
            accountId = AccountId("acc-1"),
            resolution = FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Local)),
        )
        val ready = assertIs<PlaybackPreparation.Ready>(adapter.preparePlayback(MediaId("it-0001")))
        val thrown = assertFailsWith<CancellationException> {
            adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Stop, positionMs = 1_000L))
        }
        assertEquals("scope cancelled", thrown.message)
        assertTrue(thrown.message!!.isNotEmpty())
    }
}
