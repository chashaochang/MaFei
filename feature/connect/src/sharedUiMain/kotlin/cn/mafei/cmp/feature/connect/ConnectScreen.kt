package cn.mafei.cmp.feature.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
            ) {
                Column(Modifier.padding(if (expanded) 28.dp else 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    Button(onClick = submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        shape = RoundedCornerShape(14.dp)) {
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
                .padding(horizontal = inset, vertical = if (expanded) 48.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
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
    FilterChip(
        selected = selected, onClick = onClick, enabled = enabled,
        label = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        } }, modifier = modifier.heightIn(min = 46.dp), shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun WelcomePanel(modifier: Modifier, expanded: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (expanded) 24.dp else 12.dp)) {
        Text("YOUR PRIVATE CINEMA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(if (expanded) "好故事，\n都在这里。" else "好故事，都在这里。",
            style = if (expanded) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold)
        Text("把自己的电影与剧集，\n放进一个熟悉的地方。", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (expanded) {
            Box(
                Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF343667), Color(0xFF222940), Color(0xFF162F39))))
                    .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(24.dp)),
            ) {
                Row(Modifier.align(Alignment.Center).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CinemaTile("M", Color(0xFF7C78B7), Modifier.weight(1f).height(112.dp))
                    CinemaTile("A", Color(0xFF537F8B), Modifier.weight(1f).height(140.dp))
                    CinemaTile("F", Color(0xFF94736F), Modifier.weight(1f).height(112.dp))
                }
            }
            Text("Jellyfin  /  飞牛影视", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Brand illustration, deliberately not presented as real server artwork. */
@Composable
private fun CinemaTile(letter: String, tint: Color, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = .2f))))
        .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Text(letter, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Light, color = Color.White.copy(alpha = .8f))
    }
}
