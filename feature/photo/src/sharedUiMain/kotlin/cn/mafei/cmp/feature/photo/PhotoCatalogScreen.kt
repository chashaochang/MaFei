package cn.mafei.cmp.feature.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.data.repositories.MediaPageState

/** Shared content for the legacy photo library and album pages. */
@Composable
fun PhotoCatalogScreen(title: String, albums: Boolean, page: MediaPageState, onBack: (() -> Unit)?,
    onSection: ((Boolean) -> Unit)?, onRetry: () -> Unit, onMore: () -> Unit,
    onPhoto: (MediaItem) -> Unit, onAlbum: (MediaItem) -> Unit,
    artwork: @Composable (MediaItem, Modifier) -> Unit) {
    val safe = LocalMaFeiSafeArea.current
    val photoState = rememberLazyGridState()
    val albumState = rememberLazyGridState()
    val grid = if (albums) albumState else photoState
    val groups = remember(page.items) { photoDateGroups(page.items) }
    val more by rememberUpdatedState(onMore)
    LaunchedEffect(grid, page.cursor, page.loadingMore, page.error) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { index ->
            if (index != null && index >= grid.layoutInfo.totalItemsCount - 4 && page.cursor != null && !page.loadingMore && page.error == null) more()
        }
    }
    MaFeiActionBarScaffold(title, onBack) {
        Column(Modifier.fillMaxSize()) {
            if (onSection != null) Row(Modifier.fillMaxWidth().height(48.dp)) {
                listOf(false to "照片", true to "相册").forEach { (value, text) ->
                    Column(Modifier.weight(1f).fillMaxHeight().clickable { onSection(value) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                        Text(text, color = if (albums == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
                        Box(Modifier.width(28.dp).height(2.dp).background(if (albums == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background))
                    }
                }
            }
            when {
                page.loading && page.items.isEmpty() -> LegacyLoadingView(Modifier.fillMaxSize())
                page.error != null && page.items.isEmpty() -> LegacyFailedView(onRetry, Modifier.fillMaxSize())
                page.initialized && page.items.isEmpty() -> LegacyEmptyView(title = if (albums) "暂无相册" else "暂无照片", modifier = Modifier.fillMaxSize())
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val column = when { maxWidth < 320.dp -> if (albums) 2 else 3; maxWidth < 600.dp -> if (albums) 3 else 4
                        maxWidth < 840.dp -> if (albums) 4 else 5; maxWidth < 1440.dp -> if (albums) 5 else 6
                        maxWidth < 1920.dp -> if (albums) 6 else 8; else -> if (albums) 8 else 10 }
                    val pad = if (maxWidth < 600.dp) 12.dp else 24.dp
                    MaFeiPullRefresh(page.loading && page.items.isNotEmpty(), { if (!page.loadingMore) onRetry() }) {
                        LazyVerticalGrid(GridCells.Fixed(column), state = grid,
                            contentPadding = PaddingValues(start = safe.start + pad, end = safe.end + pad, top = 8.dp, bottom = safe.bottom + 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (albums) 12.dp else 4.dp),
                            verticalArrangement = Arrangement.spacedBy(if (albums) 18.dp else 4.dp)) {
                            if (albums) items(page.items, key = { it.id.value }) { album ->
                                Column(Modifier.fillMaxWidth().clickable { onAlbum(album) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    artwork(album, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                                    Text(album.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else groups.forEach { (date, photos) ->
                                item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) {
                                    Text(if (date == "unknown") "未知日期" else date.replace("-", " / "), fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                                }
                                items(photos, key = { it.id.value }) { photo ->
                                    artwork(photo, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(4.dp)).clickable { onPhoto(photo) })
                                }
                            }
                            if (page.loadingMore) item(span = { GridItemSpan(maxLineSpan) }) { LegacyLoadingView(Modifier.fillMaxWidth().height(56.dp)) }
                            if (page.error != null) item(span = { GridItemSpan(maxLineSpan) }) { LegacyFailedView(onRetry, Modifier.fillMaxWidth().height(100.dp)) }
                        }
                    }
                }
            }
        }
    }
}
