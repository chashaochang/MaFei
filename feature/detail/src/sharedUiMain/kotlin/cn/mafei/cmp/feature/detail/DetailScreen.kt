package cn.mafei.cmp.feature.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.NavigationEdge
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.ui.LocalMaFeiNavigationChrome
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyTransientToast
import cn.mafei.cmp.common.ui.LocalMaFeiWindowModalHost
import cn.mafei.cmp.common.ui.MaFeiAnimatedModalOverlay
import cn.mafei.cmp.common.ui.MaFeiErrorDialog
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.PersonRef

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
    onToggleChasing: (() -> Unit)? = null,
    isChasing: Boolean = false,
    chasingBusy: Boolean = false,
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
    atmosphereArtwork: (@Composable (MediaItem, Dp, Modifier) -> Unit)? = null,
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
    val collapseProgress = remember(scrollState, startPx, endPx) {
        derivedStateOf {
            ((scrollState.value - startPx) / (endPx - startPx)).coerceIn(0f, 1f)
        }
    }
    val collapsedTitleOpacity = remember(collapseProgress) {
        derivedStateOf { ((collapseProgress.value - .72f) / .22f).coerceIn(0f, 1f) }
    }
    var overviewSheetVisible by remember(item?.id?.value) { mutableStateOf(false) }
    var mediaInfoSheetVisible by remember(item?.id?.value) { mutableStateOf(false) }
    var pickerKind by remember(item?.id?.value) { mutableStateOf<DetailPickerKind?>(null) }
    val sheetVisible = overviewSheetVisible || mediaInfoSheetVisible || pickerKind != null
    val useWindowModalHost = largeDetailLayout && LocalMaFeiWindowModalHost.current != null
    val dismissTopSheet: () -> Unit = {
        when {
            pickerKind != null -> pickerKind = null
            mediaInfoSheetVisible -> mediaInfoSheetVisible = false
            overviewSheetVisible -> overviewSheetVisible = false
        }
    }
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
        // Phone keeps the legacy reading width. Large screens use the whole
        // available content pane so metadata and media/file information do not
        // leave an artificial empty column on the trailing side.
        val detailContentMaxWidth = 720.dp
        Box(Modifier.fillMaxSize().captureGlassBackdrop(actionBarBackdrop)) {
            Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
                DetailAtmosphere(
                    item = item,
                    artwork = backdropArtwork,
                    cachedArtwork = atmosphereArtwork,
                    leadingBleed = atmosphereLeadingBleed,
                    trailingBleed = atmosphereTrailingBleed,
                    unblurred = largeDetailLayout,
                    sharedArtworkModifier = sharedArtworkModifier.takeIf { largeDetailLayout },
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
                                onPlay = onPlay,
                                playTarget = playTarget,
                                playbackBusy = playbackBusy,
                                onToggleFavorite = onToggleFavorite,
                                onTogglePlayed = onTogglePlayed,
                                onToggleChasing = onToggleChasing,
                                isChasing = isChasing,
                                chasingBusy = chasingBusy,
                                actionsBusy = actionsBusy,
                                seasons = seasons,
                                sectionsLoading = sectionsLoading,
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

        DetailScrollActionBar(
            title = item?.title.orEmpty(),
            onBack = { if (sheetVisible) dismissTopSheet() else onBack() },
            backdrop = actionBarBackdrop,
            materialAlpha = collapseProgress,
            titleAlpha = collapsedTitleOpacity,
            loading = item == null,
            collapsedContentColor = if (item == null) MaterialTheme.colorScheme.onSurface else Color.White,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        LegacyTransientToast(
            message = actionsError ?: playbackError,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        val activeModal = when {
            pickerKind != null -> DetailModal.Picker(pickerKind!!)
            mediaInfoSheetVisible && selectedPlaybackSource != null -> DetailModal.MediaInfo
            overviewSheetVisible -> DetailModal.Overview
            else -> null
        }
        MaFeiAnimatedModalOverlay(
            value = activeModal,
            backdrop = actionBarBackdrop,
            useWindowHost = useWindowModalHost,
            onDismiss = dismissTopSheet,
        ) { modal, modalBackdrop, motion ->
            when (modal) {
                DetailModal.Overview -> OverviewSheetOverlay(
                    overview = item?.overview,
                    backdrop = modalBackdrop,
                    onDismiss = { overviewSheetVisible = false },
                    surfaceMotion = motion,
                )
                DetailModal.MediaInfo -> MediaInfoSheetOverlay(
                    sections = selectedPlaybackSource?.infoSections.orEmpty(),
                    backdrop = modalBackdrop,
                    onDismiss = { mediaInfoSheetVisible = false },
                    surfaceMotion = motion,
                )
                is DetailModal.Picker -> DetailPickerSheetOverlay(
                    kind = modal.kind,
                    sources = playbackSources,
                    selectedSource = selectedPlaybackSource,
                    selectedAudioStreamIndex = selectedAudioStreamIndex,
                    selectedSubtitleStreamIndex = selectedSubtitleStreamIndex,
                    backdrop = modalBackdrop,
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
                    surfaceMotion = motion,
                )
            }
        }
        MaFeiErrorDialog(
            message = listOfNotNull(error, sectionsError).distinct().takeIf {
                item != null && it.isNotEmpty()
            }?.joinToString("\n"),
            title = "详情加载失败",
            backdrop = actionBarBackdrop,
            busy = loading || sectionsLoading || sheetVisible,
            onRetry = onRetry,
        )
    }
}
