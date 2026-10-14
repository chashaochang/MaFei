package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.session.*

@Composable
internal fun MineEntryBinding(
    mineContent: @Composable (Modifier) -> Unit,
) {
    // 不给它加标题栏：旧版「我的」作为底部标签时顶部是**空的**
    // （只留 `safeTop + ACTION_BAR_HEIGHT` 的占位），标题栏只在
    // 「我的」被当页面推出来（fromHome）时才出现。页面自己按同样的
    // 规则留位，见 MineScreen.destinationOwnsTitleBar。
    mineContent(Modifier.fillMaxSize())
}
