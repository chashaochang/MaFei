package cn.mafei.cmp.browser

import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.storage_api.JsonSnapshotStorage
import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.core.storage_api.StorageEditor
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.data.repositories.*
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.MediaPlaybackPreferences
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class MediaBrowserTest {
    private class Adapter : MediaProviderAdapter {
        override val provider = ProviderKind.Jellyfin
        override val serverId = ServerId("s")
        override val accountId = AccountId("a")
        override var capabilities = AdapterCapabilities()
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
        var homeFetch: (suspend (HomeFeedRequest) -> HomeFeed)? = null
        var seasonFetch: suspend (MediaId) -> Page<MediaItem> = { Page(emptyList()) }
        var episodeFetch: suspend (MediaId, MediaId?, PageRequest) -> Page<MediaItem> = { _, _, _ -> Page(emptyList()) }
        var nextUpFetch: suspend (MediaId) -> MediaItem? = { null }
        var resumeFetch: suspend (PageRequest) -> Page<MediaItem> = { Page.empty() }
        var searchFetch: suspend (String, PageRequest) -> Page<MediaItem> = { term, _ -> Page(listOf(media(term))) }
        val requests = mutableListOf<PageRequest>()
        val itemRequests = mutableListOf<Pair<CollectionId?, PageRequest>>()
        val searchRequests = mutableListOf<Pair<String, PageRequest>>()
        val resumeRequests = mutableListOf<PageRequest>()
        val episodeRequests = mutableListOf<Triple<MediaId, MediaId?, PageRequest>>()
        // openPerson 的三路作品查询经 MediaRepository 在 Dispatchers.Default 上真并发，
        // 记录列表必须线程安全，否则偶发丢条目让计数断言失败。
        val personRequests = java.util.Collections.synchronizedList(mutableListOf<Pair<MediaId, PageRequest>>())
        override suspend fun collections(request: PageRequest) = Page(libraryList)
        override suspend fun home(request: HomeFeedRequest): HomeFeed {
            homeFailure?.let { throw it }
            return homeFetch?.invoke(request) ?: feed
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
        override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> {
            episodeRequests += Triple(seriesId, seasonId, request)
            return episodeFetch(seriesId, seasonId, request)
        }
        override suspend fun nextUp(seriesId: MediaId): MediaItem? = nextUpFetch(seriesId)
        override suspend fun resumeItems(request: PageRequest): Page<MediaItem> {
            resumeRequests += request
            return resumeFetch(request)
        }
        var favoriteAction: suspend (MediaId, Boolean) -> Unit = { _, _ -> error("unused") }
        override suspend fun setFavorite(id: MediaId, favorite: Boolean) = favoriteAction(id, favorite)
        override suspend fun setWatched(id: MediaId, watched: Boolean): Unit = error("unused")
        override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation = error("unused")
        override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent): Unit = error("unused")
    }
    private class PlaybackPreferences : MediaPlaybackPreferences {
        val values = linkedMapOf<Pair<AccountId, MediaId>, String>()
        override suspend fun preferredMediaSourceId(accountId: AccountId, mediaId: MediaId): String? =
            values[accountId to mediaId]

        override suspend fun setPreferredMediaSourceId(
            accountId: AccountId,
            mediaId: MediaId,
            sourceId: String,
        ) {
            values[accountId to mediaId] = sourceId
        }
    }

    private suspend fun browser(a: Adapter, preferences: MediaPlaybackPreferences? = null): MediaBrowser {
        val repository = MediaRepository(FixedEpochClock())
        return MediaBrowser(repository, repository.open(a), capabilities = a.capabilities, playbackPreferences = preferences)
    }

    @Test fun favoriteFailureRollsBackTheSameMediaAcrossIndependentControllers() = runBlocking<Unit> {
        val a = Adapter().apply { capabilities = AdapterCapabilities(favoritesEdit = true) }
        val item = a.media("shared").copy(mediaType = MediaType.Movie)
        a.fetch = { _, _ -> Page(listOf(item)) }
        a.searchFetch = { _, _ -> Page(listOf(item)) }
        a.detail = { item }
        a.feed = HomeFeed(latest = listOf(item))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        a.favoriteAction = { _, _ -> entered.complete(Unit); release.await(); throw NetworkUnreachableError() }
        val b = browser(a)
        b.start(); b.search("shared"); b.openDetail(item.id)
        val mutation = launch { b.toggleFavorite(item) }
        entered.await()
        fun favorites() = b.state.value.let { state -> listOf(state.items.single(),
            state.searchItems.single(), state.homeFeed.latest.single(), state.detail!!).map { it.userData.isFavorite } }
        assertEquals(listOf(true, true, true, true), favorites())
        release.complete(Unit); mutation.join()
        assertEquals(listOf(false, false, false, false), favorites())
    }

    @Test fun detailActionCompletionCannotOverwriteANewerDetailGeneration() = runBlocking<Unit> {
        val a = Adapter().apply { capabilities = AdapterCapabilities(favoritesEdit = true) }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        a.favoriteAction = { _, _ -> entered.complete(Unit); release.await() }
        val b = browser(a)
        b.openDetail(MediaId("first"))
        val mutation = launch { b.toggleFavorite() }
        entered.await()
        b.openDetail(MediaId("second"))
        release.complete(Unit); mutation.join()
        assertEquals(MediaId("second"), b.state.value.detail?.id)
        assertFalse(b.state.value.detail!!.userData.isFavorite)
        assertFalse(b.state.value.detailActionBusy)
    }

    @Test fun startupLoadsRealCollectionsAndFirstLibraryOnlyOnce() = runBlocking<Unit> {
        val a = Adapter(); val b = browser(a)
        b.start(); b.start()
        assertEquals(CollectionId("first"), b.state.value.selectedCollection)
        assertEquals(listOf("first"), b.state.value.items.map { it.title })
        assertEquals(1, a.requests.size)
        assertFalse(b.state.value.loading)
    }

    @Test fun newBrowserHasNotConfirmedAnEmptyLibraryBeforeStartup() = runBlocking<Unit> {
        val b = browser(Adapter())
        assertTrue(b.state.value.loading)
        assertTrue(b.state.value.homeLoading)
    }

    @Test fun firstLibraryPageDoesNotFinishAPendingHomeFeed() = runBlocking<Unit> {
        withTimeout(5000) {
            val a = Adapter()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            a.homeFetch = { entered.complete(Unit); release.await(); HomeFeed(latest = listOf(a.media("home"))) }
            val b = browser(a)
            val pending = launch { b.start() }
            entered.await()
            while (b.state.value.loading || b.state.value.items.isEmpty()) yield()
            assertTrue(b.state.value.homeLoading)
            assertEquals(HomeFeed(), b.state.value.homeFeed)
            release.complete(Unit)
            pending.join()
            assertFalse(b.state.value.homeLoading)
            assertEquals("home", b.state.value.homeFeed.latest.single().title)
        }
    }

    @Test fun homeFailureAndCancellationClearOnlyTheHomeLoadingState() = runBlocking<Unit> {
        val failed = Adapter().apply { homeFetch = { throw NetworkUnreachableError() } }
        val failedBrowser = browser(failed)
        failedBrowser.start()
        assertFalse(failedBrowser.state.value.homeLoading)
        assertNotNull(failedBrowser.state.value.homeError)
        assertTrue(failedBrowser.state.value.items.isNotEmpty())

        val cancelled = browser(Adapter().apply { homeFetch = { throw CancellationException("cancel home") } })
        assertFailsWith<CancellationException> { cancelled.start() }
        assertFalse(cancelled.state.value.homeLoading)
        assertNull(cancelled.state.value.homeError)
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

    @Test fun backgroundHomeRefreshKeepsTheDocumentStableAndOnlyReplacesChangedShelves() = runBlocking<Unit> {
        withTimeout(5000) {
            val a = Adapter()
            a.feed = HomeFeed(
                recommendations = listOf(a.media("hero")),
                resume = listOf(a.media("resume")),
                nextUp = listOf(a.media("next")),
                latest = listOf(a.media("latest")),
            )
            val b = browser(a)
            b.start()
            val before = b.state.value
            val itemRequestCount = a.itemRequests.size
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            a.homeFetch = {
                entered.complete(Unit)
                release.await()
                HomeFeed(
                    recommendations = listOf(a.media("hero")),
                    resume = listOf(a.media("resume")),
                    nextUp = listOf(a.media("next-fresh")),
                    latest = listOf(a.media("latest")),
                )
            }

            val refresh = launch { b.refreshHome() }
            entered.await()

            assertEquals(before, b.state.value)
            assertFalse(b.state.value.loading)
            assertFalse(b.state.value.refreshing)
            assertEquals(itemRequestCount, a.itemRequests.size)

            release.complete(Unit)
            refresh.join()
            val after = b.state.value
            assertSame(before.homeFeed.recommendations, after.homeFeed.recommendations)
            assertSame(before.homeFeed.resume, after.homeFeed.resume)
            assertNotSame(before.homeFeed.nextUp, after.homeFeed.nextUp)
            assertSame(before.homeFeed.latest, after.homeFeed.latest)
            assertEquals(listOf("next-fresh"), after.homeFeed.nextUp.map { it.title })
            assertEquals(before.copy(homeFeed = after.homeFeed), after)
        }
    }

    @Test fun failedBackgroundHomeRefreshKeepsExistingContentAndPageStateUntouched() = runBlocking<Unit> {
        val a = Adapter()
        a.feed = HomeFeed(latest = listOf(a.media("visible")))
        val b = browser(a)
        b.start()
        val before = b.state.value
        // The repository serves its cached feed as StaleCache for a transient
        // network failure. The browser must still avoid publishing a stale
        // banner because that would insert a row and move the visible content.
        a.homeFetch = { throw NetworkUnreachableError() }

        b.refreshHome()

        assertEquals(before, b.state.value)
    }

    @Test fun newerBackgroundHomeRefreshCannotBeOverwrittenByAnOlderResponse() = runBlocking<Unit> {
        withTimeout(5000) {
            val a = Adapter()
            a.feed = HomeFeed(latest = listOf(a.media("initial")))
            val b = browser(a)
            b.start()
            val calls = java.util.concurrent.atomic.AtomicInteger()
            val firstEntered = CompletableDeferred<Unit>()
            val firstRelease = CompletableDeferred<Unit>()
            val secondEntered = CompletableDeferred<Unit>()
            val secondRelease = CompletableDeferred<Unit>()
            a.homeFetch = {
                when (calls.incrementAndGet()) {
                    1 -> {
                        firstEntered.complete(Unit)
                        firstRelease.await()
                        HomeFeed(latest = listOf(a.media("older")))
                    }
                    else -> {
                        secondEntered.complete(Unit)
                        secondRelease.await()
                        HomeFeed(latest = listOf(a.media("newer")))
                    }
                }
            }

            val older = launch { b.refreshHome() }
            firstEntered.await()
            val newer = launch { b.refreshHome() }
            secondEntered.await()
            secondRelease.complete(Unit)
            newer.join()
            firstRelease.complete(Unit)
            older.join()

            assertEquals(listOf("newer"), b.state.value.homeFeed.latest.map { it.title })
            assertFalse(b.state.value.loading)
            assertFalse(b.state.value.refreshing)
        }
    }

    @Test fun backgroundHomeRefreshFailureIsVisibleOnlyWhenHomeWasAlreadyEmpty() = runBlocking<Unit> {
        val a = Adapter()
        val b = browser(a)
        b.start()
        a.homeFetch = { throw ProtocolDecodingError("bad refresh payload") }

        b.refreshHome()

        assertEquals("服务器返回了无法识别的数据", b.state.value.homeError)
        assertFalse(b.state.value.loading)
        assertFalse(b.state.value.refreshing)
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

    @Test fun backgroundDetailRefreshKeepsThePresentedDocumentUntilFreshDataArrives() = runBlocking<Unit> {
        withTimeout(5000) {
            val a = Adapter()
            val source = MediaSourceInfo(
                id = "source",
                streams = listOf(
                    MediaStreamInfo(index = 1, kind = MediaStreamKind.Audio),
                    MediaStreamInfo(index = 2, kind = MediaStreamKind.Audio),
                    MediaStreamInfo(index = 3, kind = MediaStreamKind.Subtitle),
                ),
            )
            val movie = a.media("movie").copy(canPlay = true, mediaSources = listOf(source))
            a.detail = { movie }
            val b = browser(a)
            b.openDetail(movie.id)
            b.selectAudioTrack(2)
            b.selectSubtitleTrack(-1)
            val before = b.state.value
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            a.detail = {
                entered.complete(Unit)
                release.await()
                movie.copy(title = "fresh movie")
            }

            val refresh = launch { b.refreshDetail(movie.id) }
            entered.await()

            assertEquals(before, b.state.value)
            assertFalse(b.state.value.detailLoading)

            release.complete(Unit)
            refresh.join()
            val after = b.state.value
            assertEquals("fresh movie", after.detail?.title)
            assertEquals("fresh movie", after.detailPlayTarget?.title)
            assertEquals(2, after.selectedAudioStreamIndex)
            assertEquals(-1, after.selectedSubtitleStreamIndex)
            assertFalse(after.detailLoading)
            assertEquals(before.detailSeasons, after.detailSeasons)
            assertEquals(before.detailEpisodes, after.detailEpisodes)
        }
    }

    @Test fun failedBackgroundDetailRefreshKeepsExistingDetailAndStateUntouched() = runBlocking<Unit> {
        val a = Adapter()
        val movie = a.media("movie").copy(canPlay = true)
        a.detail = { movie }
        val b = browser(a)
        b.openDetail(movie.id)
        val before = b.state.value
        a.detail = { throw NetworkUnreachableError() }

        b.refreshDetail(movie.id)

        assertEquals(before, b.state.value)
    }

    @Test fun newerBackgroundDetailRefreshCannotBeOverwrittenByAnOlderResponse() = runBlocking<Unit> {
        withTimeout(5000) {
            val a = Adapter()
            val movie = a.media("movie").copy(canPlay = true)
            a.detail = { movie }
            val b = browser(a)
            b.openDetail(movie.id)
            val calls = java.util.concurrent.atomic.AtomicInteger()
            val firstEntered = CompletableDeferred<Unit>()
            val firstRelease = CompletableDeferred<Unit>()
            val secondEntered = CompletableDeferred<Unit>()
            val secondRelease = CompletableDeferred<Unit>()
            a.detail = {
                when (calls.incrementAndGet()) {
                    1 -> {
                        firstEntered.complete(Unit)
                        firstRelease.await()
                        movie.copy(title = "older")
                    }
                    else -> {
                        secondEntered.complete(Unit)
                        secondRelease.await()
                        movie.copy(title = "newer")
                    }
                }
            }

            val older = launch { b.refreshDetail(movie.id) }
            firstEntered.await()
            val newer = launch { b.refreshDetail(movie.id) }
            secondEntered.await()
            secondRelease.complete(Unit)
            newer.join()
            firstRelease.complete(Unit)
            older.join()

            assertEquals("newer", b.state.value.detail?.title)
            assertFalse(b.state.value.detailLoading)
        }
    }

    @Test fun detailRestoresPerItemSourceAndUsesThatSourcesLegacyTrackDefaults() = runBlocking<Unit> {
        val a = Adapter()
        val first = MediaSourceInfo(
            id = "source-1",
            defaultAudioIndex = 1,
            defaultSubtitleIndex = -1,
            streams = listOf(
                MediaStreamInfo(index = 1, kind = MediaStreamKind.Audio),
                MediaStreamInfo(index = 2, kind = MediaStreamKind.Subtitle),
            ),
        )
        val second = MediaSourceInfo(
            id = "source-2",
            defaultAudioIndex = 7,
            defaultSubtitleIndex = 8,
            streams = listOf(
                MediaStreamInfo(index = 7, kind = MediaStreamKind.Audio),
                MediaStreamInfo(index = 8, kind = MediaStreamKind.Subtitle),
            ),
        )
        val movie = a.media("movie").copy(canPlay = true, mediaSources = listOf(first, second))
        a.detail = { movie }
        val preferences = PlaybackPreferences().apply {
            values[a.accountId to movie.id] = "source-2"
        }
        val b = browser(a, preferences)

        b.openDetail(movie.id)

        assertEquals("source-2", b.state.value.selectedMediaSourceId)
        assertEquals(7, b.state.value.selectedAudioStreamIndex)
        assertEquals(8, b.state.value.selectedSubtitleStreamIndex)
        assertEquals("source-2", b.playbackOptionsFor(movie).mediaSourceId)

        b.selectMediaSource("source-1")

        assertEquals("source-1", b.state.value.selectedMediaSourceId)
        assertEquals(1, b.state.value.selectedAudioStreamIndex)
        assertEquals(-1, b.state.value.selectedSubtitleStreamIndex)
        assertEquals("source-1", preferences.values[a.accountId to movie.id])
    }

    @Test fun detailSelectsFirstPlayableSourceInsteadOfLeavingServerToGuess() = runBlocking<Unit> {
        val a = Adapter()
        val movie = a.media("movie").copy(
            canPlay = true,
            mediaSources = listOf(
                MediaSourceInfo(id = "blocked", playable = false),
                MediaSourceInfo(id = "playable", playable = true),
            ),
        )
        a.detail = { movie }
        val b = browser(a)

        b.openDetail(movie.id)

        assertEquals("playable", b.state.value.selectedMediaSourceId)
        assertEquals("playable", b.playbackOptionsFor(movie).mediaSourceId)
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

    @Test fun seriesDetailUsesLegacyPlaybackTargetSeasonEpisodeAndProgress() = runBlocking<Unit> {
        val a = Adapter()
        val target = MediaPlaybackTarget(
            id = MediaId("episode-2"),
            seasonNumber = 2,
            episodeNumber = 2,
            playbackPositionMs = 90_000L,
            playedPercentage = 25.0,
            durationMs = 360_000L,
        )
        val series = a.media("series").copy(mediaType = MediaType.Series, playbackTarget = target)
        val season1 = a.media("season-1").copy(mediaType = MediaType.Season, seasonNumber = 1)
        val season2 = a.media("season-2").copy(mediaType = MediaType.Season, seasonNumber = 2)
        a.detail = { series }
        a.seasonFetch = { Page(listOf(season1, season2)) }
        a.episodeFetch = { _, seasonId, request ->
            assertEquals(season2.id, seasonId)
            assertEquals(500, request.pageSize)
            Page(
                listOf(
                    a.media("episode-1").copy(
                        mediaType = MediaType.Episode,
                        seasonNumber = 2,
                        episodeNumber = 1,
                        canPlay = true,
                    ),
                    a.media("episode-2").copy(
                        mediaType = MediaType.Episode,
                        seasonNumber = 2,
                        episodeNumber = 2,
                        runtimeMs = 300_000L,
                        canPlay = true,
                    ),
                ),
            )
        }

        val b = browser(a)
        b.openDetail(series.id)

        assertEquals(season2.id, b.state.value.selectedSeasonId)
        assertEquals(MediaId("episode-2"), b.state.value.detailPlayTarget?.id)
        assertEquals(90_000L, b.state.value.detailPlayTarget?.userData?.playbackPositionMs)
        assertEquals(360_000L, b.state.value.detailPlayTarget?.runtimeMs)
        assertEquals(b.state.value.detailPlayTarget, b.state.value.selectedSeasonPlayTarget)
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

    @Test fun episodeDetailRestoresVisibleSeasonWithoutClearingItsEpisodes() = runBlocking<Unit> {
        val a = Adapter()
        val series = a.media("series").copy(mediaType = MediaType.Series)
        val season = a.media("season-1").copy(mediaType = MediaType.Season, seasonNumber = 1)
        val episodeSummary = a.media("episode-1").copy(
            mediaType = MediaType.Episode,
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Episode summary",
        )
        val episodeDetail = episodeSummary.copy(title = "Episode detail")
        a.detail = { id ->
            when (id) {
                series.id -> series
                episodeDetail.id -> episodeDetail
                else -> a.media(id.value)
            }
        }
        a.seasonFetch = { Page(listOf(season)) }
        a.episodeFetch = { _, _, _ -> Page(listOf(episodeSummary)) }
        val b = browser(a)

        b.openDetail(series.id)
        b.selectSeason(season.id)
        b.openDetail(episodeDetail.id)

        assertEquals(episodeDetail.id, b.state.value.detail?.id)
        assertTrue(b.restoreSeasonContext(series.id, season.id))
        assertEquals(series.id, b.state.value.detail?.id)
        assertFalse(b.state.value.detailLoading)
        assertEquals(listOf("Episode detail"), b.state.value.detailEpisodes.map { it.title })
        assertEquals(season.id, b.state.value.selectedSeasonId)
        assertFalse(b.state.value.detailSectionsLoading)
    }

    @Test fun seasonEpisodesStopAtTheLegacyThousandItemWindow() = runBlocking<Unit> {
        val a = Adapter()
        val series = a.media("series").copy(mediaType = MediaType.Series)
        val season = a.media("season").copy(mediaType = MediaType.Season, seasonNumber = 1)
        a.detail = { series }
        a.seasonFetch = { Page(listOf(season)) }
        a.episodeFetch = { _, _, request ->
            when (request.cursor?.value) {
                null -> Page(
                    listOf(a.media("episode-1").copy(mediaType = MediaType.Episode, episodeNumber = 1)),
                    PageCursor("second"),
                )
                "second" -> Page(
                    listOf(a.media("episode-2").copy(mediaType = MediaType.Episode, episodeNumber = 2)),
                    PageCursor("must-not-load"),
                )
                else -> error("legacy 1000-item window must not request a third page")
            }
        }

        val b = browser(a)
        b.openDetail(series.id)

        assertEquals(listOf("episode-1", "episode-2"), b.state.value.detailEpisodes.map { it.id.value })
        assertEquals(2, a.episodeRequests.size)
        assertEquals(listOf(null, PageCursor("second")), a.episodeRequests.map { it.third.cursor })
        assertTrue(a.episodeRequests.all { it.third.pageSize == 500 })
    }

    @Test fun personPageLoadsLegacyWorkGroupsAndClearsIndependently() = runBlocking<Unit> {
        val a = Adapter()
        a.personDetail = { id ->
            MediaPerson(id, a.serverId, a.provider, "Actor", overview = "Biography")
        }
        val b = browser(a)

        b.openPerson(MediaId("person"), "Actor")
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

    @Test fun personAndWorkGroupsSurviveOpeningAWorkAndReturningFromItsDetail() = runBlocking<Unit> {
        val a = Adapter()
        a.personDetail = { id ->
            MediaPerson(id, a.serverId, a.provider, "Actor", overview = "Biography")
        }
        val b = browser(a)

        b.openPerson(MediaId("person"), "Actor")
        val person = b.state.value.person
        val movies = b.state.value.personMovies
        val series = b.state.value.personSeries
        val episodes = b.state.value.personEpisodes

        b.openDetail(movies.single().id)
        assertEquals(movies.single().id, b.state.value.detailId)
        b.back()

        assertNull(b.state.value.detailId)
        assertEquals(person, b.state.value.person)
        assertEquals(movies, b.state.value.personMovies)
        assertEquals(series, b.state.value.personSeries)
        assertEquals(episodes, b.state.value.personEpisodes)
    }

    @Test fun searchReproducesLegacyEightHundredItemSingleScreenLoad() = runBlocking<Unit> {
        val a = Adapter()
        a.searchFetch = { term, request ->
            assertEquals("query", term)
            assertEquals(setOf(MediaType.Episode, MediaType.Movie, MediaType.Video, MediaType.Series),
                request.filter.mediaTypes)
            if (request.cursor == null) {
                assertEquals(PageRequest.MAX_PAGE_SIZE, request.pageSize)
                Page((0 until 500).map { a.media("item-$it") }, PageCursor("next"), totalEstimate = 900)
            } else {
                assertEquals(PageCursor("next"), request.cursor)
                assertEquals(300, request.pageSize)
                Page((500 until 800).map { a.media("item-$it") }, PageCursor("ignored-after-legacy-limit"),
                    totalEstimate = 900)
            }
        }
        val b = browser(a)
        assertTrue(b.search("  query  "))
        assertEquals("query", b.state.value.searchQuery)
        assertEquals(800, b.state.value.searchItems.size)
        assertEquals("item-0", b.state.value.searchItems.first().title)
        assertEquals("item-799", b.state.value.searchItems.last().title)
        assertEquals(800, b.state.value.searchTotalEstimate)
        assertNull(b.state.value.searchNextCursor)
        assertEquals(2, a.searchRequests.size)
    }

    @Test fun historyUsesLegacySourcesDedupesSeriesAndEnrichesLatestEpisode() = runBlocking<Unit> {
        val a = Adapter()
        val seriesId = MediaId("series-a")
        fun history(
            id: String,
            type: MediaType,
            playedAt: Long,
            series: MediaId? = null,
            season: Int? = null,
            episode: Int? = null,
        ) = a.media(id).copy(
            mediaType = type,
            seriesId = series,
            seriesTitle = if (series != null) "Series A" else null,
            seasonNumber = season,
            episodeNumber = episode,
            userData = MediaUserData(lastPlayedEpochMs = playedAt),
        )
        a.resumeFetch = { request ->
            assertEquals(10, request.pageSize)
            assertEquals(setOf(MediaType.Movie, MediaType.Episode), request.filter.mediaTypes)
            Page.of(listOf(history("episode-new", MediaType.Episode, 200, seriesId, 1, 3)))
        }
        a.fetch = { collectionId, request ->
            assertNull(collectionId)
            assertEquals(10, request.pageSize)
            assertEquals(setOf(MediaType.Movie, MediaType.Episode), request.filter.mediaTypes)
            assertTrue(request.filter.playedOnly)
            assertEquals(MediaSort(MediaSortField.DatePlayed, SortDirection.Descending), request.sort)
            Page.of(
                listOf(
                    history("movie", MediaType.Movie, 300),
                    history("episode-old", MediaType.Episode, 100, seriesId, 1, 2),
                ),
            )
        }
        a.episodeFetch = { requestedSeries, seasonId, request ->
            assertEquals(seriesId, requestedSeries)
            assertNull(seasonId)
            assertEquals(PageRequest.MAX_PAGE_SIZE, request.pageSize)
            Page.of(
                listOf(
                    a.media("s1e12").copy(mediaType = MediaType.Episode, seasonNumber = 1, episodeNumber = 12),
                    a.media("s2e3").copy(mediaType = MediaType.Episode, seasonNumber = 2, episodeNumber = 3),
                    a.media("s2e8").copy(mediaType = MediaType.Episode, seasonNumber = 2, episodeNumber = 8),
                ),
            )
        }
        val b = browser(a)

        b.loadHistory()

        val items = b.state.value.historyItems
        assertEquals(listOf("movie", "episode-new"), items.map { it.media.id.value })
        assertEquals(2, items[1].latestSeasonNumber)
        assertEquals(8, items[1].latestEpisodeNumber)
        assertTrue(items[1].seriesHasMultipleSeasons)
        assertNull(b.state.value.historyError)
        assertFalse(b.state.value.historyLoadMoreFailed)
    }

    @Test fun historyKeepsSuccessfulSourceAndRetriesFailedFirstPageFromTheFooter() = runBlocking<Unit> {
        val a = Adapter()
        var resumeFails = true
        a.resumeFetch = {
            if (resumeFails) throw NetworkUnreachableError()
            Page.of(
                listOf(
                    a.media("resume").copy(
                        mediaType = MediaType.Movie,
                        userData = MediaUserData(lastPlayedEpochMs = 200),
                    ),
                ),
            )
        }
        a.fetch = { _, _ ->
            Page.of(
                listOf(
                    a.media("completed").copy(
                        mediaType = MediaType.Movie,
                        userData = MediaUserData(isPlayed = true, lastPlayedEpochMs = 100),
                    ),
                ),
            )
        }
        val b = browser(a)

        b.loadHistory()

        assertEquals(listOf("completed"), b.state.value.historyItems.map { it.media.id.value })
        assertNull(b.state.value.historyError)
        assertTrue(b.state.value.historyLoadMoreFailed)
        assertTrue(b.state.value.historyCanLoadMore)

        resumeFails = false
        b.retryLoadMoreHistory()

        assertEquals(listOf("resume", "completed"), b.state.value.historyItems.map { it.media.id.value })
        assertFalse(b.state.value.historyLoadMoreFailed)
        assertFalse(b.state.value.historyCanLoadMore)
        assertEquals(listOf(null, null), a.resumeRequests.map { it.cursor })
    }

    @Test fun historyShowsWholePageErrorOnlyWhenNoSourceCanSupplyContent() = runBlocking<Unit> {
        val a = Adapter()
        a.resumeFetch = { throw NetworkUnreachableError() }
        a.fetch = { _, _ -> throw TimeoutError() }
        val b = browser(a)

        b.loadHistory()

        assertTrue(b.state.value.historyItems.isEmpty())
        assertEquals("播放历史加载失败，点击重试", b.state.value.historyError)
        assertFalse(b.state.value.historyLoadMoreFailed)
    }

    @Test fun historyPagingFailurePreservesRowsAndUsesExplicitRetry() = runBlocking<Unit> {
        val a = Adapter()
        var resumeTailFails = true
        a.resumeFetch = { request ->
            if (request.cursor == null) {
                Page.of(
                    listOf(
                        a.media("resume-1").copy(
                            mediaType = MediaType.Movie,
                            userData = MediaUserData(lastPlayedEpochMs = 400),
                        ),
                    ),
                    nextCursor = PageCursor("resume-next"),
                )
            } else if (resumeTailFails) {
                throw NetworkUnreachableError()
            } else {
                Page.of(
                    listOf(
                        a.media("resume-2").copy(
                            mediaType = MediaType.Movie,
                            userData = MediaUserData(lastPlayedEpochMs = 200),
                        ),
                    ),
                )
            }
        }
        a.fetch = { _, request ->
            if (request.cursor == null) {
                Page.of(
                    listOf(
                        a.media("completed-1").copy(
                            mediaType = MediaType.Movie,
                            userData = MediaUserData(isPlayed = true, lastPlayedEpochMs = 300),
                        ),
                    ),
                    nextCursor = PageCursor("completed-next"),
                )
            } else {
                Page.of(
                    listOf(
                        a.media("completed-2").copy(
                            mediaType = MediaType.Movie,
                            userData = MediaUserData(isPlayed = true, lastPlayedEpochMs = 100),
                        ),
                    ),
                )
            }
        }
        val b = browser(a)
        b.loadHistory()

        b.loadMoreHistory()

        assertEquals(
            listOf("resume-1", "completed-1", "completed-2"),
            b.state.value.historyItems.map { it.media.id.value },
        )
        assertTrue(b.state.value.historyLoadMoreFailed)
        assertNull(b.state.value.historyError)

        resumeTailFails = false
        b.retryLoadMoreHistory()

        assertEquals(
            listOf("resume-1", "completed-1", "resume-2", "completed-2"),
            b.state.value.historyItems.map { it.media.id.value },
        )
        assertFalse(b.state.value.historyLoadMoreFailed)
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
        assertNull(b.state.value.error, "Authentication is a shell dialog, not a content error row")
        assertEquals(AuthFailureKind.TokenExpired, b.authenticationFailure.value)
        assertFalse(b.state.value.loading); assertTrue(b.state.value.items.isEmpty())
    }

    @Test fun backgroundAuthenticationFailureKeepsVisibleHomeAndExposesRecovery() = runBlocking<Unit> {
        val a = Adapter().apply { feed = HomeFeed(latest = listOf(media("cached"))) }
        val b = browser(a)
        b.start()
        val visible = b.state.value.homeFeed
        a.homeFailure = AuthenticationError(AuthFailureKind.TokenRevoked, "secret")
        b.refreshHome()
        assertSame(visible, b.state.value.homeFeed)
        assertNull(b.state.value.homeError)
        assertEquals(AuthFailureKind.TokenRevoked, b.authenticationFailure.value)
        assertNull(browser(Adapter()).authenticationFailure.value)
    }

    @Test fun networkServerPermissionAndDecodeFailuresDoNotAskForLogin() = runBlocking<Unit> {
        for (failure in listOf(NetworkUnreachableError(), TimeoutError(), AuthorizationError(),
            ProtocolDecodingError(), ServerError(statusCode = 503))) {
            val a = Adapter().apply { fetch = { _, _ -> throw failure } }
            val b = browser(a)
            b.start()
            assertNull(b.authenticationFailure.value, failure::class.simpleName)
        }
    }

    @Test fun obsoleteSearchAuthenticationFailureCannotRaiseRecovery() = runBlocking<Unit> {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val a = Adapter().apply { searchFetch = { _, _ ->
                entered.complete(Unit)
                release.await()
                throw AuthenticationError(AuthFailureKind.TokenExpired)
            } }
            val b = browser(a)
            val pending = launch { b.search("old") }
            entered.await()
            b.clearSearch()
            release.complete(Unit)
            pending.join()
            assertNull(b.authenticationFailure.value)
        }
    }

    @Test fun cancellationPropagatesWithoutAnErrorBanner() = runBlocking<Unit> {
        val a = Adapter(); a.fetch = { _, _ -> throw CancellationException("cancelled") }
        val b = browser(a)
        assertFailsWith<CancellationException> { b.start() }
        assertNull(b.state.value.error); assertFalse(b.state.value.loading)
        assertNull(b.authenticationFailure.value)
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

    // ------------------------------------------------ playback queue from home

    @Test fun queueForAnEpisodeFromHomeLoadsTheSeasonItNeverOpened() = runBlocking<Unit> {
        val a = Adapter()
        val series = MediaId("series-1")
        val season = MediaId("season-1")
        val episodes = listOf(1, 2, 3).map { number ->
            a.media("e$number").copy(
                mediaType = MediaType.Episode,
                seriesId = series,
                seasonId = season,
                episodeNumber = number,
                canPlay = true,
            )
        }
        a.episodeFetch = { _, _, _ -> Page(episodes) }
        val b = browser(a)

        // 首页「继续观看」直接起播：详情页从没打开过，这一季的集一次都没取过。
        // 不补拉的话，同一部剧从首页播就没有「选集」也没有「下一集」。
        val queue = b.playbackQueueFor(episodes[1])

        assertEquals(listOf("e1", "e2", "e3"), queue.items.map { it.id.value })
        assertEquals("e2", queue.current.id.value)
        assertTrue(queue.hasNext)
        assertTrue(queue.hasPrevious)
        assertEquals(series, a.episodeRequests.single().first)
        assertEquals(season, a.episodeRequests.single().second)
    }

    @Test fun queueForAnEpisodeWithoutASeriesStaysSingleItem() = runBlocking<Unit> {
        val a = Adapter()
        a.episodeFetch = { _, _, _ -> error("没有剧集归属的条目不该去拉集列表") }
        val b = browser(a)

        val orphan = a.media("lonely").copy(mediaType = MediaType.Episode, episodeNumber = 1, canPlay = true)
        val queue = b.playbackQueueFor(orphan)

        assertEquals(listOf("lonely"), queue.items.map { it.id.value })
        assertTrue(a.episodeRequests.isEmpty())
    }

    @Test fun queueReusesTheSeasonTheDetailPageAlreadyLoaded() = runBlocking<Unit> {
        val a = Adapter()
        val series = MediaId("series-1")
        val season = MediaId("season-1")
        val episodes = listOf(1, 2).map { number ->
            a.media("e$number").copy(
                mediaType = MediaType.Episode,
                seriesId = series,
                seasonId = season,
                episodeNumber = number,
                canPlay = true,
            )
        }
        a.detail = { a.media(it.value).copy(mediaType = MediaType.Series) }
        a.seasonFetch = { Page(listOf(a.media("season-1").copy(mediaType = MediaType.Season))) }
        a.episodeFetch = { _, _, _ -> Page(episodes) }
        val b = browser(a)
        b.openDetail(series)

        val loaded = a.episodeRequests.size
        val queue = b.playbackQueueFor(episodes[1])

        assertEquals(listOf("e1", "e2"), queue.items.map { it.id.value })
        // 详情页已经把这一季加载过了：起播不该再拉一遍。
        assertEquals(loaded, a.episodeRequests.size)
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
