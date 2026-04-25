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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.xiaobai.mafei.data.EpisodeItem
import cn.xiaobai.mafei.data.RemoteArtworkImage
import cn.xiaobai.mafei.data.VideoDetail
import cn.xiaobai.mafei.data.jellyfin.messageWithHint

@Composable
fun DetailScreen(
    detail: VideoDetail,
    isLoading: Boolean,
    isShowingStaleData: Boolean,
    refreshIssueMessage: String?,
    onNavigateBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    onPlayContinue: () -> Unit,
    onPlayFromStart: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEpisodeClick: (Int) -> Unit,
    onRetryLoad: () -> Unit,
    isFavoriteUpdating: Boolean,
    favoriteStatusMessage: String?,
    isFavoriteActionError: Boolean,
    modifier: Modifier = Modifier,
) {
    var synopsisExpanded by remember(detail.itemId) { mutableStateOf(false) }
    val isPlaceholderDetail = detail.isPlaceholderLike()
    val playableEpisodeCount = detail.episodes.count { it.isPlayableSource() }
    val unplayableEpisodeCount = (detail.episodes.size - playableEpisodeCount).coerceAtLeast(0)
    val hasPlayableEpisodes = playableEpisodeCount > 0
    val canPlayAny = hasPlayableEpisodes && !isPlaceholderDetail
    val canPlayContinue = canPlayAny &&
        detail.episodes.any { it.id == detail.continueEpisodeId && it.isPlayableSource() }
    val canPlayFromStart = canPlayAny
    val playBlockedReason = when {
        isPlaceholderDetail -> "内容还在更新中，播放入口稍后开放。"
        !hasPlayableEpisodes -> "剧集资源正在准备中，稍后可播放。"
        else -> null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompact = maxWidth < 420.dp

        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeroSection(
                    detail = detail,
                    canPlayAny = canPlayAny,
                    onNavigateBack = onNavigateBack,
                    onOpenLibrary = onOpenLibrary,
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = if (isCompact) 12.dp else 16.dp)
                        .animateContentSize(animationSpec = tween(durationMillis = 220)),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val loadIssueMessage = detail.loadIssue?.messageWithHint()
                    DetailSyncStatusCard(
                        detail = detail,
                        isLoading = isLoading,
                        isShowingStaleData = isShowingStaleData,
                        playableEpisodeCount = playableEpisodeCount,
                        loadIssueMessage = loadIssueMessage,
                        refreshIssueMessage = refreshIssueMessage,
                        onRetryLoad = onRetryLoad,
                    )
                    DetailMetaCard(
                        detail = detail,
                        synopsisExpanded = synopsisExpanded,
                        onToggleSynopsis = { synopsisExpanded = !synopsisExpanded },
                    )
                    DetailPrimaryActionsCard(
                        detail = detail,
                        isLoading = isLoading,
                        isFavoriteUpdating = isFavoriteUpdating,
                        canPlayContinue = canPlayContinue,
                        canPlayFromStart = canPlayFromStart,
                        playBlockedReason = playBlockedReason,
                        onPlayContinue = onPlayContinue,
                        onPlayFromStart = onPlayFromStart,
                        onToggleFavorite = onToggleFavorite,
                        isCompact = isCompact,
                    )
                    DetailStatusCard(
                        detail = detail,
                        isFavoriteUpdating = isFavoriteUpdating,
                        favoriteStatusMessage = favoriteStatusMessage,
                        isFavoriteActionError = isFavoriteActionError,
                        onRetryFavoriteAction = onToggleFavorite,
                    )
                    DetailEpisodesHeaderCard(
                        totalEpisodes = detail.episodes.size,
                        playableEpisodeCount = playableEpisodeCount,
                        isLoading = isLoading,
                        canPlayAny = canPlayAny,
                    )
                    if (detail.episodes.isEmpty()) {
                        DetailEpisodesEmptyStateCard(
                            isLoading = isLoading,
                            onRetryLoad = onRetryLoad,
                            onOpenLibrary = onOpenLibrary,
                        )
                    } else {
                        DetailEpisodesLegendRow(
                            playableEpisodeCount = playableEpisodeCount,
                            unplayableEpisodeCount = unplayableEpisodeCount,
                        )
                    }
                }
            }

            if (detail.episodes.isNotEmpty()) {
                items(detail.episodes, key = { it.id }) { episode ->
                    val episodePlayable = canPlayAny && !isLoading && episode.isPlayableSource()
                    EpisodeRowCard(
                        episode = episode,
                        enabled = episodePlayable,
                        onClick = {
                            if (episodePlayable) {
                                onEpisodeClick(episode.id)
                            }
                        },
                        modifier = Modifier.padding(horizontal = if (isCompact) 12.dp else 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailHeroSection(
    detail: VideoDetail,
    canPlayAny: Boolean,
    onNavigateBack: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(314.dp)
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
            imageUrl = detail.backdropUrl ?: detail.posterUrl,
            contentDescription = detail.title,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.72f),
                        )
                    )
                )
        )
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.16f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(86.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    contentColor = Color.White,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "详情",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (canPlayAny) "媒体信息已就绪" else "媒体信息同步中",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNavigateBack) {
                    Text("返回")
                }
                Button(onClick = onOpenLibrary) {
                    Text("媒体库")
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DetailBadgeRow(
                detail = detail,
                canPlayAny = canPlayAny,
            )
            Text(
                text = detail.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail.presentationMetaLine(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (canPlayAny) {
                    "内容已就绪，可直接播放或继续上次进度。"
                } else {
                    "正在准备可播放内容，稍后可重试。"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.84f),
            )
        }
    }
}

