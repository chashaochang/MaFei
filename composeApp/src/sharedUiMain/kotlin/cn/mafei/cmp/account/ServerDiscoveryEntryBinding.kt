package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.feature.connect.ServerDiscoveryScreen
import cn.mafei.cmp.session.*

@Composable
internal fun ServerDiscoveryEntryBinding(
    account: AccountFlowState,
    popMineRoute: () -> Unit,
) {
    ServerDiscoveryScreen(
        loadSavedServers = { savedJellyfinServerSuggestions(account.access) },
        discoverServers = { discoveredJellyfinServerSuggestions() },
        onBack = popMineRoute,
        onSelectServer = { address ->
            account.pendingDiscoveredServerAddress = address
            popMineRoute()
        },
        modifier = Modifier.fillMaxSize(),
    )
}
