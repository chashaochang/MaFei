package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.key
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.connect.ConnectionHistoryItem
import cn.mafei.cmp.session.*
import cn.mafei.cmp.session.AccountAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Request state survives navigation between login and history; disposal cancels its scope. */
internal class SignedOutAccountState(
    private val accounts: AccountAccess,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val isBusy: () -> Boolean,
    private val onBusyChange: (Boolean) -> Unit,
    private val onErrorChange: (String?) -> Unit,
) {
    var historyConnections by mutableStateOf(emptyList<SavedConnection>())
    var rememberedPasswordKeys by mutableStateOf(emptySet<String>())
    var historyLoading by mutableStateOf(false)
    var historyError by mutableStateOf<String?>(null)
    var deletingAccountKey by mutableStateOf<String?>(null)

    fun loadHistory() {
        if (!historyLoading) {
            historyLoading = true
            historyError = null
            scope.launch {
                try {
                    val connections = accounts.savedConnections()
                    val passwordKeys = accounts.savedPasswordKeys(connections)
                    historyConnections = connections
                    rememberedPasswordKeys = passwordKeys
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    historyConnections = emptyList()
                    rememberedPasswordKeys = emptySet()
                    historyError = "历史账号读取失败，请稍后重试"
                } finally {
                    historyLoading = false
                }
            }
        }
    }

    // Deletes restorable credentials, the remembered password and the history row
    // itself. The account in use refuses, which its row already does not offer.
    fun deleteAccount(item: ConnectionHistoryItem) {
        val connection = historyConnections.firstOrNull { it.credentialKey == item.key }
        if (connection == null) {
            historyError = "历史账号已失效，请刷新后重试"
        } else if (deletingAccountKey == null && !isBusy()) {
            deletingAccountKey = item.key
            historyError = null
            scope.launch {
                try {
                    if (accounts.deleteSavedConnection(connection)) {
                        // The mutation already succeeded. Update the local source directly so
                        // deleting the last row does not start a loading state that would remove
                        // the shared dissolve surface before its animation finishes.
                        historyConnections = historyConnections.filterNot { it.credentialKey == item.key }
                        rememberedPasswordKeys = rememberedPasswordKeys - item.key
                    } else {
                        historyError = "该账号正在使用，无法删除"
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    historyError = "账号删除失败，请稍后重试"
                } finally {
                    deletingAccountKey = null
                }
            }
        }
    }

    fun connect(provider: ProviderKind, address: String, name: String, password: String, rememberPassword: Boolean) {
        if (!isBusy()) {
            onBusyChange(true)
            onErrorChange(null)
            scope.launch {
                try {
                    accounts.connect(provider, address, name, password, rememberPassword)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    onErrorChange(connectFailureMessage(failure))
                } finally {
                    onBusyChange(false)
                }
            }
        }
    }

    fun select(item: ConnectionHistoryItem, onLoginRequired: (SavedConnection, String?) -> Unit) {
        val connection = historyConnections.firstOrNull { it.credentialKey == item.key }
        if (connection == null) {
            historyError = "历史账号已失效，请刷新后重试"
        } else if (!isBusy()) {
            onBusyChange(true)
            historyError = null
            scope.launch {
                try {
                    accounts.switchAccount(connection)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Logout deliberately removes restorable secrets,
                    // while the non-secret history entry remains. Fall
                    // back to a prefilled login form for re-authentication.
                    val savedPassword =
                        try {
                            accounts.savedPassword(connection)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    onLoginRequired(connection, savedPassword)
                    historyError = null
                    onErrorChange(null)
                } finally {
                    onBusyChange(false)
                }
            }
        }
    }
}
