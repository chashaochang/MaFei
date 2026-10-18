package cn.mafei.cmp.feature.chasing

import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.data.repositories.MediaSession
import kotlinx.coroutines.*
import kotlin.test.*

class ChasingRefreshFreshnessTest {
    @Test fun emptySuccessIsFreshUntilExpiryAndManualRefreshAlwaysRuns() = runBlocking {
        val f = fixture()
        f.browser.refreshIfStale()
        assertEquals(0, f.browser.state.value.totalCount)
        f.clock.advanceBy(29_999)
        f.browser.refreshIfStale()
        assertEquals(1, f.adapter.resumeCalls)
        f.clock.advanceBy(1)
        f.browser.refreshIfStale()
        assertEquals(2, f.adapter.resumeCalls)
        f.browser.refresh()
        assertEquals(3, f.adapter.resumeCalls)
    }

    @Test fun addingAndRemovingFollowsBypassesTheFreshWindowIncludingAnEmptySelection() = runBlocking {
        val f = fixture(emptyList())
        f.browser.refreshIfStale()
        val empty = f.browser.state.value
        f.browser.refreshIfStale()
        assertSame(empty, f.browser.state.value)
        f.store.ids = listOf(MediaId("added"))
        f.browser.refreshIfStale()
        assertEquals(1, f.adapter.resumeCalls)
        assertEquals(1, f.browser.state.value.followedCount)
        f.store.ids = emptyList()
        f.browser.refreshIfStale()
        assertEquals(0, f.browser.state.value.followedCount)
        assertEquals(1, f.adapter.resumeCalls)
    }

    @Test fun failureAndPartialNextUpFailureRemainImmediatelyRetryable() = runBlocking {
        val f = fixture()
        f.adapter.resume = { throw NetworkUnreachableError() }
        f.browser.refreshIfStale()
        assertNotNull(f.browser.state.value.error)
        f.adapter.resume = { Page.empty() }
        f.adapter.failNextUp = true
        f.browser.refreshIfStale()
        assertNull(f.browser.state.value.error) // Preserve the legacy partial-result presentation.
        f.adapter.failNextUp = false
        f.browser.refreshIfStale()
        assertEquals(3, f.adapter.resumeCalls)
        f.browser.refreshIfStale()
        assertEquals(3, f.adapter.resumeCalls)
    }

    @Test fun staleRepositoryFallbackDoesNotBecomeAFreshSuccess() = runBlocking {
        val f = fixture()
        f.browser.refreshIfStale()
        f.adapter.resume = { throw NetworkUnreachableError() }
        f.browser.refresh()
        assertNull(f.browser.state.value.error)
        f.adapter.resume = { Page.empty() }
        f.browser.refreshIfStale()
        assertEquals(3, f.adapter.resumeCalls)
    }

    @Test fun automaticRequestsAreDeduplicatedAndCancellationAllowsRetry() = runBlocking {
        val f = fixture()
        val release = CompletableDeferred<Unit>()
        f.adapter.resume = { release.await(); Page.empty() }
        val request = launch(start = CoroutineStart.UNDISPATCHED) { f.browser.refreshIfStale() }
        f.browser.refreshIfStale()
        assertEquals(1, f.adapter.resumeCalls)
        request.cancelAndJoin()
        assertFalse(f.browser.state.value.loading)
        f.adapter.resume = { Page.empty() }
        f.browser.refreshIfStale()
        assertEquals(2, f.adapter.resumeCalls)
    }

    @Test fun manualRefreshSupersedesAnAutomaticRequestWhileKeepingSectionsVisible() = runBlocking {
        val f = fixture()
        f.adapter.resume = { Page(listOf(f.adapter.episode("visible"))) }
        f.browser.refreshIfStale()
        val visible = f.browser.state.value.sections
        f.clock.advanceBy(30_000)
        val release = CompletableDeferred<Unit>()
        f.adapter.resume = { release.await(); Page(listOf(f.adapter.episode("obsolete"))) }
        val old = launch(start = CoroutineStart.UNDISPATCHED) { f.browser.refreshIfStale() }
        assertEquals(visible, f.browser.state.value.sections)
        assertTrue(f.browser.state.value.refreshing)
        assertFalse(f.browser.state.value.loading)
        f.adapter.resume = { Page(listOf(f.adapter.episode("manual"))) }
        f.browser.refresh()
        release.complete(Unit)
        old.join()
        f.browser.refreshIfStale()
        assertEquals(3, f.adapter.resumeCalls)
        assertEquals(listOf("manual"), f.browser.state.value.sections.flatMap { it.items }.map { it.media.id.value })
    }

