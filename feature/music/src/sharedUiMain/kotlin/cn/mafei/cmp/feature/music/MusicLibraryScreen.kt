package cn.mafei.cmp.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.MediaItem

enum class MusicLibrarySection(val title: String) { Albums("专辑"), Artists("艺人"), Tracks("歌曲") }

@Composable
fun MusicLibraryScreen(
    title: String, section: MusicLibrarySection, state: MusicPageState, layout: MaFeiLayout,
    onSection: (MusicLibrarySection) -> Unit, onBack: (() -> Unit)?, onRetry: () -> Unit,
    onMore: () -> Unit, onOpen: (MediaItem) -> Unit, onPlay: (List<MediaItem>, Int) -> Unit,
    artwork: @Composable (MediaItem, Modifier) -> Unit, currentId: String? = null,
    preparing: Boolean = false, playing: Boolean = false, bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    // Retain independent offsets through tab switches; Navigation 3 saves these on route departure.
    val albumsState = rememberLazyGridState()
    val artistsState = rememberLazyGridState()
    val tracksState = rememberLazyListState()
    val safe = LocalMaFeiSafeArea.current
    MaFeiActionBarScaffold(title, onBack) {
        Column(Modifier.fillMaxSize().padding(start = safe.start, end = safe.end)) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = layout.pagePaddingDp.dp)) {
                MusicLibrarySection.entries.forEach { tab ->
                    Column(Modifier.weight(1f).fillMaxHeight()
                        .semantics { selected = section == tab }
                        .clickable(role = Role.Tab, onClick = { onSection(tab) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(tab.title, fontSize = 15.sp, color = if (section == tab) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(Modifier.width(28.dp).height(2.dp).background(
                            if (section == tab) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading && state.items.isEmpty() -> LegacyLoadingView(Modifier.fillMaxSize())
                    state.error != null && state.items.isEmpty() -> LegacyFailedView(onRetry, Modifier.fillMaxSize())
                    state.initialized && state.items.isEmpty() -> LegacyEmptyView(modifier = Modifier.fillMaxSize())
                    else -> MaFeiPullRefresh(state.loading && state.items.isNotEmpty(), onRetry, Modifier.fillMaxSize()) {
                        val padding = PaddingValues(start = layout.pagePaddingDp.dp, end = layout.pagePaddingDp.dp,
                            top = 16.dp, bottom = safe.bottom + bottomInset + 20.dp)
                        if (section == MusicLibrarySection.Tracks) {
                            LaunchedEffect(tracksState, state.cursor, state.loadingMore, state.error) {
                                snapshotFlow { tracksState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                    .collect { last -> if (last != null && last >= state.items.size - 5 &&
                                        state.cursor != null && !state.loadingMore && state.error == null) onMore() }
                            }
                            LazyColumn(Modifier.fillMaxSize(), state = tracksState, contentPadding = padding) {
                                itemsIndexed(state.items, key = { _, item -> item.id.value }) { index, item ->
                                    MusicTrackRow(item, index, item.id.value == currentId, preparing && item.id.value == currentId,
                                        playing, { onPlay(state.items, index) })
                                }
                                item { MusicPageFooter(state, onMore) }
                            }
                        } else BoxWithConstraints(Modifier.fillMaxSize()) {
                            val grid = if (section == MusicLibrarySection.Albums) albumsState else artistsState
                            LaunchedEffect(grid, state.cursor, state.loadingMore, state.error) {
                                snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                    .collect { last -> if (last != null && last >= state.items.size - 5 &&
                                        state.cursor != null && !state.loadingMore && state.error == null) onMore() }
                            }
                            LazyVerticalGrid(GridCells.Fixed(musicColumns(maxWidth.value)), Modifier.fillMaxSize(),
                                state = grid, contentPadding = padding, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                items(state.items, key = { it.id.value }) { item -> MusicArtworkCard(item, { onOpen(item) }, artwork) }
                                item(span = { GridItemSpan(maxLineSpan) }) { MusicPageFooter(state, onMore) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MusicPageFooter(state: MusicPageState, onMore: () -> Unit) {
    when {
        state.loadingMore -> LegacyLoadingView(Modifier.fillMaxWidth().height(52.dp))
        state.error != null && state.items.isNotEmpty() -> Box(Modifier.fillMaxWidth().height(52.dp)
            .clickable(onClick = onMore), contentAlignment = Alignment.Center) {
            Text("加载失败，点击重试", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
