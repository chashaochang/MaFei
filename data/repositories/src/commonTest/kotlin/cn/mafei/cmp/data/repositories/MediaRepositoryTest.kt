package cn.mafei.cmp.data.repositories

import cn.mafei.cmp.common.core.EpochClock
import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.storage_api.JsonSnapshotStorage
import cn.mafei.cmp.data.adapter_contract.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

private fun testRepository(
    clock: EpochClock,
    policy: MediaCachePolicy = MediaCachePolicy(),
    homeSnapshots: JsonSnapshotStorage? = null,
) = MediaRepository(clock, policy, homeSnapshots, Dispatchers.Unconfined)

private fun runTest(block: suspend () -> Unit) {
    // MediaRepository.load schedules network+decode work on Dispatchers.Default,
    // so these tests cannot complete through synchronous continuations anymore;
    // block until the coroutine finishes instead. (The old synchronous-completion
    // check started failing for every test once that scheduling change landed.)
    runBlocking { block() }
}

private class Pending<T>(block: suspend () -> T) {
    var result: Result<T>? = null
    init { block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { this@Pending.result = result }
    }) }
}

private class StubAdapter(
    override val accountId: AccountId = AccountId("a"),
    override val provider: ProviderKind = ProviderKind.Jellyfin,
    override val serverId: ServerId = ServerId("s"),
) : MediaProviderAdapter {
    override val capabilities = AdapterCapabilities(search = true)
    var calls = 0
    fun media(id: String = "m", played: Boolean = false) = MediaItem(MediaId(id), serverId, provider, id, MediaType.Movie, userData = MediaUserData(isPlayed = played))
    fun person(id: String = "p") = MediaPerson(MediaId(id), serverId, provider, id)
    var fetch: suspend () -> Page<MediaItem> = { Page(listOf(media())) }
    private suspend fun page(): Page<MediaItem> { calls++; return fetch() }
    override suspend fun items(collectionId: CollectionId?, request: PageRequest) = page()
    override suspend fun search(term: String, request: PageRequest) = page()
    override suspend fun itemsByPerson(personId: MediaId, request: PageRequest) = page()
    override suspend fun seasons(seriesId: MediaId) = page()
    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest) = page()
    override suspend fun item(id: MediaId): MediaItem { calls++; return media(id.value) }
    override suspend fun person(id: MediaId): MediaPerson { calls++; return person(id.value) }
    override suspend fun collections(request: PageRequest): Page<MediaCollection> { calls++; return Page(listOf(MediaCollection(CollectionId("lib"), serverId, provider, "Library"))) }
    override suspend fun setFavorite(id: MediaId, favorite: Boolean) = error("Not used")
    override suspend fun setWatched(id: MediaId, watched: Boolean) = error("Not used")
    override suspend fun preparePlayback(
        item: MediaId,
        options: PlaybackOptions,
    ): PlaybackPreparation = error("Not used")
    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) = error("Not used")
}

class MediaRepositoryTest {
    private val query = MediaQuery.Items()

    @Test fun freshCacheAndTtlBoundary() = runTest {
        val clock = FixedEpochClock(1000)
        val adapter = StubAdapter()
        val repo = testRepository(clock, MediaCachePolicy(freshTtlMs = 100, staleMaxAgeMs = 500))
        val session = repo.open(adapter)
        assertEquals(MediaLoadOrigin.Network, repo.browse(session, query).origin)
        clock.advanceBy(99)
        assertEquals(MediaLoadOrigin.FreshCache, repo.browse(session, query).origin)
        clock.advanceBy(1)
        assertEquals(MediaLoadOrigin.Network, repo.browse(session, query).origin)
        assertEquals(2, adapter.calls)
    }

