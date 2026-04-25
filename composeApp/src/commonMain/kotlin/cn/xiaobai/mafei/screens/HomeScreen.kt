package cn.xiaobai.mafei.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.xiaobai.mafei.data.ContinueWatchingItem
import cn.xiaobai.mafei.data.HomeState
import cn.xiaobai.mafei.data.HomeMediaView
import cn.xiaobai.mafei.data.LibraryItem
import cn.xiaobai.mafei.data.NextUpItem
import cn.xiaobai.mafei.data.RemoteArtworkImage
import cn.xiaobai.mafei.data.UpdateItem
import cn.xiaobai.mafei.data.jellyfin.messageWithHint

@Composable
fun HomeScreen(
    state: HomeState,
    isLoading: Boolean,
    onOpenUpdates: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenMediaView: (HomeMediaView) -> Unit,
    onOpenDetail: (String) -> Unit,
    onPlayContinue: (String) -> Unit,
    onPlayNextUp: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedViewId by remember(state.mediaViews.firstOrNull()?.viewId) {
        mutableStateOf(state.mediaViews.firstOrNull()?.viewId)
    }
    val selectedMediaView = state.mediaViews.firstOrNull { it.viewId == selectedViewId }
        ?: state.mediaViews.firstOrNull()
    val hasAnyContent = state.continueWatching.isNotEmpty() ||
        state.nextUp.isNotEmpty() ||
        state.updates.isNotEmpty() ||
        state.mediaViews.isNotEmpty()
    val showFullscreenLoading = isLoading && !hasAnyContent

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HomeOverviewStrip(
                continueCount = state.continueWatching.size,
                nextUpCount = state.nextUp.size,
                updateCount = state.updates.size,
                hasLoadIssue = state.loadIssue != null,
            )
        }

        item {
            HomeFeedSummaryCard(
                continueCount = state.continueWatching.size,
                nextUpCount = state.nextUp.size,
                updateCount = state.updates.size,
                isLoading = isLoading,
                hasLoadIssue = state.loadIssue != null,
                firstContinueItem = state.continueWatching.firstOrNull(),
                firstNextUpItem = state.nextUp.firstOrNull(),
                onPlayContinue = onPlayContinue,
                onPlayNextUp = onPlayNextUp,
                onOpenUpdates = onOpenUpdates,
                onOpenSearch = onOpenSearch,
                onOpenFavorites = onOpenFavorites,
            )
        }

        item {
            HomeQuickActions(
                onOpenFavorites = onOpenFavorites,
                onOpenUpdates = onOpenUpdates,
                onOpenSearch = onOpenSearch,
            )
        }

        if (showFullscreenLoading) {
            item { HomeLoadingHero() }
            item { HomeSectionSkeleton(title = "继续观看") }
            item { HomeSectionSkeleton(title = "下一集推荐") }
            item { HomeSectionSkeleton(title = "已关注更新", cardCount = 3) }
        } else if (!hasAnyContent && state.loadIssue != null) {
            item {
                HomeErrorState(
                    message = state.loadIssue.messageWithHint(),
                    onRetry = onRetry,
                    onOpenAccount = onOpenAccount,
                )
            }
        } else if (!hasAnyContent) {
            item {
                HomeEmptyState(
                    onRetry = onRetry,
                    onOpenSearch = onOpenSearch,
                )
            }
        } else {
            if (isLoading) {
                item {
                    AppStatusCard(
                        title = "首页同步中",
                        message = "正在整理首页内容，完成后会自动刷新当前页面。",
                        tone = AppStatusTone.Progress,
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            state.loadIssue?.let { issue ->
                item {
                    AppStatusCard(
                        title = "首页同步遇到问题",
                        message = "这次没有拿到完整首页内容：${issue.messageWithHint()}",
                        supportingText = "当前先展示可用内容，你可以继续浏览，也可以稍后再刷新一次。",
                        tone = AppStatusTone.Warning,
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        action = {
                            OutlinedButton(onClick = onRetry) {
                                Text("继续刷新")
                            }
                        },
                    )
                }
            }

            if (state.continueWatching.isNotEmpty()) {
                item {
                    AppHeaderRow(
                        title = "继续观看",
                        subtitle = "从上次中断的位置继续",
                        leadingIcon = Icons.Filled.PlayArrow,
                        leadingEmphasized = false,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.continueWatching, key = { it.itemId }) { item ->
                            ContinueWatchingPoster(
                                item = item,
                                onPlay = { onPlayContinue(item.itemId) },
                                onOpenDetail = { onOpenDetail(item.itemId) },
                            )
                        }
                    }
                }
            }

            if (state.nextUp.isNotEmpty()) {
                val previewNextUp = state.nextUp.take(3)
                item {
                    AppHeaderRow(
                        title = "下一集推荐",
                        subtitle = if (state.nextUp.size > previewNextUp.size) {
                            "先展示最值得立刻开播的 ${previewNextUp.size} 项"
                        } else {
                            "优先展示最接近起播的内容"
                        },
                        leadingIcon = Icons.Filled.Notifications,
                        leadingEmphasized = false,
                        trailing = {
                            AppPill(text = "共 ${state.nextUp.size} 项")
                        },
                    )
                }
                item {
                    NextUpPreviewSection(
                        items = previewNextUp,
                        onPlayNextUp = onPlayNextUp,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }

            if (state.updates.isNotEmpty()) {
                val previewUpdates = state.updates.take(4)
                item {
                    AppHeaderRow(
                        title = "已关注更新",
                        subtitle = if (state.updates.size > previewUpdates.size) {
                            "先看最近最值得处理的 ${previewUpdates.size} 条更新"
                        } else {
                            "新剧集和最近变更优先提醒"
                        },
                        leadingIcon = Icons.Filled.Refresh,
                        leadingEmphasized = false,
                        trailing = {
                            OutlinedButton(onClick = onOpenUpdates) {
                                Text("查看全部")
                            }
                        },
                    )
                }
                item {
                    UpdatesPreviewSection(
                        items = previewUpdates,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }

            if (state.mediaViews.isNotEmpty() && selectedMediaView != null) {
                item {
                    AppHeaderRow(
                        title = "内容分区",
                        subtitle = "按媒体库分区继续浏览",
                        leadingIcon = Icons.AutoMirrored.Filled.List,
                        leadingEmphasized = false,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.mediaViews, key = { it.viewId }) { view ->
                            val selected = view.viewId == selectedMediaView.viewId
                            if (selected) {
                                Button(onClick = {}, enabled = false) {
                                    Text(view.title)
                                }
                            } else {
                                OutlinedButton(onClick = { selectedViewId = view.viewId }) {
                                    Text(view.title)
                                }
                            }
                        }
                    }
                }
                item {
                    MediaViewPreviewCard(
                        view = selectedMediaView,
                        onOpenBrowse = { onOpenMediaView(selectedMediaView) },
                        onOpenDetail = onOpenDetail,
                    )
                }
            }

        }
    }
}

@Composable
private fun HomeOverviewStrip(
    continueCount: Int,
    nextUpCount: Int,
    updateCount: Int,
    hasLoadIssue: Boolean,
) {
    AppSectionCard(
        secondary = true,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (hasLoadIssue) "当前是降级浏览模式" else "今天的内容概览",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppPill(
                    text = "续播 $continueCount",
                    emphasized = continueCount > 0,
                    modifier = Modifier.weight(1f),
                )
                AppPill(
                    text = "下一集 $nextUpCount",
                    emphasized = nextUpCount > 0,
                    modifier = Modifier.weight(1f),
                )
                AppPill(
                    text = "更新 $updateCount",
                    emphasized = updateCount > 0,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = if (hasLoadIssue) {
                    "首页还在补齐数据，先给你保留最核心的入口和可操作内容。"
                } else {
                    "首页优先保留值得立刻处理的内容，其余内容继续去各分区深入浏览。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextUpPreviewSection(
    items: List<NextUpItem>,
    onPlayNextUp: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    AppSectionCard(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEachIndexed { index, item ->
                CompactFeedRow(
                    title = item.title,
                    subtitle = "下一集：${item.episodeTitle}",
                    trailing = item.subtitle,
                    badge = if (index == 0) "优先播放" else "待续播",
                    onPrimaryClick = { onPlayNextUp(item.itemId) },
                    onSecondaryClick = { onOpenDetail(item.itemId) },
                    primaryLabel = "播放",
                    secondaryLabel = "详情",
                )
            }
        }
    }
}

@Composable
private fun UpdatesPreviewSection(
    items: List<UpdateItem>,
    onOpenDetail: (String) -> Unit,
) {
    AppSectionCard(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { item ->
                CompactFeedRow(
                    title = item.title,
                    subtitle = "最新未读：${item.latestEpisodeTitle}",
                    trailing = if (item.newEpisodeCount > 1) {
                        "本轮新增 ${item.newEpisodeCount} 集"
                    } else {
                        "本轮新增 1 集"
                    },
                    badge = if (item.newEpisodeCount > 1) "建议补更" else "轻量更新",
                    onPrimaryClick = { onOpenDetail(item.itemId) },
                    onSecondaryClick = null,
                    primaryLabel = "查看",
                )
            }
        }
    }
}

@Composable
private fun CompactFeedRow(
    title: String,
    subtitle: String,
    trailing: String,
    badge: String,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: (() -> Unit)? = null,
    primaryLabel: String,
    secondaryLabel: String = "更多",
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppPill(
                    text = badge,
                    emphasized = true,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier.weight(if (onSecondaryClick != null) 1f else 0.46f),
                ) {
                    Text(primaryLabel)
                }
                if (onSecondaryClick != null) {
                    OutlinedButton(
                        onClick = onSecondaryClick,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(secondaryLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFeedSummaryCard(
    continueCount: Int,
    nextUpCount: Int,
    updateCount: Int,
    isLoading: Boolean,
    hasLoadIssue: Boolean,
    firstContinueItem: ContinueWatchingItem?,
    firstNextUpItem: NextUpItem?,
    onPlayContinue: (String) -> Unit,
    onPlayNextUp: (String) -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val primaryActionLabel = when {
        isLoading -> "正在同步"
        firstContinueItem != null -> "继续观看"
        firstNextUpItem != null -> "播放下一集"
        updateCount > 0 -> "查看更新"
        else -> "去搜索"
    }
    val primaryAction: () -> Unit = {
        when {
            firstContinueItem != null -> onPlayContinue(firstContinueItem.itemId)
            firstNextUpItem != null -> onPlayNextUp(firstNextUpItem.itemId)
            updateCount > 0 -> onOpenUpdates()
            else -> onOpenSearch()
        }
    }
    val heroSubtitle = when {
        isLoading -> "正在同步你的续播和追更内容，稍后可直接进入主线。"
        firstContinueItem != null -> "主线建议：从《${firstContinueItem.title}》继续，减少切换成本。"
        firstNextUpItem != null -> "主线建议：优先处理下一集推荐，形成连续观看节奏。"
        updateCount > 0 -> "有更新提醒可处理，先清理追更任务更高效。"
        else -> "暂无主线任务，可通过搜索快速进入想看的内容。"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        val isCompact = maxWidth < 420.dp
        Surface(
            shape = RoundedCornerShape(if (isCompact) 18.dp else 20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.surface,
                            )
                        )
                    )
                    .padding(horizontal = if (isCompact) 14.dp else 16.dp, vertical = if (isCompact) 14.dp else 16.dp)
                    .animateContentSize(animationSpec = tween(durationMillis = 220)),
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 12.dp),
            ) {
                AnimatedContent(
                    targetState = isLoading,
                    label = "home_hero_title",
                ) { syncing ->
                    Text(
                        text = if (syncing) "正在整理你的今日片单" else "今晚看什么",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = heroSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeHeroStatChip(
                        title = "续播",
                        value = continueCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    HomeHeroStatChip(
                        title = "下一集",
                        value = nextUpCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    HomeHeroStatChip(
                        title = "更新",
                        value = updateCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                AppPill(
                    text = if (isLoading) "首页同步中" else "今日推荐入口",
                    emphasized = true,
                )
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = primaryAction,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AnimatedContent(
                                targetState = primaryActionLabel,
                                label = "home_primary_action_compact",
                            ) { label ->
                                Text(label)
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenFavorites,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("我的收藏")
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = primaryAction,
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                        ) {
                            AnimatedContent(
                                targetState = primaryActionLabel,
                                label = "home_primary_action_regular",
                            ) { label ->
                                Text(label)
                            }
                        }
                        OutlinedButton(
                            onClick = if (updateCount > 0) onOpenUpdates else onOpenFavorites,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (updateCount > 0) "查看更新" else "我的收藏")
                        }
                    }
                }
                AnimatedVisibility(
                    visible = hasLoadIssue,
                    enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                    exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
                ) {
                    Text(
                        text = "当前先展示已恢复的首页内容，稍后再同步一次会更完整。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeroStatChip(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeQuickActions(
    onOpenFavorites: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    AppSectionCard(shape = RoundedCornerShape(18.dp), secondary = true) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppHeaderRow(
                title = "快捷入口",
                subtitle = "从这里快速回到收藏、更新和搜索。",
                leadingIcon = Icons.Filled.Home,
                leadingEmphasized = false,
                trailing = {
                    AppPill(text = "高频入口")
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickActionCard(
                    title = "收藏",
                    subtitle = "回到已经收好的内容清单",
                    icon = Icons.Filled.Favorite,
                    statusLabel = "常看内容",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFavorites,
                )
                QuickActionCard(
                    title = "更新",
                    subtitle = "继续处理最新剧集提醒",
                    icon = Icons.Filled.Notifications,
                    statusLabel = "追更中心",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenUpdates,
                )
            }
            OutlinedButton(
                onClick = onOpenSearch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("搜索更多内容")
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    statusLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    ),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppPill(text = statusLabel, emphasized = true)
        }
    }
}

@Composable
private fun HomeEmptyState(
    onRetry: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "暂无可展示内容",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "请确认服务器可用，或稍后再次刷新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) {
                    Text("刷新")
                }
                OutlinedButton(onClick = onOpenSearch) {
                    Text("去搜索")
                }
            }
        }
    }
}

@Composable
private fun HomeErrorState(
    message: String,
    onRetry: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Text(
                text = "首页暂时还没准备好",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) {
                    Text("继续刷新")
                }
                OutlinedButton(onClick = onOpenAccount) {
                    Text("服务器设置")
                }
            }
        }
    }
}

@Composable
private fun HomeLoadingHero() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeSkeletonBlock(width = 140.dp, height = 22.dp)
            HomeSkeletonBlock(width = 220.dp, height = 14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeSkeletonBlock(modifier = Modifier.weight(1f), height = 72.dp)
                HomeSkeletonBlock(modifier = Modifier.weight(1f), height = 72.dp)
            }
        }
    }
}

