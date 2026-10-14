package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.CancellationException

internal class FavoriteBrowser(context: BrowserContext) : BrowserController(context) {
    private var favoriteTicket = 0L
    private var favoritePages = MediaPageAccumulator()

    suspend fun refreshFavorites() {
        val operation = ++favoriteTicket
        if (!capabilities.favoritesRead) {
            favoritePages = MediaPageAccumulator()
            mutableState.value = state.value.copy(
                favoriteItems = emptyList(),
                favoriteLoading = false,
                favoriteRefreshing = false,
                favoriteLoadingMore = false,
                favoriteError = null,
                favoriteNextCursor = null,
                favoriteTotalEstimate = 0,
            )
            return
        }
        val alreadyLoaded = state.value.favoriteItems.isNotEmpty() || state.value.favoriteTotalEstimate != null
        mutableState.value = state.value.copy(
            favoriteLoading = !alreadyLoaded,
            favoriteRefreshing = alreadyLoaded,
            favoriteLoadingMore = false,
            favoriteError = null,
        )
        try {
            val loaded = repository.browse(
                session = session,
                query = MediaQuery.Items(null),
                request = favoriteRequest(),
                refresh = true,
            )
            if (operation != favoriteTicket) return
            val freshPages = MediaPageAccumulator()
            val page = freshPages.append(null, loaded.value)
            favoritePages = freshPages
            mutableState.value = state.value.copy(
                favoriteItems = page.items,
                favoriteLoading = false,
                favoriteRefreshing = false,
                favoriteLoadingMore = false,
                favoriteError = null,
                favoriteNextCursor = page.nextCursor,
                favoriteTotalEstimate = page.totalEstimate,
            )
        } catch (cancelled: CancellationException) {
            if (operation == favoriteTicket) {
                mutableState.value = state.value.copy(favoriteLoading = false, favoriteRefreshing = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == favoriteTicket) {
                mutableState.value = state.value.copy(
                    favoriteLoading = false,
                    favoriteRefreshing = false,
                    favoriteError = browseError(failure),
                )
            }
        }
    }

    suspend fun loadMoreFavorites() {
        val before = state.value
        val cursor = before.favoriteNextCursor ?: return
        if (before.favoriteLoading || before.favoriteRefreshing || before.favoriteLoadingMore) return
        val operation = ++favoriteTicket
        mutableState.value = before.copy(favoriteLoadingMore = true, favoriteError = null)
        try {
            val loaded = repository.browse(
                session = session,
                query = MediaQuery.Items(null),
                request = favoriteRequest(cursor),
            )
            if (operation != favoriteTicket) return
            val page = favoritePages.append(cursor, loaded.value)
            mutableState.value = state.value.copy(
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

    private fun favoriteRequest(cursor: PageCursor? = null): PageRequest = PageRequest(
        cursor = cursor,
        pageSize = CatalogPageSize,
        sort = state.value.favoriteSort,
        filter = MediaFilter(
            // Fixed page semantics from FavoriteListViewModel.queryTypes. This
            // is not an arbitrary user-composed filter, so Feiniu can forward
            // it even though it still advertises favoritesFilter=false.
            mediaTypes = FavoriteMediaTypes,
            favoritesOnly = true,
        ),
    )
}
