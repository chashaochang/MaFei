package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.settings.MineProfile
import cn.mafei.cmp.feature.settings.MineScreen as LegacyMineScreen
import cn.mafei.cmp.session.*
import kotlinx.coroutines.launch

@Composable
internal fun AccountRootBinding(
    mineModifier: Modifier,
    account: AccountFlowState,
    scope: kotlinx.coroutines.CoroutineScope,
    browser: MediaBrowser,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    session: ApplicationSession,
    layout: MaFeiLayout,
    busy: Boolean,
    player: BrowsePlayerState,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    embeddedWebHost: EmbeddedWebHost,
    reauthenticate: () -> Unit,
    onLogout: () -> Unit,
    authenticationFailure: cn.mafei.cmp.core.domain.AuthFailureKind?,
) {
    // Navigation 3 keeps the outgoing entry composed during a transition.
    // Read the route at event time so a late pointer delivered to that
    // stale entry cannot mutate the active session.
    fun mineRootAction(action: () -> Unit): () -> Unit = {
        if (account.minePresented && account.mineBackStack.lastOrNull() == BrowseMineRoute) action()
    }
    fun mineRootMediaAction(action: (MediaItem) -> Unit): (MediaItem) -> Unit = { item ->
        if (account.minePresented && account.mineBackStack.lastOrNull() == BrowseMineRoute) action(item)
    }
    LegacyMineScreen(
        profile = MineProfile(
            userName = session.connection.account.displayName,
            serverName = legacyVisibleServerName(session.connection),
            serverVersion = session.connection.server.serverVersion.orEmpty(),
            connectionDescription = legacyConnectionDescription(session.connection),
            policy = if (state.administrator == true) "管理员" else "普通用户",
        ),
        historyItems = state.historyItems,
        historyLoading = state.historyLoading && state.historyItems.isEmpty(),
        historyError = state.historyError,
        layout = layout,
        busy = busy,
        onHistoryItem = mineRootMediaAction(player.startPlayback),
        onHistoryRetry = mineRootAction { scope.launch { browser.loadHistoryPreview() } },
        // 「我的」里的二级页推到**容器自己的**回退栈上，容器不关闭 —— 模态 / 全屏
        // 两种呈现共用同一条栈。
        onHistoryMore = {
            if (account.minePresented && account.mineBackStack.lastOrNull() == BrowseMineRoute) {
                account.mineBackStack += BrowseMineHistoryRoute
            }
        },
        onSwitchAccount = mineRootAction {
            account.loadAccounts()
            if (account.mineBackStack.lastOrNull() != BrowseAccountSwitchRoute) {
                account.mineBackStack += BrowseAccountSwitchRoute
            }
        },
        onSettings = mineRootAction {
            if (account.mineBackStack.lastOrNull() != BrowseSettingsRoute) {
                account.mineBackStack += BrowseSettingsRoute
            }
        },
        onWebITong = if (session.selection.provider == ProviderKind.Jellyfin &&
            embeddedWebHost.nativePlaybackSupported) mineRootAction { account.mineBackStack += BrowseWebITongRoute } else null,
        onManagement = if (state.administrator == true && session.binding.adapter.management != null)
            mineRootAction { account.mineBackStack += BrowseManagementRoute() } else null,
        onPrivacy = mineRootAction {
            if (embeddedWebHost === UnsupportedEmbeddedWebHost) uriHandler.openUri(PRIVACY_AGREEMENT_URL)
            else account.mineBackStack += BrowsePrivacyWebRoute
        },
        onAddGroup = mineRootAction {
            if (account.mineBackStack.lastOrNull() != BrowseAddGroupRoute) {
                account.mineBackStack += BrowseAddGroupRoute
            }
        },
        onLogout = {
            if (account.minePresented && account.mineBackStack.lastOrNull() == BrowseMineRoute) {
                account.minePresented = false
                onLogout()
            }
        },
        onReauthenticate = mineRootAction(reauthenticate).takeIf { authenticationFailure != null },
        modifier = mineModifier,
        avatar = session.connection.account.avatarImage?.let { image ->
            { avatarModifier ->
                MediaArtwork(
                    image = image,
                    title = session.connection.account.displayName,
                    cache = artworkCache,
                    fetch = fetchArtwork,
                    modifier = avatarModifier,
                )
            }
        },
        artwork = { item, itemModifier ->
            MediaArtwork(item.preferredPoster(), item.title, artworkCache, fetchArtwork, itemModifier)
        },
    )
}
