package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiGlassDefaults
import cn.mafei.cmp.common.glass.DefaultGlassIntensity
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidToggle
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyListGroup
import cn.mafei.cmp.common.ui.LegacyListGroupItem
import cn.mafei.cmp.common.ui.LegacyTransientToast
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiActionBarButton
import cn.mafei.cmp.common.ui.MaFeiActionBarScaffold
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.MaFeiSavedAccountList
import cn.mafei.cmp.common.ui.SavedAccountDeleteAffordance
import cn.mafei.cmp.common.ui.SavedAccountListGroup
import cn.mafei.cmp.common.ui.SavedAccountListItem
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.common.ui.savedAccountListGroups
import cn.mafei.cmp.core.domain.ProviderKind
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

/** Mechanical Compose translation of ArkUI `SettingPage`. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenGlassStyleSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    feiniuRoutePreferenceTitle: String? = null,
    onOpenFeiniuPreference: (() -> Unit)? = null,
    glassStyleTitle: String = GlassStyle.Liquid.title,
    homeCarouselVisible: () -> Boolean = { true },
    onHomeCarouselVisibleChanged: (Boolean) -> Unit = {},
    showPlayerRenderMode: Boolean = false,
    playerRenderMode: () -> String = { PlayerRenderModeBuffer },
    onPlayerRenderModeChanged: (String) -> Unit = {},
    onOpenPlayerSettings: (() -> Unit)? = null,
    appearanceTitle: String = DarkAppearanceTitle,
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
                LegacyListGroup(
                    items = listOf(
                        LegacyListGroupItem("外观与显示", trailing = appearanceTitle),
                        LegacyListGroupItem("风格设置", trailing = glassStyleTitle),
                    ),
                    onItemClick = { index ->
                        if (index == 0) onOpenAppearance() else onOpenGlassStyleSettings()
                    },
                )
                if (showPlayerRenderMode) {
                    PlayerRenderModeSetting(
                        mode = playerRenderMode(),
                        onModeChange = onPlayerRenderModeChanged,
                    )
                }
                if (onOpenPlayerSettings != null) {
                    LegacyListGroup(
                        items = listOf(LegacyListGroupItem("播放器设置")),
                        onItemClick = { onOpenPlayerSettings() },
                    )
                }
                HomeCarouselSetting(
                    enabled = homeCarouselVisible(),
                    onEnabledChange = onHomeCarouselVisibleChanged,
                )
                if (feiniuRoutePreferenceTitle != null && onOpenFeiniuPreference != null) {
                    LegacyListGroup(
                        items = listOf(
                            LegacyListGroupItem("飞牛连接策略", trailing = feiniuRoutePreferenceTitle),
                        ),
                        onItemClick = { onOpenFeiniuPreference() },
                    )
                }
            }
        }
    }
}

private const val PlayerRenderModeBuffer = "buffer"
private const val PlayerRenderModeDirect = "direct"

/** The only platform currently exposing this preference is the Harmony player host. */
@Composable
private fun PlayerRenderModeSetting(
    mode: String,
    onModeChange: (String) -> Unit,
) {
    val selected = if (mode == PlayerRenderModeDirect) PlayerRenderModeDirect else PlayerRenderModeBuffer
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "播放器渲染模式",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "下次播放时生效",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PlayerRenderModeOption(
                label = "Buffer",
                selected = selected == PlayerRenderModeBuffer,
                onClick = { onModeChange(PlayerRenderModeBuffer) },
            )
            PlayerRenderModeOption(
                label = "直出",
                selected = selected == PlayerRenderModeDirect,
                onClick = { onModeChange(PlayerRenderModeDirect) },
            )
        }
    }
}

@Composable
private fun PlayerRenderModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)
                else Color.Transparent,
            )
            .mafeiInteractive(onClick, radiusDp = 9f, label = "播放器渲染模式：$label")
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** A plain settings row: the carousel is content, not a glass surface. */
@Composable
private fun HomeCarouselSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val toggleBackdrop = rememberGlassBackdrop()
    Row(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            .mafeiInteractive(
                onClick = { onEnabledChange(!enabled) },
                radiusDp = 16f,
                label = "首页轮播图",
            )
            .semantics {
                contentDescription = "首页轮播图"
                selected = enabled
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "首页轮播图",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "显示首页顶部推荐内容",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LiquidToggle(
            selected = { enabled },
            onSelect = onEnabledChange,
            backdrop = toggleBackdrop,
            modifier = Modifier.width(64.dp).height(40.dp),
        )
    }
}

