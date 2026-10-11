package cn.mafei.cmp.feature.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.NavigationEdge
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.components.LiquidFloatingTabs
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyMediaTagBadges
import cn.mafei.cmp.common.ui.LegacyPosterPlaceholder
import cn.mafei.cmp.common.ui.LegacyTransientToast
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.MaFeiProgressLine
import cn.mafei.cmp.common.ui.LocalMaFeiNavigationChrome
import cn.mafei.cmp.common.ui.TextPoster
import cn.mafei.cmp.common.ui.mafeiPageTitleBarContentHeight
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.PersonRef
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/** Mechanical Compose translation of the HarmonyOS `SeasonDetailPage`. */
@Composable
fun SeasonDetailScreen(
    series: MediaItem,
    season: MediaItem,
    seasons: List<MediaItem>,
    episodes: List<MediaItem>,
    loading: Boolean,
    // Compatibility parameters from the old paged implementation. The ArkUI
    // season page consumes the resolved legacy window instead of exposing a
    // second paging/error surface inside the episode row.
    error: String? = null,
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
    episodeArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)? = null,
    personArtwork: (@Composable (PersonRef, Modifier) -> Unit)? = null,
    onPersonSelected: (PersonRef) -> Unit = {},
    atmosphereArtwork: (@Composable (MediaItem, Dp, Modifier) -> Unit)? = null,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val backdrop = rememberGlassBackdrop()
    val actionBarBackdrop = rememberGlassBackdrop()
    val largeDetailLayout = LocalMaFeiNavigationChrome.current.effectiveLayout
        ?.let { it.navigation != NavigationPlacement.Bottom } == true
    val effectiveLayout = LocalMaFeiNavigationChrome.current.effectiveLayout
    val atmosphereBleed = effectiveLayout?.let { layout ->
        if (layout.navigation == NavigationPlacement.Sidebar ||
            layout.navigation == NavigationPlacement.Rail
        ) layout.navigationWidthDp.dp else 0.dp
    } ?: 0.dp
    val atmosphereLeadingBleed = if (
        effectiveLayout?.navigationEdge == NavigationEdge.Leading
    ) atmosphereBleed else 0.dp
    val atmosphereTrailingBleed = if (
        effectiveLayout?.navigationEdge == NavigationEdge.Trailing
    ) atmosphereBleed else 0.dp
    val scrollState = rememberScrollState()
    val heroStretch = rememberLegacyHeroStretch(scrollState)
    val density = LocalDensity.current
    val collapseStart = remember(density) { with(density) { 72.dp.toPx() } }
    val collapseEnd = remember(density) { with(density) { 272.dp.toPx() } }
    val titleStart = remember(density) { with(density) { 200.dp.toPx() } }
    val titleEnd = remember(density) { with(density) { 272.dp.toPx() } }
    val collapseProgress = remember(scrollState, collapseStart, collapseEnd) {
        derivedStateOf {
            ((scrollState.value - collapseStart) / (collapseEnd - collapseStart)).coerceIn(0f, 1f)
        }
    }
    val titleOpacity = remember(scrollState, titleStart, titleEnd) {
        derivedStateOf {
            ((scrollState.value - titleStart) / (titleEnd - titleStart)).coerceIn(0f, .6f) / .6f
        }
    }
    var overviewSheetVisible by remember(season.id.value) { mutableStateOf(false) }
    var episodeOverview by remember(season.id.value) { mutableStateOf<String?>(null) }
    val seriesName = legacySeasonSeriesName(series, season, libraryName)

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().captureGlassBackdrop(actionBarBackdrop)) {
            Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
                DetailAtmosphere(
                    item = season,
                    artwork = landscapeArtwork,
                    cachedArtwork = atmosphereArtwork,
                    leadingBleed = atmosphereLeadingBleed,
                    trailingBleed = atmosphereTrailingBleed,
                    unblurred = largeDetailLayout,
                )
            }
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(
                    Modifier.fillMaxWidth()
                        .then(if (largeDetailLayout) Modifier else Modifier.nestedScroll(heroStretch.connection))
                        // Hero stretching replaces the platform overscroll visual;
                        // running both moves the entire page during a fast return
                        // to the top instead of keeping the hero's top edge fixed.
                        .verticalScroll(scrollState, overscrollEffect = null),
                ) {
                    if (largeDetailLayout) {
                        SeasonProjectHeader(
                            seriesName = seriesName,
                            series = series,
                            season = season,
                            libraryName = libraryName,
                            safeAreaTop = safeArea.top,
                            sharedArtworkModifier = sharedArtworkModifier,
                            modifier = Modifier.align(Alignment.Start),
                        )
                    } else {
                        SeasonHero(
                            seriesName = seriesName,
                            series = series,
                            season = season,
                            libraryName = libraryName,
                            posterArtwork = posterArtwork,
                            landscapeArtwork = landscapeArtwork,
                            sharedArtworkModifier = sharedArtworkModifier,
                            extraHeight = heroStretch.extraHeight,
                        )
                    }
                    Column(
                        Modifier
                            .align(if (largeDetailLayout) Alignment.Start else Alignment.CenterHorizontally)
                            .then(
                                if (largeDetailLayout) Modifier.fillMaxWidth()
                                else Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                            )
                            .padding(
                                start = detailOuterHorizontalInset(),
                                end = detailOuterHorizontalInset(),
                                bottom = safeArea.bottom + 30.dp,
                            ),
                    ) {
                        SeasonActions(
                            season = season,
                            target = playTarget ?: episodes.firstOrNull(),
                            onPlay = onPlayFirst,
                            onTogglePlayed = onTogglePlayed,
                            playbackBusy = playbackBusy,
                            actionsBusy = actionsBusy,
                            backdrop = backdrop,
                            largeLayout = largeDetailLayout,
                        )
                        season.overview?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(DetailSectionSpacing))
                            OverviewSection(
                                overview = it,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                onOpen = { overviewSheetVisible = true },
                            )
                        }
                        Spacer(Modifier.height(DetailSectionSpacing))
                        SeasonEpisodeSection(
                            seasons = seasons,
                            season = season,
                            episodes = episodes,
                            loading = loading,
                            onSeasonSelected = onSeasonSelected,
                            onEpisodeSelected = onEpisodeSelected,
                            onEpisodeOverview = { episodeOverview = it },
                            artwork = episodeArtwork,
                            sharedArtworkModifier = sharedArtworkModifier,
                            backdrop = backdrop,
                        )
                        if (season.people.isNotEmpty()) {
                            Spacer(Modifier.height(DetailSectionSpacing))
                            LegacyPeopleSection(season.people, personArtwork, onPersonSelected)
                        }
                    }
                }
            }
        }

        DetailScrollActionBar(
            title = "$seriesName ${season.title}".trim(),
            onBack = {
                when {
                    episodeOverview != null -> episodeOverview = null
                    overviewSheetVisible -> overviewSheetVisible = false
                    else -> onBack()
                }
            },
            backdrop = actionBarBackdrop,
            materialAlpha = collapseProgress,
            titleAlpha = titleOpacity,
            expandedContentColor = Color.White,
            collapsedContentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        LegacyTransientToast(
            message = actionsError,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        cn.mafei.cmp.common.ui.MaFeiAnimatedModalOverlay(
            value = (episodeOverview ?: season.overview).takeIf { overviewSheetVisible || episodeOverview != null },
            backdrop = actionBarBackdrop,
            useWindowHost = true,
            onDismiss = {
                if (episodeOverview != null) episodeOverview = null else overviewSheetVisible = false
            },
        ) { overview, modalBackdrop, motion ->
            OverviewSheetOverlay(
                overview = overview,
                backdrop = modalBackdrop,
                onDismiss = {
                    if (episodeOverview != null) episodeOverview = null else overviewSheetVisible = false
                },
                surfaceMotion = motion,
            )
        }
    }
}

