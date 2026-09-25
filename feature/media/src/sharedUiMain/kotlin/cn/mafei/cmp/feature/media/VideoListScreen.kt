package cn.mafei.cmp.feature.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.ui.LegacyEmptyView
import cn.mafei.cmp.common.ui.LegacyFailedView
import cn.mafei.cmp.common.ui.LegacyMediaGridSkeleton
import cn.mafei.cmp.common.ui.LegacySortChoice
import cn.mafei.cmp.common.ui.LegacySortRow
import cn.mafei.cmp.common.ui.LegacySortSheet
import cn.mafei.cmp.common.ui.LegacyVideoCard
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.MaFeiPullRefresh
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.legacyMediaColumns
import cn.mafei.cmp.common.ui.legacyMediaGridGap
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaSort
import kotlinx.coroutines.flow.distinctUntilChanged

/** Mechanical Compose translation of ArkUI `VideoListPage`. */
@Composable
fun VideoListScreen(
    title: String,
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
    onBack: (() -> Unit)?,
    onSortSelected: (MediaSort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onItem: (MediaItem) -> Unit,
    bottomOverlayInset: Dp = 0.dp,
    bottomNavigationVisible: Boolean = layout.navigation == NavigationPlacement.Bottom,
    modifier: Modifier = Modifier,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
    // —— 管理员多选删除（legacy VideoListPage selectionMode）——
    canDeleteMedia: Boolean = false,
    deleteBusy: Boolean = false,
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onEnterSelection: (MediaItem) -> Unit = {},
    onToggleSelected: (MediaItem) -> Unit = {},
    onExitSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
) {
    val safeArea = LocalMaFeiSafeArea.current
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    var showSort by remember { mutableStateOf(false) }
    val contentWidth = layout.gridWidthDp
    val gap = legacyMediaGridGap(contentWidth)
    val horizontalPadding = gap * 2
    val bottomPadding = safeArea.bottom + bottomOverlayInset +
        if (bottomNavigationVisible) 112.dp else 30.dp
    // CPF may leave the final LazyGrid row under the sibling Bottom Tab when
    // the inset exists only as contentPadding. Keep a real tail item, matching
    // LibraryIndexScreen, so the last card always scrolls above the dock.
    val scrollTail = bottomPadding
    val actionBarBackdrop = if (onBack != null) rememberGlassBackdrop() else null

    LaunchedEffect(gridState, canLoadMore, loading, loadingMore, items.size) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.distinctUntilChanged().collect { (last, total) ->
            if (canLoadMore && !loading && !loadingMore && total > 0 && last >= total - 5) currentLoadMore()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .let { contentModifier ->
                    if (actionBarBackdrop == null) contentModifier
                    else contentModifier.captureGlassBackdrop(actionBarBackdrop)
                },
        ) {
            if (actionBarBackdrop != null) {
                Spacer(Modifier.height(safeArea.top + 56.dp))
            }
            if (selectionMode) {
                SelectionBar(
                    selectedCount = selectedIds.size,
                    deleteBusy = deleteBusy,
                    onExit = onExitSelection,
                    onDelete = onDeleteSelected,
                    modifier = Modifier.padding(
                        start = safeArea.start + horizontalPadding,
                        top = if (actionBarBackdrop == null) safeArea.top else 0.dp,
                        end = safeArea.end + horizontalPadding,
                    ),
                )
            } else {
                LegacySortRow(
                    label = sortChoices.firstOrNull { it.value.field == selectedSort.field }?.label ?: "按添加日期",
                    // ArkUI VideoListViewModel updates this from
                    // RefreshDataSource.totalCount(), i.e. the rows that are
                    // currently loaded, not the provider's TotalRecordCount.
                    totalCount = items.size.toString(),
                    descending = selectedSort.direction == cn.mafei.cmp.core.domain.SortDirection.Descending,
                    onOpenSort = { showSort = true },
                    onToggleDirection = onToggleSortDirection,
                    modifier = Modifier.padding(
                        start = safeArea.start + horizontalPadding,
                        top = if (actionBarBackdrop == null) safeArea.top else 0.dp,
                        end = safeArea.end + horizontalPadding,
                    ),
                )
            }

            when {
                loading && items.isEmpty() -> LegacyMediaGridSkeleton(
                    columns = legacyMediaColumns(contentWidth),
                    startPadding = safeArea.start + horizontalPadding,
                    endPadding = safeArea.end + horizontalPadding,
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
                            top = 0.dp,
                            end = safeArea.end + horizontalPadding,
                            bottom = 0.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        items(items, key = { it.id.value }) { item ->
                            val selected = item.id.value in selectedIds
                            Box {
                                LegacyVideoCard(
                                    item = item,
                                    onClick = {
                                        when {
                                            selectionMode -> onToggleSelected(item)
                                            else -> onItem(item)
                                        }
                                    },
                                    // 管理员长按进入多选（legacy enterSelection）。
                                    onLongClick = if (canDeleteMedia && !selectionMode) {
                                        { onEnterSelection(item) }
                                    } else null,
                                    poster = artwork?.let { draw -> { cardModifier -> draw(item, cardModifier) } },
                                )
                                if (selectionMode) {
                                    SelectionBadge(
                                        selected = selected,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                                    )
                                }
                            }
                        }
                        if (loadingMore) {
                            item {
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
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.fillMaxWidth().height(scrollTail))
                        }
                    }
                }
            }
        }
        if (actionBarBackdrop != null) {
            MaFeiActionBar(
                title = title,
                onBack = onBack,
                backdrop = actionBarBackdrop,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
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

/** 多选模式顶栏：计数 + 取消 + 删除（确认弹窗由外层处理）。 */
@Composable
private fun SelectionBar(
    selectedCount: Int,
    deleteBusy: Boolean,
    onExit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "已选 $selectedCount 项",
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "取消",
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onExit)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Text(
            if (deleteBusy) "删除中…" else "删除",
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = selectedCount > 0 && !deleteBusy, onClick = onDelete)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (selectedCount > 0 && !deleteBusy) {
                Color(0xFFFF6B5E)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f)
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 多选勾选角标。 */
@Composable
private fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = .82f),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
