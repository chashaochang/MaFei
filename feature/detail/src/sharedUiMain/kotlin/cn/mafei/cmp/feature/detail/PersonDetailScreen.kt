package cn.mafei.cmp.feature.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.InlineProblem
import cn.mafei.cmp.common.ui.LegacyVideoCard
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaPerson
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.flow.distinctUntilChanged

/** Mechanical Compose translation of the legacy HarmonyOS CastDetailPage. */
@Composable
fun PersonDetailScreen(
    person: MediaPerson?,
    movies: List<MediaItem>,
    series: List<MediaItem>,
    episodes: List<MediaItem>,
    loading: Boolean,
    sectionsLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onItem: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    canFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    favoriteBusy: Boolean = false,
    onLoadMoreMovies: (() -> Unit)? = null,
    onLoadMoreSeries: (() -> Unit)? = null,
    onLoadMoreEpisodes: (() -> Unit)? = null,
    loadingMoreSections: Set<String> = emptySet(),
    personArtwork: (@Composable (MediaPerson, Modifier) -> Unit)? = null,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val backdrop = rememberGlassBackdrop()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val actionBarFadeDistancePx = remember(density) { with(density) { 200.dp.toPx() } }
    val actionBarProgress by remember(scrollState) {
        derivedStateOf { (scrollState.value / actionBarFadeDistancePx).coerceIn(0f, 1f) }
    }
    var overviewSheetVisible by remember(person?.id?.value) { mutableStateOf(false) }
    LaunchedEffect(person?.id?.value) {
        if (person != null) scrollState.scrollTo(0)
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .captureGlassBackdrop(backdrop),
        ) {
            if (person == null) {
                Box(
                    Modifier.fillMaxSize().padding(
                        top = safeArea.top + 56.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = safeArea.bottom,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        error != null -> InlineProblem(error, onRetry)
                    }
                }
            } else {
                Column(
                    Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(top = safeArea.top + 56.dp, bottom = safeArea.bottom + 30.dp),
                ) {
                    PersonHeader(
                        person = person,
                        artwork = personArtwork,
                        canFavorite = canFavorite,
                        onToggleFavorite = onToggleFavorite?.takeUnless { favoriteBusy },
                        onOpenOverview = { overviewSheetVisible = true },
                    )
                    if (sectionsLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    }
                    PersonWorkSection(
                        "电影", movies, onItem, artwork,
                        onLoadMore = onLoadMoreMovies,
                        loadingMore = "movie" in loadingMoreSections,
                    )
                    PersonWorkSection(
                        "电视剧", series, onItem, artwork,
                        onLoadMore = onLoadMoreSeries,
                        loadingMore = "series" in loadingMoreSections,
                    )
                    PersonWorkSection(
                        "剧集", episodes, onItem, artwork,
                        onLoadMore = onLoadMoreEpisodes,
                        loadingMore = "episode" in loadingMoreSections,
                    )
                    if (error != null && !loading) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
                            InlineProblem(error, onRetry)
                        }
                    }
                }
            }
        }

        MaFeiActionBar(
            title = person?.name.orEmpty(),
            onBack = onBack,
            backdrop = backdrop,
            materialAlpha = if (person == null) 1f else actionBarProgress,
            titleAlpha = if (person == null) 0f else actionBarProgress,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (overviewSheetVisible) {
            OverviewSheetOverlay(
                overview = person?.overview,
                backdrop = backdrop,
                onDismiss = { overviewSheetVisible = false },
            )
        }
    }
}

@Composable
private fun PersonHeader(
    person: MediaPerson,
    artwork: (@Composable (MediaPerson, Modifier) -> Unit)?,
    canFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onOpenOverview: () -> Unit,
) {
    val imageShape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(200.dp)
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.fillMaxHeight().aspectRatio(291f / 436f)
                .clip(imageShape)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, imageShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null && person.image != null) {
                artwork(person, Modifier.fillMaxSize())
            } else {
                Image(
                    painter = painterResource(MaFeiLegacyDrawables.castAvatar),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    person.name,
                    Modifier.weight(1f).semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
                if (canFavorite && onToggleFavorite != null) {
                    // 旧版 CastDetailPage：人物本体也可收藏。
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))
                            .clickable(onClick = onToggleFavorite),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(
                                if (person.isFavorite) {
                                    MaFeiLegacyDrawables.favoriteSelected
                                } else {
                                    MaFeiLegacyDrawables.favoriteUnselected
                                },
                            ),
                            contentDescription = if (person.isFavorite) "取消收藏" else "收藏",
                            modifier = Modifier.size(22.dp),
                            colorFilter = if (person.isFavorite) {
                                null // 选中时保留原色（红心）
                            } else {
                                ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                        )
                    }
                }
            }
            person.sortName?.takeIf { it.isNotBlank() && it != person.name }?.let { sortName ->
                Text(
                    sortName,
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 1,
                )
            }
            person.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                OverviewSection(
                    overview = overview,
                    modifier = Modifier.padding(top = 10.dp),
                    onOpen = onOpenOverview,
                )
            }
        }
    }
}

@Composable
private fun PersonWorkSection(
    title: String,
    items: List<MediaItem>,
    onItem: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    onLoadMore: (() -> Unit)? = null,
    loadingMore: Boolean = false,
) {
    if (items.isEmpty()) return
    val listState = rememberLazyListState()
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    val currentLoadingMore by rememberUpdatedState(loadingMore)
    // 滚到尾部附近自动续页（契约的 PersonItems 分页能力）。
    LaunchedEffect(listState, items.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.distinctUntilChanged().collect { last ->
            if (last >= items.size - 2 && !currentLoadingMore) currentLoadMore?.invoke()
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            title,
            Modifier.padding(start = 16.dp).semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
        )
        LazyRow(
            Modifier.fillMaxWidth().height(194.dp).padding(top = 12.dp),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.width(8.dp)) }
            items(items, key = { it.id.value }) { item ->
                LegacyVideoCard(
                    item = item,
                    onClick = { onItem(item) },
                    modifier = Modifier.width(100.dp),
                    poster = artwork?.let { draw -> { imageModifier -> draw(item, imageModifier) } },
                )
            }
            if (loadingMore) {
                item {
                    Box(
                        Modifier.width(40.dp).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
            item { Spacer(Modifier.width(8.dp)) }
        }
    }
}