/**
 * Large-screen season header follows the video detail header: the current
 * artwork remains the page atmosphere and the reading content starts at the
 * shared leading gutter instead of introducing a second hero card.
 */
@Composable
private fun SeasonProjectHeader(
    seriesName: String,
    series: MediaItem,
    season: MediaItem,
    libraryName: String?,
    safeAreaTop: androidx.compose.ui.unit.Dp,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)?,
    modifier: Modifier = Modifier,
) {
    val horizontalPadding = detailPageHorizontalPadding()
    val headerBounds = modifier.fillMaxWidth()
    Box(
        (sharedArtworkModifier?.invoke(season, headerBounds) ?: headerBounds)
            .fillMaxWidth()
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = safeAreaTop + mafeiPageTitleBarContentHeight() + 20.dp,
                bottom = 24.dp,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                seriesName,
                color = Color.White.copy(alpha = .82f),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                season.title,
                Modifier.semantics { heading() },
                color = Color.White,
                fontSize = 44.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            DetailMetadata(series, libraryName, Color.White)
        }
    }
}

@Composable
private fun SeasonHero(
    seriesName: String,
    series: MediaItem,
    season: MediaItem,
    libraryName: String?,
    posterArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    landscapeArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)?,
    extraHeight: androidx.compose.ui.unit.Dp,
) {
    val horizontalPadding = detailPageHorizontalPadding()
    val heroBounds = Modifier.fillMaxWidth().height(430.dp + extraHeight)
    Box(
        sharedArtworkModifier?.invoke(season, heroBounds) ?: heroBounds,
    ) {
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
                .padding(start = horizontalPadding, end = horizontalPadding, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.width(96.dp).height(144.dp).clip(RoundedCornerShape(8.dp))) {
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
                    seriesName,
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
    largeLayout: Boolean,
) {
    Row(
        Modifier
            .then(if (largeLayout) Modifier.widthIn(max = LargeDetailControlWidth) else Modifier)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val progress = target?.seasonPlaybackProgress()
        val label = target?.episodeNumber?.let { "第 $it 集" } ?: "播放"
        Box(
            Modifier
                .then(
                    if (largeLayout) Modifier.widthIn(max = LargeDetailPlayWidth).weight(1f)
                    else Modifier.weight(1f),
                )
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp)),
        ) {
            if (progress != null && progress > 0f && progress < 1f) {
                Box(
                    Modifier.fillMaxWidth(progress).fillMaxHeight()
                        .background(Color.White.copy(alpha = .26f)),
                )
            }
            if (onPlay != null) {
                LiquidButton(
                    onClick = { if (!playbackBusy) onPlay() },
                    backdrop = backdrop,
                    isInteractive = !playbackBusy,
                    height = 50.dp,
                    modifier = Modifier.fillMaxSize().semantics {
                        contentDescription = label
                    },
                ) {
                    SeasonPlayActionContent(label, playbackBusy)
                }
            } else {
                GlassSurface(
                    backdrop = backdrop,
                    spec = DetailActionSurfaceSpec,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxSize().alpha(.48f).semantics {
                        contentDescription = label
                        disabled()
                    },
                ) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SeasonPlayActionContent(label, busy = false)
                    }
                }
            }
        }
        SeasonWatchedAction(
            selected = season.userData.isPlayed,
            onAction = onTogglePlayed,
            busy = actionsBusy,
            backdrop = backdrop,
        )
    }
}

