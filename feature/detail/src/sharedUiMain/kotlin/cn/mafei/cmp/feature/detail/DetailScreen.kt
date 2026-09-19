package cn.mafei.cmp.feature.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiShape
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.ui.LocalMaFeiNavigationChrome
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.components.LiquidFloatingTabs
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.InlineProblem
import cn.mafei.cmp.common.ui.LegacyTransientToast
import cn.mafei.cmp.common.ui.LegacyVideoCard
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.TextPoster
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaSourceInfo
import cn.mafei.cmp.core.domain.MediaStreamInfo
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.PersonKind
import cn.mafei.cmp.core.domain.episodeLabel
import cn.mafei.cmp.core.domain.episodeSubtitle
import cn.mafei.cmp.core.domain.episodeLabel
import cn.mafei.cmp.core.domain.episodeSubtitle
import cn.mafei.cmp.core.domain.PersonRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compose translation of the legacy HarmonyOS VideoDetailPage.
 *
 * The legacy screen is the specification: a fixed blurred artwork atmosphere,
 * a 430vp feathered hero, compact playback actions, three-line overview sheet,
 * three-column seasons and the original cast rhythm. Wide windows retain the
 * same 720vp reading column instead of inventing a second desktop layout.
 */
@Composable
fun DetailScreen(
    item: MediaItem?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (() -> Unit)? = null,
    playTarget: MediaItem? = item,
    playbackBusy: Boolean = false,
    playbackError: String? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onTogglePlayed: (() -> Unit)? = null,
    actionsBusy: Boolean = false,
    actionsError: String? = null,
    seasons: List<MediaItem> = emptyList(),
    sectionsLoading: Boolean = false,
    sectionsError: String? = null,
    onSeasonSelected: (MediaItem) -> Unit = {},
    similar: List<MediaItem> = emptyList(),
    similarLoading: Boolean = false,
    onSimilarItemSelected: (MediaItem) -> Unit = {},
    onOpenExternalUrl: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    selectedMediaSourceId: String? = null,
    onSelectMediaSource: ((String?) -> Unit)? = null,
    selectedAudioStreamIndex: Int? = null,
    onSelectAudioTrack: ((Int?) -> Unit)? = null,
    selectedSubtitleStreamIndex: Int? = null,
    onSelectSubtitleTrack: ((Int?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
    libraryName: String? = null,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
    backdropArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = artwork,
    logoArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
    personArtwork: (@Composable (PersonRef, Modifier) -> Unit)? = null,
    onPersonSelected: (PersonRef) -> Unit = {},
    atmosphereArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = backdropArtwork,
) {
    val backdrop = rememberGlassBackdrop()
    val actionBarBackdrop = rememberGlassBackdrop()
    val scrollState = rememberScrollState()
    val heroStretch = rememberLegacyHeroStretch(scrollState)
    val density = LocalDensity.current
    val startPx = remember(density) { with(density) { 72.dp.toPx() } }
    val endPx = remember(density) { with(density) { 290.dp.toPx() } }
    val collapseProgress by remember(scrollState, startPx, endPx) {
        derivedStateOf {
            ((scrollState.value - startPx) / (endPx - startPx)).coerceIn(0f, 1f)
        }
    }
    val collapsedTitleOpacity = when {
        collapseProgress <= .72f -> 0f
        else -> ((collapseProgress - .72f) / .22f).coerceIn(0f, 1f)
    }
    var overviewSheetVisible by remember(item?.id?.value) { mutableStateOf(false) }
    LaunchedEffect(item?.id?.value) {
        if (item != null) scrollState.scrollTo(0)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // The atmosphere mirrors the home ambient: it also runs under the floating
        // sidebar, so navigation samples the same blurred artwork instead of the
        // app-level canvas and the whole-window background stays continuous.
        val chrome = LocalMaFeiNavigationChrome.current
        val effectiveLayout = chrome.effectiveLayout
        val atmosphereBleed = if (effectiveLayout?.navigation == NavigationPlacement.Sidebar) {
            (effectiveLayout?.navigationWidthDp ?: 0f).dp
        } else {
            0.dp
        }
        val atmosphereWidth = maxWidth + atmosphereBleed
        // Home and this page share one content column on wide chrome: the layout's
        // grid width, centred in the pane. Phone keeps the legacy 720dp cap, which
        // resolves to the full pane width and must not gain extra gutters.
        val wideChrome = effectiveLayout?.navigation != null &&
            effectiveLayout.navigation != NavigationPlacement.Bottom
        val detailContentMaxWidth = if (wideChrome) {
            effectiveLayout.gridWidthDp.dp
        } else {
            720.dp
        }
        Box(Modifier.fillMaxSize().captureGlassBackdrop(actionBarBackdrop)) {
            Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
                DetailAtmosphere(item, atmosphereArtwork, atmosphereBleed, atmosphereWidth)
            }
            if (item != null) {
                DetailBody(
                    item = item,
                    scrollState = scrollState,
                    collapseProgress = collapseProgress,
                    loading = loading,
                    error = error,
                    stale = stale,
                    onRetry = onRetry,
                    onPlay = onPlay,
                    playTarget = playTarget,
                    playbackBusy = playbackBusy,
                    onToggleFavorite = onToggleFavorite,
                    onTogglePlayed = onTogglePlayed,
                    actionsBusy = actionsBusy,
                    seasons = seasons,
                    sectionsLoading = sectionsLoading,
                    sectionsError = sectionsError,
                    onSeasonSelected = onSeasonSelected,
                    similar = similar,
                    similarLoading = similarLoading,
                    onSimilarItemSelected = onSimilarItemSelected,
                    onOpenExternalUrl = onOpenExternalUrl,
                    onDelete = onDelete,
                    selectedMediaSourceId = selectedMediaSourceId,
                    onSelectMediaSource = onSelectMediaSource,
                    selectedAudioStreamIndex = selectedAudioStreamIndex,
                    onSelectAudioTrack = onSelectAudioTrack,
                    selectedSubtitleStreamIndex = selectedSubtitleStreamIndex,
                    onSelectSubtitleTrack = onSelectSubtitleTrack,
                    libraryName = libraryName,
                    contentMaxWidth = detailContentMaxWidth,
                    artwork = artwork,
                    backdropArtwork = backdropArtwork,
                    logoArtwork = logoArtwork,
                    personArtwork = personArtwork,
                    onPersonSelected = onPersonSelected,
                    onOpenOverview = { overviewSheetVisible = true },
                    backdrop = backdrop,
                    heroExtraHeight = heroStretch.extraHeight,
                    heroScrollConnection = heroStretch.connection,
                )
            } else {
                DetailState(loading, error, onRetry)
            }
        }

        MaFeiActionBar(
            title = item?.title.orEmpty(),
            onBack = onBack,
            backdrop = actionBarBackdrop,
            materialAlpha = if (item == null) 1f else collapseProgress,
            titleAlpha = if (item == null) 0f else collapsedTitleOpacity,
            contentColor = if (item == null) MaterialTheme.colorScheme.onSurface else Color.White,
            // Keep the back button on the content column's leading edge instead of
            // stranding it at the far edge of a wide window.
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = detailContentMaxWidth)
                .fillMaxWidth(),
        )
        LegacyTransientToast(
            message = actionsError ?: playbackError,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (overviewSheetVisible) {
            OverviewSheetOverlay(
                overview = item?.overview,
                backdrop = actionBarBackdrop,
                onDismiss = { overviewSheetVisible = false },
            )
        }
    }
}

@Composable
private fun DetailAtmosphere(
    item: MediaItem?,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    bleed: Dp = 0.dp,
    expandedWidth: Dp? = null,
) {
    val atmosphereModifier = Modifier
        .offset(x = -bleed)
        .then(if (expandedWidth != null) Modifier.requiredWidth(expandedWidth) else Modifier.fillMaxWidth())
        .fillMaxHeight()
        // requiredWidth, not width: the incoming constraint is the content pane, so
        // a plain width() would clamp the bleed and re-open the strip the home
        // ambient already fixed. Clip so the 1.18-scaled artwork cannot spill.
        .clipToBounds()
    Box(atmosphereModifier.background(MaterialTheme.colorScheme.background)) {
        if (item != null && artwork != null) {
            val darkMode = isSystemInDarkTheme()
            artwork(
                item,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                        alpha = .95f
                        // Legacy native detail darkens the atmosphere before text
                        // is drawn. Keeping this independent of app theme lets the
                        // media page use one reliable white ink hierarchy.
                        val brightness = if (darkMode) .58f else .72f
                        colorFilter = ColorFilter.colorMatrix(
                            ColorMatrix().apply { setToScale(brightness, brightness, brightness, 1f) },
                        )
                    }
                        .blur(DetailAtmosphereBlurRadius),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to DetailAtmosphereInk.copy(alpha = .19f),
                    .56f to DetailAtmosphereInk.copy(alpha = .34f),
                    1f to DetailAtmosphereInk.copy(alpha = .41f),
                ),
            ),
        )
    }
}

