package cn.mafei.cmp.feature.music

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.MusicCatalogQuery
import kotlin.coroutines.*
import kotlin.test.*

class MusicCatalogStoreTest {
    private val library = CollectionId("music")
    private fun track(id: String) = MediaItem(MediaId(id), ServerId("s"), ProviderKind.Jellyfin, id, MediaType.Audio)
    private fun runImmediate(block: suspend () -> Unit) {
        var result: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<Unit>) { result = value }
        })
        checkNotNull(result) { "Test unexpectedly suspended" }.getOrThrow()
    }

    @Test fun sectionsHaveIndependentPagesAndPagingDeduplicatesByIdentity() = runImmediate {
        val requests = mutableListOf<Pair<MusicCatalogQuery, PageRequest>>()
        val store = MusicCatalogStore({ query, request, _ ->
            requests += query to request
            if (request.cursor == null) Page.of(listOf(track("a")), PageCursor("next"))
            else Page.of(listOf(track("a"), track("b")), null)
        }, { id, _ -> track(id.value) })
        val albums = MusicCatalogQuery.Albums(library)
        val songs = MusicCatalogQuery.Tracks(library)
        store.load(albums); store.load(songs); store.load(songs, more = true)
        assertEquals(listOf("a"), store.pages.value[albums]!!.items.map { it.id.value })
        assertEquals(listOf("a", "b"), store.pages.value[songs]!!.items.map { it.id.value })
        assertNull(store.pages.value[songs]!!.cursor)
        store.load(songs)
        assertEquals(3, requests.size)
        assertEquals(PageCursor("next"), requests.last().second.cursor)
        assertEquals(40, requests.first().second.pageSize)
    }

    @Test fun detailPageSizesMatchLegacyAndFavoritesUpdateEveryLoadedSurface() = runImmediate {
        val sizes = mutableListOf<Int>()
        val store = MusicCatalogStore({ _, request, _ -> sizes += request.pageSize; Page.of(listOf(track("a")), null) },
            { id, _ -> track(id.value) })
        val album = MusicCatalogQuery.AlbumTracks(MediaId("album"))
        val popular = MusicCatalogQuery.ArtistPopularTracks(MediaId("artist"))
        store.load(album); store.load(popular); store.detail(MediaId("a"))
        assertEquals(listOf(50, 20), sizes)
        store.favoriteChanged(MediaId("a"), true)
        assertTrue(store.details.value[MediaId("a")]!!.item!!.userData.isFavorite)
        assertTrue(store.pages.value.values.all { it.items.single().userData.isFavorite })
    }

    @Test fun failedRefreshRetainsRealItemsAndCancellationRestoresLoadingState() = runImmediate {
        var fail = false
        var cancel = false
        val query = MusicCatalogQuery.Tracks(library)
        val store = MusicCatalogStore({ _, _, _ ->
            if (cancel) throw kotlin.coroutines.cancellation.CancellationException()
            if (fail) error("network")
            Page.of(listOf(track("a")), null)
        }, { id, _ -> track(id.value) })
        store.load(query); fail = true; store.load(query, refresh = true)
        assertEquals("a", store.pages.value[query]!!.items.single().id.value)
        assertNotNull(store.pages.value[query]!!.error)
        cancel = true
        assertFailsWith<kotlin.coroutines.cancellation.CancellationException> { store.load(query, refresh = true) }
        assertFalse(store.pages.value[query]!!.loading)
        assertTrue(store.pages.value[query]!!.initialized)
    }
}
