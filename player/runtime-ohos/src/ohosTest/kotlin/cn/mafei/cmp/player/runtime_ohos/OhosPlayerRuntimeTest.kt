package cn.mafei.cmp.player.runtime_ohos

import cn.mafei.cmp.player.api.PlayableResource
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerContentMode
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import cn.mafei.cmp.player.api.TrackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OhosPlayerRuntimeTest {

    private class FakeBridge : OhosNativePlayerBridge {
        var sink: ((OhosNativePlayerEvent) -> Unit)? = null
        var created = 0
        var handle = 0L
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var subtitleUrl: String? = null
        var allowInsecureTls = false
        var multiConnectionAllowed = true
        var startPositionMs = 0L
        var selectedTrack: Pair<TrackKind, String?>? = null
        var contentMode: PlayerContentMode? = null
        var playedFromPositionMs: Long? = null
        var released = false
        var unbound = false
        var rejectHeaders = false

        override fun bind(eventSink: (OhosNativePlayerEvent) -> Unit) {
            sink = eventSink
        }

        override fun create(): Long {
            created += 1
            handle = created.toLong()
            return handle
        }

        override fun loadUrl(
            handle: Long,
            url: String,
            headers: Map<String, String>,
            subtitleUrl: String?,
            allowInsecureTls: Boolean,
            multiConnectionAllowed: Boolean,
            startPositionMs: Long,
        ) {
            if (rejectHeaders && headers.isNotEmpty()) throw UnsupportedOperationException("headers unsupported")
            this.url = url
            this.headers = headers
            this.subtitleUrl = subtitleUrl
            this.allowInsecureTls = allowInsecureTls
            this.multiConnectionAllowed = multiConnectionAllowed
            this.startPositionMs = startPositionMs
        }

        override fun play(handle: Long, pendingSeekPositionMs: Long?) {
            playedFromPositionMs = pendingSeekPositionMs
        }
        override fun pause(handle: Long) = Unit
        override fun stop(handle: Long) = Unit
        override fun seek(handle: Long, positionMs: Long) = Unit
        override fun setSpeed(handle: Long, speed: Float) = Unit
        override fun selectTrack(handle: Long, kind: TrackKind, id: String?) {
            selectedTrack = kind to id
        }
        override fun setContentMode(handle: Long, mode: PlayerContentMode) {
            contentMode = mode
        }
        override fun releasePlayer(handle: Long) {
            released = true
        }
        override fun unbind(eventSink: (OhosNativePlayerEvent) -> Unit) {
            unbound = true
            if (sink === eventSink) sink = null
        }
    }

    @Test
    fun nativeEventsAreForwardedWithoutPolling() {
        val bridge = FakeBridge()
        val events = mutableListOf<RuntimeEvent>()
        val runtime = OhosPlayerRuntime(PlayerCapabilities(), bridge)
        runtime.bind { events += it }
        val generation = PlaybackGeneration()
        runtime.execute(RuntimeCommand.Load(generation, PlayableResource("media", "https://test/video")))

        bridge.sink?.invoke(OhosNativePlayerEvent.Ready(bridge.handle, 60_000))
        bridge.sink?.invoke(OhosNativePlayerEvent.Playing(bridge.handle))
        bridge.sink?.invoke(OhosNativePlayerEvent.Progress(bridge.handle, 1_500, 8_000, 60_000))

        assertTrue(events.filterIsInstance<RuntimeEvent.Ready>().single().generation === generation)
        assertTrue(events.filterIsInstance<RuntimeEvent.Playing>().single().generation === generation)
        val progress = events.filterIsInstance<RuntimeEvent.Progress>().single()
        assertEquals(1_500, progress.positionMs)
        assertEquals(60_000, progress.durationMs)
    }

    @Test
    fun unknownDurationIsForwardedAsNull() {
        val bridge = FakeBridge()
        val events = mutableListOf<RuntimeEvent>()
        val runtime = OhosPlayerRuntime(PlayerCapabilities(), bridge)
        runtime.bind { events += it }
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("live", "https://test/live")))

        bridge.sink?.invoke(OhosNativePlayerEvent.Ready(bridge.handle, 0))

        assertNull(events.filterIsInstance<RuntimeEvent.Ready>().single().durationMs)
    }

    @Test
    fun staleNativeSessionCannotMutateReleasedRuntime() {
        val bridge = FakeBridge()
        val events = mutableListOf<RuntimeEvent>()
        val runtime = OhosPlayerRuntime(PlayerCapabilities(), bridge)
        runtime.bind { events += it }
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("one", "https://test/one")))
        val oldSink = bridge.sink
        val oldHandle = bridge.handle
        runtime.execute(RuntimeCommand.Release)

        oldSink?.invoke(OhosNativePlayerEvent.Playing(oldHandle))

        assertTrue(events.isEmpty())
    }

    @Test
    fun loadCarriesHeadersSubtitleAndSelection() {
        val bridge = FakeBridge()
        val runtime = OhosPlayerRuntime(PlayerCapabilities(trackSelection = true), bridge)
        runtime.bind { }
        val generation = PlaybackGeneration()
        runtime.execute(
            RuntimeCommand.Load(
                generation,
                PlayableResource(
                    "media",
                    "https://test/video",
                    mapOf("Authorization" to "token"),
                    "https://test/subtitle.srt",
                    allowInsecureTls = true,
                    multiConnectionAllowed = false,
                ),
            ),
        )
        runtime.execute(RuntimeCommand.SelectTrack(generation, TrackKind.Subtitle, null))

        assertEquals(mapOf("Authorization" to "token"), bridge.headers)
        assertEquals("https://test/subtitle.srt", bridge.subtitleUrl)
        assertTrue(bridge.allowInsecureTls)
        assertEquals(false, bridge.multiConnectionAllowed)
        assertEquals(TrackKind.Subtitle to null, bridge.selectedTrack)
    }

    @Test
    fun unsupportedHeadersBecomePlayerFailure() {
        val bridge = FakeBridge().apply { rejectHeaders = true }
        val events = mutableListOf<RuntimeEvent>()
        val runtime = OhosPlayerRuntime(PlayerCapabilities(), bridge)
        runtime.bind { events += it }
        runtime.execute(
            RuntimeCommand.Load(
                PlaybackGeneration(),
                PlayableResource("media", "https://test/video", mapOf("Cookie" to "secret")),
            ),
        )

        assertEquals(PlayerFailure.UnsupportedMedia, events.filterIsInstance<RuntimeEvent.Failed>().single().failure)
    }

    @Test
    fun playCarriesImmediatelyPrecedingSeekWithoutReusingItLater() {
        val bridge = FakeBridge()
        val runtime = OhosPlayerRuntime(PlayerCapabilities(seek = true), bridge)
        runtime.bind { }
        val generation = PlaybackGeneration()
        runtime.execute(RuntimeCommand.Load(generation, PlayableResource("media", "https://test/video")))

        runtime.execute(RuntimeCommand.Seek(generation, 75_000))
        runtime.execute(RuntimeCommand.Play(generation))

        assertEquals(75_000, bridge.playedFromPositionMs)
        bridge.playedFromPositionMs = -1L
        runtime.execute(RuntimeCommand.Play(generation))
        assertNull(bridge.playedFromPositionMs)
    }

    @Test
    fun loadCarriesResumePositionIntoNativePreparation() {
        val bridge = FakeBridge()
        val runtime = OhosPlayerRuntime(PlayerCapabilities(seek = true), bridge)
        runtime.bind { }

        runtime.execute(
            RuntimeCommand.Load(
                PlaybackGeneration(),
                PlayableResource("media", "https://test/video"),
                startPositionMs = 75_000,
            ),
        )

        assertEquals(75_000, bridge.startPositionMs)
    }

    @Test
    fun releaseDisposesNativeSessionAndEventBinding() {
        val bridge = FakeBridge()
        val runtime = OhosPlayerRuntime(PlayerCapabilities(), bridge)
        runtime.bind { }
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("media", "https://test/video")))

        runtime.execute(RuntimeCommand.Release)

        assertTrue(bridge.released)
        assertTrue(bridge.unbound)
    }

    @Test
    fun releasingPreviousRuntimeDoesNotUnbindReplacementRuntime() {
        val bridge = FakeBridge()
        val previousEvents = mutableListOf<RuntimeEvent>()
        val replacementEvents = mutableListOf<RuntimeEvent>()
        val previous = OhosPlayerRuntime(PlayerCapabilities(), bridge)
        val replacement = OhosPlayerRuntime(PlayerCapabilities(), bridge)
        previous.bind { previousEvents += it }
        previous.execute(
            RuntimeCommand.Load(
                PlaybackGeneration(),
                PlayableResource("previous", "https://test/previous"),
            ),
        )
        replacement.bind { replacementEvents += it }
        val replacementGeneration = PlaybackGeneration()
        replacement.execute(
            RuntimeCommand.Load(
                replacementGeneration,
                PlayableResource("replacement", "https://test/replacement"),
            ),
        )

        previous.execute(RuntimeCommand.Release)
        bridge.sink?.invoke(OhosNativePlayerEvent.Ready(bridge.handle, 90_000))

        assertTrue(previousEvents.isEmpty())
        assertTrue(
            replacementEvents.filterIsInstance<RuntimeEvent.Ready>().single().generation ===
                replacementGeneration,
        )
    }
}
