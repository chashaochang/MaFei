package cn.xiaobai.mafei.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ServerConfigScreen(
    servers: List<JellyfinServer>,
    viewStatus: ServerManagerViewStatus,
    editingServer: JellyfinServer?,
    probeErrorMessage: String?,
    onBack: () -> Unit,
    onStartAdd: () -> Unit,
    onStartEdit: (JellyfinServer) -> Unit,
    onDeleteServer: (JellyfinServer) -> Unit,
    onSetDefault: (JellyfinServer) -> Unit,
    onCancelForm: () -> Unit,
    onSaveServer: (serverName: String, baseUrl: String, isDefault: Boolean) -> Unit,
    onTestConnection: (serverName: String, baseUrl: String) -> Unit,
    onProceedToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showForm = viewStatus == ServerManagerViewStatus.ADDING ||
        viewStatus == ServerManagerViewStatus.EDITING ||
        viewStatus == ServerManagerViewStatus.PROBING ||
        viewStatus == ServerManagerViewStatus.PROBE_FAILED

    val computedStatus = when {
        showForm -> viewStatus
        servers.isEmpty() -> ServerManagerViewStatus.EMPTY_LIST
        else -> ServerManagerViewStatus.LIST
    }
    val localizedProbeErrorMessage = localizeServerConfigMessage(probeErrorMessage)
    var actionFeedbackMessage by remember(viewStatus, servers.size) { mutableStateOf<String?>(null) }

    fun handleStartAdd() {
        actionFeedbackMessage = "正在新增服务器。"
        onStartAdd()
    }

    fun handleStartEdit(server: JellyfinServer) {
        actionFeedbackMessage = "正在编辑「${server.serverName}」。"
        onStartEdit(server)
    }

    fun handleDeleteServer(server: JellyfinServer) {
        actionFeedbackMessage = "正在移除「${server.serverName}」。"
        onDeleteServer(server)
    }

    fun handleSetDefault(server: JellyfinServer) {
        actionFeedbackMessage = "已将「${server.serverName}」设为默认服务器。"
        onSetDefault(server)
    }

    fun handleProceedToLogin() {
        actionFeedbackMessage = "准备进入登录。"
        onProceedToLogin()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppSectionCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            AppSectionHeader(
                title = "服务器与连接",
                subtitle = if (showForm) {
                    "建议先测试连通性，再保存配置。"
                } else {
                    "管理默认服务器，保证登录与启动恢复路径可用。"
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                trailing = {
                    OutlinedButton(
                        onClick = {
                            actionFeedbackMessage = null
                            onBack()
                        }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                            Text("返回")
                        }
                    }
                },
            )
        }

        ServerOverviewCard(
            servers = servers,
            computedStatus = computedStatus,
        )

        AnimatedVisibility(
            visible = viewStatus == ServerManagerViewStatus.PROBING || viewStatus == ServerManagerViewStatus.PROBE_FAILED,
        ) {
            ServerStatusBanner(
                isProbing = viewStatus == ServerManagerViewStatus.PROBING,
                probeErrorMessage = if (viewStatus == ServerManagerViewStatus.PROBE_FAILED) {
                    localizedProbeErrorMessage ?: "这次没能连上服务器，请检查地址和网络后再试一次。"
                } else {
                    null
                },
            )
        }

        AnimatedVisibility(visible = !actionFeedbackMessage.isNullOrBlank()) {
            AppInlineTip(
                message = actionFeedbackMessage.orEmpty(),
                tone = AppStatusTone.Neutral,
            )
        }

        if (computedStatus == ServerManagerViewStatus.EMPTY_LIST) {
            EmptyServerList(onStartAdd = ::handleStartAdd)
        }

        if (computedStatus == ServerManagerViewStatus.LIST) {
            ServerList(
                servers = servers,
                onStartAdd = ::handleStartAdd,
                onStartEdit = ::handleStartEdit,
                onDeleteServer = ::handleDeleteServer,
                onSetDefault = ::handleSetDefault,
                onProceedToLogin = ::handleProceedToLogin,
            )
        }

        if (showForm) {
            ServerForm(
                initialServer = editingServer,
                isProbing = viewStatus == ServerManagerViewStatus.PROBING,
                probeErrorMessage = if (viewStatus == ServerManagerViewStatus.PROBE_FAILED) {
                    localizedProbeErrorMessage ?: "这次没能连上服务器，可以再试一次。"
                } else {
                    null
                },
                onCancel = {
                    actionFeedbackMessage = "已取消这次编辑。"
                    onCancelForm()
                },
                onSave = { serverName, baseUrl, isDefault ->
                    actionFeedbackMessage = "已保存服务器配置，正在返回连接中心。"
                    onSaveServer(serverName, baseUrl, isDefault)
                },
                onTestConnection = { serverName, baseUrl ->
                    actionFeedbackMessage = "正在测试服务器连接。"
                    onTestConnection(serverName, baseUrl)
                },
            )
        }
    }
}

