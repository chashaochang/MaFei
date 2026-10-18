package cn.mafei.cmp.browser

import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.data.repositories.MediaSession
import cn.mafei.cmp.feature.home.MediaBrowser
import kotlinx.coroutines.*
import kotlin.test.*

class HistoryRefreshFreshnessTest {
    @Test fun emptyPreviewSuccessIsFreshForThirtySecondsAndManualLoadStillForces() = runBlocking {
        val f = fixture()
        f.browser.loadHistoryPreviewIfStale()
        f.clock.advanceBy(29_999)
        f.browser.loadHistoryPreviewIfStale()
        assertEquals(2, f.adapter.requests.size)
        f.clock.advanceBy(1)
        f.browser.loadHistoryPreviewIfStale()
        assertEquals(4, f.adapter.requests.size)
        f.browser.loadHistory()
        assertEquals(6, f.adapter.requests.size)
        assertEquals(listOf(24, 24, 24, 24, 10, 10), f.adapter.requests.map { it.second.pageSize })
    }

    @Test fun expiredPreviewKeepsContentVisibleWhileRefreshingAndCancellationIsRetryable() = runBlocking {
        val f = fixture()
        f.adapter.fetch = { source, _ -> Page(listOf(f.adapter.media(source, 100))) }
        f.browser.loadHistoryPreviewIfStale()
        f.clock.advanceBy(30_000)
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { _, _ -> release.await(); Page.empty() }
        val pending = launch { f.browser.loadHistoryPreviewIfStale() }
        f.adapter.awaitRequests(4)
        assertEquals(2, f.browser.state.value.historyItems.size)
        assertFalse(f.browser.state.value.historyLoading)
        assertTrue(f.browser.state.value.historyRefreshing)
        f.browser.loadHistoryPreviewIfStale()
        assertEquals(4, f.adapter.requests.size)
        pending.cancelAndJoin()
        assertFalse(f.browser.state.value.historyRefreshing)
        f.adapter.fetch = { _, _ -> Page.empty() }
        f.browser.loadHistoryPreviewIfStale()
        assertEquals(6, f.adapter.requests.size)
    }

    @Test fun partialFailureAndStaleCacheDoNotRenewPreviewFreshness() = runBlocking {
        val f = fixture()
        f.adapter.fetch = { source, _ ->
            if (source == "resume") throw NetworkUnreachableError()
            Page(listOf(f.adapter.media("completed", 100)))
        }
        f.browser.loadHistoryPreviewIfStale()
        f.adapter.fetch = { source, _ -> Page(listOf(f.adapter.media(source, 200))) }
        f.browser.loadHistoryPreviewIfStale()
        assertEquals(4, f.adapter.requests.size)
        f.clock.advanceBy(30_000)
        f.adapter.fetch = { _, _ -> throw NetworkUnreachableError() }
        f.browser.loadHistoryPreviewIfStale() // Both repository reads fall back to StaleCache.
        assertEquals(2, f.browser.state.value.historyItems.size)
        f.adapter.fetch = { _, _ -> Page.empty() }
        f.browser.loadHistoryPreviewIfStale()
        assertEquals(8, f.adapter.requests.size)
        assertTrue(f.browser.state.value.historyItems.isEmpty())
    }

    @Test fun playbackRevisionInvalidatesPreviewAndAnInflightOldRevisionCannotBecomeFresh() = runBlocking {
        val f = fixture()
        f.browser.loadHistoryPreviewIfStale()
        f.repository.markPlaybackChanged(f.session)
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { _, _ -> release.await(); Page.empty() }
        val pending = launch { f.browser.loadHistoryPreviewIfStale() }
        f.adapter.awaitRequests(4)
        f.repository.markPlaybackChanged(f.session)
        release.complete(Unit)
        pending.join()
        f.adapter.fetch = { _, _ -> Page.empty() }
        f.browser.loadHistoryPreviewIfStale()
        assertEquals(6, f.adapter.requests.size)
    }

