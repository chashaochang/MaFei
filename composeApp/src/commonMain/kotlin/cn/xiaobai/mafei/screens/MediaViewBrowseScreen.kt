package cn.xiaobai.mafei.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.xiaobai.mafei.data.HomeMediaView
import cn.xiaobai.mafei.data.LibraryItem
import cn.xiaobai.mafei.data.MediaViewSortOption
import cn.xiaobai.mafei.data.RemoteArtworkImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MediaViewBrowseScreen(
    view: HomeMediaView?,
    sortOption: MediaViewSortOption,
    totalCount: Int,
    loadIssueMessage: String?,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onBack: () -> Unit,
    onSortChange: (MediaViewSortOption) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = view?.items.orEmpty()
    val shownCount = items.size
    val showFullscreenLoading = isRefreshing && shownCount == 0
    val canRetryLoadMore = shownCount > 0 && hasMore
    val retryAction = if (canRetryLoadMore) onLoadMore else onRetry
    val retryActionLabel = if (canRetryLoadMore) "继续拉取下一批" else "重新同步目录"
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var showSortResetHint by remember { mutableStateOf(false) }
    val showScrollToTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 3 || gridState.firstVisibleItemScrollOffset > 420
        }
    }

    LaunchedEffect(sortOption) {
        val wasScrolled = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 100
        if (wasScrolled) {
            gridState.animateScrollToItem(0)
            showSortResetHint = true
            delay(1200)
            showSortResetHint = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 132.dp),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MediaBrowseTopBar(
                    title = view?.title ?: "内容浏览",
                    subtitle = view?.subtitle,
                    shownCount = shownCount,
                    totalCount = totalCount,
                    onBack = onBack,
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SortRow(
                    sortOption = sortOption,
                    shownCount = shownCount,
                    totalCount = totalCount,
                    isRefreshing = isRefreshing,
                    onSortChange = onSortChange,
                )
            }

            if (shownCount > 0 || totalCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BrowseRhythmCard(
                        sortOption = sortOption,
                        shownCount = shownCount,
                        totalCount = totalCount,
                        hasMore = hasMore,
                        isLoadingMore = isLoadingMore,
                        onLoadMore = onLoadMore,
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                AnimatedVisibility(visible = showSortResetHint) {
                    AppInlineTip(
                        message = "已按新排序回到顶部，可继续向下浏览。",
                        tone = AppStatusTone.Neutral,
                    )
                }
            }

            if (!loadIssueMessage.isNullOrBlank() && shownCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppStatusCard(
                        title = "目录同步中断",
                        message = "$loadIssueMessage\n当前先展示已加载内容，你可以继续浏览。",
                        tone = AppStatusTone.Warning,
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                            )
                        },
                        action = {
                            OutlinedButton(onClick = retryAction) {
                                Text(retryActionLabel)
                            }
                        },
                    )
                }
            }

            if (items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    when {
                        showFullscreenLoading -> {
                            BrowseLoadingState()
                        }
                        !loadIssueMessage.isNullOrBlank() -> {
                            EmptyBrowseErrorState(
                                message = loadIssueMessage,
                                onRetry = onRetry,
                                onBack = onBack,
                            )
                        }
                        else -> {
                            EmptyBrowseState(onBack = onBack)
                        }
                    }
                }
            } else {
                items(items, key = { it.itemId }) { item ->
                    PosterGridCard(
                        item = item,
                        onClick = { onOpenDetail(item.itemId) },
                    )
                }

                if (isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "正在加载下一批内容，请稍候…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else if (hasMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = onLoadMore) {
                                    Text("加载下一批内容")
                                }
                                Text(
                                    text = if (totalCount > 0) {
                                        "已展示 $shownCount / $totalCount，继续加载可查看剩余内容。"
                                    } else {
                                        "已展示 $shownCount 项"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else if (totalCount > 0 && shownCount >= totalCount) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    text = "已展示全部内容",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showScrollToTop) {
            AppPill(
                text = "回到顶部",
                emphasized = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 18.dp),
                onClick = {
                    scope.launch {
                        gridState.animateScrollToItem(0)
                    }
                },
            )
        }
    }
}

