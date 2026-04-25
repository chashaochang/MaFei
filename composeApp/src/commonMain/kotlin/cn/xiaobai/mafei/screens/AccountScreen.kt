package cn.xiaobai.mafei.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AccountScreen(
    username: String?,
    userId: String?,
    currentServer: JellyfinServer?,
    rememberSession: Boolean,
    hasSessionRecord: Boolean,
    hasSessionToken: Boolean,
    hasSessionUserId: Boolean,
    sessionServerMatchesCurrent: Boolean,
    sessionSavedAtEpochMillis: Long?,
    statusMessage: String?,
    onOpenServerConfig: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenUpdates: () -> Unit,
    onRunSessionCheck: () -> Unit,
    onGoLogin: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoggedIn = hasSessionRecord || hasSessionToken || hasSessionUserId || !username.isNullOrBlank()
    val displayUsername = username?.takeIf { it.isNotBlank() } ?: "当前账号"
    val normalizedStatusMessage = statusMessage?.trim().orEmpty()
    val statusTone = if (
        "失败" in normalizedStatusMessage ||
        "失效" in normalizedStatusMessage ||
        "异常" in normalizedStatusMessage
    ) {
        AppStatusTone.Warning
    } else {
        AppStatusTone.Neutral
    }
    var showDiagnostics by remember(isLoggedIn) { mutableStateOf(false) }
    var actionFeedbackMessage by remember(isLoggedIn) { mutableStateOf<String?>(null) }

    fun handleSettingAction(entry: SettingEntry) {
        when (entry.action) {
            SettingAction.OPEN_SERVER_CONFIG -> {
                actionFeedbackMessage = "正在打开服务器设置。"
                onOpenServerConfig()
            }

            SettingAction.OPEN_UPDATES -> {
                actionFeedbackMessage = "正在前往追更中心。"
                onOpenUpdates()
            }

            SettingAction.OPEN_FAVORITES -> {
                actionFeedbackMessage = "正在前往收藏内容。"
                onOpenFavorites()
            }

            SettingAction.SHOW_DIAGNOSTICS -> {
                showDiagnostics = true
                onRunSessionCheck()
                actionFeedbackMessage = "正在展开当前账户状态。"
            }

            SettingAction.SHOW_HINT_ONLY -> {
                actionFeedbackMessage = entry.actionHint
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompact = maxWidth < 420.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (isCompact) 12.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 14.dp),
        ) {
            if (isLoggedIn) {
                item {
                    AccountHeroCard(
                        username = displayUsername,
                        currentServer = currentServer,
                        rememberSession = rememberSession,
                        hasSessionRecord = hasSessionRecord,
                        hasSessionToken = hasSessionToken,
                        hasSessionUserId = hasSessionUserId,
                        sessionServerMatchesCurrent = sessionServerMatchesCurrent,
                        onRunSessionCheck = onRunSessionCheck,
                        onOpenServerConfig = onOpenServerConfig,
                        onOpenUpdates = onOpenUpdates,
                        onOpenFavorites = onOpenFavorites,
                        isCompact = isCompact,
                    )
                }
                item {
                    QuickEntryGrid(
                        onOpenServerConfig = onOpenServerConfig,
                        onOpenFavorites = onOpenFavorites,
                        onOpenUpdates = onOpenUpdates,
                        isCompact = isCompact,
                    )
                }
                item {
                    AnimatedVisibility(visible = !actionFeedbackMessage.isNullOrBlank()) {
                        ActionFeedbackCard(
                            message = actionFeedbackMessage.orEmpty(),
                            onDismiss = { actionFeedbackMessage = null },
                            isCompact = isCompact,
                        )
                    }
                }
                item {
                    AnimatedVisibility(visible = normalizedStatusMessage.isNotBlank()) {
                        AppStatusCard(
                            title = "会话提示",
                            message = normalizedStatusMessage,
                            tone = statusTone,
                            leading = {
                                Icon(
                                    imageVector = if (statusTone == AppStatusTone.Warning) {
                                        Icons.Filled.Warning
                                    } else {
                                        Icons.Filled.Notifications
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
                item {
                    SessionHealthCard(
                        isLoggedIn = isLoggedIn,
                        rememberSession = rememberSession,
                        hasSessionRecord = hasSessionRecord,
                        hasSessionToken = hasSessionToken,
                        hasSessionUserId = hasSessionUserId,
                        sessionServerMatchesCurrent = sessionServerMatchesCurrent,
                        sessionSavedAtEpochMillis = sessionSavedAtEpochMillis,
                        userId = userId,
                        serverBaseUrl = currentServer?.baseUrl,
                        statusMessage = statusMessage,
                        showDiagnostics = showDiagnostics,
                        onRunSessionCheck = onRunSessionCheck,
                        onToggleDiagnostics = { showDiagnostics = !showDiagnostics },
                        onGoLogin = onGoLogin,
                        onOpenServerConfig = onOpenServerConfig,
                        isCompact = isCompact,
                    )
                }
                item {
                    SettingsGroupCard(
                        title = "播放设置",
                        subtitle = "控制观看节奏和默认播放偏好",
                        leadingIcon = Icons.Filled.PlayArrow,
                        items = listOf(
                            SettingEntry(
                                title = "默认字幕语言",
                                summary = "播放时优先使用你常用的字幕语言",
                                status = "跟随服务器",
                                actionLabel = "查看设置",
                                action = SettingAction.OPEN_SERVER_CONFIG,
                                actionHint = "当前字幕语言会跟随服务器配置生效。",
                            ),
                            SettingEntry(
                                title = "自动播放下一集",
                                summary = "连续追剧时自动切换到下一集",
                                status = "已开启",
                                actionLabel = "查看状态",
                                action = SettingAction.SHOW_HINT_ONLY,
                                actionHint = "当前会保持自动连播，后续再补充更细的开关控制。",
                            ),
                            SettingEntry(
                                title = "默认倍速",
                                summary = "控制新内容开始播放时的默认速度",
                                status = "1.0x",
                                actionLabel = "查看状态",
                                action = SettingAction.SHOW_HINT_ONLY,
                                actionHint = "当前默认按 1.0x 播放，倍速偏好后续会补到这里。",
                            ),
                        ),
                        onSettingClick = ::handleSettingAction,
                    )
                }
                item {
                    SettingsGroupCard(
                        title = "外观",
                        subtitle = "调整界面显示与内容浏览密度",
                        leadingIcon = Icons.Filled.Home,
                        items = listOf(
                            SettingEntry(
                                title = "深色模式",
                                summary = "根据系统外观自动切换主题",
                                status = "跟随系统",
                                actionLabel = "查看状态",
                                action = SettingAction.SHOW_HINT_ONLY,
                                actionHint = "当前会跟随系统主题，后续再补充手动切换。",
                            ),
                            SettingEntry(
                                title = "列表样式",
                                summary = "调整封面展示密度与内容排版",
                                status = "高保真卡片",
                                actionLabel = "查看状态",
                                action = SettingAction.SHOW_HINT_ONLY,
                                actionHint = "当前默认使用高保真卡片样式，列表密度切换后续补充。",
                            ),
                        ),
                        onSettingClick = ::handleSettingAction,
                    )
                }
                item {
                    SettingsGroupCard(
                        title = "关于与帮助",
                        subtitle = "查看版本、接入信息与问题排查入口",
                        leadingIcon = Icons.Filled.Settings,
                        items = listOf(
                            SettingEntry(
                                title = "版本信息",
                                summary = "查看当前客户端版本与更新记录",
                                status = "MaFei 内测版",
                                actionLabel = "查看详情",
                                action = SettingAction.SHOW_HINT_ONLY,
                                actionHint = "当前版本为 MaFei 内测版，功能还在持续打磨中。",
                            ),
                            SettingEntry(
                                title = "服务协议",
                                summary = "了解 Jellyfin 自托管接入说明",
                                status = "已接入",
                                actionLabel = "去管理",
                                action = SettingAction.OPEN_SERVER_CONFIG,
                                actionHint = "协议与接入信息可在服务器管理中查看。",
                            ),
                            SettingEntry(
                                title = "反馈与帮助",
                                summary = "提交问题反馈并查看常见解答",
                                status = "可用",
                                actionLabel = "查看详情",
                                action = SettingAction.SHOW_DIAGNOSTICS,
                                actionHint = "这里会展示当前账户、连接和恢复状态。",
                            ),
                        ),
                        onSettingClick = ::handleSettingAction,
                    )
                }
                item {
                    DangerZoneCard(
                        onOpenServerConfig = onOpenServerConfig,
                        onSignOut = onSignOut,
                        isCompact = isCompact,
                    )
                }
            } else {
                item {
                    AccountEmptyState(
                        onGoLogin = onGoLogin,
                        onOpenServerConfig = onOpenServerConfig,
                        isCompact = isCompact,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountHeroCard(
    username: String,
    currentServer: JellyfinServer?,
    rememberSession: Boolean,
    hasSessionRecord: Boolean,
    hasSessionToken: Boolean,
    hasSessionUserId: Boolean,
    sessionServerMatchesCurrent: Boolean,
    onRunSessionCheck: () -> Unit,
    onOpenServerConfig: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenFavorites: () -> Unit,
    isCompact: Boolean,
) {
    val hasSessionIssues = !hasSessionRecord || !hasSessionToken || !hasSessionUserId || !sessionServerMatchesCurrent
    AppSectionCard(
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (isCompact) 14.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppHeaderRow(
                title = username,
                subtitle = currentServer?.serverName ?: "还没有连接服务器",
                leadingIcon = Icons.Filled.Person,
                trailing = {
                    StatusPill(
                        label = if (hasSessionRecord) "已登录" else "准备中",
                        healthy = hasSessionRecord,
                    )
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppPill(text = "账户中心", emphasized = true)
                AppPill(text = if (rememberSession) "持久会话" else "临时会话")
                AppPill(
                    text = if (sessionServerMatchesCurrent) "服务器一致" else "服务器待校验",
                    emphasized = !sessionServerMatchesCurrent,
                )
            }

            AppInlineTip(
                message = if (hasSessionIssues) {
                    "当前账户信息还需要再确认一次，刷新后再继续浏览会更稳妥。"
                } else {
                    "当前账户状态已准备好，可以直接进入收藏或追更。"
                },
                tone = if (hasSessionIssues) AppStatusTone.Warning else AppStatusTone.Neutral,
            )

            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountHeroStatChip(
                        label = "会话",
                        value = if (rememberSession) "持久" else "临时",
                        healthy = rememberSession,
                    )
                    AccountHeroStatChip(
                        label = "令牌",
                        value = if (hasSessionToken) "已就绪" else "待补齐",
                        healthy = hasSessionToken,
                    )
                    AccountHeroStatChip(
                        label = "用户ID",
                        value = if (hasSessionUserId) "已就绪" else "待补齐",
                        healthy = hasSessionUserId,
                    )
                    AccountHeroStatChip(
                        label = "服务器绑定",
                        value = if (sessionServerMatchesCurrent) "一致" else "不一致",
                        healthy = sessionServerMatchesCurrent,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AccountHeroStatChip(
                        label = "会话",
                        value = if (rememberSession) "持久" else "临时",
                        healthy = rememberSession,
                        modifier = Modifier.weight(1f),
                    )
                    AccountHeroStatChip(
                        label = "令牌",
                        value = if (hasSessionToken) "可用" else "缺失",
                        healthy = hasSessionToken,
                        modifier = Modifier.weight(1f),
                    )
                    AccountHeroStatChip(
                        label = "用户ID",
                        value = if (hasSessionUserId) "可用" else "缺失",
                        healthy = hasSessionUserId,
                        modifier = Modifier.weight(1f),
                    )
                    AccountHeroStatChip(
                        label = "服务器绑定",
                        value = if (sessionServerMatchesCurrent) "一致" else "不一致",
                        healthy = sessionServerMatchesCurrent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRunSessionCheck,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("刷新账户状态")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onOpenServerConfig,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("服务器")
                        }
                        OutlinedButton(
                            onClick = onOpenFavorites,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("收藏")
                        }
                        OutlinedButton(
                            onClick = onOpenUpdates,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("更新")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onRunSessionCheck,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("刷新账户状态")
                    }
                    OutlinedButton(
                        onClick = onOpenServerConfig,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("服务器管理")
                    }
                    OutlinedButton(
                        onClick = onOpenFavorites,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("收藏")
                    }
                    OutlinedButton(
                        onClick = onOpenUpdates,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("更新动态")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountHeroStatChip(
    label: String,
    value: String,
    healthy: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (healthy) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        },
        contentColor = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AccountContextCard(
    currentServer: JellyfinServer?,
    rememberSession: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "账户中心",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "当前连接：${currentServer?.serverName ?: "还没有连接服务器"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    )
                }
            }
            Text(
                text = if (rememberSession) {
                    "已启用保持登录，重启后可自动恢复会话。"
                } else {
                    "当前为临时登录，退出后需要重新输入账号密码。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun ProfileCard(
    username: String,
    currentServer: JellyfinServer?,
    rememberSession: Boolean,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = username.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(0.74f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "账号资料",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = currentServer?.serverName ?: "未配置服务器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (rememberSession) "已开启保持登录" else "本次为临时登录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "已登录",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickEntryGrid(
    onOpenServerConfig: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenUpdates: () -> Unit,
    isCompact: Boolean,
) {
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppHeaderRow(
            title = "快捷入口",
            subtitle = "把最常用的账户与内容入口放在这里。",
            leadingIcon = Icons.Filled.Home,
            leadingEmphasized = false,
            trailing = {
                AppPill(text = "常用 3 项")
            },
        )
        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickEntryCard(
                    title = "服务器管理",
                    subtitle = "调整默认服务器、连接地址和登录入口。",
                    icon = Icons.Filled.Settings,
                    actionLabel = "打开设置",
                    statusLabel = "连接中心",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenServerConfig,
                )
                QuickEntryCard(
                    title = "收藏",
                    subtitle = "回到你已经收好的内容清单。",
                    icon = Icons.Filled.Favorite,
                    actionLabel = "打开收藏",
                    statusLabel = "常用内容",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenFavorites,
                )
                QuickEntryCard(
                    title = "更新动态",
                    subtitle = "继续处理追更提醒和最近变化。",
                    icon = Icons.Filled.Notifications,
                    actionLabel = "打开更新",
                    statusLabel = "追更中心",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenUpdates,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickEntryCard(
                    title = "服务器管理",
                    subtitle = "调整默认服务器、连接地址和登录入口。",
                    icon = Icons.Filled.Settings,
                    actionLabel = "打开设置",
                    statusLabel = "连接中心",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenServerConfig,
                )
                QuickEntryCard(
                    title = "收藏",
                    subtitle = "回到你已经收好的内容清单。",
                    icon = Icons.Filled.Favorite,
                    actionLabel = "打开收藏",
                    statusLabel = "常用内容",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFavorites,
                )
            }
            QuickEntryCard(
                title = "更新动态",
                subtitle = "继续处理追更提醒和最近变化。",
                icon = Icons.Filled.Notifications,
                actionLabel = "打开更新",
                statusLabel = "追更中心",
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenUpdates,
            )
        }
    }
}

@Composable
private fun SessionHealthCard(
    isLoggedIn: Boolean,
    rememberSession: Boolean,
    hasSessionRecord: Boolean,
    hasSessionToken: Boolean,
    hasSessionUserId: Boolean,
    sessionServerMatchesCurrent: Boolean,
    sessionSavedAtEpochMillis: Long?,
    userId: String?,
    serverBaseUrl: String?,
    statusMessage: String?,
    showDiagnostics: Boolean,
    onRunSessionCheck: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onGoLogin: () -> Unit,
    onOpenServerConfig: () -> Unit,
    isCompact: Boolean,
) {
    val issues = mutableListOf<String>()
    if (!isLoggedIn) issues += "当前还没有登录"
    if (!hasSessionRecord) issues += "本机还没有保存可恢复的会话"
    if (hasSessionRecord && !rememberSession) issues += "当前是临时登录，重启后需要重新输入账号密码"
    if (hasSessionRecord && !hasSessionToken) issues += "登录令牌还没有准备好"
    if (hasSessionRecord && !hasSessionUserId) issues += "用户标识还没有准备好"
    if (hasSessionRecord && !sessionServerMatchesCurrent) issues += "当前登录状态与默认服务器还没有对齐"

    val hasIssues = issues.isNotEmpty()
    val statusSummary = if (hasIssues) {
        "当前账户状态还需要处理一下，完成后使用会更稳定。"
    } else {
        "当前账户状态已准备好，可以继续浏览。"
    }

    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppHeaderRow(
            title = "会话与安全",
            subtitle = "登录状态与恢复能力检查",
            leadingIcon = if (hasIssues) Icons.Filled.Warning else Icons.Filled.Notifications,
            leadingEmphasized = !hasIssues,
            trailing = {
                AppPill(
                    text = if (hasIssues) "待处理" else "已就绪",
                    emphasized = hasIssues,
                )
            },
        )
        AppSectionCard(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        label = if (hasIssues) "待处理" else "已就绪",
                        healthy = !hasIssues,
                    )
                    StatusPill(
                        label = if (rememberSession) "持久会话" else "临时会话",
                        healthy = rememberSession,
                    )
                }

                AppStatusCard(
                    message = statusSummary,
                    tone = if (hasIssues) AppStatusTone.Warning else AppStatusTone.Neutral,
                    leading = {
                        Icon(
                            imageVector = if (hasIssues) Icons.Filled.Warning else Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )

                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRunSessionCheck,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp),
                            )
                            Text(if (hasIssues) "重新检查" else "更新状态")
                        }
                        OutlinedButton(
                            onClick = onToggleDiagnostics,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp),
                            )
                            Text(if (showDiagnostics) "收起详细信息" else "查看详细信息")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onRunSessionCheck,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp),
                            )
                            Text(if (hasIssues) "重新检查" else "更新状态")
                        }
                        OutlinedButton(
                            onClick = onToggleDiagnostics,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp),
                            )
                            Text(if (showDiagnostics) "收起详细信息" else "查看详细信息")
                        }
                    }
                }

                AnimatedVisibility(visible = showDiagnostics) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppSectionCard(
                            secondary = true,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AppSectionHeader(
                                    title = "当前账户详情",
                                    subtitle = "用于确认登录恢复与服务器绑定状态",
                                )
                                Text(
                                    text = "会话记录：${if (hasSessionRecord) "已保存" else "未保存"} · 令牌：${if (hasSessionToken) "已就绪" else "待补齐"} · 服务器绑定：${if (sessionServerMatchesCurrent) "已对齐" else "待确认"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "会话保存时间：${if ((sessionSavedAtEpochMillis ?: 0L) > 0L) sessionSavedAtEpochMillis.toString() else "未知"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "当前用户标识：${userId?.ifBlank { "--" } ?: "--"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "连接服务器：${serverBaseUrl?.ifBlank { "--" } ?: "--"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (hasIssues) {
                                    AppStatusCard(
                                        title = "当前还需要处理",
                                        message = issues.joinToString(separator = "；"),
                                        tone = AppStatusTone.Warning,
                                    )
                                }
                                statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                                    AppInlineTip(
                                        message = message,
                                        tone = AppStatusTone.Neutral,
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = if (isLoggedIn) onOpenServerConfig else onGoLogin,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (isLoggedIn) "打开服务器设置" else "去登录")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    healthy: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (healthy) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        },
        contentColor = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun QuickEntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    actionLabel: String,
    statusLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        enabled = enabled,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppPill(
                text = if (enabled) statusLabel else "暂不可用",
                emphasized = enabled,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = actionLabel,
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
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector,
    items: List<SettingEntry>,
    onSettingClick: (SettingEntry) -> Unit,
) {
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppHeaderRow(
            title = title,
            subtitle = subtitle,
            leadingIcon = leadingIcon,
            leadingEmphasized = false,
            trailing = {
                AppPill(text = "${items.size} 项")
            },
        )
        AppSectionCard(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items.forEachIndexed { index, item ->
                    Card(
                        onClick = { onSettingClick(item) },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
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
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = item.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AppPill(text = item.status)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item.actionLabel,
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
                        }
                    }
                    if (index < items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class SettingEntry(
    val title: String,
    val summary: String,
    val status: String,
    val actionLabel: String,
    val action: SettingAction,
    val actionHint: String,
)

private enum class SettingAction {
    OPEN_SERVER_CONFIG,
    OPEN_UPDATES,
    OPEN_FAVORITES,
    SHOW_DIAGNOSTICS,
    SHOW_HINT_ONLY,
}

@Composable
private fun ActionFeedbackCard(
    message: String,
    onDismiss: () -> Unit,
    isCompact: Boolean,
) {
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppInlineTip(
            message = message,
            tone = AppStatusTone.Neutral,
            leading = {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        OutlinedButton(
            onClick = onDismiss,
            modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier,
        ) {
            Text(if (isCompact) "关闭提示" else "关闭")
        }
    }
}

@Composable
private fun DangerZoneCard(
    onOpenServerConfig: () -> Unit,
    onSignOut: () -> Unit,
    isCompact: Boolean,
) {
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppHeaderRow(
            title = "会话管理",
            subtitle = "执行退出或切换前的高影响操作",
            leadingIcon = Icons.Filled.Warning,
            leadingEmphasized = false,
            trailing = {
                AppPill(text = "高风险", emphasized = true)
            },
        )
        AppSectionCard(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppStatusCard(
                    message = "退出登录会清除当前本地会话，但不会删除服务器配置与媒体数据。",
                    tone = AppStatusTone.Warning,
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenServerConfig,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("服务器设置")
                        }
                        Button(
                            onClick = onSignOut,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("退出登录")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onOpenServerConfig,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("服务器设置")
                        }
                        Button(
                            onClick = onSignOut,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("退出登录")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountEmptyState(
    onGoLogin: () -> Unit,
    onOpenServerConfig: () -> Unit,
    isCompact: Boolean,
) {
    AppSectionCard(
        shape = RoundedCornerShape(20.dp),
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
                title = "尚未登录",
                subtitle = "先连接你的 Jellyfin 服务器，再登录账号开始使用。",
                leadingIcon = Icons.Filled.Person,
                leadingEmphasized = false,
            )
            AppInlineTip(
                message = "先准备好服务器，再登录账号，首页和收藏这些内容就会慢慢同步出来。",
                tone = AppStatusTone.Neutral,
            )
            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onGoLogin,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("去登录")
                    }
                    OutlinedButton(
                        onClick = onOpenServerConfig,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("服务器管理")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onGoLogin) {
                        Text("去登录")
                    }
                    OutlinedButton(onClick = onOpenServerConfig) {
                        Text("服务器管理")
                    }
                }
            }
        }
    }
}
