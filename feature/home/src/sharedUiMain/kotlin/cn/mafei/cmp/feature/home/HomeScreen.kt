package cn.mafei.cmp.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.*

/** Stateless responsive browser. Session, insets and destination state stay in the shell. */
@Composable
fun HomeScreen(
    collections: List<MediaCollection>,
    items: List<MediaItem>,
    selectedCollection: CollectionId?,
    loading: Boolean,
    error: String?,
    stale: Boolean,
    canLoadMore: Boolean,
    overview: Boolean,
    layout: MaFeiLayout,
    onCollection: (MediaCollection) -> Unit,
    onItem: (MediaItem) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenLibrary: () -> Unit,
    accountActions: (@Composable () -> Unit)? = null,
    brandTitle: String = "马飞",
    brandSubtitle: String = "我的影院",
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
    backdropArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = artwork,
) {
    val selected = collections.firstOrNull { it.id == selectedCollection }
    val recentItems = items.sortedWith(compareByDescending<MediaItem> { it.dateAddedEpochMs ?: Long.MIN_VALUE }
        .thenBy { it.title })
    val progressItems = items.filter { it.userData.hasProgress && !it.userData.isPlayed }.take(8)
    LazyVerticalGrid(
        columns = GridCells.Fixed(layout.posterColumns),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = layout.pagePaddingDp.dp,
            end = layout.pagePaddingDp.dp,
            top = if (layout.navigation == NavigationPlacement.Bottom) 12.dp else MaFeiSpace.section,
            bottom = if (layout.navigation == NavigationPlacement.Bottom) 112.dp else 48.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(layout.gapDp.dp),
        verticalArrangement = Arrangement.spacedBy(MaFeiSpace.section),
    ) {
        if (overview && items.isNotEmpty()) {
            val hero = recentItems.firstOrNull() ?: items.first()
            item(span = { GridItemSpan(maxLineSpan) }) {
                Hero(hero, layout, onItem, backdropArtwork)
            }
        }
        if (layout.navigation == NavigationPlacement.Bottom) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrandHeader(brandTitle, brandSubtitle, loading, onRetry, accountActions)
            }
        }
        if (!overview || layout.navigation != NavigationPlacement.Bottom) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TopLine(if (overview) "首页" else selected?.name ?: "媒体库", loading, onRetry)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            StatusContent(collections, items, selectedCollection, loading, error, stale, overview, onRetry)
        }

        if (overview && items.isNotEmpty()) {
            if (progressItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MediaShelf("继续观看", progressItems, onItem, backdropArtwork, onOpenLibrary, layout)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeading("最近加入", selected?.name, onOpenLibrary)
            }
        } else if (!overview) {
            if (collections.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MaFeiSpace.small)) {
                    items(collections, key = { it.id.value }) { collection ->
                        FilterChip(
                            selected = collection.id == selectedCollection,
                            onClick = { if (collection.id != selectedCollection) onCollection(collection) },
                            label = { Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.widthIn(max = 240.dp).heightIn(min = MaFeiSpace.touchTarget),
                            shape = MaFeiShape.field,
                        )
                    }
                }
            }
            if (items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(selected?.name ?: "全部内容", Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (canLoadMore) "已加载 ${items.size} 项" else "${items.size} 项",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (loading && items.isEmpty()) items(6) { MediaPlaceholder() }
        val displayed = if (overview) recentItems.take((layout.posterColumns * 2).coerceAtLeast(6)) else items
        items(displayed, key = { it.id.value }) { item ->
            MediaCard(
                item.title, metadata(item), onClick = { onItem(item) },
                status = status(item),
                poster = artwork?.let { painter -> { posterModifier -> painter(item, posterModifier) } },
                progress = progress(item),
            )
        }
        if (!overview && canLoadMore) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = onLoadMore, enabled = !loading,
                    modifier = Modifier.widthIn(min = 200.dp).heightIn(min = MaFeiSpace.touchTarget)) {
                    Text(if (loading) "正在加载…" else "加载更多内容")
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(
    title: String,
    subtitle: String,
    loading: Boolean,
    onRetry: () -> Unit,
    accountActions: (@Composable () -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRetry, enabled = !loading, modifier = Modifier.size(MaFeiSpace.touchTarget)) {
            MaFeiIcon(MaFeiGlyph.Refresh, Modifier.size(20.dp))
        }
        accountActions?.invoke()
    }
}

@Composable
private fun TopLine(title: String, loading: Boolean, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        IconButton(onClick = onRetry, enabled = !loading, modifier = Modifier.size(MaFeiSpace.touchTarget)) {
            MaFeiIcon(MaFeiGlyph.Refresh, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StatusContent(
    collections: List<MediaCollection>, items: List<MediaItem>, selectedCollection: CollectionId?,
    loading: Boolean, error: String?, stale: Boolean, overview: Boolean, onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (stale) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaFeiShape.field) {
            Text("离线内容 · 当前显示缓存，恢复连接后可刷新。", Modifier.fillMaxWidth().padding(14.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        error?.let { InlineProblem(it, if (loading) null else onRetry) }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        if (!loading && error == null && collections.isEmpty()) {
            LibraryEmptyState("这里还没有媒体库", "这个账号暂时没有可访问的媒体库。请检查服务器中的媒体库和账号权限。")
        } else if (!loading && error == null && items.isEmpty()) {
            LibraryEmptyState(
                if (selectedCollection == null) "选择一个媒体库" else "还没有发现内容",
                if (selectedCollection == null && !overview) "从导航中选择媒体库，开始浏览。" else "这个媒体库暂无内容，完成服务器扫描后可再刷新。",
            )
        }
    }
}

@Composable
private fun Hero(
    item: MediaItem,
    layout: MaFeiLayout,
    onItem: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
) {
    val backdrop = rememberGlassBackdrop()
    Box(
        Modifier
            .then(
                if (layout.navigation == NavigationPlacement.Bottom) {
                    Modifier.offset(x = (-layout.pagePaddingDp).dp)
                        .requiredWidth((layout.gridWidthDp + layout.pagePaddingDp * 2f).dp)
                } else Modifier.fillMaxWidth()
            )
            .height(layout.heroHeightDp.dp)
            .clip(
                if (layout.navigation == NavigationPlacement.Bottom) {
                    RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
                } else MaFeiShape.panel
            ),
    ) {
        Box(
            Modifier.fillMaxSize().captureGlassBackdrop(backdrop),
        ) {
            if (artwork != null) artwork(item, Modifier.fillMaxSize()) else TextPoster(item.title, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    if (layout.horizontalHero) {
                        Brush.horizontalGradient(
                            listOf(Color(0xD40A141D), Color(0x740A141D), Color(0x120A141D), Color.Transparent),
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0x080A141D), Color(0x140A141D), Color(0xC80A141D)),
                        )
                    },
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Color(0x700A141D)),
                    ),
                ),
            )
        }
        Column(
            Modifier.align(if (layout.horizontalHero) Alignment.CenterStart else Alignment.BottomStart)
                .widthIn(max = if (layout.horizontalHero) 540.dp else 680.dp)
                .padding(if (layout.horizontalHero) 34.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(if (layout.horizontalHero) 12.dp else 8.dp),
        ) {
            Text(
                if (item.userData.hasProgress) "继续观看" else "最近加入",
                color = Color.White.copy(alpha = .72f),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(item.title, color = Color.White,
                style = if (layout.horizontalHero) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(metadata(item), color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodyLarge)
            if (layout.horizontalHero) item.overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            LiquidButton(
                onClick = { onItem(item) },
                backdrop = backdrop,
            ) {
                Text("查看详情", color = Color.White, style = MaterialTheme.typography.labelLarge)
                MaFeiIcon(MaFeiGlyph.Arrow, Modifier.size(18.dp), Color.White)
            }
        }
    }
}

@Composable
private fun MediaShelf(
    title: String,
    items: List<MediaItem>,
    onItem: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    onAll: () -> Unit,
    layout: MaFeiLayout,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(title, null, onAll)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(items, key = { _, item -> item.id.value }) { index, item ->
                val width = when {
                    layout.horizontalHero -> 276.dp
                    index == 0 -> 292.dp
                    else -> 224.dp
                }
                Column(
                    Modifier.width(width).mafeiInteractive({ onItem(item) }, label = "查看${item.title}"),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaFeiShape.poster)) {
                        if (artwork != null) artwork(item, Modifier.fillMaxSize()) else TextPoster(item.title, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color(0xC90A141D)))
                        ))
                        progress(item)?.let { value ->
                            LinearProgressIndicator({ value }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                                color = MaterialTheme.colorScheme.primary, trackColor = Color.Black.copy(alpha = .45f))
                        }
                        Column(
                            Modifier.align(Alignment.BottomStart).padding(12.dp).padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(item.title, color = Color.White, style = MaterialTheme.typography.titleSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(progressLabel(item), color = Color.White.copy(alpha = .78f),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String?, onAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(" · $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAll, modifier = Modifier.heightIn(min = MaFeiSpace.touchTarget)) {
            Text("查看全部")
            MaFeiIcon(MaFeiGlyph.Arrow, Modifier.size(16.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaFeiShape.poster)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)))
        Box(Modifier.fillMaxWidth(.75f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        Box(Modifier.fillMaxWidth(.45f).height(10.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)))
    }
}

private fun status(item: MediaItem): String? = when {
    item.userData.isPlayed -> "已看"
    item.userData.isFavorite -> "已收藏"
    else -> null
}

private fun progress(item: MediaItem): Float? = item.runtimeMs?.takeIf { it > 0 && item.userData.hasProgress }?.let {
    (item.userData.playbackPositionMs.toFloat() / it).coerceIn(0f, 1f)
}

private fun progressLabel(item: MediaItem): String {
    val minutes = item.userData.playbackPositionMs / 60_000
    val remaining = item.runtimeMs?.let { ((it - item.userData.playbackPositionMs).coerceAtLeast(0) / 60_000) }
    return if (remaining != null) "看到 $minutes 分钟 · 剩余 $remaining 分钟" else "看到 $minutes 分钟"
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
    item.runtimeMs?.takeIf { it > 0 }?.let { "${it / 60_000} 分钟" },
).joinToString(" · ").ifEmpty { "媒体" }
