package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.AuthFailureKind
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun mediaItem(
    index: Int,
    mediaType: MediaType = MediaType.Movie,
    collectionId: CollectionId? = null,
    seriesId: MediaId? = null,
    seasonId: MediaId? = null,
): MediaItem = MediaItem(
    id = MediaId("item-$index"),
    serverId = ServerId("fake-server"),
    provider = ProviderKind.Jellyfin,
    title = "Item $index",
    mediaType = mediaType,
    collectionId = collectionId,
    seriesId = seriesId,
    seasonId = seasonId,
)

class MediaProviderAdapterContractTest {

    // ------------------------------------------------------------- identity

    @Test
    fun exposesBoundIdentity() {
        val adapter = FakeMediaProviderAdapter(
            provider = ProviderKind.Feiniu,
            serverId = ServerId("srv-1"),
            accountId = AccountId("acc-1"),
        )
        assertEquals(ProviderKind.Feiniu, adapter.provider)
        assertEquals(ServerId("srv-1"), adapter.serverId)
        assertEquals(AccountId("acc-1"), adapter.accountId)
    }

    // ------------------------------------------------ capability declaration

    @Test
    fun defaultCapabilitiesDenyEverything() {
        val caps = AdapterCapabilities()
        for (capability in AdapterCapability.entries) {
            assertFalse(caps.supports(capability), "$capability must default to unsupported")
        }
    }

    @Test
    fun capabilityFlagsAreIndependent() {
        val caps = AdapterCapabilities(
            search = true,
            favoritesRead = true,
            favoritesFilter = false,
            favoritesEdit = true,
            watchedFilter = false,
            watchedEdit = true,
            playbackReporting = false,
        )
        assertTrue(caps.supports(AdapterCapability.Search))
        assertTrue(caps.supports(AdapterCapability.FavoritesRead))
        assertFalse(caps.supports(AdapterCapability.FavoritesFilter))
        assertTrue(caps.supports(AdapterCapability.FavoritesEdit))
        assertFalse(caps.supports(AdapterCapability.WatchedFilter))
        assertTrue(caps.supports(AdapterCapability.WatchedEdit))
        assertFalse(caps.supports(AdapterCapability.PlaybackReporting))
    }

