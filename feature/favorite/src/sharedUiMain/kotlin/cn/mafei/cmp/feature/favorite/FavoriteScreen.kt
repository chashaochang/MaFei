package cn.mafei.cmp.feature.favorite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyEmptyView
import cn.mafei.cmp.common.ui.LegacyFailedView
import cn.mafei.cmp.common.ui.LegacyMediaGridSkeleton
import cn.mafei.cmp.common.ui.MaFeiPageTitleBar
import cn.mafei.cmp.common.ui.MaFeiPageTitleBarHeight
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.MaFeiPullRefresh
import cn.mafei.cmp.common.ui.LegacySortChoice
import cn.mafei.cmp.common.ui.LegacySortRow
import cn.mafei.cmp.common.ui.LegacySortSheet
import cn.mafei.cmp.common.ui.LegacyVideoCard
import cn.mafei.cmp.common.ui.legacyMediaColumns
import cn.mafei.cmp.common.ui.legacyMediaGridGap
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.SortDirection
import kotlinx.coroutines.flow.distinctUntilChanged

/** Mechanical Compose translation of ArkUI `FavoriteListPage(fromHome = true)`. */
@Composable
fun FavoriteScreen(
    items: List<MediaItem>,
    selectedSort: MediaSort,
    sortChoices: List<LegacySortChoice<MediaSort>>,
    loading: Boolean,
    refreshing: Boolean,
    loadingMore: Boolean,
    error: String?,
    canLoadMore: Boolean,
    layout: MaFeiLayout,
    gridState: LazyGridState,
    onSortSelected: (MediaSort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onItem: (MediaItem) -> Unit,
    bottomOverlayInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    var showSort by remember { mutableStateOf(false) }
    val contentWidth = layout.gridWidthDp
    val gap = legacyMediaGridGap(contentWidth)
    val horizontalPadding = gap * 2
    val bottomPadding = safeArea.bottom + bottomOverlayInset +
        if (layout.navigation == NavigationPlacement.Bottom) 112.dp else 30.dp
    val backdrop = rememberGlassBackdrop()

    LaunchedEffect(gridState, canLoadMore, loading, loadingMore, items.size) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.distinctUntilChanged().collect { (last, total) ->
            if (canLoadMore && !loading && !loadingMore && total > 0 && last >= total - 5) currentLoadMore()
        }
    }

    // 一级页面与「媒体库」共用同一条顶部栏。内容记录 backdrop，顶部栏作为兄弟层消费，
    // 避免把玻璃消费者重新录进同一个 layer 形成自采样递归。
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
            LegacySortRow(
                label = sortChoices.firstOrNull { it.value.field == selectedSort.field }?.label ?: "按添加日期",
                // Legacy FavoriteListViewModel displays the current data
                // source count after every page append.
                totalCount = items.size.toString(),
                descending = selectedSort.direction == SortDirection.Descending,
                onOpenSort = { showSort = true },
                onToggleDirection = onToggleSortDirection,
                modifier = Modifier.padding(
                    start = safeArea.start + horizontalPadding,
                    top = safeArea.top + MaFeiPageTitleBarHeight + 12.dp,
                    end = safeArea.end + horizontalPadding,
                ),
            )

            when {
                loading && items.isEmpty() -> LegacyMediaGridSkeleton(
                    columns = legacyMediaColumns(contentWidth),
                    startPadding = safeArea.start + horizontalPadding,
                    endPadding = safeArea.end + horizontalPadding,
                    topPadding = 12.dp,
                    bottomPadding = bottomPadding,
                    modifier = Modifier.weight(1f),
                )
                error != null && items.isEmpty() -> LegacyFailedView(onRetry, Modifier.weight(1f))
                items.isEmpty() -> LegacyEmptyView(modifier = Modifier.weight(1f))
                else -> MaFeiPullRefresh(
                    isRefreshing = refreshing,
                    onRefresh = onRetry,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(legacyMediaColumns(contentWidth)),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = safeArea.start + horizontalPadding,
                            end = safeArea.end + horizontalPadding,
                            bottom = bottomPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        items(items, key = { it.id.value }) { item ->
                            LegacyVideoCard(
                                item = item,
                                onClick = { onItem(item) },
                                poster = artwork?.let { draw -> { cardModifier -> draw(item, cardModifier) } },
                            )
                        }
                        if (loadingMore) item {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                contentAlignment = Alignment.Center,
                            ) {
                                MaFeiLoadingIndicator(
                                    modifier = Modifier.size(28.dp),
                                    style = MaFeiLoadingStyle.Inline,
                                )
                            }
                        }
                    }
                }
            }
        }
        MaFeiPageTitleBar(
            title = "收藏",
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showSort) {
        LegacySortSheet(
            choices = sortChoices,
            selected = selectedSort,
            onSelected = onSortSelected,
            onDismiss = { showSort = false },
        )
    }
}
