package cn.xiaobai.mafei.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SplashLoginScreen(
    currentServer: JellyfinServer?,
    loginStatus: LoginUiStatus,
    statusMessage: String,
    onSwitchServer: () -> Unit,
    onEditAddress: () -> Unit,
    onInputChanged: () -> Unit,
    onSubmitLogin: (username: String, password: String, rememberSession: Boolean) -> Unit,
    onConfirmCertificate: (username: String, password: String, rememberSession: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberSession by remember { mutableStateOf(true) }
    val normalizedUsername = username.trim()
    val hasServer = currentServer != null
    var actionFeedbackMessage by remember(loginStatus, hasServer) { mutableStateOf<String?>(null) }
    val isSubmitting = loginStatus == LoginUiStatus.LOGGING_IN
    val canSubmit = !isSubmitting && hasServer && normalizedUsername.isNotEmpty() && password.isNotEmpty()
    val statusUi = statusUi(loginStatus)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AppSectionCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.animateContentSize(),
                secondary = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppHeaderRow(
                        title = "欢迎登录 MaFei",
                        subtitle = "登录后可继续浏览首页、媒体库、更新与收藏。",
                        leadingIcon = Icons.Filled.Person,
                        leadingEmphasized = false,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppPill(
                            text = if (hasServer) "服务器已就绪" else "待配置服务器",
                            emphasized = hasServer,
                        )
                        AppPill(text = if (rememberSession) "保持登录" else "临时登录")
                    }
                }
            }
        }

        item {
            AppSectionCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppHeaderRow(
                        title = "账号登录",
                        subtitle = "输入你的 Jellyfin 账号与密码",
                        leadingIcon = Icons.Filled.Person,
                        leadingEmphasized = false,
                    )

                    AppSectionCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        secondary = true,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("当前服务器", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = currentServer?.serverName ?: "还没有默认服务器",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = currentServer?.baseUrl ?: "请先设置默认服务器后再登录。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AppPill(
                                text = "连接状态：${currentServer?.let { healthLabel(it.health) } ?: "待检测"}",
                                emphasized = currentServer?.health == ServerHealthStatus.HEALTHY,
                            )
                            if (currentServer?.health == ServerHealthStatus.UNREACHABLE) {
                                AppInlineTip(
                                    message = "当前默认服务器不可达，建议先修正地址后再登录。",
                                    tone = AppStatusTone.Warning,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                actionFeedbackMessage = "正在切换服务器。"
                                onSwitchServer()
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("切换服务器")
                        }
                        OutlinedButton(
                            onClick = {
                                actionFeedbackMessage = "正在打开服务器设置。"
                                onEditAddress()
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (hasServer) "编辑地址" else "去设置服务器")
                        }
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            onInputChanged()
                        },
                        label = { Text("用户名") },
                        placeholder = { Text("请输入 Jellyfin 用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            onInputChanged()
                        },
                        label = { Text("密码") },
                        placeholder = { Text("请输入密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberSession,
                            enabled = !isSubmitting,
                            onCheckedChange = { checked ->
                                rememberSession = checked
                                onInputChanged()
                            },
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("保持登录")
                            Text(
                                text = if (rememberSession) {
                                    "将尽量在本机保持安全会话，减少重复登录。"
                                } else {
                                    "关闭后下次打开通常需要再次输入账号密码。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            actionFeedbackMessage = "正在验证账号与服务器连接。"
                            onSubmitLogin(normalizedUsername, password, rememberSession)
                        },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isSubmitting) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text("正在登录")
                            }
                        } else {
                            Text("登录")
                        }
                    }

                    if (!hasServer) {
                        AppStatusCard(
                            message = "登录前需要先准备好服务器。\n当前还没有默认服务器，请先前往服务器管理完成设置。",
                            tone = AppStatusTone.Warning,
                            leading = {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                        )
                    } else if (!isSubmitting && (normalizedUsername.isEmpty() || password.isEmpty())) {
                        AppStatusCard(
                            message = "请输入用户名和密码后继续。",
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

                    if (loginStatus == LoginUiStatus.CERTIFICATE_WARNING) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                            onClick = {
                                actionFeedbackMessage = "本次将继续尝试登录。"
                                onConfirmCertificate(
                                    normalizedUsername,
                                    password,
                                    rememberSession,
                                )
                                },
                                enabled = !isSubmitting && normalizedUsername.isNotEmpty() && password.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("仅本次继续")
                            }
                            OutlinedButton(
                                onClick = {
                                    actionFeedbackMessage = "先去调整服务器地址或证书设置。"
                                    onEditAddress()
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("修正地址")
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = loginStatus == LoginUiStatus.LOGIN_FAILED ||
                            loginStatus == LoginUiStatus.SERVER_UNREACHABLE ||
                            loginStatus == LoginUiStatus.VALIDATION_FAILED,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    actionFeedbackMessage = "先切换到可用服务器再继续。"
                                    onSwitchServer()
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("切换服务器")
                            }
                            OutlinedButton(
                                onClick = {
                                    actionFeedbackMessage = "正在重新尝试登录。"
                                    onSubmitLogin(normalizedUsername, password, rememberSession)
                                },
                                enabled = canSubmit,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("重试登录")
                            }
                        }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = !actionFeedbackMessage.isNullOrBlank()) {
                AppInlineTip(
                    message = actionFeedbackMessage.orEmpty(),
                    tone = AppStatusTone.Neutral,
                )
            }
        }

        item {
            AppStatusCard(
                title = statusUi.title,
                message = statusUi.hint,
                supportingText = statusMessage.takeIf { it.isNotBlank() },
                tone = statusUi.tone,
                leading = {
                    Icon(
                        imageVector = statusUi.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

private data class StatusUi(
    val title: String,
    val hint: String,
    val icon: ImageVector,
    val tone: AppStatusTone,
)

@Composable
private fun statusUi(loginStatus: LoginUiStatus): StatusUi {
    return when (loginStatus) {
        LoginUiStatus.DEFAULT,
        LoginUiStatus.INPUTTING -> StatusUi(
            title = "准备登录",
            hint = "请确认服务器地址后输入账号密码。",
            icon = Icons.Filled.Notifications,
            tone = AppStatusTone.Neutral,
        )

        LoginUiStatus.VALIDATION_FAILED -> StatusUi(
            title = "输入信息需要检查",
            hint = "请先补全用户名、密码或修正服务器地址。",
            icon = Icons.Filled.Warning,
            tone = AppStatusTone.Warning,
        )

        LoginUiStatus.LOGGING_IN -> StatusUi(
            title = "正在登录",
            hint = "正在验证账号与服务器连接，请稍候。",
            icon = Icons.Filled.Refresh,
            tone = AppStatusTone.Progress,
        )

        LoginUiStatus.LOGIN_FAILED -> StatusUi(
            title = "这次没能登录成功",
            hint = "可以先检查账号密码，或确认当前服务器是否可用。",
            icon = Icons.Filled.Warning,
            tone = AppStatusTone.Warning,
        )

        LoginUiStatus.SERVER_UNREACHABLE -> StatusUi(
            title = "服务器不可达",
            hint = "可以先检查网络、服务器地址，或切换到其他服务器。",
            icon = Icons.Filled.Warning,
            tone = AppStatusTone.Warning,
        )

        LoginUiStatus.CERTIFICATE_WARNING -> StatusUi(
            title = "证书告警",
            hint = "当前服务器证书存在风险，请先修复证书或更换服务器。",
            icon = Icons.Filled.Warning,
            tone = AppStatusTone.Warning,
        )
    }
}

private fun healthLabel(status: ServerHealthStatus): String = when (status) {
    ServerHealthStatus.HEALTHY -> "可连接"
    ServerHealthStatus.UNREACHABLE -> "不可达"
    ServerHealthStatus.UNKNOWN -> "待检测"
}
