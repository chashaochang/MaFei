package cn.mafei.cmp.data.repositories

import cn.mafei.cmp.core.domain.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class PagedMediaStoreTest {
    private fun item(id: String) = MediaItem(MediaId(id), ServerId("s"), ProviderKind.Jellyfin, id)

    @Test fun concurrentLoadsClaimOneRequestAndOtherQueriesRemainIndependent() = runBlocking {
        withTimeout(5000) {
            val count = AtomicInteger()
            val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
            val store = PagedMediaStore<String>(60) { query, _, _ ->
                count.incrementAndGet()
                if (query == "photos") { started.complete(Unit); finish.await() }
                Page.of(listOf(item(query)))
            }
            val first = launch(Dispatchers.Default) { store.load("photos") }
            started.await()
            List(24) { launch(Dispatchers.Default) { store.load("photos") } }.joinAll()
            store.load("albums")
            assertEquals(2, count.get())
            finish.complete(Unit); first.join()
            assertEquals(listOf("photos"), store.pages.value["photos"]!!.items.map { it.id.value })
            assertEquals(listOf("albums"), store.pages.value["albums"]!!.items.map { it.id.value })
        }
    }

    @Test fun moreDeduplicatesAndStopsWhenServerHasNoCursor() = runBlocking {
        val requests = mutableListOf<PageCursor?>()
        val store = PagedMediaStore<String>(2) { _, request, _ ->
            requests += request.cursor
            if (request.cursor == null) Page.of(listOf(item("a"), item("b")), PageCursor("next"))
            else Page.of(listOf(item("b"), item("c")))
        }
        store.load("photos"); store.load("photos", more = true); store.load("photos", more = true)
        assertEquals(listOf(null, PageCursor("next")), requests)
        assertEquals(listOf("a", "b", "c"), store.pages.value["photos"]!!.items.map { it.id.value })
    }

    @Test fun refreshFailureKeepsVisibleItemsAndPagination() = runBlocking<Unit> {
        var fail = false
        val store = PagedMediaStore<String>(2) { _, _, _ ->
            if (fail) error("network") else Page.of(listOf(item("a")), PageCursor("next"))
        }
        store.load("photos"); fail = true; store.load("photos", refresh = true)
        val page = store.pages.value["photos"]!!
        assertEquals("a", page.items.single().id.value); assertEquals(PageCursor("next"), page.cursor)
        assertFalse(page.loading); assertNotNull(page.error)
    }

    @Test fun cancellationAndRefreshKeepFavoriteChangedDuringRequest() = runBlocking {
        withTimeout(5000) {
            var block = false
            val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
            val store = PagedMediaStore<String>(2) { _, _, _ ->
                if (block) { started.complete(Unit); finish.await() }
                Page.of(listOf(item("a")))
            }
            store.load("photos"); block = true
            val request = launch { store.load("photos", refresh = true) }; started.await()
            store.favoriteChanged(MediaId("a"), true)
            request.cancelAndJoin()
            assertFalse(store.pages.value["photos"]!!.loading)
            assertTrue(store.pages.value["photos"]!!.items.single().userData.isFavorite)
            block = false; store.load("photos", refresh = true)
            assertFalse(store.pages.value["photos"]!!.items.single().userData.isFavorite)
        }
    }

    @Test fun swallowedCancellationCannotPublishFetchedPage() = runBlocking {
        withTimeout(5000) {
            val started = CompletableDeferred<Unit>()
            val store = PagedMediaStore<String>(2) { _, _, _ ->
                started.complete(Unit)
                try { awaitCancellation() } catch (_: CancellationException) { }
                Page.of(listOf(item("stale")))
            }
            val request = launch { store.load("photos") }; started.await(); request.cancelAndJoin()
            assertFalse(store.pages.value["photos"]!!.initialized)
            assertTrue(store.pages.value["photos"]!!.items.isEmpty())
        }
    }
}
