package cn.mafei.cmp.browser

import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.feature.home.MediaBrowser
import kotlinx.coroutines.*
import kotlin.test.*

class FavoriteRefreshFreshnessTest {
    @Test fun emptySuccessIsFreshUntilTheBoundaryAndManualRefreshBypassesIt() = runBlocking {
        val f = fixture()
        f.browser.refreshFavoritesIfStale()
        f.clock.advanceBy(29_999)
        f.browser.refreshFavoritesIfStale()
        assertEquals(1, f.adapter.requests.size)
        f.clock.advanceBy(1)
        f.browser.refreshFavoritesIfStale()
        assertEquals(2, f.adapter.requests.size)
        f.browser.refreshFavorites()
        assertEquals(3, f.adapter.requests.size)
    }

    @Test fun returningWithinTheWindowRetainsAllPagesAndTheNextCursor() = runBlocking {
        val f = fixture()
        f.adapter.fetch = { request ->
            if (request.cursor == null) Page(listOf(f.adapter.media("first")), PageCursor("second"))
            else Page(listOf(f.adapter.media("second")), PageCursor("third"))
        }
        f.browser.refreshFavoritesIfStale()
        f.browser.loadMoreFavorites()
        val before = f.browser.state.value
        f.browser.refreshFavoritesIfStale()
        assertEquals(listOf("first", "second"), f.browser.state.value.favoriteItems.map { it.id.value })
        assertEquals(PageCursor("third"), f.browser.state.value.favoriteNextCursor)
        assertSame(before, f.browser.state.value)
        assertEquals(2, f.adapter.requests.size)
    }

    @Test fun failedRefreshAndStaleFallbackCanBeRetriedImmediately() = runBlocking {
        val f = fixture()
        f.adapter.fetch = { throw NetworkUnreachableError() }
        f.browser.refreshFavoritesIfStale()
        assertNotNull(f.browser.state.value.favoriteError)
        f.adapter.fetch = { Page(listOf(f.adapter.media("saved"))) }
        f.browser.refreshFavoritesIfStale()
        assertEquals(2, f.adapter.requests.size)
        f.adapter.fetch = { throw NetworkUnreachableError() }
        f.browser.refreshFavorites() // Repository returns the old page as StaleCache.
        assertEquals("saved", f.browser.state.value.favoriteItems.single().id.value)
        f.adapter.fetch = { Page(listOf(f.adapter.media("new"))) }
        f.browser.refreshFavoritesIfStale()
        assertEquals(4, f.adapter.requests.size)
        assertEquals("new", f.browser.state.value.favoriteItems.single().id.value)
    }

    @Test fun staleFallbackDoesNotDiscardAccumulatedPages() = runBlocking {
        val f = fixture()
        f.adapter.fetch = { request ->
            if (request.cursor == null) Page(listOf(f.adapter.media("first")), PageCursor("second"))
            else Page(listOf(f.adapter.media("second")), PageCursor("third"))
        }
        f.browser.refreshFavoritesIfStale()
        f.browser.loadMoreFavorites()
        f.adapter.fetch = { throw NetworkUnreachableError() }
        f.browser.refreshFavorites()
        assertEquals(listOf("first", "second"), f.browser.state.value.favoriteItems.map { it.id.value })
        assertEquals(PageCursor("third"), f.browser.state.value.favoriteNextCursor)
        assertFalse(f.browser.state.value.favoriteRefreshing)
        f.browser.refreshFavoritesIfStale()
        assertEquals(4, f.adapter.requests.size)
    }

    @Test fun automaticReentryDoesNotDuplicateAnInFlightRequestAndCancellationIsRetryable() = runBlocking {
        val f = fixture()
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { release.await(); Page.empty() }
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { f.browser.refreshFavoritesIfStale() }
        f.browser.refreshFavoritesIfStale()
        assertEquals(1, f.adapter.requests.size)
        pending.cancelAndJoin()
        assertFalse(f.browser.state.value.favoriteLoading)
        f.adapter.fetch = { Page.empty() }
        f.browser.refreshFavoritesIfStale()
        assertEquals(2, f.adapter.requests.size)
    }

    @Test fun manualRefreshSupersedesPendingAutomaticRefreshAndKeepsContentVisible() = runBlocking {
        val f = fixture()
        f.adapter.fetch = { Page(listOf(f.adapter.media("visible"))) }
        f.browser.refreshFavoritesIfStale()
        f.clock.advanceBy(30_000)
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { release.await(); Page(listOf(f.adapter.media("obsolete"))) }
        val old = launch(start = CoroutineStart.UNDISPATCHED) { f.browser.refreshFavoritesIfStale() }
        assertTrue(f.browser.state.value.favoriteRefreshing)
        assertFalse(f.browser.state.value.favoriteLoading)
        assertEquals("visible", f.browser.state.value.favoriteItems.single().id.value)
        f.adapter.fetch = { Page(listOf(f.adapter.media("manual"))) }
        f.browser.refreshFavorites()
        release.complete(Unit)
        old.join()
        f.browser.refreshFavoritesIfStale()
        assertEquals(3, f.adapter.requests.size)
        assertEquals("manual", f.browser.state.value.favoriteItems.single().id.value)
    }

