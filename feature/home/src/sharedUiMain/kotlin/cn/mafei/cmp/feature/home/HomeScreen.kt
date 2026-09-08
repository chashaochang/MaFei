package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.ui.InlineProblem
import cn.mafei.cmp.common.ui.MediaCard
import cn.mafei.cmp.common.ui.ScreenHeading
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
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(148.dp),
            state = gridState,
            modifier = Modifier.widthIn(max = 1240.dp).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScreenHeading("媒体库", "你的影片与剧集")
                    Row {
                        TextButton(onClick = onRetry, enabled = !loading) { Text("刷新") }
                        TextButton(onClick = onLogout) { Text("退出当前账号") }
                    }
                    if (collections.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(collections, key = { it.id.value }) { collection ->
                                FilterChip(selected = collection.id == selectedCollection,
                                    onClick = { onCollection(collection) }, label = { Text(collection.name) })
                            }
                        }
                    }
                    if (stale) Text("当前显示缓存内容，连接恢复后可刷新。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let { InlineProblem(it, if (loading) null else onRetry) }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!loading && error == null && collections.isEmpty()) {
                        Text("这个账号还没有可访问的媒体库。", style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = onRetry) { Text("刷新媒体库") }
                    } else if (!loading && error == null && items.isEmpty()) {
                        Text(if (selectedCollection == null) "选择一个媒体库，开始浏览。" else "这个媒体库暂无内容。")
                    }
                    if (items.isNotEmpty()) Text("封面加载尚未接入，以下为文字封面。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(items, key = { it.id.value }) { item ->
                MediaCard(item.title, metadata(item), onClick = { onItem(item) },
                    status = when {
                        item.userData.isPlayed -> "已看"
                        item.userData.hasProgress -> "继续观看 · ${item.userData.playbackPositionMs / 60_000} 分钟"
                        item.userData.isFavorite -> "已收藏"
                        else -> null
                    })
            }
            if (canLoadMore) item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = onLoadMore, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loading) "正在加载…" else "加载更多")
                }
            }
        }
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
