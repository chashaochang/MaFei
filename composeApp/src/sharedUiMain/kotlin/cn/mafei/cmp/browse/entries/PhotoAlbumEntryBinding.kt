package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.ui.LegacyPosterPlaceholder
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.session.*

@Composable
internal fun PhotoAlbumEntryBinding(
    route: BrowsePhotoAlbumRoute,
    fetchPhotoArtwork: suspend (MediaImage) -> ByteArray?,
    artworkCache: ArtworkMemoryCache,
    navigator: BrowseNavigator,
    navigation: BrowseNavigationState,
    photoStore: cn.mafei.cmp.data.repositories.PagedMediaStore<cn.mafei.cmp.data.adapter_contract.PhotoCatalogQuery>,
) {
    PhotoCatalogRouteContent(photoStore, route.albumId, route.title, library = false, onBack = navigator::pop,
        onPhoto = { item -> navigation.browseInitialItems[item.id.value] = item
            navigation.browseBackStack += BrowsePhotoViewerRoute(route.albumId, item.id.value, route.libraryId, route.origin) },
        onAlbum = { item -> navigation.browseBackStack += BrowsePhotoAlbumRoute(item.id.value, item.title, route.libraryId, route.origin) },
        artwork = { item, modifier -> MediaArtwork(item.primaryImage, item.title, artworkCache,
            { image, _ -> fetchPhotoArtwork(image) }, modifier,
            fallback = { LegacyPosterPlaceholder(MediaType.PhotoAlbum, it) }) })
}
