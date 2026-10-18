package cn.mafei.cmp

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.UriHandler
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.feature.chasing.ChasingBrowser
import cn.mafei.cmp.feature.chasing.ChasingSelection
import cn.mafei.cmp.feature.home.HomeHeroBackdropState
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.photo.PhotoSaver
import cn.mafei.cmp.session.ApplicationSession
import cn.mafei.cmp.session.SessionPlaybackRuntime
import kotlinx.coroutines.CoroutineScope

/** Route content reads only its tab's controller-owned projection, inside the retained entry. */
@Composable
internal fun BrowsePageEntry(
    route: BrowseRoute,
    browser: MediaBrowser,
    scope: CoroutineScope,
    layout: MaFeiLayout,
    player: BrowsePlayerState,
    playerShellState: PlayerShellState,
    navigator: BrowseNavigator,
    navigation: BrowseNavigationState,
    selection: BrowseSelectionState,
    selectDestination: (String) -> Unit,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    fetchPhotoArtwork: suspend (MediaImage) -> ByteArray?,
    homeGridState: LazyGridState,
    pageScrollStates: BrowsePageScrollStates,
    chasingListState: LazyListState,
    heroBackdrop: HomeHeroBackdropState,
    homeCarouselVisible: State<Boolean>,
    musicMiniPlayerMedia: MediaItem?,
    catalogs: BrowseCatalogs,
    search: BrowseSearchState,
    chasingBrowser: ChasingBrowser,
    chasingSelection: ChasingSelection,
    chasingEnabled: Boolean,
    playback: SessionPlaybackRuntime?,
    uriHandler: UriHandler,
    session: ApplicationSession,
    repository: MediaRepository,
    photoSaver: PhotoSaver,
) {
    val authenticationFailure by browser.authenticationFailure.collectAsState()
    val chasingState by chasingBrowser.state.collectAsState()
    val chasingSelectionState by chasingSelection.state.collectAsState()
    val musicStore = catalogs.music
    val liveStore = catalogs.live
    val photoStore = catalogs.photos
    val latestStore = catalogs.latest
    val sharedArtworkModifier = LocalBrowseArtworkTransition.current
    val root = LocalBrowseRoot.current
    val state by remember(browser, root) { browser.stateForTab(root.toString()) }.collectAsState()
    val librarySortChoices = legacySortChoices(session.connection.server.provider, state.librarySort.direction)
    val favoriteSortChoices = legacySortChoices(session.connection.server.provider, state.favoriteSort.direction)
    when (route) {
        BrowseHomeRoute ->
            HomeEntryBinding(
                sharedArtworkModifier,
                authenticationFailure,
                selectDestination,
                scope,
                browser,
                state,
                layout,
                player,
                artworkCache,
                fetchArtwork,
                navigator,
                navigation,
                homeGridState,
                heroBackdrop,
                homeCarouselVisible,
                musicMiniPlayerMedia,
            )
        is BrowseLatestRoute ->
            LatestEntryBinding(
                route,
                route.shellPresentation().fullPage,
                scope,
                browser,
                state,
                layout,
                player,
                artworkCache,
                fetchArtwork,
                navigator,
                pageScrollStates.grid(root, route),
                musicMiniPlayerMedia,
                latestStore,
            )
        BrowseLibraryIndexRoute ->
            LibraryIndexEntryBinding(
                scope,
                browser,
                state,
                layout,
                navigator,
                navigation,
                chasingEnabled,
                musicMiniPlayerMedia,
            )
        BrowseChasingRoute ->
            ChasingEntryBinding(
                scope,
                state,
                layout,
                player,
                artworkCache,
                fetchArtwork,
                navigator,
                chasingBrowser,
                chasingState,
                chasingListState,
                musicMiniPlayerMedia,
            )
        BrowseFavoriteRoute ->
            FavoriteEntryBinding(
                scope,
                browser,
                state,
                layout,
                player,
                artworkCache,
                fetchArtwork,
                navigator,
                pageScrollStates.grid(root, route),
                musicMiniPlayerMedia,
                favoriteSortChoices,
            )
        BrowseSearchRoute ->
            SearchEntryBinding(
                scope,
                browser,
                state,
                layout,
                player,
                artworkCache,
                fetchArtwork,
                navigator,
                search,
                pageScrollStates.grid(root, route),
            )
        is BrowseLibraryRoute ->
            LibraryEntryBinding(
                route,
                route.shellPresentation().fullPage,
                playerShellState,
                fetchPhotoArtwork,
                scope,
                browser,
                state,
                layout,
                player,
                artworkCache,
                fetchArtwork,
                navigator,
                navigation,
                selection,
                pageScrollStates.grid(root, route),
                musicMiniPlayerMedia,
                librarySortChoices,
                musicStore,
                liveStore,
                photoStore,
            )
        is BrowseMusicDetailRoute ->
            MusicDetailEntryBinding(
                route,
                playerShellState,
                browser,
                player,
                artworkCache,
                fetchArtwork,
                navigator,
                navigation,
                musicMiniPlayerMedia,
                musicStore,
                repository,
                session.media,
            )
        is BrowsePhotoAlbumRoute ->
            PhotoAlbumEntryBinding(
                route,
                fetchPhotoArtwork,
                artworkCache,
                navigator,
                navigation,
                photoStore,
            )
        is BrowsePhotoViewerRoute ->
            PhotoViewerEntryBinding(
                route,
                photoSaver,
                fetchPhotoArtwork,
                session,
                navigator,
                navigation,
                photoStore,
                repository,
                session.media,
            )
        is BrowseDetailRoute ->
            DetailEntryBinding(
                route,
                playback,
                uriHandler,
                sharedArtworkModifier,
                scope,
                browser,
                state,
                layout,
                player,
                artworkCache,
                fetchArtwork,
                navigator,
                navigation,
                selection,
                chasingSelection,
                chasingSelectionState,
                chasingEnabled,
            )
        is BrowsePersonRoute ->
             PersonEntryBinding(route, scope, browser, state, artworkCache, fetchArtwork, navigator)
        is BrowseSeasonRoute ->
            SeasonEntryBinding(
                route,
                playback,
                sharedArtworkModifier,
                scope,
                browser,
                state,
                layout,
                player,
                artworkCache,
                fetchArtwork,
                navigator,
                navigation,
            )
        is BrowseEpisodeRoute ->
            EpisodeEntryBinding(
                route,
                playback,
                uriHandler,
                sharedArtworkModifier,
                scope,
                browser,
                state,
                layout,
                player,
                artworkCache,
                fetchArtwork,
                navigator,
                navigation,
                selection,
            )
        // 「我的」的分支已搬到它自己的导航容器（mineNavContent）。这里兜底。
        else -> Unit
    }
}
