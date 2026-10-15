package cn.mafei.cmp.data.repositories

import cn.mafei.cmp.common.core.EpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.storage_api.JsonSnapshotStorage
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.adapter_contract.HomeFeedRequest
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackPreviewFrame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * In-memory media read repository (04D). Credentials and persistence are not
 * owned here. 04E opens a new handle with an account-scoped adapter after
 * login/restore, and closes it on logout. Do not reuse a facade across accounts.
 *
 * Short mutex-protected state transitions linearize selection, invalidation
 * and response publication. Network runs outside the mutex. Older same-key
 * refreshes and previous identity generations cannot publish or use fallback.
 */
class MediaRepository(
    private val clock: EpochClock,
    private val policy: MediaCachePolicy = MediaCachePolicy(),
    /**
     * Optional durable store for the last successfully fetched home feed
     * (per account). When present, [homeSnapshot] lets the browser paint the
     * previous session's home instantly on cold start while the network
     * refresh runs; the old HarmonyOS client had the same cache-first open.
     */
    private val homeSnapshots: JsonSnapshotStorage? = null,
    /**
     * Where network + decode runs. Production keeps the UI thread free by
     * dispatching to [Dispatchers.Default]; deterministic unit tests inject
     * an unconfined dispatcher so manual continuations stay synchronous.
     */
    private val fetchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private var generation = 0L
    private var revision = 0L
    private var sequence = 0L
    private var active: Binding? = null
    private class Binding(val session: MediaSession, val adapter: MediaProviderAdapter)
    private sealed interface Key
    private data class BrowseKey(val session: MediaSession, val query: MediaQuery, val request: PageRequest) : Key
    private data class CollectionsKey(val session: MediaSession, val request: PageRequest) : Key
    private data class HomeKey(val session: MediaSession, val request: HomeFeedRequest) : Key
    private data class ItemKey(val session: MediaSession, val id: MediaId) : Key
    private data class PersonKey(val session: MediaSession, val id: MediaId) : Key
    private val pages = MediaCache<BrowseKey, Page<MediaItem>>(policy.maxEntries)
    private val libraries = MediaCache<CollectionsKey, Page<MediaCollection>>(policy.maxEntries)
    private val homes = MediaCache<HomeKey, HomeFeed>(policy.maxEntries)
    private val details = MediaCache<ItemKey, MediaItem>(policy.maxEntries)
    private val people = MediaCache<PersonKey, MediaPerson>(policy.maxEntries)
    private val listUserState = MediaCache<ItemKey, MediaUserData>(policy.maxEntries)
    private val latest = mutableMapOf<Key, Long>()

    suspend fun open(adapter: MediaProviderAdapter): MediaSession = mutex.withLock {
        check(generation < Long.MAX_VALUE) { "Media session generation exhausted" }
        val session = MediaSession(adapter.provider, adapter.serverId, adapter.accountId, ++generation)
        clearData()
        active = Binding(session, adapter)
        session
    }

    /** A delayed old logout cannot close a newer selection. */
    suspend fun close(session: MediaSession) = mutex.withLock {
        if (active?.session === session) { clearData(); active = null }
    }

    /** Call around mutations/reconciliation so earlier reads cannot restore pre-edit values. */
    suspend fun invalidate(session: MediaSession) = mutex.withLock {
        requireActive(session)
        clearData()
    }

    suspend fun browse(
        session: MediaSession,
        query: MediaQuery,
        request: PageRequest = PageRequest(),
        refresh: Boolean = false,
    ): MediaLoad<Page<MediaItem>> {
        val normalized = if (query is MediaQuery.Search) query.copy(term = query.term.trim()) else query
        val frozen = request.snapshot()
        if ((normalized is MediaQuery.Seasons || normalized is MediaQuery.NextUp ||
                normalized is MediaQuery.ChasingNextUp) &&
            (frozen.cursor != null || !frozen.filter.isEmpty || frozen.sort != null)
        ) {
            throw ClientConfigError("$normalized is a single-call surface without cursor, filters or sort")
        }
        val key = BrowseKey(session, normalized, frozen)
        return load(session, key, pages, refresh, store = { page ->
            if (normalized is MediaQuery.Items || normalized is MediaQuery.Search || normalized is MediaQuery.PersonItems || normalized is MediaQuery.Music) {
                for (item in page.items) {
                    val itemKey = ItemKey(session, item.id)
                    listUserState.put(itemKey, item.userData, clock.nowEpochMs())
                    // A newly observed list state must not leave a cached detail stale.
                    details.remove(itemKey)
                }
            }
            page
        }) { adapter ->
            val page = when (normalized) {
                is MediaQuery.Music -> adapter.music(normalized.query, frozen)
                is MediaQuery.LiveChannels -> adapter.liveChannels(frozen)
                is MediaQuery.Photos -> adapter.photos(normalized.query, frozen)
                is MediaQuery.Items -> adapter.items(normalized.collectionId, frozen)
                is MediaQuery.Search -> adapter.search(normalized.term, frozen)
                is MediaQuery.Seasons -> adapter.seasons(normalized.seriesId)
                is MediaQuery.Episodes -> adapter.episodes(normalized.seriesId, normalized.seasonId, frozen)
                is MediaQuery.NextUp -> Page.of(listOfNotNull(adapter.nextUp(normalized.seriesId)), nextCursor = null)
                is MediaQuery.ChasingNextUp -> Page.of(
                    listOfNotNull(
                        adapter.chasingNextUp(normalized.seriesId, normalized.nextUpDateCutoffEpochMs),
                    ),
                    nextCursor = null,
                )
                is MediaQuery.PersonItems -> adapter.itemsByPerson(normalized.personId, frozen)
                is MediaQuery.Resume -> adapter.resumeItems(frozen)
                is MediaQuery.Similar -> Page.of(adapter.similar(normalized.itemId), nextCursor = null)
            }
            page.items.forEach { validateItem(session, it) }
            page.copy(items = page.items.toList())
        }
    }

    suspend fun collections(session: MediaSession, request: PageRequest = PageRequest(), refresh: Boolean = false): MediaLoad<Page<MediaCollection>> {
        val frozen = request.snapshot()
        if (!frozen.filter.isEmpty || frozen.sort != null) throw ClientConfigError("Collections cannot express filters or sort")
        return load(session, CollectionsKey(session, frozen), libraries, refresh) { adapter ->
            adapter.collections(frozen).also { page ->
                if (page.items.any { it.serverId != session.serverId || it.provider != session.provider }) {
                    throw ProtocolDecodingError("Collections returned a foreign provider/server identity")
                }
            }.let { it.copy(items = it.items.toList()) }
        }
    }

    suspend fun home(
        session: MediaSession,
        request: HomeFeedRequest = HomeFeedRequest(),
        refresh: Boolean = false,
    ): MediaLoad<HomeFeed> {
        val result = load(session, HomeKey(session, request), homes, refresh, store = { feed ->
            val observedAt = clock.nowEpochMs()
            feed.allItems().forEach { item ->
                val key = ItemKey(session, item.id)
                listUserState.put(key, item.userData, observedAt)
                details.remove(key)
            }
            feed.copy(
                recommendations = feed.recommendations.toList(),
                resume = feed.resume.toList(),
                nextUp = feed.nextUp.toList(),
                latest = feed.latest.toList(),
            )
        }) { adapter ->
            adapter.home(request).also { feed -> feed.allItems().forEach { validateItem(session, it) } }
        }
        // Refresh succeeded: replace the durable snapshot so the NEXT cold
        // start paints this content. A failed save only costs one cold-start
        // of freshness and must never fail the request.
        if (result.origin == MediaLoadOrigin.Network) persistHomeSnapshot(session, result.value)
        return result
    }

    /**
     * The last durably persisted home feed for [session]'s account, or null.
     * Read-only: never touches the network or the in-memory cache. Corrupt
     * snapshots are dropped so the next successful refresh rewrites them.
     */
    suspend fun homeSnapshot(session: MediaSession): HomeFeed? {
        val snapshots = homeSnapshots ?: return null
        val key = homeSnapshotKey(session)
        return try {
            snapshots.load(key, HomeFeed.serializer())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            runCatching { snapshots.delete(key) }
            null
        }
    }

    private suspend fun persistHomeSnapshot(session: MediaSession, feed: HomeFeed) {
        val snapshots = homeSnapshots ?: return
        try {
            snapshots.save(
                homeSnapshotKey(session),
                feed,
                HomeFeed.serializer(),
                schemaVersion = HomeSnapshotSchemaVersion,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    private fun homeSnapshotKey(session: MediaSession): String =
        "$HomeSnapshotKeyPrefix/${session.provider.name}/${session.serverId.value}/${session.accountId.value}"

    suspend fun item(session: MediaSession, id: MediaId, refresh: Boolean = false): MediaLoad<MediaItem> {
        val key = ItemKey(session, id)
        return load(session, key, details, refresh, store = { detail ->
            // FNOS detail has no user-state fields. Retain recent list state,
            // never overwrite it with detail's default false/zero. This is a
            // last-observed hint, not authoritative login/persistent state.
            val hint = listUserState.get(key)
            if (session.provider == ProviderKind.Feiniu && hint != null &&
                hint.age(clock.nowEpochMs())?.let { it <= policy.staleMaxAgeMs } == true
            ) detail.copy(userData = hint.value) else detail
        }) { adapter ->
            adapter.item(id).also {
                validateItem(session, it)
                if (it.id != id) throw ProtocolDecodingError("Detail returned a different media identity")
            }
        }
    }

    suspend fun photoOriginal(session: MediaSession, id: MediaId): MediaDownload {
        val adapter = requireActive(session).adapter
        val result = adapter.photoOriginal(id)
        requireActive(session)
        return result
    }

    suspend fun danmakuXml(session: MediaSession, id: MediaId): String? {
        val adapter = mutex.withLock { requireActive(session).adapter }
        val result = adapter.danmakuXml(id)
        mutex.withLock { requireActive(session) }
        return result
    }

    /** Account-scoped seek preview; providers without a real frame source return null. */
    suspend fun previewFrame(
        session: MediaSession,
        item: MediaId,
        positionMs: Long,
        mediaSourceId: String? = null,
    ): PlaybackPreviewFrame? {
        currentCoroutineContext().ensureActive()
        val adapter = mutex.withLock { requireActive(session).adapter }
        return adapter.previewFrame(item, positionMs, mediaSourceId)
    }

    suspend fun person(
        session: MediaSession,
        id: MediaId,
        name: String,
        refresh: Boolean = false,
    ): MediaLoad<MediaPerson> {
        val key = PersonKey(session, id)
        return load(session, key, people, refresh) { adapter ->
            adapter.person(id, name).also {
                if (it.id != id) throw ProtocolDecodingError("Person detail returned a different identity")
                if (it.serverId != session.serverId || it.provider != session.provider) {
                    throw ProtocolDecodingError("Person detail returned a foreign provider/server identity")
                }
            }
        }
    }

    suspend fun setFavorite(session: MediaSession, id: MediaId, favorite: Boolean) {
        mutate(session) { adapter -> adapter.setFavorite(id, favorite) }
    }

    suspend fun setWatched(session: MediaSession, id: MediaId, watched: Boolean) {
        mutate(session) { adapter -> adapter.setWatched(id, watched) }
    }

    /** Administrator-scoped media deletion; the server enforces the policy. */
    suspend fun deleteItems(session: MediaSession, ids: Collection<MediaId>) {
        if (ids.isEmpty()) return
        mutate(session) { adapter -> adapter.deleteItems(ids) }
    }

    /** Whether the current caller may delete media (drives UI affordances only). */
    suspend fun canDeleteMedia(session: MediaSession): Boolean =
        mutex.withLock { requireActive(session).adapter }.canDeleteMedia()

    /** Provider-authoritative role used by the legacy Mine profile header. */
    suspend fun isAdministrator(session: MediaSession): Boolean =
        mutex.withLock { requireActive(session).adapter }.isAdministrator()

    suspend fun management(session: MediaSession): cn.mafei.cmp.data.adapter_contract.ServerManagement {
        val delegate = mutex.withLock { requireActive(session).adapter.management }
            ?: throw ClientConfigError("Provider does not expose server management")
        return object : cn.mafei.cmp.data.adapter_contract.ServerManagement {
            override val currentDeviceId get() = delegate.currentDeviceId
            private suspend fun <T> checked(block: suspend () -> T): T {
                currentCoroutineContext().ensureActive()
                mutex.withLock { requireActive(session) }
                val result = withContext(fetchDispatcher) { block() }
                currentCoroutineContext().ensureActive()
                mutex.withLock { requireActive(session) }
                return result
            }
            override suspend fun isAdministrator() = checked { delegate.isAdministrator() }
            override suspend fun server() = checked { delegate.server() }
            override suspend fun counts() = checked { delegate.counts() }
            override suspend fun users() = checked { delegate.users() }
            override suspend fun tasks() = checked { delegate.tasks() }
            override suspend fun sessions(nowEpochMs: Long) = checked { delegate.sessions(nowEpochMs) }
            override suspend fun activity(offset: Int, limit: Int, minDate: String?, hasUserId: Boolean?) =
                checked { delegate.activity(offset, limit, minDate, hasUserId) }
            override suspend fun sessionCommand(id: String, command: ManagedSessionCommand) = checked { delegate.sessionCommand(id, command) }
            override suspend fun sessionMessage(id: String, text: String) = checked { delegate.sessionMessage(id, text) }
            override suspend fun startLibraryScan() = checked { delegate.startLibraryScan() }
            override suspend fun startTask(id: String) = checked { delegate.startTask(id) }
            override suspend fun cancelTask(id: String) = checked { delegate.cancelTask(id) }
            override suspend fun devices() = checked { delegate.devices() }
            override suspend fun renameDevice(id: String, customName: String) = checked { delegate.renameDevice(id, customName) }
            override suspend fun revokeDevice(id: String) = checked { delegate.revokeDevice(id) }
        }
    }

    private suspend fun mutate(
        session: MediaSession,
        action: suspend (MediaProviderAdapter) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        val adapter = mutex.withLock { requireActive(session).adapter }
        action(adapter)
        currentCoroutineContext().ensureActive()
        mutex.withLock {
            requireActive(session)
            // The provider accepted the mutation; no pre-edit list/detail
            // snapshot may be published or reused afterwards.
            clearData()
        }
    }

    private class Start<T>(
        val cached: MediaCache.Entry<T>?, val fresh: Boolean,
        val ticket: Long, val revision: Long, val adapter: MediaProviderAdapter,
    )

    private suspend fun <K : Key, T> load(
        session: MediaSession, key: K, cache: MediaCache<K, T>, refresh: Boolean,
        store: (T) -> T = { it }, fetch: suspend (MediaProviderAdapter) -> T,
    ): MediaLoad<T> {
        currentCoroutineContext().ensureActive()
        val start = mutex.withLock {
            val binding = requireActive(session)
            val cached = cache.get(key)
            val fresh = !refresh && cached?.age(clock.nowEpochMs())?.let { it < policy.freshTtlMs } == true
            if (!fresh) {
                check(sequence < Long.MAX_VALUE) { "Media request sequence exhausted" }
                latest[key] = ++sequence
            }
            Start(cached, fresh, sequence, revision, binding.adapter)
        }
        if (start.fresh) {
            return MediaLoad(start.cached!!.value, session, MediaLoadOrigin.FreshCache)
        }
        try {
            // 网络 + **解码 + 领域映射**都放到默认调度器上：调用方链路是
            // `LaunchedEffect → MediaBrowser.refresh()`，也就是 UI 上下文；这里不切的话，
            // 一次首页首屏要把好几页响应的 JSON 解在 UI 线程上，界面会整段卡住 ——
            // 看起来就像"异步没生效"。状态发布仍在原来的上下文里做（锁内那次），
            // 所以 `MediaBrowser` 的单线程状态语义不变。
            val value = withContext(fetchDispatcher) { fetch(start.adapter) }
            currentCoroutineContext().ensureActive()
            return mutex.withLock {
                validatePublication(session, key, start)
                val stored = store(value)
                cache.put(key, stored, clock.nowEpochMs())
                MediaLoad(stored, session, MediaLoadOrigin.Network)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: DomainError) {
            return mutex.withLock {
                validatePublication(session, key, start)
                if (error is AuthenticationError || error is AuthorizationError) clearData()
                val cached = start.cached
                if (allowsStale(error) && cached != null &&
                    cached.age(clock.nowEpochMs())?.let { it <= policy.staleMaxAgeMs } == true
                ) {
                    MediaLoad(cached.value, session, MediaLoadOrigin.StaleCache, error)
                } else {
                    throw error
                }
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { if (latest[key] == start.ticket) latest.remove(key) }
            }
        }
    }

    private fun requireActive(session: MediaSession): Binding = active?.takeIf { it.session === session }
        ?: throw CancellationException("Media account selection was replaced or closed")

    private fun validatePublication(session: MediaSession, key: Key, start: Start<*>) {
        requireActive(session)
        if (revision != start.revision || latest[key] != start.ticket) {
            throw CancellationException("Media response superseded by refresh or invalidation")
        }
    }

    private fun clearData() {
        check(revision < Long.MAX_VALUE) { "Media cache revision exhausted" }
        revision++
        pages.clear(); libraries.clear(); homes.clear(); details.clear(); people.clear(); listUserState.clear(); latest.clear()
    }

    private fun validateItem(session: MediaSession, item: MediaItem) {
        if (item.serverId != session.serverId || item.provider != session.provider) {
            throw ProtocolDecodingError("Media response returned a foreign provider/server identity")
        }
    }

    private fun allowsStale(error: DomainError): Boolean = error is NetworkUnreachableError || error is TimeoutError ||
        (error is ServerError && error.statusCode?.let { it in 500..599 } == true)

    private fun PageRequest.snapshot() = copy(filter = filter.copy(
        mediaTypes = filter.mediaTypes.toSet(), genres = filter.genres.toSet(), years = filter.years.toSet(),
        searchTerm = filter.searchTerm?.trim()?.takeIf { it.isNotEmpty() },
    ))

    private fun HomeFeed.allItems(): Sequence<MediaItem> = sequence {
        yieldAll(recommendations)
        yieldAll(resume)
        yieldAll(nextUp)
        yieldAll(latest)
    }
}

private const val HomeSnapshotSchemaVersion = 1L
private const val HomeSnapshotKeyPrefix = "mafei.media.home-snapshot.v1"