    @Test fun previewRefreshPreservesFullHistoryTailAndCursorIncludingLaterPagination() = runBlocking {
        val f = fixture()
        f.adapter.fetch = { source, request ->
            when (request.cursor?.value) {
                null -> Page(listOf(f.adapter.media("$source-head", 300)), PageCursor("second"))
                "second" -> Page(listOf(f.adapter.media("$source-tail", 200)), PageCursor("third"))
                else -> Page(listOf(f.adapter.media("$source-last", 100)))
            }
        }
        f.browser.ensureHistoryLoaded()
        f.browser.loadMoreHistory()
        val before = f.browser.state.value
        f.clock.advanceBy(30_000)
        f.adapter.fetch = { source, request ->
            if (request.cursor == null) Page(listOf(f.adapter.media("$source-new", 400)))
            else {
                assertEquals(PageCursor("third"), request.cursor)
                assertEquals(10, request.pageSize)
                Page(listOf(f.adapter.media("$source-last", 100)))
            }
        }
        f.browser.loadHistoryPreviewIfStale()
        assertEquals(6, f.browser.state.value.historyItems.size)
        assertEquals(before.historyResumeCursor, f.browser.state.value.historyResumeCursor)
        assertEquals(before.historyCompletedCursor, f.browser.state.value.historyCompletedCursor)
        f.browser.ensureHistoryLoaded()
        assertEquals(6, f.adapter.requests.size)
        f.browser.loadMoreHistory()
        assertEquals(8, f.browser.state.value.historyItems.size)
        assertEquals(setOf("resume-new", "completed-new"), f.browser.state.value.historyItems.take(2).map { it.media.id.value }.toSet())
    }

    @Test fun cancelledInitialHistoryCanBeEnsuredAgainAfterPreviewAndDoesNotStickLoading() = runBlocking {
        val f = fixture()
        f.adapter.fetch = { source, _ -> Page(listOf(f.adapter.media(source, 100))) }
        f.browser.loadHistoryPreviewIfStale()
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { _, _ -> release.await(); Page.empty() }
        val pending = launch { f.browser.ensureHistoryLoaded() }
        f.adapter.awaitRequests(4)
        pending.cancelAndJoin()
        assertFalse(f.browser.state.value.historyLoading)
        assertFalse(f.browser.state.value.historyRefreshing)
        assertEquals(2, f.browser.state.value.historyItems.size)
        f.adapter.fetch = { _, _ -> Page.empty() }
        f.browser.ensureHistoryLoaded()
        f.browser.ensureHistoryLoaded()
        assertEquals(6, f.adapter.requests.size)
        assertTrue(f.browser.state.value.historyItems.isEmpty())
    }

    @Test fun failedInitialHistoryCanBeEnsuredAgainAndManualRefreshKeepsOldRowsUntilSuccess() = runBlocking {
        val f = fixture()
        f.adapter.fetch = { _, _ -> throw NetworkUnreachableError() }
        f.browser.ensureHistoryLoaded()
        assertNotNull(f.browser.state.value.historyError)
        f.adapter.fetch = { source, _ -> Page(listOf(f.adapter.media(source, 100))) }
        f.browser.ensureHistoryLoaded()
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { _, _ -> release.await(); Page.empty() }
        val pending = launch { f.browser.loadHistory() }
        f.adapter.awaitRequests(6)
        assertEquals(2, f.browser.state.value.historyItems.size)
        assertTrue(f.browser.state.value.historyRefreshing)
        release.complete(Unit)
        pending.join()
        assertTrue(f.browser.state.value.historyItems.isEmpty())
    }

    @Test fun previewReentryDoesNotInterruptFullHistoryPagination() = runBlocking {
        val f = fixture()
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { source, request ->
            if (request.cursor == null) Page(listOf(f.adapter.media("$source-first", 200)), PageCursor("next"))
            else { release.await(); Page(listOf(f.adapter.media("$source-next", 100))) }
        }
        f.browser.ensureHistoryLoaded()
        f.clock.advanceBy(30_000)
        val pending = launch { f.browser.loadMoreHistory() }
        f.adapter.awaitRequests(4)
        f.browser.loadHistoryPreviewIfStale()
        assertEquals(4, f.adapter.requests.size)
        release.complete(Unit)
        pending.join()
        assertEquals(4, f.browser.state.value.historyItems.size)
    }

