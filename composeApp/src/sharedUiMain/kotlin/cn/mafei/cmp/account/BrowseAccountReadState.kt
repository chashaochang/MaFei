package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import cn.mafei.cmp.core.domain.PlaybackHistoryItem
import cn.mafei.cmp.feature.home.MediaBrowserState

/** UI projection only; the browser remains the owner of history and account facts. */
internal data class BrowseAccountReadState(
    val administrator: Boolean?,
    val historyItems: List<PlaybackHistoryItem>,
    val historyLoading: Boolean,
    val historyLoadingMore: Boolean,
    val historyLoadMoreFailed: Boolean,
    val historyCanLoadMore: Boolean,
    val historyError: String?,
)

/** Switching foreground documents does not invalidate the retained account tree. */
@Composable
internal fun rememberBrowseAccountReadState(source: State<MediaBrowserState>): State<BrowseAccountReadState> =
    remember(source) {
        derivedStateOf(structuralEqualityPolicy()) {
            val state = source.value
            BrowseAccountReadState(
                administrator = state.administrator,
                historyItems = state.historyItems,
                historyLoading = state.historyLoading,
                historyLoadingMore = state.historyLoadingMore,
                historyLoadMoreFailed = state.historyLoadMoreFailed,
                historyCanLoadMore = state.historyCanLoadMore,
                historyError = state.historyError,
            )
        }
    }
