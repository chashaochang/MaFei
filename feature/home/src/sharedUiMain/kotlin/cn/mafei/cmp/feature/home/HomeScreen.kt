package cn.mafei.cmp.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.*

private const val RecentFeedKey = "__home_recent__"
private const val HeroAutoplayMillis = 6_500

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
    onPlay: ((MediaItem) -> Unit)? = null,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenLibrary: () -> Unit,
    accountActions: (@Composable () -> Unit)? = null,
    brandTitle: String = "马飞",
    brandSubtitle: String = "我的影院",
    heroAutoplay: Boolean = true,
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
    backdropArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = artwork,
) {
    val selected = collections.firstOrNull { it.id == selectedCollection }
    val recentItems = items.sortedWith(compareByDescending<MediaItem> { it.dateAddedEpochMs ?: Long.MIN_VALUE }
        .thenBy { it.title })
    val progressItems = items.filter { it.userData.hasProgress && !it.userData.isPlayed }.take(8)
    val nextUpItems = items.filter {
        it.mediaType == MediaType.Episode && !it.userData.isPlayed && !it.userData.hasProgress
    }.take(8)
    var selectedFeedKey by rememberSaveable { mutableStateOf(RecentFeedKey) }
    val latestItems = if (selectedFeedKey == RecentFeedKey) recentItems else {
        items.filter { it.collectionId?.value == selectedFeedKey }
    }
    val latestColumns = when (layout.family) {
        MaFeiLayoutFamily.Phone -> 3
        MaFeiLayoutFamily.Tablet -> if (layout.gridWidthDp < 760f) 3 else 4
        MaFeiLayoutFamily.Desktop -> 5
    }
    val showStatus = stale || error != null || loading || collections.isEmpty() || items.isEmpty()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (overview) latestColumns else layout.posterColumns),
            state = gridState,
            modifier = Modifier.fillMaxHeight().widthIn(max = layout.contentMaxWidthDp.dp).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = layout.pagePaddingDp.dp,
                end = layout.pagePaddingDp.dp,
                top = when {
                    layout.navigation != NavigationPlacement.Bottom -> MaFeiSpace.section
                    overview -> 0.dp
                    else -> 12.dp
                },
                bottom = if (layout.navigation == NavigationPlacement.Bottom) 112.dp else 48.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(layout.gapDp.dp),
            verticalArrangement = Arrangement.spacedBy(MaFeiSpace.section),
        ) {
        if (!overview) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TopLine(selected?.name ?: "媒体库", loading, onRetry)
            }
        }
        if (overview && items.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                RecommendationHero(
                    items = recentItems.take(6),
                    layout = layout,
                    onItem = onItem,
                    artwork = backdropArtwork,
                    brandTitle = brandTitle,
                    brandSubtitle = brandSubtitle,
                    loading = loading,
                    onRetry = onRetry,
                    accountActions = accountActions,
                    autoplay = heroAutoplay,
                )
            }
        }
        if (showStatus) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StatusContent(collections, items, selectedCollection, loading, error, stale, overview, onRetry)
            }
        }

        if (overview && items.isNotEmpty()) {
            if (progressItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeMediaShelf(
                        title = "继续观看",
                        items = progressItems,
                        onItem = onPlay ?: onItem,
                        artwork = backdropArtwork,
                        showProgress = true,
                        layout = layout,
                    )
                }
            }
            if (nextUpItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeMediaShelf(
                        title = "接下来",
                        items = nextUpItems,
                        onItem = onPlay ?: onItem,
                        artwork = backdropArtwork,
                        showProgress = false,
                        layout = layout,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LatestFeedSelector(
                    collections = collections,
                    selectedKey = selectedFeedKey,
                    onSelected = { key ->
                        selectedFeedKey = key
                        collections.firstOrNull { it.id.value == key }?.let(onCollection)
                    },
                    onOpenLibrary = onOpenLibrary,
                )
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
        val displayed = if (overview) latestItems else items
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecommendationHero(
    items: List<MediaItem>,
    layout: MaFeiLayout,
    onItem: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    brandTitle: String,
    brandSubtitle: String,
    loading: Boolean,
    onRetry: () -> Unit,
    accountActions: (@Composable () -> Unit)?,
    autoplay: Boolean,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val autoplayProgress = remember { Animatable(0f) }
    LaunchedEffect(pagerState.currentPage, items.size) {
        autoplayProgress.snapTo(0f)
        if (autoplay && items.size > 1) {
            autoplayProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(HeroAutoplayMillis, easing = LinearEasing),
            )
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
        }
    }
    Box(
        Modifier
            .then(
                if (layout.navigation == NavigationPlacement.Bottom) {
                    // The grid centres a child that is wider than its padded content area.
                    // Requiring the full viewport width is therefore enough to bleed the
                    // hero to both edges; an extra negative offset clips narrow windows.
                    Modifier.requiredWidth((layout.gridWidthDp + layout.pagePaddingDp * 2f).dp)
                } else Modifier.fillMaxWidth()
            )
            .height(layout.heroHeightDp.dp)
            .clip(
                if (layout.navigation == NavigationPlacement.Bottom) {
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                } else RoundedCornerShape(26.dp)
            ),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            Box(
                Modifier.fillMaxSize().mafeiInteractive(
                    onClick = { onItem(item) },
                    radiusDp = if (layout.navigation == NavigationPlacement.Bottom) 0f else 26f,
                    label = "查看${item.title}",
                ),
            ) {
                if (artwork != null) artwork(item, Modifier.fillMaxSize())
                else TextPoster(item.title, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        if (layout.horizontalHero) {
                            Brush.horizontalGradient(
                                listOf(Color(0xDA071019), Color(0x8F071019), Color(0x16071019), Color.Transparent),
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(Color(0x16071019), Color(0x16071019), Color(0xE3071019)),
                            )
                        },
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0x8F071019))),
                    ),
                )
                Column(
                    Modifier.align(if (layout.horizontalHero) Alignment.CenterStart else Alignment.BottomStart)
                        .widthIn(max = if (layout.horizontalHero) 560.dp else 680.dp)
                        .padding(
                            start = if (layout.horizontalHero) 36.dp else 18.dp,
                            end = 20.dp,
                            bottom = if (items.size > 1) 38.dp else 24.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(if (layout.horizontalHero) 9.dp else 6.dp),
                ) {
                    Text(
                        item.title,
                        color = Color.White,
                        style = if (layout.horizontalHero) {
                            MaterialTheme.typography.displaySmall
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        metadata(item),
                        color = Color.White.copy(alpha = .82f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Text(
                            overview,
                            color = Color.White.copy(alpha = .78f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (layout.family == MaFeiLayoutFamily.Phone) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(brandTitle, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        brandSubtitle,
                        color = Color.White.copy(alpha = .72f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(
                    onClick = onRetry,
                    enabled = !loading,
                    modifier = Modifier.size(MaFeiSpace.touchTarget),
                ) {
                    MaFeiIcon(MaFeiGlyph.Refresh, Modifier.size(20.dp), Color.White)
                }
                if (accountActions != null) {
                    CompositionLocalProvider(LocalContentColor provides Color.White) { accountActions() }
                }
            }
        }
        if (items.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = if (layout.horizontalHero) 36.dp else 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.indices.forEach { index ->
                    LinearProgressIndicator(
                        progress = {
                            when {
                                index < pagerState.currentPage -> 1f
                                index == pagerState.currentPage -> autoplayProgress.value
                                else -> 0f
                            }
                        },
                        modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = .22f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeMediaShelf(
    title: String,
    items: List<MediaItem>,
    onItem: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    showProgress: Boolean,
    layout: MaFeiLayout,
) {
    val width = if (layout.family == MaFeiLayoutFamily.Phone) 160.dp else 240.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(if (layout.family == MaFeiLayoutFamily.Phone) 8.dp else 16.dp)) {
            items(items, key = { it.id.value }) { item ->
                Column(
                    Modifier.width(width).mafeiInteractive({ onItem(item) }, label = "查看${item.title}"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaFeiShape.poster)) {
                        if (artwork != null) artwork(item, Modifier.fillMaxSize()) else TextPoster(item.title, Modifier.fillMaxSize())
                        if (showProgress) progress(item)?.let { value ->
                            LinearProgressIndicator({ value }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                                color = MaterialTheme.colorScheme.primary, trackColor = Color.Black.copy(alpha = .36f))
                        }
                    }
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (showProgress) progressLabel(item) else metadata(item),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LatestFeedSelector(
    collections: List<MediaCollection>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = RecentFeedKey) {
                FeedTab("最近添加", selectedKey == RecentFeedKey) { onSelected(RecentFeedKey) }
            }
            items(collections, key = { it.id.value }) { collection ->
                FeedTab(collection.name, selectedKey == collection.id.value) { onSelected(collection.id.value) }
            }
        }
        IconButton(onClick = onOpenLibrary, modifier = Modifier.size(MaFeiSpace.touchTarget)) {
            MaFeiIcon(MaFeiGlyph.Arrow, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FeedTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.height(40.dp).mafeiInteractive(onClick, radiusDp = 8f, label = label)
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier.width(if (selected) 22.dp else 0.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
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
