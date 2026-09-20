package cn.mafei.cmp.data.repositories

import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class MediaRepositoryConcurrencyTest {
    private class Adapter : MediaProviderAdapter {
        override val provider = ProviderKind.Jellyfin
        override val serverId = ServerId("s")
        override val accountId = AccountId("a")
        override val capabilities = AdapterCapabilities()
        val calls = AtomicInteger()
        var fetch: suspend () -> Page<MediaItem> = { Page.empty() }
        override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> { calls.incrementAndGet(); return fetch() }
        override suspend fun search(term: String, request: PageRequest) = error("unused")
        override suspend fun seasons(seriesId: MediaId) = error("unused")
        override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest) = error("unused")
        override suspend fun collections(request: PageRequest) = error("unused")
        override suspend fun item(id: MediaId) = error("unused")
        override suspend fun setFavorite(id: MediaId, favorite: Boolean) = error("unused")
        override suspend fun setWatched(id: MediaId, watched: Boolean) = error("unused")
        override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions) = error("unused")
        override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) = error("unused")
    }

    @Test fun cancelledCoroutineCannotCacheEvenIfAdapterSwallowsCancellation() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val a = Adapter()
            a.fetch = {
                entered.complete(Unit)
                try { release.await() } catch (_: CancellationException) { /* deliberately misbehaving adapter */ }
                Page.empty()
            }
            val r = MediaRepository(FixedEpochClock()); val s = r.open(a)
            val job = async(Dispatchers.Default) { r.browse(s, MediaQuery.Items()) }
            entered.await()
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            a.fetch = { Page.empty() }
            assertEquals(MediaLoadOrigin.Network, r.browse(s, MediaQuery.Items()).origin)
            assertEquals(2, a.calls.get())
        }
    }

    @Test fun accountReplacementOnAnotherDispatcherDiscardsPendingRead() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val a = Adapter()
            a.fetch = { entered.complete(Unit); release.await(); Page.empty() }
            val r = MediaRepository(FixedEpochClock()); val old = r.open(a)
            val job = async(Dispatchers.Default) { r.browse(old, MediaQuery.Items()) }
            entered.await()
            val b = Adapter(); val fresh = withContext(Dispatchers.Default) { r.open(b) }
            release.complete(Unit)
            assertFailsWith<CancellationException> { job.await() }
            assertEquals(MediaLoadOrigin.Network, r.browse(fresh, MediaQuery.Items()).origin)
        }
    }
}
