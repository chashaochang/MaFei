package cn.mafei.cmp.feature.management

import cn.mafei.cmp.common.core.EpochClock
import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.serialization.parseIsoInstantToEpochMs
import cn.mafei.cmp.data.adapter_contract.ServerManagement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class DeviceSort { LastActivity, Name, App, User }
data class DeviceQuery(val text: String = "", val recentDays: Int = 0, val users: Set<String> = emptySet(),
    val sort: DeviceSort = DeviceSort.LastActivity, val ascending: Boolean = false, val page: Int = 0)
data class DevicePage(val items: List<ManagedDevice>, val page: Int, val pages: Int, val total: Int)
fun queryDevices(devices: List<ManagedDevice>, query: DeviceQuery, nowMs: Long): DevicePage {
    val text = query.text.trim().lowercase(); val names = query.users.map(String::lowercase).toSet()
    val since = nowMs - query.recentDays * 86_400_000L
    val filtered = devices.filter {
        (text.isEmpty() || "${it.displayName} ${it.appName} ${it.lastUserName}".lowercase().contains(text)) &&
            (query.recentDays <= 0 || (parseIsoInstantToEpochMs(it.lastActivity) ?: Long.MIN_VALUE) >= since) &&
            (names.isEmpty() || it.lastUserName.lowercase() in names)
    }.sortedWith { a, b ->
        fun field(item: ManagedDevice) = when (query.sort) {
            DeviceSort.LastActivity -> item.lastActivity; DeviceSort.Name -> item.displayName
            DeviceSort.App -> "${item.appName} ${item.appVersion}"; DeviceSort.User -> item.lastUserName
        }
        val comparison = field(a).compareTo(field(b), ignoreCase = true)
        if (query.ascending) comparison else -comparison
    }
    val pages = (filtered.size + 24) / 25; val page = query.page.coerceIn(0, (pages - 1).coerceAtLeast(0))
    return DevicePage(filtered.drop(page * 25).take(25), page, pages, filtered.size)
}
data class ManagementDevicesState(val devices: List<ManagedDevice>? = null, val loading: Boolean = true,
    val error: String? = null, val permissionError: String? = null, val pending: Boolean = false, val message: String? = null)

class ManagementDevicesBrowser(private val api: ServerManagement, val clock: EpochClock = SystemEpochClock) {
    private val mutable = MutableStateFlow(ManagementDevicesState())
    val state = mutable.asStateFlow()
    val currentDeviceId get() = api.currentDeviceId
    private val reads = Mutex(); private val actions = Mutex()
    suspend fun refresh(): Unit = reads.withLock {
        mutable.update { it.copy(loading = true, error = null) }
        try {
            if (!permission()) return@withLock
            val devices = api.devices()
            mutable.update { it.copy(devices = devices, loading = false, error = null) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutable.update { it.copy(loading = false, error = failureText(failure)) } }
    }
    suspend fun rename(id: String, name: String) = action {
        api.renameDevice(id, name); refresh(); "设备名称已更新"
    }
    suspend fun revoke(id: String) = action { api.revokeDevice(id); refresh(); "已撤销设备授权" }
    suspend fun revokeOthers() = action {
        val current = api.currentDeviceId.takeIf { it.isNotBlank() } ?: throw AuthorizationError("Current device is unknown")
        val all = api.devices()
        val ids = all.filter { !it.current && it.id.isNotBlank() && it.id != current }.map { it.id }.distinct()
        var failed = 0
        for (id in ids) try { api.revokeDevice(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed++ }
        refresh()
        if (failed == 0) "已撤销设备授权" else "成功 ${ids.size - failed} · 失败 $failed"
    }
    private suspend fun permission(): Boolean {
        val allowed = api.isAdministrator()
        mutable.update { it.copy(permissionError = if (allowed) null else "当前账号不是服务器管理员", loading = if (allowed) it.loading else false) }
        return allowed
    }
    private suspend fun action(block: suspend () -> String) {
        if (!actions.tryLock()) return
        try {
            mutable.update { it.copy(pending = true, message = null) }
            if (permission()) { val message = block(); mutable.update { it.copy(message = message) } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutable.update { it.copy(message = failureText(failure)) } }
        finally { mutable.update { it.copy(pending = false) }; actions.unlock() }
    }
    private fun failureText(error: Exception) = when (error) {
        is AuthorizationError -> "当前账号不是服务器管理员"
        is AuthenticationError -> "登录已失效，请重新登录"
        is ResourceNotFoundError -> "当前服务器不支持设备管理"
        else -> "设备操作失败，请重试"
    }
}