@Composable
private fun BoxScope.DetailBody(
    item: MediaItem,
    scrollState: androidx.compose.foundation.ScrollState,
    collapseProgress: Float,
    loading: Boolean,
    error: String?,
    stale: Boolean,
    onRetry: () -> Unit,
    onPlay: (() -> Unit)?,
    playTarget: MediaItem?,
    playbackBusy: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onTogglePlayed: (() -> Unit)?,
    actionsBusy: Boolean,
    seasons: List<MediaItem>,
    sectionsLoading: Boolean,
    sectionsError: String?,
    onSeasonSelected: (MediaItem) -> Unit,
    similar: List<MediaItem>,
    similarLoading: Boolean,
    onSimilarItemSelected: (MediaItem) -> Unit,
    onOpenExternalUrl: ((String) -> Unit)?,
    onDelete: (() -> Unit)?,
    selectedMediaSourceId: String?,
    onSelectMediaSource: ((String?) -> Unit)?,
    selectedAudioStreamIndex: Int?,
    onSelectAudioTrack: ((Int?) -> Unit)?,
    selectedSubtitleStreamIndex: Int?,
    onSelectSubtitleTrack: ((Int?) -> Unit)?,
    libraryName: String?,
    contentMaxWidth: Dp,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    backdropArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    logoArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    personArtwork: (@Composable (PersonRef, Modifier) -> Unit)?,
    onPersonSelected: (PersonRef) -> Unit,
    onOpenOverview: () -> Unit,
    backdrop: GlassBackdrop,
    heroExtraHeight: Dp,
    heroScrollConnection: NestedScrollConnection,
) {
    val safeArea = LocalMaFeiSafeArea.current
    // The full-page atmosphere is always media-first and darkened. Downstream
    // text therefore shares one white ink hierarchy instead of switching with
    // the app theme while it still sits on cover art.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                // Match the home page's content column. A fixed phone-width column
                // left ~100dp of dead space on each side of a desktop window.
                .widthIn(max = contentMaxWidth)
                .fillMaxWidth()
                .nestedScroll(heroScrollConnection)
                .verticalScroll(scrollState),
        ) {
            DetailHero(
                item = item,
                height = 430.dp + heroExtraHeight,
                collapseProgress = collapseProgress,
                libraryName = libraryName,
                artwork = backdropArtwork ?: artwork,
                logoArtwork = logoArtwork,
                )

            Column(
                Modifier.fillMaxWidth().padding(bottom = safeArea.bottom + 30.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                DetailActions(
                    item = item,
                    playTarget = playTarget,
                    onPlay = onPlay,
                    playbackBusy = playbackBusy,
                    onToggleFavorite = onToggleFavorite,
                    onTogglePlayed = onTogglePlayed,
                    actionsBusy = actionsBusy,
                    backdrop = backdrop,
                )
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                }
                if (item.overview?.isNotBlank() == true) {
                    Spacer(Modifier.height(28.dp))
                    OverviewSection(
                        overview = item.overview,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onOpen = onOpenOverview,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (item.mediaType == MediaType.Series) {
                    SeriesSections(
                        seasons = seasons,
                        loading = sectionsLoading,
                        error = sectionsError,
                        artwork = artwork,
                        onSeasonSelected = onSeasonSelected,
                    )
                }
                if (item.people.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    LegacyPeopleSection(item.people, personArtwork, onPersonSelected)
                }
                PlaybackSelectionSection(
                    sources = item.mediaSources,
                    selectedMediaSourceId = selectedMediaSourceId,
                    onSelectMediaSource = onSelectMediaSource,
                    selectedAudioStreamIndex = selectedAudioStreamIndex,
                    onSelectAudioTrack = onSelectAudioTrack,
                    selectedSubtitleStreamIndex = selectedSubtitleStreamIndex,
                    onSelectSubtitleTrack = onSelectSubtitleTrack,
                    backdrop = backdrop,
                )
                if (item.mediaSources.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    MediaInfoSection(
                        sources = item.mediaSources,
                        onDelete = onDelete,
                    )
                }
                if (onOpenExternalUrl != null && item.externalUrls.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    ExternalLinksSection(item.externalUrls, onOpenExternalUrl, backdrop)
                }
                if (similar.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SimilarSection(
                        items = similar,
                        loading = similarLoading,
                        onItemSelected = onSimilarItemSelected,
                        artwork = artwork,
                    )
                } else if (similarLoading) {
                    Spacer(Modifier.height(20.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                }
                if (item.overview.isNullOrBlank() && item.mediaType != MediaType.Series && item.people.isEmpty() &&
                    error != null && !stale
                ) {
                    InlineProblem(error, onRetry)
                }
            }
        }
    }
}

@Composable
private fun DetailHero(
    item: MediaItem,
    height: Dp,
    collapseProgress: Float,
    libraryName: String?,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    logoArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
) {
    val titleOpacity = when {
        collapseProgress <= .72f -> 1f
        else -> (1f - (collapseProgress - .72f) / .22f).coerceIn(0f, 1f)
    }
    val titlePhase = smoothStep(((collapseProgress - .42f) / .58f).coerceIn(0f, 1f))
    val hasLogo = item.images.any { it.kind == ImageKind.Logo }
    val collapsedScale = if (hasLogo) .28f else .46f
    val titleScale = 1f - (1f - collapsedScale) * titlePhase
    // 与手机同一套规格：hero 是 edge-to-edge 的 band，底部羽化融入氛围背景。
    // 之前宽屏把 hero 做成圆角卡片（"桌面化第二套布局"），既失去沉浸感，也让
    // 宽屏/窄屏是两个页面。现在只有一种详情页。
    val roundedHero = false

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = if (roundedHero) 12.dp else 0.dp)
            .height(height)
            .clip(if (roundedHero) RoundedCornerShape(26.dp) else RoundedCornerShape(0.dp)),
    ) {
        // A rounded card keeps a crisp bottom edge so the card reads as a single
        // surface over the atmosphere; the phone band still feathers into the
        // canvas the way the legacy page did.
        val artworkModifier = if (roundedHero) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxSize().featherBottom(.64f)
        }
        if (artwork != null) {
            artwork(item, artworkModifier)
        } else {
            TextPoster(item.title, artworkModifier)
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

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            if (hasLogo && logoArtwork != null) {
                Box(
                    Modifier
                        .width(196.dp)
                        .height(76.dp)
                        .graphicsLayer {
                            scaleX = titleScale
                            scaleY = titleScale
                            alpha = titleOpacity
                            translationY = -8.dp.toPx() - 8.dp.toPx() * titlePhase
                        },
                ) {
                    logoArtwork(item, Modifier.fillMaxSize())
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = titleScale
                            scaleY = titleScale
                            alpha = titleOpacity
                            translationY = -16.dp.toPx() * titlePhase
                        },
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    detailSubtitle(item).takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(
                            subtitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        item.title,
                        Modifier.fillMaxWidth().semantics { heading() },
                        color = Color.White,
                        fontSize = 38.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.displaySmall.copy(
                            shadow = Shadow(Color.Black.copy(alpha = .82f), Offset(0f, 3f), 10f),
                        ),
                    )
                }
            }

            DetailMetadata(item, libraryName, Color.White)
        }
    }
}