    @Test
    fun gatedMethodsWithoutCapabilityThrowClientConfigError() {
        // Reference pattern every real adapter must follow: the shared
        // requireCapability gate precedes any provider call, so an absent
        // capability can never fake success.
        val adapter = FakeMediaProviderAdapter(capabilities = AdapterCapabilities())
        runSuspendTest {
            assertFailsWith<ClientConfigError> { adapter.search("term", PageRequest()) }
            assertFailsWith<ClientConfigError> { adapter.setFavorite(MediaId("i-1"), favorite = true) }
            assertFailsWith<ClientConfigError> { adapter.setWatched(MediaId("i-1"), watched = true) }
            val ready = PlaybackPreparation.Ready(streamUrl = "https://fake-server/stream/1")
            assertFailsWith<ClientConfigError> { adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Start)) }
        }
        // The writes were rejected before any state changed.
        assertEquals(emptyList(), adapter.favoriteToggles)
        assertEquals(emptyList(), adapter.watchedToggles)
        assertEquals(emptyList(), adapter.reportedEvents)
    }

    @Test
    fun enabledCapabilitiesRecordWrites() {
        val adapter = FakeMediaProviderAdapter(
            capabilities = AdapterCapabilities(
                search = true,
                favoritesEdit = true,
                watchedEdit = true,
                playbackReporting = true,
            ),
            itemsSource = listOf(mediaItem(0)),
            playbackOutcome = PlaybackPreparation.Ready(
                streamUrl = "https://fake-server/stream/1",
                playSessionId = null,
                reportingContext = "ctx",
            ),
        )
        runSuspendTest {
            adapter.setFavorite(MediaId("item-0"), favorite = true)
            adapter.setWatched(MediaId("item-0"), watched = true)
            val ready = assertIs<PlaybackPreparation.Ready>(adapter.preparePlayback(MediaId("item-0")))
            adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Progress, positionMs = 1_500L))
        }
        assertEquals(listOf(MediaId("item-0") to true), adapter.favoriteToggles)
        assertEquals(listOf(MediaId("item-0") to true), adapter.watchedToggles)
        assertEquals(1, adapter.reportedEvents.size)
        assertEquals(PlaybackEventKind.Progress, adapter.reportedEvents.single().second.kind)
        assertEquals(1_500L, adapter.reportedEvents.single().second.positionMs)
    }

    // ------------------------------------------------------------ pagination

    @Test
    fun nullCursorFetchesFirstPage() {
        val items = (0 until 120).map { mediaItem(it) }
        val adapter = FakeMediaProviderAdapter(itemsSource = items)
        runSuspendTest {
            val page = adapter.items(collectionId = null, request = PageRequest(pageSize = 50))
            assertEquals((0 until 50).map { MediaId("item-$it") }, page.items.map { it.id })
            assertEquals(OffsetPageCursorCodec.encode(50), page.nextCursor)
            assertEquals(120, page.totalEstimate)
            assertTrue(page.hasMore)
        }
    }

    @Test
    fun cursorRoundTripWalksAllPagesWithoutOverlap() {
        val items = (0 until 120).map { mediaItem(it) }
        val adapter = FakeMediaProviderAdapter(itemsSource = items)
        val seen = mutableListOf<MediaId>()
        runSuspendTest {
            var cursor: cn.mafei.cmp.core.domain.PageCursor? = null
            do {
                val page = adapter.items(null, PageRequest(cursor = cursor, pageSize = 50))
                seen += page.items.map { it.id }
                cursor = page.nextCursor
            } while (cursor != null)
        }
        assertEquals((0 until 120).map { MediaId("item-$it") }, seen)
    }

    @Test
    fun exhaustedPageCarriesNullCursor() {
        val items = (0 until 50).map { mediaItem(it) }
        val adapter = FakeMediaProviderAdapter(itemsSource = items)
        runSuspendTest {
            val first = adapter.items(null, PageRequest(pageSize = 50))
            assertEquals(50, first.items.size)
            assertNull(first.nextCursor)
            // A terminal empty page stays terminal (never restarts).
            val pastEnd = adapter.items(
                null,
                PageRequest(cursor = OffsetPageCursorCodec.encode(50), pageSize = 50),
            )
            assertTrue(pastEnd.isEmpty)
            assertNull(pastEnd.nextCursor)
        }
    }

    @Test
    fun invalidCursorNeverRestartsFirstPage() {
        val items = (0 until 120).map { mediaItem(it) }
        val adapter = FakeMediaProviderAdapter(itemsSource = items)
        runSuspendTest {
            // Structurally invalid cursors are contract violations, not a
            // license to silently re-serve page one.
            assertFailsWith<ClientConfigError> {
                adapter.items(null, PageRequest(cursor = cn.mafei.cmp.core.domain.PageCursor("v2:off:0")))
            }
            assertFailsWith<ClientConfigError> {
                adapter.items(null, PageRequest(cursor = cn.mafei.cmp.core.domain.PageCursor("v1:off:-1")))
            }
        }
        assertEquals(emptyList(), adapter.calls.filter { it != "items" })
    }

    @Test
    fun collectionFilterNarrowsItems() {
        val movies = CollectionId("col-movies")
        val items = listOf(
            mediaItem(0, collectionId = movies),
            mediaItem(1, collectionId = CollectionId("col-tv")),
        )
        val adapter = FakeMediaProviderAdapter(itemsSource = items)
        runSuspendTest {
            val page = adapter.items(movies, PageRequest())
            assertEquals(listOf(MediaId("item-0")), page.items.map { it.id })
        }
    }

    @Test
    fun collectionsAreASingleChunk() {
        val collections = listOf(
            MediaCollection(
                id = CollectionId("col-1"),
                serverId = ServerId("fake-server"),
                provider = ProviderKind.Jellyfin,
                name = "Movies",
            ),
        )
        val adapter = FakeMediaProviderAdapter(collectionsSource = collections)
        runSuspendTest {
            val page = adapter.collections(PageRequest())
            assertEquals(1, page.items.size)
            assertNull(page.nextCursor)
            assertEquals(1, page.totalEstimate)
        }
    }

    @Test
    fun seasonsAndEpisodesNavigateHierarchy() {
        val series = MediaId("series-1")
        val seasonA = MediaId("season-a")
        val seasonB = MediaId("season-b")
        val items = buildList {
            add(mediaItem(0, MediaType.Series))
            add(mediaItem(1, MediaType.Season, seriesId = series))
            add(mediaItem(2, MediaType.Season, seriesId = series))
            add(mediaItem(3, MediaType.Episode, seriesId = series, seasonId = seasonA))
            add(mediaItem(4, MediaType.Episode, seriesId = series, seasonId = seasonB))
        }
        val adapter = FakeMediaProviderAdapter(itemsSource = items)
        runSuspendTest {
            val seasons = adapter.seasons(series)
            assertEquals(
                listOf(MediaId("item-1"), MediaId("item-2")),
                seasons.items.map { it.id },
            )
            val episodesA = adapter.episodes(series, seasonA, PageRequest())
            assertEquals(listOf(MediaId("item-3")), episodesA.items.map { it.id })
            val allEpisodes = adapter.episodes(series, null, PageRequest())
            assertEquals(2, allEpisodes.items.size)
        }
    }

    // ------------------------------------------------------------- failures

    @Test
    fun domainErrorPropagatesUnchanged() {
        val adapter = FakeMediaProviderAdapter(
            capabilities = AdapterCapabilities(search = true),
            failure = AuthenticationError(AuthFailureKind.TokenExpired, message = "session expired"),
        )
        runSuspendTest {
            val error = assertFailsWith<AuthenticationError> {
                adapter.search("term", PageRequest())
            }
            assertEquals(AuthFailureKind.TokenExpired, error.kind)
        }
    }

    @Test
    fun cancellationPropagatesUntouched() {
        val adapter = FakeMediaProviderAdapter(cancellation = CancellationException("scope cancelled"))
        runSuspendTest {
            val thrown = assertFailsWith<CancellationException> {
                adapter.items(null, PageRequest())
            }
            assertEquals("scope cancelled", thrown.message)
            // assertFailsWith above already proves the cancelled call was not
            // swallowed into an "empty page success".
        }
    }

    @Test
    fun cancelledSearchIsNotAnEmptySuccess() {
        val adapter = FakeMediaProviderAdapter(
            capabilities = AdapterCapabilities(search = true),
            cancellation = CancellationException("cancelled"),
        )
        runSuspendTest {
            assertFailsWith<CancellationException> { adapter.search("term", PageRequest()) }
        }
    }

    // ------------------------------------------------------------- playback

    @Test
    fun preparationRefusalIsExplicit() {
        val adapter = FakeMediaProviderAdapter(
            playbackOutcome = PlaybackPreparation.Refused(
                reason = RefusalReason.LinkMissing,
                detail = "play envelope carried no play_link",
            ),
        )
        runSuspendTest {
            when (val outcome = adapter.preparePlayback(MediaId("item-0"))) {
                is PlaybackPreparation.Ready -> throw AssertionError("expected refusal, got $outcome")
                is PlaybackPreparation.Refused -> {
                    assertEquals(RefusalReason.LinkMissing, outcome.reason)
                    assertEquals("play envelope carried no play_link", outcome.detail)
                }
            }
        }
    }

    @Test
    fun readyPreparationCarriesValidatedStream() {
        val outcome = PlaybackPreparation.Ready(
            streamUrl = "https://fake-server/v/api/v1/media/range/m-1",
            subtitleUrl = "https://fake-server/v/api/v1/subtitle/dl/s-1",
            headers = mapOf("Authorization" to "tok"),
            playSessionId = null,
            reportingContext = "guid-set",
        )
        assertEquals("https://fake-server/v/api/v1/media/range/m-1", outcome.streamUrl)
        assertEquals("tok", outcome.headers["Authorization"])
        // A blank stream URL can never become a Ready outcome.
        assertFailsWith<IllegalArgumentException> { PlaybackPreparation.Ready(streamUrl = " ") }
    }

    @Test
    fun playbackEventRejectsNegativePositions() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackEvent(PlaybackEventKind.Progress, positionMs = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            PlaybackEvent(PlaybackEventKind.Stop, positionMs = 0L, durationMs = -5L)
        }
    }
}
