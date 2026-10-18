package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

internal class FavoriteBrowser(context: BrowserContext, private val nowEpochMs: () -> Long) : BrowserController(context) {
    private var favoriteTicket = 0L
    private var favoritePages = MediaPageAccumulator()
    private var favoritePagesRequest: PageRequest? = null
    private var activeRefresh: Long? = null
    private var activeRefreshJob: Job? = null
    private var successfulRefreshAt: Long? = null
    private var successfulRevision: Long? = null

    suspend fun refreshFavorites() = refreshFavorites(force = true)

    /** Tab reentry preserves accumulated pages while the last successful refresh is fresh. */
    suspend fun refreshFavoritesIfStale() = refreshFavorites(force = false)

    private suspend fun refreshFavorites(force: Boolean) {
        // A cancelled route job can still be unwinding when a rapid return starts a new request.
        if (!force && ((activeRefresh != null && activeRefreshJob?.isActive != false) || state.value.favoriteLoadingMore)) return
        val operation = ++favoriteTicket
        activeRefresh = operation
        activeRefreshJob = currentCoroutineContext()[Job]
        try {
            val revision = repository.refreshRevision(session)
            if (operation != favoriteTicket) return
            val age = successfulRefreshAt?.let { nowEpochMs() - it }
            if (!force && successfulRevision == revision && age != null && age in 0 until 30_000L) return
            successfulRefreshAt = null
            if (!capabilities.favoritesRead) {
                favoritePages = MediaPageAccumulator()
                favoritePagesRequest = null
                mutableState.value = state.value.copy(
                    favoriteItems = emptyList(), favoriteLoading = false, favoriteRefreshing = false,
                    favoriteLoadingMore = false, favoriteError = null, favoriteNextCursor = null, favoriteTotalEstimate = 0,
                )
                markFresh(operation, revision)
                return
            }
            val alreadyLoaded = state.value.favoriteItems.isNotEmpty() || state.value.favoriteTotalEstimate != null
            mutableState.value =
                state.value.copy(favoriteLoading = !alreadyLoaded, favoriteRefreshing = alreadyLoaded, favoriteLoadingMore = false, favoriteError = null)
            val request = favoriteRequest()
            val loaded = repository.browse(session = session, query = MediaQuery.Items(null), request = request, refresh = true)
            if (operation != favoriteTicket) return
            if (loaded.origin == MediaLoadOrigin.StaleCache && alreadyLoaded && favoritePagesRequest == request) {
                // Retain accumulated pages only for the same query; another sort owns different cursors.
                mutableState.value = state.value.copy(favoriteLoading = false, favoriteRefreshing = false)
                return
            }
            val freshPages = MediaPageAccumulator()
            val page = freshPages.append(null, loaded.value)
            favoritePages = freshPages
            favoritePagesRequest = request
            mutableState.value =
                state.value.copy(
                    favoriteItems = page.items,
                    favoriteLoading = false,
                    favoriteRefreshing = false,
                    favoriteLoadingMore = false,
                    favoriteError = null,
                    favoriteNextCursor = page.nextCursor,
                    favoriteTotalEstimate = page.totalEstimate,
                )
            if (loaded.origin != MediaLoadOrigin.StaleCache) markFresh(operation, revision)
        } catch (cancelled: CancellationException) {
            if (operation == favoriteTicket) {
                successfulRefreshAt = null
                mutableState.value = state.value.copy(favoriteLoading = false, favoriteRefreshing = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == favoriteTicket) {
                successfulRefreshAt = null
                mutableState.value = state.value.copy(favoriteLoading = false, favoriteRefreshing = false, favoriteError = browseError(failure))
            }
        } finally {
            if (activeRefresh == operation) {
                activeRefresh = null
                activeRefreshJob = null
            }
        }
    }

    private suspend fun markFresh(operation: Long, revision: Long) {
        val currentRevision = repository.refreshRevision(session)
        if (operation == favoriteTicket && revision == currentRevision) {
            successfulRevision = revision
            successfulRefreshAt = nowEpochMs()
        }
    }

    suspend fun loadMoreFavorites() {
        val before = state.value
        val cursor = before.favoriteNextCursor ?: return
        if (before.favoriteLoading || before.favoriteRefreshing || before.favoriteLoadingMore) return
        val operation = ++favoriteTicket
        mutableState.value = before.copy(favoriteLoadingMore = true, favoriteError = null)
        try {
            val loaded = repository.browse(session = session, query = MediaQuery.Items(null), request = favoriteRequest(cursor))
            if (operation != favoriteTicket) return
            val page = favoritePages.append(cursor, loaded.value)
            mutableState.value =
                state.value.copy(
                    favoriteItems = page.items,
                    favoriteLoadingMore = false,
                    favoriteNextCursor = page.nextCursor,
                    favoriteTotalEstimate = page.totalEstimate,
                )
        } catch (cancelled: CancellationException) {
            if (operation == favoriteTicket) mutableState.value = state.value.copy(favoriteLoadingMore = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == favoriteTicket) {
                mutableState.value = state.value.copy(favoriteLoadingMore = false, favoriteError = browseError(failure))
            }
        }
    }

    suspend fun setFavoriteSort(sort: MediaSort) {
        if (state.value.favoriteSort == sort) return
        mutableState.value = state.value.copy(favoriteSort = sort)
        refreshFavorites()
    }

    private fun favoriteRequest(cursor: PageCursor? = null): PageRequest =
        PageRequest(
            cursor = cursor,
            pageSize = CatalogPageSize,
            sort = state.value.favoriteSort,
            filter =
                MediaFilter(
                    // Fixed page semantics from FavoriteListViewModel.queryTypes. This
                    // is not an arbitrary user-composed filter, so Feiniu can forward
                    // it even though it still advertises favoritesFilter=false.
                    mediaTypes = FavoriteMediaTypes,
                    favoritesOnly = true,
                ),
        )
}
