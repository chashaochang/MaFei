package cn.xiaobai.mafei.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.xiaobai.mafei.data.LibraryItem
import cn.xiaobai.mafei.data.UpdateItem

@Composable
fun UpdatesScreen(
    updates: List<UpdateItem>,
    recentlyAdded: List<LibraryItem>,
    isLoading: Boolean,
    isShowingStaleData: Boolean,
    loadIssueMessage: String?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalNewEpisodes = updates.sumOf { it.newEpisodeCount }
    val totalRecentAdds = recentlyAdded.size
    var completedItemIds by remember(updates.map { it.itemId }) {
        mutableStateOf(emptySet<String>())
    }
    val completedUpdates = updates.filter { it.itemId in completedItemIds }
    val pendingUpdates = updates.filterNot { it.itemId in completedItemIds }
    val prioritizedPendingUpdates = pendingUpdates.sortedWith(
        compareByDescending<UpdateItem> { it.newEpisodeCount }.thenBy { it.title }
    )
    val supplementalRecentlyAdded = if (updates.isEmpty()) {
        recentlyAdded.take(8)
    } else {
        recentlyAdded.take(3)
    }
    val hasLoadIssue = !loadIssueMessage.isNullOrBlank()
    val hasAnyContent = updates.isNotEmpty() || recentlyAdded.isNotEmpty()
    val showFullscreenLoading = isLoading && !hasAnyContent
    val completionPercent = if (updates.isEmpty()) 0 else {
        ((completedUpdates.size.toFloat() / updates.size.toFloat()) * 100).toInt()
    }

    BoxWithConstraints(modifier = modifier) {
        val isCompact = maxWidth < 420.dp
        val horizontalPadding = if (isCompact) 12.dp else 16.dp
        val sectionSpacing = if (isCompact) 12.dp else 16.dp
        val cardCorner = if (isCompact) 16.dp else 20.dp

        LazyColumn(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            item {
                AppSectionCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isCompact) 8.dp else 10.dp)
                        .animateContentSize(),
                    secondary = true,
                    shape = RoundedCornerShape(if (isCompact) 14.dp else 16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isCompact) 12.dp else 14.dp, vertical = if (isCompact) 10.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppHeaderRow(
                            title = "追更中心",
                            subtitle = "优先展示有新集的条目，最近入库仅作补充发现。",
                            leadingIcon = Icons.Filled.Notifications,
                            leadingEmphasized = false,
                            trailing = {
                                OutlinedButton(
                                    onClick = onRefresh,
                                    enabled = !isLoading,
                                ) {
                                    Text(if (isLoading) "同步中" else "刷新")
                                }
                            },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppPill(
                                text = "待处理 ${pendingUpdates.size}",
                                emphasized = pendingUpdates.isNotEmpty(),
                            )
                            AppPill(text = "已处理 ${completedUpdates.size}")
                            AppPill(text = "完成 $completionPercent%")
                        }

                        AnimatedVisibility(visible = isShowingStaleData && hasAnyContent) {
                            AppInlineTip(
                                message = "最新更新还在整理中，当前先展示刚才可用的结果。",
                                tone = AppStatusTone.Warning,
                            )
                        }
                    }
                }
            }

            item {
                UpdatesSummaryCard(
                    totalUpdateSeriesCount = updates.size,
                    pendingSeriesCount = pendingUpdates.size,
                    completedSeriesCount = completedUpdates.size,
                    totalNewEpisodes = totalNewEpisodes,
                    recentlyAddedCount = totalRecentAdds,
                    hasLoadIssue = hasLoadIssue,
                    isLoading = isLoading,
                    isShowingStaleData = isShowingStaleData,
                    isCompact = isCompact,
                )
            }

        if (updates.isNotEmpty()) {
            item {
                UpdatesTaskProgressCard(
                    pendingSeriesCount = pendingUpdates.size,
                    completedSeriesCount = completedUpdates.size,
                    totalSeriesCount = updates.size,
                    topPending = prioritizedPendingUpdates.firstOrNull(),
                    isCompact = isCompact,
                    onResetProgress = {
                        completedItemIds = emptySet()
                    },
                )
            }
        }

        if (showFullscreenLoading) {
            item { UpdatesLoadingState() }
        } else if (!hasAnyContent && hasLoadIssue) {
            item {
                UpdatesErrorState(
                    message = loadIssueMessage.orEmpty(),
                    onRetry = onRetry,
                )
            }
        } else if (!hasAnyContent) {
            item { UpdatesEmptyState(onRetry = onRetry) }
        } else {
                item {
                    AnimatedVisibility(visible = isLoading) {
                        UpdatesSyncingStrip()
                    }
                }

                item {
                    AnimatedVisibility(visible = hasLoadIssue) {
                        UpdatesErrorStrip(
                            message = if (isShowingStaleData) {
                                "这次没能拿到完整更新，当前先展示可用内容：${loadIssueMessage.orEmpty()}"
                            } else {
                                "更新列表暂时没有刷新完成：${loadIssueMessage.orEmpty()}"
                            },
                            onRetry = onRetry,
                        )
                    }
                }

            if (prioritizedPendingUpdates.isNotEmpty()) {
                item {
                    AppSectionHeader(
                        title = "待处理追更（按优先级）",
                        subtitle = "优先展示堆积剧集更多的内容，帮助你先清理主路径任务。",
                        trailing = {
                            AppPill(
                                text = "${prioritizedPendingUpdates.size} 项",
                                emphasized = true,
                            )
                        },
                    )
                }

                items(
                    prioritizedPendingUpdates,
                    key = { item -> "pending-${item.itemId}" },
                ) { update ->
                    val priorityLabel = buildUpdatePriorityLabel(
                        rank = prioritizedPendingUpdates.indexOfFirst { it.itemId == update.itemId },
                        newEpisodeCount = update.newEpisodeCount,
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cardCorner))
                            .clickable { onOpenDetail(update.itemId) },
                        shape = RoundedCornerShape(cardCorner),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(if (isCompact) 12.dp else 16.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 12.dp),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = update.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "最新未读：${update.latestEpisodeTitle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                AppPill(
                                    text = if (isCompact) {
                                        "$priorityLabel · ${update.newEpisodeCount}集"
                                    } else {
                                        "$priorityLabel · ${update.newEpisodeCount} 集更新"
                                    },
                                    emphasized = update.newEpisodeCount >= 2,
                                )
                            }

                            if (isCompact) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = if (update.newEpisodeCount > 1) {
                                            "建议优先补最近新增剧集，再切换到下一条任务。"
                                        } else {
                                            "本条任务较轻，可快速完成本轮闭环。"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Button(
                                        onClick = { onOpenDetail(update.itemId) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("继续追更")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            completedItemIds = completedItemIds + update.itemId
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("标记已处理")
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (update.newEpisodeCount > 1) {
                                            "建议优先补最近新增剧集，再切换到下一条任务。"
                                        } else {
                                            "本条任务较轻，可快速完成本轮闭环。"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            completedItemIds = completedItemIds + update.itemId
                                        }
                                    ) {
                                        Text("标记已处理")
                                    }
                                    OutlinedButton(onClick = { onOpenDetail(update.itemId) }) {
                                        Text("继续追更")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (completedUpdates.isNotEmpty()) {
                item {
                    AppSectionHeader(
                        title = "本轮已处理",
                        subtitle = "保留已完成记录，方便你确认本轮追更已闭环。",
                        trailing = {
                            AppPill(text = "${completedUpdates.size} 项已完成")
                        },
                    )
                }
                items(
                    completedUpdates,
                    key = { item -> "done-${item.itemId}" },
                ) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isCompact) 12.dp else 14.dp, vertical = if (isCompact) 10.dp else 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "本轮已处理 · ${item.newEpisodeCount} 集更新",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    completedItemIds = completedItemIds - item.itemId
                                }
                            ) {
                                Text(if (isCompact) "撤销处理" else "撤销")
                            }
                        }
                    }
                }
            }

            if (supplementalRecentlyAdded.isNotEmpty()) {
                item {
                    AppSectionHeader(
                        title = "补充发现（最近入库）",
                        subtitle = if (updates.isEmpty()) {
                            "当前暂无追更条目，可先浏览近期入库内容。"
                        } else {
                            "不影响追更主路径，仅补充少量新入库内容。"
                        },
                        trailing = {
                            AppPill(text = "${supplementalRecentlyAdded.size} 条")
                        },
                    )
                }

                items(
                    supplementalRecentlyAdded,
                    key = { item -> "latest-${item.itemId}" },
                ) { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenDetail(item.itemId) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isCompact) 12.dp else 14.dp, vertical = if (isCompact) 10.dp else 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = "补充",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun UpdatesSummaryCard(
    totalUpdateSeriesCount: Int,
    pendingSeriesCount: Int,
    completedSeriesCount: Int,
    totalNewEpisodes: Int,
    recentlyAddedCount: Int,
    hasLoadIssue: Boolean,
    isLoading: Boolean,
    isShowingStaleData: Boolean,
    isCompact: Boolean,
) {
    AppSectionCard(
        shape = RoundedCornerShape(if (isCompact) 18.dp else 24.dp),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(if (isCompact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 14.dp),
        ) {
            AppSectionHeader(
                title = "本次更新概览",
                subtitle = "任务分布与本轮追更节奏",
                trailing = {
                    AppPill(
                        text = "${pendingSeriesCount} 待处理",
                        emphasized = pendingSeriesCount > 0,
                    )
                },
            )
            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdatesStatCard(
                        title = "待处理",
                        value = pendingSeriesCount.toString(),
                        modifier = Modifier.fillMaxWidth(),
                        isCompact = true,
                    )
                    UpdatesStatCard(
                        title = "已处理",
                        value = completedSeriesCount.toString(),
                        modifier = Modifier.fillMaxWidth(),
                        isCompact = true,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UpdatesStatCard(
                        title = "待处理",
                        value = pendingSeriesCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    UpdatesStatCard(
                        title = "已处理",
                        value = completedSeriesCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            UpdatesStatCard(
                title = "本轮新增集数",
                value = totalNewEpisodes.toString(),
                modifier = Modifier.fillMaxWidth(),
                isCompact = isCompact,
            )
            Text(
                text = "总任务 $totalUpdateSeriesCount 项 · 最近入库（补充）$recentlyAddedCount 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppInlineTip(
                message = if (hasLoadIssue) {
                    if (isShowingStaleData) {
                        "当前先展示可用更新内容，稍后再刷新一次会更完整。"
                    } else {
                        "更新列表这次没有刷新完成，稍后再试一次即可。"
                    }
                } else if (isLoading) {
                    "正在整理最新更新，完成后会自动刷新当前列表。"
                } else {
                    "默认聚合追更条目，最近入库只作为补充发现。"
                },
                tone = when {
                    hasLoadIssue -> AppStatusTone.Warning
                    isLoading -> AppStatusTone.Progress
                    else -> AppStatusTone.Neutral
                },
            )
        }
    }
}

@Composable
private fun UpdatesTaskProgressCard(
    pendingSeriesCount: Int,
    completedSeriesCount: Int,
    totalSeriesCount: Int,
    topPending: UpdateItem?,
    isCompact: Boolean,
    onResetProgress: () -> Unit,
) {
    val progress = if (totalSeriesCount == 0) {
        0f
    } else {
        completedSeriesCount.toFloat() / totalSeriesCount.toFloat()
    }
    AppSectionCard(
        shape = RoundedCornerShape(if (isCompact) 16.dp else 20.dp),
        secondary = true,
        modifier = Modifier.animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompact) 12.dp else 14.dp, vertical = if (isCompact) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 10.dp),
        ) {
            AppSectionHeader(
                title = "本轮追更进度",
                subtitle = "$completedSeriesCount / $totalSeriesCount 已处理",
                trailing = {
                    AppPill(
                        text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        emphasized = pendingSeriesCount == 0 && totalSeriesCount > 0,
                    )
                },
            )
            if (completedSeriesCount > 0) {
                OutlinedButton(
                    onClick = onResetProgress,
                    modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier,
                ) {
                    Text(if (isCompact) "重新开始本轮进度" else "重置进度")
                }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            AppInlineTip(
                message = when {
                    totalSeriesCount == 0 -> "当前没有追更任务，可先浏览最近入库内容。"
                    pendingSeriesCount == 0 -> "本轮追更任务已全部处理完成，做得很好。"
                    topPending == null -> "当前仍有待处理任务，建议从堆积集数最多的条目开始。"
                    else -> "下一步：优先处理《${topPending.title}》，可一次消化 ${topPending.newEpisodeCount} 集更新。"
                },
                tone = if (pendingSeriesCount == 0 && totalSeriesCount > 0) {
                    AppStatusTone.Neutral
                } else {
                    AppStatusTone.Progress
                },
            )
        }
    }
}

@Composable
private fun UpdatesSyncingStrip() {
    AppInlineTip(
        message = "正在整理最新更新，当前列表会在完成后自动刷新。",
        tone = AppStatusTone.Progress,
        leading = {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        },
    )
}

@Composable
private fun UpdatesStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (isCompact) 12.dp else 14.dp, vertical = if (isCompact) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdatesLoadingState() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UpdatesSkeletonBar(widthFraction = 0.52f, height = 22.dp)
                    UpdatesSkeletonBar(widthFraction = 0.9f, height = 14.dp)
                    UpdatesSkeletonBar(widthFraction = 0.66f, height = 14.dp)
                }
            }
        }
    }
}

