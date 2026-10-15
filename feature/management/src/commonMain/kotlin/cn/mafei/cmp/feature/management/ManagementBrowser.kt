package cn.mafei.cmp.feature.management

import cn.mafei.cmp.common.core.EpochClock
import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.serialization.formatIsoInstant
import cn.mafei.cmp.data.adapter_contract.ServerManagement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ManagementSection<T>(val value: T? = null, val loading: Boolean = true, val error: String? = null)
data class ManagementState(
    val allowed: Boolean? = null, val permissionError: String? = null,
    val server: ManagementSection<ManagedServer> = ManagementSection(),
    val counts: ManagementSection<ManagedMediaCounts> = ManagementSection(),
    val users: ManagementSection<List<ManagedUser>> = ManagementSection(),
    val tasks: ManagementSection<List<ManagedTask>> = ManagementSection(),
    val sessions: ManagementSection<List<ManagedSession>> = ManagementSection(),
    val activity: ManagementSection<ManagedActivityPage> = ManagementSection(),
    val actionPending: Boolean = false, val actionMessage: String? = null,
    val scanLockedUntil: Long = 0,
)

/** Visible-route jobs own polling; account-scoped Repository owns request validity. */
class ManagementBrowser(private val api: ServerManagement, private val clock: EpochClock = SystemEpochClock) {
    private val mutable = MutableStateFlow(ManagementState())
    val state: StateFlow<ManagementState> = mutable.asStateFlow()
    private val refreshes = Mutex()
    private val actions = Mutex()
    private val taskReads = Mutex()
    private val sessionReads = Mutex()

    suspend fun refresh(): Unit = refreshes.withLock {
        if (!checkPermission()) return@withLock
        coroutineScope {
            listOf(
                async { updateSection({ it.server }, { state, value -> state.copy(server = value) }) { api.server() } },
                async { updateSection({ it.counts }, { state, value -> state.copy(counts = value) }) { api.counts() } },
                async { updateSection({ it.users }, { state, value -> state.copy(users = value) }) { api.users() } },
                async { refreshTasks() }, async { refreshSessions() },
                async { updateSection({ it.activity }, { state, value -> state.copy(activity = value) }) {
                    api.activity(0, 7, formatIsoInstant(clock.nowEpochMs() - 86_400_000), true)
                } },
            ).awaitAll()
        }
    }

    suspend fun followDashboard(): Unit = coroutineScope {
        refresh()
        if (state.value.allowed != true) return@coroutineScope
        launch {
            while (isActive && state.value.allowed == true) {
                delay(3000)
                val current = state.value
                if (current.tasks.value.orEmpty().any { it.busy } || current.scanLockedUntil > 0) refreshTasks()
            }
        }
        launch {
            while (isActive && state.value.allowed == true) { delay(2000); refreshSessions() }
        }
    }

    suspend fun refreshTasks(): Unit = taskReads.withLock {
        updateSection({ it.tasks }, { state, value -> state.copy(tasks = value) }) { api.tasks() }
        mutable.update { current ->
            val scanBusy = current.tasks.value.orEmpty().any { it.key == "RefreshLibrary" && it.busy }
            if (scanBusy || clock.nowEpochMs() >= current.scanLockedUntil) current.copy(scanLockedUntil = 0) else current
        }
    }
    suspend fun loadTask() { if (checkPermission()) refreshTasks() }
    private suspend fun refreshSessions(): Unit = sessionReads.withLock {
        updateSection({ it.sessions }, { state, value -> state.copy(sessions = value) }) { api.sessions(clock.nowEpochMs()) }
    }
    suspend fun loadSession() { if (checkPermission()) refreshSessions() }
    suspend fun sessionAction(id: String, command: ManagedSessionCommand) = action {
        api.sessionCommand(id, command); refreshSessions()
        if (command == ManagedSessionCommand.Stop) "已停止播放" else "操作成功"
    }
    suspend fun sessionMessage(id: String, text: String) = action {
        api.sessionMessage(id, text); "消息已发送"
    }

    suspend fun startScan() = action {
        if (state.value.scanLockedUntil > clock.nowEpochMs()) return@action "媒体库正在扫描"
        mutable.update { it.copy(scanLockedUntil = clock.nowEpochMs() + 15_000) }
        try {
            when (api.startLibraryScan()) {
                LibraryScanResult.Started -> "已开始扫描媒体库"
                LibraryScanResult.AlreadyRunning -> "媒体库正在扫描"
                LibraryScanResult.Unavailable -> { mutable.update { it.copy(scanLockedUntil = 0) }; "当前服务器未提供媒体库扫描任务" }
            }.also { refreshTasks() }
        } catch (failure: Exception) { mutable.update { it.copy(scanLockedUntil = 0) }; throw failure }
    }
    suspend fun taskAction(task: ManagedTask) = action {
        if (task.busy) api.cancelTask(task.id) else api.startTask(task.id)
        refreshTasks()
        if (task.busy) "已取消任务" else "任务已启动"
    }

    private suspend fun checkPermission(): Boolean = try {
        val allowed = api.isAdministrator()
        mutable.update { it.copy(allowed = allowed, permissionError = if (allowed) null else "当前账号不是服务器管理员") }
        allowed
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { mutable.update { it.copy(allowed = null, permissionError = "权限检查失败，请重试") }; false }

    private suspend fun action(block: suspend () -> String) {
        if (!actions.tryLock()) return
        try {
            mutable.update { it.copy(actionPending = true, actionMessage = null) }
            if (!checkPermission()) return
            val message = block()
            mutable.update { it.copy(actionMessage = message) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutable.update { it.copy(actionMessage = failureText(failure)) } }
        finally { mutable.update { it.copy(actionPending = false) }; actions.unlock() }
    }

    private suspend fun <T> updateSection(
        read: (ManagementState) -> ManagementSection<T>,
        write: (ManagementState, ManagementSection<T>) -> ManagementState,
        request: suspend () -> T,
    ) {
        try {
            val value = request()
            mutable.update { write(it, ManagementSection(value, loading = false)) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            mutable.update {
                val failed = write(it, read(it).copy(loading = false, error = failureText(failure)))
                if (failure is AuthorizationError || failure is AuthenticationError) failed.copy(allowed = false, permissionError = failureText(failure)) else failed
            }
        }
    }

    private fun failureText(failure: Exception) = when (failure) {
        is AuthorizationError -> "当前账号不是服务器管理员"
        is AuthenticationError -> "登录已失效，请重新登录"
        is ResourceNotFoundError -> "当前服务器版本不支持此信息"
        else -> "加载失败，请重试"
    }
}
