package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.ServerError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.domain.TimeoutError
import cn.mafei.cmp.core.http_contract.HttpTimeoutException
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.adapter_contract.PlaybackTransport
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind
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

/** Server negotiated remux: DirectPlay refused, DirectStream offered. */
private const val PLAYBACK_INFO_DIRECT_STREAM_ONLY =
    """{"MediaSources":[{"Id":"msRemux0102030405060708","Protocol":"File","Container":"mkv","SupportsDirectPlay":false,"SupportsDirectStream":true,"SupportsTranscoding":false,"MediaStreams":[{"Index":0,"Type":"Video","Codec":"hevc"},{"Index":1,"Type":"Audio","Codec":"aac"}]}],"PlaySessionId":"session-0001"}"""

/** Pure audio source: no video stream, one audio stream. */
private const val PLAYBACK_INFO_AUDIO_ONLY =
    """{"MediaSources":[{"Id":"msAudio0102030405060708","Protocol":"File","Container":"flac","SupportsDirectPlay":true,"MediaStreams":[{"Index":0,"Type":"Audio","Codec":"flac"}]}],"PlaySessionId":"session-0002"}"""

/** Two versions: the playable remux source carries its own default subtitle. */
private const val PLAYBACK_INFO_MULTI_SOURCE_SUBTITLES =
    """{"MediaSources":[{"Id":"msVersionA","Protocol":"File","Container":"mkv","SupportsDirectPlay":false,"SupportsDirectStream":false,"SupportsTranscoding":false,"MediaStreams":[{"Index":2,"Type":"Subtitle","Codec":"srt","IsExternal":true,"DeliveryMethod":"External","DeliveryUrl":"/Videos/m9/Subtitles/2/0/Stream.srt"}]},{"Id":"msVersionB","Protocol":"File","Container":"mkv","SupportsDirectPlay":true,"MediaStreams":[{"Index":0,"Type":"Video","Codec":"h264"},{"Index":1,"Type":"Audio","Codec":"aac"},{"Index":3,"Type":"Subtitle","Codec":"ass","IsExternal":true,"IsDefault":true,"DeliveryMethod":"External","DeliveryUrl":"/Videos/m9/Subtitles/3/0/Stream.ass"}]}],"PlaySessionId":"session-0003"}"""

/** Server mandates subtitle track 3 while track 2 carries the default flag. */
private const val PLAYBACK_INFO_MANDATED_SUBTITLE =
    """{"MediaSources":[{"Id":"msMandated","Protocol":"File","Container":"mkv","SupportsDirectPlay":true,"DefaultSubtitleStreamIndex":3,"MediaStreams":[{"Index":0,"Type":"Video","Codec":"h264"},{"Index":2,"Type":"Subtitle","Codec":"srt","IsExternal":true,"IsDefault":true,"DeliveryMethod":"External","DeliveryUrl":"/Videos/m8/Subtitles/2/0/Stream.srt"},{"Index":3,"Type":"Subtitle","Codec":"ass","IsExternal":true,"DeliveryMethod":"External","DeliveryUrl":"/Videos/m8/Subtitles/3/0/Stream.ass"}]}],"PlaySessionId":"session-0004"}"""

/** Server turns subtitles off (-1) despite a default external track. */
private const val PLAYBACK_INFO_SUBTITLES_OFF =
    """{"MediaSources":[{"Id":"msOff","Protocol":"File","Container":"mkv","SupportsDirectPlay":true,"DefaultSubtitleStreamIndex":-1,"MediaStreams":[{"Index":0,"Type":"Video","Codec":"h264"},{"Index":2,"Type":"Subtitle","Codec":"srt","IsExternal":true,"IsDefault":true,"DeliveryMethod":"External","DeliveryUrl":"/Videos/m7/Subtitles/2/0/Stream.srt"}]}],"PlaySessionId":"session-0005"}"""

/** Server mandates an embedded (no DeliveryUrl) track: no external fallback. */
private const val PLAYBACK_INFO_MANDATED_EMBEDDED =
    """{"MediaSources":[{"Id":"msEmbedded","Protocol":"File","Container":"mkv","SupportsDirectPlay":true,"DefaultSubtitleStreamIndex":1,"MediaStreams":[{"Index":0,"Type":"Video","Codec":"h264"},{"Index":1,"Type":"Subtitle","Codec":"subrip","IsExternal":false},{"Index":2,"Type":"Subtitle","Codec":"srt","IsExternal":true,"DeliveryMethod":"External","DeliveryUrl":"/Videos/m6/Subtitles/2/0/Stream.srt"}]}],"PlaySessionId":"session-0006"}"""

