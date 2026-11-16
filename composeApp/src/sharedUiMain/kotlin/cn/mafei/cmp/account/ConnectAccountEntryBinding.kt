package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.input.key.key
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.connect.ConnectScreen
import cn.mafei.cmp.feature.connect.PlexConnectActions
import cn.mafei.cmp.common.ui.providerName
import cn.mafei.cmp.feature.connect.rememberConnectFormState
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun ConnectAccountEntryBinding(
    route: BrowseConnectAccountRoute,
    account: AccountFlowState,
    session: ApplicationSession,
    busy: Boolean,
    popMineRoute: () -> Unit,
) {
    val formState = rememberConnectFormState()
    val uriHandler = LocalUriHandler.current
    DisposableEffect(account, route) { onDispose { account.plex.cancel() } }
    var prefilling by remember(route) { mutableStateOf(route.credentialKey != null) }
    LaunchedEffect(formState, route) {
        // Returning from discovery may recreate this entry. Preserve the
        // selected address until the result effect below consumes it.
        if (account.pendingDiscoveredServerAddress == null) {
            formState.provider = route.provider
            formState.baseUrl = route.presetBaseUrl.orEmpty()
            formState.username = route.presetUsername.orEmpty()
            formState.password = ""
            formState.rememberPassword = true
            try {
                val connection =
                    route.credentialKey?.let { key ->
                        session.connection.takeIf { it.credentialKey == key } ?: account.access.savedConnections().firstOrNull { it.credentialKey == key }
                    }
                if (connection != null) {
                    val password = account.access.savedPassword(connection)
                    formState.password = password.orEmpty()
                    formState.rememberPassword = password != null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                account.accountConnectError = "无法读取已保存的密码，请手动输入"
            } finally {
                prefilling = false
            }
        } else {
            prefilling = false
        }
    }
    LaunchedEffect(formState, account.pendingDiscoveredServerAddress) {
        val address = account.pendingDiscoveredServerAddress ?: return@LaunchedEffect
        formState.provider = ProviderKind.Jellyfin
        formState.baseUrl = address
        formState.requestServerAddressFocus()
        account.pendingDiscoveredServerAddress = null
    }
    ConnectScreen(
        busy = account.accountConnectBusy || prefilling,
        onSubmit = { provider, address, name, password, rememberPassword ->
            if (!prefilling)
                account.connect(provider, address, name, password, rememberPassword) {
                    account.minePresented = false
                    account.mineBackStack.clear()
                    account.mineBackStack += BrowseMineRoute
                }
        },
        modifier = Modifier.fillMaxSize(),
        formState = formState,
        routeTitle = "连接 ${providerName(route.provider)}",
        onBack = popMineRoute,
        showProviderSwitcher = false,
        showHistory = false,
        plexState = account.plex.ui,
        plexActions = PlexConnectActions(
            authorize = { account.plex.authorize(uriHandler::openUri) },
            openBrowser = { account.plex.reopen(uriHandler::openUri) },
            connect = { serverId ->
                account.plex.connect(serverId) {
                    account.minePresented = false
                    account.mineBackStack.clear()
                    account.mineBackStack += BrowseMineRoute
                }
            },
            cancel = account.plex::cancel,
        ),
        onOpenServerDiscovery = {
            if (account.mineBackStack.lastOrNull() != BrowseServerDiscoveryRoute) {
                account.mineBackStack += BrowseServerDiscoveryRoute
            }
        },
    )
}
