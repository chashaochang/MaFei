package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.chasing.ChasingBrowser
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.*
import cn.mafei.cmp.common.ui.LocalMaFeiTabInteractionState

@Composable
internal fun BindBrowseDataLifecycle(
    browser: MediaBrowser,
    search: BrowseSearchState,
    feiniu: FeiniuPreferenceState,
    provider: ProviderKind,
    currentRoute: BrowseRoute,
    chasingBrowser: ChasingBrowser,
    account: AccountFlowState,
    selection: BrowseSelectionState,
) {
    val tabInteraction = LocalMaFeiTabInteractionState.current
    LaunchedEffect(browser) { browser.start() }
    LaunchedEffect(search) { search.load() }
    LaunchedEffect(feiniu, provider) {
        if (provider == ProviderKind.Feiniu) {
            feiniu.load()
        }
    }
    LaunchedEffect(currentRoute, account.minePresented) {
        if (account.minePresented) return@LaunchedEffect
        // Cached content is already on screen. Only start automatic requests
        // after the navigation droplet settles; leaving cancels this wait.
        tabInteraction?.awaitIdle()
        if (currentRoute == BrowseFavoriteRoute || currentRoute == BrowseLibraryIndexRoute) {
            browser.refreshFavoritesIfStale()
        }
        if (currentRoute == BrowseChasingRoute) chasingBrowser.refreshIfStale()
    }
    // Mine owns an independent resume + completed preview. Reentry keeps the
    // cached content and only refreshes stale heads once the droplet is idle.
    LaunchedEffect(account.minePresented, account.mineBackStack.lastOrNull()) {
        if (account.minePresented && account.mineBackStack.lastOrNull() == BrowseMineRoute) {
            tabInteraction?.awaitIdle()
            browser.loadHistoryPreviewIfStale()
        }
    }
    // 独立播放历史：该页面已经迁入「我的」自己的回退栈，不能再监听主浏览路由。
    // 否则从账号浮层进入时 currentRoute 仍是首页/媒体库，只会留下 Mine 预览数据。
    LaunchedEffect(account.minePresented, account.mineBackStack.lastOrNull()) {
        if (account.minePresented && account.mineBackStack.lastOrNull() == BrowseMineHistoryRoute) {
            tabInteraction?.awaitIdle()
            browser.ensureHistoryLoaded()
        }
        selection.librarySelectedIds = emptySet()
    }
    // 删除可用性只探测一次：服务器用户策略决定是否出现删除入口。
    LaunchedEffect(browser) {
        browser.refreshDeleteAvailability()
        browser.refreshAdministratorStatus()
    }
}
