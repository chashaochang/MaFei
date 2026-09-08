package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import cn.mafei.cmp.session.ApplicationRuntime
import cn.mafei.cmp.session.ApplicationSessionStatus

val LocalApplicationRuntime = staticCompositionLocalOf<ApplicationRuntime> { error("Application runtime is not installed") }

@Composable
fun App(runtime: ApplicationRuntime, onThemeChanged: (Boolean) -> Unit = {}) {
    LaunchedEffect(runtime) { runtime.sessions.start() }
    val state by runtime.sessions.state.collectAsState()
    val status = when (state.status) {
        ApplicationSessionStatus.NotStarted, ApplicationSessionStatus.Restoring -> "正在恢复账号"
        ApplicationSessionStatus.SignedOut -> "未登录"
        ApplicationSessionStatus.Ready -> "账号已恢复 · 尚未验证服务器"
        ApplicationSessionStatus.NeedsAttention -> "账号恢复失败 · 请重试或重新登录"
        ApplicationSessionStatus.Closed -> "会话已关闭"
    }
    CompositionLocalProvider(LocalApplicationRuntime provides runtime) {
        FoundationProbeScreen(onThemeChanged, status)
    }
}
