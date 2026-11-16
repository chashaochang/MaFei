package cn.mafei.cmp.data.adapter_plex

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.providers.plex.core.PlexClient
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PlexMediaProviderAdapterTest {
    private class Transport : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        var status = 200
        var body = """{"MediaContainer":{}}"""
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return HttpResponse(status, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)
        }
        override fun close() = Unit
    }
    private val transport = Transport()
    private val adapter = PlexMediaProviderAdapter(
        PlexClient(HttpUrl.parse("https://media.test/plex"), "device-fixture", "server-fixture", transport),
        ServerId("s"), AccountId("a"),
    )

    @Test fun librariesAndItemsMapVideoIdentityWhilePagingConsumesRawRows() = runBlocking {
        transport.body = """{"MediaContainer":{"Directory":[
            {"key":"1","type":"movie","title":"Movies"},
            {"key":"2","type":"show","title":"TV"},
            {"key":"3","type":"artist","title":"Music"}
        ]}}"""
        assertEquals(listOf("Movies", "TV"), adapter.collections(PageRequest()).items.map { it.name })
        transport.body = """{"MediaContainer":{"totalSize":4,"Metadata":[$episode,{"type":"artist"},null]}}"""
        val page = adapter.items(CollectionId("2"), PageRequest(pageSize = 3,
            filter = MediaFilter(unplayedOnly = true), sort = MediaSort(MediaSortField.DateAdded, SortDirection.Descending)))
        assertEquals(3, OffsetPageCursorCodec.decode(assertNotNull(page.nextCursor)))
        assertEquals(4, page.totalEstimate)
        val item = page.items.single()
        assertEquals(ProviderKind.Plex, item.provider)
        assertEquals(MediaType.Episode, item.mediaType)
        assertEquals(MediaId("10"), item.seriesId)
        assertEquals(MediaId("11"), item.seasonId)
        assertEquals(2, item.episodeNumber)
        assertEquals(12345L, item.userData.playbackPositionMs)
        assertEquals(100_000L, item.runtimeMs)
        assertEquals("https://media.test/plex/library/metadata/12/thumb", item.images.first().url)
        val query = transport.requests.last().url.queryParameters
        assertEquals("0", query.first("X-Plex-Container-Start"))
        assertEquals("3", query.first("X-Plex-Container-Size"))
        assertEquals("1", query.first("unwatched"))
        assertEquals("addedAt:desc", query.first("sort"))
        transport.body = """{"MediaContainer":{"totalSize":4,"Metadata":[]}}"""
        assertFalse(adapter.items(CollectionId("2"), PageRequest(page.nextCursor)).hasMore)
        assertEquals("3", transport.requests.last().url.queryParameters.first("X-Plex-Container-Start"))
        assertFalse(adapter.capabilities.favoritesEdit)
        assertFalse(adapter.capabilities.mediaDelete)
    }

    @Test fun searchMapsItsEnvelopeDeduplicatesAndNeverAdvertisesOffsetPaging() = runBlocking {
        transport.body = """{"MediaContainer":{"SearchResult":[{"Metadata":$episode},{"Metadata":$episode},
            {"Metadata":{"ratingKey":"99","type":"artist","title":"Music"}}]}}"""
        val page = adapter.search("Fixture", PageRequest(pageSize = 2,
            filter = MediaFilter(mediaTypes = setOf(MediaType.Movie, MediaType.Episode, MediaType.Series, MediaType.Video))))
        assertEquals(listOf(MediaId("12")), page.items.map { it.id })
        assertNull(page.nextCursor)
        assertEquals("/plex/library/search", transport.requests.single().url.encodedPath)
        assertEquals("movies,tv,otherVideos", transport.requests.single().url.queryParameters.first("searchTypes"))
        assertFailsWith<ClientConfigError> { adapter.search("Fixture", PageRequest(PageCursor("1"))) }
        assertEquals(1, transport.requests.size)
    }

    @Test fun resumeAcceptsTheApplicationHistoryFilterAndKeepsOnlyInProgressVideo() = runBlocking {
        transport.body = """{"MediaContainer":{"Hub":[
            {"Metadata":[$episode,{"ratingKey":"13","type":"show","title":"Show","viewOffset":5000}]},
            {"Metadata":[$episode,{"ratingKey":"14","type":"movie","title":"Not started"}]}
        ]}}"""
        val page = adapter.resumeItems(PageRequest(
            filter = MediaFilter(mediaTypes = setOf(MediaType.Movie, MediaType.Episode)),
        ))
        assertEquals(listOf("12"), page.items.map { it.id.value })
        assertNull(page.nextCursor)
        assertEquals("home.continue,home.ondeck", transport.requests.single().url.queryParameters.first("identifier"))
    }

    @Test fun originalPlaybackRespectsDefaultAndExplicitTracksAndCanDisableExternalSubtitles() = runBlocking<Unit> {
        transport.body = """{"MediaContainer":{"Metadata":[$playable]}}"""
        val ready = assertIs<PlaybackPreparation.Ready>(adapter.preparePlayback(MediaId("12"), PlaybackOptions()))
        assertEquals("https://media.test/plex/library/parts/7/file.mkv", ready.streamUrl)
        assertEquals("https://media.test/plex/library/streams/103/subtitle.srt", ready.subtitleUrl)
        assertEquals(PlaybackTransport.DirectPlay, ready.transport)
        assertEquals(emptyList(), ready.qualities)
        assertEquals("server-fixture", ready.headers["X-Plex-Token"])
        assertFalse(ready.streamUrl.contains("server-fixture"))
        assertEquals(setOf(2, 3), ready.tracks.filter { it.selected }.map { it.index }.toSet())
        assertTrue(ready.tracks.filter { it.kind == PlaybackTrackKind.Subtitle }.all { it.renegotiate })
        val embedded = assertIs<PlaybackPreparation.Ready>(adapter.preparePlayback(MediaId("12"),
            PlaybackOptions(audioStreamIndex = 1, subtitleStreamIndex = 4)))
        assertNull(embedded.subtitleUrl)
        assertEquals(setOf(1, 4), embedded.tracks.filter { it.selected }.map { it.index }.toSet())
        assertFalse(embedded.tracks.single { it.index == 4 }.renegotiate)
        val off = assertIs<PlaybackPreparation.Ready>(adapter.preparePlayback(MediaId("12"), PlaybackOptions(subtitleStreamIndex = -1)))
        assertNull(off.subtitleUrl)
        assertTrue(off.tracks.filter { it.kind == PlaybackTrackKind.Subtitle }.none { it.selected })
        assertIs<PlaybackPreparation.Refused>(adapter.preparePlayback(MediaId("12"), PlaybackOptions(audioStreamIndex = 200)))
        assertIs<PlaybackPreparation.Refused>(adapter.preparePlayback(MediaId("12"), PlaybackOptions(subtitleStreamIndex = 200)))
    }

    @Test fun multipartForeignLinksAndTranscodingRequestsCannotBecomePlayable() = runBlocking {
        transport.body = """{"MediaContainer":{"Metadata":[$playable]}}"""
        assertIs<PlaybackPreparation.Refused>(adapter.preparePlayback(MediaId("12"), PlaybackOptions(maxStreamingBitrate = 1_000_000)))
        assertTrue(transport.requests.isEmpty())
        transport.body = """{"MediaContainer":{"Metadata":[{"ratingKey":"12","type":"movie","title":"Multipart",
            "Media":[{"id":"8","Part":[{"key":"/library/parts/7/a.mkv"},{"key":"/library/parts/8/b.mkv"}]}]}]}}"""
        assertIs<PlaybackPreparation.Refused>(adapter.preparePlayback(MediaId("12"), PlaybackOptions(mediaSourceId = "8")))
        transport.body = """{"MediaContainer":{"Metadata":[${playable.replace("/library/parts/7/file.mkv", "https://foreign.test/file.mkv")}]}}"""
        assertEquals(RefusalReason.LinkRejected,
            assertIs<PlaybackPreparation.Refused>(adapter.preparePlayback(MediaId("12"), PlaybackOptions())).reason)
    }

    @Test fun timelineRetainsMillisecondsAndExactPreparationOwnershipThroughRelease() = runBlocking {
        transport.body = """{"MediaContainer":{"Metadata":[$playable]}}"""
        val ready = assertIs<PlaybackPreparation.Ready>(adapter.preparePlayback(MediaId("12"), PlaybackOptions()))
        val before = transport.requests.size
        assertFailsWith<ClientConfigError> { adapter.reportPlayback(ready.copy(), PlaybackEvent(PlaybackEventKind.Start)) }
        assertEquals(before, transport.requests.size)
        adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Progress, 12345, isPaused = true))
        val report = transport.requests.last()
        assertEquals(HttpMethod.Post, report.method)
        assertEquals("/plex/:/timeline", report.url.encodedPath)
        assertEquals("12345", report.url.queryParameters.first("time"))
        assertEquals("100000", report.url.queryParameters.first("duration"))
        assertEquals("paused", report.url.queryParameters.first("state"))
        assertEquals(ready.headers["X-Plex-Session-Identifier"], report.headers.first("X-Plex-Session-Identifier"))
        adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Stop, 12345))
        assertEquals("stopped", transport.requests.last().url.queryParameters.first("state"))
        adapter.releasePlayback(ready)
        val released = transport.requests.size
        assertFailsWith<ClientConfigError> { adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Progress)) }
        assertEquals(released, transport.requests.size)
    }

    @Test fun errorsAreCategorizedWithoutRetainingResponseSecrets() = runBlocking {
        transport.status = 401
        transport.body = "private-token"
        val failure = assertFailsWith<AuthenticationError> { adapter.items(null, PageRequest()) }
        assertEquals(AuthFailureKind.TokenExpired, failure.kind)
        assertNull(failure.cause)
        assertFalse(failure.toString().contains("private-token"))
    }

    private val episode = """{"ratingKey":"12","type":"episode","title":"Fixture Episode",
        "grandparentRatingKey":"10","parentRatingKey":"11","grandparentTitle":"Fixture Show",
        "parentIndex":1,"index":2,"viewOffset":12345,"duration":100000,"thumb":"/library/metadata/12/thumb"}"""
    private val playable = """{"ratingKey":"12","type":"episode","title":"Fixture Episode","duration":100000,
        "Media":[{"id":"8","container":"mkv","Part":[{"id":7,"key":"/library/parts/7/file.mkv","Stream":[
            {"id":101,"index":1,"streamType":2,"codec":"aac"},
            {"id":102,"index":2,"streamType":2,"codec":"ac3","selected":1},
            {"id":103,"index":3,"streamType":3,"codec":"srt","selected":1,"key":"/library/streams/103/subtitle.srt"},
            {"id":104,"index":4,"streamType":3,"codec":"ass"}
        ]}]}]}"""
}
