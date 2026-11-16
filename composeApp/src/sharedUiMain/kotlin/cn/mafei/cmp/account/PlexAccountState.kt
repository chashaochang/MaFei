package cn.mafei.cmp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.mafei.cmp.feature.connect.PlexConnectPhase
import cn.mafei.cmp.feature.connect.PlexConnectServer
import cn.mafei.cmp.feature.connect.PlexConnectUiState
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** Cloud credentials stay inside the account boundary and expire with this flow. */
internal class PlexAccountState(
    private val access: AccountAccess,
    private val scope: CoroutineScope,
    private val isBusy: () -> Boolean,
    private val onBusyChange: (Boolean) -> Unit,
    private val onError: (String?) -> Unit,
) {
    var ui by mutableStateOf(PlexConnectUiState())
        private set
    private var challenge: PlexLoginChallenge? = null
    private var servers: PlexLoginServers? = null
    private var job: Job? = null
    private var generation = 0

    fun authorize(openBrowser: (String) -> Unit) {
        if (isBusy()) return
        cancel()
        val request = generation
        onError(null)
        ui = PlexConnectUiState(PlexConnectPhase.Starting)
        job = scope.launch {
            try {
                val pending = access.beginPlexLogin()
                coroutineContext.ensureActive()
                if (request != generation) return@launch
                challenge = pending
                ui = PlexConnectUiState(PlexConnectPhase.Waiting)
                reopen(openBrowser)
                coroutineContext.ensureActive()
                val authorized = access.awaitPlexServers(pending)
                coroutineContext.ensureActive()
                if (request != generation) return@launch
                challenge = null
                if (authorized.choices.isEmpty()) {
                    ui = PlexConnectUiState()
                    onError("此 Plex 账号下没有可连接的影视服务器")
                } else {
                    servers = authorized
                    ui = PlexConnectUiState(PlexConnectPhase.Choosing,
                        authorized.choices.map { PlexConnectServer(it.id, it.name) })
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == generation) {
                    challenge = null
                    servers = null
                    ui = PlexConnectUiState()
                    onError(connectFailureMessage(failure))
                }
            }
        }
    }

    fun reopen(openBrowser: (String) -> Unit) {
        val pending = challenge ?: return
        try {
            openBrowser(pending.browserUrl)
        } catch (_: Exception) {
            onError("无法打开 Plex 授权页面，请检查默认浏览器")
        }
    }

    fun connect(serverId: String, onConnected: () -> Unit = {}) {
        val authorized = servers ?: return
        if (isBusy() || ui.phase != PlexConnectPhase.Choosing || authorized.choices.none { it.id == serverId }) return
        val request = generation
        onBusyChange(true)
        onError(null)
        ui = ui.copy(phase = PlexConnectPhase.Connecting)
        job = scope.launch {
            try {
                access.connectPlex(authorized, serverId)
                coroutineContext.ensureActive()
                if (request == generation) {
                    servers = null
                    ui = PlexConnectUiState()
                    onConnected()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == generation) {
                    ui = ui.copy(phase = PlexConnectPhase.Choosing)
                    onError(connectFailureMessage(failure))
                }
            } finally {
                if (request == generation) onBusyChange(false)
            }
        }
    }

    fun cancel() {
        val wasConnecting = ui.phase == PlexConnectPhase.Connecting
        generation += 1
        job?.cancel()
        job = null
        challenge = null
        servers = null
        ui = PlexConnectUiState()
        if (wasConnecting) onBusyChange(false)
    }
}
