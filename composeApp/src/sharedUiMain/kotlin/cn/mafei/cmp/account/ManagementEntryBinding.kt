package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.session.*

@Composable
internal fun ManagementEntryBinding(
    route: BrowseManagementRoute,
    account: AccountFlowState,
    mediaSession: cn.mafei.cmp.data.repositories.MediaSession,
    popMineRoute: () -> Unit,
    repository: cn.mafei.cmp.data.repositories.MediaRepository,
) {
    ManagementRoute(
        repository,
        mediaSession,
        route.taskId,
        popMineRoute,
        onTask = { id -> account.mineBackStack += BrowseManagementRoute(taskId = id) },
        Modifier.fillMaxSize(),
        sessionId = route.sessionId,
        onSession = { id -> account.mineBackStack += BrowseManagementRoute(sessionId = id) },
        onDevices = { account.mineBackStack += BrowseManagementDevicesRoute() },
    )
}
