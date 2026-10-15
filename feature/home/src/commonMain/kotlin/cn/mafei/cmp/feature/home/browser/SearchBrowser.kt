package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.CancellationException

internal class SearchBrowser(context: BrowserContext) : BrowserController(context) {
    private var searchTicket = 0L
    private var searchPages = MediaPageAccumulator()

    /**
     * Legacy SearchViewModel semantics: one screen load, at most 800 visible Movie/Series/Episode/Video results, with no UI-driven pagination.
     *
     * PageRequest is intentionally capped at 500 by the shared contract, so Jellyfin's old `limit = 800` request is reproduced as two internal segments. Feiniu
     * remains a single endpoint call because its adapter returns the provider's unpaged search result in one chunk.
     *
     * @return true only when the current request completed successfully. The caller uses this to preserve the old "submit saves history after success"
     *   behavior; incremental text changes ignore the return value.
     */
    suspend fun search(term: String): Boolean {
        val normalized = term.trim()
        val operation = ++searchTicket
        searchPages = MediaPageAccumulator()
        if (normalized.isEmpty()) {
            mutableState.value =
                state.value.copy(
                    searchQuery = "",
                    searchItems = emptyList(),
                    searchLoading = false,
                    searchLoadingMore = false,
                    searchError = null,
                    searchNextCursor = null,
                    searchTotalEstimate = null,
                )
            return false
        }
        mutableState.value =
            state.value.copy(
                searchQuery = normalized,
                searchItems = emptyList(),
                searchLoading = true,
                searchLoadingMore = false,
                searchError = null,
                searchNextCursor = null,
                searchTotalEstimate = null,
            )
        try {
            val request = PageRequest(pageSize = SearchFirstSegmentSize, filter = MediaFilter(mediaTypes = SearchMediaTypes))
            val first = repository.browse(session, MediaQuery.Search(normalized), request, refresh = true)
            if (operation != searchTicket) return false
            var page = searchPages.append(null, first.value)
            val secondCursor = page.nextCursor
            val remaining = LegacySearchLimit - page.items.size
            if (secondCursor != null && remaining > 0) {
                val second =
                    repository.browse(
                        session,
                        MediaQuery.Search(normalized),
                        PageRequest(
                            cursor = secondCursor,
                            pageSize = minOf(remaining, PageRequest.MAX_PAGE_SIZE),
                            filter = MediaFilter(mediaTypes = SearchMediaTypes),
                        ),
                        refresh = true,
                    )
                if (operation != searchTicket) return false
                page = searchPages.append(secondCursor, second.value)
            }
            val items = page.items.take(LegacySearchLimit)
            mutableState.value =
                state.value.copy(
                    searchItems = items,
                    searchLoading = false,
                    searchLoadingMore = false,
                    searchError = null,
                    // The old page displayed the number of mapped items, not the
                    // server's possibly larger TotalRecordCount.
                    searchNextCursor = null,
                    searchTotalEstimate = items.size,
                )
            return true
        } catch (cancelled: CancellationException) {
            if (operation == searchTicket) mutableState.value = state.value.copy(searchLoading = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == searchTicket) {
                mutableState.value = state.value.copy(searchLoading = false, searchError = browseError(failure))
            }
            return false
        }
    }

    fun clearSearch() {
        ++searchTicket
        searchPages = MediaPageAccumulator()
        mutableState.value =
            state.value.copy(
                searchQuery = "",
                searchItems = emptyList(),
                searchLoading = false,
                searchLoadingMore = false,
                searchError = null,
                searchNextCursor = null,
                searchTotalEstimate = null,
            )
    }
}
