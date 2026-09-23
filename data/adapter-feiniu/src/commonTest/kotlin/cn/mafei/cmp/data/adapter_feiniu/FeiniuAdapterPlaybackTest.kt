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
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind
import cn.mafei.cmp.data.adapter_contract.PlaybackTransport
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
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val REGULAR_STREAM_LIST =
    """{"code":0,"data":{"files":[{"guid":"md-0001","file_name":"movie.mkv","path":"/media/movie.mkv"}],"video_streams":[{"media_guid":"md-0001","guid":"vd-0001","resolution_type":"4K","bps":20000000}]}}"""

private const val STRM_STREAM_LIST =
    """{"code":0,"data":{"files":[{"guid":"md-0001","file_name":"cloud/movie.strm","path":"/cloud/movie.strm"}]}}"""

private const val USER_INFO_WITH_SOURCE =
    """{"code":0,"data":{"sources":[{"source_name":"fixture-source"}]}}"""

private const val OFFICIAL_STRM_DIRECT =
    """{"code":0,"data":{"header":{"Referer":"https://cloud.test/","Cookie":["a=1","b=2"]},"direct_link_qualities":[{"url":"https://direct.cloud.test/movie.m3u8","resolution":"4K","bitrate":20000000}],"qualities":[{"url":"https://fallback.cloud.test/movie.m3u8"}]}}"""

private const val OFFICIAL_STRM_QUALITY_ONLY =
    """{"code":0,"data":{"header":{"User-Agent":["Cloud-UA"]},"qualities":[{"url":"https://quality.cloud.test/movie.mp4","resolution":"1080P","bitrate":8000000}]}}"""

private const val OFFICIAL_STRM_LADDER =
    """{"code":0,"data":{"header":{"Referer":"https://cloud.test/"},"direct_link_qualities":[{"url":"https://direct.cloud.test/movie-4k.m3u8","resolution":"4K","bitrate":20000000},{"url":"https://direct.cloud.test/movie-1080.m3u8","resolution":"1080P","bitrate":8000000},{"url":"https://direct.cloud.test/movie-720.m3u8","resolution":"720P","bitrate":4000000}]}}"""

private const val PLAY_TRANSCODED =
    """{"code":0,"data":{"media_guid":"md-0001","video_guid":"vd-0001","play_link":"/v/api/v1/media/hls/md-0001/master.m3u8","audio_index":1}}"""

