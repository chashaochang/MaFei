package cn.xiaobai.mafei.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.xiaobai.mafei.data.LibraryItem
import cn.xiaobai.mafei.data.RemoteArtworkImage

@Composable
fun SearchScreen(
    keyword: String,
    results: List<LibraryItem>,
    isLoading: Boolean,
    isShowingStaleData: Boolean,
    hintMessage: String,
    loadIssueMessage: String?,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasResults = results.isNotEmpty()
    val normalizedKeyword = keyword.trim()
    val searchHistory = remember {
        mutableStateListOf("科幻", "电影", "剧集", "最近入库")
    }
    val suggestedFromResults = remember(results) {
        results
            .flatMap { item ->
                buildList {
                    add(item.title.trim())
                    item.subtitle.split("·", "|", "/", " ")
                        .map { it.trim() }
                        .filter { it.length >= 2 }
                        .forEach(::add)
                }
            }
            .filter { it.length in 2..14 }
            .distinct()
            .take(20)
    }
    val contextualSuggestions = if (normalizedKeyword.isBlank()) {
        searchHistory.take(6)
    } else {
        (
            suggestedFromResults.filter { candidate ->
                candidate.contains(normalizedKeyword, ignoreCase = true) ||
                    normalizedKeyword.contains(candidate, ignoreCase = true)
            } + searchHistory.filter { previous ->
                previous.contains(normalizedKeyword, ignoreCase = true)
            }
            ).distinct().take(8)
    }
    val resultCountLabel = when {
        isLoading && hasResults -> "更新中，先展示 ${results.size} 项"
        isLoading -> "正在获取结果"
        results.isNotEmpty() -> "已找到 ${results.size} 项"
        keyword.isBlank() -> "输入关键词开始搜索"
        else -> "未命中结果"
    }
    val resultRhythmLabel = when {
        hasResults && normalizedKeyword.isNotBlank() -> "“$normalizedKeyword” 的匹配内容"
        hasResults -> "媒体库推荐结果"
        normalizedKeyword.isNotBlank() -> "“$normalizedKeyword” 暂无匹配"
        else -> "可先使用下方快捷词"
    }

    LaunchedEffect(normalizedKeyword, isLoading, hasResults) {
        if (!isLoading && normalizedKeyword.isNotBlank() && normalizedKeyword.length <= 24) {
            searchHistory.removeAll { it.equals(normalizedKeyword, ignoreCase = true) }
            searchHistory.add(0, normalizedKeyword)
            while (searchHistory.size > 8) {
                searchHistory.removeAt(searchHistory.lastIndex)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 0.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchTopBar(
                keyword = keyword,
                onKeywordChange = onKeywordChange,
                onSearch = onSearch,
                onNavigateBack = onNavigateBack,
                onApplySuggestion = { suggestion ->
                    onKeywordChange(suggestion)
                    onSearch()
                },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchInfoRow(
                resultCountLabel = resultCountLabel,
                resultRhythmLabel = resultRhythmLabel,
                hintMessage = hintMessage,
                isLoading = isLoading,
            )
        }

        if (contextualSuggestions.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchSuggestionDeck(
                    keyword = normalizedKeyword,
                    suggestions = contextualSuggestions,
                    onApplySuggestion = { suggestion ->
                        onKeywordChange(suggestion)
                        onSearch()
                    },
                    onClearHistory = {
                        searchHistory.clear()
                    },
                )
            }
        }

        if (isLoading && hasResults) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchSyncingStrip(
                    isShowingStaleData = isShowingStaleData,
                )
            }
        }

        if (!loadIssueMessage.isNullOrBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchErrorStrip(
                    message = loadIssueMessage,
                    hasResults = hasResults,
                    isShowingStaleData = isShowingStaleData,
                    onRetry = onSearch,
                )
            }
        }

        when {
            isLoading && !hasResults -> {
                items(8) { index ->
                    SearchPosterSkeleton(index = index)
                }
            }

            results.isEmpty() && !loadIssueMessage.isNullOrBlank() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchEmptyState(
                        title = "搜索结果还没准备好",
                        message = "这次没有拿到搜索结果，稍后再试一次通常就会恢复。",
                        icon = Icons.Filled.Warning,
                        primaryLabel = "重新搜索",
                        secondaryLabel = "浏览媒体库",
                        onPrimary = onSearch,
                        onSecondary = onOpenLibrary,
                    )
                }
            }

            results.isEmpty() && keyword.isNotBlank() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchEmptyState(
                        title = "未找到相关内容",
                        message = "可以换个关键词，或直接去媒体库继续浏览。",
                        icon = Icons.Filled.Search,
                        primaryLabel = "试试推荐词",
                        secondaryLabel = "浏览媒体库",
                        onPrimary = {
                            onKeywordChange(
                                contextualSuggestions.firstOrNull { it != normalizedKeyword }
                                    ?: "最近入库"
                            )
                            onSearch()
                        },
                        onSecondary = onOpenLibrary,
                    )
                }
            }

            results.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchEmptyState(
                        title = "开始搜索 Jellyfin 媒体",
                        message = "输入片名、剧名或关键词，搜索结果会在这里展示。",
                        icon = Icons.Filled.Search,
                        primaryLabel = "使用推荐词搜索",
                        secondaryLabel = "浏览媒体库",
                        onPrimary = {
                            onKeywordChange(contextualSuggestions.firstOrNull() ?: "最近入库")
                            onSearch()
                        },
                        onSecondary = onOpenLibrary,
                    )
                }
            }

            else -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchResultsHeader(
                        keyword = normalizedKeyword,
                        count = results.size,
                        isLoading = isLoading,
                        isShowingStaleData = isShowingStaleData,
                    )
                }
                items(results, key = { it.itemId }) { item ->
                    SearchPosterCard(
                        item = item,
                        onClick = { onOpenDetail(item.itemId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionDeck(
    keyword: String,
    suggestions: List<String>,
    onApplySuggestion: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (keyword.isBlank()) "最近搜索" else "猜你想找",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (keyword.isBlank()) {
                    OutlinedButton(onClick = onClearHistory) {
                        Text("清除记录")
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions, key = { it }) { suggestion ->
                    OutlinedButton(onClick = { onApplySuggestion(suggestion) }) {
                        Text(suggestion)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSyncingStrip(
    isShowingStaleData: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isShowingStaleData) {
                    "正在更新结果，当前先展示可用内容。"
                } else {
                    "正在更新结果，列表会在完成后自动刷新。"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SearchTopBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onNavigateBack: () -> Unit,
    onApplySuggestion: (String) -> Unit,
) {
    val quickSuggestions = listOf("电影", "剧集", "最近入库", "收藏")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 220)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "搜索",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "在媒体库中快速查找你想看的内容。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row {
                    OutlinedButton(onClick = onNavigateBack) {
                        Text("返回")
                    }
                }
            }
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                singleLine = true,
                label = { Text("搜索 Jellyfin 媒体") },
                placeholder = { Text("输入片名、剧名或关键词") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (keyword.isNotBlank()) {
                        IconButton(onClick = { onKeywordChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "清空关键词",
                            )
                        }
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onSearch) {
                    Text(if (keyword.isBlank()) "搜索媒体库" else "搜索")
                }
            }
            AnimatedVisibility(
                visible = keyword.isBlank(),
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "快速开始",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickSuggestions) { suggestion ->
                            OutlinedButton(onClick = { onApplySuggestion(suggestion) }) {
                                Text(suggestion)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInfoRow(
    resultCountLabel: String,
    resultRhythmLabel: String,
    hintMessage: String,
    isLoading: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 180)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = resultCountLabel,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Text(
                text = resultRhythmLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = hintMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "同步中",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchErrorStrip(
    message: String,
    hasResults: Boolean,
    isShowingStaleData: Boolean,
    onRetry: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                    )
                    Text(
                        text = when {
                            hasResults && isShowingStaleData -> "搜索更新失败，已保留上次结果"
                            hasResults -> "搜索更新失败"
                            else -> "搜索暂不可用"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onRetry) {
                Text("重新搜索")
            }
        }
    }
}

@Composable
private fun SearchEmptyState(
    title: String,
    message: String,
    icon: ImageVector,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
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
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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

@Composable
private fun SearchResultsHeader(
    keyword: String,
    count: Int,
    isLoading: Boolean,
    isShowingStaleData: Boolean,
) {
    val stateLabel = when {
        isLoading && isShowingStaleData -> "更新中 · 已保留"
        isLoading -> "更新中"
        isShowingStaleData -> "当前结果"
        else -> "最新结果"
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (keyword.isBlank()) "搜索结果" else "“$keyword” 的结果",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "按相关性展示，可进入详情，也可返回继续筛选关键词。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchPosterSkeleton(
    index: Int,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
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
                        if (index % 2 == 0) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        }
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            )
        }
    }
}

@Composable
private fun SearchPosterCard(
    item: LibraryItem,
    onClick: () -> Unit,
) {
    val subtitleSegments = item.subtitle
        .split(" · ")
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val badge = subtitleSegments.firstOrNull()
    val secondaryMeta = subtitleSegments.drop(1).joinToString(" · ")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
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
                    modifier = Modifier.fillMaxSize(),
                )
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
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Box(
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
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (item.posterUrl.isNullOrBlank()) item.title.take(1) else "",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White.copy(alpha = 0.85f),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (secondaryMeta.isNotBlank()) secondaryMeta else "查看详情",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "详情",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
