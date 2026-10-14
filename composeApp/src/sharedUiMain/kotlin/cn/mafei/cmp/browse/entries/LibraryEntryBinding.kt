package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.isPhoneNavigation
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.ui.LegacyPosterPlaceholder
import cn.mafei.cmp.common.ui.LegacySortChoice
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.SortDirection
import cn.mafei.cmp.feature.music.MusicCatalogStore
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.PlaybackQueue
import cn.mafei.cmp.feature.media.VideoListScreen
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.session.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import cn.mafei.cmp.common.design_system.MaFeiLayout

@Composable
internal fun LibraryEntryBinding(
    route: BrowseLibraryRoute,
    showingFullPageRoute: Boolean,
    playerShellState: PlayerShellState,
    fetchPhotoArtwork: suspend (MediaImage) -> ByteArray?,
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
    libraryGridState: LazyGridState,
    musicMiniPlayerMedia: MediaItem?,
    librarySortChoices: List<LegacySortChoice<MediaSort>>,
    musicStore: MusicCatalogStore,
    liveStore: cn.mafei.cmp.data.repositories.PagedMediaStore<Unit>,
    photoStore: cn.mafei.cmp.data.repositories.PagedMediaStore<cn.mafei.cmp.data.adapter_contract.PhotoCatalogQuery>,
) {
    val collection = state.collections.firstOrNull { it.id.value == route.collectionId }
    if (browser.capabilities.liveTv && collection?.mediaTypes?.contains(MediaType.LiveChannel) == true) {
        val pages by liveStore.pages.collectAsState()
        LaunchedEffect(liveStore) { liveStore.load(Unit) }
        cn.mafei.cmp.feature.livetv.LiveChannelScreen(collection.name,
            pages[Unit] ?: cn.mafei.cmp.data.repositories.MediaPageState(loading = true),
            onBack = navigator::pop.takeIf { layout.isPhoneNavigation },
            onRetry = { scope.launch { liveStore.load(Unit, refresh = true) } },
            onMore = { scope.launch { liveStore.load(Unit, more = true) } },
            onChannel = { channels, index ->
                channels.getOrNull(index)?.let { player.startPlaybackItem(it, PlaybackQueue(channels, index), 0L) }
            }, artwork = { item, modifier -> MediaArtwork(item.primaryImage, item.title, artworkCache, fetchArtwork,
                modifier, fallback = { LegacyPosterPlaceholder(item.mediaType, it) }) })
    } else if (browser.capabilities.photos && collection?.mediaTypes?.contains(MediaType.Photo) == true) {
        PhotoCatalogRouteContent(photoStore, route.collectionId, collection.name, library = true,
            onBack = navigator::pop.takeIf { layout.isPhoneNavigation },
            onPhoto = { item -> navigation.browseInitialItems[item.id.value] = item
                navigation.browseBackStack += BrowsePhotoViewerRoute(route.collectionId, item.id.value, route.collectionId, BrowseDetailOrigin.Library) },
            onAlbum = { item -> navigation.browseBackStack += BrowsePhotoAlbumRoute(item.id.value, item.title, route.collectionId, BrowseDetailOrigin.Library) },
            artwork = { item, modifier -> MediaArtwork(item.primaryImage, item.title, artworkCache,
                { image, _ -> fetchPhotoArtwork(image) }, modifier,
                fallback = { LegacyPosterPlaceholder(MediaType.PhotoAlbum, it) }) })
    } else if (browser.capabilities.music && collection?.mediaTypes?.any { it in setOf(MediaType.Audio, MediaType.MusicAlbum, MediaType.MusicArtist) } == true) {
        MusicLibraryRouteContent(musicStore, route.collectionId, collection.name, layout,
            onBack = navigator::pop.takeIf { layout.isPhoneNavigation },
            onOpen = { item -> navigator.openDetail(item, collection.name, libraryId = collection.id.value) },
            onPlay = player.playMusicQueue,
            artwork = { item, modifier -> MediaArtwork(item.preferredPoster(), item.title, artworkCache, fetchArtwork,
                modifier, fallback = { LegacyPosterPlaceholder(item.mediaType, it) }) },
            currentId = player.playingMedia?.id?.value, preparing = player.playbackBusy,
            playing = playerShellState.phase == PlayerPhase.Playing,
            bottomInset = if (musicMiniPlayerMedia != null) 76.dp else 0.dp)
    } else {
    VideoListScreen(
        title = state.collections.firstOrNull { it.id.value == route.collectionId }?.name.orEmpty(),
        items = state.items,
        selectedSort = state.librarySort,
        sortChoices = librarySortChoices,
        loading = state.loading,
        refreshing = state.refreshing,
        loadingMore = state.loadingMore,
        error = state.error,
        canLoadMore = state.nextCursor != null,                                    layout = layout,
        gridState = libraryGridState,
        onBack = navigator::pop.takeIf { layout.isPhoneNavigation },
        onSortSelected = { sort ->
            scope.launch { browser.setLibrarySort(sort) }
        },
        onToggleSortDirection = {
            val direction = if (state.librarySort.direction == SortDirection.Descending) {
                SortDirection.Ascending
            } else {
                SortDirection.Descending
            }
            scope.launch {
                browser.setLibrarySort(state.librarySort.copy(direction = direction))
            }
        },
        onItem = { item ->
            navigator.openDetail(
                item,
                state.collections.firstOrNull { it.id.value == route.collectionId }?.name,
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
        canDeleteMedia = state.deleteAvailable == true,
        deleteBusy = state.deleteBusy,
        selectionMode = selection.librarySelectionMode,
        selectedIds = selection.librarySelectedIds,
        onEnterSelection = { item -> selection.librarySelectedIds = setOf(item.id.value) },
        onToggleSelected = { item ->
            selection.librarySelectedIds = if (item.id.value in selection.librarySelectedIds) {
                selection.librarySelectedIds - item.id.value
            } else {
                selection.librarySelectedIds + item.id.value
            }
        },
        onExitSelection = { selection.librarySelectedIds = emptySet() },
        onDeleteSelected = {
            if (selection.librarySelectedIds.isNotEmpty()) {
                selection.pendingDeleteMedia = selection.librarySelectedIds.map { MediaId(it) }
            }
        },
        bottomOverlayInset = if (musicMiniPlayerMedia != null) 76.dp else 0.dp,
        bottomNavigationVisible = !showingFullPageRoute &&
            layout.isPhoneNavigation,
        modifier = Modifier.fillMaxSize(),
        artwork = { item, modifier ->
            MediaArtwork(
                item.preferredPoster(),
                item.title,
                artworkCache,
                fetchArtwork,
                modifier,
                fallback = { fallbackModifier ->
                    LegacyPosterPlaceholder(item.mediaType, fallbackModifier)
                },
            )
        },
    )
    }
}
