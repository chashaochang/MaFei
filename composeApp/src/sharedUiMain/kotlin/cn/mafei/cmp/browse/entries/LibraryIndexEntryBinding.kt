package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.feature.home.LibraryIndexScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.*
import kotlinx.coroutines.launch

@Composable
internal fun LibraryIndexEntryBinding(
    scope: kotlinx.coroutines.CoroutineScope,
    browser: MediaBrowser,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    layout: MaFeiLayout,
    navigator: BrowseNavigator,
    navigation: BrowseNavigationState,
    musicMiniPlayerMedia: MediaItem?,
) {
    LibraryIndexScreen(
        collections = state.collections,
        favoriteCount = state.favoriteTotalEstimate ?: state.favoriteItems.size,
        loading = state.loading,
        refreshing = state.refreshing,
        error = state.error,
        layout = layout,
        onCollection = navigator.browseCollection,
        onChasing =
            if (browser.capabilities.chasing) {
                { navigation.browseBackStack += BrowseChasingRoute }
            } else null,
        onFavorite = {
            if (navigation.browseBackStack.lastOrNull() != BrowseFavoriteRoute) {
                navigation.browseBackStack += BrowseFavoriteRoute
            }
        },
        onRetry = { scope.launch { browser.refresh() } },
        bottomOverlayInset = if (musicMiniPlayerMedia != null) 76.dp else 0.dp,
        modifier = Modifier.fillMaxSize(),
    )
}
