package cn.mafei.cmp.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.AppGlassCard
import cn.mafei.cmp.common.ui.InlineProblem
import cn.mafei.cmp.common.ui.ScreenHeading
import cn.mafei.cmp.core.domain.ProviderKind

/** Shell owns system insets. Password is only remembered in this composition, never saved. */
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
    val focusManager = LocalFocusManager.current
    val explicitAddress = baseUrl.trim().let { it.startsWith("https://") || it.startsWith("http://") }
    // Some Jellyfin accounts allow an empty password; authentication policy belongs to the server.
    val canSubmit = !busy && explicitAddress && username.isNotBlank()
    val submit: () -> Unit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onSubmit(provider, baseUrl.trim(), username.trim(), password)
            password = ""
        }
    }
    Box(modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            ScreenHeading("连接你的媒体库", "MaFei · 让收藏的故事，随处继续")
            AppGlassCard(backdrop = null, modifier = Modifier.fillMaxWidth()) {
                Text("服务器类型", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = provider == ProviderKind.Jellyfin,
                        onClick = { provider = ProviderKind.Jellyfin; password = "" }, enabled = !busy,
                        label = { Text("Jellyfin") })
                    FilterChip(selected = provider == ProviderKind.Feiniu,
                        onClick = { provider = ProviderKind.Feiniu; password = "" }, enabled = !busy,
                        label = { Text("飞牛影视") })
                }
                OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), enabled = !busy,
                    label = { Text("服务器地址") }, placeholder = { Text("https://media.example.com") },
                    supportingText = { Text(if (provider == ProviderKind.Feiniu) "输入影视服务的完整地址，暂不支持 FNID" else "包含 http:// 或 https://，可带端口和子路径") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next))
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), enabled = !busy,
                    label = { Text("用户名") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !busy,
                    label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }))
                error?.let { InlineProblem(it) }
                Button(onClick = submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (busy) "正在连接…" else "连接并登录")
                }
            }
            Text("密码仅用于本次登录。登录凭证保存在设备安全存储中。建议使用 HTTPS；HTTP 会以明文传输登录数据。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
