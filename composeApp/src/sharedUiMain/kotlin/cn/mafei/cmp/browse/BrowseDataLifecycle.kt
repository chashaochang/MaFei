package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.chasing.ChasingBrowser
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.*

@Composable
internal fun BindBrowseDataLifecycle(
    browser: MediaBrowser,
    search: BrowseSearchState,
    feiniu: FeiniuPreferenceState,
    provider: ProviderKind,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    libraryGridState: LazyGridState,
    currentRoute: BrowseRoute,
    chasingBrowser: ChasingBrowser,
    account: AccountFlowState,
    selection: BrowseSelectionState,
) {
    LaunchedEffect(browser) { browser.start() }
    LaunchedEffect(search) { search.load() }
    LaunchedEffect(feiniu, provider) {
        if (provider == ProviderKind.Feiniu) {
            feiniu.load()
        }
    }
    LaunchedEffect(state.selectedCollection) { libraryGridState.scrollToItem(0) }
    LaunchedEffect(currentRoute) {
        if (currentRoute == BrowseFavoriteRoute || currentRoute == BrowseLibraryIndexRoute) {
            browser.refreshFavorites()
        }
        if (currentRoute == BrowseChasingRoute) chasingBrowser.refresh()
    }
    // Legacy MineTab refreshes its independent resume + completed preview every
    // time the account surface becomes visible; it never borrows HomeFeed.resume.
    LaunchedEffect(account.minePresented) { if (account.minePresented && account.mineBackStack.lastOrNull() == BrowseMineRoute) browser.loadHistoryPreview() }
    // 独立播放历史：该页面已经迁入「我的」自己的回退栈，不能再监听主浏览路由。
    // 否则从账号浮层进入时 currentRoute 仍是首页/媒体库，只会留下 Mine 预览数据。
    LaunchedEffect(account.mineBackStack.lastOrNull()) {
        if (account.mineBackStack.lastOrNull() == BrowseMineHistoryRoute) browser.loadHistory()
        selection.librarySelectedIds = emptySet()
    }
    // 删除可用性只探测一次：服务器用户策略决定是否出现删除入口。
    LaunchedEffect(browser) {
        browser.refreshDeleteAvailability()
        browser.refreshAdministratorStatus()
    }
}
