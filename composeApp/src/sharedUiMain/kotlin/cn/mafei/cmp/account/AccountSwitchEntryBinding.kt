package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import cn.mafei.cmp.feature.settings.AccountSwitchItem
import cn.mafei.cmp.feature.settings.AccountSwitchScreen
import cn.mafei.cmp.session.*
import kotlinx.coroutines.flow.map

@Composable
internal fun AccountSwitchEntryBinding(account: AccountFlowState, session: ApplicationSession, popMineRoute: () -> Unit) {
    val accountItems =
        account.accountConnections.map { connection ->
            AccountSwitchItem(
                key = connection.credentialKey,
                provider = connection.server.provider,
                serverId = connection.server.id,
                serverName = connection.server.name,
                displayName = connection.account.displayName,
                address = connection.server.baseUrl,
                lastUsedEpochMs = connection.account.lastActiveEpochMs,
                isCurrent = connection.selection == session.selection,
                hasRememberedPassword = connection.credentialKey in account.accountPasswordKeys,
            )
        }
    AccountSwitchScreen(
        items = accountItems,
        loading = account.accountsLoading,
        busyKey = account.switchingAccountKey,
        deletingKey = account.deletingAccountKey,
        error = account.accountsError,
        onBack = popMineRoute,
        onRetry = account::loadAccounts,
        onAccount = { item ->
            account.switchAccount(item) { connection ->
                account.mineBackStack +=
                    BrowseConnectAccountRoute(
                        provider = connection.server.provider,
                        presetBaseUrl = connection.server.baseUrl,
                        presetUsername = connection.account.userName,
                        credentialKey = connection.credentialKey,
                    )
            }
        },
        onDelete = account::deleteAccount,
        onAddProvider = { provider ->
            account.accountConnectError = null
            account.mineBackStack += BrowseConnectAccountRoute(provider)
        },
        modifier = Modifier.fillMaxSize(),
    )
}
