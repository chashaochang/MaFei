package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.ui.LegacyPosterPlaceholder
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.feature.music.MusicCatalogStore
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.session.*

@Composable
internal fun MusicDetailEntryBinding(
    route: BrowseMusicDetailRoute,
    playerShellState: PlayerShellState,
    browser: MediaBrowser,
    player: BrowsePlayerState,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    navigator: BrowseNavigator,
    navigation: BrowseNavigationState,
    musicMiniPlayerMedia: MediaItem?,
    musicStore: MusicCatalogStore,
    repository: cn.mafei.cmp.data.repositories.MediaRepository,
    mediaSession: cn.mafei.cmp.data.repositories.MediaSession,
) {
    MusicDetailRouteContent(musicStore, route.mediaId, route.title, route.artist,
        onBack = navigator::pop,
        onAlbum = { item -> navigation.browseBackStack += BrowseMusicDetailRoute(item.id.value, item.title, false, route.libraryId, route.origin) },
        onPlay = player.playMusicQueue,
        onFavorite = if (browser.capabilities.favoritesEdit) {{ item ->
            val favorite = !item.userData.isFavorite
            repository.setFavorite(mediaSession, item.id, favorite)
            musicStore.favoriteChanged(item.id, favorite)
        }} else null,
        artwork = { item, modifier -> MediaArtwork(item.preferredPoster(), item.title, artworkCache, fetchArtwork,
            modifier, fallback = { LegacyPosterPlaceholder(item.mediaType, it) }) },
        currentId = player.playingMedia?.id?.value, preparing = player.playbackBusy,
        playing = playerShellState.phase == PlayerPhase.Playing,
        bottomInset = if (musicMiniPlayerMedia != null) 76.dp else 0.dp)
}