    @Test fun rapidReturnCanReplaceACancelledRequestBeforeItsCleanupFinishes() = runBlocking {
        val f = fixture()
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { withContext(NonCancellable) { release.await() }; Page.empty() }
        val old = launch(start = CoroutineStart.UNDISPATCHED) { f.browser.refreshFavoritesIfStale() }
        old.cancel() // LaunchedEffect cancellation need not finish before the next route enters.
        f.adapter.fetch = { Page(listOf(f.adapter.media("returned"))) }
        f.browser.refreshFavoritesIfStale()
        assertEquals(2, f.adapter.requests.size)
        release.complete(Unit)
        old.join()
        assertEquals("returned", f.browser.state.value.favoriteItems.single().id.value)
        assertFalse(f.browser.state.value.favoriteRefreshing)
    }

    @Test fun automaticReentryDoesNotInterruptPagination() = runBlocking {
        val f = fixture()
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { request ->
            if (request.cursor == null) Page(listOf(f.adapter.media("first")), PageCursor("second"))
            else { release.await(); Page(listOf(f.adapter.media("second"))) }
        }
        f.browser.refreshFavoritesIfStale()
        f.clock.advanceBy(30_000)
        val page = launch(start = CoroutineStart.UNDISPATCHED) { f.browser.loadMoreFavorites() }
        f.browser.refreshFavoritesIfStale()
        assertEquals(2, f.adapter.requests.size)
        release.complete(Unit)
        page.join()
        assertEquals(2, f.browser.state.value.favoriteItems.size)
    }

    @Test fun acceptedMutationsAndExplicitInvalidationBypassTheFreshWindow() = runBlocking {
        val f = fixture()
        f.browser.refreshFavoritesIfStale()
        f.repository.setFavorite(f.session, MediaId("movie"), true)
        f.browser.refreshFavoritesIfStale()
        f.repository.setWatched(f.session, MediaId("movie"), true)
        f.browser.refreshFavoritesIfStale()
        f.repository.invalidate(f.session)
        f.browser.refreshFavoritesIfStale()
        assertEquals(4, f.adapter.requests.size)
    }

    @Test fun mutationDuringARequestCannotMarkTheOldResultFresh() = runBlocking {
        val f = fixture()
        val release = CompletableDeferred<Unit>()
        f.adapter.fetch = { release.await(); Page.empty() }
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { f.browser.refreshFavoritesIfStale() }
        f.repository.setFavorite(f.session, MediaId("movie"), true)
        release.complete(Unit)
        pending.join()
        f.adapter.fetch = { Page.empty() }
        f.browser.refreshFavoritesIfStale()
        assertEquals(2, f.adapter.requests.size)
    }

    private suspend fun fixture(): Fixture {
        val clock = FixedEpochClock()
        val adapter = Adapter()
        val repository = MediaRepository(clock, fetchDispatcher = Dispatchers.Unconfined)
        val session = repository.open(adapter)
        return Fixture(clock, adapter, repository, session,
            MediaBrowser(repository, session, adapter.capabilities, nowEpochMs = clock::nowEpochMs))
    }

    private data class Fixture(
        val clock: FixedEpochClock,
        val adapter: Adapter,
        val repository: MediaRepository,
        val session: cn.mafei.cmp.data.repositories.MediaSession,
        val browser: MediaBrowser,
    )

    private class Adapter : MediaProviderAdapter {
        override val provider = ProviderKind.Jellyfin
        override val serverId = ServerId("server")
        override val accountId = AccountId("account")
        override val capabilities = AdapterCapabilities(favoritesRead = true, favoritesEdit = true, watchedEdit = true)
        val requests = mutableListOf<PageRequest>()
        var fetch: suspend (PageRequest) -> Page<MediaItem> = { Page.empty() }
        fun media(id: String) = MediaItem(MediaId(id), serverId, provider, id, MediaType.Movie)
        override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> {
            requests += request
            return fetch(request)
        }
        override suspend fun item(id: MediaId) = media(id.value)
        override suspend fun collections(request: PageRequest): Page<MediaCollection> = Page.empty()
        override suspend fun search(term: String, request: PageRequest): Page<MediaItem> = Page.empty()
        override suspend fun seasons(seriesId: MediaId): Page<MediaItem> = Page.empty()
        override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> = Page.empty()
        override suspend fun setFavorite(id: MediaId, favorite: Boolean) = Unit
        override suspend fun setWatched(id: MediaId, watched: Boolean) = Unit
        override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation = error("unused")
        override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) = error("unused")
    }
}
