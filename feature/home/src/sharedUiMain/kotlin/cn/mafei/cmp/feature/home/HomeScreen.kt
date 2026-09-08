package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.ui.InlineProblem
import cn.mafei.cmp.common.ui.MediaCard
import cn.mafei.cmp.core.domain.*

/** Stateless library browser. Insets and session ownership stay in the application shell. */
@Composable
fun HomeScreen(
    collections: List<MediaCollection>,
    items: List<MediaItem>,
    selectedCollection: CollectionId?,
    loading: Boolean,
    error: String?,
    stale: Boolean,
    canLoadMore: Boolean,
    onCollection: (MediaCollection) -> Unit,
    onItem: (MediaItem) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val compact = maxWidth < 600.dp
        val pagePadding = if (compact) 20.dp else 36.dp
        val selected = collections.firstOrNull { it.id == selectedCollection }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (compact) 136.dp else 164.dp),
            state = gridState,
            modifier = Modifier.widthIn(max = 1440.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = pagePadding, end = pagePadding, top = 28.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("我的媒体库", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("收藏的故事，慢慢看。", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!compact) {
                            OutlinedButton(onClick = onRetry, enabled = !loading) { Text(if (loading) "更新中" else "刷新媒体库") }
                            TextButton(onClick = onLogout) { Text("退出登录") }
                        }
                    }
                    if (compact) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRetry, enabled = !loading) { Text(if (loading) "更新中" else "刷新") }
                            TextButton(onClick = onLogout) { Text("退出登录") }
                        }
                    }
                    if (collections.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(collections, key = { it.id.value }) { collection ->
                                FilterChip(selected = collection.id == selectedCollection,
                                    onClick = { if (collection.id != selectedCollection) onCollection(collection) },
                                    label = { Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.widthIn(max = 240.dp).heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp))
                            }
                        }
                    }
                    if (stale) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Text("离线内容 · 当前显示缓存，恢复连接后可刷新。", Modifier.fillMaxWidth().padding(14.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    error?.let { InlineProblem(it, if (loading) null else onRetry) }
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                        if (items.isEmpty()) Text("正在打开媒体库…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!loading && error == null && collections.isEmpty()) {
                        LibraryEmptyState("这里还没有媒体库", "这个账号暂时没有可访问的媒体库。请检查服务器中的媒体库和账号权限。")
                    } else if (!loading && error == null && items.isEmpty()) {
                        LibraryEmptyState(if (selectedCollection == null) "选择一个媒体库" else "还没有发现内容",
                            if (selectedCollection == null) "从上方选择媒体库，开始浏览。" else "这个媒体库暂无内容，完成服务器扫描后可再刷新。")
                    }
                    if (selected != null && items.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(selected.name, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (canLoadMore) "已加载 ${items.size} 项" else "${items.size} 项", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (loading && items.isEmpty()) {
                items(6) { MediaPlaceholder() }
            }
            items(items, key = { it.id.value }) { item ->
                MediaCard(item.title, metadata(item), onClick = { onItem(item) },
                    status = when {
                        item.userData.isPlayed -> "已看"
                        item.userData.hasProgress -> "看到 ${item.userData.playbackPositionMs / 60_000} 分钟"
                        item.userData.isFavorite -> "已收藏"
                        else -> null
                    },
                    poster = artwork?.let { painter -> { posterModifier -> painter(item, posterModifier) } },
                    progress = item.runtimeMs?.takeIf { it > 0 && item.userData.hasProgress }?.let {
                        item.userData.playbackPositionMs.toFloat() / it
                    },
                )
            }
            if (canLoadMore) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = onLoadMore, enabled = !loading, modifier = Modifier.widthIn(min = 200.dp).heightIn(min = 48.dp)) {
                        Text(if (loading) "正在加载…" else "加载更多内容")
                    }
                }
            } else if (items.isNotEmpty() && !loading) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text("已显示全部内容", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, Modifier.widthIn(max = 460.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun MediaPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)))
        Box(Modifier.fillMaxWidth(.75f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        Box(Modifier.fillMaxWidth(.45f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)))
    }
}

private fun metadata(item: MediaItem): String = listOfNotNull(
    item.year?.toString(),
    when (item.mediaType) {
        MediaType.Movie -> "电影"
        MediaType.Series -> "剧集"
        MediaType.Season -> "季"
        MediaType.Episode -> "单集"
        MediaType.MusicArtist -> "音乐人"
        MediaType.MusicAlbum -> "专辑"
        MediaType.Audio -> "音频"
        MediaType.PhotoAlbum -> "相册"
        MediaType.Photo -> "照片"
        MediaType.BoxSet -> "合集"
        MediaType.Folder -> "文件夹"
        MediaType.Video -> "视频"
        MediaType.Unknown -> null
    },
).joinToString(" · ").ifEmpty { "媒体" }
