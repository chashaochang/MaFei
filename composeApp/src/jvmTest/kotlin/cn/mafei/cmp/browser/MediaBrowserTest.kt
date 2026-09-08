package cn.mafei.cmp.browser

import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.data.repositories.*
import cn.mafei.cmp.feature.home.MediaBrowser
import kotlinx.coroutines.*
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
        val requests = mutableListOf<PageRequest>()
        override suspend fun collections(request: PageRequest) = Page(libraryList)
        override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> {
            requests += request
            return fetch(collectionId, request)
        }
        override suspend fun item(id: MediaId) = detail(id)
        override suspend fun search(term: String, request: PageRequest): Page<MediaItem> = error("unused")
        override suspend fun seasons(seriesId: MediaId): Page<MediaItem> = error("unused")
        override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> = error("unused")
        override suspend fun setFavorite(id: MediaId, favorite: Boolean): Unit = error("unused")
        override suspend fun setWatched(id: MediaId, watched: Boolean): Unit = error("unused")
        override suspend fun preparePlayback(item: MediaId): PlaybackPreparation = error("unused")
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
}