@Composable
private fun RowScope.SeasonPlayActionContent(
    label: String,
    busy: Boolean,
) {
    AnimatedContent(
        targetState = Pair(label, busy),
        transitionSpec = {
            fadeIn(tween(DetailMotionFastMillis)) togetherWith
                fadeOut(tween(DetailMotionExitMillis))
        },
        label = "season-play-state",
    ) { (currentLabel, isBusy) ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (isBusy) {
                    MaFeiLoadingIndicator(
                        modifier = Modifier.size(20.dp),
                        style = MaFeiLoadingStyle.Button,
                        tint = Color.White,
                    )
                } else {
                    Image(
                        painter = painterResource(MaFeiLegacyDrawables.playRound),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    currentLabel,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SeasonWatchedAction(
    selected: Boolean,
    onAction: (() -> Unit)?,
    busy: Boolean,
    backdrop: GlassBackdrop,
) {
    val label = if (selected) "标记为未播放" else "标记为已播放"
    val busyAlpha by animateFloatAsState(
        targetValue = if (busy) .56f else 1f,
        animationSpec = tween(DetailMotionFastMillis),
        label = "season-action-alpha",
    )
    val content: @Composable () -> Unit = {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                fadeIn(tween(DetailMotionFastMillis)) togetherWith
                    fadeOut(tween(DetailMotionExitMillis))
            },
            label = "season-watched-selection",
        ) { isSelected ->
            Image(
                painter = painterResource(MaFeiLegacyDrawables.watched),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                ),
            )
        }
    }
    if (onAction != null) {
        LiquidButton(
            onClick = { if (!busy) onAction() },
            backdrop = backdrop,
            isInteractive = !busy,
            height = 44.dp,
            horizontalPadding = 11.dp,
            modifier = Modifier.width(44.dp).alpha(busyAlpha).semantics {
                contentDescription = label
                if (busy) disabled()
            },
        ) { content() }
    } else {
        GlassSurface(
            backdrop = backdrop,
            spec = DetailActionSurfaceSpec,
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
    onSeasonSelected: (MediaItem) -> Unit,
    onEpisodeSelected: (MediaItem) -> Unit,
    onEpisodeOverview: (String) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)?,
    backdrop: GlassBackdrop,
) {
    var selectedGroup by remember(season.id.value) { mutableIntStateOf(0) }
    val showLoading = rememberDelayedDetailLoading(loading)
    val groups = remember(episodes) { episodes.chunked(30) }
    val groupLabels = remember(groups) {
        groups.mapIndexed { index, group ->
            val start = group.firstOrNull()?.episodeNumber ?: index * 30 + 1
            val end = group.lastOrNull()?.episodeNumber ?: start
            if (group.size == 1) "$start" else "$start - $end"
        }
    }
    val selectedEpisodes = groups.getOrNull(selectedGroup).orEmpty()
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
                "共${episodes.size.takeIf { it > 0 } ?: season.childCount ?: 0}集",
                color = LocalContentColor.current.copy(alpha = .68f),
                fontSize = 13.sp,
            )
        }
        if (seasons.size > 1) {
            Spacer(Modifier.height(12.dp))
            LiquidFloatingTabs(
                // ArkUI SeasonDetailViewModel reuses the mapped VideoItem
                // name here: numbered seasons are always rendered as
                // “第 N 季”, regardless of the server's raw title.
                labels = seasons.map { candidate ->
                    candidate.seasonNumber?.let { "第 $it 季" } ?: candidate.title
                },
                selectedIndex = seasons.indexOfFirst { it.id == season.id }.coerceAtLeast(0),
                onSelected = { index -> seasons.getOrNull(index)?.let(onSeasonSelected) },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        DetailAnimatedVisibility(
            visible = !loading && groupLabels.size > 1,
            enter = fadeIn(tween(DetailMotionEnterMillis)) +
                slideInVertically(tween(DetailMotionEnterMillis)) { it / 10 },
            exit = fadeOut(tween(DetailMotionExitMillis)),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                LiquidFloatingTabs(
                    labels = groupLabels,
                    selectedIndex = selectedGroup,
                    onSelected = { selectedGroup = it },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            DetailAnimatedVisibility(
                visible = showLoading,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(DetailMotionFastMillis)),
                exit = fadeOut(tween(DetailMotionExitMillis)),
            ) {
                SeasonEpisodeSkeletons()
            }
            DetailAnimatedVisibility(
                visible = !loading && groups.isNotEmpty(),
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(DetailMotionEnterMillis)) +
                    slideInVertically(tween(DetailMotionEnterMillis)) { it / 18 },
                exit = fadeOut(tween(DetailMotionExitMillis)),
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(season.id.value, selectedGroup) { listState.scrollToItem(0) }
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                ) {
                    items(selectedEpisodes, key = { it.id.value }) { episode ->
                        SeasonEpisodeCard(
                            item = episode,
                            artwork = artwork,
                            sharedArtworkModifier = sharedArtworkModifier,
                            onClick = { onEpisodeSelected(episode) },
                            onOverview = { overview -> onEpisodeOverview(overview) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonEpisodeSkeletons() {
    LazyRow(
        Modifier.fillMaxWidth().height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
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
    }
}

@Composable
private fun SeasonEpisodeCard(
    item: MediaItem,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)?,
    onClick: () -> Unit,
    onOverview: (String) -> Unit,
) {
    val cardBounds = Modifier.width(180.dp).height(204.dp)
    Column(
        (sharedArtworkModifier?.invoke(item, cardBounds) ?: cardBounds)
            .mafeiInteractive(onClick, radiusDp = 6f, label = item.title),
        horizontalAlignment = Alignment.Start,
    ) {
        val imageShape = RoundedCornerShape(6.dp)
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, imageShape)
                .clip(imageShape),
        ) {
            if (artwork != null) {
                val artworkModifier = Modifier.fillMaxSize()
                artwork(item, artworkModifier)
            }
            else LegacyPosterPlaceholder(item.mediaType, Modifier.fillMaxSize())
            episodeProgress(item)?.let { progress ->
                MaFeiProgressLine(
                    progress = progress,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    progressColor = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = .34f),
                )
            }
            LegacyMediaTagBadges(
                tags = episodeResolutionTags(item.tags),
                maxTags = null,
                filterOther = false,
                deduplicate = false,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
        }
        Text(
            legacyEpisodeCardTitle(item),
            Modifier.fillMaxWidth().padding(top = 8.dp),
            color = LocalContentColor.current,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            val trimmedOverview = overview.trim()
            val overviewPreview = if (trimmedOverview.length > EpisodeOverviewPreviewCharacters) {
                trimmedOverview.take(EpisodeOverviewPreviewCharacters).trimEnd() + " … "
            } else {
                "$trimmedOverview "
            }
            val preview = buildAnnotatedString {
                append(overviewPreview)
                withLink(LinkAnnotation.Clickable("episode-overview", linkInteractionListener = { onOverview(overview) })) {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("详情") }
                }
            }
            Text(
                text = preview,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                style = androidx.compose.ui.text.TextStyle(
                    color = LocalContentColor.current.copy(alpha = .68f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )
        }
        item.runtimeMs?.takeIf { it > 0L }?.let { runtime ->
            Text(
                legacyEpisodeDuration(runtime),
                Modifier.fillMaxWidth().padding(top = 4.dp),
                color = LocalContentColor.current.copy(alpha = .68f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

private const val EpisodeOverviewPreviewCharacters = 24

private fun episodeResolutionTags(tags: List<String>): List<String> = tags
    .map(String::trim)
    .filter(String::isNotEmpty)
    .filter { tag ->
        when (tag.uppercase()) {
            "8K", "4K", "2160P", "2160", "1440P", "1440",
            "1080P", "1080", "720P", "720", "480P", "480" -> true
            else -> false
        }
    }
    .distinctBy(String::uppercase)

private fun legacySeriesInfo(series: MediaItem, libraryName: String?): String = buildList {
    series.officialRating?.takeIf { it.isNotBlank() }?.let(::add)
    addAll(series.genres.filter { it.isNotBlank() })
    series.year?.let { add(it.toString()) }
    series.runtimeMs?.takeIf { it > 0L }?.let { add(legacyDetailDurationLabel(it)) }
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