@Composable
private fun MediaBrowseTopBar(
    title: String,
    subtitle: String?,
    shownCount: Int,
    totalCount: Int,
    onBack: () -> Unit,
) {
    val progressLabel = if (totalCount > 0) {
        "已展示 $shownCount / $totalCount"
    } else {
        "已展示 $shownCount"
    }
    AppSectionCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppHeaderRow(
                title = title,
                subtitle = subtitle?.takeIf { it.isNotBlank() }
                    ?: "目录浏览页：先调排序，再连续下拉浏览。",
                leadingIcon = Icons.AutoMirrored.Filled.List,
                trailing = {
                    OutlinedButton(onClick = onBack) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                            Text("媒体库")
                        }
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppPill(text = progressLabel, emphasized = shownCount > 0)
                if (totalCount > 0) {
                    AppPill(text = "共 $totalCount 项")
                }
            }
        }
    }
}

@Composable
private fun SortRow(
    sortOption: MediaViewSortOption,
    shownCount: Int,
    totalCount: Int,
    isRefreshing: Boolean,
    onSortChange: (MediaViewSortOption) -> Unit,
) {
    val totalCountLabel = if (totalCount > 0) "共 $totalCount 项" else "共 $shownCount 项"
    val progressLabel = if (totalCount > 0) "已显示 $shownCount / $totalCount" else "已显示 $shownCount"
    val progressValue = if (totalCount > 0) {
        (shownCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    AppSectionCard(
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppSectionHeader(
                title = "筛选与排序",
                subtitle = "优先调整排序，再连续下滑浏览列表",
                trailing = { AppPill(text = totalCountLabel) },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaViewSortOption.entries.forEach { option ->
                    val selected = option == sortOption
                    AppPill(
                        text = option.label,
                        selected = selected,
                        onClick = { if (!selected) onSortChange(option) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "当前：${sortOption.label} · $progressLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedVisibility(visible = isRefreshing) {
                    AppPill(
                        text = "刷新中",
                        emphasized = true,
                    )
                }
            }
            if (totalCount > 0) {
                LinearProgressIndicator(
                    progress = { progressValue },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BrowseRhythmCard(
    sortOption: MediaViewSortOption,
    shownCount: Int,
    totalCount: Int,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val remainingCount = (totalCount - shownCount).coerceAtLeast(0)
    val stageMessage = when {
        isLoadingMore -> "正在拉取下一批内容，完成后会自动追加到列表。"
        hasMore && remainingCount > 0 -> "还可继续浏览 $remainingCount 项，建议保持当前排序连续浏览。"
        hasMore -> "还有更多内容可继续加载。"
        shownCount == 0 -> "当前分区暂无可浏览内容。"
        else -> "当前分区内容已全部展示，可切换排序或返回媒体库。"
    }
    AppSectionCard(
        shape = RoundedCornerShape(16.dp),
        secondary = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppSectionHeader(
                title = "浏览节奏",
                subtitle = stageMessage,
                trailing = { AppPill(text = "排序：${sortOption.label}") },
            )
            if (hasMore && !isLoadingMore) {
                OutlinedButton(onClick = onLoadMore) {
                    Text("加载下一批内容")
                }
            }
        }
    }
}

@Composable
private fun PosterGridCard(
    item: LibraryItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                    fallbackLabel = item.title.take(2),
                    modifier = Modifier.fillMaxSize(),
                )
                val badges = item.subtitle.split(" · ").filter { it.isNotBlank() }.take(2)
                if (badges.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = badges.first(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.46f),
                                )
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title.take(1),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Text(
                        text = "详情",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.86f),
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyBrowseState(
    onBack: () -> Unit,
) {
    AppSectionCard(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppHeaderRow(
                title = "当前分区暂无内容",
                subtitle = "可先切换排序再试，或返回媒体库选择其他分区。",
                leadingIcon = Icons.AutoMirrored.Filled.List,
                leadingEmphasized = false,
            )
            AppInlineTip(
                message = "有些目录会在服务器完成整理后再出现内容。",
                tone = AppStatusTone.Neutral,
            )
            OutlinedButton(onClick = onBack) {
                Text("返回媒体库")
            }
        }
    }
}

@Composable
private fun BrowseLoadingState() {
    AppSectionCard(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "正在刷新目录…",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "新内容返回后会自动接续当前浏览。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyBrowseErrorState(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    AppSectionCard(
        shape = RoundedCornerShape(18.dp),
        secondary = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppHeaderRow(
                title = "目录暂时还没准备好",
                subtitle = "请先重试；若仍失败，可先返回媒体库继续浏览其他分区。",
                leadingIcon = Icons.Filled.Warning,
                leadingEmphasized = false,
            )
            AppStatusCard(
                message = message,
                tone = AppStatusTone.Warning,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRetry) {
                    Text("继续刷新")
                }
                OutlinedButton(onClick = onBack) {
                    Text("返回媒体库")
                }
            }
        }
    }
}
