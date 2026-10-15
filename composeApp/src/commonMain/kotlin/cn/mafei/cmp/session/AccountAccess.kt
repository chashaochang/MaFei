package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.data.repositories.session.SessionSelection

/** Narrow account boundary shared by login/history and signed-in account routes. */
internal interface AccountAccess {
    val activeSelection: SessionSelection?

    suspend fun savedConnections(): List<SavedConnection>

    suspend fun savedPasswordKeys(connections: List<SavedConnection>): Set<String>

    suspend fun savedPassword(connection: SavedConnection): String?

    suspend fun switchAccount(connection: SavedConnection)

    suspend fun deleteSavedConnection(connection: SavedConnection): Boolean

    suspend fun connect(provider: ProviderKind, baseUrl: String, username: String, password: String, rememberPassword: Boolean)
}

internal fun ApplicationRuntime.accountAccess(): AccountAccess =
    object : AccountAccess {
        override val activeSelection
            get() = sessions.state.value.session?.selection

        override suspend fun savedConnections() = this@accountAccess.savedConnections()

        override suspend fun savedPasswordKeys(connections: List<SavedConnection>) = this@accountAccess.savedPasswordKeys(connections)

        override suspend fun savedPassword(connection: SavedConnection) = this@accountAccess.savedPassword(connection)

        override suspend fun switchAccount(connection: SavedConnection) {
            this@accountAccess.switchAccount(connection)
        }

        override suspend fun deleteSavedConnection(connection: SavedConnection) = this@accountAccess.deleteSavedConnection(connection)

        override suspend fun connect(provider: ProviderKind, baseUrl: String, username: String, password: String, rememberPassword: Boolean) {
            this@accountAccess.connect(provider, baseUrl, username, password, rememberPassword)
        }
    }
