package cn.mafei.cmp.feature.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.*

@Composable
fun ManagementDashboardScreen(
    state: ManagementState, onBack: () -> Unit, onRefresh: () -> Unit,
    onScan: () -> Unit, onTask: (ManagedTask) -> Unit, modifier: Modifier = Modifier,
    onSession: (ManagedSession) -> Unit = {},
    onDevices: () -> Unit = {},
) {
    var confirmScan by remember { mutableStateOf(false) }
    MaFeiActionBarScaffold("服务器控制台", onBack, modifier) {
        when {
            state.permissionError != null -> Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                Text(state.permissionError, color = MaterialTheme.colorScheme.onSurface)
                LegacyListGroup(listOf(LegacyListGroupItem("重试", showArrow = false)), { onRefresh() })
            }
            state.allowed != true -> MaFeiLoadingIndicator(Modifier.align(Alignment.Center))
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                val safe = LocalMaFeiSafeArea.current
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = safe.bottom + 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (wide) Row(Modifier.widthIn(max = 1180.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardPrimary(state, onRefresh, { confirmScan = true }, onTask, Modifier.weight(.57f))
                        DashboardSecondary(state, onRefresh, onSession, Modifier.weight(.43f))
                    } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardPrimary(state, onRefresh, { confirmScan = true }, onTask)
                        DashboardSecondary(state, onRefresh, onSession)
                    }
                    LegacyListGroup(listOf(LegacyListGroupItem("设备")), { onDevices() },
                        Modifier.widthIn(max = 1180.dp).padding(top = 12.dp))
                }
            }
        }
    }
    if (confirmScan) LegacyConfirmDialog("扫描媒体库", "确定要触发 Jellyfin 服务器扫描全部媒体库吗？",
        "开始扫描", "取消", { confirmScan = false; onScan() }, { confirmScan = false })
}

