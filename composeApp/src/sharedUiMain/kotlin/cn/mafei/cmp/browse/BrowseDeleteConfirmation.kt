package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.*
import kotlinx.coroutines.launch

@Composable
internal fun BrowseDeleteConfirmation(
    scope: kotlinx.coroutines.CoroutineScope,
    browser: MediaBrowser,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    navigation: BrowseNavigationState,
    selection: BrowseSelectionState,
    deleteConfirmBackdrop: cn.mafei.cmp.common.glass.GlassBackdrop,
) {
    selection.pendingDeleteMedia?.let { ids ->
        MaFeiGlassConfirmDialog(
            title = "删除媒体",
            content = "确定要删除选中的 ${ids.size} 项媒体吗？删除后无法恢复。",
            confirmText = "删除",
            dismissText = "取消",
            destructive = true,
            backdrop = deleteConfirmBackdrop,
            onDismiss = { selection.pendingDeleteMedia = null },
            onConfirm = {
                selection.pendingDeleteMedia = null
                selection.librarySelectedIds = emptySet()
                scope.launch {
                    browser.deleteMedia(ids)
                    // 详情页里删除自身：数据已删，回退出详情（与旧版行为一致）。
                    if (ids.any { it == state.detailId }) {
                        if (navigation.browseBackStack.lastOrNull() is BrowseDetailRoute ||
                            navigation.browseBackStack.lastOrNull() is BrowseEpisodeRoute
                        ) {
                            navigation.browseBackStack.removeLastOrNull()
                        }
                        browser.back()
                    }
                }
            },
        )
    }
}