    @Test fun rapidReturnCanReplaceACancelledRequestBeforeItsCleanupFinishes() = runBlocking {
        val f = fixture()
        val release = CompletableDeferred<Unit>()
        f.adapter.resume = { withContext(NonCancellable) { release.await() }; Page.empty() }
        val old = launch(start = CoroutineStart.UNDISPATCHED) { f.browser.refreshIfStale() }
        old.cancel()
        f.adapter.resume = { Page(listOf(f.adapter.episode("returned"))) }
        f.browser.refreshIfStale()
        assertEquals(2, f.adapter.resumeCalls)
        release.complete(Unit)
        old.join()
        assertEquals(listOf("returned"), f.browser.state.value.sections.flatMap { it.items }.map { it.media.id.value })
        assertFalse(f.browser.state.value.refreshing)
    }

    @Test fun mutationWhileReadingLocalFollowsStillInvalidatesFreshContent() = runBlocking {
        val f = fixture()
        f.browser.refreshIfStale()
        val release = CompletableDeferred<Unit>()
        f.store.read = { release.await(); f.store.ids }
        val returning = launch(start = CoroutineStart.UNDISPATCHED) { f.browser.refreshIfStale() }
        f.repository.setWatched(f.session, MediaId("episode"), true)
        release.complete(Unit)
        returning.join()
        assertEquals(2, f.adapter.resumeCalls)
    }

    @Test fun repositoryMutationAndInvalidationBypassTheFreshWindow() = runBlocking {
        val f = fixture()
        f.browser.refreshIfStale()
        f.repository.setWatched(f.session, MediaId("episode"), true)
        f.browser.refreshIfStale()
        f.repository.invalidate(f.session)
        f.browser.refreshIfStale()
        f.repository.markPlaybackChanged(f.session)
        f.browser.refreshIfStale()
        assertEquals(4, f.adapter.resumeCalls)
    }

    @Test fun playbackChangeDuringLoadingPreservesPublicationWithoutMakingItFresh() = runBlocking {
        val f = fixture()
        val release = CompletableDeferred<Unit>()
        f.adapter.resume = { release.await(); Page.empty() }
        val old = launch(start = CoroutineStart.UNDISPATCHED) { f.browser.refreshIfStale() }
        f.repository.markPlaybackChanged(f.session)
        release.complete(Unit)
        old.join()
        assertFalse(old.isCancelled)
        assertFalse(f.browser.state.value.loading)
        assertNull(f.browser.state.value.error)
        f.adapter.resume = { Page.empty() }
        f.browser.refreshIfStale()
        assertEquals(2, f.adapter.resumeCalls)
    }

    private suspend fun fixture(ids: List<MediaId> = listOf(MediaId("series"))): Fixture {
        val clock = FixedEpochClock(1_800_000_000_000)
        val adapter = Adapter()
        val repository = MediaRepository(clock, fetchDispatcher = Dispatchers.Unconfined)
        val session = repository.open(adapter)
        val store = Store(ids)
        return Fixture(clock, adapter, repository, session, store,
            ChasingBrowser(repository, session, adapter.capabilities, store, clock::nowEpochMs))
    }

    private data class Fixture(
        val clock: FixedEpochClock,
        val adapter: Adapter,
        val repository: MediaRepository,
        val session: MediaSession,
        val store: Store,
        val browser: ChasingBrowser,
    )

    private class Store(var ids: List<MediaId>) : ChasingSeriesStore {
        var read: suspend () -> List<MediaId> = { ids }
        override suspend fun seriesIds(accountId: AccountId) = read()
        override suspend fun replace(accountId: AccountId, seriesIds: List<MediaId>) { ids = seriesIds }
    }

    private class Adapter : MediaProviderAdapter {
        override val provider = ProviderKind.Jellyfin
        override val serverId = ServerId("server")
        override val accountId = AccountId("account")
        override val capabilities = AdapterCapabilities(chasing = true, watchedEdit = true)
        var resumeCalls = 0
        var failNextUp = false
        var resume: suspend () -> Page<MediaItem> = { Page.empty() }
        fun episode(id: String) = MediaItem(MediaId(id), serverId, provider, id, MediaType.Episode,
            seriesId = MediaId("series"), userData = MediaUserData(playedPercentage = 30.0))
        override suspend fun resumeItems(request: PageRequest): Page<MediaItem> { resumeCalls++; return resume() }
        override suspend fun chasingNextUp(seriesId: MediaId, nextUpDateCutoffEpochMs: Long): MediaItem? {
            if (failNextUp) error("one source failed")
            return null
        }
        override suspend fun item(id: MediaId) = MediaItem(id, serverId, provider, id.value, MediaType.Series,
            userData = MediaUserData(unplayedItemCount = 1))
        override suspend fun collections(request: PageRequest): Page<MediaCollection> = Page.empty()
        override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> = Page.empty()
        override suspend fun search(term: String, request: PageRequest): Page<MediaItem> = Page.empty()
        override suspend fun seasons(seriesId: MediaId): Page<MediaItem> = Page.empty()
        override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> = Page.empty()
        override suspend fun setFavorite(id: MediaId, favorite: Boolean) = Unit
        override suspend fun setWatched(id: MediaId, watched: Boolean) = Unit
        override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation = error("unused")
        override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) = error("unused")
    }
}
