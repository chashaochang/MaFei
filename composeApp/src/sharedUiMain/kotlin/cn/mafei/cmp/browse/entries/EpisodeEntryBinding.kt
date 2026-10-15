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
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.feature.detail.DetailScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.*
import kotlinx.coroutines.launch

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
internal fun EpisodeEntryBinding(
    route: BrowseEpisodeRoute,
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
    val initialEpisode = navigation.browseInitialItems[route.episodeId]
    LaunchedEffect(route.episodeId, initialEpisode) {
        if (state.detailId?.value != route.episodeId) {
            browser.openDetail(MediaId(route.episodeId), initialEpisode)
        }
    }
    val episode = state.detail?.takeIf { it.id.value == route.episodeId } ?: initialEpisode
    DetailScreen(
        item = episode,
        // Once the route has an initial row, keep it
        // visible while the full detail refreshes.
        // A loading state is only a blocking state
        // when neither the row nor the detail is
        // available.
        loading = episode == null && state.detailLoading,
        error = state.detailError,
        onBack = navigator::pop,
        onRetry = { scope.launch { browser.openDetail(MediaId(route.episodeId), initialEpisode) } },
        onPlay = episode?.takeIf { playback != null }?.let { item -> { player.startPlayback(item) } },
        playTarget = episode,
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
        actionsBusy = state.detailActionBusy,
        actionsError = state.detailActionError,
        playbackBusy = player.playbackBusy,
        playbackError = player.playbackError,
        libraryName = route.mediaName,
        modifier = Modifier.fillMaxSize(),
        artwork = { item, modifier ->
            MediaArtwork(item.legacyPrimaryThenBackdropArtworkCandidates(), item.title, artworkCache, fetchArtwork, modifier, targetWidthPx = 480)
        },
        sharedArtworkModifier = sharedArtworkModifier,
        // The episode Hero must use the same primary
        // then backdrop chain as the season episode
        // cards.  Using the detail-backdrop chain
        // here caused a visible image swap on entry.
        backdropArtwork = { item, modifier ->
            MediaArtwork(
                item.legacyPrimaryThenBackdropArtworkCandidates(),
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
