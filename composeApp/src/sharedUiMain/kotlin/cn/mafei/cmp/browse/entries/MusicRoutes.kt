package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.data.adapter_contract.MusicCatalogQuery
import cn.mafei.cmp.feature.music.*
import kotlinx.coroutines.launch

@Composable
internal fun MusicLibraryRouteContent(store: MusicCatalogStore, libraryId: String, title: String,
    layout: MaFeiLayout, onBack: (() -> Unit)?, onOpen: (MediaItem) -> Unit,
    onPlay: (List<MediaItem>, Int) -> Unit, artwork: @Composable (MediaItem, Modifier) -> Unit,
    currentId: String?, preparing: Boolean, playing: Boolean, bottomInset: Dp) {
    var section by rememberSaveable(libraryId) { mutableStateOf(MusicLibrarySection.Albums) }
    val query = remember(libraryId, section) {
        val library = CollectionId(libraryId)
        when (section) {
            MusicLibrarySection.Albums -> MusicCatalogQuery.Albums(library)
            MusicLibrarySection.Artists -> MusicCatalogQuery.Artists(library)
            MusicLibrarySection.Tracks -> MusicCatalogQuery.Tracks(library)
        }
    }
    val pages by store.pages.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(store, query) { store.load(query) }
    MusicLibraryScreen(title, section, pages[query] ?: MusicPageState(loading = true), layout,
        onSection = { section = it }, onBack = onBack,
        onRetry = { scope.launch { store.load(query, refresh = true) } },
        onMore = { scope.launch { store.load(query, more = true) } }, onOpen = onOpen, onPlay = onPlay,
        artwork = artwork, currentId = currentId, preparing = preparing, playing = playing, bottomInset = bottomInset)
}

@Composable
internal fun MusicDetailRouteContent(store: MusicCatalogStore, id: String, title: String, artist: Boolean,
    onBack: () -> Unit, onAlbum: (MediaItem) -> Unit,
    onPlay: (List<MediaItem>, Int) -> Unit, onFavorite: (suspend (MediaItem) -> Unit)?,
    artwork: @Composable (MediaItem, Modifier) -> Unit, currentId: String?, preparing: Boolean,
    playing: Boolean, bottomInset: Dp = 0.dp) {
    val mediaId = remember(id) { MediaId(id) }
    val trackQuery = remember(mediaId, artist) { if (artist) MusicCatalogQuery.ArtistPopularTracks(mediaId)
        else MusicCatalogQuery.AlbumTracks(mediaId) }
    val albumQuery = remember(mediaId) { MusicCatalogQuery.ArtistAlbums(mediaId) }
    val pages by store.pages.collectAsState()
    val details by store.details.collectAsState()
    val scope = rememberCoroutineScope()
    var favoriteBusy by remember(id) { mutableStateOf(false) }
    var favoriteError by remember(id) { mutableStateOf(false) }
    LaunchedEffect(store, mediaId, artist) {
        launch { store.detail(mediaId) }
        launch { store.load(trackQuery) }
        if (artist) launch { store.load(albumQuery) }
    }
    val detail = details[mediaId] ?: MusicDetailState(loading = true)
    val detailItem = detail.item
    MusicDetailScreen(title, artist, detail, pages[trackQuery] ?: MusicPageState(loading = true),
        albums = pages[albumQuery] ?: MusicPageState(loading = artist), onBack = onBack,
        onRetry = {
            scope.launch { store.detail(mediaId, refresh = true) }
            scope.launch { store.load(trackQuery, refresh = true) }
            if (artist) scope.launch { store.load(albumQuery, refresh = true) }
        }, onMoreTracks = { scope.launch { store.load(trackQuery, more = true) } },
        onMoreAlbums = { scope.launch { store.load(albumQuery, more = true) } }, onAlbum = onAlbum,
        onPlay = onPlay,
        onFavorite = if (!artist && onFavorite != null && detailItem != null) {{
            if (!favoriteBusy) {
                favoriteBusy = true
                scope.launch {
                    try { onFavorite(detailItem); favoriteError = false }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { favoriteError = true }
                    finally { favoriteBusy = false }
                }
            }
        }} else null,
        favoriteBusy = favoriteBusy, artwork = artwork, currentId = currentId, preparing = preparing,
        playing = playing, bottomInset = bottomInset)
    cn.mafei.cmp.common.ui.LegacyTransientToast(if (favoriteError) "收藏失败，请重试" else null)
}