@Composable
private fun HomeSectionSkeleton(
    title: String,
    cardCount: Int = 2,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppSectionHeader(
            title = title,
            subtitle = "正在加载内容…",
        )
        repeat(cardCount) {
            HomeSkeletonBlock(
                modifier = Modifier.fillMaxWidth(),
                height = if (cardCount <= 2) 198.dp else 96.dp,
            )
        }
    }
}

@Composable
private fun HomeSkeletonBlock(
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp? = null,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
    )
}

@Composable
private fun ContinueWatchingPoster(
    item: ContinueWatchingItem,
    onPlay: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    Card(
        modifier = Modifier.width(248.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface,
                            )
                        )
                    )
            ) {
                RemoteArtworkImage(
                    imageUrl = item.backdropUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.52f),
                                )
                            )
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.episodeTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppPill(text = "续播主线", emphasized = true)
                    AppPill(text = "${item.progressPercent}%")
                }
                Text(
                    item.progressLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { item.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                        Text("继续播放")
                    }
                    OutlinedButton(
                        onClick = onOpenDetail,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("详情")
                    }
                }
            }
            Box(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun UpdateRowCard(
    item: UpdateItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                RemoteArtworkImage(
                    imageUrl = item.thumbnailUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    showPlaceholder = false,
                    enableCrossfade = false,
                )
                Text(
                    text = item.title.take(1),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "最新未读：${item.latestEpisodeTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AppPill(
                    text = if (item.newEpisodeCount > 1) "建议优先补更" else "本轮可快速补完",
                    emphasized = item.newEpisodeCount > 1,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    "更新 ${item.newEpisodeCount} 集",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaViewPreviewCard(
    view: HomeMediaView,
    onOpenBrowse: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(view.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        view.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onOpenBrowse) {
                    Text("进入分区")
                }
            }
            AppInlineTip(
                message = if (view.items.isEmpty()) {
                    "当前分区还没有可预览内容，可以稍后再回来看看。"
                } else {
                    "先从预览里挑一项进入详情，或直接进入分区连续浏览。"
                },
                tone = AppStatusTone.Neutral,
            )
            if (view.items.isEmpty()) {
                Text("当前分区暂无内容。", style = MaterialTheme.typography.bodyMedium)
            } else {
                view.items.take(6).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenDetail(item.itemId) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.colorScheme.surface,
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            RemoteArtworkImage(
                                imageUrl = item.posterUrl,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Text(
                                text = item.title.take(1),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            AppPill(text = "进入详情后可继续浏览")
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "进入详情",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextUpCard(
    item: NextUpItem,
    onPlay: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface,
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    RemoteArtworkImage(
                        imageUrl = item.thumbnailUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        showPlaceholder = false,
                        enableCrossfade = false,
                    )
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "下一集：${item.episodeTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppPill(text = "下一步建议先播这集", emphasized = true)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                    Text("播放下一集")
                }
                OutlinedButton(
                    onClick = onOpenDetail,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("详情")
                }
            }
        }
    }
}

@Composable
private fun LibraryPosterCard(
    item: LibraryItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(164.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(214.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface,
                            )
                        )
                    )
            ) {
                RemoteArtworkImage(
                    imageUrl = item.posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = item.title.take(1),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp),
                )
                val badge = item.subtitle.split(" · ").firstOrNull()?.takeIf { it.isNotBlank() }
                if (badge != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.32f),
                    contentColor = Color.White,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(modifier = Modifier.height(2.dp))
        }
    }
}
