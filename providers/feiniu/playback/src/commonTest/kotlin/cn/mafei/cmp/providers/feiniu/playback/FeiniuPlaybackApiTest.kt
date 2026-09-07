package cn.mafei.cmp.providers.feiniu.playback

import cn.mafei.cmp.core.http_contract.HttpBody
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayRequest
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

class FeiniuPlaybackApiTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private class Fixture {
        val transport = FakeFeiniuTransport()
        val client = FeiniuClient(
            transport = transport,
            sessionState = cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState(FeiniuCredentials("tok", "Trim-MC-token=tok")),
        )
        val api = FeiniuPlaybackApi(client)
        val resolution = FeiniuHostResolution(FeiniuHostCandidate(FeiniuFixtures.BASE_URL, FeiniuRoute.Direct))
    }

    @Test
    fun playInfoDecodesResumeSelection() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)

        val info = fixture.api.playInfo(fixture.resolution, itemGuid = "it-0001", mediaGuid = "md-0001")
        assertEquals("md-0001", info.mediaGuid)
        assertEquals("vd-0001", info.videoGuid)
        assertEquals("Fixture Movie", info.item?.title)

        val body = (fixture.transport.requests.single().body as HttpBody.Text).text
        assertEquals("""{"item_guid":"it-0001","media_guid":"md-0001"}""", body)
    }

    @Test
    fun playOpensExpiringStreamLinkWithTranscodeParameters() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.PLAY_PLAY)

        val play = fixture.api.play(
            fixture.resolution,
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
        assertEquals("/v/api/v1/stream/fixture?sign=abc", play.playLink)
        assertEquals("/v/api/v1/subtitle/dl/sb-0001", play.subtitleLink)
        assertEquals(1, play.isSubtitleExternal)

        val body = (fixture.transport.requests.single().body as HttpBody.Text).text
        assertTrue(body.contains("\"media_guid\":\"md-0001\""), body)
        assertTrue(body.contains("\"resolution\":\"1080p\""), body)
        assertTrue(body.contains("\"bitrate\":8000000"), body)
        assertTrue(body.contains("\"forced_sdr\":0"), body)
    }

    @Test
    fun recordSendsProgressEnvelope() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, """{"code":0,"msg":""}""")

        fixture.api.record(
            fixture.resolution,
            cn.mafei.cmp.providers.feiniu.core.FeiniuPlayRecordRequest(
                itemGuid = "it-0001",
                mediaGuid = "md-0001",
                videoGuid = "vd-0001",
                audioGuid = "au-0001",
                resolution = "1080p",
                bitrate = 8_000_000,
                timestampSeconds = 1_700_000_000,
                durationSeconds = 7_200,
            ),
        )
        val body = (fixture.transport.requests.single().body as HttpBody.Text).text
        assertTrue(body.contains("\"ts\":1700000000"), body)
        assertTrue(body.contains("\"duration\":7200"), body)
    }

    @Test
    fun directRangeUrlComposesWithOffset() {
        val fixture = Fixture()
        assertEquals(
            "https://feiniu.test/v/api/v1/media/range/md-0001",
            fixture.api.directRangeUrl(FeiniuFixtures.BASE_URL, "md-0001"),
        )
        assertEquals(
            "https://feiniu.test/v/api/v1/media/range/md-0001?range=bytes=1024-",
            fixture.api.directRangeUrl(FeiniuFixtures.BASE_URL, "md-0001", byteOffset = 1024),
        )
    }
}
