package cn.mafei.cmp.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.ui.InlineProblem
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.core.domain.ProviderKind

/** Shell owns system insets. Password is composition-only and cleared after submission. */
@Composable
fun ConnectScreen(
    busy: Boolean,
    error: String?,
    onSubmit: (ProviderKind, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var provider by remember { mutableStateOf(ProviderKind.Jellyfin) }
    var baseUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val explicitAddress = baseUrl.trim().let { it.startsWith("https://") || it.startsWith("http://") }
    // Some Jellyfin accounts allow an empty password. The server owns authentication policy.
    val canSubmit = !busy && explicitAddress && username.isNotBlank()
    val submit: () -> Unit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onSubmit(provider, baseUrl.trim(), username.trim(), password)
            password = ""
            showPassword = false
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        val expanded = maxWidth >= 900.dp
        val inset = if (maxWidth < 600.dp) 20.dp else 40.dp
        val form: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                shape = RoundedCornerShape(if (expanded) 28.dp else 24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
                ),
            ) {
                Column(
                    Modifier.padding(if (expanded) 28.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("连接媒体服务器", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold)
                        Text("登录你的账号，打开私人媒体库。", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProviderOption("Jellyfin", provider == ProviderKind.Jellyfin, !busy, Modifier.weight(1f)) {
                            provider = ProviderKind.Jellyfin; password = ""; showPassword = false
                        }
                        ProviderOption("飞牛影视", provider == ProviderKind.Feiniu, !busy, Modifier.weight(1f)) {
                            provider = ProviderKind.Feiniu; password = ""; showPassword = false
                        }
                    }
                    OutlinedTextField(
                        baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), enabled = !busy,
                        label = { Text("服务器地址") }, placeholder = { Text("http://192.168.1.10:8096") },
                        supportingText = { Text(if (provider == ProviderKind.Feiniu) "填写影视服务完整地址，暂不支持 FNID。" else "包含 http:// 或 https://，可带端口和子路径。") },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        username, { username = it }, Modifier.fillMaxWidth(), enabled = !busy,
                        label = { Text("用户名") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        password, { password = it }, Modifier.fillMaxWidth(), enabled = !busy,
                        label = { Text("密码") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }, enabled = !busy) {
                                Text(if (showPassword) "隐藏" else "显示", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                    error?.let { InlineProblem(it) }
                    Button(onClick = submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp)) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(if (busy) "正在连接服务器…" else "进入媒体库", fontWeight = FontWeight.SemiBold)
                    }
                    Text("密码仅用于本次登录，不会保存。建议使用 HTTPS；HTTP 会明文传输登录数据。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(
            Modifier.widthIn(max = 1120.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = inset, vertical = if (expanded) 48.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (expanded) 28.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (expanded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    WelcomePanel(Modifier.weight(1f), expanded = true)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { form() }
                }
            } else {
                WelcomePanel(Modifier.widthIn(max = 460.dp).fillMaxWidth(), expanded = false)
                form()
            }
        }
    }
}

@Composable
private fun ProviderOption(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp).mafeiInteractive(
            onClick = onClick,
            enabled = enabled,
            radiusDp = 14f,
            label = label,
        ),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .18f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .46f),
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .42f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f),
        ),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun WelcomePanel(modifier: Modifier, expanded: Boolean) {
    if (expanded) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("马飞", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "把 Jellyfin 与飞牛影视放进同一个私人影院。",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(
                Modifier.width(72.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .65f),
                thickness = 2.dp,
            )
            Text(
                "连接后将直接进入你的真实媒体库。账号密码只用于本次认证。",
                Modifier.widthIn(max = 420.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("JELLYFIN  ·  飞牛影视", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Row(modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("马飞", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("连接你的私人媒体库", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("M", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary.copy(alpha = .34f),
                fontWeight = FontWeight.Light)
        }
    }
}
