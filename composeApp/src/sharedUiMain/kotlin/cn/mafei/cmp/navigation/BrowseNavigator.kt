package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.legacyDetailBackdropArtworkCandidates
import cn.mafei.cmp.common.media.prefetchArtwork
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Stateless commands over a session-owned stack. No page or window owns the stack. */
internal class BrowseNavigator(
    private val browser: MediaBrowser,
    private val navigation: BrowseNavigationState,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val search: BrowseSearchState,
    private val shellContext: BrowseShellContext,
    private val artworkCache: ArtworkMemoryCache,
    private val fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    private val phoneLayout: Boolean,
) {
    fun openDetailId(
        id: MediaId,
        mediaName: String?,
        initialItem: MediaItem? = null,
        origin: BrowseDetailOrigin = shellContext.origin ?: BrowseDetailOrigin.Home,
        libraryId: String? = shellContext.libraryId,
    ) {
        if (navigation.browseBackStack.lastOrNull() !is BrowseDetailRoute) {
            navigation.browseBackStack += BrowseDetailRoute(mediaId = id.value, mediaName = mediaName, origin = origin, libraryId = libraryId)
        }
        // Start the landscape request as soon as a home card is
        // tapped. The portrait card remains the shared-transition
        // first frame; the detail Hero can then fade in the real
        // Backdrop without waiting for the detail JSON round trip.
        initialItem?.let { tappedItem ->
            scope.launch {
                prefetchArtwork(tappedItem.legacyDetailBackdropArtworkCandidates(), artworkCache, fetchArtwork, targetWidthPx = if (phoneLayout) 720 else 960)
            }
        }
        scope.launch { browser.openDetail(id, initialItem) }
    }

    fun openDetail(
        item: MediaItem,
        mediaName: String?,
        origin: BrowseDetailOrigin = shellContext.origin ?: BrowseDetailOrigin.Home,
        libraryId: String? = shellContext.libraryId,
    ) {
        if (browser.capabilities.music && (item.mediaType == MediaType.MusicAlbum || item.mediaType == MediaType.MusicArtist)) {
            navigation.browseBackStack += BrowseMusicDetailRoute(item.id.value, item.title, item.mediaType == MediaType.MusicArtist, libraryId, origin)
        } else if (browser.capabilities.photos && item.mediaType == MediaType.PhotoAlbum) {
            navigation.browseBackStack += BrowsePhotoAlbumRoute(item.id.value, item.title, libraryId, origin)
        } else if (browser.capabilities.photos && item.mediaType == MediaType.Photo) {
            navigation.browseInitialItems[item.id.value] = item
            navigation.browseBackStack += BrowsePhotoViewerRoute(item.parentId?.value ?: libraryId, item.id.value, libraryId, origin)
        } else openDetailId(item.id, mediaName, item, origin, libraryId)
    }

    val openPerson: (cn.mafei.cmp.core.domain.PersonRef) -> Unit = { person ->
        person.id?.let { id ->
            val route =
                BrowsePersonRoute(
                    personId = id.value,
                    personName = person.name,
                    origin = shellContext.origin ?: BrowseDetailOrigin.Home,
                    libraryId = shellContext.libraryId,
                )
            if (navigation.browseBackStack.lastOrNull() != route) {
                navigation.browseBackStack += route
                scope.launch { browser.openPerson(id, person.name) }
            }
        }
    }
    val browseCollection: (cn.mafei.cmp.core.domain.MediaCollection) -> Unit = { collection ->
        if (collection.id != browser.state.value.selectedCollection) {
            scope.launch { browser.select(collection.id) }
        }
        if (navigation.browseBackStack.lastOrNull() != BrowseLibraryRoute(collection.id.value)) {
            navigation.browseBackStack += BrowseLibraryRoute(collection.id.value)
        }
    }

    fun openSearch() {
        if (navigation.browseBackStack.lastOrNull() != BrowseSearchRoute) {
            browser.clearSearch()
            navigation.browseBackStack += BrowseSearchRoute
        }
    }

    fun openLatest() {
        val route = BrowseLatestRoute()
        if (navigation.browseBackStack.lastOrNull() != route) {
            navigation.browseBackStack += route
        }
    }

    fun pop() {
        val route = navigation.browseBackStack.lastOrNull()
        val revealed = navigation.browseBackStack.getOrNull(navigation.browseBackStack.lastIndex - 1)
        val deferDetailClear = route is BrowseDetailRoute
        // Restore the season document before removing the episode route so
        // Navigation 3 never renders an intermediate full-page loading
        // state during the pop transition.
        val restoredSeasonContext =
            if (route is BrowseEpisodeRoute && revealed is BrowseSeasonRoute) {
                browser.restoreSeasonContext(seriesId = MediaId(revealed.seriesId), seasonId = MediaId(revealed.seasonId))
            } else {
                false
            }
        when (route) {
            // Keep the detail document composed while NavDisplay runs the
            // pop transition so the shared-element source remains alive.
            is BrowseDetailRoute -> Unit
            is BrowsePersonRoute -> browser.clearPerson()
            BrowseSearchRoute -> {
                search.cancel()
                browser.clearSearch()
            }
            else -> Unit
        }
        if (navigation.browseBackStack.size > 1) {
            navigation.browseBackStack.removeLastOrNull()
            if (deferDetailClear) {
                val expectedStack = navigation.browseBackStack.toList()
                scope.launch {
                    delay(BrowseSharedReturnDurationMs)
                    if (navigation.browseBackStack == expectedStack) browser.back()
                }
            }
            when (revealed) {
                BrowseHomeRoute -> scope.launch { browser.refreshHome() }
                is BrowseDetailRoute ->
                    scope.launch {
                        val id = MediaId(revealed.mediaId)
                        if (browser.state.value.detail?.id == id) browser.refreshDetail(id) else browser.openDetail(id)
                    }
                is BrowseEpisodeRoute ->
                    scope.launch {
                        val id = MediaId(revealed.episodeId)
                        if (browser.state.value.detail?.id == id) browser.refreshDetail(id) else browser.openDetail(id)
                    }
                is BrowseSeasonRoute ->
                    if (!restoredSeasonContext)
                        scope.launch {
                            val seriesId = MediaId(revealed.seriesId)
                            val seasonId = MediaId(revealed.seasonId)
                            // Episode details temporarily use the provider detail
                            // slot. If no in-memory snapshot is available, use the
                            // existing sequential restore as a cold fallback.
                            if (!browser.restoreSeasonContext(seriesId, seasonId)) {
                                val current = browser.state.value
                                val requiresRestore = current.detail?.id != seriesId || current.selectedSeasonId != seasonId
                                if (current.detail?.id != seriesId) browser.openDetail(seriesId)
                                if (browser.state.value.selectedSeasonId != seasonId) {
                                    browser.selectSeason(seasonId)
                                }
                                if (!requiresRestore) browser.refreshDetail(seriesId)
                            }
                        }
                else -> Unit
            }
        }
    }
}

internal const val BrowseSharedReturnDurationMs = 460L
