package cn.mafei.cmp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.PhotoCatalogQuery
import cn.mafei.cmp.data.repositories.*
import cn.mafei.cmp.feature.photo.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@Composable
internal fun PhotoCatalogRouteContent(store: PagedMediaStore<PhotoCatalogQuery>, parentId: String, title: String,
    library: Boolean, onBack: (() -> Unit)?, onPhoto: (MediaItem) -> Unit, onAlbum: (MediaItem) -> Unit,
    artwork: @Composable (MediaItem, Modifier) -> Unit) {
    var albums by rememberSaveable(parentId) { mutableStateOf(false) }
    val query = remember(parentId, albums) { PhotoCatalogQuery(MediaId(parentId), albums) }
    val pages by store.pages.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(store, query) { store.load(query) }
    PhotoCatalogScreen(title, albums, pages[query] ?: MediaPageState(loading = true), onBack,
        onSection = if (library) {{ albums = it }} else null,
        onRetry = { scope.launch { store.load(query, refresh = true) } },
        onMore = { scope.launch { store.load(query, more = true) } }, onPhoto = onPhoto, onAlbum = onAlbum, artwork = artwork)
}

@Composable
internal fun PhotoViewerRouteContent(store: PagedMediaStore<PhotoCatalogQuery>, parentId: String?, photoId: String,
    initialItem: MediaItem?, onBack: () -> Unit, loadItem: suspend (MediaId) -> MediaItem,
    onFavorite: suspend (MediaItem) -> Unit, onSave: (suspend (MediaItem) -> PhotoSaveResult)?,
    fetch: suspend (MediaImage, Int) -> ByteArray?) {
    val query = remember(parentId) { parentId?.let { PhotoCatalogQuery(MediaId(it)) } }
    val pages by store.pages.collectAsState()
    var single by remember(photoId) { mutableStateOf(initialItem) }
    var loadFailed by remember(photoId) { mutableStateOf(false) }
    LaunchedEffect(store, query, photoId) {
        if (query != null) store.load(query)
        if (single?.photo == null) {
            try { single = loadItem(MediaId(photoId)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { loadFailed = true }
        }
    }
    val source = query?.let { pages[it]?.items }.orEmpty()
    // Freeze the viewer's opening order. A catalog refresh must not jump the
    // pager to a different photo, and a tapped off-page photo is never lost.
    var opening by remember(photoId) { mutableStateOf<List<MediaItem>?>(null) }
    LaunchedEffect(photoId, source, single) {
        if (opening == null && (source.any { it.id.value == photoId } || single != null)) {
            opening = photoDateGroups(source).flatMap { it.second }.let { items ->
                if (items.any { it.id.value == photoId }) items else listOfNotNull(single)
            }
        }
    }
    val latest = source.associateBy { it.id }
    val photos = opening.orEmpty().map { latest[it.id] ?: single?.takeIf { one -> one.id == it.id } ?: it }
    if (photos.isEmpty()) {
        if (loadFailed) LegacyFailedView(onBack, Modifier.fillMaxSize()) else LegacyLoadingView(Modifier.fillMaxSize())
        return
    }
    val scope = rememberCoroutineScope()
    val cache = remember { ArtworkMemoryCache(maxWidthPx = 4096, maxEncodedBytes = 16 * 1024 * 1024,
        maxSingleBytes = 64L * 1024 * 1024, maxCacheBytes = 96L * 1024 * 1024) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<MediaItem?>(null) }
    var infoLoading by remember { mutableStateOf(false) }
    var infoFailed by remember { mutableStateOf(false) }
    var infoJob by remember { mutableStateOf<Job?>(null) }
    var infoTicket by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    PhotoViewerScreen(photos, photoId, onBack,
        onFavorite = { item -> if (!busy) {
            busy = true
            scope.launch {
                try {
                    onFavorite(item)
                    store.favoriteChanged(item.id, !item.userData.isFavorite)
                    single = single?.takeIf { it.id == item.id }?.copy(userData = item.userData.copy(isFavorite = !item.userData.isFavorite)) ?: single
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { toast = "收藏状态更新失败" }
                finally { busy = false }
            }
        } }, favoriteBusy = busy,
        onSave = onSave?.let { save -> { item -> if (!saving) {
            saving = true
            scope.launch {
                try { toast = when (save(item)) { PhotoSaveResult.Saved -> "已保存到相册"; PhotoSaveResult.Cancelled -> null; PhotoSaveResult.Failed -> "保存失败，请重试" } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { toast = "保存失败，请重试" }
                finally { saving = false }
            }
        } } }, saving = saving, info = info, infoLoading = infoLoading, infoFailed = infoFailed,
        onInfo = { item ->
            val ticket = ++infoTicket
            infoJob?.cancel()
            info = null; infoLoading = true; infoFailed = false
            infoJob = scope.launch {
                try { val loaded = loadItem(item.id); if (ticket == infoTicket) info = loaded }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (ticket == infoTicket) infoFailed = true }
                finally { if (ticket == infoTicket) infoLoading = false }
            }
        }, artwork = { item, modifier -> MediaArtwork(item.photo?.fullImage ?: item.primaryImage, item.title, cache, fetch,
            modifier, contentScale = ContentScale.Fit, targetWidthPx = 4096,
            fallback = { LegacyPosterPlaceholder(MediaType.PhotoAlbum, it) }) })
    LegacyTransientToast(toast)
}
