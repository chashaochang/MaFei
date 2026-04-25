package cn.xiaobai.mafei.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StartupRestoreScreen(
    status: StartupRestoreStatus,
    currentDefaultServer: JellyfinServer?,
    onRetry: () -> Unit,
    onOpenServerConfig: () -> Unit,
    onGoLogin: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusUi = startupStatusUi(status, currentDefaultServer)
    val hasDefaultServer = currentDefaultServer != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        AppSectionCard(
            shape = RoundedCornerShape(20.dp),
            secondary = true,
            modifier = Modifier.animateContentSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppHeaderRow(
                    title = "正在准备 MaFei",
                    subtitle = "检查默认服务器与本地登录状态，准备回到上次浏览进度。",
                    leadingIcon = Icons.Filled.Home,
                    leadingEmphasized = false,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppPill(
                        text = restoreStageLabel(status),
                        emphasized = status == StartupRestoreStatus.LOADING,
                    )
                    AppPill(
                        text = if (hasDefaultServer) "默认服务器已就绪" else "还没有默认服务器",
                        emphasized = hasDefaultServer,
                    )
                }
            }
        }

        AppStatusCard(
            title = statusUi.title,
            message = statusUi.description,
            supportingText = statusUi.hint,
            tone = when (status) {
                StartupRestoreStatus.LOADING -> AppStatusTone.Progress
                StartupRestoreStatus.RESTORE_SUCCESS -> AppStatusTone.Neutral
                StartupRestoreStatus.SESSION_INVALID,
                StartupRestoreStatus.NO_DEFAULT_SERVER,
                -> AppStatusTone.Warning
            },
            leading = {
                androidx.compose.material3.Icon(
                    imageVector = statusUi.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        AppSectionCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppSectionHeader(
                    title = "当前连接",
                    subtitle = "用于启动检查与自动登录",
                )
                Text(
                    text = currentDefaultServer?.serverName ?: "尚未设置",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = currentDefaultServer?.baseUrl ?: "请前往服务器管理添加并设为默认",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppInlineTip(
                    message = when (status) {
                        StartupRestoreStatus.LOADING -> "正在执行启动检查，通常 1-2 秒内完成。"
                        StartupRestoreStatus.RESTORE_SUCCESS -> "准备完成后会直接进入首页，也可以随时回到连接中心调整。"
                        StartupRestoreStatus.SESSION_INVALID -> "上次登录状态已经失效，重新登录后就能继续使用。"
                        StartupRestoreStatus.NO_DEFAULT_SERVER -> "先准备好默认服务器，后续就能自动回到应用。"
                    },
                    tone = when (status) {
                        StartupRestoreStatus.LOADING -> AppStatusTone.Progress
                        StartupRestoreStatus.RESTORE_SUCCESS -> AppStatusTone.Neutral
                        StartupRestoreStatus.SESSION_INVALID,
                        StartupRestoreStatus.NO_DEFAULT_SERVER,
                        -> AppStatusTone.Warning
                    },
                )
            }
        }

        AnimatedVisibility(visible = status == StartupRestoreStatus.LOADING) {
            AppStatusCard(
                title = "启动检查中",
                message = "正在完成启动检查…",
                tone = AppStatusTone.Progress,
                leading = {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                },
            )
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("重新检查启动状态")
            }
        }

        AnimatedVisibility(visible = status != StartupRestoreStatus.LOADING) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (status) {
                    StartupRestoreStatus.RESTORE_SUCCESS -> {
                        Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) {
                            Text("进入首页")
                        }
                        OutlinedButton(onClick = onOpenServerConfig, modifier = Modifier.fillMaxWidth()) {
                            Text("前往服务器管理")
                        }
                    }

                    StartupRestoreStatus.SESSION_INVALID -> {
                        Button(onClick = onGoLogin, modifier = Modifier.fillMaxWidth()) {
                            Text("前往登录")
                        }
                        OutlinedButton(onClick = onOpenServerConfig, modifier = Modifier.fillMaxWidth()) {
                            Text("前往服务器管理")
                        }
                    }

                    StartupRestoreStatus.NO_DEFAULT_SERVER -> {
                        Button(onClick = onOpenServerConfig, modifier = Modifier.fillMaxWidth()) {
                            Text("去配置服务器")
                        }
                    }

                    StartupRestoreStatus.LOADING -> Unit
                }
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                    Text("重新检查启动状态")
                }
            }
        }
    }
}

private data class StartupStatusUi(
    val title: String,
    val description: String,
    val hint: String,
    val icon: ImageVector,
)

@Composable
private fun startupStatusUi(
    status: StartupRestoreStatus,
    currentDefaultServer: JellyfinServer?,
): StartupStatusUi {
    return when (status) {
        StartupRestoreStatus.LOADING -> StartupStatusUi(
            title = "正在检查启动状态",
            description = "正在确认默认服务器和本地登录状态，请稍候。",
            hint = "通常只需 1-2 秒。若长时间停留，可点击“重新检测”。",
            icon = Icons.Filled.Notifications,
        )

        StartupRestoreStatus.RESTORE_SUCCESS -> StartupStatusUi(
            title = "准备完成",
            description = "已连接到 ${currentDefaultServer?.serverName.orEmpty()}，可以直接进入首页。",
            hint = "如需切换环境，可先进入服务器管理。",
            icon = Icons.Filled.Home,
        )

        StartupRestoreStatus.SESSION_INVALID -> StartupStatusUi(
            title = "会话已失效",
            description = "之前的登录状态已经不能继续使用，请重新登录。",
            hint = "若你已更换服务器地址，建议先更新服务器配置。",
            icon = Icons.Filled.Warning,
        )

        StartupRestoreStatus.NO_DEFAULT_SERVER -> StartupStatusUi(
            title = "还没有默认服务器",
            description = "先添加一个服务器并设为默认，后续启动会更顺畅。",
            hint = "默认服务器用于启动自动恢复与登录入口。",
            icon = Icons.Filled.Settings,
        )
    }
}

private fun restoreStageLabel(status: StartupRestoreStatus): String {
    return when (status) {
        StartupRestoreStatus.LOADING -> "恢复中"
        StartupRestoreStatus.RESTORE_SUCCESS -> "准备完成"
        StartupRestoreStatus.SESSION_INVALID -> "会话失效"
        StartupRestoreStatus.NO_DEFAULT_SERVER -> "缺少默认服务器"
    }
}
