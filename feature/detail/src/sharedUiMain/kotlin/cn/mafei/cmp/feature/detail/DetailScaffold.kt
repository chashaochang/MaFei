package cn.mafei.cmp.feature.detail

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.ui.LocalMaFeiNavigationChrome
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.ui.InlineProblem
import cn.mafei.cmp.common.ui.mafeiPageTitleBarContentHeight
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.PersonRef
import cn.mafei.cmp.feature.detail.generated.resources.Res
import cn.mafei.cmp.feature.detail.generated.resources.ma_shan_zheng_regular
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font

/**
 * Detail pages use the same horizontal rhythm as Home/MediaList: 16dp on a
 * phone and the layout policy's page padding on tablet/desktop. The legacy
 * detail components already contain their 16dp inner gutter, so callers add
 * only the difference when the large-screen token is wider.
 */
@Composable
internal fun detailPageHorizontalPadding(): Dp {
    return (LocalMaFeiNavigationChrome.current.effectiveLayout?.pagePaddingDp ?: 16f).dp
}

@Composable
internal fun detailOuterHorizontalInset(): Dp =
    (detailPageHorizontalPadding() - 16.dp).coerceAtLeast(0.dp)

@Composable
internal fun DetailAtmosphere(
    item: MediaItem?,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    leadingBleed: Dp = 0.dp,
    trailingBleed: Dp = 0.dp,
    unblurred: Boolean = false,
    cachedArtwork: (@Composable (MediaItem, Dp, Modifier) -> Unit)? = null,
) {
    val atmosphereModifier = if (leadingBleed > 0.dp || trailingBleed > 0.dp) {
        Modifier
            .fillMaxSize()
            .detailAtmosphereBleed(leadingBleed, trailingBleed)
            .clipToBounds()
    } else {
        Modifier.fillMaxSize().clipToBounds()
    }
    Box(atmosphereModifier.background(MaterialTheme.colorScheme.background)) {
        if (item != null && (artwork != null || cachedArtwork != null)) {
            val darkMode = isSystemInDarkTheme()
            val imageModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                        alpha = .95f
                        // The large-screen detail page is unblurred by design,
                        // so it needs a stronger contrast pass for the white
                        // title and metadata. Phone values remain unchanged.
                        val brightness = when {
                            unblurred && darkMode -> .46f
                            unblurred -> .58f
                            darkMode -> .58f
                            else -> .72f
                        }
                        colorFilter = ColorFilter.colorMatrix(
                            ColorMatrix().apply { setToScale(brightness, brightness, brightness, 1f) },
                        )
                    }
                    .let { modifier ->
                        if (unblurred || cachedArtwork != null) modifier else modifier.blur(DetailAtmosphereBlurRadius)
                    }
            if (cachedArtwork != null) {
                cachedArtwork(item, if (unblurred) 0.dp else DetailAtmosphereBlurRadius, imageModifier)
            } else {
                artwork?.invoke(item, imageModifier)
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                if (unblurred) {
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .28f),
                        .56f to Color.Black.copy(alpha = .46f),
                        1f to Color.Black.copy(alpha = .58f),
                    )
                } else {
                    Brush.verticalGradient(
                        0f to DetailAtmosphereInk.copy(alpha = .19f),
                        .56f to DetailAtmosphereInk.copy(alpha = .34f),
                        1f to DetailAtmosphereInk.copy(alpha = .41f),
                    )
                },
            ),
        )
    }
}

/**
 * Measures the artwork at the full window width while reporting the route's
 * original content-pane size to its parent. `requiredWidth` cannot be used for
 * this: Compose centres an over-constrained child before applying its offset,
 * which leaves an uncovered strip on the opposite edge.
 */
private fun Modifier.detailAtmosphereBleed(
    leading: Dp,
    trailing: Dp,
): Modifier = layout { measurable, constraints ->
    val leadingPx = leading.roundToPx()
    val trailingPx = trailing.roundToPx()
    val paneWidth = constraints.maxWidth
    val windowWidth = (paneWidth + leadingPx + trailingPx).coerceAtLeast(1)
    val placeable = measurable.measure(
        constraints.copy(minWidth = windowWidth, maxWidth = windowWidth),
    )
    layout(paneWidth, constraints.maxHeight) {
        placeable.placeRelative(-leadingPx, 0)
    }
}

