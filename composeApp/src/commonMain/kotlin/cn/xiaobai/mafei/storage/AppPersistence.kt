package cn.xiaobai.mafei.storage

import cn.xiaobai.mafei.screens.JellyfinServer
import kotlinx.serialization.Serializable

@Serializable
data class SessionRecord(
    val serverId: String,
    val userId: String? = null,
    val username: String,
    val rememberSession: Boolean,
    val accessToken: String? = null,
    val savedAtEpochMillis: Long,
)

interface AppPersistence {
    fun loadServers(): List<JellyfinServer>
    fun saveServers(servers: List<JellyfinServer>)
    fun loadSession(): SessionRecord?
    fun saveSession(session: SessionRecord)
    fun clearSession()
}

expect fun createAppPersistence(): AppPersistence
