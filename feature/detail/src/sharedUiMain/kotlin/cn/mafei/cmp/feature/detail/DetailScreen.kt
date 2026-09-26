package cn.mafei.cmp.feature.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.font.FontFamily
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
import cn.mafei.cmp.common.design_system.NavigationEdge
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.ui.LocalMaFeiNavigationChrome
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.glass.rememberCombinedGlassBackdrop
import cn.mafei.cmp.common.ui.InlineProblem
import cn.mafei.cmp.common.ui.LegacyTransientToast
import cn.mafei.cmp.common.ui.LegacyVideoCard
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.TextPoster
import cn.mafei.cmp.common.ui.legacyMediaColumns
import cn.mafei.cmp.common.ui.legacyMediaGridGap
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.common.ui.mafeiPageTitleBarContentHeight
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.PersonKind
import cn.mafei.cmp.core.domain.episodeLabel
import cn.mafei.cmp.core.domain.episodeSubtitle
import cn.mafei.cmp.core.domain.PersonRef
import cn.mafei.cmp.feature.detail.generated.resources.Res
import cn.mafei.cmp.feature.detail.generated.resources.ma_shan_zheng_regular
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_1080
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_4k
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_dolby_atmos
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_dolby_surround
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_dolby_vision
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_dts
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_hdr
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_sdr
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_stereo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compose translation of the legacy HarmonyOS VideoDetailPage.
 *
 * The legacy screen is the specification: a fixed blurred artwork atmosphere,
 * a 430vp feathered hero, compact playback actions, three-line overview sheet,
 * width-driven season grid and the original cast rhythm. Wide windows retain the
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
    // Source-compatible only: the ArkUI page has no similar-content section,
    // so callers from an older app shell may still pass these without the
    // detail page rendering invented content.
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
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)? = null,
    backdropArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = artwork,
    logoArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
    personArtwork: (@Composable (PersonRef, Modifier) -> Unit)? = null,
    onPersonSelected: (PersonRef) -> Unit = {},
    atmosphereArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = backdropArtwork,
) {
    val backdrop = rememberGlassBackdrop()
    val actionBarBackdrop = rememberGlassBackdrop()
    val scrollState = rememberScrollState()
    val largeDetailLayout = LocalMaFeiNavigationChrome.current.effectiveLayout
        ?.let { it.navigation != NavigationPlacement.Bottom } == true
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
    var mediaInfoSheetVisible by remember(item?.id?.value) { mutableStateOf(false) }
    var pickerKind by remember(item?.id?.value) { mutableStateOf<DetailPickerKind?>(null) }
    val sheetVisible = overviewSheetVisible || mediaInfoSheetVisible || pickerKind != null
    val dismissTopSheet: () -> Unit = {
        when {
            pickerKind != null -> pickerKind = null
            mediaInfoSheetVisible -> mediaInfoSheetVisible = false
            overviewSheetVisible -> overviewSheetVisible = false
        }
    }
    DetailSheetBackHandler(enabled = sheetVisible, onDismiss = dismissTopSheet)
    val playbackSources = remember(item?.provider, item?.mediaSources) {
        item?.let {
            presentDetailPlaybackSources(it.provider, selectLegacyDetailSources(it.mediaSources))
        }.orEmpty()
    }
    val selectedPlaybackSource = remember(playbackSources, selectedMediaSourceId) {
        playbackSources.firstOrNull { it.id == selectedMediaSourceId } ?: playbackSources.firstOrNull()
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The atmosphere mirrors the home ambient: it also runs under the floating
        // sidebar, so navigation samples the same blurred artwork instead of the
        // app-level canvas and the whole-window background stays continuous.
        val chrome = LocalMaFeiNavigationChrome.current
        val effectiveLayout = chrome.effectiveLayout
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
        val atmosphereWidth = maxWidth + atmosphereLeadingBleed + atmosphereTrailingBleed
        // Legacy keeps the hero full-width and constrains only the reading area.
        val detailContentMaxWidth = 720.dp
        Box(Modifier.fillMaxSize().captureGlassBackdrop(actionBarBackdrop)) {
            Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
                DetailAtmosphere(
                    item = item,
                    artwork = atmosphereArtwork,
                    leadingBleed = atmosphereLeadingBleed,
                    expandedWidth = atmosphereWidth,
                    unblurred = largeDetailLayout,
                )
            }
            AnimatedContent(
                targetState = item != null,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(tween(DetailMotionEnterMillis)) +
                        slideInVertically(tween(DetailMotionEnterMillis)) { it / 40 }) togetherWith
                        fadeOut(tween(DetailMotionExitMillis))
                },
                contentKey = { it },
                label = "detail-content",
            ) { hasItem ->
                if (hasItem) {
                    item?.let { loadedItem ->
                        Box(Modifier.fillMaxSize()) {
                            DetailBody(
                                item = loadedItem,
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
                                playbackSources = playbackSources,
                                onOpenExternalUrl = onOpenExternalUrl,
                                selectedMediaSourceId = selectedMediaSourceId,
                                onSelectMediaSource = onSelectMediaSource,
                                selectedAudioStreamIndex = selectedAudioStreamIndex,
                                onSelectAudioTrack = onSelectAudioTrack,
                                selectedSubtitleStreamIndex = selectedSubtitleStreamIndex,
                                onSelectSubtitleTrack = onSelectSubtitleTrack,
                                pickerKind = pickerKind,
                                onOpenPicker = { pickerKind = it },
                                libraryName = libraryName,
                                contentMaxWidth = detailContentMaxWidth,
                                artwork = artwork,
                                sharedArtworkModifier = sharedArtworkModifier,
                                backdropArtwork = backdropArtwork,
                                logoArtwork = logoArtwork,
                                personArtwork = personArtwork,
                                onPersonSelected = onPersonSelected,
                                onOpenOverview = { overviewSheetVisible = true },
                                onOpenMediaInfo = { mediaInfoSheetVisible = true },
                                backdrop = backdrop,
                                simplifiedLargeLayout = largeDetailLayout,
                                heroExtraHeight = heroStretch.extraHeight,
                                heroScrollConnection = heroStretch.connection,
                            )
                        }
                    }
                } else {
                    DetailState(loading, error, onRetry)
                }
            }
        }

        MaFeiActionBar(
            title = item?.title.orEmpty(),
            onBack = { if (sheetVisible) dismissTopSheet() else onBack() },
            backdrop = actionBarBackdrop,
            materialAlpha = if (item == null) 1f else collapseProgress,
            titleAlpha = if (item == null) 0f else collapsedTitleOpacity,
            contentColor = if (item == null) MaterialTheme.colorScheme.onSurface else Color.White,
            modifier = Modifier.align(Alignment.TopCenter),
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
        if (mediaInfoSheetVisible && selectedPlaybackSource != null) {
            MediaInfoSheetOverlay(
                sections = selectedPlaybackSource.infoSections,
                backdrop = actionBarBackdrop,
                onDismiss = { mediaInfoSheetVisible = false },
            )
        }
        pickerKind?.let { kind ->
            DetailPickerSheetOverlay(
                kind = kind,
                sources = playbackSources,
                selectedSource = selectedPlaybackSource,
                selectedAudioStreamIndex = selectedAudioStreamIndex,
                selectedSubtitleStreamIndex = selectedSubtitleStreamIndex,
                backdrop = actionBarBackdrop,
                onDismiss = { pickerKind = null },
                onSelectSource = { sourceId ->
                    pickerKind = null
                    onSelectMediaSource?.invoke(sourceId)
                },
                onSelectAudio = { streamIndex ->
                    pickerKind = null
                    onSelectAudioTrack?.invoke(streamIndex)
                },
                onSelectSubtitle = { streamIndex ->
                    pickerKind = null
                    onSelectSubtitleTrack?.invoke(streamIndex)
                },
            )
        }
    }
}

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
private fun DetailAtmosphere(
    item: MediaItem?,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    leadingBleed: Dp = 0.dp,
    expandedWidth: Dp? = null,
    unblurred: Boolean = false,
) {
    val atmosphereModifier = Modifier
        .offset(x = -leadingBleed)
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
                        // Keep the artwork dark enough for the existing white
                        // detail ink. Large screens only remove the blur; they
                        // keep this contrast treatment unchanged.
                        val brightness = if (darkMode) .58f else .72f
                        colorFilter = ColorFilter.colorMatrix(
                            ColorMatrix().apply { setToScale(brightness, brightness, brightness, 1f) },
                        )
                    }
                    .let { modifier ->
                        if (unblurred) modifier else modifier.blur(DetailAtmosphereBlurRadius)
                    },
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
    val showRefreshProgress = rememberDelayedDetailLoading(loading)
    val selectedPlaybackSource = playbackSources.firstOrNull { it.id == selectedMediaSourceId }
        ?: playbackSources.firstOrNull()
    // The full-page atmosphere is always media-first and darkened. Downstream
    // text therefore shares one white ink hierarchy instead of switching with
    // the app theme while it still sits on cover art.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
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
                    safeAreaTop = safeArea.top,
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
                )
            }

            Column(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth()
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
                    actionsBusy = actionsBusy,
                    backdrop = backdrop,
                )
                Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                    DetailAnimatedVisibility(
                        visible = showRefreshProgress,
                        enter = fadeIn(tween(DetailMotionFastMillis)),
                        exit = fadeOut(tween(DetailMotionExitMillis)),
                    ) {
                        MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Inline)
                    }
                }
                if (item.mediaType != MediaType.Series && playbackSources.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
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
                    )
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
                if (item.mediaType != MediaType.Series && selectedPlaybackSource != null &&
                    (selectedPlaybackSource.hasFileInformation || selectedPlaybackSource.hasVideoInformation)
                ) {
                    Spacer(Modifier.height(16.dp))
                    MediaInfoSection(
                        source = selectedPlaybackSource,
                        onOpenDetails = onOpenMediaInfo,
                    )
                }
                if (onOpenExternalUrl != null && item.externalUrls.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
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
    safeAreaTop: Dp,
) {
    val horizontalPadding = detailPageHorizontalPadding()
    val hasLogo = item.images.any { it.kind == ImageKind.Logo }
    val usesCalligraphy = item.title.any { it in '\u3400'..'\u9fff' }
    val calligraphyFont = if (usesCalligraphy) {
        FontFamily(Font(Res.font.ma_shan_zheng_regular))
    } else {
        null
    }
    Box(
        Modifier
            .widthIn(max = 720.dp)
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
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    item.title,
                    Modifier.semantics { heading() },
                    color = Color.White,
                    fontSize = if (usesCalligraphy) 68.sp else 38.sp,
                    lineHeight = if (usesCalligraphy) 72.sp else 44.sp,
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
            if (item.mediaType != MediaType.Series && playbackSource != null && playbackSource.tagKeys.isNotEmpty()) {
                DetailHeroMediaTags(playbackSource.tagKeys)
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
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)?,
    logoArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    playbackSource: DetailPlaybackSource?,
) {
    val horizontalPadding = detailPageHorizontalPadding()
    val titleOpacity = when {
        collapseProgress <= .72f -> 1f
        else -> (1f - (collapseProgress - .72f) / .22f).coerceIn(0f, 1f)
    }
    val titlePhase = smoothStep(((collapseProgress - .42f) / .58f).coerceIn(0f, 1f))
    val hasLogo = item.images.any { it.kind == ImageKind.Logo }
    val usesCalligraphy = item.title.any { it in '\u3400'..'\u9fff' }
    val calligraphyFont = if (usesCalligraphy) {
        FontFamily(Font(Res.font.ma_shan_zheng_regular))
    } else {
        null
    }
    val collapsedScale = when {
        hasLogo -> .28f
        usesCalligraphy -> .23f
        else -> .46f
    }
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
            artwork(
                item,
                sharedArtworkModifier?.invoke(item, artworkModifier) ?: artworkModifier,
            )
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
                .padding(start = horizontalPadding, end = horizontalPadding, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            if (hasLogo && logoArtwork != null) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
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
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                        fontSize = if (usesCalligraphy) 68.sp else 38.sp,
                        lineHeight = if (usesCalligraphy) 72.sp else 44.sp,
                        fontFamily = calligraphyFont,
                        fontWeight = if (usesCalligraphy) FontWeight.Normal else FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.displaySmall.copy(
                            shadow = Shadow(Color.Black.copy(alpha = .82f), Offset(0f, 3f), 10f),
                        ),
                    )
                }
            }

            DetailMetadata(item, libraryName, Color.White)
            if (item.mediaType != MediaType.Series && playbackSource != null && playbackSource.tagKeys.isNotEmpty()) {
                DetailHeroMediaTags(playbackSource.tagKeys)
            }
        }
    }
}

