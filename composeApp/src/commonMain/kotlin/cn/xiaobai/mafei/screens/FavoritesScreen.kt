package cn.xiaobai.mafei.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.xiaobai.mafei.data.LibraryItem
import cn.xiaobai.mafei.data.RemoteArtworkImage

@Composable
fun FavoritesScreen(
    favorites: List<LibraryItem>,
    loadIssueMessage: String?,
    onNavigateBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isShowingStaleData: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val hasLoadIssue = !loadIssueMessage.isNullOrBlank()
    val hasData = favorites.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            FavoritesHeaderCard(
                count = favorites.size,
                isLoading = isLoading,
                onNavigateBack = onNavigateBack,
                onOpenLibrary = onOpenLibrary,
                onRefresh = onRetry,
            )
        }

        item {
            FavoritesSummaryCard(
                count = favorites.size,
                isLoading = isLoading,
                hasLoadIssue = hasLoadIssue,
                isShowingStaleData = isShowingStaleData,
            )
        }

        item {
            AnimatedVisibility(visible = isLoading && hasData) {
                FavoritesSyncStrip()
            }
        }

        item {
            AnimatedVisibility(visible = hasLoadIssue) {
                FavoritesIssueCard(
                    message = loadIssueMessage.orEmpty(),
                    hasData = hasData,
                    isLoading = isLoading,
                    onRetry = onRetry,
                )
            }
        }

        when {
            isLoading && !hasData -> {
                items(4) { index ->
                    FavoritesSkeletonCard(index = index)
                }
            }

            !isLoading && !hasData -> {
                item {
                    FavoritesEmptyState(
                        title = if (hasLoadIssue) "暂时无法展示收藏" else "还没有收藏内容",
                        message = if (hasLoadIssue) {
                            "这次没有拿到完整收藏列表，你可以先去媒体库继续浏览，稍后再回来刷新。"
                        } else {
                            "在详情页点一下收藏，这里就会逐渐变成你的常看清单。"
                        },
                        icon = if (hasLoadIssue) Icons.Filled.Warning else Icons.Filled.FavoriteBorder,
                        iconTint = if (hasLoadIssue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        primaryLabel = if (hasLoadIssue) "重试加载" else "前往媒体库",
                        secondaryLabel = if (hasLoadIssue) "前往媒体库" else "稍后刷新",
                        onPrimary = if (hasLoadIssue) onRetry else onOpenLibrary,
                        onSecondary = if (hasLoadIssue) onOpenLibrary else onRetry,
                    )
                }
            }

            else -> {
                item {
                    AppSectionHeader(
                        title = "收藏内容",
                        subtitle = "按收藏顺序快速进入详情",
                        trailing = {
                            AppPill(text = "${favorites.size} 项")
                        },
                    )
                }
                items(favorites, key = { it.itemId }) { item ->
                    FavoriteItemCard(
                        item = item,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesHeaderCard(
    count: Int,
    isLoading: Boolean,
    onNavigateBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    onRefresh: () -> Unit,
) {
    AppSectionCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppHeaderRow(
                title = "我的收藏",
                subtitle = "常看内容和已关注条目",
                leadingIcon = Icons.Filled.Favorite,
                leadingEmphasized = false,
            )
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
                        text = when {
                            isLoading -> "正在整理你的收藏清单。"
                            count > 0 -> "已收好 $count 条常看内容，点一下就能回到详情。"
                            else -> "先去媒体库挑一部喜欢的内容，这里很快就会有第一条收藏。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onNavigateBack) {
                        Text("返回")
                    }
                    Button(onClick = onOpenLibrary) {
                        Text("去媒体库")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                    Text(if (isLoading) "同步中" else "刷新收藏")
                }
            }
        }
    }
}

@Composable
private fun FavoritesSummaryCard(
    count: Int,
    isLoading: Boolean,
    hasLoadIssue: Boolean,
    isShowingStaleData: Boolean,
) {
    AppSectionCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppHeaderRow(
                title = "同步状态",
                subtitle = "收藏数量与数据新鲜度",
                leadingIcon = Icons.Filled.Notifications,
                leadingEmphasized = false,
                trailing = {
                    AppPill(
                        text = when {
                            isLoading -> "同步中"
                            hasLoadIssue -> "待刷新"
                            else -> "已同步"
                        },
                        emphasized = hasLoadIssue,
                    )
                },
            )
            Text(
                text = if (count > 0) "当前共 $count 个收藏条目" else "当前暂无收藏条目",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val statusText = when {
                isLoading -> "正在刷新收藏内容，完成后会自动更新列表。"
                hasLoadIssue && isShowingStaleData -> "当前先展示可见收藏内容，稍后再刷新一次会更完整。"
                hasLoadIssue -> "这次没有拿到完整收藏列表。"
                else -> "收藏列表已准备好。"
            }
            AppInlineTip(
                message = statusText,
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
private fun FavoritesIssueCard(
    message: String,
    hasData: Boolean,
    isLoading: Boolean,
    onRetry: () -> Unit,
) {
    AppStatusCard(
        title = if (hasData) "收藏还在更新中" else "收藏列表暂时还没准备好",
        message = message,
        supportingText = "你可以继续浏览当前内容，或先去媒体库看看其他条目。",
        tone = AppStatusTone.Warning,
        leading = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
            )
        },
        action = {
            OutlinedButton(
                onClick = onRetry,
                enabled = !isLoading,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 4.dp),
                )
                Text(if (isLoading) "刷新中…" else "继续刷新")
            }
        },
    )
}

@Composable
private fun FavoritesSyncStrip() {
    AppInlineTip(
        message = "正在整理收藏内容，当前列表会在完成后自动刷新。",
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

@Composable
private fun FavoriteItemCard(
    item: LibraryItem,
    onOpenDetail: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail(item.itemId) },
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 52.dp)
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
                    imageUrl = item.posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = if (item.posterUrl.isNullOrBlank()) item.title.take(1) else "",
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
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "查看详情",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier.padding(7.dp),
                )
            }
        }
    }
}

@Composable
private fun FavoritesSkeletonCard(
    index: Int,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(width = 70.dp, height = 44.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
            ) { }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "加载收藏条目 ${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(" ", modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun FavoritesEmptyState(
    title: String,
    message: String,
    icon: ImageVector,
    iconTint: Color,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    AppSectionCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = iconTint,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(primaryLabel)
                }
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}
