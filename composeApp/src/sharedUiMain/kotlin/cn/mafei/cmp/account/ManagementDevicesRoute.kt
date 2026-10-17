package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.data.repositories.MediaSession
import cn.mafei.cmp.feature.management.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ManagementDevicesRoute(repository: MediaRepository, session: MediaSession, deviceId: String?,
    onBack: () -> Unit, onDevice: (String) -> Unit, modifier: Modifier = Modifier) {
    var browser by remember(session) { mutableStateOf<ManagementDevicesBrowser?>(null) }
    var error by remember(session) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(session, retry) {
        try {
            error = null
            val current = browser ?: ManagementDevicesBrowser(repository.management(session)).also { browser = it }
            current.refresh()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "设备加载失败，请重试" }
    }
    val state = browser?.state?.collectAsState()?.value ?: ManagementDevicesState()
    val rendered = state.copy(error = error ?: state.error, loading = error == null && state.loading)
    if (deviceId == null) ManagementDevicesScreen(rendered, browser?.currentDeviceId.orEmpty(), browser?.clock?.nowEpochMs() ?: 0,
        onBack, { retry++ }, onDevice, { scope.launch { browser?.revokeOthers() } }, modifier)
    else ManagementDeviceDetailScreen(rendered.devices?.firstOrNull { it.id == deviceId }, rendered, onBack, { retry++ },
        { name -> scope.launch { browser?.rename(deviceId, name) } }, { scope.launch { browser?.revoke(deviceId) } }, modifier)
}
