package cn.xiaobai.mafei.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import cn.xiaobai.mafei.data.HomeState
import cn.xiaobai.mafei.data.RemoteArtworkImage
import cn.xiaobai.mafei.data.jellyfin.messageWithHint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LibraryHubScreen(
    state: HomeState,
    isLoading: Boolean,
    onOpenSearch: () -> Unit,
    onOpenMediaView: (HomeMediaView) -> Unit,
    onOpenDetail: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedViewId by remember(state.mediaViews.firstOrNull()?.viewId) {
        mutableStateOf(state.mediaViews.firstOrNull()?.viewId)
    }
    var entryFilter by remember { mutableStateOf(LibraryEntryFilter.ALL) }
    val filteredMediaViews = remember(state.mediaViews, entryFilter) {
        when (entryFilter) {
            LibraryEntryFilter.ALL -> state.mediaViews
                .sortedWith(compareByDescending<HomeMediaView> { it.items.size }.thenBy { it.title })
            LibraryEntryFilter.HAS_CONTENT -> state.mediaViews
                .filter { it.items.isNotEmpty() }
                .sortedWith(compareByDescending<HomeMediaView> { it.items.size }.thenBy { it.title })
            LibraryEntryFilter.EMPTY -> state.mediaViews
                .filter { it.items.isEmpty() }
                .sortedBy { it.title }
        }
    }
    val totalViewCount = state.mediaViews.size
    val populatedViewCount = state.mediaViews.count { it.items.isNotEmpty() }
    val emptyViewCount = (totalViewCount - populatedViewCount).coerceAtLeast(0)
    val selectedMediaView = filteredMediaViews.firstOrNull { it.viewId == selectedViewId }
        ?: filteredMediaViews.firstOrNull()
    val latestAddedPreview = state.latestAdded.take(4)
    val selectedViewPreviewCount = selectedMediaView?.items?.take(6)?.size ?: 0
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selectedViewKey = selectedMediaView?.viewId
    var previousSelectedViewId by remember { mutableStateOf(selectedViewKey) }
    var showSelectionHint by remember { mutableStateOf(false) }
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 3 || listState.firstVisibleItemScrollOffset > 420
        }
    }
    val showFullscreenLoading = isLoading && state.mediaViews.isEmpty()

    LaunchedEffect(selectedViewKey) {
        val switched = previousSelectedViewId != null && selectedViewKey != null &&
            previousSelectedViewId != selectedViewKey
        if (switched) {
            val wasScrolled = listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 120
            if (wasScrolled) {
                listState.animateScrollToItem(0)
            }
            showSelectionHint = true
            delay(1200)
            showSelectionHint = false
        }
        previousSelectedViewId = selectedViewKey
    }

    LaunchedEffect(filteredMediaViews.firstOrNull()?.viewId, filteredMediaViews.size) {
        if (filteredMediaViews.isNotEmpty() && selectedMediaView == null) {
            selectedViewId = filteredMediaViews.first().viewId
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AppSectionCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .animateContentSize(animationSpec = tween(durationMillis = 180)),
                ) {
                    AppHeaderRow(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        title = "媒体库目录",
                        subtitle = "首屏先选分区，再进入目录浏览；近期入库仅作补充发现。",
                        leadingIcon = Icons.AutoMirrored.Filled.List,
                        trailing = {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = onOpenSearch) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Search,
                                            contentDescription = null,
                                        )
                                        Text("搜索媒体")
                                    }
                                }
                                if (selectedMediaView != null) {
                                    Button(onClick = { onOpenMediaView(selectedMediaView) }) {
                                        Text("浏览当前分区")
                                    }
                                }
                            }
                        },
                    )
                    LazyRow(
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { OverviewChip(label = "分区", value = "${filteredMediaViews.size}/${state.mediaViews.size}") }
                        item { OverviewChip(label = "当前首屏预览", value = "$selectedViewPreviewCount") }
                        item { OverviewChip(label = "近期入库", value = "${state.latestAdded.size}") }
                    }
                }
            }

            if (totalViewCount > 0) {
                item {
                    LibraryFlowCard(
                        totalViewCount = totalViewCount,
                        populatedViewCount = populatedViewCount,
                        emptyViewCount = emptyViewCount,
                        selectedViewTitle = selectedMediaView?.title,
                        selectedViewPreviewCount = selectedViewPreviewCount,
                    )
                }
            }

                item {
                    AnimatedVisibility(
                        visible = showSelectionHint && selectedMediaView != null,
                        enter = fadeIn(animationSpec = tween(180)),
                        exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
                    ) {
                        AppInlineTip(
                            message = "已切换到 ${selectedMediaView?.title ?: "当前分区"}，继续向下浏览预览内容。",
                            tone = AppStatusTone.Neutral,
                        )
                    }
                }

            if (showFullscreenLoading) {
                item { LibraryHubSkeleton() }
            } else if (state.mediaViews.isEmpty() && state.loadIssue != null) {
                item {
                    LibraryHubErrorState(
                        message = state.loadIssue.messageWithHint(),
                        onRetry = onRetry,
                    )
                }
            } else if (state.mediaViews.isEmpty()) {
                item {
                    LibraryHubEmptyState(
                        onRetry = onRetry,
                    )
                }
            } else {
                if (isLoading) {
                    item {
                        AppInlineTip(
                            message = "正在同步媒体库目录，当前先展示已有分区与预览内容。",
                            tone = AppStatusTone.Progress,
                        )
                    }
                }

                state.loadIssue?.let { issue ->
                    item {
                        AppStatusCard(
                            title = "媒体库同步遇到问题",
                            message = "这次没有拿到完整媒体库目录：${issue.messageWithHint()}",
                            supportingText = "当前先展示可用目录，稍后再刷新一次会更完整。",
                            tone = AppStatusTone.Warning,
                            leading = {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
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

                item {
                    LibraryPreviewSection(
                        title = "分区入口",
                        subtitle = "先选分区，再进入完整目录浏览。当前筛选：${entryFilter.label}",
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LibraryEntryFilter.entries, key = { it.name }) { filter ->
                                AppPill(
                                    text = filter.label,
                                    selected = entryFilter == filter,
                                    onClick = {
                                        if (entryFilter != filter) {
                                            entryFilter = filter
                                        }
                                    },
                                )
                            }
                        }
                        AppInlineTip(
                            message = when {
                                filteredMediaViews.isEmpty() -> "当前筛选下没有可展示分区，可切回“全部分区”。"
                                selectedMediaView == null -> "请选择一个分区以继续浏览。"
                                else -> "当前选中 ${selectedMediaView.title}，可直接进入目录浏览完整内容。"
                            },
                            tone = AppStatusTone.Neutral,
                        )
                        if (filteredMediaViews.isEmpty()) {
                            OutlinedButton(
                                onClick = { entryFilter = LibraryEntryFilter.ALL },
                            ) {
                                Text("查看全部分区")
                            }
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filteredMediaViews, key = { it.viewId }) { view ->
                                    val selected = view.viewId == selectedMediaView?.viewId
                                    val label = if (view.items.isNotEmpty()) {
                                        "${view.title} (${view.items.size})"
                                    } else {
                                        view.title
                                    }
                                    if (selected) {
                                        FilledTonalButton(onClick = { onOpenMediaView(view) }) {
                                            Text("已选 · $label")
                                        }
                                    } else {
                                        OutlinedButton(onClick = { selectedViewId = view.viewId }) {
                                            Text(label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                selectedMediaView?.let { mediaView ->
                    item {
                        LibraryBrowseGuidanceCard(
                            mediaView = mediaView,
                            onOpenMediaView = { onOpenMediaView(mediaView) },
                            onOpenSearch = onOpenSearch,
                        )
                    }
                }

                selectedMediaView?.let { mediaView ->
                    item {
                        AppSectionCard(
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppSectionHeader(
                                    title = "${mediaView.title} 预览",
                                    subtitle = "${mediaView.subtitle} · 预览 ${mediaView.items.take(6).size} 项",
                                    trailing = {
                                        Button(onClick = { onOpenMediaView(mediaView) }) {
                                            Text("浏览全部")
                                        }
                                    },
                                )

                                if (mediaView.items.isEmpty()) {
                                    AppInlineTip(
                                        message = "当前分区暂无内容。",
                                        tone = AppStatusTone.Neutral,
                                        leading = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.List,
                                                contentDescription = null,
                                            )
                                        },
                                    )
                                } else {
                                    mediaView.items.take(6).forEach { item ->
                                        LibraryPreviewRow(
                                            title = item.title,
                                            subtitle = item.subtitle,
                                            artworkUrl = item.posterUrl,
                                            actionLabel = "详情",
                                            onClick = { onOpenDetail(item.itemId) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    LibraryPreviewSection(
                        title = "补充发现",
                        subtitle = if (latestAddedPreview.isEmpty()) {
                            "暂无近期入库内容"
                        } else {
                            "按近期入库时间快速发现新内容"
                        },
                        isSecondary = true,
                    ) {
                        if (latestAddedPreview.isEmpty()) {
                            Text(
                                text = "暂无近期入库内容。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            latestAddedPreview.forEach { item ->
                                LibraryPreviewRow(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    artworkUrl = item.posterUrl,
                                    actionLabel = "看看",
                                    isSecondary = true,
                                    onClick = { onOpenDetail(item.itemId) },
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp),
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140)),
        ) {
            AppPill(
                text = "回到顶部",
                emphasized = true,
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
            )
        }
    }
}

@Composable
private fun LibraryBrowseGuidanceCard(
    mediaView: HomeMediaView,
    onOpenMediaView: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    AppSectionCard(
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppHeaderRow(
                title = "当前浏览分区：${mediaView.title}",
                subtitle = "下一步：进入分区目录查看更多内容（非仅预览）。",
                leadingIcon = Icons.AutoMirrored.Filled.List,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepPill(
                    label = "1. 选分区",
                    emphasized = true,
                )
                StepPill(label = "2. 看预览")
                StepPill(label = "3. 浏览全部")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenMediaView,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("进入 ${mediaView.title} 分区目录")
                }
                OutlinedButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("搜索定位内容")
                }
            }
        }
    }
}

@Composable
private fun StepPill(
    label: String,
    emphasized: Boolean = false,
) {
    AppPill(
        text = label,
        emphasized = emphasized,
    )
}

@Composable
private fun OverviewChip(
    label: String,
    value: String,
) {
    AppPill(
        text = "$label $value",
    )
}

@Composable
private fun LibraryFlowCard(
    totalViewCount: Int,
    populatedViewCount: Int,
    emptyViewCount: Int,
    selectedViewTitle: String?,
    selectedViewPreviewCount: Int,
) {
    AppSectionCard(
        shape = RoundedCornerShape(16.dp),
        secondary = true,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppSectionHeader(
                title = "浏览主路径",
                subtitle = "先选分区，再看预览，最后进入完整目录。",
                trailing = { AppPill(text = "分区 $populatedViewCount/$totalViewCount") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepPill(label = "1. 选分区", emphasized = true)
                StepPill(label = "2. 看预览")
                StepPill(label = "3. 浏览全部")
            }
            AppInlineTip(
                message = when {
                    selectedViewTitle.isNullOrBlank() -> {
                        if (populatedViewCount > 0) {
                            "请选择一个有内容的分区，继续完成浏览闭环。"
                        } else if (emptyViewCount > 0) {
                            "当前分区仍在整理中，稍后刷新可获取内容。"
                        } else {
                            "正在等待媒体库返回可用分区。"
                        }
                    }
                    selectedViewPreviewCount > 0 -> {
                        "当前已选 $selectedViewTitle，可先看预览再进入完整目录。"
                    }
                    else -> {
                        "$selectedViewTitle 暂无预览内容，可切换分区继续浏览。"
                    }
                },
                tone = AppStatusTone.Neutral,
            )
            if (emptyViewCount > 0) {
                Text(
                    text = "仍有 $emptyViewCount 个分区暂无内容，服务器同步后会自动补齐。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LibraryPreviewSection(
    title: String,
    subtitle: String,
    isSecondary: Boolean = false,
    content: @Composable () -> Unit,
) {
    AppSectionCard(
        shape = RoundedCornerShape(18.dp),
        secondary = isSecondary,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppSectionHeader(
                title = title,
                subtitle = subtitle,
            )
            content()
        }
    }
}

@Composable
private fun LibraryPreviewRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    actionLabel: String,
    isSecondary: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = if (isSecondary) 4.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = if (isSecondary) 56.dp else 64.dp,
                    height = if (isSecondary) 36.dp else 42.dp,
                )
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
                imageUrl = artworkUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = if (artworkUrl.isNullOrBlank()) title.take(1) else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = if (isSecondary) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontWeight = if (isSecondary) FontWeight.Normal else FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = actionLabel,
                style = if (isSecondary) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = if (isSecondary) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (!isSecondary) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LibraryHubSkeleton() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(width = 64.dp, height = 42.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) { }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.72f),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(" ", modifier = Modifier.padding(vertical = 7.dp))
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        ) {
                            Text(" ", modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHubEmptyState(
    onRetry: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
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
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Text(
                text = "媒体库暂无可用分区",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "可以先确认服务器内容是否完成同步，稍后再刷新一次。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text("刷新")
            }
        }
    }
}

@Composable
private fun LibraryHubErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
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
                text = "媒体库暂时还没准备好",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text("继续刷新")
            }
        }
    }
}

private enum class LibraryEntryFilter(
    val label: String,
) {
    ALL("全部分区"),
    HAS_CONTENT("有内容"),
    EMPTY("空分区"),
}
