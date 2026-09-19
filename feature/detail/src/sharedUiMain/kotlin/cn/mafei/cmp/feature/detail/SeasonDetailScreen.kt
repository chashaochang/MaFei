package cn.mafei.cmp.feature.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.components.LiquidFloatingTabs
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyMediaTagBadges
import cn.mafei.cmp.common.ui.LegacyTransientToast
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.TextPoster
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.PersonRef
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/** Mechanical Compose translation of the HarmonyOS `SeasonDetailPage`. */
@Composable
fun SeasonDetailScreen(
    series: MediaItem,
    season: MediaItem,
    seasons: List<MediaItem>,
    episodes: List<MediaItem>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSeasonSelected: (MediaItem) -> Unit,
    onEpisodeSelected: (MediaItem) -> Unit,
    onPlayFirst: (() -> Unit)?,
    playTarget: MediaItem? = null,
    onTogglePlayed: (() -> Unit)? = null,
    playbackBusy: Boolean = false,
    actionsBusy: Boolean = false,
    actionsError: String? = null,
    libraryName: String? = null,
    canLoadMoreEpisodes: Boolean = false,
    episodesLoadingMore: Boolean = false,
    onLoadMoreEpisodes: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    posterArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
    landscapeArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
    personArtwork: (@Composable (PersonRef, Modifier) -> Unit)? = null,
    onPersonSelected: (PersonRef) -> Unit = {},
    atmosphereArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = landscapeArtwork,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val backdrop = rememberGlassBackdrop()
    val actionBarBackdrop = rememberGlassBackdrop()
    val scrollState = rememberScrollState()
    val heroStretch = rememberLegacyHeroStretch(scrollState)
    val density = LocalDensity.current
    val collapseStart = remember(density) { with(density) { 72.dp.toPx() } }
    val collapseEnd = remember(density) { with(density) { 272.dp.toPx() } }
    val titleStart = remember(density) { with(density) { 200.dp.toPx() } }
    val titleEnd = remember(density) { with(density) { 272.dp.toPx() } }
    val collapseProgress by remember(scrollState, collapseStart, collapseEnd) {
        derivedStateOf {
            ((scrollState.value - collapseStart) / (collapseEnd - collapseStart)).coerceIn(0f, 1f)
        }
    }
    val titleOpacity by remember(scrollState, titleStart, titleEnd) {
        derivedStateOf {
            ((scrollState.value - titleStart) / (titleEnd - titleStart)).coerceIn(0f, .6f) / .6f
        }
    }
    var overviewSheetVisible by remember(season.id.value) { mutableStateOf(false) }
    LaunchedEffect(season.id.value) { scrollState.scrollTo(0) }

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().captureGlassBackdrop(actionBarBackdrop)) {
            Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
                SeasonAtmosphere(season, atmosphereArtwork)
            }
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth()
                    .nestedScroll(heroStretch.connection)
                    .verticalScroll(scrollState),
            ) {
                SeasonHero(
                    series = series,
                    season = season,
                    libraryName = libraryName,
                    posterArtwork = posterArtwork,
                    landscapeArtwork = landscapeArtwork,
                    extraHeight = heroStretch.extraHeight,
                )
                Column(
                    Modifier.fillMaxWidth().padding(bottom = safeArea.bottom + 30.dp),
                ) {
                    SeasonActions(
                        season = season,
                        target = playTarget ?: episodes.firstOrNull(),
                        onPlay = onPlayFirst,
                        onTogglePlayed = onTogglePlayed,
                        playbackBusy = playbackBusy,
                        actionsBusy = actionsBusy,
                        backdrop = backdrop,
                    )
                    season.overview?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(20.dp))
                        OverviewSection(
                            overview = it,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onOpen = { overviewSheetVisible = true },
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    SeasonEpisodeSection(
                        seasons = seasons,
                        season = season,
                        episodes = episodes,
                        loading = loading,
                        error = error,
                        onSeasonSelected = onSeasonSelected,
                        onEpisodeSelected = onEpisodeSelected,
                        artwork = landscapeArtwork,
                        backdrop = backdrop,
                        canLoadMore = canLoadMoreEpisodes,
                        loadingMore = episodesLoadingMore,
                        onLoadMore = onLoadMoreEpisodes,
                    )
                    if (season.people.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        LegacyPeopleSection(season.people, personArtwork, onPersonSelected)
                    }
                }
            }
        }

        MaFeiActionBar(
            title = "${series.title} ${season.title}".trim(),
            onBack = onBack,
            backdrop = actionBarBackdrop,
            materialAlpha = collapseProgress,
            titleAlpha = titleOpacity,
            contentColor = lerp(
                Color.White,
                MaterialTheme.colorScheme.onSurface,
                collapseProgress,
            ),
            modifier = Modifier.align(Alignment.TopCenter),
        )
        LegacyTransientToast(
            message = actionsError,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (overviewSheetVisible) {
            OverviewSheetOverlay(
                overview = season.overview,
                backdrop = actionBarBackdrop,
                onDismiss = { overviewSheetVisible = false },
            )
        }
    }
}

