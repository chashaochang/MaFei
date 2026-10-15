package cn.mafei.cmp.session

import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.core.storage_api.StorageCorruptedException
import cn.mafei.cmp.data.repositories.session.SessionSelection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Non-secret connection metadata. The application coordinator owns all writes. */
class ConnectionCatalog(private val settings: SettingsStorage) {
    suspend fun find(selection: SessionSelection): SavedConnection? = all().singleOrNull { it.selection == selection }

    /** Non-secret account history, independent from whether credentials remain restorable. */
    suspend fun all(): List<SavedConnection> = entries()

    suspend fun save(connection: SavedConnection) {
        val snapshot = connection.copy(feiniuRoutes = connection.feiniuRoutes.toList())
        val existing = entries()
        require(existing.none { it.credentialKey == snapshot.credentialKey && it.selection != snapshot.selection }) {
            "Credential namespace belongs to another account"
        }
        require(existing.none { it.selection == snapshot.selection && it.credentialKey != snapshot.credentialKey }) {
            "Saved account credential namespace must remain stable"
        }
        require(existing.size < 256 || existing.any { it.selection == snapshot.selection })
        val encoded = Json.encodeToString(snapshot)
        require(encoded.length <= 262_144) { "Connection metadata exceeds limit" }
        settings.edit { putString(PREFIX + snapshot.credentialKey, encoded) }
    }

    /** Removes exactly the stored non-secret record for [connection]. */
    suspend fun remove(connection: SavedConnection): Boolean {
        val stored = find(connection.selection) ?: return false
        require(stored.credentialKey == connection.credentialKey) {
            "Saved account credential namespace does not match"
        }
        return settings.remove(PREFIX + stored.credentialKey)
    }

    private suspend fun entries(): List<SavedConnection> {
        val keys = settings.listKeys(PREFIX)
        if (keys.size > 256) corrupt()
        val entries = keys.map { key ->
            val raw = settings.getString(key) ?: corrupt()
            try {
                require(raw.length <= 262_144)
                Json.decodeFromString<SavedConnection>(raw).also { require(key == PREFIX + it.credentialKey) }
            } catch (_: Exception) { corrupt() }
        }
        if (entries.map { it.selection }.distinct().size != entries.size) corrupt()
        return entries
    }

    private fun corrupt(): Nothing = throw StorageCorruptedException(null, "Invalid saved connection metadata")
    private companion object { const val PREFIX = "mafei.connection.v1/" }
}