private fun Modifier.featherBottom(startFraction: Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White,
                    startFraction.coerceIn(0f, .98f) to Color.White,
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

@Composable
private fun DetailMetadata(item: MediaItem, libraryName: String?, color: Color) {
    val primaryInfo = buildList {
        item.officialRating?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(item.genres.filter { it.isNotBlank() })
    }.joinToString(" · ")
    val secondaryInfo = buildList {
        item.year?.let { add(it.toString()) }
        item.runtimeMs?.takeIf { it > 0L }?.let { add(durationLabel(it)) }
        libraryName?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")

    if ((item.communityRating ?: 0.0) > 0.0 || primaryInfo.isNotBlank()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                buildAnnotatedString {
                    item.communityRating?.takeIf { it > 0.0 }?.let { rating ->
                        withStyle(SpanStyle(color = ScoreColor)) { append("${formatRating(rating)}分") }
                    }
                    if ((item.communityRating ?: 0.0) > 0.0 && primaryInfo.isNotBlank()) append(" · ")
                    append(primaryInfo)
                },
                color = color,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
            )
        }
    }
    if (secondaryInfo.isNotBlank()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                secondaryInfo,
                color = color,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DetailActions(
    item: MediaItem,
    playTarget: MediaItem?,
    onPlay: (() -> Unit)?,
    playbackBusy: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onTogglePlayed: (() -> Unit)?,
    actionsBusy: Boolean,
    backdrop: GlassBackdrop,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailPlayAction(
            item = item,
            target = playTarget,
            onPlay = onPlay,
            busy = playbackBusy,
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
        )
        DetailIconAction(
            icon = if (item.userData.isFavorite) {
                MaFeiLegacyDrawables.favoriteSelected
            } else {
                MaFeiLegacyDrawables.favoriteUnselected
            },
            selected = item.userData.isFavorite,
            selectedColor = FavoriteColor,
            preserveOriginalColor = item.userData.isFavorite,
            accessibilityLabel = if (item.userData.isFavorite) "取消收藏" else "收藏",
            onAction = onToggleFavorite?.takeUnless { actionsBusy },
            backdrop = backdrop,
        )
        DetailIconAction(
            icon = MaFeiLegacyDrawables.watched,
            selected = item.userData.isPlayed,
            selectedColor = MaterialTheme.colorScheme.primary,
            accessibilityLabel = if (item.userData.isPlayed) "标记为未播放" else "标记为已播放",
            onAction = onTogglePlayed?.takeUnless { actionsBusy },
            backdrop = backdrop,
        )
    }
}

@Composable
private fun RowScope.DetailPlayAction(
    item: MediaItem,
    target: MediaItem?,
    onPlay: (() -> Unit)?,
    busy: Boolean,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    val progress = target?.playbackProgress() ?: item.playbackProgress()
    val remaining = target?.remainingTime().orEmpty()
    val label = detailPlayLabel(item, target)
    Box(modifier.height(50.dp).clip(MaFeiShape.capsule)) {
        if (progress != null && progress > 0f && progress < 1f && remaining.isNotBlank()) {
            Box(
                Modifier.fillMaxWidth(progress).fillMaxHeight()
                    .background(Color.White.copy(alpha = .26f)),
            )
        }
        if (onPlay != null) {
            LiquidButton(
                onClick = { if (!busy) onPlay() },
                backdrop = backdrop,
                height = 50.dp,
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = if (remaining.isBlank()) label else "$label $remaining"
                },
            ) {
                DetailPlayContents(label, remaining, busy)
            }
        } else {
            GlassSurface(
                backdrop = backdrop,
                spec = DetailActionSurfaceSpec,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = label
                    disabled()
                },
            ) {
                Row(
                    Modifier.fillMaxSize().alpha(.48f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailPlayContents(label, remaining, busy = false)
                }
            }
        }
    }
}

