package cn.mafei.cmp.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import cn.mafei.cmp.common.ui.LegacyVideoCard
import cn.mafei.cmp.common.ui.mediaArtworkGridHoverInset
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.MaFeiPullRefresh
import cn.mafei.cmp.common.ui.legacyMediaColumns
import cn.mafei.cmp.common.ui.legacyMediaGridGap
import cn.mafei.cmp.common.ui.mafeiPageTitleBarContentHeight
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaItem

/**
 * Jellyfin-style recent additions: one section per server library instead of a
 * single provider-aggregated grid. Each section keeps the library name visible
 * and uses the same responsive poster grid as a library page.
 */
@Composable
fun HomeLatestByLibraryScreen(
    collections: List<MediaCollection>,
    latestByCollection: Map<CollectionId, List<MediaItem>>,
    loadingCollections: Set<CollectionId>,
    errors: Map<CollectionId, String>,
    layout: MaFeiLayout,
    gridState: LazyGridState,
    onBack: (() -> Unit)?,
    onRetry: () -> Unit,
    onItem: (MediaItem) -> Unit,
    onPlay: ((MediaItem) -> Unit)? = null,
    onToggleWatched: ((MediaItem) -> Unit)? = null,
    onToggleFavorite: ((MediaItem) -> Unit)? = null,
    bottomOverlayInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val backdrop = rememberGlassBackdrop()
    val columns = legacyMediaColumns(layout.gridWidthDp)
    val gap = legacyMediaGridGap(layout.gridWidthDp)
    val hoverInset = mediaArtworkGridHoverInset(layout.gridWidthDp.dp, columns, gap)
    val horizontalPadding = gap * 2
    val bottomPadding = safeArea.bottom + bottomOverlayInset +
        if (layout.navigation == NavigationPlacement.Bottom) 112.dp else 30.dp
    val hasContent = collections.any { latestByCollection[it.id].orEmpty().isNotEmpty() }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .captureGlassBackdrop(backdrop),
        ) {
            if (collections.isEmpty()) {
                LegacyEmptyView(
                    title = "暂无媒体库",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MaFeiPullRefresh(
                    isRefreshing = loadingCollections.isNotEmpty() && !hasContent,
                    onRefresh = onRetry,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = safeArea.start + horizontalPadding,
                            top = safeArea.top + mafeiPageTitleBarContentHeight() + 12.dp,
                            end = safeArea.end + horizontalPadding,
                            bottom = bottomPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(maxOf(gap, hoverInset)),
                    ) {
                        collections.forEach { collection ->
                            val itemsForLibrary = latestByCollection[collection.id].orEmpty()
                            val error = errors[collection.id]
                            item(
                                key = "latest-library-header:${collection.id.value}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Text(
                                    "最近添加 · ${collection.name}",
                                    Modifier.fillMaxWidth()
                                        .padding(top = if (collection == collections.first()) 0.dp else 12.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            if (itemsForLibrary.isNotEmpty()) {
                                items(
                                    items = itemsForLibrary,
                                    key = { item -> "latest:${collection.id.value}:${item.id.value}" },
                                ) { item ->
                                    LegacyVideoCard(
                                        item = item,
                                        onClick = { onItem(item) },
                                        onPlay = onPlay?.let { play -> { play(item) } },
                                        onToggleWatched = onToggleWatched?.let { toggle -> { toggle(item) } },
                                        onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(item) } },
                                        poster = artwork?.let { draw -> { cardModifier -> draw(item, cardModifier) } },
                                    )
                                }
                            } else {
                                item(
                                    key = "latest-library-state:${collection.id.value}",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    when {
                                        collection.id in loadingCollections -> Box(
                                            Modifier.fillMaxWidth().height(88.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            MaFeiLoadingIndicator(
                                                modifier = Modifier,
                                                style = MaFeiLoadingStyle.Inline,
                                            )
                                        }
                                        error != null -> Text(
                                            "加载失败，点击重试",
                                            Modifier.fillMaxWidth().mafeiInteractive(onRetry, radiusDp = 8f)
                                                .padding(vertical = 20.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        else -> Text(
                                            "暂无最近添加",
                                            Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        item(
                            key = "latest-library-tail",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            Spacer(Modifier.height(1.dp))
                        }
                    }
                }
            }
        }
        MaFeiActionBar(
            title = "最近添加",
            onBack = onBack,
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
