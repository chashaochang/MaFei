package cn.mafei.cmp.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.*

internal val HomeFloatingTabsInlineInset = 8.dp

internal val HomeFloatingTabsSlotHeight = 56.dp

internal val HomeFloatingActionSize = 40.dp

internal val HomeFloatingActionGap = 8.dp

private const val HomeFeedCardChromeHeightDp = 44f

internal fun homeFloatingTabsEndInset(
    overlapsActions: Boolean,
    hasSearch: Boolean,
    showRefresh: Boolean,
): Dp = when {
    !overlapsActions || !hasSearch -> 0.dp
    showRefresh -> 96.dp
    else -> 48.dp
}

internal data class HomeFloatingTabsPlacement(
    val visible: Boolean,
    val pinned: Boolean,
    val topPx: Int,
    val anchorIndex: Int?,
)

internal fun resolveHomeFloatingTabsPlacement(
    anchorOffsetY: Int?,
    anchorIndex: Int?,
    inlineInsetPx: Int,
    stickyTopPx: Int,
    beforeContentPaddingPx: Int = 0,
): HomeFloatingTabsPlacement {
    if (anchorOffsetY == null || anchorIndex == null) {
        return HomeFloatingTabsPlacement(
            visible = false,
            pinned = false,
            topPx = stickyTopPx,
            anchorIndex = null,
        )
    }
    // LazyGrid item offsets exclude its content padding; sibling overlays use
    // viewport coordinates, including the safe-area padding when no hero exists.
    val naturalTop = anchorOffsetY + beforeContentPaddingPx + inlineInsetPx
    val pinned = naturalTop <= stickyTopPx
    return HomeFloatingTabsPlacement(
        visible = true,
        pinned = pinned,
        topPx = maxOf(stickyTopPx, naturalTop),
        anchorIndex = anchorIndex,
    )
}

internal fun homeLatestFeedFillHeightDp(
    minimumHeightDp: Float,
    contentWidthDp: Float,
    columns: Int,
    columnGapDp: Float,
    rowGapDp: Float,
    itemCount: Int,
    emptyContentHeightDp: Float,
    trailingContentHeightDp: Float = 0f,
): Float {
    val safeColumns = columns.coerceAtLeast(1)
    val rows = if (itemCount <= 0) 0 else (itemCount + safeColumns - 1) / safeColumns
    val availableWidth = (
        contentWidthDp - columnGapDp.coerceAtLeast(0f) * (safeColumns - 1)
        ).coerceAtLeast(1f)
    val cardWidth = availableWidth / safeColumns
    val estimatedContentHeight = if (rows == 0) {
        emptyContentHeightDp.coerceAtLeast(0f)
    } else {
        rows * (cardWidth * 1.5f + HomeFeedCardChromeHeightDp) +
            (rows - 1) * rowGapDp.coerceAtLeast(0f)
    } + trailingContentHeightDp.coerceAtLeast(0f)
    return (minimumHeightDp - estimatedContentHeight).coerceAtLeast(0f)
}

internal enum class BrowseSortField(val label: String) {
    DateAdded("添加日期"),
    Title("名称"),
    Runtime("时长"),
}

@Composable
internal fun HomeRefreshContainer(
    stretchHero: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    heroStretchConnection: NestedScrollConnection,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (stretchHero) {
        // Phone home follows the original HarmonyOS interaction: the list stays
        // anchored while the hero itself absorbs and visualises the pull.
        // Keeping Material's pull connection here would make both containers
        // compete for the same downward gesture.
        Box(
            modifier = modifier.nestedScroll(heroStretchConnection),
            contentAlignment = Alignment.TopCenter,
        ) {
            content()
        }
    } else {
        MaFeiPullRefresh(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = modifier,
        ) {
            content()
        }
    }
}

@Composable
internal fun HomeSearchButton(
    onClick: () -> Unit,
    backdrop: cn.mafei.cmp.common.glass.GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.size(HomeFloatingActionSize).semantics { contentDescription = "搜索" },
        height = HomeFloatingActionSize,
        horizontalPadding = 8.dp,
    ) {
        MaFeiIcon(
            MaFeiGlyph.Search,
            Modifier.size(22.dp),
            MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun BrowseToolbar(
    sort: BrowseSortField,
    descending: Boolean,
    itemCount: Int,
    canLoadMore: Boolean,
    onNextSort: () -> Unit,
    onToggleDirection: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            sort.label,
            Modifier.mafeiInteractive(onNextSort, radiusDp = 10f, label = "切换排序方式")
                .padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(
            Modifier.size(38.dp).mafeiInteractive(onToggleDirection, radiusDp = 19f, label = "切换排序方向")
                .semantics { contentDescription = "切换排序方向" },
            contentAlignment = Alignment.Center,
        ) {
            MaFeiIcon(
                MaFeiGlyph.Arrow,
                Modifier.size(16.dp).graphicsLayer { rotationZ = if (descending) 90f else -90f },
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (canLoadMore) "$itemCount+" else itemCount.toString(),
            Modifier.clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
    }
}

internal fun sortBrowseItems(
    items: List<MediaItem>,
    field: BrowseSortField,
    descending: Boolean,
): List<MediaItem> {
    fun direction(value: Int): Int = if (descending) -value else value
    val comparator = Comparator<MediaItem> { left, right ->
        val compared = when (field) {
            BrowseSortField.Title -> direction(left.title.compareTo(right.title, ignoreCase = true))
            BrowseSortField.DateAdded -> compareNullable(left.dateAddedEpochMs, right.dateAddedEpochMs, descending)
            BrowseSortField.Runtime -> compareNullable(left.runtimeMs, right.runtimeMs, descending)
        }
        if (compared != 0) compared else left.id.value.compareTo(right.id.value)
    }
    return items.sortedWith(comparator)
}

internal fun List<MediaItem>.distinctByMediaId(): List<MediaItem> = distinctBy { it.id.value }

private fun <T : Comparable<T>> compareNullable(left: T?, right: T?, descending: Boolean): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    else -> left.compareTo(right).let { if (descending) -it else it }
}

@Composable
internal fun StatusContent(
    collections: List<MediaCollection>, items: List<MediaItem>, selectedCollection: CollectionId?,
    loading: Boolean, overview: Boolean, overviewHasContent: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) {
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Inline)
            }
        }
        if (!loading && !(overview && overviewHasContent) && collections.isEmpty()) {
            LibraryEmptyState("这里还没有媒体库", "这个账号暂时没有可访问的媒体库。请检查服务器中的媒体库和账号权限。")
        } else if (!loading && !(overview && overviewHasContent) && items.isEmpty()) {
            LibraryEmptyState(
                if (selectedCollection == null) "选择一个媒体库" else "还没有发现内容",
                if (selectedCollection == null && !overview) "从导航中选择媒体库，开始浏览。" else "这个媒体库暂无内容，完成服务器扫描后可再刷新。",
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
        Text(message, Modifier.widthIn(max = 460.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
internal fun MediaPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaFeiShape.poster)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)))
        Box(Modifier.fillMaxWidth(.75f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        Box(Modifier.fillMaxWidth(.45f).height(10.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)))
    }
}