@Composable
private fun DetailPlayContents(label: String, remaining: String, busy: Boolean) {
    if (busy) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
    } else {
        Image(
            painter = painterResource(MaFeiLegacyDrawables.playRound),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            label,
            color = Color.White,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (remaining.isNotBlank()) {
            Text(
                compactRemainingTime(remaining),
                color = Color.White.copy(alpha = .78f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DetailIconAction(
    icon: DrawableResource,
    selected: Boolean,
    selectedColor: Color,
    preserveOriginalColor: Boolean = false,
    accessibilityLabel: String,
    onAction: (() -> Unit)?,
    backdrop: GlassBackdrop,
) {
    val content: @Composable RowScope.() -> Unit = {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = if (selected && preserveOriginalColor) null else ColorFilter.tint(
                if (selected) selectedColor else Color.White,
            ),
        )
    }
    if (onAction != null) {
        LiquidButton(
            onClick = onAction,
            backdrop = backdrop,
            height = 44.dp,
            horizontalPadding = 11.dp,
            modifier = Modifier.width(44.dp).semantics { contentDescription = accessibilityLabel },
            content = content,
        )
    } else {
        GlassSurface(
            backdrop = backdrop,
            spec = DetailActionSurfaceSpec,
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(44.dp).alpha(.48f).semantics {
                contentDescription = accessibilityLabel
                disabled()
            },
        ) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
internal fun OverviewSection(
    overview: String?,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val text = overview?.takeIf { it.isNotBlank() } ?: return
    val textColor = LocalContentColor.current.copy(alpha = .86f)
    val moreColor = LocalContentColor.current.copy(alpha = .78f)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = textColor,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val preview = remember(text, maxWidthPx, textStyle, moreColor) {
            legacyOverviewPreview(
                text = text,
                maxWidthPx = maxWidthPx,
                textMeasurer = textMeasurer,
                style = textStyle,
                moreColor = moreColor,
            )
        }
        ClickableText(
            text = preview,
            modifier = Modifier.fillMaxWidth(),
            style = textStyle,
            maxLines = LegacyOverviewMaxLines,
            overflow = TextOverflow.Ellipsis,
            onClick = { offset ->
                if (preview.getStringAnnotations(LegacyOverviewMoreTag, offset, offset).isNotEmpty()) {
                    onOpen()
                }
            },
        )
    }
}

/** Same-layer bottom sheet so the page backdrop remains the live glass source. */
@Composable
internal fun BoxScope.OverviewSheetOverlay(
    overview: String?,
    backdrop: GlassBackdrop,
    onDismiss: () -> Unit,
) {
    val text = overview?.takeIf { it.isNotBlank() } ?: return
    val safeArea = LocalMaFeiSafeArea.current
    val dismissInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = .28f))
            .clickable(
                interactionSource = dismissInteraction,
                indication = null,
                onClick = onDismiss,
            ),
    )
    GlassSurface(
        backdrop = backdrop,
        spec = OverviewSheetSurfaceSpec,
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().height(420.dp)
            .clickable(
                interactionSource = sheetInteraction,
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = safeArea.bottom + 10.dp,
            ),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .36f)),
            )
            Text(
                "简介",
                Modifier.padding(top = 14.dp, bottom = 16.dp).semantics { heading() },
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text,
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun SeriesSections(
    seasons: List<MediaItem>,
    loading: Boolean,
    error: String?,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    onSeasonSelected: (MediaItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (seasons.isNotEmpty()) {
            if (seasons.size > 1) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "共${seasons.size}季",
                    Modifier.padding(horizontal = 16.dp).semantics { heading() },
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                seasons.chunked(3).forEach { rowSeasons ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowSeasons.forEach { season ->
                            SeasonCard(
                                season = season,
                                artwork = artwork,
                                onClick = { onSeasonSelected(season) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowSeasons.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }
        if (seasons.isEmpty() && error != null) {
            Text(
                error,
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun RowScope.SeasonCard(
    season: MediaItem,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LegacyVideoCard(
        item = season,
        title = season.seasonNumber?.let { "第 $it 季" } ?: season.title,
        subtitle = season.year?.toString().orEmpty(),
        textAlign = TextAlign.Center,
        onClick = onClick,
        modifier = modifier,
        poster = artwork?.let { draw -> { cardModifier -> draw(season, cardModifier) } },
    )
}

/**
 * 播放选择区（legacy MediaSourceSelector + AudioSubtitleSelector 的页面内化）：
 * 多版本时先选版本（选择持久化为「最近一次选择」，下次详情页同名源自动预选），
 * 再按需挑音轨/字幕轨；字幕「关闭」传 -1，「自动」交回服务器协商。区块只在
 * 真的有多可选内容时出现。
 */
@Composable
private fun PlaybackSelectionSection(
    sources: List<MediaSourceInfo>,
    selectedMediaSourceId: String?,
    onSelectMediaSource: ((String?) -> Unit)?,
    selectedAudioStreamIndex: Int?,
    onSelectAudioTrack: ((Int?) -> Unit)?,
    selectedSubtitleStreamIndex: Int?,
    onSelectSubtitleTrack: ((Int?) -> Unit)?,
    backdrop: GlassBackdrop,
) {
    val primary = sources.firstOrNull { it.id == selectedMediaSourceId } ?: sources.firstOrNull() ?: return
    val multiSource = sources.size > 1 && onSelectMediaSource != null
    val audioTracks = primary.audioStreams.filter { it.index != null }
    val subtitleTracks = primary.subtitleStreams.filter { it.index != null && it.isExternal }
    val multiAudio = audioTracks.size > 1 && onSelectAudioTrack != null
    val multiSubtitle = subtitleTracks.isNotEmpty() && onSelectSubtitleTrack != null
    if (!multiSource && !multiAudio && !multiSubtitle) return

    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            "播放选择",
            Modifier.padding(horizontal = 16.dp).semantics { heading() },
            color = LocalContentColor.current,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (multiSource) {
                SelectionChips(
                    labels = listOf(AutoChoiceLabel) + sources.map { sourceLabel(it) },
                    selectedIndex = sources.indexOfFirst { it.id == selectedMediaSourceId } + 1,
                    onSelect = { index ->
                        onSelectMediaSource?.invoke(if (index == 0) null else sources.getOrNull(index - 1)?.id)
                    },
                    backdrop = backdrop,
                )
            }
            if (multiAudio) {
                SelectionChips(
                    labels = listOf(AutoChoiceLabel) + audioTracks.map { trackLabel(it) },
                    selectedIndex = audioTracks.indexOfFirst { it.index == selectedAudioStreamIndex } + 1,
                    onSelect = { index ->
                        onSelectAudioTrack?.invoke(if (index == 0) null else audioTracks.getOrNull(index - 1)?.index)
                    },
                    backdrop = backdrop,
                )
            }
            if (multiSubtitle) {
                SelectionChips(
                    labels = listOf(SubtitleOffLabel, AutoChoiceLabel) + subtitleTracks.map { trackLabel(it) },
                    selectedIndex = when (selectedSubtitleStreamIndex) {
                        null -> 1
                        -1 -> 0
                        else -> subtitleTracks.indexOfFirst { it.index == selectedSubtitleStreamIndex } + 2
                    },
                    onSelect = { index ->
                        onSelectSubtitleTrack?.invoke(
                            when {
                                index == 0 -> -1
                                index == 1 -> null
                                else -> subtitleTracks.getOrNull(index - 2)?.index
                            },
                        )
                    },
                    backdrop = backdrop,
                )
            }
        }
    }
}

/** 一排单选胶囊：与季切换同款 LiquidFloatingTabs，选中态有液态滑动。 */
@Composable
private fun SelectionChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: GlassBackdrop,
) {
    LiquidFloatingTabs(
        labels = labels,
        selectedIndex = selectedIndex.coerceIn(0, labels.size - 1),
        onSelected = onSelect,
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val AutoChoiceLabel = "自动"
private const val SubtitleOffLabel = "关闭字幕"

private fun sourceLabel(source: MediaSourceInfo): String = buildList {
    add(source.name?.takeIf { it.isNotBlank() } ?: source.container?.uppercase() ?: "版本")
    source.sizeBytes?.takeIf { it > 0L }?.let { add(formatFileSize(it)) }
    source.videoStream?.resolutionLabel?.let(::add)
}.joinToString(" · ")

private fun trackLabel(stream: MediaStreamInfo): String = buildList {
    add(stream.title?.takeIf { it.isNotBlank() } ?: stream.language?.uppercase() ?: "轨道 ${stream.index ?: 0}")
    stream.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
}.joinToString(" · ")

/**
 * Legacy ArkUI 「文件媒体信息」 block: every playable file with its container,
 * size, duration and its video/audio/subtitle streams. The mapping already
 * carries the data, so the block renders whenever the provider reports sources
 * and stays absent for providers that do not. [onDelete]（管理员）在标题行挂
 * 一个删除入口，确认弹窗由外层处理。
 */
@Composable
internal fun MediaInfoSection(
    sources: List<MediaSourceInfo>,
    onDelete: (() -> Unit)? = null,
) {
    if (sources.isEmpty()) return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "文件媒体信息",
                Modifier.weight(1f).semantics { heading() },
                color = LocalContentColor.current,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
            )
            if (onDelete != null) {
                Text(
                    "删除",
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color(0xFFFF6B5E),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            sources.forEach { source -> MediaSourceBlock(source) }
        }
    }
}

/** 外部链接（IMDb/TMDB/...），与旧版详情页同位；点击交给平台浏览器。 */
@Composable
private fun ExternalLinksSection(
    links: List<cn.mafei.cmp.core.domain.MediaExternalUrl>,
    onOpenUrl: (String) -> Unit,
    backdrop: GlassBackdrop,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "外部链接",
            Modifier.padding(horizontal = 16.dp).semantics { heading() },
            color = LocalContentColor.current,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
        )
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.width(8.dp)) }
            items(links, key = { it.url }) { link ->
                GlassSurface(
                    backdrop = backdrop,
                    spec = DetailActionSurfaceSpec,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                ) {
                    Text(
                        link.name,
                        Modifier
                            .clickable(onClick = { onOpenUrl(link.url) })
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
            }
            item { Spacer(Modifier.width(8.dp)) }
        }
    }
}

/** 「相似推荐」横滑（legacy Similar 区块）。 */
@Composable
private fun SimilarSection(
    items: List<MediaItem>,
    loading: Boolean,
    onItemSelected: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
) {
    if (items.isEmpty() && !loading) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            "相似推荐",
            Modifier.padding(horizontal = 16.dp).semantics { heading() },
            color = LocalContentColor.current,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
        )
        LazyRow(
            Modifier.fillMaxWidth().height(194.dp).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.width(8.dp)) }
            items(items, key = { it.id.value }) { item ->
                LegacyVideoCard(
                    item = item,
                    onClick = { onItemSelected(item) },
                    modifier = Modifier.width(100.dp),
                    poster = artwork?.let { draw -> { imageModifier -> draw(item, imageModifier) } },
                )
            }
            item { Spacer(Modifier.width(8.dp)) }
        }
    }
}

@Composable
private fun MediaSourceBlock(source: MediaSourceInfo) {
    val summary = buildList {
        source.container?.takeIf { it.isNotBlank() }?.let { add("容器 ${it.uppercase()}") }
        source.sizeBytes?.takeIf { it > 0L }?.let { add("大小 ${formatFileSize(it)}") }
        source.runTimeMs?.takeIf { it > 0L }?.let { add("时长 ${durationLabel(it)}") }
        if (source.isRemote) add("网络源")
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            source.name ?: source.path ?: "媒体文件",
            color = LocalContentColor.current,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        source.path?.takeIf { it.isNotBlank() && it != source.name }?.let { path ->
            Text(
                path,
                color = Color.White.copy(alpha = .52f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (summary.isNotEmpty()) {
            Text(
                summary.joinToString(" · "),
                color = Color.White.copy(alpha = .68f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        source.videoStream?.let { stream -> MediaInfoLine("视频", videoStreamSummary(stream)) }
        if (source.audioStreams.isNotEmpty()) {
            MediaInfoLine("音频", source.audioStreams.joinToString("\n", transform = ::audioStreamSummary))
        }
        MediaInfoLine(
            "字幕",
            source.subtitleStreams
                .takeIf { it.isNotEmpty() }
                ?.joinToString("\n", transform = ::subtitleStreamSummary)
                ?: "无字幕",
        )
    }
}

@Composable
private fun MediaInfoLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            Modifier.width(32.dp),
            color = Color.White.copy(alpha = .60f),
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Text(
            value,
            Modifier.weight(1f),
            color = Color.White.copy(alpha = .88f),
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

private fun videoStreamSummary(stream: MediaStreamInfo): String = buildList {
    stream.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    stream.resolutionLabel?.let { add(it) }
    stream.bitRate?.takeIf { it > 0 }?.let { add("${it / 1000} kbps") }
    stream.language?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    if (stream.isDefault) add("默认")
}.joinToString(" · ").ifBlank { "—" }

private fun audioStreamSummary(stream: MediaStreamInfo): String = buildList {
    stream.title?.takeIf { it.isNotBlank() }?.let { add(it) }
    stream.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    (stream.channelLayout ?: stream.channels?.let { "$it 声道" })?.let { add(it) }
    stream.language?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    if (stream.isDefault) add("默认")
}.joinToString(" · ").ifBlank { "—" }

private fun subtitleStreamSummary(stream: MediaStreamInfo): String = buildList {
    stream.title?.takeIf { it.isNotBlank() }?.let { add(it) }
    stream.language?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    stream.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    add(if (stream.isExternal) "外挂" else "内嵌")
    if (stream.isForced) add("强制")
    if (stream.isDefault) add("默认")
}.joinToString(" · ")

private fun formatFileSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    val rounded = (value * 10).toLong() / 10.0
    return if (unit == 0) "$bytes ${units[0]}" else "$rounded ${units[unit]}"
}

@Composable
internal fun LegacyPeopleSection(
    people: List<PersonRef>,
    artwork: (@Composable (PersonRef, Modifier) -> Unit)?,
    onPersonSelected: (PersonRef) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            "演职人员",
            Modifier.padding(start = 16.dp).semantics { heading() },
            color = LocalContentColor.current,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
        )
        LazyRow(
            Modifier.fillMaxWidth().height(130.dp).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.width(8.dp)) }
            items(
                people,
                key = { it.id?.value ?: "${it.name}:${it.role.orEmpty()}:${it.personType}" },
            ) { person ->
                Column(
                    Modifier.width(66.dp).mafeiInteractive(
                        onClick = { onPersonSelected(person) },
                        enabled = person.id != null,
                        radiusDp = 33f,
                        label = "查看${person.name}",
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(66.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (artwork != null && person.image != null) {
                            artwork(person, Modifier.fillMaxSize())
                        } else {
                            MaFeiIcon(
                                glyph = MaFeiGlyph.PersonFilled,
                                color = LocalContentColor.current.copy(alpha = .68f),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                    Text(
                        person.name,
                        Modifier.width(66.dp),
                        color = LocalContentColor.current,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        person.legacyRoleLabel(),
                        Modifier.width(66.dp),
                        color = LocalContentColor.current.copy(alpha = .68f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item { Spacer(Modifier.width(8.dp)) }
        }
    }
}

internal fun episodeLabel(item: MediaItem): String = buildString {
    val season = item.seasonNumber
    val episode = item.episodeNumber
    if (season != null && episode != null) append("S${season}:E${episode} · ")
    append(item.title)
}

internal fun episodeProgress(item: MediaItem): Float? =
    item.playbackProgress()?.takeIf { it > 0f && it < 1f }

private const val LegacyOverviewMaxLines = 3
private const val LegacyOverviewMoreTag = "legacy-overview-more"

private val OverviewSheetSurfaceSpec = GlassSurfaceSpec(
    role = GlassRole.Dialog,
    level = GlassLevel.Full,
    tintAlpha = .76f,
    intensity = 1f,
    cornerRadiusDp = 24f,
)

private val DetailAtmosphereBlurRadius = 48.dp

private fun legacyOverviewPreview(
    text: String,
    maxWidthPx: Int,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    moreColor: Color,
): AnnotatedString {
    if (maxWidthPx <= 0) return AnnotatedString(text)

    fun fits(prefixLength: Int): Boolean {
        val candidate = if (prefixLength >= text.length) text else {
            text.substring(0, prefixLength) + "... 更多"
        }
        return !textMeasurer.measure(
            text = AnnotatedString(candidate),
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = true,
            maxLines = LegacyOverviewMaxLines,
            constraints = Constraints(maxWidth = maxWidthPx),
        ).hasVisualOverflow
    }

    if (fits(text.length)) return AnnotatedString(text)

    var left = 0
    var right = text.length
    while (left < right) {
        val middle = (left + right + 1) / 2
        if (fits(middle)) left = middle else right = middle - 1
    }
    val prefixEnd = left.coerceIn(0, text.length)
    return buildAnnotatedString {
        append(text.substring(0, prefixEnd))
        append("... ")
        pushStringAnnotation(LegacyOverviewMoreTag, "more")
        withStyle(SpanStyle(color = moreColor)) { append("更多") }
        pop()
    }
}

internal class LegacyHeroStretchState(
    val extraHeight: Dp,
    val connection: NestedScrollConnection,
)

@Composable
internal fun rememberLegacyHeroStretch(scrollState: ScrollState): LegacyHeroStretchState {
    val density = LocalDensity.current
    val stretchPx = remember { mutableFloatStateOf(0f) }
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val connection = remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f || stretchPx.floatValue <= 0f) {
                    return Offset.Zero
                }
                settleJob.value?.cancel()
                val previous = stretchPx.floatValue
                val next = (previous + available.y / 3f).coerceAtLeast(0f)
                stretchPx.floatValue = next
                return Offset(0f, (next - previous) * 3f)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (
                    source != NestedScrollSource.UserInput || available.y <= 0f ||
                    scrollState.value != 0
                ) return Offset.Zero
                settleJob.value?.cancel()
                stretchPx.floatValue += available.y / 3f
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val start = stretchPx.floatValue
                if (start <= 0f) return Velocity.Zero
                settleJob.value?.cancel()
                settleJob.value = scope.launch {
                    Animatable(start).animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = .88f, stiffness = 288f),
                    ) {
                        stretchPx.floatValue = value
                    }
                }
                return Velocity(0f, available.y)
            }
        }
    }
    return LegacyHeroStretchState(
        extraHeight = with(density) { stretchPx.floatValue.toDp() },
        connection = connection,
    )
}

@Composable
private fun DetailState(loading: Boolean, error: String?, onRetry: () -> Unit) {
    val safeArea = LocalMaFeiSafeArea.current
    when {
        loading -> LegacyDetailSkeleton(
            Modifier.fillMaxSize().padding(
                start = safeArea.start,
                top = safeArea.top,
                end = safeArea.end,
                bottom = safeArea.bottom,
            ),
        )
        error != null -> Box(
            Modifier.fillMaxSize().padding(
                start = safeArea.start + 24.dp,
                top = safeArea.top + 88.dp,
                end = safeArea.end + 24.dp,
                bottom = safeArea.bottom + 24.dp,
            ),
            contentAlignment = Alignment.Center,
        ) {
            InlineProblem(error, onRetry)
        }
    }
}

/** Mechanical counterpart of ArkUI `SkeletonDetailView`. */
@Composable
private fun LegacyDetailSkeleton(modifier: Modifier = Modifier) {
    val skeleton = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)
    // Same column width as the loaded page (and the same phone gate), so the
    // layout does not jump when data arrives.
    val skeletonLayout = LocalMaFeiNavigationChrome.current.effectiveLayout
    val contentMaxWidth = skeletonLayout
        ?.takeIf { it.navigation != NavigationPlacement.Bottom }
        ?.gridWidthDp?.dp ?: 720.dp
    Column(modifier.widthIn(max = contentMaxWidth).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(350.dp).background(skeleton))
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(skeleton))
            Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(6.dp)).background(skeleton))
            Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp)).background(skeleton))
            Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(skeleton))
            Box(Modifier.width(80.dp).height(40.dp).clip(RoundedCornerShape(8.dp)).background(skeleton))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(4) {
                    Box(Modifier.size(60.dp).clip(CircleShape).background(skeleton))
                }
            }
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)).background(skeleton))
        }
    }
}