@Composable
private fun DetailHeroMediaTags(tags: List<DetailVideoTag>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEach { tag ->
            Image(
                painter = painterResource(tag.drawable),
                contentDescription = null,
                modifier = Modifier.height(21.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}

private val DetailVideoTag.drawable: DrawableResource
    get() = when (this) {
        DetailVideoTag.Tag1080 -> Res.drawable.video_tag_1080
        DetailVideoTag.Tag4K -> Res.drawable.video_tag_4k
        DetailVideoTag.Stereo -> Res.drawable.video_tag_stereo
        DetailVideoTag.Hdr -> Res.drawable.video_tag_hdr
        DetailVideoTag.Sdr -> Res.drawable.video_tag_sdr
        DetailVideoTag.Dts -> Res.drawable.video_tag_dts
        DetailVideoTag.DolbyVision -> Res.drawable.video_tag_dolby_vision
        DetailVideoTag.DolbyAtmos -> Res.drawable.video_tag_dolby_atmos
        DetailVideoTag.DolbySurround -> Res.drawable.video_tag_dolby_surround
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
        item.runtimeMs?.takeIf { it > 0L }?.let { add(legacyDetailDurationLabel(it)) }
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
            onAction = onToggleFavorite,
            busy = actionsBusy,
            backdrop = backdrop,
        )
        DetailIconAction(
            icon = MaFeiLegacyDrawables.watched,
            selected = item.userData.isPlayed,
            selectedColor = MaterialTheme.colorScheme.primary,
            accessibilityLabel = if (item.userData.isPlayed) "标记为未播放" else "标记为已播放",
            onAction = onTogglePlayed,
            busy = actionsBusy,
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
    val label = legacyDetailPlayLabel(item, target)
    Box(modifier.height(50.dp).clip(MaFeiShape.capsule)) {
        if (onPlay != null) {
            LiquidButton(
                onClick = { if (!busy) onPlay() },
                backdrop = backdrop,
                isInteractive = !busy,
                height = 50.dp,
                horizontalPadding = 0.dp,
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = if (remaining.isBlank()) label else "$label $remaining"
                },
            ) {}
        } else {
            GlassSurface(
                backdrop = backdrop,
                spec = DetailActionSurfaceSpec,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = label
                    disabled()
                },
            ) {}
        }
        // LegacyDetailPlayAction paints progress above the material surface and
        // below the button contents. Painting it before LiquidButton lets the
        // refracted backdrop cover the fill completely.
        if (progress != null && progress > 0f && progress < 1f && remaining.isNotBlank()) {
            Box(
                Modifier.fillMaxWidth(progress).fillMaxHeight()
                    .background(Color.White.copy(alpha = .26f)),
            )
        }
        Row(
            Modifier.fillMaxSize()
                .alpha(if (onPlay == null) .48f else 1f)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailPlayContents(label, remaining, busy && onPlay != null)
        }
    }
}

@Composable
private fun DetailPlayContents(label: String, remaining: String, busy: Boolean) {
    AnimatedContent(
        targetState = Triple(label, remaining, busy),
        transitionSpec = {
            fadeIn(tween(DetailMotionFastMillis)) togetherWith
                fadeOut(tween(DetailMotionExitMillis))
        },
        label = "detail-play-state",
    ) { (currentLabel, currentRemaining, isBusy) ->
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    currentLabel,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (currentRemaining.isNotBlank()) {
                    Text(
                        compactRemainingTime(currentRemaining),
                        color = Color.White.copy(alpha = .78f),
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                    )
                }
            }
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
    busy: Boolean,
    backdrop: GlassBackdrop,
) {
    val busyAlpha by animateFloatAsState(
        targetValue = if (busy) .56f else 1f,
        animationSpec = tween(DetailMotionFastMillis),
        label = "detail-action-alpha",
    )
    val content: @Composable RowScope.() -> Unit = {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                fadeIn(tween(DetailMotionFastMillis)) togetherWith
                    fadeOut(tween(DetailMotionExitMillis))
            },
            label = "detail-action-selection",
        ) { isSelected ->
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = if (isSelected && preserveOriginalColor) null else ColorFilter.tint(
                    if (isSelected) selectedColor else Color.White,
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
                contentDescription = accessibilityLabel
                if (busy) disabled()
            },
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
    val moreColor = MaterialTheme.colorScheme.primary
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
    val sheetMaterialBackdrop = rememberGlassBackdrop()
    val closeButtonBackdrop = rememberCombinedGlassBackdrop(backdrop, sheetMaterialBackdrop)
    val contentScrollState = rememberScrollState()
    val sheetDrag = rememberDetailSheetDrag(contentScrollState, onDismiss)
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
            .then(sheetDrag)
            .clickable(
                interactionSource = sheetInteraction,
                indication = null,
                onClick = {},
            ),
        exportedBackdrop = sheetMaterialBackdrop,
    ) {
        Column(
            Modifier.fillMaxSize().padding(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = safeArea.bottom + 10.dp,
            ),
        ) {
            DetailSheetHeader("简介", closeButtonBackdrop, onDismiss)
            Text(
                text,
                Modifier.fillMaxWidth().weight(1f).verticalScroll(contentScrollState),
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
    val showLoading = rememberDelayedDetailLoading(loading)
    Column(Modifier.fillMaxWidth()) {
        DetailAnimatedVisibility(
            visible = seasons.isNotEmpty(),
            enter = fadeIn(tween(DetailMotionEnterMillis)) +
                slideInVertically(tween(DetailMotionEnterMillis)) { it / 16 },
            exit = fadeOut(tween(DetailMotionExitMillis)),
        ) {
            Column(Modifier.fillMaxWidth()) {
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
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val horizontalPadding = 16.dp
                    val availableWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(1.dp)
                    // Reuse the same density progression as media/favorite/search
                    // grids. The actual constrained width is used here, so the
                    // count follows sidebar state and window resizing instead of
                    // guessing from a device category.
                    val columns = legacyMediaColumns(availableWidth.value)
                    val gap = legacyMediaGridGap(availableWidth.value)
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        seasons.chunked(columns).forEach { rowSeasons ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                rowSeasons.forEach { season ->
                                    SeasonCard(
                                        season = season,
                                        artwork = artwork,
                                        onClick = { onSeasonSelected(season) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(columns - rowSeasons.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        DetailAnimatedVisibility(
            visible = showLoading,
            enter = fadeIn(tween(DetailMotionFastMillis)),
            exit = fadeOut(tween(DetailMotionExitMillis)),
        ) {
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Inline)
            }
        }
        DetailAnimatedVisibility(
            visible = seasons.isEmpty() && !loading && error != null,
            enter = fadeIn(tween(DetailMotionEnterMillis)),
            exit = fadeOut(tween(DetailMotionExitMillis)),
        ) {
            Text(
                error.orEmpty(),
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
        subtitle = buildList {
            add("共${season.childCount ?: 0}集")
            season.year?.let { add(it.toString()) }
        }.joinToString(" · "),
        textAlign = TextAlign.Center,
        onClick = onClick,
        modifier = modifier,
        poster = artwork?.let { draw -> { cardModifier -> draw(season, cardModifier) } },
    )
}

/**
 * 播放选择区（legacy MediaSourceSelector + AudioSubtitleSelector 的页面内化）：
 * 多版本时先选版本（按账号和当前媒体条目保存），再按需挑音轨/字幕轨；
 * 字幕「无字幕」传 -1。区块只在真的有多可选内容时出现。
 */
@Composable
private fun PlaybackSelectionSection(
    sources: List<DetailPlaybackSource>,
    selectedMediaSourceId: String?,
    onSelectMediaSource: ((String?) -> Unit)?,
    selectedAudioStreamIndex: Int?,
    onSelectAudioTrack: ((Int?) -> Unit)?,
    selectedSubtitleStreamIndex: Int?,
    onSelectSubtitleTrack: ((Int?) -> Unit)?,
    pickerKind: DetailPickerKind?,
    onOpenPicker: (DetailPickerKind) -> Unit,
    backdrop: GlassBackdrop,
) {
    val primary = sources.firstOrNull { it.id == selectedMediaSourceId } ?: sources.firstOrNull() ?: return
    val multiSource = sources.size > 1 && onSelectMediaSource != null
    val audioTracks = primary.audioOptions
    val subtitleTracks = primary.subtitleOptions
    val effectiveAudioIndex = selectedAudioStreamIndex ?: primary.defaultAudioStreamIndex
    val effectiveSubtitleIndex = selectedSubtitleStreamIndex ?: primary.defaultSubtitleStreamIndex
    val audioLabel = audioTracks.firstOrNull { it.streamIndex == effectiveAudioIndex }?.compactLabel ?: "无音频"
    val subtitleLabel = subtitleTracks.firstOrNull {
        it.streamIndex == effectiveSubtitleIndex
    }?.compactLabel ?: "无字幕"

    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiSource) {
            DetailSelectorTrigger(
                label = primary.compactLabel,
                hasOptions = true,
                expanded = pickerKind == DetailPickerKind.Source,
                onClick = { onOpenPicker(DetailPickerKind.Source) },
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.weight(if (multiSource) 2f else 1f).fillMaxHeight()) {
            DetailSelectorTrigger(
                label = subtitleLabel,
                hasOptions = subtitleTracks.size > 1 && onSelectSubtitleTrack != null,
                expanded = pickerKind == DetailPickerKind.Subtitle,
                onClick = { onOpenPicker(DetailPickerKind.Subtitle) },
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
            )
            DetailSelectorTrigger(
                label = audioLabel,
                hasOptions = audioTracks.size > 1 && onSelectAudioTrack != null,
                expanded = pickerKind == DetailPickerKind.Audio,
                onClick = { onOpenPicker(DetailPickerKind.Audio) },
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailSelectorTrigger(
    label: String,
    hasOptions: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    LiquidButton(
        onClick = if (hasOptions) onClick else ({}),
        backdrop = backdrop,
        isInteractive = hasOptions,
        height = 32.dp,
        horizontalPadding = 8.dp,
        modifier = modifier.fillMaxHeight().padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            // Keep the label and chevron as one centred group. `fill = true`
            // consumed the whole remaining row and pushed the chevron to the
            // button edge instead of keeping it beside the text.
            Modifier.weight(1f, fill = false),
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hasOptions) {
            MaFeiIcon(
                glyph = MaFeiGlyph.Chevron,
                modifier = Modifier.size(12.dp).graphicsLayer {
                    rotationZ = if (expanded) -90f else 90f
                },
                color = Color.White,
            )
        }
    }
}

private enum class DetailPickerKind {
    Source,
    Subtitle,
    Audio,
}

/**
 * Same-layer translation of the legacy ArkUI bindSheet selectors. The source
 * list keeps the 68vp checkmark row; audio and subtitle keep the 66vp radio row.
 */
@Composable
private fun BoxScope.DetailPickerSheetOverlay(
    kind: DetailPickerKind,
    sources: List<DetailPlaybackSource>,
    selectedSource: DetailPlaybackSource?,
    selectedAudioStreamIndex: Int?,
    selectedSubtitleStreamIndex: Int?,
    backdrop: GlassBackdrop,
    onDismiss: () -> Unit,
    onSelectSource: (String) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
) {
    val source = selectedSource ?: return
    val safeArea = LocalMaFeiSafeArea.current
    val dismissInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }
    val sheetMaterialBackdrop = rememberGlassBackdrop()
    val closeButtonBackdrop = rememberCombinedGlassBackdrop(backdrop, sheetMaterialBackdrop)
    val contentScrollState = rememberScrollState()
    val sheetDrag = rememberDetailSheetDrag(contentScrollState, onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)).clickable(
            interactionSource = dismissInteraction,
            indication = null,
            onClick = onDismiss,
        ),
    )
    BoxWithConstraints(
        Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth(),
    ) {
        val sheetMaxHeight = maxHeight * .70f
        val listMaxHeight = (sheetMaxHeight - 76.dp).coerceAtLeast(120.dp)
        GlassSurface(
            backdrop = backdrop,
            spec = OverviewSheetSurfaceSpec,
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight).then(sheetDrag).clickable(
                interactionSource = sheetInteraction,
                indication = null,
                onClick = {},
            ),
            exportedBackdrop = sheetMaterialBackdrop,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = safeArea.bottom + 10.dp,
                ),
            ) {
                DetailSheetHeader(
                    title = when (kind) {
                        DetailPickerKind.Source -> "选择播放版本"
                        DetailPickerKind.Subtitle -> "字幕"
                        DetailPickerKind.Audio -> "音频"
                    },
                    backdrop = closeButtonBackdrop,
                    onDismiss = onDismiss,
                    contentColor = Color.White,
                )
                Column(
                    Modifier.fillMaxWidth().heightIn(max = listMaxHeight).verticalScroll(contentScrollState),
                    verticalArrangement = Arrangement.spacedBy(
                        if (kind == DetailPickerKind.Source) 4.dp else 12.dp,
                    ),
                ) {
                    when (kind) {
                        DetailPickerKind.Source -> sources.forEach { option ->
                            DetailSourceOptionRow(
                                option = option,
                                selected = option.id == source.id,
                                onClick = { onSelectSource(option.id) },
                            )
                        }

                        DetailPickerKind.Subtitle -> {
                            val selectedIndex = selectedSubtitleStreamIndex ?: source.defaultSubtitleStreamIndex
                            source.subtitleOptions.forEach { option ->
                                DetailTrackOptionRow(
                                    option = option,
                                    selected = option.streamIndex == selectedIndex,
                                    onClick = { onSelectSubtitle(option.streamIndex) },
                                )
                            }
                        }

                        DetailPickerKind.Audio -> {
                            val selectedIndex = selectedAudioStreamIndex ?: source.defaultAudioStreamIndex
                            source.audioOptions.forEach { option ->
                                DetailTrackOptionRow(
                                    option = option,
                                    selected = option.streamIndex == selectedIndex,
                                    onClick = { onSelectAudio(option.streamIndex) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSourceOptionRow(
    option: DetailPlaybackSource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = .04f))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.padding(start = 16.dp).width(20.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                MaFeiIcon(
                    glyph = MaFeiGlyph.Watched,
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                )
            }
        }
        Column(
            Modifier.weight(1f).padding(start = 12.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                option.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                option.summary,
                color = Color.White.copy(alpha = .72f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailTrackOptionRow(
    option: DetailMediaOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = .04f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val strokeWidth = 1.5.dp.toPx()
            drawCircle(
                color = Color.White.copy(alpha = if (selected) .95f else .70f),
                radius = size.minDimension / 2f - strokeWidth / 2f,
                style = Stroke(width = strokeWidth),
            )
            if (selected) {
                drawCircle(
                    color = Color.White.copy(alpha = .95f),
                    radius = size.minDimension * .25f,
                )
            }
        }
        Column(
            Modifier.weight(1f).padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                option.title,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (option.summary.isNotEmpty()) {
                Text(
                    option.summary,
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun MediaInfoSection(
    source: DetailPlaybackSource,
    onOpenDetails: () -> Unit,
) {
    if (!source.hasFileInformation && !source.hasVideoInformation) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (source.hasFileInformation) {
            Text(
                "文件信息",
                Modifier.semantics { heading() },
                color = LocalContentColor.current,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
            )
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DetailInformationBackground)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DetailInformationValue("文件位置", source.filePath)
                DetailInformationValue("文件大小", source.fileSize)
                DetailInformationValue("文件创建日期", source.fileCreatedDate)
                DetailInformationValue("添加日期", source.addedDate)
            }
        }
        if (source.hasVideoInformation) {
            if (source.hasFileInformation) Spacer(Modifier.height(16.dp))
            Text(
                "视频信息",
                Modifier.semantics { heading() },
                color = LocalContentColor.current,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
            )
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DetailInformationBackground)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DetailInformationValue("视频", source.videoSummary, multiline = true)
                DetailInformationValue("音频", source.audioSummary, multiline = true)
                DetailInformationValue("字幕", source.subtitleSummary, multiline = true)
                if (source.infoSections.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .18f)))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenDetails)
                            .padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "查看全部",
                            Modifier.weight(1f),
                            color = Color.White.copy(alpha = .88f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        MaFeiIcon(
                            glyph = MaFeiGlyph.Chevron,
                            modifier = Modifier.size(16.dp),
                            color = Color.White.copy(alpha = .88f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInformationValue(
    label: String,
    value: String,
    multiline: Boolean = false,
) {
    if (value.isBlank()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label,
            color = Color.White.copy(alpha = .60f),
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Text(
            value,
            color = Color.White.copy(alpha = .88f),
            fontSize = 12.sp,
            lineHeight = if (multiline) 20.sp else 18.sp,
        )
    }
}

@Composable
internal fun BoxScope.MediaInfoSheetOverlay(
    sections: List<DetailInfoSection>,
    backdrop: GlassBackdrop,
    onDismiss: () -> Unit,
) {
    if (sections.isEmpty()) return
    val safeArea = LocalMaFeiSafeArea.current
    val dismissInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }
    val sheetMaterialBackdrop = rememberGlassBackdrop()
    val closeButtonBackdrop = rememberCombinedGlassBackdrop(backdrop, sheetMaterialBackdrop)
    val contentScrollState = rememberScrollState()
    val sheetDrag = rememberDetailSheetDrag(contentScrollState, onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)).clickable(
            interactionSource = dismissInteraction,
            indication = null,
            onClick = onDismiss,
        ),
    )
    GlassSurface(
        backdrop = backdrop,
        spec = OverviewSheetSurfaceSpec,
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight(.70f)
            .then(sheetDrag)
            .clickable(
                interactionSource = sheetInteraction,
                indication = null,
                onClick = {},
            ),
        exportedBackdrop = sheetMaterialBackdrop,
    ) {
        Column(
            Modifier.fillMaxSize().padding(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = safeArea.bottom + 10.dp,
            ),
        ) {
            DetailSheetHeader(
                "文件媒体信息",
                closeButtonBackdrop,
                onDismiss,
                contentColor = Color.White,
            )
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(contentScrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                sections.forEach { section ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            section.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        section.cards.forEach { card -> DetailInfoCard(card) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun DetailInfoCard(card: DetailInfoCard) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(DetailInformationBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            card.title,
            color = Color.White.copy(alpha = .92f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        card.rows.forEach { row ->
            if (row.dividerBefore) {
                Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .18f)))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    row.title,
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    row.value,
                    Modifier.widthIn(max = 440.dp),
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** 外部链接（IMDb/TMDB/...），与旧版详情页同位；点击交给平台浏览器。 */
@Composable
private fun ExternalLinksSection(
    links: List<cn.mafei.cmp.core.domain.MediaExternalUrl>,
    onOpenUrl: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "链接",
            Modifier.padding(horizontal = 16.dp).semantics { heading() },
            color = LocalContentColor.current,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            links.forEach { link ->
                Text(
                    link.name,
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(DetailInformationBackground)
                        .clickable(onClick = { onOpenUrl(link.url) })
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White.copy(alpha = .88f),
                    fontSize = 12.sp,
                )
            }
        }
    }
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
                            Image(
                                painter = painterResource(MaFeiLegacyDrawables.castAvatar),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
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

// All three legacy detail pages use TextSectionAttribute's default maxLines=3.
// LastSpanAttribute(..., 2, ...) reserves suffix characters; it is not a line count.
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

private fun detailSubtitle(item: MediaItem): String = item.episodeSubtitle ?: ""

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
    PersonKind.Actor -> "饰${role.orEmpty()}"
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
private val DetailInformationBackground = Color(0x24FFFFFF)