    @Test fun rapidReturnReplacesCancelledPreviewBeforeItsCleanupFinishes() = runBlocking {
        val f = fixture()
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { _, _ -> withContext(NonCancellable) { release.await() }; Page.empty() }
        val pending = launch { f.browser.loadHistoryPreviewIfStale() }
        f.adapter.awaitRequests(2)
        pending.cancel()
        f.adapter.fetch = { source, _ -> Page(listOf(f.adapter.media(source, 100))) }
        f.browser.loadHistoryPreviewIfStale()
        assertEquals(4, f.adapter.requests.size)
        release.complete(Unit)
        pending.join()
        assertEquals(2, f.browser.state.value.historyItems.size)
        assertFalse(f.browser.state.value.historyLoading)
    }

    @Test fun cancelledFullRefreshCannotReplaceRetainedPageWalkWhenPreviewSupersedesIt() = runBlocking {
        val f = fixture()
        f.adapter.fetch = { source, _ -> Page(listOf(f.adapter.media("$source-old", 100)), PageCursor("tail")) }
        f.browser.ensureHistoryLoaded()
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { _, _ -> withContext(NonCancellable) { release.await() }; Page.empty() }
        val pending = launch { f.browser.loadHistory() }
        f.adapter.awaitRequests(4)
        pending.cancel()
        f.adapter.fetch = { source, _ -> Page(listOf(f.adapter.media("$source-new", 200))) }
        f.browser.loadHistoryPreviewIfStale()
        release.complete(Unit)
        pending.join()
        f.adapter.fetch = { source, request ->
            assertEquals(PageCursor("tail"), request.cursor)
            Page(listOf(f.adapter.media("$source-tail", 50)))
        }
        f.browser.loadMoreHistory()
        assertEquals(6, f.browser.state.value.historyItems.size)
        assertFalse(f.browser.state.value.historyLoadMoreFailed)
        assertNull(f.browser.state.value.historyError)
    }

    private suspend fun fixture(): Fixture {
        val clock = FixedEpochClock()
        val adapter = Adapter()
        val repository = MediaRepository(clock, fetchDispatcher = Dispatchers.Unconfined)
        val session = repository.open(adapter)
        return Fixture(clock, adapter, repository, session,
            MediaBrowser(repository, session, adapter.capabilities, nowEpochMs = clock::nowEpochMs))
    }

    private data class Fixture(val clock: FixedEpochClock, val adapter: Adapter, val repository: MediaRepository,
                               val session: MediaSession, val browser: MediaBrowser)

    private class Adapter : MediaProviderAdapter {
        override val provider = ProviderKind.Jellyfin
        override val serverId = ServerId("server")
        override val accountId = AccountId("account")
        override val capabilities = AdapterCapabilities()
        val requests = mutableListOf<Pair<String, PageRequest>>()
        var fetch: suspend (String, PageRequest) -> Page<MediaItem> = { _, _ -> Page.empty() }
        fun media(id: String, playedAt: Long) = MediaItem(MediaId(id), serverId, provider, id, MediaType.Movie,
            userData = MediaUserData(lastPlayedEpochMs = playedAt))
        suspend fun awaitRequests(count: Int) = withTimeout(5_000) { while (requests.size < count) yield() }
        override suspend fun resumeItems(request: PageRequest): Page<MediaItem> {
            requests += "resume" to request
            return fetch("resume", request)
        }
        override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> {
            requests += "completed" to request
            return fetch("completed", request)
        }
        override suspend fun item(id: MediaId) = media(id.value, 100)
        override suspend fun collections(request: PageRequest): Page<MediaCollection> = Page.empty()
        override suspend fun home(request: HomeFeedRequest) = HomeFeed()
        override suspend fun search(term: String, request: PageRequest): Page<MediaItem> = Page.empty()
        override suspend fun seasons(seriesId: MediaId): Page<MediaItem> = Page.empty()
        override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> = Page.empty()
        override suspend fun setFavorite(id: MediaId, favorite: Boolean) = Unit
        override suspend fun setWatched(id: MediaId, watched: Boolean) = Unit
        override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation = error("unused")
        override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) = error("unused")
    }
}