internal fun durationLabel(runtimeMs: Long): String {
    val totalMinutes = runtimeMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}小时${minutes}分钟"
        hours > 0L -> "${hours}小时"
        else -> "${minutes}分钟"
    }
}

private fun detailSubtitle(item: MediaItem): String = item.episodeSubtitle ?: ""

private fun detailPlayLabel(item: MediaItem, target: MediaItem?): String {
    if (item.mediaType != MediaType.Series || target == null || target.mediaType != MediaType.Episode) {
        return if ((target ?: item).userData.hasProgress) "继续播放" else "播放"
    }
    // 集信息格式由 model 按 provider 的风格标记给出（见 core:domain/MediaPresentation.kt），
    // 此处不再判断 provider。
    return target.episodeLabel ?: "播放"
}

private fun MediaItem.playbackProgress(): Float? {
    val duration = runtimeMs?.takeIf { it > 0L } ?: return null
    val explicit = userData.playedPercentage?.takeIf { it > 0.0 }?.div(100.0)?.toFloat()
    return (explicit ?: (userData.playbackPositionMs.toFloat() / duration)).coerceIn(0f, 1f)
}

private fun MediaItem.remainingTime(): String {
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

private fun compactRemainingTime(value: String): String = value
    .replace("剩余 ", "剩余")
    .replace(" 小时 ", "小时")
    .replace(" 分钟", "分")

private fun PersonRef.legacyRoleLabel(): String = when (personType) {
    PersonKind.Actor -> role?.takeIf { it.isNotBlank() }?.let { "饰$it" }.orEmpty()
    PersonKind.Director -> "导演"
    else -> role.orEmpty()
}

private fun formatRating(value: Double): String {
    val tenths = (value * 10.0).roundToInt()
    return "${tenths / 10}.${abs(tenths % 10)}"
}

private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

private val DetailActionSurfaceSpec = GlassSurfaceSpec(
    role = GlassRole.Overlay,
    level = GlassLevel.Full,
    tintAlpha = .18f,
    intensity = .86f,
    cornerRadiusDp = 25f,
)

private val ScoreColor = Color(0xFFF1B020)
private val FavoriteColor = Color(0xFFE9210B)
private val DetailAtmosphereInk = Color(0xFF161618)
