package cn.mafei.cmp.feature.music

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageCursor
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.data.adapter_contract.MusicCatalogQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

data class MusicPageState(
    val items: List<MediaItem> = emptyList(),
    val cursor: PageCursor? = null,
    val initialized: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

data class MusicDetailState(
    val item: MediaItem? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** One store per authenticated runtime session. Each tab/detail retains its own page. */
class MusicCatalogStore(
    private val loadPage: suspend (MusicCatalogQuery, PageRequest, Boolean) -> Page<MediaItem>,
    private val loadItem: suspend (MediaId, Boolean) -> MediaItem,
) {
    private val mutablePages = MutableStateFlow<Map<MusicCatalogQuery, MusicPageState>>(emptyMap())
    val pages = mutablePages.asStateFlow()
    private val mutableDetails = MutableStateFlow<Map<MediaId, MusicDetailState>>(emptyMap())
    val details = mutableDetails.asStateFlow()

    suspend fun load(query: MusicCatalogQuery, refresh: Boolean = false, more: Boolean = false) {
        val before = pages.value[query] ?: MusicPageState()
        if (before.loading || before.loadingMore) return
        if (!refresh && !more && before.initialized) return
        if (more && before.cursor == null) return
        val size = when (query) {
            is MusicCatalogQuery.AlbumTracks -> 50
            is MusicCatalogQuery.ArtistPopularTracks -> 20
            else -> 40
        }
        setPage(query, before.copy(loading = !more, loadingMore = more, error = null))
        try {
            val page = loadPage(query, PageRequest(pageSize = size, cursor = if (more) before.cursor else null), refresh)
            val merged = ((if (more) before.items else emptyList()) + page.items).distinctBy { it.id }
            setPage(query, MusicPageState(merged, page.nextCursor, initialized = true))
        } catch (cancelled: CancellationException) {
            setPage(query, before)
            throw cancelled
        } catch (_: Exception) {
            setPage(query, before.copy(loading = false, loadingMore = false, error = "加载失败，请重试"))
        }
    }

    suspend fun detail(id: MediaId, refresh: Boolean = false) {
        val before = details.value[id] ?: MusicDetailState()
        if (before.loading || (!refresh && before.item != null)) return
        setDetail(id, before.copy(loading = true, error = null))
        try {
            setDetail(id, MusicDetailState(item = loadItem(id, refresh)))
        } catch (cancelled: CancellationException) {
            setDetail(id, before)
            throw cancelled
        } catch (_: Exception) {
            setDetail(id, before.copy(loading = false, error = "加载失败，请重试"))
        }
    }

    /** Apply only after the repository has acknowledged the favorite write. */
    fun favoriteChanged(id: MediaId, favorite: Boolean) {
        fun MediaItem.updated() = if (this.id == id) copy(userData = userData.copy(isFavorite = favorite)) else this
        mutablePages.update { states -> states.mapValues { (_, page) -> page.copy(items = page.items.map { it.updated() }) } }
        mutableDetails.update { states -> states.mapValues { (_, detail) -> detail.copy(item = detail.item?.updated()) } }
    }

    private fun setPage(query: MusicCatalogQuery, state: MusicPageState) {
        mutablePages.update { it + (query to state) }
    }
    private fun setDetail(id: MediaId, state: MusicDetailState) {
        mutableDetails.update { it + (id to state) }
    }
}
