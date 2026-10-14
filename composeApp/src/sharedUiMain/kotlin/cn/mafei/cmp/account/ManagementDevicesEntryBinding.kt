package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.session.*

@Composable
internal fun ManagementDevicesEntryBinding(
    route: BrowseManagementDevicesRoute,
    account: AccountFlowState,
    mediaSession: cn.mafei.cmp.data.repositories.MediaSession,
    popMineRoute: () -> Unit,
    repository: cn.mafei.cmp.data.repositories.MediaRepository,
) {
    ManagementDevicesRoute(
        repository,
        mediaSession,
        route.deviceId,
        popMineRoute,
        onDevice = { id -> account.mineBackStack += BrowseManagementDevicesRoute(id) },
        Modifier.fillMaxSize(),
    )
}
