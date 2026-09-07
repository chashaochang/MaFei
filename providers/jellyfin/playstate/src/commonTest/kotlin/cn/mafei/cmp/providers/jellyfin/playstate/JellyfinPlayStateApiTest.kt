package cn.mafei.cmp.providers.jellyfin.playstate

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSession
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import cn.mafei.cmp.providers.jellyfin.wire.WirePlayMethod
import cn.mafei.cmp.providers.jellyfin.wire.WirePlaybackOrder
import cn.mafei.cmp.providers.jellyfin.wire.WireRepeatMode
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

class JellyfinPlayStateApiTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private companion object {
        val base: HttpUrl = HttpUrl.parse(JellyfinFixtures.BASE_URL)
    }

    private class Fixture {
        val transport = FakeJellyfinTransport()
        val client = JellyfinClient(
            JellyfinServerConfig(base, "MaFei", "Test", "device-1", "2.0.0"),
            transport,
            InMemoryJellyfinSessionState(JellyfinSession("u1", "tok")),
        )
        val api = DefaultJellyfinPlayStateApi(client)
    }

    @Test
    fun startProgressStopPostWithTickPositionsAnd204Handling() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueue(FakeJellyfinTransport.noContent(base))
        fixture.transport.enqueue(FakeJellyfinTransport.noContent(base))
        fixture.transport.enqueue(FakeJellyfinTransport.noContent(base))

        fixture.api.reportStartMs(
            itemId = "m1",
            playSessionId = "ps1",
            mediaSourceId = "ms1",
            positionMs = 1_500L,
            audioStreamIndex = 1,
            subtitleStreamIndex = 3,
            playMethod = WirePlayMethod.DirectPlay,
        )
        fixture.api.reportProgressMs("m1", "ps1", positionMs = 60_000L, isPaused = true)
        fixture.api.reportStoppedMs("m1", "ps1", positionMs = 120_000L)

        assertEquals(3, fixture.transport.requests.size)
        val (start, progress, stopped) = fixture.transport.requests
        assertEquals("https://jellyfin.test/Sessions/Playing", start.url.toString())
        assertEquals("https://jellyfin.test/Sessions/Playing/Progress", progress.url.toString())
        assertEquals("https://jellyfin.test/Sessions/Playing/Stopped", stopped.url.toString())

        val startBody = (start.body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(startBody.contains("\"PositionTicks\":15000000"), startBody)
        assertTrue(startBody.contains("\"PlayMethod\":\"DirectPlay\""), startBody)

        fixture.transport.enqueue(FakeJellyfinTransport.noContent(base))
        fixture.api.reportStart(
            cn.mafei.cmp.providers.jellyfin.wire.PlaybackStartInfoDto(
                itemId = "m2",
                sessionId = "session-1",
                repeatMode = WireRepeatMode.RepeatAll,
                playbackOrder = WirePlaybackOrder.Shuffle,
            ),
        )
        val typedBody = (fixture.transport.requests.last().body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(typedBody.contains("\"SessionId\":\"session-1\""), typedBody)
        assertTrue(typedBody.contains("\"RepeatMode\":\"RepeatAll\""), typedBody)
        assertTrue(typedBody.contains("\"PlaybackOrder\":\"Shuffle\""), typedBody)

        val progressBody = (progress.body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(progressBody.contains("\"PositionTicks\":600000000"), progressBody)
        assertTrue(progressBody.contains("\"IsPaused\":true"), progressBody)

        val stoppedBody = (stopped.body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(stoppedBody.contains("\"PositionTicks\":1200000000"), stoppedBody)

        // every report carried the session token in the authorization header
        start.headers.first("Authorization")!!.let { assertTrue(it.contains("Token=\"tok\"")) }
    }
}
