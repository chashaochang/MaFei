package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.key
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.settings.AccountSwitchItem
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Account surface state and requests survive its child routes, and end with the signed-in session. */
internal class AccountFlowState(val access: AccountAccess, private val scope: kotlinx.coroutines.CoroutineScope) {
    var accountConnections by mutableStateOf(emptyList<SavedConnection>())
    var accountPasswordKeys by mutableStateOf(emptySet<String>())
    var accountsLoading by mutableStateOf(false)
    var accountsError by mutableStateOf<String?>(null)
    var switchingAccountKey by mutableStateOf<String?>(null)
    var deletingAccountKey by mutableStateOf<String?>(null)
    var accountConnectBusy by mutableStateOf(false)
    var accountConnectError by mutableStateOf<String?>(null)
    var pendingDiscoveredServerAddress by mutableStateOf<String?>(null)
    var minePresented by mutableStateOf(false)
    var returnToEmbeddedWeb by mutableStateOf(false)
    var authenticationNoticeDismissed by mutableStateOf(false)
    val mineBackStack = mutableStateListOf<BrowseRoute>(BrowseMineRoute)

    fun loadAccounts() {
        if (!accountsLoading) {
            accountsLoading = true
            accountsError = null
            scope.launch {
                try {
                    val connections = access.savedConnections()
                    val passwordKeys = access.savedPasswordKeys(connections)
                    accountConnections = connections
                    accountPasswordKeys = passwordKeys
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    accountConnections = emptyList()
                    accountPasswordKeys = emptySet()
                    accountsError = "加载失败"
                } finally {
                    accountsLoading = false
                }
            }
        }
    }

    fun switchAccount(item: AccountSwitchItem, onLoginRequired: (SavedConnection) -> Unit) {
        val connection = accountConnections.firstOrNull { it.credentialKey == item.key }
        if (connection != null && switchingAccountKey == null) {
            switchingAccountKey = item.key
            accountsError = null
            scope.launch {
                try {
                    access.switchAccount(connection)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (needsLogin: SavedAccountRequiresLoginException) {
                    // 保存的凭据不可恢复（系统清理/卸载重装/当初未落盘）：
                    // 这不是"切换失败"，而是"需要重新登录"。把用户带到
                    // 这台服务器的登录页并预填地址与用户名，登录一次即可。
                    accountsError = null
                    onLoginRequired(connection)
                } catch (_: Exception) {
                    accountsError = "账号切换失败，请稍后重试"
                } finally {
                    switchingAccountKey = null
                }
            }
        }
    }

    fun deleteAccount(item: AccountSwitchItem) {
        val connection = accountConnections.firstOrNull { it.credentialKey == item.key }
        if (connection != null && !item.isCurrent && switchingAccountKey == null && deletingAccountKey == null) {
            deletingAccountKey = item.key
            accountsError = null
            scope.launch {
                try {
                    access.deleteSavedConnection(connection)
                    accountConnections = access.savedConnections()
                    accountPasswordKeys = accountPasswordKeys - connection.credentialKey
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    accountsError = "账号删除失败，请稍后重试"
                } finally {
                    deletingAccountKey = null
                }
            }
        }
    }

    fun connect(provider: ProviderKind, address: String, name: String, password: String, rememberPassword: Boolean, onConnected: () -> Unit) {
        if (!accountConnectBusy) {
            accountConnectBusy = true
            accountConnectError = null
            scope.launch {
                try {
                    access.connect(provider, address, name, password, rememberPassword)
                    onConnected()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    accountConnectError = connectFailureMessage(failure)
                } finally {
                    accountConnectBusy = false
                }
            }
        }
    }
}
