package cn.mafei.cmp.feature.livetv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.data.repositories.MediaPageState

@Composable
fun LiveChannelScreen(title: String, page: MediaPageState, onBack: (() -> Unit)?, onRetry: () -> Unit,
    onMore: () -> Unit, onChannel: (List<MediaItem>, Int) -> Unit,
    artwork: @Composable (MediaItem, Modifier) -> Unit) {
    val safe = LocalMaFeiSafeArea.current
    val grid = rememberLazyGridState()
    LaunchedEffect(grid, page.cursor, page.loadingMore) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= page.items.lastIndex - 3 && page.cursor != null && !page.loadingMore && page.error == null) onMore()
        }
    }
    MaFeiActionBarScaffold(title, onBack) {
        when {
            page.loading && page.items.isEmpty() -> LegacyLoadingView(Modifier.fillMaxSize())
            page.error != null && page.items.isEmpty() -> LegacyFailedView(onRetry, Modifier.fillMaxSize())
            page.initialized && page.items.isEmpty() -> LegacyEmptyView(modifier = Modifier.fillMaxSize())
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = when { maxWidth < 320.dp -> 2; maxWidth < 600.dp -> 3; maxWidth < 840.dp -> 4
                    maxWidth < 1440.dp -> 6; maxWidth < 1920.dp -> 8; else -> 12 }
                val hoverInset = mediaArtworkGridHoverInset(maxWidth - safe.start - safe.end - 32.dp, columns, 8.dp, 2f)
                LazyVerticalGrid(GridCells.Fixed(columns), state = grid,
                    contentPadding = PaddingValues(start = safe.start + 16.dp, end = safe.end + 16.dp, top = hoverInset, bottom = safe.bottom + 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(hoverInset)) {
                    items(page.items.size, key = { page.items[it].id.value }) { index ->
                        val item = page.items[index]
                        LegacyVideoCard(item, { onChannel(page.items, index) }, subtitle = "", posterAspectRatio = 2f,
                            poster = { artwork(item, it) })
                    }
                    if (page.loadingMore) item(span = { GridItemSpan(maxLineSpan) }) { LegacyLoadingView(Modifier.fillMaxWidth().height(56.dp)) }
                    if (page.error != null) item(span = { GridItemSpan(maxLineSpan) }) { LegacyFailedView(onRetry, Modifier.fillMaxWidth().height(100.dp)) }
                }
            }
        }
    }
}