@Composable
private fun ServerOverviewCard(
    servers: List<JellyfinServer>,
    computedStatus: ServerManagerViewStatus,
) {
    val defaultServer = servers.firstOrNull { it.isDefault }
    val reachableCount = servers.count { it.health == ServerHealthStatus.HEALTHY }
    val unreachableCount = servers.count { it.health == ServerHealthStatus.UNREACHABLE }
    val statusHint = when (computedStatus) {
        ServerManagerViewStatus.EMPTY_LIST -> "先添加一个服务器，后续登录和启动都需要它。"
        ServerManagerViewStatus.ADDING -> "正在新增服务器，建议先测试连通性再保存。"
        ServerManagerViewStatus.EDITING -> "正在编辑服务器信息，保存后会立即生效。"
        ServerManagerViewStatus.PROBING -> "正在检查服务器连接，请稍候。"
        ServerManagerViewStatus.PROBE_FAILED -> "连接测试未通过，请检查地址、证书或网络。"
        ServerManagerViewStatus.LIST -> "默认服务器会用于启动、登录和主要浏览路径。"
        ServerManagerViewStatus.DELETE_CONFIRM -> "删除后不会影响其他服务器记录。"
    }

    AppSectionCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppSectionHeader(
                title = "连接概览",
                subtitle = statusHint,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppPill(text = "已配置 ${servers.size} 台")
                AppPill(text = "可用 $reachableCount 台")
                AppPill(text = "不可达 $unreachableCount 台")
            }
            Text(
                text = if (defaultServer == null) {
                    "默认服务器：尚未设置"
                } else {
                    "默认服务器：${defaultServer.serverName}（${defaultServer.baseUrl}）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (defaultServer == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            AppInlineTip(
                message = if (defaultServer == null) {
                    "先准备好默认服务器，再继续登录或回到应用。"
                } else {
                    "默认服务器会用于启动恢复、登录入口和主要浏览路径。"
                },
                tone = if (defaultServer == null) AppStatusTone.Warning else AppStatusTone.Neutral,
            )
        }
    }
}

