package cn.xiaobai.mafei.storage

import android.content.SharedPreferences
import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.screens.ServerHealthStatus

private const val PREFS_NAME = "mafei_local_persistence"
private const val KEY_SERVERS = "servers_payload"
private const val KEY_SESSION = "session_payload"
private const val FIELD_SEP = '\u001F'
private const val RECORD_SEP = '\u001E'

private class AndroidAppPersistence(
    private val prefs: SharedPreferences,
) : AppPersistence {

    override fun loadServers(): List<JellyfinServer> {
        val payload = prefs.getString(KEY_SERVERS, null).orEmpty()
        if (payload.isBlank()) return emptyList()
        return payload
            .split(RECORD_SEP)
            .mapNotNull { decodeServer(it) }
    }

    override fun saveServers(servers: List<JellyfinServer>) {
        val payload = servers.joinToString(RECORD_SEP.toString()) { encodeServer(it) }
        prefs.edit().putString(KEY_SERVERS, payload).apply()
    }

    override fun loadSession(): SessionRecord? {
        val payload = prefs.getString(KEY_SESSION, null).orEmpty()
        if (payload.isBlank()) return null
        return decodeSession(payload)
    }

    override fun saveSession(session: SessionRecord) {
        prefs.edit().putString(KEY_SESSION, encodeSession(session)).apply()
    }

    override fun clearSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }
}

private fun encodeServer(server: JellyfinServer): String {
    return listOf(
        server.id,
        server.serverName,
        server.baseUrl,
        server.isDefault.toString(),
        server.health.name,
        server.errorMessage.orEmpty(),
    ).joinToString(FIELD_SEP.toString()) { escape(it) }
}

private fun decodeServer(record: String): JellyfinServer? {
    val parts = record.split(FIELD_SEP).map { unescape(it) }
    if (parts.size < 6) return null
    val health = runCatching { ServerHealthStatus.valueOf(parts[4]) }
        .getOrDefault(ServerHealthStatus.UNKNOWN)
    return JellyfinServer(
        id = parts[0],
        serverName = parts[1],
        baseUrl = parts[2],
        isDefault = parts[3].toBooleanStrictOrNull() ?: false,
        health = health,
        errorMessage = parts[5].ifBlank { null },
    )
}

private fun encodeSession(session: SessionRecord): String {
    return listOf(
        session.serverId,
        session.username,
        session.rememberSession.toString(),
        session.accessToken.orEmpty(),
        session.savedAtEpochMillis.toString(),
        session.userId.orEmpty(),
    ).joinToString(FIELD_SEP.toString()) { escape(it) }
}

private fun decodeSession(record: String): SessionRecord? {
    val parts = record.split(FIELD_SEP).map { unescape(it) }
    if (parts.size < 5) return null
    val rememberSession = parts[2].toBooleanStrictOrNull() ?: return null
    val savedAt = parts[4].toLongOrNull() ?: 0L
    return SessionRecord(
        serverId = parts[0],
        userId = parts.getOrNull(5)?.ifBlank { null },
        username = parts[1],
        rememberSession = rememberSession,
        accessToken = parts[3].ifBlank { null },
        savedAtEpochMillis = savedAt,
    )
}

private fun escape(input: String): String {
    return input
        .replace("\\", "\\\\")
        .replace(FIELD_SEP.toString(), "\\u001F")
        .replace(RECORD_SEP.toString(), "\\u001E")
}

private fun unescape(input: String): String {
    return input
        .replace("\\u001F", FIELD_SEP.toString())
        .replace("\\u001E", RECORD_SEP.toString())
        .replace("\\\\", "\\")
}

actual fun createAppPersistence(): AppPersistence {
    val context = AndroidAppContextHolder.requireContext()
    return AndroidAppPersistence(
        prefs = context.getSharedPreferences(PREFS_NAME, 0),
    )
}
