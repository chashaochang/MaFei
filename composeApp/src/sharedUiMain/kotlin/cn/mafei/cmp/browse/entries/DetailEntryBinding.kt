package cn.mafei.cmp

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.design_system.MaFeiLayoutFamily
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.CachedBlurredArtwork
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.media.legacyDetailBackdropArtworkCandidates
import cn.mafei.cmp.common.media.legacyPrimaryThenBackdropArtworkCandidates
import cn.mafei.cmp.common.media.posterArtworkCandidates
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.feature.chasing.ChasingSelection
import cn.mafei.cmp.feature.detail.DetailScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.*
import kotlinx.coroutines.launch

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
internal fun DetailEntryBinding(
    route: BrowseDetailRoute,
    playback: SessionPlaybackRuntime?,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    scope: kotlinx.coroutines.CoroutineScope,
    browser: MediaBrowser,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    layout: MaFeiLayout,
    player: BrowsePlayerState,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    navigator: BrowseNavigator,
    navigation: BrowseNavigationState,
    selection: BrowseSelectionState,
    chasingSelection: ChasingSelection,
    chasingSelectionState: cn.mafei.cmp.feature.chasing.ChasingSelectionState,
) {
    val sharedArtworkModifier: @Composable (MediaItem, Modifier) -> Modifier = { item, modifier ->
        with(sharedTransitionScope) {
            modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "media-artwork:${item.id.value}"),
                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                enter = fadeIn(tween(320)),
                exit = fadeOut(tween(320)),
                boundsTransform = { _, _ -> tween(420) },
                zIndexInOverlay = 1f,
            )
        }
    }
    // Feiniu's catalog rows do not always carry the
    // legacy can_play flag even when the detail/play
    // endpoint can resolve the item.  The resolved
    // target is the authoritative play affordance;
    // preparation will report a real failure.
    val detailPlayTarget = state.detailPlayTarget
    DetailScreen(
        item = state.detail,
        loading = state.detailLoading,
        error = state.detailError,
        onBack = navigator::pop,
        onRetry = { state.detailId?.let { id -> scope.launch { browser.openDetail(id) } } },
        onPlay = detailPlayTarget?.takeIf { playback != null }?.let { item -> { player.startPlayback(item) } },
        playTarget = detailPlayTarget,
        onOpenExternalUrl = { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: Exception) {
                player.playbackError = "无法打开外部链接"
            }
        },
        onDelete =
            if (state.deleteAvailable == true) {
                { state.detailId?.let { id -> selection.pendingDeleteMedia = listOf(id) } }
            } else null,
        selectedMediaSourceId = state.selectedMediaSourceId,
        onSelectMediaSource = { id -> scope.launch { browser.selectMediaSource(id) } },
        selectedAudioStreamIndex = state.selectedAudioStreamIndex,
        onSelectAudioTrack = { browser.selectAudioTrack(it) },
        selectedSubtitleStreamIndex = state.selectedSubtitleStreamIndex,
        onSelectSubtitleTrack = { browser.selectSubtitleTrack(it) },
        onToggleFavorite =
            if (browser.capabilities.favoritesEdit) {
                { scope.launch { browser.toggleFavorite() } }
            } else null,
        onTogglePlayed =
            if (browser.capabilities.watchedEdit) {
                { scope.launch { browser.toggleWatched() } }
            } else null,
        onToggleChasing =
            state.detail
                ?.takeIf { browser.capabilities.chasing && it.mediaType == MediaType.Series }
                ?.let { series -> { scope.launch { chasingSelection.toggle(series.id) } } },
        isChasing = state.detail?.id in chasingSelectionState.ids,
        chasingBusy = chasingSelectionState.saving,
        actionsBusy = state.detailActionBusy,
        actionsError = state.detailActionError ?: chasingSelectionState.error,
        seasons = state.detailSeasons,
        sectionsLoading = state.detailSectionsLoading,
        sectionsError = state.detailSectionsError,
        onSeasonSelected = { season ->
            if (navigation.browseBackStack.lastOrNull() !is BrowseSeasonRoute) {
                navigation.browseBackStack +=
                    BrowseSeasonRoute(
                        seriesId = route.mediaId,
                        seasonId = season.id.value,
                        mediaName = route.mediaName,
                        origin = route.origin,
                        libraryId = route.libraryId,
                    )
            }
        },
        playbackBusy = player.playbackBusy,
        playbackError = player.playbackError,
        libraryName = route.mediaName,
        modifier = Modifier.fillMaxSize(),
        artwork = { item, modifier ->
            MediaArtwork(item.legacyPrimaryThenBackdropArtworkCandidates(), item.title, artworkCache, fetchArtwork, modifier, targetWidthPx = 480)
        },
        sharedArtworkModifier = sharedArtworkModifier,
        backdropArtwork = { item, modifier ->
            MediaArtwork(
                item.legacyDetailBackdropArtworkCandidates(),
                item.title,
                artworkCache,
                fetchArtwork,
                modifier,
                targetWidthPx = 720,
                warmStartImages = item.posterArtworkCandidates(),
                revealDurationMillis = 360,
            )
        },
        logoArtwork = { item, modifier ->
            MediaArtwork(
                image = item.preferredLogo(),
                title = item.title,
                cache = artworkCache,
                fetch = fetchArtwork,
                modifier = modifier,
                contentScale = ContentScale.Fit,
                fallback = { fallback -> Box(fallback) },
                targetWidthPx = 480,
            )
        },
        personArtwork = { person, modifier ->
            LegacyPersonArtwork(image = person.image, name = person.name, cache = artworkCache, fetch = fetchArtwork, modifier = modifier)
        },
        onPersonSelected = navigator.openPerson,
        atmosphereArtwork = { item, radius, modifier ->
            MediaArtwork(
                images = item.legacyDetailBackdropArtworkCandidates(),
                title = item.title,
                cache = artworkCache,
                fetch = fetchArtwork,
                modifier = modifier,
                targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 240 else 960,
                bitmapContent = { bitmap, imageModifier -> CachedBlurredArtwork(bitmap, radius, imageModifier, cache = artworkCache) },
            )
        },
    )
}