@Composable
private fun ServerStatusBanner(
    isProbing: Boolean,
    probeErrorMessage: String?,
) {
    AppStatusCard(
        message = if (isProbing) {
            "正在检查服务器连接，稍后会给出下一步建议。"
        } else {
            probeErrorMessage ?: "这次没能连上服务器，可以再试一次。"
        },
        tone = if (isProbing) AppStatusTone.Progress else AppStatusTone.Warning,
        leading = {
            if (isProbing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(2.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun EmptyServerList(
    onStartAdd: () -> Unit,
) {
    AppSectionCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppHeaderRow(
                title = "还没有服务器配置",
                subtitle = "至少添加一个 Jellyfin 服务器后，才能继续登录和启动恢复。",
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                leadingEmphasized = false,
            )
            AppInlineTip(
                message = "建议先添加家庭主服务器，再补充备用或外网地址。",
                tone = AppStatusTone.Neutral,
            )
            Button(onClick = onStartAdd) {
                Text("添加服务器")
            }
        }
    }
}

@Composable
private fun ServerList(
    servers: List<JellyfinServer>,
    onStartAdd: () -> Unit,
    onStartEdit: (JellyfinServer) -> Unit,
    onDeleteServer: (JellyfinServer) -> Unit,
    onSetDefault: (JellyfinServer) -> Unit,
    onProceedToLogin: () -> Unit,
) {
    val hasDefaultServer = servers.any { it.isDefault }

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
                title = "服务器列表",
                subtitle = "选择默认服务器后，可直接继续登录。",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onStartAdd) {
                    Text("新增服务器")
                }
                Button(
                    onClick = onProceedToLogin,
                    enabled = hasDefaultServer,
                ) {
                    Text("继续登录")
                }
            }
            if (!hasDefaultServer) {
                AppInlineTip(
                    message = "请先设置默认服务器后再继续登录。",
                    tone = AppStatusTone.Warning,
                )
            } else {
                AppInlineTip(
                    message = "默认服务器已经准备好，可以直接继续登录。",
                    tone = AppStatusTone.Neutral,
                )
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(servers, key = { it.id }) { server ->
            ServerCard(
                server = server,
                onEdit = { onStartEdit(server) },
                onDelete = { onDeleteServer(server) },
                onSetDefault = { onSetDefault(server) },
            )
        }
    }
}

@Composable
private fun ServerCard(
    server: JellyfinServer,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    AppSectionCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    server.serverName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (server.isDefault) {
                    AppPill(
                        text = "默认",
                        emphasized = true,
                    )
                }
            }

            Text(server.baseUrl, style = MaterialTheme.typography.bodyMedium)
            AppPill(
                text = when (server.health) {
                    ServerHealthStatus.HEALTHY -> "可连接"
                    ServerHealthStatus.UNREACHABLE -> "当前不可达"
                    ServerHealthStatus.UNKNOWN -> "待检测"
                },
                emphasized = server.health == ServerHealthStatus.HEALTHY,
            )
            if (server.health == ServerHealthStatus.UNREACHABLE) {
                AppInlineTip(
                    message = "当前连接不可达，建议先修正地址或网络后再设为默认。",
                    tone = AppStatusTone.Warning,
                )
            }
            if (!server.errorMessage.isNullOrBlank()) {
                AppInlineTip(
                    message = localizeServerConfigMessage(server.errorMessage) ?: server.errorMessage,
                    tone = AppStatusTone.Warning,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (server.isDefault) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text("当前默认")
                    }
                } else {
                    OutlinedButton(onClick = onSetDefault) {
                        Text("设为默认")
                    }
                }
                OutlinedButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun ServerForm(
    initialServer: JellyfinServer?,
    isProbing: Boolean,
    probeErrorMessage: String?,
    onCancel: () -> Unit,
    onSave: (serverName: String, baseUrl: String, isDefault: Boolean) -> Unit,
    onTestConnection: (serverName: String, baseUrl: String) -> Unit,
) {
    var serverName by remember(initialServer?.id) { mutableStateOf(initialServer?.serverName ?: "") }
    var baseUrl by remember(initialServer?.id) { mutableStateOf(initialServer?.baseUrl ?: "") }
    var isDefault by remember(initialServer?.id) { mutableStateOf(initialServer?.isDefault ?: true) }

    AppSectionCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppSectionHeader(
                title = if (initialServer == null) "新增服务器" else "编辑服务器",
                subtitle = "默认服务器将用于登录入口与启动恢复。",
            )

            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it },
                label = { Text("服务器名称") },
                placeholder = { Text("家庭 Jellyfin") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "建议填写完整地址（含协议与端口），例如 https://media.example.com",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("服务器地址") },
                placeholder = { Text("http://192.168.1.10:8096") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AppInlineTip(
                message = "建议优先填写完整 HTTPS 地址；若是局域网服务，也请保留协议与端口。",
                tone = AppStatusTone.Neutral,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("设为默认服务器")
                Switch(
                    checked = isDefault,
                    onCheckedChange = { isDefault = it },
                )
            }

            if (isProbing) {
                AppStatusCard(
                    message = "正在测试连接，请稍候…",
                    tone = AppStatusTone.Progress,
                    leading = {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    },
                )
            }

            if (!probeErrorMessage.isNullOrBlank()) {
                AppStatusCard(
                    title = "连接测试未通过",
                    message = localizeServerConfigMessage(probeErrorMessage) ?: probeErrorMessage,
                    tone = AppStatusTone.Warning,
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                        )
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onTestConnection(serverName, baseUrl) },
                    enabled = !isProbing,
                ) {
                    Text(if (isProbing) "测试中…" else "测试连接")
                }
                Button(
                    onClick = { onSave(serverName, baseUrl, isDefault) },
                    enabled = !isProbing && serverName.isNotBlank() && baseUrl.isNotBlank(),
                ) {
                    Text("保存")
                }
                OutlinedButton(onClick = onCancel, enabled = !isProbing) {
                    Text("取消")
                }
            }
            if (!isProbing && probeErrorMessage.isNullOrBlank()) {
                AppInlineTip(
                    message = "连接测试通过后再保存，可显著减少启动恢复失败概率。",
                    tone = AppStatusTone.Neutral,
                )
            }
        }
    }
}

private fun localizeServerConfigMessage(message: String?): String? {
    val raw = message?.trim()
    if (raw.isNullOrEmpty()) return null
    val lower = raw.lowercase()
    return when {
        "server name is required" in lower -> "请输入服务器名称。"
        "base url must be a full url" in lower -> "服务器地址必须是完整 URL（需包含 http:// 或 https://）。"
        "invalid base url format" in lower -> "服务器地址格式无效，请检查后重试。"
        "server url already exists" in lower -> "该服务器地址已存在，请复用已有配置或编辑原记录。"
        "server to edit not found" in lower -> "未找到待编辑的服务器，请返回重试。"
        "connection test failed" in lower -> "连接测试失败，请检查地址、网络或证书设置。"
        else -> raw
    }
}
