package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiGlassDefaults
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyListGroup
import cn.mafei.cmp.common.ui.LegacyListGroupItem
import cn.mafei.cmp.common.ui.LegacyTransientToast
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiActionBarButton
import cn.mafei.cmp.common.ui.MaFeiActionBarScaffold
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.core.domain.ProviderKind
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/** Mechanical Compose translation of ArkUI `SettingPage`. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    feiniuRoutePreferenceTitle: String? = null,
    onOpenFeiniuPreference: (() -> Unit)? = null,
) {
    val safeArea = LocalMaFeiSafeArea.current
    MaFeiActionBarScaffold(
        title = "设置",
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = safeArea.bottom.coerceAtLeast(24.dp)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 外观与显示：入口先隐藏。浅色模式未真正实现——各页面按深色优先构建
                // （浅色文字 + 深色氛围图），选「浅色」只会把表面染浅、页面本体依旧深色，
                // 看起来是坏的。等浅色做完再放开这一行；页面与路由仍在
                // （AppearanceScreen / BrowseAppearanceRoute），恢复时取消注释即可。
                // LegacyListGroup(
                //     items = listOf(LegacyListGroupItem("外观与显示", trailing = DarkAppearanceTitle)),
                //     onItemClick = { onOpenAppearance() },
                // )
                if (feiniuRoutePreferenceTitle != null && onOpenFeiniuPreference != null) {
                    LegacyListGroup(
                        items = listOf(
                            LegacyListGroupItem("飞牛连接策略", trailing = feiniuRoutePreferenceTitle),
                        ),
                        onItemClick = { onOpenFeiniuPreference() },
                    )
                }
                // 关于/隐私从「我的」挪到这里：设置页才有内容，也让「我的」只留账号相关操作。
                LegacyListGroup(
                    items = listOf(LegacyListGroupItem("关于马飞")),
                    onItemClick = { onOpenAbout() },
                )
            }
        }
    }
}

internal const val DarkAppearanceTitle = "深色"

/**
 * Appearance settings. The product is dark-only — every page is built dark-first
 * (light text on dark atmospheres) and the login/history backgrounds are dark
 * images — so a light option could only tint the surfaces while the pages behind
 * it stayed dark. Rather than offer that, this page states the current appearance.
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeArea = LocalMaFeiSafeArea.current
    MaFeiActionBarScaffold(
        title = "外观与主题",
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = safeArea.bottom.coerceAtLeast(24.dp)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "颜色模式",
                    Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)).padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .semantics { selected = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            DarkAppearanceTitle,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Text(
                    "当前版本仅提供深色外观。",
                    Modifier.fillMaxWidth().padding(start = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .76f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

/** Mechanical Compose translation of ArkUI `AddAccountPage`. */
@Composable
fun AddAccountScreen(
    onBack: () -> Unit,
    onProvider: (ProviderKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    MaFeiActionBarScaffold(
        title = "新增账号",
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Text(
                    "选择要连接的媒体服务",
                    Modifier.fillMaxWidth().padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)),
                ) {
                    ProviderRow(
                        title = "Jellyfin",
                        description = "连接现有 Jellyfin 服务器",
                        logo = MaFeiLegacyDrawables.jellyfinLogo,
                        onClick = { onProvider(ProviderKind.Jellyfin) },
                    )
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(.5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f)),
                    )
                    ProviderRow(
                        title = "飞牛影视",
                        description = "使用 FNID 或服务器地址连接飞牛影视",
                        logo = MaFeiLegacyDrawables.feiniuLogo,
                        onClick = { onProvider(ProviderKind.Feiniu) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    title: String,
    description: String,
    logo: org.jetbrains.compose.resources.DrawableResource,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(80.dp)
            .mafeiInteractive(onClick, radiusDp = 0f, label = title)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Image(
            painter = painterResource(MaFeiLegacyDrawables.arrowRight),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

/** Mechanical Compose translation of ArkUI `AddGroupPage`. */
@Composable
fun AddGroupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeArea = LocalMaFeiSafeArea.current
    MaFeiActionBarScaffold(
        title = "加入群聊",
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = safeArea.bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(MaFeiLegacyDrawables.groupQrCode),
                    contentDescription = "群聊二维码",
                    modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFCFCFC)).padding(8.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(30.dp))
                Text("扫描二维码加入群聊", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

data class AccountSwitchItem(
    val key: String,
    val provider: ProviderKind,
    val serverName: String,
    val displayName: String,
    val address: String,
    val lastUsedEpochMs: Long?,
    val isCurrent: Boolean,
)

private data class AccountSwitchGroup(
    val key: String,
    val title: String,
    val items: List<AccountSwitchItem>,
)

private data class AccountProviderMenuAnchor(
    val boundsInRoot: Rect,
    val centerOnAnchor: Boolean,
)

/** Mechanical Compose translation of the account-list portion of ArkUI `AccountPage`. */
@Composable
fun AccountSwitchScreen(
    items: List<AccountSwitchItem>,
    loading: Boolean,
    busyKey: String?,
    deletingKey: String?,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAccount: (AccountSwitchItem) -> Unit,
    onDelete: (AccountSwitchItem) -> Unit,
    onAddProvider: (ProviderKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val backdrop = rememberGlassBackdrop()
    var pendingDelete by remember { mutableStateOf<AccountSwitchItem?>(null) }
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var titleButtonBounds by remember { mutableStateOf(Rect.Zero) }
    var emptyButtonBounds by remember { mutableStateOf(Rect.Zero) }
    var providerMenuAnchor by remember { mutableStateOf<AccountProviderMenuAnchor?>(null) }
    val accountGroups = remember(items) { accountSwitchGroups(items) }
    Box(modifier.fillMaxSize().onGloballyPositioned { rootBounds = it.boundsInRoot() }) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .captureGlassBackdrop(backdrop),
        ) {
            Spacer(Modifier.height(safeArea.top + 56.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(36.dp))
                    }
                    items.isEmpty() -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(
                            painter = painterResource(MaFeiLegacyDrawables.noHistory),
                            contentDescription = null,
                            modifier = Modifier.size(200.dp),
                            contentScale = ContentScale.Inside,
                        )
                        Text("暂无已保存账号", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "新增账号",
                            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .onGloballyPositioned { emptyButtonBounds = it.boundsInRoot() }
                                .mafeiInteractive(
                                    onClick = {
                                        providerMenuAnchor = AccountProviderMenuAnchor(
                                            boundsInRoot = emptyButtonBounds,
                                            centerOnAnchor = true,
                                        )
                                    },
                                    radiusDp = 22f,
                                    label = "新增账号",
                                )
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    else -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp)
                                .padding(top = 10.dp, bottom = safeArea.bottom + 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            accountGroups.forEach { group ->
                                Column {
                                    Text(
                                        group.title,
                                        Modifier.fillMaxWidth().height(40.dp)
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Column(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)),
                                    ) {
                                        group.items.forEachIndexed { index, item ->
                                            AccountSwitchRow(
                                                item = item,
                                                busy = busyKey == item.key || deletingKey == item.key,
                                                enabled = busyKey == null && deletingKey == null,
                                                onClick = {
                                                    if (!item.isCurrent) onAccount(item)
                                                },
                                                onDelete = {
                                                    if (!item.isCurrent) pendingDelete = item
                                                },
                                            )
                                            if (index < group.items.lastIndex) {
                                                Box(
                                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(.5.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f),
                                                        ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        MaFeiActionBar(
            title = "切换账号",
            onBack = onBack,
            backdrop = backdrop,
            actionCount = 1,
            modifier = Modifier.align(Alignment.TopCenter),
            actions = { buttonBackdrop ->
                MaFeiActionBarButton(
                    onClick = {
                        providerMenuAnchor = AccountProviderMenuAnchor(
                            boundsInRoot = titleButtonBounds,
                            centerOnAnchor = false,
                        )
                    },
                    backdrop = buttonBackdrop,
                    contentDescription = "新增账号",
                    modifier = Modifier.onGloballyPositioned { titleButtonBounds = it.boundsInRoot() },
                ) {
                Image(
                    painter = painterResource(MaFeiLegacyDrawables.add),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                )
                }
            },
        )
        providerMenuAnchor?.let { anchor ->
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { providerMenuAnchor = null }
                },
            )
            AccountProviderMenu(
                backdrop = backdrop,
                anchor = anchor,
                rootBounds = rootBounds,
                onDismiss = { providerMenuAnchor = null },
                onProvider = onAddProvider,
            )
        }
        LegacyTransientToast(
            message = error,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        pendingDelete?.let { item ->
            MaFeiGlassConfirmDialog(
                title = "删除账号",
                content = "确定要删除「${item.displayName}」这个账号吗？",
                confirmText = "删除",
                dismissText = "取消",
                destructive = true,
                backdrop = backdrop,
                onDismiss = { pendingDelete = null },
                onConfirm = {
                    pendingDelete = null
                    onDelete(item)
                },
            )
        }
    }
}

private fun accountSwitchGroups(items: List<AccountSwitchItem>): List<AccountSwitchGroup> {
    return items.groupBy { item ->
        val title = item.serverName.trim().ifEmpty { item.address.trim() }
        "${item.provider}\n$title\n${item.address.trim()}"
    }.map { (key, groupedItems) ->
        AccountSwitchGroup(
            key = key,
            title = groupedItems.first().serverName.trim().ifEmpty { groupedItems.first().address.trim() },
            items = groupedItems.sortedWith(
                compareByDescending<AccountSwitchItem> { it.isCurrent }
                    .thenByDescending { it.lastUsedEpochMs ?: Long.MIN_VALUE },
            ),
        )
    }.sortedWith(
        compareByDescending<AccountSwitchGroup> { group -> group.items.any { it.isCurrent } }
            .thenByDescending { group -> group.items.firstOrNull()?.lastUsedEpochMs ?: Long.MIN_VALUE },
    )
}

@Composable
private fun AccountProviderMenu(
    backdrop: GlassBackdrop,
    anchor: AccountProviderMenuAnchor,
    rootBounds: Rect,
    onDismiss: () -> Unit,
    onProvider: (ProviderKind) -> Unit,
) {
    val density = LocalDensity.current
    val menuWidth = 216.dp
    val menuHeight = 112.dp
    val edgePadding = 8.dp
    val (menuWidthPx, menuHeightPx, edgePaddingPx) = with(density) {
        Triple(menuWidth.toPx(), menuHeight.toPx(), edgePadding.toPx())
    }
    val anchorBounds = anchor.boundsInRoot
    val anchorRight = anchorBounds.right - rootBounds.left
    val anchorCenter = anchorBounds.center.x - rootBounds.left
    val anchorTop = anchorBounds.top - rootBounds.top
    val anchorBottom = anchorBounds.bottom - rootBounds.top
    val rawX = if (anchor.centerOnAnchor) anchorCenter - menuWidthPx / 2f else anchorRight - menuWidthPx
    val x = rawX.coerceIn(
        edgePaddingPx,
        (rootBounds.width - menuWidthPx - edgePaddingPx).coerceAtLeast(edgePaddingPx),
    )
    val belowY = anchorBottom + with(density) { 6.dp.toPx() }
    val aboveY = anchorTop - menuHeightPx - with(density) { 6.dp.toPx() }
    val y = if (belowY + menuHeightPx <= rootBounds.height - edgePaddingPx) belowY
    else aboveY.coerceAtLeast(edgePaddingPx)

    GlassSurface(
        backdrop = backdrop,
        spec = MaFeiGlassDefaults.spec(GlassRole.Overlay).copy(cornerRadiusDp = 16f),
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(menuWidth),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            AccountProviderMenuRow(
                title = "Jellyfin",
                logo = MaFeiLegacyDrawables.jellyfinLogo,
                onClick = {
                    onDismiss()
                    onProvider(ProviderKind.Jellyfin)
                },
            )
            AccountProviderMenuRow(
                title = "飞牛影视",
                logo = MaFeiLegacyDrawables.feiniuLogo,
                onClick = {
                    onDismiss()
                    onProvider(ProviderKind.Feiniu)
                },
            )
        }
    }
}

@Composable
private fun AccountProviderMenuRow(
    title: String,
    logo: org.jetbrains.compose.resources.DrawableResource,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .mafeiInteractive(onClick, radiusDp = 0f, label = title)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            contentScale = ContentScale.Fit,
        )
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AccountSwitchRow(
    item: AccountSwitchItem,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(80.dp)
            .mafeiInteractive({ if (enabled) onClick() }, radiusDp = 0f, label = item.displayName)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(
                if (item.provider == ProviderKind.Jellyfin) MaFeiLegacyDrawables.jellyfinLogo
                else MaFeiLegacyDrawables.feiniuLogo,
            ),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.displayName,
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.isCurrent) {
                    Text(
                        "当前账号",
                        Modifier.clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                    )
                }
            }
            Text(
                item.address,
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else if (!item.isCurrent) {
            Box(
                Modifier.size(32.dp).mafeiInteractive(
                    onClick = { if (enabled) onDelete() },
                    radiusDp = 16f,
                    label = "删除账号",
                ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(MaFeiLegacyDrawables.delete),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
    }
}
