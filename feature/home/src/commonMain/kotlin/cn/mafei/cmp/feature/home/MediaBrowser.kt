package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaBrowserState(
    val collections: List<MediaCollection> = emptyList(),
    val selectedCollection: CollectionId? = null,
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val stale: Boolean = false,
    val nextCursor: PageCursor? = null,
    val detailId: MediaId? = null,
    val detail: MediaItem? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
)

/** One screen and one account. Calls/state mutations run on the UI dispatcher.
 * Network suspends outside state updates; request tickets reject obsolete completions.
 * Repository still owns account isolation and cursor validation; no SDK leaks to UI.
 */
class MediaBrowser(private val repository: MediaRepository, private val session: MediaSession) {
    private val mutableState = MutableStateFlow(MediaBrowserState())
    val state: StateFlow<MediaBrowserState> = mutableState.asStateFlow()
    private var ticket = 0L
    private var detailTicket = 0L
    private var initialized = false
    private var pages = MediaPageAccumulator()

    suspend fun start() {
        if (initialized) return
        refresh()
    }

    suspend fun refresh() {
        val operation = ++ticket
        mutableState.value = state.value.copy(loading = true, error = null)
        try {
            val libraries = repository.collections(session, refresh = true)
            if (operation != ticket) return
            val selected = state.value.selectedCollection?.takeIf { id -> libraries.value.items.any { it.id == id } }
                ?: libraries.value.items.firstOrNull()?.id
            if (selected != state.value.selectedCollection) {
                pages = MediaPageAccumulator()
                mutableState.value = state.value.copy(items = emptyList(), nextCursor = null)
            }
            mutableState.value = state.value.copy(collections = libraries.value.items, selectedCollection = selected,
                stale = libraries.origin == MediaLoadOrigin.StaleCache)
            initialized = true
            // An empty library response is a real empty state, not permission to list the entire server.
            if (selected == null) {
                pages = MediaPageAccumulator()
                mutableState.value = state.value.copy(items = emptyList(), nextCursor = null, loading = false)
            } else loadFirst(selected, operation, libraries.origin == MediaLoadOrigin.StaleCache)
        } catch (cancelled: CancellationException) {
            if (operation == ticket) mutableState.value = state.value.copy(loading = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == ticket) mutableState.value = state.value.copy(loading = false, error = browseError(failure))
        }
    }

    suspend fun select(id: CollectionId) {
        if (state.value.collections.none { it.id == id }) return
        val operation = ++ticket
        pages = MediaPageAccumulator()
        mutableState.value = state.value.copy(selectedCollection = id, items = emptyList(), nextCursor = null,
            loading = true, error = null, stale = false)
        try { loadFirst(id, operation, false) }
        catch (cancelled: CancellationException) {
            if (operation == ticket) mutableState.value = state.value.copy(loading = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == ticket) mutableState.value = state.value.copy(loading = false, error = browseError(failure))
        }
    }

    private suspend fun loadFirst(id: CollectionId, operation: Long, staleLibraries: Boolean) {
        val loaded = repository.browse(session, MediaQuery.Items(id), libraryRequest(id), refresh = true)
        if (operation != ticket) return
        val freshPages = MediaPageAccumulator()
        val page = freshPages.append(null, loaded.value)
        pages = freshPages
        mutableState.value = state.value.copy(items = page.items, nextCursor = page.nextCursor,
            loading = false, error = null, stale = staleLibraries || loaded.origin == MediaLoadOrigin.StaleCache)
    }

    suspend fun loadMore() {
        val before = state.value
        val cursor = before.nextCursor ?: return
        val id = before.selectedCollection ?: return
        if (before.loading) return
        val operation = ++ticket
        mutableState.value = before.copy(loading = true, error = null)
        try {
            val loaded = repository.browse(session, MediaQuery.Items(id), libraryRequest(id, cursor))
            if (operation != ticket) return
            val page = pages.append(cursor, loaded.value)
            mutableState.value = state.value.copy(items = page.items, nextCursor = page.nextCursor, loading = false,
                stale = before.stale || loaded.origin == MediaLoadOrigin.StaleCache)
        } catch (cancelled: CancellationException) {
            if (operation == ticket) mutableState.value = state.value.copy(loading = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == ticket) mutableState.value = state.value.copy(loading = false, error = browseError(failure))
        }
    }

    private fun libraryRequest(id: CollectionId, cursor: PageCursor? = null): PageRequest = PageRequest(
        cursor = cursor,
        filter = MediaFilter(mediaTypes = state.value.collections.firstOrNull { it.id == id }?.mediaTypes.orEmpty()),
    )

    suspend fun openDetail(id: MediaId) {
        val operation = ++detailTicket
        mutableState.value = state.value.copy(detailId = id, detail = null, detailLoading = true, detailError = null)
        try {
            val loaded = repository.item(session, id, refresh = true)
            if (operation != detailTicket) return
            mutableState.value = state.value.copy(detail = loaded.value, detailLoading = false,
                detailError = if (loaded.origin == MediaLoadOrigin.StaleCache) "连接暂不可用，显示缓存详情" else null)
        } catch (cancelled: CancellationException) {
            if (operation == detailTicket) mutableState.value = state.value.copy(detailLoading = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailTicket) mutableState.value = state.value.copy(detailLoading = false, detailError = browseError(failure))
        }
    }

    fun back() {
        ++detailTicket
        mutableState.value = state.value.copy(detailId = null, detail = null, detailLoading = false, detailError = null)
    }
}

/** Never display exception messages, URLs, response bodies or credential-bearing causes. */
private fun browseError(error: Exception): String = when (error) {
    is AuthenticationError -> "登录已失效，请退出后重新登录"
    is AuthorizationError -> "当前账号没有访问权限"
    is NetworkUnreachableError, is TimeoutError -> "无法连接服务器，请检查网络后重试"
    is ResourceNotFoundError -> "内容已不存在或不可见"
    is ProtocolDecodingError -> "服务器返回了无法识别的数据"
    else -> "加载失败，请重试"
}