private const val ISO_SOURCE =
    """{"Id":"disc-source","Protocol":"File","Container":"iso","Path":"/media/disc.iso","SupportsDirectPlay":false,"SupportsDirectStream":false,"SupportsTranscoding":true,"TranscodingUrl":"/videos/disc/master.m3u8","DefaultAudioStreamIndex":1,"DefaultSubtitleStreamIndex":3,"MediaStreams":[{"Index":0,"Type":"Video","Codec":"hevc"},{"Index":1,"Type":"Audio","DisplayTitle":"English TrueHD"},{"Index":2,"Type":"Subtitle","Codec":"pgssub","IsExternal":false},{"Index":3,"Type":"Subtitle","Codec":"srt","IsExternal":true,"DeliveryMethod":"External","DeliveryUrl":"/Videos/disc/disc-source/Subtitles/3/0/Stream.srt"}]}"""

private const val PLAYBACK_INFO_ISO =
    """{"MediaSources":[$ISO_SOURCE],"PlaySessionId":"iso-session"}"""

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

    @Test
    fun qualityCeilingKeepsTranscodingWhenTheServerHonoursIt() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.PLAYBACK_INFO)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(
                cn.mafei.cmp.core.domain.MediaId("m0102030405060708"),
                PlaybackOptions(maxStreamingBitrate = 8_000_000),
            ),
        )
        // 上限走服务端重编：选中的是那条只提供转码的源。
        assertTrue(ready.streamUrl.contains("/videos/m0102030405060708/master.m3u8"), ready.streamUrl)
        assertEquals(PlaybackTransport.Transcode, ready.transport)
        assertEquals(8_000_000, ready.appliedBitrate)
        // Jellyfin 按需求转码：档位表由界面按视频尺寸自己造（null = 可造）。
        assertEquals(null, ready.qualities)
    }

    @Test
    fun qualityCeilingTheServerCannotHonourStillReportsDirectPlay() = runTest {
        // 服务端没有转码路径（只有直连源）时，界面点了 1080p 也只能直连。必须如实报
        // DirectPlay + appliedBitrate = null，卡片才会显示「直连 / 自动」，而不是把
        // 用户点的那一档当成事实（旧版那个 bug 就是这么来的）。
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_AUDIO_ONLY)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(
                cn.mafei.cmp.core.domain.MediaId("m0102030405060708"),
                PlaybackOptions(maxStreamingBitrate = 8_000_000),
            ),
        )
        assertEquals(PlaybackTransport.DirectPlay, ready.transport)
        assertEquals(null, ready.appliedBitrate)
        assertTrue(ready.streamUrl.contains("static=true"), ready.streamUrl)
    }

    // ------------------------------------------------------------- prepare

    @Test
    fun nativeIsoFallbackKeepsSourceTracksResumeAndReporting() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_ISO)
        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(
            cn.mafei.cmp.core.domain.MediaId("disc"),
            PlaybackOptions(isoDirectPlay = true, startPositionMs = 90_000),
        ))
        val url = HttpUrl.parse(ready.streamUrl)
        assertEquals("/Videos/disc/stream.iso", url.encodedPath)
        assertEquals("true", url.queryParameters.first("static"))
        assertEquals("disc-source", url.queryParameters.first("mediaSourceId"))
        assertEquals("iso-session", url.queryParameters.first("playSessionId"))
        assertEquals("device-1", url.queryParameters.first("deviceId"))
        assertEquals("fixture-token", url.queryParameters.first("api_key"))
        assertTrue(ready.streamUrl.endsWith("#bdiso"))
        assertEquals(PlaybackTransport.DirectPlay, ready.transport)
        assertEquals("disc-source", ready.mediaSourceId)
        assertEquals(null, ready.appliedBitrate)
        // The SDK's local Range proxy drops the ISO suffix/marker.
        assertEquals(false, ready.multiConnectionAllowed)
        assertEquals(listOf(1, 2, 3), ready.tracks.map { it.index })
        assertEquals(false, ready.tracks.first { it.index == 1 }.renegotiate)
        assertEquals(false, ready.tracks.first { it.index == 2 }.renegotiate)
        assertEquals(true, ready.tracks.first { it.index == 3 }.renegotiate)
        assertEquals("https://jellyfin.test/Videos/disc/disc-source/Subtitles/3/0/Stream.srt?api_key=fixture-token", ready.subtitleUrl)
        val request = (fixture.transport.requests.single().body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(request.contains("\"StartTimeTicks\":900000000"), request)
        assertTrue(request.contains("\"Container\":\"iso\",\"Type\":\"Video\""), request)

        fixture.transport.enqueue(FakeJellyfinTransport.noContent(url))
        fixture.adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Start, 90_000))
        val report = (fixture.transport.requests.last().body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(report.contains("\"MediaSourceId\":\"disc-source\""), report)
        assertTrue(report.contains("\"PlaySessionId\":\"iso-session\""), report)
        assertTrue(report.contains("\"PlayMethod\":\"DirectPlay\""), report)
        fixture.transport.enqueue(FakeJellyfinTransport.noContent(url))
        fixture.adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Progress, 95_000))
        val progress = (fixture.transport.requests.last().body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(progress.contains("\"PlayMethod\":\"DirectPlay\""), progress)
    }

    @Test
    fun directPlayIsoAlsoReachesTheDiscReader() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_ISO.replace("\"SupportsDirectPlay\":false", "\"SupportsDirectPlay\":true"))
        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(
            cn.mafei.cmp.core.domain.MediaId("disc"), PlaybackOptions(isoDirectPlay = true),
        ))
        assertTrue(ready.streamUrl.endsWith("#bdiso"), ready.streamUrl)
        assertEquals(PlaybackTransport.DirectPlay, ready.transport)
    }

    @Test
    fun localIsoDoesNotRequireATranscodingUrl() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_ISO
            .replace("\"TranscodingUrl\":\"/videos/disc/master.m3u8\",", "")
            .replace("\"Container\":\"iso\"", "\"Container\":\" ISO \""))
        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(
            cn.mafei.cmp.core.domain.MediaId("disc"), PlaybackOptions(isoDirectPlay = true),
        ))
        assertEquals("/Videos/disc/stream.iso", HttpUrl.parse(ready.streamUrl).encodedPath)
        assertEquals(PlaybackTransport.DirectPlay, ready.transport)
    }

    @Test
    fun incompleteIsoProbeStillUsesTheVideoDiscRoute() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_ISO.replace("{\"Index\":0,\"Type\":\"Video\",\"Codec\":\"hevc\"},", ""))
        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(
            cn.mafei.cmp.core.domain.MediaId("disc"), PlaybackOptions(isoDirectPlay = true),
        ))
        assertEquals("/Videos/disc/stream.iso", HttpUrl.parse(ready.streamUrl).encodedPath)
    }

    @Test
    fun isoOnAnEngineWithoutDiscSupportKeepsTheServerStream() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_ISO)
        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("disc")))
        assertEquals(PlaybackTransport.Transcode, ready.transport)
        assertTrue(ready.streamUrl.contains("master.m3u8"), ready.streamUrl)
        assertTrue(!ready.streamUrl.contains("#bdiso"))
    }

    @Test
    fun remoteIsoIsNotMistakenForTheLegacyLocalFileFallback() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_ISO.replace("\"Protocol\":\"File\"", "\"Protocol\":\"Http\""))
        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(
            cn.mafei.cmp.core.domain.MediaId("disc"), PlaybackOptions(isoDirectPlay = true),
        ))
        assertEquals(PlaybackTransport.Transcode, ready.transport)
        assertTrue(!ready.streamUrl.contains("#bdiso"))
    }

    @Test
    fun nativeProfileKeepsNormalVideoAndHdrCapabilities() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.PLAYBACK_INFO)
        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(
            cn.mafei.cmp.core.domain.MediaId("m0102030405060708"), PlaybackOptions(isoDirectPlay = true),
        ))
        assertEquals(PlaybackTransport.DirectPlay, ready.transport)
        assertTrue(!ready.streamUrl.contains("#bdiso"))
        assertEquals(true, ready.multiConnectionAllowed)
        val request = (fixture.transport.requests.single().body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(request.contains("mp4,m4v,mov,mkv,webm,ts,mpegts,flv,avi,3gp"), request)
        assertTrue(request.contains("\"Codec\":\"hevc\""), request)
        assertTrue(request.contains("DOVIWithHDR10"), request)
    }

    @Test
    fun isoStaticFallbackDoesNotClaimAQualityCeilingWasApplied() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_ISO)
        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(
            cn.mafei.cmp.core.domain.MediaId("disc"), PlaybackOptions(isoDirectPlay = true, maxStreamingBitrate = 8_000_000),
        ))
        assertEquals(PlaybackTransport.DirectPlay, ready.transport)
        assertEquals(null, ready.appliedBitrate)
    }

    @Test
    fun negotiationTimeoutOnlyFallsBackWithConfirmedIsoMetadata() = runTest {
        val fixture = Fixture()
        fixture.transport.handler = { request ->
            if (request.url.encodedPath.endsWith("/PlaybackInfo")) throw HttpTimeoutException(request.url, 30_000)
            FakeJellyfinTransport.jsonResponse(200, """{"Id":"disc","MediaSources":[$ISO_SOURCE]}""", request.url)
        }
        val ready = assertIs<PlaybackPreparation.Ready>(fixture.adapter.preparePlayback(
            cn.mafei.cmp.core.domain.MediaId("disc"), PlaybackOptions(isoDirectPlay = true, mediaSourceId = "disc-source"),
        ))
        assertEquals("disc-source", ready.mediaSourceId)
        assertEquals(null, ready.playSessionId)
        assertTrue(ready.streamUrl.endsWith("#bdiso"))
        assertEquals(3, ready.tracks.size)
        fixture.adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Start, 0))
    }

    @Test
    fun ordinaryTimeoutIsNeverConvertedToAnIsoStream() = runTest {
        val fixture = Fixture()
        fixture.transport.handler = { request ->
            if (request.url.encodedPath.endsWith("/PlaybackInfo")) throw HttpTimeoutException(request.url, 30_000)
            FakeJellyfinTransport.jsonResponse(200, """{"Id":"m1","MediaSources":[{"Id":"movie","Protocol":"File","Container":"mkv"}]}""", request.url)
        }
        assertFailsWith<TimeoutError> {
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m1"), PlaybackOptions(isoDirectPlay = true))
        }
    }

    @Test
    fun timeoutFallbackMustNotChangeAnExplicitlySelectedVersion() = runTest {
        val fixture = Fixture()
        fixture.transport.handler = { request ->
            if (request.url.encodedPath.endsWith("/PlaybackInfo")) throw HttpTimeoutException(request.url, 30_000)
            FakeJellyfinTransport.jsonResponse(200, """{"Id":"disc","MediaSources":[$ISO_SOURCE]}""", request.url)
        }
        assertFailsWith<TimeoutError> {
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("disc"),
                PlaybackOptions(isoDirectPlay = true, mediaSourceId = "another-version"))
        }
    }

    @Test
    fun standardTimeoutStillFailsWithoutDiscCapableRuntime() = runTest {
        val fixture = Fixture()
        fixture.transport.handler = { request -> throw HttpTimeoutException(request.url, 30_000) }
        assertFailsWith<TimeoutError> { fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m1")) }
        assertEquals(1, fixture.transport.requests.size)
    }

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
        assertEquals("ms0102030405060708", ready.mediaSourceId)
        assertEquals(
            listOf("English AAC 5.1", "Chinese AAC 2.0", "English SRT"),
            ready.tracks.mapNotNull { it.displayTitle },
        )
        assertEquals(
            listOf(1),
            ready.tracks.filter { it.kind == PlaybackTrackKind.Audio && it.selected }.map { it.index },
        )
        assertEquals(
            listOf(3),
            ready.tracks.filter { it.kind == PlaybackTrackKind.Subtitle && it.selected }.map { it.index },
        )
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
    fun directStreamOnlySourceServesStaticallyLikeDirectPlay() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_DIRECT_STREAM_ONLY)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m0102030405060708")),
        )
        val stream = ready.streamUrl
        // Official Jellyfin 10.10.7 semantics: direct stream also serves with
        // Static=true; the source stays the negotiated remux candidate.
        assertTrue(stream.startsWith("https://jellyfin.test/Videos/m0102030405060708/stream.mkv"), stream)
        assertTrue(stream.contains("static=true"), stream)
        assertTrue(stream.contains("mediaSourceId=msRemux0102030405060708"), stream)
        assertTrue(stream.contains("playSessionId=session-0001"), stream)
        assertTrue(stream.contains("api_key=fixture-token"), stream)
    }

    @Test
    fun audioOnlySourceUsesTheAudioRoute() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_AUDIO_ONLY)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("song")),
        )
        val stream = ready.streamUrl
        assertTrue(ready.audioOnly)
        assertTrue(stream.startsWith("https://jellyfin.test/Audio/song/stream.flac"), stream)
        assertTrue(stream.contains("static=true"), stream) // direct play of the audio file
        assertTrue(stream.contains("mediaSourceId=msAudio0102030405060708"), stream)
        assertTrue(!stream.contains("/Videos/"), stream)
    }

    @Test
    fun unknownErrorCodeAlsoRefuses() = runTest {
        val fixture = Fixture()
        // A future server error code survives SDK tolerance as Unknown — the
        // adapter must refuse, never read "unrecognized" as "no error".
        fixture.transport.enqueueJson(200, """{"MediaSources":[{"Id":"ms1","SupportsDirectPlay":true}],"PlaySessionId":"p","ErrorCode":"SomethingNew"}""")
        val refused = assertIs<PlaybackPreparation.Refused>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m1")),
        )
        assertEquals(RefusalReason.ServerRefused, refused.reason)
        // The SDK tolerates the unknown string into Unknown; the adapter must
        // still refuse instead of reading it as "no error".
        assertEquals(true, refused.detail?.contains("Unknown"))
    }

    @Test
    fun subtitleComesFromSelectedSourceOnly() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_MULTI_SOURCE_SUBTITLES)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m9")),
        )
        // Version B is the playable source; its default ASS track wins, the
        // dead version A's SRT never leaks in.
        assertEquals(
            "https://jellyfin.test/Videos/m9/Subtitles/3/0/Stream.ass?api_key=fixture-token",
            ready.subtitleUrl,
        )
    }

    @Test
    fun mandatedSubtitleIndexWinsOverSourceDefault() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_MANDATED_SUBTITLE)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m8")),
        )
        // The server picked track 3; the source-default flag on track 2 and
        // "first external track" heuristics must not override it.
        assertEquals(
            "https://jellyfin.test/Videos/m8/Subtitles/3/0/Stream.ass?api_key=fixture-token",
            ready.subtitleUrl,
        )
    }

    @Test
    fun mandatedNegativeSubtitleIndexTurnsSubtitlesOff() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_SUBTITLES_OFF)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m7")),
        )
        // -1 means subtitles off: no track is mounted, default flag or not.
        assertEquals(null, ready.subtitleUrl)
    }

    @Test
    fun mandatedEmbeddedSubtitleNeverFallsBackToAnotherExternalTrack() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PLAYBACK_INFO_MANDATED_EMBEDDED)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m6")),
        )
        // The mandate names an embedded track without a delivery URL; the
        // player renders it in-stream, so no external URL is attached.
        assertEquals(null, ready.subtitleUrl)
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
        // Position 0 is a legal, load-bearing value (seek back to start).
        assertTrue(startBody.contains("\"PositionTicks\":0"), startBody)

        val progressBody = bodyOf(progress)
        assertTrue(progressBody.contains("\"PositionTicks\":15000000"), progressBody) // 1_500ms * 10_000
        assertTrue(progressBody.contains("\"IsPaused\":true"), progressBody)

        val stoppedBody = bodyOf(stopped)
        assertTrue(stoppedBody.contains("\"PositionTicks\":1200000000"), stoppedBody)
    }

    @Test
    fun zeroPositionProgressIsReportedNotDropped() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.PLAYBACK_INFO)
        val ready = assertIs<PlaybackPreparation.Ready>(
            fixture.adapter.preparePlayback(cn.mafei.cmp.core.domain.MediaId("m0102030405060708")),
        )
        fixture.transport.enqueue(FakeJellyfinTransport.noContent(HttpUrl.parse(JellyfinFixtures.BASE_URL)))
        // User seeks back to the very beginning: the server must receive 0.
        fixture.adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Progress, positionMs = 0L, isPaused = false))
        val body = (fixture.transport.requests.last().body as cn.mafei.cmp.core.http_contract.HttpBody.Text).text
        assertTrue(body.contains("\"PositionTicks\":0"), body)
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