@Composable
private fun DashboardPrimary(state: ManagementState, onRefresh: () -> Unit, onScan: () -> Unit,
    onTask: (ManagedTask) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ManagementContentGroup {
            SectionState(state.server, onRefresh) { server ->
                Text(if (server.online) "服务器在线" else "服务器离线", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text(server.name.ifBlank { "未知服务器" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${server.productName.ifBlank { "Jellyfin" }} ${server.version}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            SectionState(state.counts, onRefresh) { counts ->
                Row(Modifier.fillMaxWidth()) {
                    Metric("电影", counts.movies, Modifier.weight(1f)); Metric("剧集", counts.series, Modifier.weight(1f))
                    Metric("单集", counts.episodes, Modifier.weight(1f))
                }
            }
            val scanning = state.tasks.value.orEmpty().any { it.key == "RefreshLibrary" && it.busy } || state.scanLockedUntil > 0
            LegacyListGroup(listOf(LegacyListGroupItem(if (state.actionPending) "正在启动扫描" else if (scanning) "媒体库正在扫描" else "扫描媒体库", showArrow = false)),
                { onScan() }, enabled = !state.actionPending && !scanning)
            state.actionMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        }
        ManagementContentGroup {
            GroupHeading("用户")
            SectionState(state.users, onRefresh) { users -> Row(Modifier.fillMaxWidth()) {
                Metric("总数", users.size, Modifier.weight(1f))
                Metric("管理员", users.count { it.administrator }, Modifier.weight(1f))
                Metric("已禁用", users.count { it.disabled }, Modifier.weight(1f))
            } }
        }
        ManagementContentGroup {
            GroupHeading("正在运行的任务")
            SectionState(state.tasks, onRefresh) { tasks ->
                val running = tasks.filter { !it.hidden && it.busy }
                if (running.isEmpty()) Text("当前没有正在运行的任务", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                else LegacyListGroup(running.map { LegacyListGroupItem(it.name, trailing = "${it.progress.toInt()}%", subtitle = it.category) }, { onTask(running[it]) })
            }
        }
    }
}

@Composable
private fun DashboardSecondary(state: ManagementState, onRefresh: () -> Unit, onSession: (ManagedSession) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ManagementContentGroup {
            GroupHeading("当前播放会话")
            SectionState(state.sessions, onRefresh) { sessions ->
                if (sessions.isEmpty()) Text("当前没有播放中的会话", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                sessions.forEach { session -> Column(Modifier.fillMaxWidth().mafeiInteractive({ onSession(session) }, label = session.itemName.ifBlank { session.userName }), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(session.itemName.ifBlank { session.userName }, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Text(listOf(session.userName, session.deviceName, session.itemSubtitle).filter(String::isNotBlank).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    if (session.durationMs > 0) MaFeiProgressLine((session.positionMs.toFloat() / session.durationMs).coerceIn(0f, 1f))
                } }
            }
        }
        ManagementContentGroup {
            GroupHeading("最近活动")
            SectionState(state.activity, onRefresh) { page ->
                if (page.items.isEmpty()) Text("暂无活动记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                page.items.forEach { activity -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(activity.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    if (activity.shortOverview.isNotBlank()) Text(activity.shortOverview, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(listOf(activity.date, activity.userName).filter(String::isNotBlank).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                } }
            }
        }
    }
}

@Composable
fun ManagementSessionDetailScreen(session: ManagedSession?, pending: Boolean, message: String?, onBack: () -> Unit,
    onPlayPause: () -> Unit, onStop: () -> Unit, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    var confirmStop by remember { mutableStateOf(false) }
    var draft by remember(session?.id) { mutableStateOf("") }
    MaFeiActionBarScaffold("会话详情", onBack, modifier) {
        if (session == null) Text("信息已失效，请返回后重试", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurface)
        else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            .padding(bottom = LocalMaFeiSafeArea.current.bottom + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ManagementContentGroup {
                Text(session.itemName.ifBlank { session.userName }, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (session.itemSubtitle.isNotBlank()) Text(session.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (session.durationMs > 0) MaFeiProgressLine((session.positionMs.toFloat() / session.durationMs).coerceIn(0f, 1f))
            }
            LegacyListGroup(listOf(
                detailRow("播放方式", when (session.playbackKind) {
                    ManagedPlaybackKind.DirectPlay -> "直接播放"; ManagedPlaybackKind.DirectStream -> "直接串流"
                    ManagedPlaybackKind.Remux -> "重新封装"; ManagedPlaybackKind.Transcode -> "转码"; else -> "未播放"
                }),
                detailRow("进度", "${session.positionMs / 1000}s / ${session.durationMs / 1000}s"),
                detailRow("最后活动", session.lastActivity),
            ), {})
            if (session.playbackKind in setOf(ManagedPlaybackKind.Transcode, ManagedPlaybackKind.Remux, ManagedPlaybackKind.DirectStream)) LegacyListGroup(listOf(
                detailRow("码率", session.bitrate.takeIf { it > 0 }?.toString().orEmpty()),
                detailRow("帧率", session.framerate.takeIf { it > 0 }?.toString().orEmpty()),
                detailRow("封装", session.container), detailRow("视频编码", session.videoCodec),
                detailRow("音频编码", session.audioCodec), detailRow("分辨率", session.resolution),
                detailRow("转码原因", session.transcodeReasons.joinToString(", ")),
            ), {})
            LegacyListGroup(listOf(detailRow("用户", session.userName), detailRow("设备", session.deviceName),
                detailRow("设备类型", session.deviceType), detailRow("客户端", session.client),
                detailRow("网络地址", session.remoteAddress)), {})
            if (session.controllable) {
                LegacyListGroup(listOf(LegacyListGroupItem(if (session.paused) "继续播放" else "暂停播放", showArrow = false),
                    LegacyListGroupItem("停止播放", showArrow = false)), { if (it == 0) onPlayPause() else confirmStop = true }, enabled = !pending)
                ManagementTextInput(draft, { draft = it }, "向此客户端发送消息")
                LegacyListGroup(listOf(LegacyListGroupItem("发送消息", showArrow = false)), { onMessage(draft.trim()) }, enabled = !pending && draft.isNotBlank())
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (confirmStop && session != null) LegacyConfirmDialog("停止播放", "${session.userName} · ${session.deviceName}\n${session.itemName}",
        "停止播放", "取消", { confirmStop = false; onStop() }, { confirmStop = false }, destructive = true)
}

@Composable
fun ManagementTaskDetailScreen(task: ManagedTask?, loading: Boolean, error: String?, pending: Boolean,
    message: String?, onBack: () -> Unit, onRetry: () -> Unit, onAction: () -> Unit, modifier: Modifier = Modifier) {
    MaFeiActionBarScaffold("任务详情", onBack, modifier) {
        if (task == null) Column(Modifier.align(Alignment.Center).padding(24.dp)) {
            if (loading) MaFeiLoadingIndicator() else {
                Text(error ?: "信息已失效，请返回后重试", color = MaterialTheme.colorScheme.onSurface)
                LegacyListGroup(listOf(LegacyListGroupItem("重试", showArrow = false)), { onRetry() })
            }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            .padding(bottom = LocalMaFeiSafeArea.current.bottom + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ManagementContentGroup {
                Text(task.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(taskStateText(task), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                MaFeiProgressLine((task.progress / 100).toFloat())
            }
            LegacyListGroup(listOf(
                detailRow("任务名称", task.name), detailRow("状态", taskStateText(task)), detailRow("分类", task.category),
                detailRow("进度", "${task.progress.toInt()}%"), detailRow("任务标识", task.key),
                detailRow("上次开始", task.lastStart), detailRow("上次结束", task.lastEnd), detailRow("上次结果", task.lastStatus),
            ), {})
            if (task.description.isNotBlank()) ManagementContentGroup { GroupHeading("说明"); Text(task.description, color = MaterialTheme.colorScheme.onSurface) }
            if (task.lastError.isNotBlank()) ManagementContentGroup { GroupHeading("上次错误"); Text(task.lastError, color = MaterialTheme.colorScheme.onSurface) }
            message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (task.id.isNotBlank()) LegacyListGroup(listOf(LegacyListGroupItem(if (task.busy) "取消任务" else "启动任务", showArrow = false)), { onAction() }, enabled = !pending)
        }
    }
}

private fun detailRow(label: String, value: String) = LegacyListGroupItem(label, trailing = value.ifBlank { "未提供" }, showArrow = false)
private fun taskStateText(task: ManagedTask) = when (task.state) { "Running" -> "运行中"; "Cancelling" -> "正在取消"; else -> task.state.ifBlank { "未提供" } }
@Composable private fun GroupHeading(title: String) { Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
@Composable private fun Metric(label: String, value: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value.toString(), color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}
@Composable private fun ManagementContentGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable private fun <T> SectionState(section: ManagementSection<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    section.value?.let { content(it) }
    when {
        section.error != null -> Text(if (section.value == null) section.error else "刷新失败，正在显示上次结果",
            Modifier.mafeiInteractive(onRetry, label = "重试").padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        section.loading && section.value == null -> MaFeiLoadingIndicator()
    }
}

@Composable
internal fun ManagementTextInput(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.foundation.text.BasicTextField(value, onChange, modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        enabled = enabled, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        decorationBox = { field -> Box { if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant); field() } })
}