@Composable
private fun DetailBadgeRow(
    detail: VideoDetail,
    canPlayAny: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val badges = buildList {
            if (detail.isFavorite) add("已收藏")
            if (detail.updateCount > 0) add("更新 ${detail.updateCount} 集")
            add(if (canPlayAny) "可播放" else "待重试")
        }
        badges.forEachIndexed { index, badge ->
            Surface(
                shape = CircleShape,
                color = if (index == 0) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                } else {
                    Color.White.copy(alpha = 0.14f)
                },
                contentColor = Color.White,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailSyncStatusCard(
    detail: VideoDetail,
    isLoading: Boolean,
    isShowingStaleData: Boolean,
    playableEpisodeCount: Int,
    loadIssueMessage: String?,
    refreshIssueMessage: String?,
    onRetryLoad: () -> Unit,
) {
    val detailState = when {
        isLoading && isShowingStaleData -> "正在更新，当前先展示可用内容"
        isLoading -> "正在更新详情内容"
        isShowingStaleData -> "当前展示可用内容"
        else -> "详情内容已更新"
    }
    val episodeEntryState = when {
        detail.episodes.isEmpty() -> "剧集信息正在整理"
        playableEpisodeCount <= 0 -> "剧集资源正在整理，完成后即可播放"
        playableEpisodeCount < detail.episodes.size -> {
            "已有部分剧集准备好（$playableEpisodeCount / ${detail.episodes.size}）"
        }
        else -> "全部剧集已准备好"
    }

    AppSectionCard(
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 220)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppHeaderRow(
                title = "同步与内容状态",
                subtitle = "加载反馈、可播放状态与数据新鲜度",
                leadingIcon = Icons.Filled.Notifications,
                leadingEmphasized = false,
            )
            if (!loadIssueMessage.isNullOrBlank()) {
                AppStatusCard(
                    title = "内容更新还没完成",
                    message = loadIssueMessage,
                    tone = AppStatusTone.Warning,
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    action = {
                        OutlinedButton(
                            onClick = onRetryLoad,
                            enabled = !isLoading,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp),
                            )
                            Text(if (isLoading) "刷新中..." else "继续刷新详情")
                        }
                    },
                )
            }
            if (isLoading || !refreshIssueMessage.isNullOrBlank()) {
                val hasRefreshIssue = !refreshIssueMessage.isNullOrBlank()
                AppStatusCard(
                    title = if (hasRefreshIssue) "更新未完成" else "正在获取最新内容",
                    message = refreshIssueMessage ?: "数据同步进行中，完成后会自动刷新。",
                    supportingText = if (isShowingStaleData) "当前先展示可用内容。" else null,
                    tone = if (hasRefreshIssue) AppStatusTone.Warning else AppStatusTone.Progress,
                    leading = {
                        if (hasRefreshIssue) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    action = if (hasRefreshIssue) {
                        {
                            OutlinedButton(
                                onClick = onRetryLoad,
                                enabled = !isLoading,
                            ) {
                                Text(if (isLoading) "刷新中..." else "继续刷新")
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            StatusLine(
                label = "详情状态",
                value = detailState,
            )
            StatusLine(
                label = "更新信息",
                value = if (detail.updateCount > 0) "更新 ${detail.updateCount} 集" else "暂无新集",
            )
            StatusLine(
                label = "剧集入口",
                value = episodeEntryState,
            )
            AppInlineTip(
                message = if (playableEpisodeCount > 0) {
                    "已经有可进入的剧集，适合先从主播放入口开始。"
                } else {
                    "剧集还在整理中，当前可以先看看剧情和收藏状态。"
                },
                tone = if (playableEpisodeCount > 0) AppStatusTone.Neutral else AppStatusTone.Progress,
            )
        }
    }
}

@Composable
private fun DetailPrimaryActionsCard(
    detail: VideoDetail,
    isLoading: Boolean,
    isFavoriteUpdating: Boolean,
    canPlayContinue: Boolean,
    canPlayFromStart: Boolean,
    playBlockedReason: String?,
    onPlayContinue: () -> Unit,
    onPlayFromStart: () -> Unit,
    onToggleFavorite: () -> Unit,
    isCompact: Boolean,
) {
    AppSectionCard(
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 180)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppSectionHeader(
                title = "播放与收藏",
                subtitle = "优先主播放入口，再处理收藏状态",
                trailing = {
                    AppPill(
                        text = if (detail.isFavorite) "已收藏" else "未收藏",
                        emphasized = detail.isFavorite,
                    )
                },
            )
            DetailActionsRow(
                detail = detail,
                isLoading = isLoading,
                isFavoriteUpdating = isFavoriteUpdating,
                canPlayContinue = canPlayContinue,
                canPlayFromStart = canPlayFromStart,
                playBlockedReason = playBlockedReason,
                onPlayContinue = onPlayContinue,
                onPlayFromStart = onPlayFromStart,
                onToggleFavorite = onToggleFavorite,
                isCompact = isCompact,
            )
        }
    }
}

@Composable
private fun DetailMetaCard(
    detail: VideoDetail,
    synopsisExpanded: Boolean,
    onToggleSynopsis: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppHeaderRow(
                title = "剧情与信息",
                subtitle = "简介、标签与续播进度",
                leadingIcon = Icons.Filled.Settings,
                leadingEmphasized = false,
            )
            MetaPillRow(metaLine = detail.presentationMetaLine())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = detail.synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.synopsis.length > 90) {
                    Text(
                        text = if (synopsisExpanded) "收起" else "更多",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onToggleSynopsis),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppInlineTip(
                    message = if (detail.continueProgressPercent > 0) {
                        "已经为你保留上次进度，适合直接继续播放。"
                    } else {
                        "如果准备从头开始，这里会继续补齐完整剧集信息。"
                    },
                    tone = AppStatusTone.Neutral,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("续播进度", style = MaterialTheme.typography.titleSmall)
                    Text(
                        detail.continueProgressLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LinearProgressIndicator(
                    progress = { detail.continueProgressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
            }
        }
    }
}

@Composable
private fun MetaPillRow(
    metaLine: String,
) {
    val tokens = metaLine.split(" · ")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tokens.chunked(2).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { meta ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailActionsRow(
    detail: VideoDetail,
    isLoading: Boolean,
    isFavoriteUpdating: Boolean,
    canPlayContinue: Boolean,
    canPlayFromStart: Boolean,
    playBlockedReason: String?,
    onPlayContinue: () -> Unit,
    onPlayFromStart: () -> Unit,
    onToggleFavorite: () -> Unit,
    isCompact: Boolean,
) {
    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(durationMillis = 180)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onPlayContinue,
            enabled = !isLoading && canPlayContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 4.dp),
            )
            Text(
                when {
                    isLoading -> "同步中..."
                    detail.continueProgressPercent > 0 -> "继续播放"
                    else -> "立即播放"
                }
            )
        }
        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPlayFromStart,
                    enabled = !isLoading && canPlayFromStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (isLoading) {
                            "同步中..."
                        } else {
                            "从头播放"
                        }
                    )
                }
                OutlinedButton(
                    onClick = onToggleFavorite,
                    enabled = !isFavoriteUpdating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                    Text(
                        when {
                            isFavoriteUpdating -> "处理中..."
                            detail.isFavorite -> "取消收藏"
                            else -> "加入收藏"
                        }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onPlayFromStart,
                    enabled = !isLoading && canPlayFromStart,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (isLoading) {
                            "同步中..."
                        } else {
                            "从头播放"
                        }
                    )
                }
                OutlinedButton(
                    onClick = onToggleFavorite,
                    enabled = !isFavoriteUpdating,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                    Text(
                        when {
                            isFavoriteUpdating -> "处理中..."
                            detail.isFavorite -> "取消收藏"
                            else -> "加入收藏"
                        }
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = !playBlockedReason.isNullOrBlank(),
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
        ) {
            AppInlineTip(
                message = playBlockedReason ?: "",
                tone = AppStatusTone.Warning,
                leading = {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
        }
        AnimatedVisibility(
            visible = playBlockedReason.isNullOrBlank() && isLoading,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
        ) {
            AppInlineTip(
                message = "正在获取最新播放信息，稍后可播放。",
                tone = AppStatusTone.Progress,
                leading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                },
            )
        }
        AnimatedVisibility(
            visible = playBlockedReason.isNullOrBlank() && !isLoading,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
        ) {
            AppInlineTip(
                message = if (detail.continueProgressPercent > 0) {
                    "建议先从保留进度继续，再决定是否回到开头重看。"
                } else {
                    "内容已经准备好，可以直接播放，也可以先加入收藏。"
                },
                tone = AppStatusTone.Neutral,
                leading = {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun DetailStatusCard(
    detail: VideoDetail,
    isFavoriteUpdating: Boolean,
    favoriteStatusMessage: String?,
    isFavoriteActionError: Boolean,
    onRetryFavoriteAction: () -> Unit,
) {
    AppSectionCard(
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 200)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppSectionHeader(
                title = "收藏反馈",
                subtitle = "仅在收藏操作时展示状态与结果",
                trailing = {
                    AppPill(
                        text = if (detail.isFavorite) "已收藏" else "未收藏",
                        emphasized = detail.isFavorite,
                    )
                },
            )
            AnimatedVisibility(
                visible = isFavoriteUpdating,
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
            ) {
                AppInlineTip(
                    message = "正在同步收藏状态…",
                    tone = AppStatusTone.Progress,
                    leading = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    },
                )
            }
            AnimatedVisibility(
                visible = !favoriteStatusMessage.isNullOrBlank(),
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
            ) {
                AppStatusCard(
                    title = if (isFavoriteActionError) "收藏未完成" else "收藏已更新",
                    message = favoriteStatusMessage ?: "",
                    tone = if (isFavoriteActionError) AppStatusTone.Warning else AppStatusTone.Neutral,
                    leading = {
                        Icon(
                            imageVector = if (isFavoriteActionError) Icons.Filled.Warning else Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    action = if (isFavoriteActionError && !isFavoriteUpdating) {
                        {
                            OutlinedButton(onClick = onRetryFavoriteAction) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(end = 4.dp),
                                )
                                Text("重试收藏")
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            if (!isFavoriteUpdating && favoriteStatusMessage.isNullOrBlank()) {
                Text(
                    text = if (detail.isFavorite) {
                        "已加入收藏，可在收藏页快速访问。"
                    } else {
                        "未加入收藏，添加后可在收藏页追踪更新。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailEpisodesHeaderCard(
    totalEpisodes: Int,
    playableEpisodeCount: Int,
    isLoading: Boolean,
    canPlayAny: Boolean,
) {
    val playableRatio = if (totalEpisodes > 0) {
        (playableEpisodeCount.toFloat() / totalEpisodes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    AppSectionCard(
        shape = RoundedCornerShape(20.dp),
        secondary = true,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 180)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppSectionHeader(
                title = "剧集列表",
                subtitle = "选择剧集进入播放页",
                trailing = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                            AppPill(text = "$totalEpisodes 集")
                            if (playableEpisodeCount < totalEpisodes) {
                                AppPill(
                                text = "可播放 $playableEpisodeCount",
                                emphasized = canPlayAny,
                            )
                        }
                    }
                },
            )
            if (totalEpisodes > 0) {
                LinearProgressIndicator(
                    progress = { playableRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
            }
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
            ) {
                AppInlineTip(
                        message = "正在核对每一集的可播放状态，请稍候。",
                    tone = AppStatusTone.Progress,
                    leading = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    },
                )
            }
            AnimatedVisibility(
                visible = !isLoading && playableEpisodeCount <= 0 && totalEpisodes > 0,
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
            ) {
                AppInlineTip(
                        message = "剧集资源还在整理中，稍后再刷新一次通常就会恢复。",
                        tone = AppStatusTone.Warning,
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailEpisodesEmptyStateCard(
    isLoading: Boolean,
    onRetryLoad: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    AppSectionCard(
        shape = RoundedCornerShape(16.dp),
        secondary = true,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppHeaderRow(
                title = if (isLoading) "正在整理剧集" else "还没有拿到剧集信息",
                subtitle = if (isLoading) {
                    "正在同步当前内容的剧集信息。"
                } else {
                    "可能是服务器还在同步，稍后再刷新一次通常就会恢复。"
                },
                leadingIcon = if (isLoading) Icons.Filled.Notifications else Icons.Filled.Warning,
                leadingEmphasized = false,
            )
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRetryLoad,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp),
                        )
                        Text("继续刷新剧集")
                    }
                    OutlinedButton(
                        onClick = onOpenLibrary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("前往媒体库继续浏览")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailEpisodesLegendRow(
    playableEpisodeCount: Int,
    unplayableEpisodeCount: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppPill(text = "已就绪 $playableEpisodeCount", emphasized = playableEpisodeCount > 0)
        if (unplayableEpisodeCount > 0) {
            AppPill(text = "整理中 $unplayableEpisodeCount")
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EpisodeRowCard(
    episode: EpisodeItem,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodeIndexLabel = "第 ${episode.id} 集"
    val statusText = when {
        episode.isNew -> "最新更新"
        !enabled -> "整理中"
        else -> "已就绪"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.58f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 132.dp, height = 74.dp)
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
                    imageUrl = episode.thumbnailUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = if (episode.thumbnailUrl.isNullOrBlank()) {
                        episode.id.toString().padStart(2, '0')
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.24f),
                    contentColor = Color.White,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(5.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            text = episode.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppPill(text = episodeIndexLabel)
                            AppPill(text = statusText, emphasized = enabled || episode.isNew)
                        }
                    }
                }
                Text(
                    text = episode.durationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (enabled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "进入播放",
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
                } else {
                    Text(
                        text = "该剧集资源正在准备，稍后刷新可恢复播放。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun EpisodeItem.isPlayableSource(): Boolean {
    return !playbackItemId.isNullOrBlank() && durationLabel != "--"
}

private fun VideoDetail.isPlaceholderLike(): Boolean {
    return loadIssue != null ||
        metaLine.contains("占位") ||
        synopsis.contains("无法从 Jellyfin 拉取详情数据") ||
        episodes.all { it.durationLabel == "--" }
}

private fun VideoDetail.presentationMetaLine(): String {
    val cleaned = metaLine
        .split(" · ")
        .map { it.trim() }
        .filter { token ->
            token.isNotBlank() &&
                !token.contains("占位") &&
                !token.contains("placeholder", ignoreCase = true)
        }
        .joinToString(" · ")
    return if (cleaned.isNotBlank()) cleaned else "媒体详情"
}