private const val PLAY_TRANSCODED_WITHOUT_LINK =
    """{"code":0,"data":{"media_guid":"md-0001","video_guid":"vd-0001"}}"""

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
            transport.enqueueJson(200, REGULAR_STREAM_LIST)
            return assertIs<PlaybackPreparation.Ready>(adapter.preparePlayback(MediaId(itemGuid)))
        }
    }

    // ------------------------------------------------------------- prepare

    @Test
    fun unrestrictedPlaybackUsesLegacyDirectRangeWithoutTranscodeNegotiation() = runTest {
        val fixture = Fixture()
        val ready = fixture.prepareReady()
        assertEquals("https://feiniu.test/v/api/v1/media/range/md-0001", ready.streamUrl)
        assertEquals(null, ready.subtitleUrl)
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
        assertTrue(context.contains("\"playLink\":\"/v/api/v1/media/range/md-0001\""), context)
        assertEquals(
            listOf("/v/api/v1/play/info", "/v/api/v1/stream/list/it-0001"),
            fixture.transport.requests.map { it.url.encodedPath },
        )
    }

    @Test
    fun relayRoutesAttachTheRelayCookieHeader() = runTest {
        val fixture = Fixture(route = FeiniuRoute.Relay)
        val ready = fixture.prepareReady()
        assertEquals(true, ready.headers["Cookie"]!!.contains("mode=relay"))
    }

    @Test
    fun selectedMediaSourceIsPassedToPlayInfoAndUsedForDirectRange() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(
            200,
            """{"code":0,"data":{"files":[{"guid":"md-selected","file_name":"selected.mkv"}]}}""",
        )

        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(
                MediaId("it-0001"),
                PlaybackOptions(mediaSourceId = "md-selected"),
            ),
        )

        assertEquals("https://feiniu.test/v/api/v1/media/range/md-selected", ready.streamUrl)
        assertEquals("fixture-token", ready.headers["Authorization"])
        assertTrue(bodyOf(fixture.transport.requests.first()).contains("\"media_guid\":\"md-selected\""))
        assertEquals(2, fixture.transport.requests.size)
    }

    @Test
    fun strmUsesOfficialDirectLinkWithoutLeakingFeiniuCredentials() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, STRM_STREAM_LIST)
        fixture.transport.enqueueJson(200, USER_INFO_WITH_SOURCE)
        fixture.transport.enqueueJson(200, OFFICIAL_STRM_DIRECT)

        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(MediaId("it-0001")))

        assertEquals("https://direct.cloud.test/movie.m3u8", ready.streamUrl)
        assertEquals("https://cloud.test/", ready.headers["Referer"])
        assertEquals("a=1; b=2", ready.headers["Cookie"])
        assertFalse(ready.headers.containsKey("Authorization"))
        assertTrue(ready.allowInsecureTls)
        assertFalse(ready.multiConnectionAllowed)
        assertTrue(ready.reportingContext.orEmpty().contains("/v/api/v1/media/range/md-0001"))
        assertEquals(
            listOf(
                "/v/api/v1/play/info",
                "/v/api/v1/stream/list/it-0001",
                "/v/api/v1/user/info",
                "/v/api/v1/stream",
            ),
            fixture.transport.requests.map { it.url.encodedPath },
        )
        val officialBody = bodyOf(fixture.transport.requests.last())
        assertTrue(officialBody.contains("\"ip\":\"${cn.mafei.cmp.providers.feiniu.core.FeiniuDigest.md5("fixture-source")}\""))
    }

    @Test
    fun strmFallsBackFromDirectQualitiesToOrdinaryQualities() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, STRM_STREAM_LIST)
        fixture.transport.enqueueJson(200, USER_INFO_WITH_SOURCE)
        fixture.transport.enqueueJson(200, OFFICIAL_STRM_QUALITY_ONLY)

        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(MediaId("it-0001")))

        assertEquals("https://quality.cloud.test/movie.mp4", ready.streamUrl)
        assertEquals("Cloud-UA", ready.headers["User-Agent"])
        assertFalse(ready.headers.containsKey("Authorization"))
    }

    @Test
    fun failedOfficialStrmResolutionFallsBackToAuthenticatedRange() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, STRM_STREAM_LIST)
        fixture.transport.enqueueJson(200, USER_INFO_WITH_SOURCE)
        fixture.transport.enqueueJson(500, """{"code":500,"msg":"unavailable"}""")

        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(MediaId("it-0001")))

        assertEquals("https://feiniu.test/v/api/v1/media/range/md-0001", ready.streamUrl)
        assertEquals("fixture-token", ready.headers["Authorization"])
    }

    @Test
    fun strmHonoursTheRequestedCeilingByPickingTheLargestRenditionAtOrBelowIt() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, STRM_STREAM_LIST)
        fixture.transport.enqueueJson(200, USER_INFO_WITH_SOURCE)
        fixture.transport.enqueueJson(200, OFFICIAL_STRM_LADDER)

        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(MediaId("it-0001"), PlaybackOptions(maxStreamingBitrate = 8_000_000)),
        )

        // 上限 8M 时给的是 1080P，而不是「第一个能直连的」（那会是 4K）。
        assertEquals("https://direct.cloud.test/movie-1080.m3u8", ready.streamUrl)
        assertEquals(PlaybackTransport.DirectStream, ready.transport)
        assertEquals(8_000_000, ready.appliedBitrate)
        assertEquals(
            listOf(20_000_000, 8_000_000, 4_000_000),
            ready.qualities?.map { it.bitrate },
        )
        assertEquals(listOf("4K", "1080P", "720P"), ready.qualities?.map { it.label })
    }

    @Test
    fun strmWithoutACeilingKeepsTheLegacyFirstReachableLink() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, STRM_STREAM_LIST)
        fixture.transport.enqueueJson(200, USER_INFO_WITH_SOURCE)
        fixture.transport.enqueueJson(200, OFFICIAL_STRM_LADDER)

        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(MediaId("it-0001")))

        assertEquals("https://direct.cloud.test/movie-4k.m3u8", ready.streamUrl)
        // 没要求上限就不报「生效档位」：界面此时显示「自动」。
        assertEquals(null, ready.appliedBitrate)
    }

    @Test
    fun strmReportsTheRealBitrateWhenNoRenditionFitsTheCeiling() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, STRM_STREAM_LIST)
        fixture.transport.enqueueJson(200, USER_INFO_WITH_SOURCE)
        fixture.transport.enqueueJson(200, OFFICIAL_STRM_LADDER)

        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(MediaId("it-0001"), PlaybackOptions(maxStreamingBitrate = 2_000_000)),
        )

        // 上游最小 4M：只能给最小的一档，但**如实上报 4M**，界面才不会显示 2M。
        assertEquals("https://direct.cloud.test/movie-720.m3u8", ready.streamUrl)
        assertEquals(4_000_000, ready.appliedBitrate)
    }

    @Test
    fun rangeRouteReportsOriginalFileWithOnDemandTranscodingAvailable() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, REGULAR_STREAM_LIST)

        // 没有码率上限 = 原画直连；null 表示 provider 支持按需重编，界面仍应提供
        // 旧版清晰度阶梯，用户点中后才调用 /play/play。
        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(MediaId("it-0001")))

        assertEquals("https://feiniu.test/v/api/v1/media/range/md-0001", ready.streamUrl)
        assertEquals(PlaybackTransport.DirectPlay, ready.transport)
        assertEquals(null, ready.qualities)
        assertEquals(null, ready.appliedBitrate)
    }

    @Test
    fun ordinaryFileWithACeilingAsksFnOsToTranscode() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, REGULAR_STREAM_LIST)
        fixture.transport.enqueueJson(200, PLAY_TRANSCODED)

        // 旧版 FeiniuVideoPlaybackProvider：有上限就 playTranscoded。普通文件的档位
        // 只能由服务端重编产出，range 原画永远给不了。
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(
                MediaId("it-0001"),
                PlaybackOptions(maxStreamingBitrate = 8_000_000, startPositionMs = 123_456L),
            ),
        )

        assertEquals("/v/api/v1/play/play", fixture.transport.requests.last().url.encodedPath)
        val body = bodyOf(fixture.transport.requests.last())
        assertTrue(body.contains("\"media_guid\":\"md-0001\""), body)
        assertTrue(body.contains("\"bitrate\":8000000"), body)
        assertTrue(body.contains("\"startTimestamp\":123"), body)
        assertEquals("https://feiniu.test/v/api/v1/media/hls/md-0001/master.m3u8", ready.streamUrl)
        assertEquals(PlaybackTransport.Transcode, ready.transport)
        assertEquals(8_000_000, ready.appliedBitrate)
        // 服务端按需重编：档位表交给界面自己造（null），菜单因此出现。
        assertEquals(null, ready.qualities)
        // 转码流的轨道都在服务端选：换轨要重新协商，不能交给原生播放器。
        assertTrue(ready.tracks.filter { it.kind == PlaybackTrackKind.Audio }.all { it.renegotiate })
    }

    @Test
    fun transcodingWithoutAPlayLinkIsARefusalNotASilentOriginalFile() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        fixture.transport.enqueueJson(200, REGULAR_STREAM_LIST)
        fixture.transport.enqueueJson(200, PLAY_TRANSCODED_WITHOUT_LINK)

        // 用户点了档位、服务端却给不出链接：明确失败，绝不放回原画冒充换档成功。
        val refused = assertIs<PlaybackPreparation.Refused>(
            fixture.adapter.preparePlayback(MediaId("it-0001"), PlaybackOptions(maxStreamingBitrate = 8_000_000)),
        )
        assertEquals(RefusalReason.NoPlayableSource, refused.reason)
    }

    @Test
    fun missingMediaSourceIsAnExplicitRefusal() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, """{"code":0,"msg":"","data":{"guid":"it-0001"}}""")
        fixture.transport.enqueueJson(200, """{"code":0,"data":{"files":[],"video_streams":[]}}""")
        val refused = assertIs<PlaybackPreparation.Refused>(fixture.adapter.preparePlayback(MediaId("it-0001")))
        assertEquals(RefusalReason.NoPlayableSource, refused.reason)
    }

    @Test
    fun streamCatalogFailureSurfacesAsDomainError() = runTest {
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
        assertTrue(progressBody.contains("\"play_link\":\"/v/api/v1/media/range/md-0001\""), progressBody)

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
        val scripted = listOf(FeiniuFixtures.PLAY_INFO, REGULAR_STREAM_LIST)
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