@Composable
private fun SeasonAtmosphere(
    season: MediaItem,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (artwork != null) {
            artwork(
                season,
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1.18f
                    scaleY = 1.18f
                    alpha = .95f
                }.blur(128.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.background.copy(alpha = .22f),
                    .56f to MaterialTheme.colorScheme.background.copy(alpha = .34f),
                    1f to MaterialTheme.colorScheme.background.copy(alpha = .72f),
                ),
            ),
        )
    }
}

@Composable
private fun SeasonHero(
    series: MediaItem,
    season: MediaItem,
    libraryName: String?,
    posterArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    landscapeArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    extraHeight: androidx.compose.ui.unit.Dp,
) {
    Box(Modifier.fillMaxWidth().height(430.dp + extraHeight)) {
        if (landscapeArtwork != null) {
            landscapeArtwork(season, Modifier.fillMaxSize().seasonFeatherBottom())
        } else {
            TextPoster(season.title, Modifier.fillMaxSize().seasonFeatherBottom())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .24f to Color.Black.copy(alpha = .08f),
                    .64f to Color.Black.copy(alpha = .26f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.width(96.dp).height(144.dp).clip(RoundedCornerShape(6.dp))) {
                if (posterArtwork != null) posterArtwork(season, Modifier.fillMaxSize())
                else TextPoster(season.title, Modifier.fillMaxSize())
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    season.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    series.title,
                    Modifier.semantics { heading() },
                    color = Color.White,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                legacySeriesInfo(series, libraryName).takeIf { it.isNotBlank() }?.let { info ->
                    Text(
                        info,
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 23.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonActions(
    season: MediaItem,
    target: MediaItem?,
    onPlay: (() -> Unit)?,
    onTogglePlayed: (() -> Unit)?,
    playbackBusy: Boolean,
    actionsBusy: Boolean,
    backdrop: GlassBackdrop,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val progress = target?.seasonPlaybackProgress()
        val remaining = target?.seasonRemainingTime().orEmpty()
        val label = target?.episodeNumber?.let { "第 $it 集" } ?: "播放"
        Box(Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp))) {
            if (progress != null && progress > 0f && progress < 1f && remaining.isNotBlank()) {
                Box(
                    Modifier.fillMaxWidth(progress).fillMaxHeight()
                        .background(Color.White.copy(alpha = .26f)),
                )
            }
            if (onPlay != null) {
                LiquidButton(
                    onClick = { if (!playbackBusy) onPlay() },
                    backdrop = backdrop,
                    height = 50.dp,
                    modifier = Modifier.fillMaxSize().semantics {
                        contentDescription = if (remaining.isBlank()) label else "$label $remaining"
                    },
                ) {
                    if (playbackBusy) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Image(
                            painter = painterResource(MaFeiLegacyDrawables.playRound),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (remaining.isNotBlank()) {
                            Text(
                                remaining.replace("剩余 ", "剩余").replace(" 小时 ", "小时")
                                    .replace(" 分钟", "分"),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .78f),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else {
                GlassSurface(
                    backdrop = backdrop,
                    spec = SeasonActionSurfaceSpec,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxSize().alpha(.48f).semantics {
                        contentDescription = label
                        disabled()
                    },
                ) {}
            }
        }
        SeasonWatchedAction(
            selected = season.userData.isPlayed,
            onAction = onTogglePlayed?.takeUnless { actionsBusy },
            backdrop = backdrop,
        )
    }
}

@Composable
private fun SeasonWatchedAction(
    selected: Boolean,
    onAction: (() -> Unit)?,
    backdrop: GlassBackdrop,
) {
    val label = if (selected) "标记为未播放" else "标记为已播放"
    val content: @Composable () -> Unit = {
        Image(
            painter = painterResource(MaFeiLegacyDrawables.watched),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
    if (onAction != null) {
        LiquidButton(
            onClick = onAction,
            backdrop = backdrop,
            height = 44.dp,
            horizontalPadding = 11.dp,
            modifier = Modifier.width(44.dp).semantics { contentDescription = label },
        ) { content() }
    } else {
        GlassSurface(
            backdrop = backdrop,
            spec = SeasonActionSurfaceSpec,
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(44.dp).alpha(.48f).semantics {
                contentDescription = label
                disabled()
            },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
    }
}

@Composable
private fun SeasonEpisodeSection(
    seasons: List<MediaItem>,
    season: MediaItem,
    episodes: List<MediaItem>,
    loading: Boolean,
    error: String?,
    onSeasonSelected: (MediaItem) -> Unit,
    onEpisodeSelected: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    backdrop: GlassBackdrop,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: (() -> Unit)?,
) {
    var selectedGroup by remember(season.id.value) { mutableIntStateOf(0) }
    val groups = remember(episodes) { episodes.chunked(30) }
    val groupLabels = remember(groups) {
        groups.mapIndexed { index, group ->
            val start = group.firstOrNull()?.episodeNumber ?: index * 30 + 1
            val end = group.lastOrNull()?.episodeNumber ?: start
            if (group.size == 1) "$start" else "$start - $end"
        }
    }
    LaunchedEffect(groups.size) {
        if (selectedGroup !in groups.indices) selectedGroup = 0
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "选集",
                Modifier.weight(1f).semantics { heading() },
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "共${seasons.size.coerceAtLeast(1)}季",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        if (seasons.size > 1) {
            Spacer(Modifier.height(12.dp))
            LiquidFloatingTabs(
                labels = seasons.map { it.title },
                selectedIndex = seasons.indexOfFirst { it.id == season.id }.coerceAtLeast(0),
                onSelected = { index -> seasons.getOrNull(index)?.let(onSeasonSelected) },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        if (!loading && groupLabels.size > 1) {
            Spacer(Modifier.height(12.dp))
            LiquidFloatingTabs(
                labels = groupLabels,
                selectedIndex = selectedGroup,
                onSelected = { selectedGroup = it },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        when {
            loading -> SeasonEpisodeSkeletons()
            error != null -> Text(
                error,
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
            groups.isNotEmpty() -> {
                val listState = rememberLazyListState()
                LaunchedEffect(season.id.value, selectedGroup) { listState.scrollToItem(0) }
                // 最后一组滚到尾部时续拉下一页（>200 集的超长季）。
                val currentLoadMore by rememberUpdatedState(onLoadMore)
                LaunchedEffect(listState, selectedGroup, groups.size, canLoadMore, loadingMore) {
                    snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    }.distinctUntilChanged().collect { last ->
                        if (canLoadMore && !loadingMore && selectedGroup == groups.lastIndex &&
                            last >= groups[selectedGroup].size - 2
                        ) {
                            currentLoadMore?.invoke()
                        }
                    }
                }
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item { Spacer(Modifier.width(4.dp)) }
                    items(groups[selectedGroup], key = { it.id.value }) { episode ->
                        SeasonEpisodeCard(
                            item = episode,
                            artwork = artwork,
                            onClick = { onEpisodeSelected(episode) },
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
                    item { Spacer(Modifier.width(4.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SeasonEpisodeSkeletons() {
    LazyRow(
        Modifier.fillMaxWidth().height(184.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.width(4.dp)) }
        items(3) {
            Column(Modifier.width(180.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    Modifier.fillMaxWidth(.82f).height(14.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    Modifier.fillMaxWidth(.58f).height(12.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        item { Spacer(Modifier.width(4.dp)) }
    }
}

@Composable
private fun SeasonEpisodeCard(
    item: MediaItem,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(180.dp).heightIn(min = 158.dp)
            .mafeiInteractive(onClick, radiusDp = 6f, label = item.title),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))) {
            if (artwork != null) artwork(item, Modifier.fillMaxSize())
            else TextPoster(item.title, Modifier.fillMaxSize())
            episodeProgress(item)?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = .34f),
                )
            }
            LegacyMediaTagBadges(
                tags = item.tags,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
        }
        Text(
            item.episodeNumber?.let { "$it.${item.title}" } ?: item.title,
            Modifier.fillMaxWidth().padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.overview.orEmpty(),
            Modifier.fillMaxWidth().padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.runtimeMs?.takeIf { it > 0L }?.let { runtime ->
            Text(
                legacyEpisodeDuration(runtime),
                Modifier.fillMaxWidth().padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun legacySeriesInfo(series: MediaItem, libraryName: String?): String = buildList {
    series.officialRating?.takeIf { it.isNotBlank() }?.let(::add)
    addAll(series.genres.filter { it.isNotBlank() })
    series.year?.let { add(it.toString()) }
    series.runtimeMs?.takeIf { it > 0L }?.let { add(durationLabel(it)) }
    libraryName?.takeIf { it.isNotBlank() }?.let(::add)
}.joinToString(" · ")

private fun MediaItem.seasonPlaybackProgress(): Float? {
    val duration = runtimeMs?.takeIf { it > 0L } ?: return null
    val explicit = userData.playedPercentage?.takeIf { it > 0.0 }?.div(100.0)?.toFloat()
    return (explicit ?: userData.playbackPositionMs.toFloat() / duration).coerceIn(0f, 1f)
}

private fun MediaItem.seasonRemainingTime(): String {
    val duration = runtimeMs?.takeIf { it > 0L } ?: return ""
    val position = when {
        userData.playbackPositionMs > 0L -> userData.playbackPositionMs
        (userData.playedPercentage ?: 0.0) > 0.0 ->
            (duration * (userData.playedPercentage!!.coerceIn(0.0, 100.0) / 100.0)).roundToInt().toLong()
        else -> 0L
    }
    if (position <= 0L || position >= duration || (userData.playedPercentage ?: 0.0) >= 100.0) return ""
    val remainingMinutes = (duration - position).coerceAtLeast(0L) / 60_000L
    val hours = remainingMinutes / 60L
    val minutes = remainingMinutes % 60L
    return if (hours > 0L) "剩余 $hours 小时 $minutes 分钟" else "剩余 $minutes 分钟"
}

private fun legacyEpisodeDuration(runtimeMs: Long): String {
    val totalSeconds = runtimeMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return buildString {
        if (hours > 0L) append("${hours}小时")
        if (minutes > 0L) append("${minutes}分钟")
        if (seconds > 0L) append("${seconds}秒")
        if (isEmpty()) append("0秒")
    }
}

private fun Modifier.seasonFeatherBottom(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White,
                    .64f to Color.White,
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

private val SeasonActionSurfaceSpec = GlassSurfaceSpec(
    role = GlassRole.Overlay,
    level = GlassLevel.Full,
    tintAlpha = .18f,
    intensity = .86f,
    cornerRadiusDp = 25f,
)
