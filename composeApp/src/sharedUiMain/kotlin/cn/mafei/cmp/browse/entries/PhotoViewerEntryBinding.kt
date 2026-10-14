package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.session.*

@Composable
internal fun PhotoViewerEntryBinding(
    route: BrowsePhotoViewerRoute,
    photoSaver: cn.mafei.cmp.feature.photo.PhotoSaver,
    fetchPhotoArtwork: suspend (MediaImage) -> ByteArray?,
    session: ApplicationSession,
    navigator: BrowseNavigator,
    navigation: BrowseNavigationState,
    photoStore: cn.mafei.cmp.data.repositories.PagedMediaStore<cn.mafei.cmp.data.adapter_contract.PhotoCatalogQuery>,
    repository: cn.mafei.cmp.data.repositories.MediaRepository,
    mediaSession: cn.mafei.cmp.data.repositories.MediaSession,
) {
    PhotoViewerRouteContent(
        photoStore,
        route.parentId,
        route.photoId,
        navigation.browseInitialItems[route.photoId],
        navigator::pop,
        loadItem = { id -> repository.item(mediaSession, id).value },
        onFavorite = { item -> repository.setFavorite(mediaSession, item.id, !item.userData.isFavorite) },
        onSave =
            if (photoSaver.supported) {
                { item -> photoSaver.save(repository.photoOriginal(mediaSession, item.id)) }
            } else null,
        fetch = { image, _ -> fetchPhotoArtwork(image) },
        artworkNamespace = "${session.connection.credentialKey}/photos",
    )
}
