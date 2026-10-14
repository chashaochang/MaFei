package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.LocalMaFeiPopupColors
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyListGroup
import cn.mafei.cmp.common.ui.LegacyListGroupItem
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiActionBarScaffold
import cn.mafei.cmp.common.ui.MaFeiPageTitleBar
import cn.mafei.cmp.common.ui.MaFeiPageTitleBarHeight
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.MaFeiProgressLine
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.common.ui.mafeiArtworkFeedback
import cn.mafei.cmp.common.ui.mafeiMediaCardInteraction
import cn.mafei.cmp.common.ui.rememberMediaCardInteraction
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.PlaybackHistoryItem
import cn.mafei.cmp.core.domain.movieRatingText
import cn.mafei.cmp.core.domain.progressPercent
import cn.mafei.cmp.core.domain.titleText
import cn.mafei.cmp.core.domain.updateBadgeText
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.flow.distinctUntilChanged

data class MineProfile(
    val userName: String,
    val serverName: String,
    val serverVersion: String = "",
    val connectionDescription: String = "",
    val policy: String = "",
)

/** Mechanical Compose translation of ArkUI `MineTab`. */
@Composable
fun MineScreen(
    profile: MineProfile,
    historyItems: List<PlaybackHistoryItem>,
    historyLoading: Boolean,
    historyError: String?,
    layout: MaFeiLayout,
    busy: Boolean,
    onHistoryItem: (MediaItem) -> Unit,
    onHistoryRetry: () -> Unit,
    onHistoryMore: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSettings: () -> Unit,
    onPrivacy: () -> Unit,
    onAddGroup: () -> Unit,
    onLogout: () -> Unit,
    onWebITong: (() -> Unit)? = null,
    onManagement: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    versionName: String = "",
    avatar: (@Composable (Modifier) -> Unit)? = null,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val bottomPadding = safeArea.bottom +
        if (layout.navigation == NavigationPlacement.Bottom) 112.dp else 28.dp
    // 顶部占位与旧版一致：`destinationOwnsTitleBar` 时不留（宽屏工作区自己带 chrome），
    // 手机标签页留 `safeTop + ACTION_BAR_HEIGHT(56)`。旧版那个高度的位置**不画标题栏**
    // （`ActionBar(title: '我的', showBackButton: true)` 只在 `fromHome` 为真时显示，
    // 也就是「我的」被当页面推出来的时候；作为底部标签时它是空的）。
    val destinationOwnsTitleBar = layout.navigation != NavigationPlacement.Bottom
    val columns = if (layout.gridWidthDp >= 840f) 4 else 3
    val visibleHistory = historyItems.take(if (columns == 4) 8 else 6)
    val backdrop = rememberGlassBackdrop()

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
            // 这里原来画 `bg_mine`（浅灰底 + 播放按钮水印）。那是**飞牛主题**的资源：旧版的
            // 判据是 `AppThemeTokens.showMineBackground(style) = (style === ThemeStyle.Feiniu)`，
            // 而飞牛主题当前是「暂时不做」的，所以本应用（深色玻璃主题）不该画它 ——
            // 在深色底上它是一块没有来历的浅色水印，看起来就是"背景很奇怪"。
            // 将来真做飞牛主题时，按同样的判据把这段加回来（objectFit 用 Contain）。
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Wide HomeMineWorkspace owns the surrounding chrome; the phone
                // MineTab reserves the fixed ActionBar exactly as the ArkUI page.
                if (!destinationOwnsTitleBar) {
                    Spacer(Modifier.height(safeArea.top + MaFeiPageTitleBarHeight))
                }
                Column(
                    Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 16.dp)
                        .padding(bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MineProfileHeader(profile, avatar)

                    if (onManagement != null) LegacyListGroup(
                        listOf(LegacyListGroupItem("服务器控制台", MaFeiLegacyDrawables.settings)),
                        { onManagement() },
                    )

                    when {
                        historyLoading -> MineHistoryLoadingSection(columns)
                        historyError != null -> MineHistoryErrorSection(onRetry = onHistoryRetry)
                        visibleHistory.isNotEmpty() -> MineHistorySection(
                            items = visibleHistory,
                            columns = columns,
                            onItem = onHistoryItem,
                            onMore = onHistoryMore,
                            artwork = artwork,
                        )
                    }

                    LegacyListGroup(
                        items = listOf(
                            LegacyListGroupItem("切换账号", MaFeiLegacyDrawables.accountSwitch),
                        ) + if (onWebITong != null) listOf(LegacyListGroupItem("网(Web)易通", MaFeiLegacyDrawables.web)) else emptyList(),
                        onItemClick = { index -> if (index == 0) onSwitchAccount() else onWebITong?.invoke() },
                    )
                    LegacyListGroup(
                        items = listOf(
                            LegacyListGroupItem("设置", MaFeiLegacyDrawables.settings),
                        ),
                        onItemClick = { onSettings() },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegacyListGroup(
                            items = listOf(
                                LegacyListGroupItem("隐私协议", MaFeiLegacyDrawables.privacy),
                                LegacyListGroupItem("加入群聊", MaFeiLegacyDrawables.addGroup),
                            ),
                            onItemClick = { index ->
                                when (index) {
                                    0 -> onPrivacy()
                                    1 -> onAddGroup()
                                }
                            },
                        )
                        if (versionName.isNotBlank()) {
                            Text(
                                versionName,
                                Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    LegacyListGroup(
                        items = listOf(LegacyListGroupItem("退出登录", showArrow = false)),
                        onItemClick = { if (!busy) onLogout() },
                    )
                }
            }
        }
        if (!destinationOwnsTitleBar) {
            // 与「媒体库」「收藏」共用同一条一级页标题栏（无返回、玻璃材质、内容从它下面滚过）。
            MaFeiPageTitleBar(
                title = "我的",
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun MineProfileHeader(
    profile: MineProfile,
    avatar: (@Composable (Modifier) -> Unit)?,
) {
    val popupColors = LocalMaFeiPopupColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(popupColors?.item ?: MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))) {
            if (avatar != null) {
                avatar(Modifier.fillMaxSize())
            } else {
                Image(
                    painter = painterResource(MaFeiLegacyDrawables.defaultAvatar),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    profile.userName,
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.policy.isNotBlank()) {
                    Text(
                        profile.policy,
                        Modifier.clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                    )
                }
            }
            Text(
                profile.serverName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (profile.serverVersion.isNotBlank()) {
                Text(
                    profile.serverVersion,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .76f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (profile.connectionDescription.isNotBlank()) {
                Text(
                    profile.connectionDescription,
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MineHistorySection(
    items: List<PlaybackHistoryItem>,
    columns: Int,
    onItem: (MediaItem) -> Unit,
    onMore: () -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
) {
    val popupColors = LocalMaFeiPopupColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(popupColors?.item ?: MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "最近在看",
                Modifier.weight(1f).semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(
                Modifier.height(36.dp).mafeiInteractive(onMore, radiusDp = 18f, label = "更多")
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("更多", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Image(
                    painter = painterResource(MaFeiLegacyDrawables.arrowRight),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
        MineHistoryRows(items, columns, onItem, artwork)
    }
}

@Composable
private fun MineHistoryRows(
    items: List<PlaybackHistoryItem>,
    columns: Int,
    onItem: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { item ->
                    MineHistoryPoster(
                        item = item,
                        onClick = { onItem(item.media) },
                        modifier = Modifier.weight(1f),
                        artwork = artwork,
                    )
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MineHistoryPoster(
    item: PlaybackHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
) {
    val popupColors = LocalMaFeiPopupColors.current
    val badgeText = item.movieRatingText ?: item.updateBadgeText
    val progress = item.progressPercent
    val interaction = rememberMediaCardInteraction()
    Column(
        modifier.mafeiMediaCardInteraction(interaction, onClick, label = item.titleText),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).mafeiArtworkFeedback(interaction, RoundedCornerShape(10.dp))
                .background(popupColors?.container ?: MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (artwork != null) artwork(item.media, Modifier.fillMaxSize())
            else Image(
                painter = painterResource(MaFeiLegacyDrawables.noHistory),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Inside,
            )
            badgeText?.let { text ->
                Text(
                    text,
                    Modifier.align(Alignment.BottomEnd).padding(
                        end = 4.dp,
                        bottom = if (progress != null) 7.dp else 4.dp,
                    ).clip(RoundedCornerShape(5.dp)).background(Color(0x99000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            progress?.let { percent ->
                MaFeiProgressLine(
                    progress = percent / 100f,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    progressColor = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = .32f),
                )
            }
        }
        Text(
            item.titleText,
            Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MineHistoryLoadingSection(columns: Int) {
    val popupColors = LocalMaFeiPopupColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(popupColors?.item ?: MaterialTheme.colorScheme.surface.copy(alpha = .82f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "最近在看",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(columns) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Box(
                                Modifier.fillMaxWidth(.72f).height(13.dp).clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MineHistoryErrorSection(onRetry: () -> Unit) {
    val popupColors = LocalMaFeiPopupColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(popupColors?.item ?: MaterialTheme.colorScheme.surface.copy(alpha = .82f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "最近在看",
            Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            Modifier.fillMaxWidth().height(64.dp)
                .mafeiInteractive(onRetry, radiusDp = 0f, label = "播放历史加载失败，点击重试"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "播放历史加载失败，点击重试",
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Image(
                painter = painterResource(MaFeiLegacyDrawables.replay),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

/** Mechanical Compose translation of ArkUI `MineHistoryPage`. */
@Composable
fun MineHistoryScreen(
    items: List<PlaybackHistoryItem>,
    loading: Boolean,
    error: String?,
    layout: MaFeiLayout,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onItem: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    canLoadMore: Boolean = false,
    loadingMore: Boolean = false,
    loadMoreFailed: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    onRetryLoadMore: (() -> Unit)? = null,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val columns = when {
        layout.gridWidthDp >= 1920f -> 8
        layout.gridWidthDp >= 1440f -> 6
        layout.gridWidthDp >= 840f -> 4
        else -> 3
    }
    val horizontalPadding = if (layout.gridWidthDp < 600f) 16.dp else 24.dp
    MaFeiActionBarScaffold(
        title = "播放历史",
        onBack = onBack,
        modifier = modifier,
    ) {
        when {
            loading -> MineHistoryPageLoadingGrid(
                columns = columns,
                modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding),
            )
            error != null -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("播放历史加载失败，点击重试", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    "重试",
                    Modifier.height(44.dp).mafeiInteractive(onRetry, radiusDp = 22f, label = "重试")
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painterResource(MaFeiLegacyDrawables.noHistory),
                    null,
                    Modifier.size(160.dp),
                    contentScale = ContentScale.Inside,
                )
                Text("暂无播放记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val gridState = rememberLazyGridState()
                val currentLoadMore by rememberUpdatedState(onLoadMore)
                val currentLoadingMore by rememberUpdatedState(loadingMore)
                val currentLoadMoreFailed by rememberUpdatedState(loadMoreFailed)
                // 触底续页：resume + 已看完两路的游标由 browser 决定是否还有。
                LaunchedEffect(gridState, canLoadMore, items.size) {
                    snapshotFlow {
                        val info = gridState.layoutInfo
                        (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
                    }.distinctUntilChanged().collect { (last, total) ->
                        if (canLoadMore && !currentLoadingMore && !currentLoadMoreFailed &&
                            total > 0 && last >= total - 4
                        ) {
                            currentLoadMore?.invoke()
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth().fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        top = 12.dp,
                        end = horizontalPadding,
                        bottom = safeArea.bottom + 20.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(items, key = { item -> historyItemKey(item) }) { item ->
                        MineHistoryPoster(
                            item = item,
                            onClick = { onItem(item.media) },
                            artwork = artwork,
                        )
                    }
                    if (loadingMore || loadMoreFailed) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            if (loadingMore) {
                                Row(
                                    Modifier.fillMaxWidth().height(52.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MaFeiLoadingIndicator(
                                        modifier = Modifier.size(24.dp),
                                        style = MaFeiLoadingStyle.Inline,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "正在加载",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                    )
                                }
                            } else {
                                Box(
                                    Modifier.fillMaxWidth().height(44.dp)
                                        .mafeiInteractive(
                                            onClick = { onRetryLoadMore?.invoke() },
                                            radiusDp = 22f,
                                            label = "加载更多失败，点击重试",
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "加载更多失败，点击重试",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
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

@Composable
private fun MineHistoryPageLoadingGrid(
    columns: Int,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false,
    ) {
        items(columns * 3) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    Modifier.fillMaxWidth(.72f).height(13.dp).clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

private fun historyItemKey(item: PlaybackHistoryItem): String =
    "${item.media.id.value}|${item.latestSeasonNumber ?: 0}|${item.latestEpisodeNumber ?: 0}|" +
        if (item.seriesHasMultipleSeasons) "1" else "0"
