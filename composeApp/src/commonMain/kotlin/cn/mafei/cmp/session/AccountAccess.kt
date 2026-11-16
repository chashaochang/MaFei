package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.data.repositories.session.SessionSelection
import cn.mafei.cmp.core.domain.ClientConfigError

/** Narrow account boundary shared by login/history and signed-in account routes. */
internal interface AccountAccess {
    val activeSelection: SessionSelection?

    suspend fun savedConnections(): List<SavedConnection>

    suspend fun savedPasswordKeys(connections: List<SavedConnection>): Set<String>

    suspend fun savedPassword(connection: SavedConnection): String?

    suspend fun switchAccount(connection: SavedConnection)

    suspend fun deleteSavedConnection(connection: SavedConnection): Boolean

    suspend fun connect(provider: ProviderKind, baseUrl: String, username: String, password: String, rememberPassword: Boolean)

    suspend fun beginPlexLogin(): PlexLoginChallenge = throw ClientConfigError("Plex authorization unavailable")
    suspend fun awaitPlexServers(challenge: PlexLoginChallenge): PlexLoginServers = throw ClientConfigError("Plex authorization unavailable")
    suspend fun connectPlex(servers: PlexLoginServers, serverId: String) {
        throw ClientConfigError("Plex authorization unavailable")
    }
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
        override suspend fun beginPlexLogin() = this@accountAccess.beginPlexLogin()
        override suspend fun awaitPlexServers(challenge: PlexLoginChallenge) = this@accountAccess.awaitPlexServers(challenge)
        override suspend fun connectPlex(servers: PlexLoginServers, serverId: String) {
            this@accountAccess.connectPlex(servers, serverId)
        }
    }
