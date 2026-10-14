package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.feature.settings.AddAccountScreen
import cn.mafei.cmp.session.*

@Composable
internal fun AddAccountEntryBinding(
    account: AccountFlowState,
    popMineRoute: () -> Unit,
) {
    AddAccountScreen(
        onBack = popMineRoute,
        onProvider = { provider ->
            account.accountConnectError = null
            account.mineBackStack += BrowseConnectAccountRoute(provider)
        },
        modifier = Modifier.fillMaxSize(),
    )
}
