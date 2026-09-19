package cn.mafei.cmp.browser

import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.storage_api.JsonSnapshotStorage
import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.core.storage_api.StorageEditor
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.data.repositories.*
import cn.mafei.cmp.feature.home.MediaBrowser
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class MediaBrowserTest {
    private class Adapter : MediaProviderAdapter {
        override val provider = ProviderKind.Jellyfin
        override val serverId = ServerId("s")
        override val accountId = AccountId("a")
        override val capabilities = AdapterCapabilities()
        fun media(id: String) = MediaItem(MediaId(id), serverId, provider, id)
        var libraryList = listOf("first", "second").map { MediaCollection(CollectionId(it), serverId, provider, it) }
        var fetch: suspend (CollectionId?, PageRequest) -> Page<MediaItem> = { id, _ -> Page(listOf(media(id!!.value))) }
        var detail: suspend (MediaId) -> MediaItem = { media(it.value) }
        var personDetail: suspend (MediaId) -> MediaPerson = {
            MediaPerson(it, serverId, provider, it.value)
        }
        var personWorks: suspend (MediaId, PageRequest) -> Page<MediaItem> = { _, request ->
            Page(listOf(media(request.filter.mediaTypes.single().name)))
        }
        var feed = HomeFeed()
        var homeFailure: Exception? = null
        var seasonFetch: suspend (MediaId) -> Page<MediaItem> = { Page(emptyList()) }
        var episodeFetch: suspend (MediaId, MediaId?, PageRequest) -> Page<MediaItem> = { _, _, _ -> Page(emptyList()) }
        var searchFetch: suspend (String, PageRequest) -> Page<MediaItem> = { term, _ -> Page(listOf(media(term))) }
        val requests = mutableListOf<PageRequest>()
        val itemRequests = mutableListOf<Pair<CollectionId?, PageRequest>>()
        val searchRequests = mutableListOf<Pair<String, PageRequest>>()
        // openPerson 的三路作品查询经 MediaRepository 在 Dispatchers.Default 上真并发，
        // 记录列表必须线程安全，否则偶发丢条目让计数断言失败。
        val personRequests = java.util.Collections.synchronizedList(mutableListOf<Pair<MediaId, PageRequest>>())
        override suspend fun collections(request: PageRequest) = Page(libraryList)
        override suspend fun home(request: HomeFeedRequest): HomeFeed {
            homeFailure?.let { throw it }
            return feed
        }
        override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> {
            requests += request
            itemRequests += collectionId to request
            return fetch(collectionId, request)
        }
        override suspend fun item(id: MediaId) = detail(id)
        override suspend fun person(id: MediaId) = personDetail(id)
        override suspend fun itemsByPerson(personId: MediaId, request: PageRequest): Page<MediaItem> {
            personRequests += personId to request
            return personWorks(personId, request)
        }
        override suspend fun search(term: String, request: PageRequest): Page<MediaItem> {
            searchRequests += term to request
            return searchFetch(term, request)
        }
        override suspend fun seasons(seriesId: MediaId): Page<MediaItem> = seasonFetch(seriesId)
        override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> =
            episodeFetch(seriesId, seasonId, request)
        override suspend fun setFavorite(id: MediaId, favorite: Boolean): Unit = error("unused")
        override suspend fun setWatched(id: MediaId, watched: Boolean): Unit = error("unused")
        override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation = error("unused")
        override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent): Unit = error("unused")
    }
    private suspend fun browser(a: Adapter): MediaBrowser {
        val repository = MediaRepository(FixedEpochClock())
        return MediaBrowser(repository, repository.open(a))
    }

    @Test fun startupLoadsRealCollectionsAndFirstLibraryOnlyOnce() = runBlocking<Unit> {
        val a = Adapter(); val b = browser(a)
        b.start(); b.start()
        assertEquals(CollectionId("first"), b.state.value.selectedCollection)
        assertEquals(listOf("first"), b.state.value.items.map { it.title })
        assertEquals(1, a.requests.size)
        assertFalse(b.state.value.loading)
    }

    @Test fun startupPublishesProviderAuthoritativeHomeFeed() = runBlocking<Unit> {
        val a = Adapter()
        a.feed = HomeFeed(
            recommendations = listOf(a.media("hero")),
            resume = listOf(a.media("resume")),
            nextUp = listOf(a.media("next")),
            latest = listOf(a.media("latest")),
        )
        val b = browser(a)
        b.start()
        assertEquals(listOf("hero"), b.state.value.homeFeed.recommendations.map { it.title })
        assertEquals(listOf("resume"), b.state.value.homeFeed.resume.map { it.title })
        assertEquals(listOf("next"), b.state.value.homeFeed.nextUp.map { it.title })
        assertEquals(listOf("latest"), b.state.value.homeFeed.latest.map { it.title })
        assertNull(b.state.value.homeError)
    }

    @Test fun homeLibraryFeedDoesNotReplaceTheActiveLibraryOrItsItems() = runBlocking<Unit> {
        val a = Adapter()
        a.libraryList = listOf(
            a.libraryList[0].copy(mediaTypes = setOf(MediaType.Movie)),
            a.libraryList[1].copy(mediaTypes = setOf(MediaType.Series)),
        )
        val b = browser(a)
        b.start()
        val initialItems = b.state.value.items
        val initialSelection = b.state.value.selectedCollection

        b.loadHomeLatest(CollectionId("second"))

        assertEquals(initialSelection, b.state.value.selectedCollection)
        assertEquals(initialItems, b.state.value.items)
        assertEquals(listOf("second"), b.state.value.homeLatestByCollection[CollectionId("second")]?.map { it.title })
        val (collectionId, request) = a.itemRequests.last()
        assertEquals(CollectionId("second"), collectionId)
        assertEquals(MediaSort(MediaSortField.DateAdded, SortDirection.Descending), request.sort)
        assertEquals(setOf(MediaType.Series), request.filter.mediaTypes)
        assertFalse(b.state.value.homeLatestLoading.contains(CollectionId("second")))
    }

    @Test fun loadedHomeLibraryFeedIsReusedWithoutAnotherRequest() = runBlocking<Unit> {
        val a = Adapter()
        val b = browser(a)
        b.start()
        b.loadHomeLatest(CollectionId("second"))
        val requestCount = a.itemRequests.size

        b.loadHomeLatest(CollectionId("second"))

        assertEquals(requestCount, a.itemRequests.size)
    }

    @Test fun emptyCollectionsDoNotSendServerWideBrowse() = runBlocking<Unit> {
        val a = Adapter(); a.libraryList = emptyList(); val b = browser(a)
        b.start()
        assertTrue(b.state.value.items.isEmpty()); assertTrue(a.requests.isEmpty())
        assertNull(b.state.value.error); assertFalse(b.state.value.loading)
    }

    @Test fun libraryMediaKindsApplyToBothFirstAndFollowingPages() = runBlocking<Unit> {
        val a = Adapter()
        a.libraryList = listOf(a.libraryList.first().copy(mediaTypes = setOf(MediaType.Series)))
        a.fetch = { _, request -> Page(listOf(a.media(if (request.cursor == null) "a" else "b")),
            if (request.cursor == null) PageCursor("next") else null) }
        val b = browser(a); b.start(); b.loadMore()
        assertEquals(2, a.requests.size)
        a.requests.forEach { assertEquals(setOf(MediaType.Series), it.filter.mediaTypes) }
    }

    @Test fun opaquePagesMergeWithoutDuplicatesAndStopAtEnd() = runBlocking<Unit> {
        val a = Adapter(); a.fetch = { _, request ->
            if (request.cursor == null) Page(listOf(a.media("a"), a.media("b")), PageCursor("next"))
            else Page(listOf(a.media("b").copy(title = "updated"), a.media("c")))
        }
        val b = browser(a); b.start(); b.loadMore(); b.loadMore()
        assertEquals(listOf("a", "updated", "c"), b.state.value.items.map { it.title })
        assertEquals(PageCursor("next"), a.requests.last().cursor)
        assertEquals(2, a.requests.size); assertNull(b.state.value.nextCursor)
    }

    @Test fun failedLoadMoreKeepsItsCursorAndPreviouslyLoadedItems() = runBlocking<Unit> {
        val a = Adapter(); var fail = true
        a.fetch = { _, request ->
            if (request.cursor == null) Page(listOf(a.media("a")), PageCursor("next"))
            else if (fail) throw NetworkUnreachableError("secret-token-in-message")
            else Page(listOf(a.media("b")))
        }
        val b = browser(a); b.start(); b.loadMore()
        assertEquals(listOf("a"), b.state.value.items.map { it.title })
        assertEquals(PageCursor("next"), b.state.value.nextCursor)
        assertFalse(b.state.value.error!!.contains("secret-token"))
        fail = false; b.loadMore()
        assertEquals(2, b.state.value.items.size); assertNull(b.state.value.error)
    }

    @Test fun refreshAndPaginationExposeDifferentBusyStates() = runBlocking<Unit> {
        withTimeout(5000) {
            val a = Adapter()
            a.fetch = { _, request ->
                Page(listOf(a.media(if (request.cursor == null) "first" else "second")), PageCursor("next"))
            }
            val b = browser(a)
            b.start()

            val refreshEntered = CompletableDeferred<Unit>()
            val refreshRelease = CompletableDeferred<Unit>()
            a.fetch = { _, request ->
                if (request.cursor == null) {
                    refreshEntered.complete(Unit)
                    refreshRelease.await()
                }
                Page(listOf(a.media("refreshed")), PageCursor("next"))
            }
            val refreshJob = launch { b.refresh() }
            refreshEntered.await()
            assertTrue(b.state.value.refreshing)
            assertFalse(b.state.value.loading)
            assertFalse(b.state.value.loadingMore)
            refreshRelease.complete(Unit)
            refreshJob.join()

            val pagingEntered = CompletableDeferred<Unit>()
            val pagingRelease = CompletableDeferred<Unit>()
            a.fetch = { _, request ->
                if (request.cursor != null) {
                    pagingEntered.complete(Unit)
                    pagingRelease.await()
                }
                Page(listOf(a.media("paged")))
            }
            val pagingJob = launch { b.loadMore() }
            pagingEntered.await()
            assertTrue(b.state.value.loadingMore)
            assertFalse(b.state.value.loading)
            assertFalse(b.state.value.refreshing)
            pagingRelease.complete(Unit)
            pagingJob.join()
            assertFalse(b.state.value.loadingMore)
        }
    }

    @Test fun lateFirstLibraryCannotOverwriteNewLibrary() = runBlocking<Unit> {
        withTimeout(5000) {
            val a = Adapter(); val b = browser(a); b.start()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            a.fetch = { id, _ ->
                if (id == CollectionId("first")) { entered.complete(Unit); release.await() }
                Page(listOf(a.media(id!!.value)))
            }
            val old = launch { b.select(CollectionId("first")) }; entered.await()
            b.select(CollectionId("second")); release.complete(Unit); old.join()
            assertEquals(CollectionId("second"), b.state.value.selectedCollection)
            assertEquals(listOf("second"), b.state.value.items.map { it.title })
        }
    }

    @Test fun backingOutRejectsLateDetailAndPreservesLibrary() = runBlocking<Unit> {
        withTimeout(5000) {
            val a = Adapter(); val b = browser(a); b.start()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            a.detail = { entered.complete(Unit); release.await(); a.media(it.value) }
            val pending = launch { b.openDetail(MediaId("first")) }; entered.await()
            b.back(); release.complete(Unit); pending.join()
            assertNull(b.state.value.detail); assertNull(b.state.value.detailId)
            assertEquals(listOf("first"), b.state.value.items.map { it.title })
        }
    }

    @Test fun seriesDetailLoadsSortedSeasonsAndFirstSeasonEpisodes() = runBlocking<Unit> {
        val a = Adapter()
        val series = a.media("series").copy(mediaType = MediaType.Series)
        val season1 = a.media("season-1").copy(mediaType = MediaType.Season, seasonNumber = 1)
        val season2 = a.media("season-2").copy(mediaType = MediaType.Season, seasonNumber = 2)
        a.detail = { series }
        a.seasonFetch = { Page(listOf(season2, season1)) }
        a.episodeFetch = { _, seasonId, _ ->
            assertEquals(season1.id, seasonId)
            Page(
                listOf(
                    a.media("episode-2").copy(mediaType = MediaType.Episode, episodeNumber = 2),
                    a.media("episode-1").copy(mediaType = MediaType.Episode, episodeNumber = 1),
                ),
            )
        }
        val b = browser(a)
        b.openDetail(series.id)
        assertEquals(listOf(season1.id, season2.id), b.state.value.detailSeasons.map { it.id })
        assertEquals(season1.id, b.state.value.selectedSeasonId)
        assertEquals(listOf("episode-1", "episode-2"), b.state.value.detailEpisodes.map { it.id.value })
        assertFalse(b.state.value.detailSectionsLoading)
        assertNull(b.state.value.detailSectionsError)
    }

    @Test fun selectingSeasonReloadsEpisodesAndBackClearsSeriesState() = runBlocking<Unit> {
        val a = Adapter()
        val series = a.media("series").copy(mediaType = MediaType.Series)
        val season1 = a.media("season-1").copy(mediaType = MediaType.Season, seasonNumber = 1)
        val season2 = a.media("season-2").copy(mediaType = MediaType.Season, seasonNumber = 2)
        a.detail = { series }
        a.seasonFetch = { Page(listOf(season1, season2)) }
        a.episodeFetch = { _, seasonId, _ -> Page(listOf(a.media("episode-${seasonId?.value}"))) }
        val b = browser(a)
        b.openDetail(series.id)
        b.selectSeason(season2.id)
        assertEquals(season2.id, b.state.value.selectedSeasonId)
        assertEquals(listOf("episode-season-2"), b.state.value.detailEpisodes.map { it.id.value })
        b.back()
        assertTrue(b.state.value.detailSeasons.isEmpty())
        assertNull(b.state.value.selectedSeasonId)
        assertTrue(b.state.value.detailEpisodes.isEmpty())
    }

    @Test fun personPageLoadsLegacyWorkGroupsAndClearsIndependently() = runBlocking<Unit> {
        val a = Adapter()
        a.personDetail = { id ->
            MediaPerson(id, a.serverId, a.provider, "Actor", overview = "Biography")
        }
        val b = browser(a)

        b.openPerson(MediaId("person"))
        assertEquals("Actor", b.state.value.person?.name)
        assertEquals(listOf("Movie"), b.state.value.personMovies.map { it.title })
        assertEquals(listOf("Series"), b.state.value.personSeries.map { it.title })
        assertEquals(listOf("Episode"), b.state.value.personEpisodes.map { it.title })
        assertEquals(3, a.personRequests.size)
        a.personRequests.forEach { (id, request) ->
            assertEquals(MediaId("person"), id)
            assertEquals(10, request.pageSize)
            assertEquals(MediaSort(MediaSortField.Title, SortDirection.Ascending), request.sort)
            assertEquals(1, request.filter.mediaTypes.size)
        }
        b.clearPerson()
        assertNull(b.state.value.person)
        assertNull(b.state.value.personId)
        assertTrue(b.state.value.personMovies.isEmpty())
    }

    @Test fun searchTrimsTermAndMergesOpaquePages() = runBlocking<Unit> {
        val a = Adapter()
        a.searchFetch = { term, request ->
            assertEquals("query", term)
            if (request.cursor == null) Page(listOf(a.media("one")), PageCursor("next"))
            else Page(listOf(a.media("one").copy(title = "updated"), a.media("two")))
        }
        val b = browser(a)
        b.search("  query  ")
        b.loadMoreSearch()
        assertEquals("query", b.state.value.searchQuery)
        assertEquals(listOf("updated", "two"), b.state.value.searchItems.map { it.title })
        assertNull(b.state.value.searchNextCursor)
        assertEquals(PageCursor("next"), a.searchRequests.last().second.cursor)
    }

    @Test fun clearingSearchRejectsLateResults() = runBlocking<Unit> {
        withTimeout(5000) {
            val a = Adapter()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            a.searchFetch = { _, _ -> entered.complete(Unit); release.await(); Page(listOf(a.media("late"))) }
            val b = browser(a)
            val pending = launch { b.search("late") }
            entered.await()
            b.clearSearch()
            release.complete(Unit)
            pending.join()
            assertTrue(b.state.value.searchItems.isEmpty())
            assertTrue(b.state.value.searchQuery.isEmpty())
            assertFalse(b.state.value.searchLoading)
        }
    }

    @Test fun offlineRefreshLabelsStaleContent() = runBlocking<Unit> {
        val a = Adapter(); val b = browser(a); b.start()
        a.fetch = { _, _ -> throw NetworkUnreachableError() }; b.refresh()
        assertTrue(b.state.value.stale); assertEquals(1, b.state.value.items.size)
    }

    @Test fun authenticationFailureNeverShowsRawServerMessage() = runBlocking<Unit> {
        val a = Adapter(); a.fetch = { _, _ -> throw AuthenticationError(AuthFailureKind.TokenExpired, "token=secret") }
        val b = browser(a); b.start()
        assertEquals("登录已失效，请退出后重新登录", b.state.value.error)
        assertFalse(b.state.value.loading); assertTrue(b.state.value.items.isEmpty())
    }

    @Test fun cancellationPropagatesWithoutAnErrorBanner() = runBlocking<Unit> {
        val a = Adapter(); a.fetch = { _, _ -> throw CancellationException("cancelled") }
        val b = browser(a)
        assertFailsWith<CancellationException> { b.start() }
        assertNull(b.state.value.error); assertFalse(b.state.value.loading)
    }

    @Test fun replacedLibraryCannotShowPreviousLibraryItemsOnFailure() = runBlocking<Unit> {
        val a = Adapter(); val b = browser(a); b.start()
        a.libraryList = a.libraryList.drop(1)
        a.fetch = { _, _ -> throw NetworkUnreachableError() }; b.refresh()
        assertEquals(CollectionId("second"), b.state.value.selectedCollection)
        assertTrue(b.state.value.items.isEmpty())
    }

    @Test fun coldStartPaintsThePersistedHomeSnapshotAndSurvivesANetworkFailure() = runBlocking<Unit> {
        val snapshots = JsonSnapshotStorage(MapSettingsStorage(), Json { ignoreUnknownKeys = true }) { 1000L }
        // Warm session: a successful refresh persists the durable snapshot.
        val warmAdapter = Adapter()
        warmAdapter.feed = HomeFeed(latest = listOf(warmAdapter.media("cached-latest")))
        val warmRepository = MediaRepository(FixedEpochClock(), homeSnapshots = snapshots)
        MediaBrowser(warmRepository, warmRepository.open(warmAdapter)).start()

        // Cold start (process restart): the provider is unreachable, the page
        // must still open on the previous session's home — not a blank state.
        val coldAdapter = Adapter()
        coldAdapter.feed = HomeFeed(latest = listOf(coldAdapter.media("fresh-latest")))
        coldAdapter.homeFailure = NetworkUnreachableError()
        coldAdapter.fetch = { _, _ -> throw NetworkUnreachableError() }
        val coldRepository = MediaRepository(FixedEpochClock(), homeSnapshots = snapshots)
        val cold = MediaBrowser(coldRepository, coldRepository.open(coldAdapter))
        cold.start()

        assertEquals(listOf("cached-latest"), cold.state.value.homeFeed.latest.map { it.title })
        assertNotNull(cold.state.value.homeError) // the failed refresh stays honest
        assertFalse(cold.state.value.homeStale) // snapshot display is not an offline banner
    }

    @Test fun liveNetworkHomeReplacesThePersistedSnapshot() = runBlocking<Unit> {
        val snapshots = JsonSnapshotStorage(MapSettingsStorage(), Json { ignoreUnknownKeys = true }) { 1000L }
        val warmAdapter = Adapter()
        warmAdapter.feed = HomeFeed(latest = listOf(warmAdapter.media("cached-latest")))
        val warmRepository = MediaRepository(FixedEpochClock(), homeSnapshots = snapshots)
        MediaBrowser(warmRepository, warmRepository.open(warmAdapter)).start()

        val coldAdapter = Adapter()
        coldAdapter.feed = HomeFeed(latest = listOf(coldAdapter.media("fresh-latest")))
        val coldRepository = MediaRepository(FixedEpochClock(), homeSnapshots = snapshots)
        val cold = MediaBrowser(coldRepository, coldRepository.open(coldAdapter))
        cold.start()

        assertEquals(listOf("fresh-latest"), cold.state.value.homeFeed.latest.map { it.title })
        assertNull(cold.state.value.homeError)
        assertFalse(cold.state.value.homeStale)
    }
}

/** Minimal in-memory [SettingsStorage] for the home snapshot persistence tests. */
private class MapSettingsStorage : SettingsStorage {
    private val values = mutableMapOf<String, String>()
    override suspend fun getString(key: String): String? = values[key]
    override suspend fun putString(key: String, value: String) { values[key] = value }
    override suspend fun getBytes(key: String): ByteArray? = null
    override suspend fun putBytes(key: String, value: ByteArray) = error("unused")
    override suspend fun remove(key: String): Boolean = values.remove(key) != null
    override suspend fun contains(key: String): Boolean = values.containsKey(key)
    override suspend fun listKeys(prefix: String?): List<String> =
        values.keys.filter { prefix == null || it.startsWith(prefix) }.sorted()
    override suspend fun edit(mutate: StorageEditor.() -> Unit) = error("unused")
}