@Composable
internal fun BoxScope.DetailBody(
    item: MediaItem,
    scrollState: androidx.compose.foundation.ScrollState,
    collapseProgress: State<Float>,
    loading: Boolean,
    error: String?,
    stale: Boolean,
    onRetry: () -> Unit,
    onPlay: (() -> Unit)?,
    playTarget: MediaItem?,
    playbackBusy: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onTogglePlayed: (() -> Unit)?,
    onToggleChasing: (() -> Unit)?,
    isChasing: Boolean,
    chasingBusy: Boolean,
    actionsBusy: Boolean,
    seasons: List<MediaItem>,
    sectionsLoading: Boolean,
    sectionsError: String?,
    onSeasonSelected: (MediaItem) -> Unit,
    playbackSources: List<DetailPlaybackSource>,
    onOpenExternalUrl: ((String) -> Unit)?,
    selectedMediaSourceId: String?,
    onSelectMediaSource: ((String?) -> Unit)?,
    selectedAudioStreamIndex: Int?,
    onSelectAudioTrack: ((Int?) -> Unit)?,
    selectedSubtitleStreamIndex: Int?,
    onSelectSubtitleTrack: ((Int?) -> Unit)?,
    pickerKind: DetailPickerKind?,
    onOpenPicker: (DetailPickerKind) -> Unit,
    libraryName: String?,
    contentMaxWidth: Dp,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)?,
    backdropArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    logoArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    personArtwork: (@Composable (PersonRef, Modifier) -> Unit)?,
    onPersonSelected: (PersonRef) -> Unit,
    onOpenOverview: () -> Unit,
    onOpenMediaInfo: () -> Unit,
    backdrop: GlassBackdrop,
    simplifiedLargeLayout: Boolean,
    heroExtraHeight: Dp,
    heroScrollConnection: NestedScrollConnection,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val outerHorizontalInset = detailOuterHorizontalInset()
    val selectedPlaybackSource = playbackSources.firstOrNull { it.id == selectedMediaSourceId }
        ?: playbackSources.firstOrNull()
    // The full-page atmosphere is always media-first and darkened. Downstream
    // text therefore shares one white ink hierarchy instead of switching with
    // the app theme while it still sits on cover art.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        val contentAlignment = if (simplifiedLargeLayout) Alignment.Start else Alignment.CenterHorizontally
        val contentWidthModifier = if (simplifiedLargeLayout) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.widthIn(max = contentMaxWidth).fillMaxWidth()
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(
                    if (simplifiedLargeLayout) Modifier
                    else Modifier.nestedScroll(heroScrollConnection),
                )
                // The legacy hero owns the top-edge stretch. Compose's default
                // overscroll translates the whole scroll layer after a fast
                // return from the bottom, which pulls the hero away from the
                // viewport top instead of stretching it in place.
                .verticalScroll(scrollState, overscrollEffect = null),
        ) {
            if (simplifiedLargeLayout) {
                DetailProjectHeader(
                    item = item,
                    libraryName = libraryName,
                    logoArtwork = logoArtwork,
                    playbackSource = selectedPlaybackSource,
                    backdrop = backdrop,
                    safeAreaTop = safeArea.top,
                    sharedArtworkModifier = sharedArtworkModifier,
                    modifier = Modifier.align(contentAlignment),
                )
            } else {
                DetailHero(
                    item = item,
                    height = 430.dp + heroExtraHeight,
                    collapseProgress = collapseProgress,
                    libraryName = libraryName,
                    artwork = backdropArtwork ?: artwork,
                    sharedArtworkModifier = sharedArtworkModifier,
                    logoArtwork = logoArtwork,
                    playbackSource = selectedPlaybackSource,
                    backdrop = backdrop,
                )
            }

            Column(
                Modifier
                    .align(contentAlignment)
                    .then(contentWidthModifier)
                    .padding(
                        start = outerHorizontalInset,
                        end = outerHorizontalInset,
                        bottom = safeArea.bottom + 30.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // On large screens the project header owns the complete gap
                // below the navigation chrome. The phone keeps the legacy
                // spacer so its original Hero rhythm is unchanged.
                if (!simplifiedLargeLayout) {
                    Spacer(Modifier.height(16.dp))
                }
                DetailActions(
                    item = item,
                    playTarget = playTarget,
                    onPlay = onPlay,
                    playbackBusy = playbackBusy,
                    onToggleFavorite = onToggleFavorite,
                    onTogglePlayed = onTogglePlayed,
                    onToggleChasing = onToggleChasing,
                    isChasing = isChasing,
                    chasingBusy = chasingBusy,
                    actionsBusy = actionsBusy,
                    backdrop = backdrop,
                    largeLayout = simplifiedLargeLayout,
                )
                if (item.mediaType != MediaType.Series && playbackSources.isNotEmpty()) {
                    Spacer(Modifier.height(DetailActionSelectorSpacing))
                    PlaybackSelectionSection(
                        sources = playbackSources,
                        selectedMediaSourceId = selectedPlaybackSource?.id,
                        onSelectMediaSource = onSelectMediaSource,
                        selectedAudioStreamIndex = selectedAudioStreamIndex,
                        onSelectAudioTrack = onSelectAudioTrack,
                        selectedSubtitleStreamIndex = selectedSubtitleStreamIndex,
                        onSelectSubtitleTrack = onSelectSubtitleTrack,
                        pickerKind = pickerKind,
                        onOpenPicker = onOpenPicker,
                        backdrop = backdrop,
                        largeLayout = simplifiedLargeLayout,
                    )
                }
                if (item.overview?.isNotBlank() == true) {
                    Spacer(Modifier.height(DetailSectionSpacing))
                    OverviewSection(
                        overview = item.overview,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onOpen = onOpenOverview,
                    )
                }
                if (item.mediaType == MediaType.Series &&
                    (seasons.isNotEmpty() || sectionsLoading || sectionsError != null)
                ) {
                    Spacer(Modifier.height(DetailSectionSpacing))
                    SeriesSections(
                        seasons = seasons,
                        loading = sectionsLoading,
                        error = sectionsError,
                        artwork = artwork,
                        sharedArtworkModifier = sharedArtworkModifier,
                        onSeasonSelected = onSeasonSelected,
                    )
                }
                if (item.people.isNotEmpty()) {
                    Spacer(Modifier.height(DetailSectionSpacing))
                    LegacyPeopleSection(item.people, personArtwork, onPersonSelected)
                }
                if (item.mediaType != MediaType.Series && selectedPlaybackSource != null &&
                    (selectedPlaybackSource.hasFileInformation || selectedPlaybackSource.hasVideoInformation)
                ) {
                    Spacer(Modifier.height(DetailSectionSpacing))
                    MediaInfoSection(
                        source = selectedPlaybackSource,
                        onOpenDetails = onOpenMediaInfo,
                    )
                }
                if (onOpenExternalUrl != null && item.externalUrls.isNotEmpty()) {
                    Spacer(Modifier.height(DetailSectionSpacing))
                    ExternalLinksSection(item.externalUrls, onOpenExternalUrl)
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

/**
 * Large-screen detail header.  The page uses the current artwork as its
 * full-window background, so there is no second hero image competing with it.
 * The project mark/title keeps the same source and typography as the legacy
 * hero, but is aligned to the shared page gutter at the upper leading edge.
 */
@Composable
private fun DetailProjectHeader(
    item: MediaItem,
    libraryName: String?,
    logoArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    playbackSource: DetailPlaybackSource?,
    backdrop: GlassBackdrop,
    safeAreaTop: Dp,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)?,
    modifier: Modifier = Modifier,
) {
    val horizontalPadding = detailPageHorizontalPadding()
    val heroTags = detailHeroTags(item, playbackSource)
    val largeTypography = usesLargeDetailTypography()
    val hasLogo = item.images.any { it.kind == ImageKind.Logo }
    val usesCalligraphy = item.title.any { it in '\u3400'..'\u9fff' }
    val calligraphyFont = if (usesCalligraphy) {
        FontFamily(Font(Res.font.ma_shan_zheng_regular))
    } else {
        null
    }
    val headerBounds = modifier.fillMaxWidth()
    Box(
        (sharedArtworkModifier?.invoke(item, headerBounds) ?: headerBounds)
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
            if (hasLogo && logoArtwork != null) {
                Box(Modifier.width(196.dp).height(76.dp)) {
                    logoArtwork(item, Modifier.fillMaxSize())
                }
            } else {
                detailSubtitle(item).takeIf { it.isNotBlank() }?.let { subtitle ->
                    Text(
                        subtitle,
                        color = Color.White,
                        fontSize = if (largeTypography) 16.sp else 14.sp,
                        lineHeight = if (largeTypography) 22.sp else 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    item.title,
                    Modifier.semantics { heading() },
                    color = Color.White,
                    fontSize = when {
                        usesCalligraphy && largeTypography -> 72.sp
                        usesCalligraphy -> 68.sp
                        largeTypography -> 44.sp
                        else -> 38.sp
                    },
                    lineHeight = when {
                        usesCalligraphy && largeTypography -> 76.sp
                        usesCalligraphy -> 72.sp
                        largeTypography -> 50.sp
                        else -> 44.sp
                    },
                    fontFamily = calligraphyFont,
                    fontWeight = if (usesCalligraphy) FontWeight.Normal else FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.displaySmall.copy(
                        shadow = Shadow(Color.Black.copy(alpha = .82f), Offset(0f, 3f), 10f),
                    ),
                )
            }
            DetailMetadata(item, libraryName, Color.White)
            if (item.mediaType != MediaType.Series && heroTags.isNotEmpty()) {
                DetailHeroMediaTags(
                    heroTags,
                    backdrop,
                    centered = false,
                    modifier = Modifier.padding(top = DetailHeroTagSpacing),
                )
            }
        }
    }
}

private val DetailAtmosphereBlurRadius = 48.dp

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
internal fun DetailState(loading: Boolean, error: String?, onRetry: () -> Unit) {
    val safeArea = LocalMaFeiSafeArea.current
    Box(Modifier.fillMaxSize()) {
        DetailAnimatedVisibility(
            visible = loading,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(DetailMotionFastMillis)),
            exit = fadeOut(tween(DetailMotionExitMillis)),
        ) {
            LegacyDetailSkeleton(
                Modifier.fillMaxSize().padding(
                    start = safeArea.start,
                    top = safeArea.top,
                    end = safeArea.end,
                    bottom = safeArea.bottom,
                ),
            )
        }
        DetailAnimatedVisibility(
            visible = !loading && error != null,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(DetailMotionEnterMillis)) +
                slideInVertically(tween(DetailMotionEnterMillis)) { it / 24 },
            exit = fadeOut(tween(DetailMotionExitMillis)),
        ) {
            Box(
                Modifier.fillMaxSize().padding(
                    start = safeArea.start + 24.dp,
                    top = safeArea.top + 88.dp,
                    end = safeArea.end + 24.dp,
                    bottom = safeArea.bottom + 24.dp,
                ),
                contentAlignment = Alignment.Center,
            ) {
                InlineProblem(error.orEmpty(), onRetry)
            }
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
    Column(
        modifier.widthIn(max = contentMaxWidth).fillMaxWidth()
            .padding(horizontal = detailOuterHorizontalInset()),
    ) {
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

internal fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

internal val DetailSectionSpacing = 28.dp

internal val DetailSectionContentSpacing = 12.dp

internal val DetailMediaSubsectionSpacing = 20.dp

@Composable
internal fun usesLargeDetailTypography(): Boolean =
    LocalMaFeiNavigationChrome.current.effectiveLayout
        ?.let { it.navigation != NavigationPlacement.Bottom } == true

private val DetailAtmosphereInk = Color(0xFF161618)
