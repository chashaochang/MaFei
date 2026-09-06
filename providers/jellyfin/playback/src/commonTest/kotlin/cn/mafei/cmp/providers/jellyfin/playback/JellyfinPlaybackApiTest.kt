package cn.mafei.cmp.providers.jellyfin.playback

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSession
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import cn.mafei.cmp.providers.jellyfin.wire.MediaStreamType
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackErrorCode
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

class JellyfinPlaybackApiTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private companion object {
        val base: HttpUrl = HttpUrl.parse(JellyfinFixtures.BASE_URL)
    }

    private class Fixture {
        val transport = FakeJellyfinTransport()
        val client = JellyfinClient(
            JellyfinServerConfig(base, "MaFei", "Test", "device-1", "2.0.0"),
            transport,
            InMemoryJellyfinSessionState(JellyfinSession("u1", "tok-1")),
        )
        val api = DefaultJellyfinPlaybackApi(client)
    }

    @Test
    fun playbackInfoDecodesSourcesAndTracks() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.PLAYBACK_INFO, base)

        val info = fixture.api.playbackInfo("u1", "m1", playSessionId = "session-0001")
        assertEquals(2, info.mediaSources.size)
        assertEquals("session-0001", info.playSessionId)

        val direct = info.mediaSources[0]
        assertEquals(cn.mafei.cmp.providers.jellyfin.wire.MediaProtocol.File, direct.protocol)
        assertEquals(true, direct.supportsDirectPlay)
        assertEquals(3, direct.defaultSubtitleStreamIndex)
        val subtitle = direct.mediaStreams.first { it.type == MediaStreamType.Subtitle }
        assertEquals("srt", subtitle.codec)
        assertEquals(true, subtitle.isExternal)
        assertEquals("eng", subtitle.language)

        val transcode = info.mediaSources[1]
        assertEquals("/videos/m0102030405060708/master.m3u8?VideoCodec=h264&AudioCodec=aac&PlaySessionId=session-0001", transcode.transcodingUrl)
        assertEquals("hls", transcodingSubProtocol(transcode))

        val body = (fixture.transport.requests.single().body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertNotNull(body)
        assertEquals("""{"UserId":"u1","PlaySessionId":"session-0001","AutoOpenLiveStream":false}""", body)
    }

    private fun transcodingSubProtocol(source: cn.mafei.cmp.providers.jellyfin.wire.MediaSourceInfoDto): String? = source.transcodingSubProtocol

    @Test
    fun playbackInfoReportsInDtoErrorCodeWithoutThrowing() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.PLAYBACK_INFO_ERROR, base)

        val info = fixture.api.playbackInfo("u1", "m1")
        assertEquals(PlaybackErrorCode.NoCompatibleStream, info.errorCode)
        assertEquals(0, info.mediaSources.size)
    }

    @Test
    fun directStreamUrlUsesSessionToken() {
        val fixture = Fixture()
        val url = fixture.api.directStreamUrl("m1", mediaSourceId = "ms1", playSessionId = "ps1")
        assertEquals("https://jellyfin.test/Videos/m1/stream?static=true&mediaSourceId=ms1&playSessionId=ps1&api_key=tok-1", url.toString())
    }

    @Test
    fun transcodingUrlAbsolutizesAndAppendsToken() {
        val fixture = Fixture()
        val url = fixture.api.transcodingUrl("/videos/m1/master.m3u8?PlaySessionId=ps1")
        assertEquals("https://jellyfin.test/videos/m1/master.m3u8?PlaySessionId=ps1&api_key=tok-1", url.toString())
    }
}
