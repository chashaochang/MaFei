package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.data.repositories.MediaSession
import cn.mafei.cmp.feature.management.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ManagementRoute(repository: MediaRepository, session: MediaSession, taskId: String?,
    onBack: () -> Unit, onTask: (String) -> Unit, modifier: Modifier = Modifier,
    sessionId: String? = null, onSession: (String) -> Unit = {}, onDevices: () -> Unit = {}) {
    var browser by remember(session) { mutableStateOf<ManagementBrowser?>(null) }
    var error by remember(session) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(session, taskId, sessionId, retry) {
        try {
            error = null
            val current = browser ?: ManagementBrowser(repository.management(session)).also { browser = it }
            if (sessionId != null) current.loadSession()
            else if (taskId == null) current.followDashboard() else current.loadTask()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "控制台加载失败，请重试" }
    }
    val state = browser?.state?.collectAsState()?.value ?: ManagementState()
    if (taskId == null && sessionId == null || state.allowed != true) ManagementDashboardScreen(
        state.copy(permissionError = error ?: state.permissionError), onBack,
        onRefresh = { retry++ }, onScan = { scope.launch { browser?.startScan() } },
        onTask = { onTask(it.id) }, modifier, onSession = { onSession(it.id) },
        onDevices = onDevices,
    ) else if (sessionId != null) ManagementSessionDetailScreen(state.sessions.value?.firstOrNull { it.id == sessionId },
        state.actionPending, state.actionMessage, onBack,
        onPlayPause = { scope.launch { browser?.sessionAction(sessionId, cn.mafei.cmp.core.domain.ManagedSessionCommand.PlayPause) } },
        onStop = { scope.launch { browser?.sessionAction(sessionId, cn.mafei.cmp.core.domain.ManagedSessionCommand.Stop) } },
        onMessage = { text -> scope.launch { browser?.sessionMessage(sessionId, text) } }, modifier)
    else ManagementTaskDetailScreen(state.tasks.value?.firstOrNull { it.id == taskId }, state.tasks.loading,
        error ?: state.tasks.error, state.actionPending, state.actionMessage, onBack, { retry++ },
        onAction = { state.tasks.value?.firstOrNull { it.id == taskId }?.let { task -> scope.launch { browser?.taskAction(task) } } }, modifier)
}