    @Test fun explicitRefreshBypassesFreshCache() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val s = r.open(a)
        r.browse(s, query); r.browse(s, query, refresh = true)
        assertEquals(2, a.calls)
    }

    @Test fun everyQueryDimensionSeparatesCacheEntries() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val s = r.open(a)
        r.browse(s, query)
        r.browse(s, MediaQuery.Items(CollectionId("other")))
        r.browse(s, query, PageRequest(pageSize = 2))
        r.browse(s, query, PageRequest(cursor = PageCursor("opaque")))
        r.browse(s, query, PageRequest(filter = MediaFilter(favoritesOnly = true)))
        r.browse(s, query, PageRequest(sort = MediaSort(MediaSortField.Title)))
        r.browse(s, MediaQuery.Search("term"))
        r.browse(s, MediaQuery.Seasons(MediaId("series")))
        r.browse(s, MediaQuery.Episodes(MediaId("series"), MediaId("season")))
        r.browse(s, MediaQuery.PersonItems(MediaId("person")))
        assertEquals(10, a.calls)
        r.browse(s, query)
        assertEquals(10, a.calls)
    }

    @Test fun searchWhitespaceAndSetOrderNormalizeWithoutCaseFolding() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val s = r.open(a)
        r.browse(s, MediaQuery.Search(" term "), PageRequest(filter = MediaFilter(genres = linkedSetOf("Drama", "SciFi"))))
        r.browse(s, MediaQuery.Search("term"), PageRequest(filter = MediaFilter(genres = linkedSetOf("SciFi", "Drama"))))
        assertEquals(1, a.calls)
        r.browse(s, MediaQuery.Search("Term"), PageRequest(filter = MediaFilter(genres = setOf("SciFi", "Drama"))))
        assertEquals(2, a.calls)
    }

    @Test fun reselectionInvalidatesEvenSameAccountGeneration() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val old = r.open(a)
        r.browse(old, query)
        val fresh = r.open(a)
        assertFailsWith<CancellationException> { r.browse(old, query) }
        assertTrue(fresh.generation > old.generation)
        assertEquals(MediaLoadOrigin.Network, r.browse(fresh, query).origin)
    }

    @Test fun switchingAccountDoesNotReturnOrCacheLateResults() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val old = r.open(a)
        lateinit var continuation: Continuation<Page<MediaItem>>
        a.fetch = { suspendCoroutine { continuation = it } }
        val pending = Pending { r.browse(old, query) }
        assertNull(pending.result)
        val b = StubAdapter(AccountId("b")); val fresh = r.open(b)
        continuation.resume(Page(listOf(a.media("old"))))
        assertIs<CancellationException>(pending.result!!.exceptionOrNull())
        assertEquals("m", r.browse(fresh, query).value.items.single().id.value)
        assertEquals(1, b.calls)
    }

    @Test fun newerRefreshWinsWhenResponsesArriveInReverseOrder() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val s = r.open(a)
        lateinit var first: Continuation<Page<MediaItem>>
        a.fetch = { suspendCoroutine { first = it } }
        val pending = Pending { r.browse(s, query, refresh = true) }
        a.fetch = { Page(listOf(a.media("new"))) }
        r.browse(s, query, refresh = true)
        first.resume(Page(listOf(a.media("old"))))
        assertIs<CancellationException>(pending.result!!.exceptionOrNull())
        assertEquals("new", r.browse(s, query).value.items.single().id.value)
    }

    @Test fun onlyExplicitTransientErrorsCanUseStaleCache() = runTest {
        val clock = FixedEpochClock()
        val a = StubAdapter(); val r = testRepository(clock, MediaCachePolicy(100, 500)); val s = r.open(a)
        r.browse(s, query); clock.advanceBy(100)
        for (error in listOf(NetworkUnreachableError(), TimeoutError(), ServerError(503))) {
            a.fetch = { throw error }
            val cached = r.browse(s, query)
            assertEquals(MediaLoadOrigin.StaleCache, cached.origin)
            assertSame(error, cached.refreshError)
        }
        for (error in listOf<Throwable>(AuthenticationError(AuthFailureKind.TokenExpired), AuthorizationError(), ServerError(400), ServerError(), ProtocolDecodingError(), ClientConfigError(), CancellationException("cancel"))) {
            a.fetch = { throw error }
            val actual = try { r.browse(s, query); null } catch (failure: Throwable) { failure }
            assertSame(error, actual)
        }
    }

    @Test fun staleFallbackHasAHardAgeLimit() = runTest {
        val clock = FixedEpochClock()
        val a = StubAdapter(); val r = testRepository(clock, MediaCachePolicy(100, 500)); val s = r.open(a)
        r.browse(s, query); clock.advanceBy(501); a.fetch = { throw NetworkUnreachableError() }
        assertFailsWith<NetworkUnreachableError> { r.browse(s, query) }
    }

    @Test fun clockRollbackDoesNotMakeCacheFreshForever() = runTest {
        val clock = FixedEpochClock(1000)
        val a = StubAdapter(); val r = testRepository(clock); val s = r.open(a)
        r.browse(s, query); clock.setTo(900); r.browse(s, query)
        assertEquals(2, a.calls)
    }

    @Test fun invalidationDropsInFlightResults() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val s = r.open(a)
        lateinit var continuation: Continuation<Page<MediaItem>>
        a.fetch = { suspendCoroutine { continuation = it } }
        val pending = Pending { r.browse(s, query) }
        r.invalidate(s)
        continuation.resume(Page(listOf(a.media())))
        assertIs<CancellationException>(pending.result!!.exceptionOrNull())
    }

    @Test fun authenticationFailureEvictsPreviouslyFreshCache() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val s = r.open(a)
        r.browse(s, query)
        a.fetch = { throw AuthenticationError(AuthFailureKind.TokenExpired) }
        assertFailsWith<AuthenticationError> { r.browse(s, query, refresh = true) }
        assertFailsWith<AuthenticationError> { r.browse(s, query) }
        assertEquals(3, a.calls)
    }

    @Test fun lateNetworkFailureCannotFallbackAfterAccountSwitch() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val s = r.open(a)
        r.browse(s, query)
        lateinit var continuation: Continuation<Page<MediaItem>>
        a.fetch = { suspendCoroutine { continuation = it } }
        val pending = Pending { r.browse(s, query, refresh = true) }
        r.open(StubAdapter(AccountId("b")))
        continuation.resumeWithException(NetworkUnreachableError())
        assertIs<CancellationException>(pending.result!!.exceptionOrNull())
    }

    @Test fun suppliedMutableFilterCannotMutateStoredCacheKey() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val s = r.open(a)
        val genres = mutableSetOf("Drama")
        r.browse(s, query, PageRequest(filter = MediaFilter(genres = genres)))
        genres.add("Comedy")
        r.browse(s, query, PageRequest(filter = MediaFilter(genres = setOf("Drama"))))
        assertEquals(1, a.calls)
        r.browse(s, query, PageRequest(filter = MediaFilter(genres = genres)))
        assertEquals(2, a.calls)
    }

    @Test fun newFeiniuListStateInvalidatesPriorDetailHint() = runTest {
        val a = StubAdapter(provider = ProviderKind.Feiniu)
        val r = testRepository(FixedEpochClock()); val s = r.open(a)
        a.fetch = { Page(listOf(a.media(played = true))) }
        r.browse(s, query); assertTrue(r.item(s, MediaId("m")).value.userData.isPlayed)
        a.fetch = { Page(listOf(a.media(played = false))) }
        r.browse(s, query, refresh = true)
        assertFalse(r.item(s, MediaId("m")).value.userData.isPlayed)
    }

    @Test fun closingOldHandleCannotCloseNewAccount() = runTest {
        val r = testRepository(FixedEpochClock()); val old = r.open(StubAdapter())
        val fresh = r.open(StubAdapter(AccountId("b")))
        r.close(old)
        assertEquals(MediaLoadOrigin.Network, r.browse(fresh, query).origin)
        r.close(fresh)
        assertFailsWith<CancellationException> { r.browse(fresh, query) }
    }

    @Test fun collectionsAndDetailsUseSeparateTypedCaches() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val s = r.open(a)
        r.collections(s); r.collections(s); r.item(s, MediaId("m")); r.item(s, MediaId("m"))
        r.person(s, MediaId("p")); r.person(s, MediaId("p"))
        assertEquals(3, a.calls)
    }

    @Test fun homeFeedHasAnIndependentAccountScopedCache() = runTest {
        val a = StubAdapter()
        val r = testRepository(FixedEpochClock())
        val s = r.open(a)
        val first = r.home(s)
        val cached = r.home(s)
        assertEquals(MediaLoadOrigin.Network, first.origin)
        assertEquals(MediaLoadOrigin.FreshCache, cached.origin)
        assertEquals(listOf("m"), cached.value.latest.map { it.title })
        assertEquals(1, a.calls)
    }

    @Test fun feiniuDetailKeepsRecentListUserState() = runTest {
        val a = StubAdapter(provider = ProviderKind.Feiniu)
        a.fetch = { Page(listOf(a.media(played = true))) }
        val r = testRepository(FixedEpochClock()); val s = r.open(a)
        r.browse(s, query)
        assertTrue(r.item(s, MediaId("m")).value.userData.isPlayed)
    }

    @Test fun boundedCacheEvictsOldEntries() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock(), MediaCachePolicy(maxEntries = 1)); val s = r.open(a)
        r.browse(s, MediaQuery.Search("one")); r.browse(s, MediaQuery.Search("two")); r.browse(s, MediaQuery.Search("one"))
        assertEquals(3, a.calls)
    }

    @Test fun foreignServerResultIsRejectedInsteadOfCached() = runTest {
        val a = StubAdapter(); val r = testRepository(FixedEpochClock()); val s = r.open(a)
        a.fetch = { Page(listOf(a.media().copy(serverId = ServerId("wrong")))) }
        assertFailsWith<ProtocolDecodingError> { r.browse(s, query) }
    }

    @Test fun pageMergeDeduplicatesWithoutParsingProviderCursor() {
        val a = StubAdapter(); val pages = MediaPageAccumulator()
        val cursor = PageCursor("provider-specific-token")
        pages.append(null, Page(listOf(a.media("1"), a.media("2")), cursor))
        val combined = pages.append(cursor, Page(listOf(a.media("2", true), a.media("3"))))
        assertEquals(listOf("1", "2", "3"), combined.items.map { it.id.value })
        assertTrue(combined.items[1].userData.isPlayed)
        assertNull(combined.nextCursor)
    }

    @Test fun cursorCycleAndOutOfOrderPagesDoNotCorruptAccumulation() {
        val a = StubAdapter(); val pages = MediaPageAccumulator(); val cursor = PageCursor("token")
        pages.append(null, Page(listOf(a.media("1")), cursor))
        assertFailsWith<ClientConfigError> { pages.append(PageCursor("other"), Page(listOf(a.media("bad")))) }
        assertFailsWith<ProtocolDecodingError> { pages.append(cursor, Page(listOf(a.media("bad")), cursor)) }
        assertEquals(listOf("1", "2"), pages.append(cursor, Page(listOf(a.media("2")))).items.map { it.id.value })
    }

    @Test fun successfulHomeRefreshPersistsSnapshotForTheNextColdStart() = runTest {
        val snapshots = JsonSnapshotStorage(MemorySettingsStorage(), Json { ignoreUnknownKeys = true }) { 1000L }
        val adapter = StubAdapter()
        val warm = testRepository(FixedEpochClock(), homeSnapshots = snapshots)
        warm.home(warm.open(adapter))
        // Simulate a process restart: a new repository over the same durable store.
        val cold = testRepository(FixedEpochClock(), homeSnapshots = snapshots)
        val snapshot = cold.homeSnapshot(cold.open(StubAdapter()))
        assertEquals(listOf("m"), snapshot!!.latest.map { it.title })
    }

    @Test fun corruptHomeSnapshotIsDroppedInsteadOfFailingLoads() = runTest {
        val storage = MemorySettingsStorage()
        val snapshots = JsonSnapshotStorage(storage, Json { ignoreUnknownKeys = true }) { 1000L }
        val repo = testRepository(FixedEpochClock(), homeSnapshots = snapshots)
        val key = "mafei.media.home-snapshot.v1/Jellyfin/s/a"
        storage.putString(key, "{not json")
        assertNull(repo.homeSnapshot(repo.open(StubAdapter())))
        assertNull(storage.getString(key)) // dropped so a later refresh can rewrite it
    }

    @Test fun homeSnapshotIsNullWithoutSnapshotStorage() = runTest {
        val repo = testRepository(FixedEpochClock())
        assertNull(repo.homeSnapshot(repo.open(StubAdapter())))
    }
}

/** Minimal in-memory [SettingsStorage] for snapshot persistence tests. */
private class MemorySettingsStorage : cn.mafei.cmp.core.storage_api.SettingsStorage {
    private val values = mutableMapOf<String, String>()
    override suspend fun getString(key: String): String? = values[key]
    override suspend fun putString(key: String, value: String) { values[key] = value }
    override suspend fun getBytes(key: String): ByteArray? = null
    override suspend fun putBytes(key: String, value: ByteArray) = error("unused")
    override suspend fun remove(key: String): Boolean = values.remove(key) != null
    override suspend fun contains(key: String): Boolean = values.containsKey(key)
    override suspend fun listKeys(prefix: String?): List<String> =
        values.keys.filter { prefix == null || it.startsWith(prefix) }.sorted()
    override suspend fun edit(mutate: cn.mafei.cmp.core.storage_api.StorageEditor.() -> Unit) = error("unused")
}