/** Product-level Liquid Glass control; deliberately uses no Material Slider. */
@Composable
private fun LiquidGlassIntensitySetting(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val progress = value.coerceIn(0f, 1f)
    val percent = (progress * 100f).roundToInt()
    val defaultProgress = DefaultGlassIntensity
    val defaultSnapThreshold = 0.045f
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "液态玻璃",
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "$percent%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        Text(
            "调节液态玻璃的背景模糊强度",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun update(position: Offset) {
                            val rawProgress = (position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val snappedProgress = if (
                                abs(rawProgress - defaultProgress) <= defaultSnapThreshold
                            ) {
                                defaultProgress
                            } else {
                                rawProgress
                            }
                            latestOnValueChange.value(snappedProgress)
                        }
                        update(down.position)
                        down.consume()
                        drag(down.id) { change ->
                            update(change.position)
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val trackHeight = 6.dp
            val knobSize = 22.dp
            val knobOffset = (maxWidth - knobSize) * progress
            Box(
                Modifier.fillMaxWidth().height(trackHeight).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)),
            )
            Box(
                Modifier.fillMaxWidth(progress).height(trackHeight).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Box(
                Modifier
                    .offset(x = (maxWidth - 2.dp) * defaultProgress)
                    .width(2.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .58f)),
            )
            Box(
                Modifier.offset(x = knobOffset).size(knobSize)
                    .clip(RoundedCornerShape(knobSize / 2))
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
        Box(Modifier.fillMaxWidth()) {
            Text("无背景模糊", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(
                "默认",
                Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "高斯模糊最强",
                Modifier.align(Alignment.CenterEnd),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

internal const val DarkAppearanceTitle = "深色"

@Composable
fun GlassStyleScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Liquid,
    liquidGlassIntensity: Float = DefaultGlassIntensity,
    saving: Boolean = false,
    error: String? = null,
    onSelectStyle: (GlassStyle) -> Unit = {},
    onLiquidGlassIntensityChanged: (Float) -> Unit = {},
) {
    val safeArea = LocalMaFeiSafeArea.current
    MaFeiActionBarScaffold(
        title = "风格设置",
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
                LegacyListGroup(
                    items = GlassStyle.entries.map {
                        LegacyListGroupItem(it.title, isSelected = it == style)
                    },
                    enabled = !saving,
                    onItemClick = { onSelectStyle(GlassStyle.entries[it]) },
                )
                if (style == GlassStyle.Liquid) {
                    LiquidGlassIntensitySetting(
                        value = liquidGlassIntensity,
                        onValueChange = onLiquidGlassIntensityChanged,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    mode: cn.mafei.cmp.common.design_system.AppearanceMode = cn.mafei.cmp.common.design_system.AppearanceMode.Dark,
    saving: Boolean = false,
    error: String? = null,
    onSelect: (cn.mafei.cmp.common.design_system.AppearanceMode) -> Unit = {},
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
                val modes = cn.mafei.cmp.common.design_system.AppearanceMode.entries
                LegacyListGroup(
                    items = modes.map { LegacyListGroupItem(it.title, isSelected = it == mode) },
                    enabled = !saving,
                    onItemClick = { onSelect(modes[it]) },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
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

typealias AccountSwitchItem = SavedAccountListItem
internal typealias AccountSwitchGroup = SavedAccountListGroup

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
    Box(modifier.fillMaxSize().onGloballyPositioned { rootBounds = it.boundsInRoot() }) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .captureGlassBackdrop(backdrop),
        ) {
            Spacer(Modifier.height(safeArea.top + 56.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MaFeiLoadingIndicator(
                            modifier = Modifier.size(40.dp),
                            style = MaFeiLoadingStyle.Page,
                        )
                    }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        MaFeiSavedAccountList(
                            items = items,
                            enabled = busyKey == null && deletingKey == null,
                            busyKey = busyKey,
                            deletingKey = deletingKey,
                            deleteAffordance = SavedAccountDeleteAffordance.Button,
                            onAccount = { item -> if (!item.isCurrent) onAccount(item) },
                            onDelete = { item -> if (!item.isCurrent) pendingDelete = item },
                            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp)
                                .padding(top = 10.dp, bottom = safeArea.bottom + 10.dp),
                            showCurrentLabel = true,
                            showRememberedPassword = true,
                            emptyContent = {
                                Column(
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
                                    Text(
                                        "暂无已保存账号",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                    )
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
                                            ).padding(horizontal = 24.dp, vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            },
                        )
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

internal fun accountSwitchGroups(items: List<AccountSwitchItem>): List<AccountSwitchGroup> {
    return savedAccountListGroups(items)
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
