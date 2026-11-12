package cn.mafei.cmp

import androidx.compose.runtime.*
import cn.mafei.cmp.common.ui.LocalMaFeiTabInteractionState
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.chasing.ChasingBrowser
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.MediaBrowserState
import cn.mafei.cmp.player.api.PlayerRenderMode
import kotlinx.coroutines.launch

/** Detail-to-detail changes with the same chrome do not invalidate session owners or the shell. */
@Composable
internal fun BrowseShellRouteScope(
    navigation: BrowseNavigationState,
    content: @Composable (BrowseShellPresentation) -> Unit,
) {
    val presentation by remember(navigation) { derivedStateOf { navigation.currentRoute.shellPresentation() } }
    content(presentation)
}

@Composable
internal fun ResetBrowseSelectionOnCollectionChange(state: State<MediaBrowserState>, selection: BrowseSelectionState) {
    LaunchedEffect(state.value.selectedCollection) { selection.librarySelectedIds = emptySet() }
}

@Composable
internal fun BindBrowseSessionPlayerLifecycle(
    player: BrowsePlayerState,
    playerShellState: PlayerShellState,
    playerRenderMode: PlayerRenderMode,
    state: State<MediaBrowserState>,
    onPlayerScreenChanged: (Boolean) -> Unit,
    onPlayerFullscreenChanged: (Boolean) -> Unit,
    separateVideoWindow: Boolean = false,
) {
    BindBrowsePlayerLifecycle(
        player, playerShellState, playerRenderMode, state.value.detailId,
        onPlayerScreenChanged, onPlayerFullscreenChanged, separateVideoWindow,
    )
}

/** The exact route is read only by the effect host, not by the application/session owner. */
@Composable
internal fun BindBrowseNavigationDataLifecycle(
    browser: MediaBrowser,
    search: BrowseSearchState,
    feiniu: FeiniuPreferenceState,
    provider: ProviderKind,
    navigation: BrowseNavigationState,
    chasingBrowser: ChasingBrowser,
    account: AccountFlowState,
    selection: BrowseSelectionState,
) {
    val tabInteraction = LocalMaFeiTabInteractionState.current
    LaunchedEffect(browser, navigation.activeRoot, account.minePresented) {
        if (account.minePresented) return@LaunchedEffect
        tabInteraction?.awaitIdle()
        browser.resumeTabContext()
    }
    BindBrowseDataLifecycle(browser, search, feiniu, provider, navigation.currentRoute, chasingBrowser, account, selection)
}

@Composable
internal fun BindHomeLibraryPrefetch(
    navigation: BrowseNavigationState,
    browser: MediaBrowser,
    collections: List<MediaCollection>,
    phoneNavigation: Boolean,
    active: Boolean,
) {
    val currentRoute = navigation.currentRoute
    val tabInteraction = LocalMaFeiTabInteractionState.current
    LaunchedEffect(currentRoute, collections, phoneNavigation, active) {
        if (active && currentRoute == BrowseHomeRoute && !phoneNavigation) {
            tabInteraction?.awaitIdle()
            collections.forEach { collection -> launch { browser.loadHomeLatest(collection.id) } }
        }
    }
}
