package cn.mafei.cmp.feature.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.MaFeiSavedAccountList
import cn.mafei.cmp.common.ui.SavedAccountDeleteAffordance
import cn.mafei.cmp.common.ui.SavedAccountListDefaults
import org.jetbrains.compose.resources.painterResource

@Composable
fun AccountHistoryScreen(
    items: List<ConnectionHistoryItem>,
    loading: Boolean,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelected: (ConnectionHistoryItem) -> Unit,
    /** Non-null while that row's deletion is in flight. */
    deletingKey: String?,
    /** Confirmation and persistence remain owned by the route. */
    onRequestDelete: (ConnectionHistoryItem) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 窗口背景层的源（含影视墙），由调用方铺好并捕获。两栏布局必须传：本栏宽度会随
     * 布局动画变化，各自捕获本栏内容既会让两栏的墙错位、也会把模糊拉花。为 null 时
     * 本面板自己铺一层墙并捕获。
     */
    pageBackdrop: GlassBackdrop? = null,
) {
    val safeArea = currentMaFeiSafeArea()
    val ownBackdrop = if (pageBackdrop == null) rememberGlassBackdrop() else null
    val windowBackdrop = pageBackdrop ?: requireNotNull(ownBackdrop)
    Box(modifier.fillMaxSize()) {
        if (ownBackdrop != null) {
            Box(Modifier.fillMaxSize().captureGlassBackdrop(ownBackdrop)) {
                ConnectAtmosphere()
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f)))
            }
        }
        Column(
            Modifier.fillMaxSize().padding(
                start = safeArea.start,
                top = safeArea.top + 64.dp,
                end = safeArea.end,
                bottom = safeArea.bottom,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HistoryContent(
                items = items,
                loading = loading,
                error = error,
                enabled = !busy && deletingKey == null,
                deletingKey = deletingKey,
                backdrop = windowBackdrop,
                onRetry = onRetry,
                onSelected = onSelected,
                onRequestDelete = onRequestDelete,
                modifier = Modifier.weight(1f).widthIn(max = 600.dp).fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        MaFeiActionBar(
            title = "账号历史",
            onBack = onBack,
            backdrop = windowBackdrop,
            actionCount = 1,
            modifier = Modifier.align(Alignment.TopCenter),
            actions = {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (busy || deletingKey != null) {
                        MaFeiLoadingIndicator(
                            modifier = Modifier.size(22.dp),
                            style = MaFeiLoadingStyle.Button,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun HistoryContent(
    items: List<ConnectionHistoryItem>,
    loading: Boolean,
    error: String?,
    enabled: Boolean,
    deletingKey: String?,
    backdrop: GlassBackdrop,
    onRetry: () -> Unit,
    onSelected: (ConnectionHistoryItem) -> Unit,
    onRequestDelete: (ConnectionHistoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        loading -> Box(modifier, contentAlignment = Alignment.Center) {
            MaFeiLoadingIndicator(
                modifier = Modifier.size(32.dp),
                style = MaFeiLoadingStyle.Page,
                tint = Color.White.copy(alpha = .82f),
            )
        }

        error != null -> Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                error,
                color = Color.White.copy(alpha = .78f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(18.dp))
            LiquidButton(onClick = onRetry, backdrop = backdrop) {
                Text("重试", color = Color.White.copy(alpha = .94f), fontWeight = FontWeight.Medium)
            }
        }

        else -> MaFeiSavedAccountList(
            items = items,
            enabled = enabled,
            busyKey = null,
            deletingKey = deletingKey,
            deleteAffordance = SavedAccountDeleteAffordance.LongPress,
            onAccount = onSelected,
            onDelete = onRequestDelete,
            modifier = modifier,
            showCurrentLabel = false,
            showRememberedPassword = true,
            colors = SavedAccountListDefaults.colors(
                groupTitle = Color.White.copy(alpha = .70f),
                container = Color.Black.copy(alpha = .30f),
                divider = Color.White.copy(alpha = .10f),
                title = Color.White.copy(alpha = .95f),
                subtitle = Color.White.copy(alpha = .55f),
                trailingIcon = Color.White,
                currentBackground = Color.White.copy(alpha = .16f),
                currentContent = Color.White.copy(alpha = .92f),
                passwordBackground = Color.White.copy(alpha = .92f),
                passwordContent = Color.Black.copy(alpha = .88f),
            ),
            emptyContent = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(MaFeiLegacyDrawables.history),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp).alpha(.36f),
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "暂无历史账号",
                        color = Color.White.copy(alpha = .60f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            },
        )
    }
}
