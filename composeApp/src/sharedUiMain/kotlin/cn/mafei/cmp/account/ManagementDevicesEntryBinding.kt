package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.session.*

@Composable
internal fun ManagementDevicesEntryBinding(
    route: BrowseManagementDevicesRoute,
    account: AccountFlowState,
    session: ApplicationSession,
    popMineRoute: () -> Unit,
    runtime: ApplicationRuntime,
) {
    ManagementDevicesRoute(runtime.sessions.media, session.media, route.deviceId, popMineRoute,
        onDevice = { id -> account.mineBackStack += BrowseManagementDevicesRoute(id) }, Modifier.fillMaxSize())
}
