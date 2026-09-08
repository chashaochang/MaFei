package cn.mafei.cmp.data.repositories

import cn.mafei.cmp.common.core.EpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
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
    private data class ItemKey(val session: MediaSession, val id: MediaId) : Key
    private val pages = MediaCache<BrowseKey, Page<MediaItem>>(policy.maxEntries)
    private val libraries = MediaCache<CollectionsKey, Page<MediaCollection>>(policy.maxEntries)
    private val details = MediaCache<ItemKey, MediaItem>(policy.maxEntries)
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
        if (normalized is MediaQuery.Seasons && (frozen.cursor != null || !frozen.filter.isEmpty || frozen.sort != null)) {
            throw ClientConfigError("Seasons is a single-call surface without filters or sort")
        }
        val key = BrowseKey(session, normalized, frozen)
        return load(session, key, pages, refresh, store = { page ->
            if (normalized is MediaQuery.Items || normalized is MediaQuery.Search) {
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
                is MediaQuery.Items -> adapter.items(normalized.collectionId, frozen)
                is MediaQuery.Search -> adapter.search(normalized.term, frozen)
                is MediaQuery.Seasons -> adapter.seasons(normalized.seriesId)
                is MediaQuery.Episodes -> adapter.episodes(normalized.seriesId, normalized.seasonId, frozen)
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
        if (start.fresh) return MediaLoad(start.cached!!.value, session, MediaLoadOrigin.FreshCache)
        try {
            val value = fetch(start.adapter)
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
                ) MediaLoad(cached.value, session, MediaLoadOrigin.StaleCache, error)
                else throw error
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
        pages.clear(); libraries.clear(); details.clear(); listUserState.clear(); latest.clear()
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
}
