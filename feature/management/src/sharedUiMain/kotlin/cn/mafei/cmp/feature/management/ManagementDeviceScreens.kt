package cn.mafei.cmp.feature.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.ManagedDevice

@Composable
fun ManagementDevicesScreen(state: ManagementDevicesState, currentDeviceId: String, nowMs: Long,
    onBack: () -> Unit, onRefresh: () -> Unit, onDevice: (String) -> Unit, onRevokeOthers: () -> Unit,
    modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf(DeviceQuery()) }
    var showFilter by remember { mutableStateOf(false) }
    var confirmOthers by remember { mutableStateOf(false) }
    val devices = state.devices.orEmpty()
    val page = remember(devices, query, nowMs) { queryDevices(devices, query, nowMs) }
    val revocable = devices.count { it.id.isNotBlank() && it.id != currentDeviceId && !it.current }
    val canRevoke = state.permissionError == null && !state.pending && currentDeviceId.isNotBlank() && revocable > 0
    MaFeiActionBarScaffold("设备", onBack, modifier, actionCount = if (canRevoke) 1 else 0, actions = { backdrop ->
        if (canRevoke) MaFeiActionBarButton({ confirmOthers = true }, backdrop, "撤销其他设备") {
            Image(painterResource(MaFeiLegacyDrawables.delete), null, Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error))
        }
    }) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 960.dp).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = LocalMaFeiSafeArea.current.bottom + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ManagementTextInput(query.text, { query = query.copy(text = it, page = 0) }, "搜索设备、应用或用户")
            LegacyListGroup(listOf(LegacyListGroupItem("筛选设备")), { showFilter = true })
            if (state.permissionError == null) {
                if (page.items.isNotEmpty()) LegacyListGroup(page.items.map { device -> LegacyListGroupItem(
                    device.displayName, trailing = if (device.current) "当前设备" else "",
                    subtitle = "${device.appName} ${device.appVersion} · ${device.lastUserName}\n${device.lastActivity}",
                ) }, { onDevice(page.items[it].id) })
                else if (!state.loading && state.error == null) LegacyEmptyView("暂无已授权设备")
                if (page.pages > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegacyListGroup(listOf(LegacyListGroupItem("上一页", showArrow = false)), { query = query.copy(page = page.page - 1) }, Modifier.weight(1f), enabled = page.page > 0)
                    Text("${page.page + 1} / ${page.pages}", color = MaterialTheme.colorScheme.onSurface)
                    LegacyListGroup(listOf(LegacyListGroupItem("下一页", showArrow = false)), { query = query.copy(page = page.page + 1) }, Modifier.weight(1f), enabled = page.page + 1 < page.pages)
                }
            }
            DeviceFeedback(state, onRefresh)
        }
    }
    if (showFilter) DeviceFilterSheet(query, devices.map { it.lastUserName }.filter(String::isNotBlank).distinct(),
        onDismiss = { showFilter = false }, onApply = { query = it.copy(page = 0); showFilter = false })
    if (confirmOthers) LegacyConfirmDialog("撤销设备授权？", "将撤销设备数量：$revocable\n当前设备将保留授权", "撤销授权", "取消",
        { confirmOthers = false; onRevokeOthers() }, { confirmOthers = false }, destructive = true)
}

@Composable
fun ManagementDeviceDetailScreen(device: ManagedDevice?, state: ManagementDevicesState, onBack: () -> Unit,
    onRefresh: () -> Unit, onRename: (String) -> Unit, onRevoke: () -> Unit, modifier: Modifier = Modifier) {
    var name by remember(device?.id, device?.customName) { mutableStateOf(device?.displayName.orEmpty()) }
    var confirm by remember { mutableStateOf(false) }
    MaFeiActionBarScaffold("设备详情", onBack, modifier) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = LocalMaFeiSafeArea.current.bottom + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (device != null && state.permissionError == null) {
                Text(device.displayName, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                Text(if (device.current) "当前设备" else "已授权", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LegacyListGroup(listOf(
                    LegacyListGroupItem("应用", trailing = "${device.appName} ${device.appVersion}", showArrow = false),
                    LegacyListGroupItem("最近用户", trailing = device.lastUserName, showArrow = false),
                    LegacyListGroupItem("最近活动", trailing = device.lastActivity, showArrow = false),
                ), {})
                Text("自定义名称", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ManagementTextInput(name, { name = it }, device.name, enabled = !state.pending)
                LegacyListGroup(listOf(LegacyListGroupItem("保存名称", showArrow = false)), { onRename(name) }, enabled = !state.pending)
                LegacyListGroup(listOf(LegacyListGroupItem("撤销授权", showArrow = false)), { confirm = true }, enabled = !state.pending && !device.current)
                if (device.current) Text("当前设备不能撤销授权", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!state.loading && state.error == null && state.permissionError == null) Text("信息已失效，请返回后重试", color = MaterialTheme.colorScheme.onSurface)
            DeviceFeedback(state, onRefresh)
        }
    }
    if (confirm && device != null) LegacyConfirmDialog("撤销设备授权？", device.displayName, "撤销授权", "取消",
        { confirm = false; onRevoke() }, { confirm = false }, destructive = true)
}

@Composable private fun DeviceFeedback(state: ManagementDevicesState, onRefresh: () -> Unit) {
    val error = state.permissionError ?: state.error
    if (state.loading && state.devices == null) MaFeiLoadingIndicator()
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.onSurface)
        LegacyListGroup(listOf(LegacyListGroupItem("重试", showArrow = false)), { onRefresh() })
    }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable private fun DeviceFilterSheet(initial: DeviceQuery, users: List<String>, onDismiss: () -> Unit, onApply: (DeviceQuery) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    LegacyModalSheet("筛选设备", onDismiss, .8f) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("活动时间", color = MaterialTheme.colorScheme.onSurface)
            val days = listOf(0, 7, 30)
            LegacyListGroup(days.map { LegacyListGroupItem(if (it == 0) "全部时间" else "$it 天", isSelected = draft.recentDays == it) },
                { draft = draft.copy(recentDays = days[it]) })
            Text("排序", color = MaterialTheme.colorScheme.onSurface)
            val titles = listOf("按最近活动", "按名称", "按应用", "按用户")
            LegacyListGroup(DeviceSort.entries.map { LegacyListGroupItem(titles[it.ordinal], isSelected = draft.sort == it) },
                { draft = draft.copy(sort = DeviceSort.entries[it]) })
            LegacyListGroup(listOf(LegacyListGroupItem("升序", isSelected = draft.ascending)), { draft = draft.copy(ascending = !draft.ascending) })
            if (users.isNotEmpty()) {
                Text("最近用户", color = MaterialTheme.colorScheme.onSurface)
                LegacyListGroup(users.map { LegacyListGroupItem(it, isSelected = it in draft.users) }, { index ->
                    val user = users[index]; draft = draft.copy(users = if (user in draft.users) draft.users - user else draft.users + user)
                })
            }
            LegacyListGroup(listOf(LegacyListGroupItem("重置", showArrow = false), LegacyListGroupItem("应用", showArrow = false)),
                { if (it == 0) draft = DeviceQuery(text = initial.text) else onApply(draft) })
        }
    }
}
