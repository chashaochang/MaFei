package cn.mafei.cmp.feature.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.mafeiInteractive
import org.jetbrains.compose.resources.painterResource

enum class PlexConnectPhase { Idle, Starting, Waiting, Choosing, Connecting }
data class PlexConnectServer(val id: String, val name: String)
data class PlexConnectUiState(
    val phase: PlexConnectPhase = PlexConnectPhase.Idle,
    val servers: List<PlexConnectServer> = emptyList(),
)

data class PlexConnectActions(
    val authorize: () -> Unit = {},
    val openBrowser: () -> Unit = {},
    val connect: (String) -> Unit = {},
    val cancel: () -> Unit = {},
)

@Composable
internal fun PlexConnectForm(
    state: PlexConnectUiState,
    actions: PlexConnectActions,
    busy: Boolean,
    backdrop: GlassBackdrop,
    onOpenHistory: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val waiting = state.phase == PlexConnectPhase.Starting || state.phase == PlexConnectPhase.Waiting
    val connecting = state.phase == PlexConnectPhase.Connecting
    Column(
        modifier.padding(top = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state.phase) {
            PlexConnectPhase.Idle -> PlexFormButton("连接 Plex", !busy, backdrop, actions.authorize)
            PlexConnectPhase.Starting, PlexConnectPhase.Waiting -> {
                MaFeiLoadingIndicator(Modifier.size(28.dp), style = MaFeiLoadingStyle.Button, tint = Color.White)
                Text(
                    if (state.phase == PlexConnectPhase.Starting) "正在请求授权…" else "等待 Plex 授权",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = .82f),
                )
                if (state.phase == PlexConnectPhase.Waiting) {
                    PlexFormButton("打开 Plex", !busy, backdrop, actions.openBrowser)
                }
            }
            PlexConnectPhase.Choosing, PlexConnectPhase.Connecting -> {
                Text("选择服务器", style = MaterialTheme.typography.titleSmall, color = Color.White)
                state.servers.forEach { server ->
                    PlexFormButton(server.name, !busy && !connecting, backdrop, { actions.connect(server.id) })
                }
                if (connecting) {
                    MaFeiLoadingIndicator(Modifier.size(24.dp), style = MaFeiLoadingStyle.Button, tint = Color.White)
                }
            }
        }
        if (waiting || state.phase == PlexConnectPhase.Choosing || connecting) {
            PlexFormButton("取消", true, backdrop, actions.cancel)
        }
        if (onOpenHistory != null) {
            Row(
                Modifier.heightIn(min = 44.dp).mafeiInteractive(
                    onClick = onOpenHistory, enabled = !busy, label = "账号历史",
                ).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(painterResource(MaFeiLegacyDrawables.history), null, Modifier.size(18.dp))
                Text("账号历史", color = Color.White.copy(alpha = .76f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PlexFormButton(text: String, enabled: Boolean, backdrop: GlassBackdrop, onClick: () -> Unit) {
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().then(if (enabled) Modifier else Modifier.semantics { disabled() }),
        isInteractive = enabled,
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (enabled) .96f else .5f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
