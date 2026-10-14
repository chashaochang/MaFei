package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.session.*

@Composable
internal fun ManagementEntryBinding(
    route: BrowseManagementRoute,
    account: AccountFlowState,
    session: ApplicationSession,
    popMineRoute: () -> Unit,
    runtime: ApplicationRuntime,
) {
    ManagementRoute(runtime.sessions.media, session.media, route.taskId, popMineRoute,
        onTask = { id -> account.mineBackStack += BrowseManagementRoute(taskId = id) }, Modifier.fillMaxSize(),
        sessionId = route.sessionId, onSession = { id -> account.mineBackStack += BrowseManagementRoute(sessionId = id) },
        onDevices = { account.mineBackStack += BrowseManagementDevicesRoute() })
}
