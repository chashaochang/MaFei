package cn.mafei.cmp.data.repositories

import cn.mafei.cmp.core.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

data class MediaPageState(val items: List<MediaItem> = emptyList(), val cursor: PageCursor? = null,
    val initialized: Boolean = false, val loading: Boolean = false, val loadingMore: Boolean = false,
    val error: String? = null)

/** Session-owned state shared by photo tabs/albums and live channel catalogs. */
class PagedMediaStore<Q>(private val pageSize: Int,
    private val fetch: suspend (Q, PageRequest, Boolean) -> Page<MediaItem>) {
    private val mutable = MutableStateFlow<Map<Q, MediaPageState>>(emptyMap())
    val pages = mutable.asStateFlow()

    suspend fun load(query: Q, refresh: Boolean = false, more: Boolean = false) {
        val before = claim(query, refresh, more) ?: return
        try {
            val page = fetch(query, PageRequest(pageSize = pageSize, cursor = if (more) before.cursor else null), refresh)
            currentCoroutineContext().ensureActive()
            val items = ((if (more) before.items else emptyList()) + page.items).distinctBy { it.id }
            finish(query, before, MediaPageState(items, page.nextCursor, initialized = true))
        } catch (cancelled: CancellationException) { finish(query, before, before); throw cancelled }
        catch (_: Exception) { finish(query, before, before.copy(loading = false, loadingMore = false, error = "加载失败，请重试")) }
    }

    fun favoriteChanged(id: MediaId, favorite: Boolean) {
        mutable.update { pages -> pages.mapValues { (_, page) -> page.copy(items = page.items.map {
            if (it.id == id) it.copy(userData = it.userData.copy(isFavorite = favorite)) else it
        }) } }
    }
    private fun claim(query: Q, refresh: Boolean, more: Boolean): MediaPageState? {
        while (true) {
            val snapshot = mutable.value
            val before = snapshot[query] ?: MediaPageState()
            if (before.loading || before.loadingMore || (!refresh && !more && before.initialized) ||
                (more && before.cursor == null)) return null
            if (mutable.compareAndSet(snapshot, snapshot + (query to before.copy(
                    loading = !more, loadingMore = more, error = null)))) return before
        }
    }

    private fun finish(query: Q, before: MediaPageState, result: MediaPageState) {
        val previousFavorites = before.items.associate { it.id to it.userData.isFavorite }
        mutable.update { pages ->
            // A favorite changed while this request was in flight must survive
            // refresh, error and cancellation, without freezing fresh server data.
            val edits = pages[query]?.items.orEmpty().filter {
                previousFavorites[it.id] != it.userData.isFavorite
            }.associate { it.id to it.userData.isFavorite }
            pages + (query to result.copy(items = result.items.map { item ->
                edits[item.id]?.let { item.copy(userData = item.userData.copy(isFavorite = it)) } ?: item
            }))
        }
    }
}
