package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.common.ui.MaFeiBackHandler
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog
import cn.mafei.cmp.session.*

@Composable
internal fun BrowseAccountModal(
    backdrop: cn.mafei.cmp.common.glass.GlassBackdrop,
    layout: cn.mafei.cmp.common.design_system.MaFeiLayout,
    player: BrowsePlayerState,
    account: AccountFlowState,
    mineFullScreen: Boolean,
    mineNavContent: @Composable (Modifier) -> Unit,
    authenticationMessage: String?,
    reauthenticate: () -> Unit,
) {
    // 大屏：居中的详情页同规格模态。复用同一个 mineNavContent，
    // 所以来回切形态时里面停在哪一页、翻了几层都保持不变。
    if (account.minePresented && !mineFullScreen) {
        MineAccountSheet(layout = layout, backdrop = backdrop, onDismiss = { account.minePresented = false }, content = mineNavContent)
    }
    if (
        authenticationMessage != null &&
            !account.authenticationNoticeDismissed &&
            account.mineBackStack.lastOrNull() !is BrowseConnectAccountRoute &&
            !player.playerExpanded
    ) {
        cn.mafei.cmp.common.ui.MaFeiBackHandler(enabled = true) { account.authenticationNoticeDismissed = true }
        MaFeiGlassConfirmDialog(
            title = "账号验证",
            content = authenticationMessage,
            confirmText = "重新登录",
            dismissText = "取消",
            onConfirm = reauthenticate,
            onDismiss = { account.authenticationNoticeDismissed = true },
            backdrop = backdrop,
        )
    }
}
