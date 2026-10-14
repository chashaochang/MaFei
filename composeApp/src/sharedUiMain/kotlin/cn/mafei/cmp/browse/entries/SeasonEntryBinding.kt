package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import cn.mafei.cmp.common.design_system.MaFeiLayoutFamily
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.CachedBlurredArtwork
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.media.legacyDetailBackdropArtworkCandidates
import cn.mafei.cmp.common.media.legacyPrimaryArtworkCandidates
import cn.mafei.cmp.common.media.legacyPrimaryThenBackdropArtworkCandidates
import cn.mafei.cmp.common.media.posterArtworkCandidates
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.feature.detail.DetailScreen
import cn.mafei.cmp.feature.detail.SeasonDetailScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.*
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.launch
import cn.mafei.cmp.common.design_system.MaFeiLayout

@Composable
internal fun SeasonEntryBinding(
    route: BrowseSeasonRoute,
    playback: SessionPlaybackRuntime?,
    sharedArtworkModifier: @Composable (MediaItem, Modifier) -> Modifier,
    scope: kotlinx.coroutines.CoroutineScope,
    browser: MediaBrowser,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    layout: MaFeiLayout,
    player: BrowsePlayerState,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    navigator: BrowseNavigator,
    navigation: BrowseNavigationState,
) {
    val sharedArtworkModifier: @Composable (MediaItem, Modifier) -> Modifier = { item, modifier ->
        with(sharedTransitionScope) {
            modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(
                    key = "media-artwork:${item.id.value}",
                ),
                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                enter = fadeIn(tween(320)),
                exit = fadeOut(tween(320)),
                boundsTransform = { _, _ -> tween(420) },
                zIndexInOverlay = 1f,
            )
        }
    }
    // The ArkUI season page owns its own ViewModel. Our
    // provider-neutral browser has one detail slot, so an
    // episode page temporarily replaces the series in that
    // slot. Make the route the source of truth: entering or
    // returning to this destination restores the exact
    // series + season before rendering it.
    LaunchedEffect(route.seriesId, route.seasonId) {
        if (state.detail?.id?.value != route.seriesId) {
            browser.openDetail(MediaId(route.seriesId))
        }
        if (state.selectedSeasonId?.value != route.seasonId ||
            state.selectedSeasonDetail?.id?.value != route.seasonId
        ) {
            browser.selectSeason(MediaId(route.seasonId))
        }
    }
    val series = state.detail?.takeIf { it.id.value == route.seriesId }
    val seasonSummary = state.detailSeasons.firstOrNull { it.id.value == route.seasonId }
    val season = state.selectedSeasonDetail
        ?.takeIf { it.id.value == route.seasonId }
        ?: seasonSummary
    if (series == null || season == null) {
        DetailScreen(
            item = null,
            loading = state.detailLoading || state.detailSectionsLoading ||
                (state.detailError == null && state.detailSectionsError == null),
            error = state.detailError ?: state.detailSectionsError,
            onBack = navigator::pop,
            onRetry = {
                scope.launch {
                    browser.openDetail(MediaId(route.seriesId))
                    browser.selectSeason(MediaId(route.seasonId))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        // The legacy season page always exposes playFirst(): it resolves
        // progress/unwatched/first from the episode queue and lets playback
        // preparation decide whether the target can actually be opened.
        // Display-profile episode rows may not carry canPlay, so filtering it
        // here leaves a visible but empty action capsule.
        val playTarget = state.selectedSeasonPlayTarget
            ?: state.detailEpisodes.firstOrNull()
        SeasonDetailScreen(
            series = series,
            season = season,
            seasons = state.detailSeasons,
            episodes = state.detailEpisodes,
            loading = state.detailSectionsLoading,
            onBack = navigator::pop,
            onSeasonSelected = { nextSeason ->
                val lastIndex = navigation.browseBackStack.lastIndex
                if (lastIndex >= 0) {
                    navigation.browseBackStack[lastIndex] = BrowseSeasonRoute(
                        seriesId = route.seriesId,
                        seasonId = nextSeason.id.value,
                        mediaName = route.mediaName,
                        origin = route.origin,
                        libraryId = route.libraryId,
                    )
                }
            },
            onEpisodeSelected = { episode ->
                navigation.browseInitialItems[episode.id.value] = episode
                navigation.browseBackStack += BrowseEpisodeRoute(
                    seriesId = route.seriesId,
                    seasonId = route.seasonId,
                    episodeId = episode.id.value,
                    mediaName = route.mediaName,
                    origin = route.origin,
                    libraryId = route.libraryId,
                )
            },
            onPlayFirst = playTarget?.takeIf { playback != null }?.let { item ->
                { player.startPlayback(item) }
            },
            playTarget = playTarget,
            onTogglePlayed = if (browser.capabilities.watchedEdit) {
                { scope.launch { browser.toggleWatched(season) } }
            } else null,
            playbackBusy = player.playbackBusy,
            actionsBusy = state.detailActionBusy,
            actionsError = state.detailActionError,
            libraryName = route.mediaName,
            modifier = Modifier.fillMaxSize(),
            posterArtwork = { item, modifier ->
                MediaArtwork(
                    item.legacyPrimaryArtworkCandidates(),
                    item.title,
                    artworkCache,
                    fetchArtwork,
                    modifier,
                    targetWidthPx = 480,
                )
            },
            landscapeArtwork = { item, modifier ->
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
            sharedArtworkModifier = sharedArtworkModifier,
            episodeArtwork = { item, modifier ->
                MediaArtwork(
                    item.legacyPrimaryThenBackdropArtworkCandidates(),
                    item.title,
                    artworkCache,
                    fetchArtwork,
                    modifier,
                    targetWidthPx = 720,
                )
            },
            personArtwork = { person, modifier ->
                LegacyPersonArtwork(
                    image = person.image,
                    name = person.name,
                    cache = artworkCache,
                    fetch = fetchArtwork,
                    modifier = modifier,
                )
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
                    bitmapContent = { bitmap, imageModifier ->
                        CachedBlurredArtwork(bitmap, radius, imageModifier, cache = artworkCache)
                    },
                )
            },
        )
    }
}
