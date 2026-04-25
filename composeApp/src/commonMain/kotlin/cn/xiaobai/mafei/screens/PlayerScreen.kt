package cn.xiaobai.mafei.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.xiaobai.mafei.data.PlaybackContext
import cn.xiaobai.mafei.data.jellyfin.messageWithHint

@Composable
fun PlayerScreen(
    playbackContext: PlaybackContext,
    isLoading: Boolean,
    canStartPlayback: Boolean,
    onStartPlayback: () -> Unit,
    onRetryLoad: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusUi = playerStatusUi(
        playbackContext = playbackContext,
        isLoading = isLoading,
        canStartPlayback = canStartPlayback,
    )
    val hasLoadIssue = playbackContext.loadIssue != null
    val statusTone = when {
        isLoading -> AppStatusTone.Progress
        hasLoadIssue -> AppStatusTone.Warning
        else -> AppStatusTone.Neutral
    }
    val retryable = playbackContext.loadIssue?.retryable == true
    val statusSupporting = playbackContext.loadIssue?.messageWithHint()
        ?.takeIf { it.isNotBlank() }
        ?: playbackContext.statusMessage.takeIf { it.isNotBlank() }

    Surface(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxWidth < 420.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isCompact) 12.dp else 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppSectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppHeaderRow(
                            title = "播放器",
                            subtitle = "播放准备与控制面板",
                            leadingIcon = Icons.Filled.PlayArrow,
                            leadingEmphasized = false,
                            trailing = {
                                OutlinedButton(onClick = onBack) {
                                    Text("返回")
                                }
                            },
                        )
                        if (isCompact) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppPill(text = "流类型 ${playbackContext.streamTypeLabel}", emphasized = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppPill(text = "时长 ${playbackContext.runtimeLabel}")
                                    AppPill(text = "起播 ${playbackContext.startPositionLabel}")
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppPill(text = "流类型 ${playbackContext.streamTypeLabel}", emphasized = true)
                                AppPill(text = "时长 ${playbackContext.runtimeLabel}")
                                AppPill(text = "起播 ${playbackContext.startPositionLabel}")
                            }
                        }
                    }
                }

                AppSectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppHeaderRow(
                            title = "播放状态",
                            subtitle = "资源加载与可播放检查",
                            leadingIcon = when {
                                hasLoadIssue -> Icons.Filled.Warning
                                isLoading -> Icons.Filled.Refresh
                                else -> Icons.Filled.Notifications
                            },
                            leadingEmphasized = false,
                        )
                        AppStatusCard(
                            title = statusUi.title,
                            message = statusUi.description,
                            supportingText = statusSupporting,
                            tone = statusTone,
                            leading = {
                                Icon(
                                    imageVector = statusUi.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            action = if (retryable && !isLoading) {
                                {
                                    OutlinedButton(onClick = onRetryLoad) {
                                        Text("重试")
                                    }
                                }
                            } else {
                                null
                            },
                        )
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }

                AppSectionCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    ),
                                ),
                            )
                            .padding(16.dp)
                            .animateContentSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(
                            targetState = isLoading,
                            label = "player_preview_state",
                        ) { loading ->
                            if (loading) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                    Text(
                                        text = "正在准备播放资源…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        )
                                    }
                                    Text(
                                        text = playbackContext.showTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = playbackContext.episodeTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                AppSectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppHeaderRow(
                            title = "播放操作",
                            subtitle = "主操作与恢复动作",
                            leadingIcon = Icons.Filled.PlayArrow,
                            leadingEmphasized = false,
                        )
                        if (isCompact) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onStartPlayback,
                                    enabled = canStartPlayback && !isLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("开始播放")
                                }
                                OutlinedButton(
                                    onClick = onRetryLoad,
                                    enabled = !isLoading && retryable,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(end = 4.dp),
                                    )
                                    Text("重新加载")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = onStartPlayback,
                                    enabled = canStartPlayback && !isLoading,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("开始播放")
                                }
                                OutlinedButton(
                                    onClick = onRetryLoad,
                                    enabled = !isLoading && retryable,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(end = 4.dp),
                                    )
                                    Text("重新加载")
                                }
                            }
                        }
                        AppInlineTip(
                            message = "当前阶段先保证可播，快进/字幕/音轨等控制项会逐步补齐。",
                            tone = AppStatusTone.Neutral,
                            leading = {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                        if (isCompact) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppPill(text = "后退 10 秒")
                                    AppPill(text = "前进 10 秒")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppPill(text = "字幕")
                                    AppPill(text = "音轨")
                                    AppPill(text = "倍速")
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppPill(text = "后退 10 秒")
                                AppPill(text = "前进 10 秒")
                                AppPill(text = "字幕")
                                AppPill(text = "音轨")
                                AppPill(text = "倍速")
                            }
                        }
                    }
                }

                AppSectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    secondary = true,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppHeaderRow(
                            title = "播放信息",
                            subtitle = "当前播放会话与基础流信息",
                            leadingIcon = Icons.Filled.Settings,
                            leadingEmphasized = false,
                        )
                        PlayerInfoLine(label = "流类型", value = playbackContext.streamTypeLabel)
                        PlayerInfoLine(
                            label = "播放地址",
                            value = playbackContext.streamUrl,
                            maxLines = 2,
                        )
                        PlayerInfoLine(
                            label = "播放对象",
                            value = "${playbackContext.playbackItemId} · 媒体源 ${playbackContext.mediaSourceId ?: "--"}",
                        )
                        PlayerInfoLine(
                            label = "会话信息",
                            value = "会话 ${playbackContext.playSessionId ?: "--"} · 格式 ${playbackContext.mediaContainer ?: "--"} · 时长 ${playbackContext.runtimeLabel} · 起播 ${playbackContext.startPositionLabel}",
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerInfoLine(
    label: String,
    value: String,
    maxLines: Int = 1,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class PlayerStatusUi(
    val title: String,
    val description: String,
    val icon: ImageVector,
)

private fun playerStatusUi(
    playbackContext: PlaybackContext,
    isLoading: Boolean,
    canStartPlayback: Boolean,
): PlayerStatusUi {
    val hasError = playbackContext.loadIssue != null
    return when {
        isLoading -> PlayerStatusUi(
            title = "正在加载",
            description = "播放器正在准备地址和媒体资源，请稍候。",
            icon = Icons.Filled.Refresh,
        )

        hasError -> PlayerStatusUi(
            title = "加载失败",
            description = playbackContext.loadIssue?.messageWithHint()
                ?: "播放资源准备失败，可尝试重新加载。",
            icon = Icons.Filled.Warning,
        )

        canStartPlayback -> PlayerStatusUi(
            title = "可以开始播放",
            description = "资源已就绪，点击“开始播放”进入播放流程。",
            icon = Icons.Filled.PlayArrow,
        )

        else -> PlayerStatusUi(
            title = "等待可播放资源",
            description = "当前播放地址不可用，可稍后重试加载。",
            icon = Icons.Filled.Notifications,
        )
    }
}