@Composable
private fun UpdatesSkeletonBar(
    widthFraction: Float,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant,
                    )
                )
            )
    )
}

@Composable
private fun UpdatesEmptyState(
    onRetry: () -> Unit,
) {
    AppSectionCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppHeaderRow(
                title = "当前没有可追更新集",
                subtitle = "当服务器有新剧集时，这里会第一时间提醒你继续追更。",
                leadingIcon = Icons.Filled.Notifications,
                leadingEmphasized = false,
            )
            AppInlineTip(
                message = "可以先去看看最近入库，新的追更提醒会在准备好后回到这里。",
                tone = AppStatusTone.Neutral,
            )
            OutlinedButton(onClick = onRetry) {
                Text("查看最新更新")
            }
        }
    }
}

@Composable
private fun UpdatesErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    AppSectionCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppHeaderRow(
                title = "更新列表暂时不可用",
                subtitle = "连接恢复后，这里会继续显示你的追更主线。",
                leadingIcon = Icons.Filled.Warning,
                leadingEmphasized = false,
            )
            AppStatusCard(
                message = message,
                tone = AppStatusTone.Warning,
            )
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun UpdatesErrorStrip(
    message: String,
    onRetry: () -> Unit,
) {
    AppStatusCard(
        title = "更新未完成",
        message = message,
        tone = AppStatusTone.Warning,
        action = {
            OutlinedButton(onClick = onRetry) {
                Text("继续刷新")
            }
        },
    )
}

private fun buildUpdatePriorityLabel(
    rank: Int,
    newEpisodeCount: Int,
): String {
    return when {
        rank <= 0 && newEpisodeCount >= 2 -> "P0 立即处理"
        newEpisodeCount >= 4 -> "P0 高堆积"
        newEpisodeCount >= 2 -> "P1 今日补更"
        else -> "P2 轻量任务"
    }
}
