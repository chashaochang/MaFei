package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiLayoutFamily
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.media.landscapeArtworkCandidates
import cn.mafei.cmp.common.media.posterArtworkCandidates
import cn.mafei.cmp.common.media.prefetchLandscapeArtwork
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.feature.home.HomeHeroBackdropState
import cn.mafei.cmp.feature.home.HomeScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.legacyHomeHeroDetailTarget
import cn.mafei.cmp.feature.home.legacyHomeLatestMediaName
import cn.mafei.cmp.session.*
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.launch
import cn.mafei.cmp.common.design_system.MaFeiLayout

@Composable
internal fun HomeEntryBinding(
    sharedArtworkModifier: @Composable (MediaItem, Modifier) -> Modifier,
    authenticationFailure: cn.mafei.cmp.core.domain.AuthFailureKind?,
    selectDestination: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    browser: MediaBrowser,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    layout: MaFeiLayout,
    player: BrowsePlayerState,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    navigator: BrowseNavigator,
    navigation: BrowseNavigationState,
    homeGridState: LazyGridState,
    heroBackdrop: HomeHeroBackdropState,
    homeCarouselVisible: State<Boolean>,
    musicMiniPlayerMedia: MediaItem?,
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
    HomeScreen(
        collections = state.collections,
        items = state.items,
        homeRecommendations = state.homeFeed.recommendations,
        homeResume = state.homeFeed.resume,
        homeNextUp = state.homeFeed.nextUp,
        homeLatest = state.homeFeed.latest,
        homeLatestByCollection = state.homeLatestByCollection,
        homeLatestLoading = state.homeLatestLoading,
        homeLatestErrors = state.homeLatestErrors,
        selectedCollection = state.selectedCollection,
        loading = state.loading,
        homeLoading = state.homeLoading,
        refreshing = state.refreshing,
        loadingMore = state.loadingMore,
        error = state.homeError ?: state.error,
        authenticationRequired = authenticationFailure != null,
        stale = state.stale || state.homeStale,
        canLoadMore = state.nextCursor != null,
        overview = true,
        showHomeHero = homeCarouselVisible.value,
        layout = layout,
        onCollection = { collection -> scope.launch { browser.loadHomeLatest(collection.id) } },
        onOpenCollectionLatest = { collection ->
            val route = BrowseLatestRoute(collection.id.value)
            if (navigation.browseBackStack.lastOrNull() != route) navigation.browseBackStack += route
        },
        onItem = { item ->
            navigator.openDetail(item, item.legacyHomeLatestMediaName(state.collections))
        },
        onHeroItem = { item ->
            val target = item.legacyHomeHeroDetailTarget()
            navigator.openDetailId(
                target.mediaId,
                target.mediaName,
                item.takeIf { it.id == target.mediaId },
            )
        },
        onPlay = player.startPlayback,
        onToggleWatched = if (browser.capabilities.watchedEdit) {
            { item -> scope.launch { browser.toggleWatched(item) } }
        } else null,
        onToggleFavorite = if (browser.capabilities.favoritesEdit) {
            { item -> scope.launch { browser.toggleFavorite(item) } }
        } else null,
        onRetry = { scope.launch { browser.refresh() } },
        onLoadMore = { scope.launch { browser.loadMore() } },
        // The library shelf still opens the library index. The
        // separate home latest shelf uses its own list route.
        onOpenLibrary = { selectDestination(LibraryDestination) },
        onOpenLatest = navigator::openLatest,
        onSearch = navigator::openSearch,
        bottomOverlayInset = if (musicMiniPlayerMedia != null) 76.dp else 0.dp,
        modifier = Modifier.fillMaxSize(),
        gridState = homeGridState,
        artwork = { item, modifier ->
            MediaArtwork(
                item.posterArtworkCandidates(),
                item.title,
                artworkCache,
                fetchArtwork,
                modifier,
                // Wide home cards are 140dp. On the
                // 2.5x MatePad canvas that is about
                // 350px, so decoding 480px posters
                // only adds allocation pressure while
                // the shelf is entering the viewport.
                targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 480 else 360,
            )
        },
        sharedArtworkModifier = sharedArtworkModifier,
        backdropArtwork = { item, modifier ->
            MediaArtwork(
                item.landscapeArtworkCandidates(),
                item.title,
                artworkCache,
                fetchArtwork,
                modifier,
                targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 720 else 960,
            )
        },
        prefetchBackdrop = if (layout.family == MaFeiLayoutFamily.Phone) {
            null
        } else {
            { item ->
                prefetchLandscapeArtwork(
                    item,
                    artworkCache,
                    fetchArtwork,
                    targetWidthPx = 960,
                )
            }
        },
        heroBackdropState = heroBackdrop,
        shelfArtwork = { item, modifier ->
            MediaArtwork(
                item.landscapeArtworkCandidates(),
                item.title,
                artworkCache,
                fetchArtwork,
                modifier,
                targetWidthPx = 480,
            )
        },
    )
}
