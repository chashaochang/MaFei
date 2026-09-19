package cn.mafei.cmp.storage

import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.core.storage_api.StorageEditor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LegacyCmpSettingsPayload(
    val status: String,
    val version: Int,
    val entries: List<LegacyCmpSettingsEntry> = emptyList(),
)

@Serializable
private data class LegacyCmpSettingsEntry(val key: String, val value: String)

internal fun legacyCmpSettings(payload: String): Map<String, String> = try {
    val decoded = Json.decodeFromString<LegacyCmpSettingsPayload>(payload)
    require(decoded.status == "captured" && decoded.version == 1 && decoded.entries.size <= 4096)
    buildMap {
        decoded.entries.forEach { entry ->
            require(entry.key.isNotEmpty() && entry.key.length <= 512 && entry.value.length <= 1_048_576)
            require(put(entry.key, entry.value) == null)
        }
    }
} catch (_: Exception) {
    emptyMap()
}

/** Imports the former CMP Preferences snapshot before any v2 storage read. */
internal class ImportingSettingsStorage(
    private val primary: SettingsStorage,
    private val legacy: Map<String, String>,
) : SettingsStorage {
    private val mutex = Mutex()
    private var imported = legacy.isEmpty()

    private suspend fun ensureImported() {
        if (imported) return
        mutex.withLock {
            if (imported) return
            val existing = primary.listKeys().toSet()
            val missing = legacy.filterKeys { it !in existing }
            if (missing.isNotEmpty()) {
                primary.edit { missing.forEach { (key, value) -> putString(key, value) } }
            }
            imported = true
        }
    }

    override suspend fun getString(key: String): String? { ensureImported(); return primary.getString(key) }
    override suspend fun putString(key: String, value: String) { ensureImported(); primary.putString(key, value) }
    override suspend fun getBytes(key: String): ByteArray? { ensureImported(); return primary.getBytes(key) }
    override suspend fun putBytes(key: String, value: ByteArray) { ensureImported(); primary.putBytes(key, value) }
    override suspend fun remove(key: String): Boolean { ensureImported(); return primary.remove(key) }
    override suspend fun contains(key: String): Boolean { ensureImported(); return primary.contains(key) }
    override suspend fun listKeys(prefix: String?): List<String> { ensureImported(); return primary.listKeys(prefix) }
    override suspend fun edit(mutate: StorageEditor.() -> Unit) { ensureImported(); primary.edit(mutate) }
}

/**
 * Reads old AssetStore namespaces only when the new private store misses an
 * alias, then copies the recovered secret forward. Persistent tombstones stop
 * a later logout from resurrecting a legacy credential.
 */
internal class CompatibleOhosCredentialStore(
    private val primary: SecureCredentialStore,
    private val legacy: List<SecureCredentialStore>,
    private val settings: SettingsStorage,
) : SecureCredentialStore {
    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) {
        primary.save(alias, secret, overwrite)
        settings.remove(tombstone(alias))
    }

    override suspend fun read(alias: CredentialAlias): SecureSecret? {
        primary.read(alias)?.let { return it }
        if (settings.contains(tombstone(alias))) return null
        legacy.forEach { store ->
            val secret = try {
                store.read(alias)
            } catch (_: CredentialAccessException) {
                null
            } catch (failure: IllegalStateException) {
                if (!failure.isMissingAssetApi()) throw failure
                null
            }
            if (secret != null) {
                primary.save(alias, secret)
                return secret
            }
        }
        return null
    }

    override suspend fun delete(alias: CredentialAlias): Boolean {
        settings.putString(tombstone(alias), "deleted")
        var deleted = primary.delete(alias)
        legacy.forEach { store ->
            deleted = try {
                store.delete(alias) || deleted
            } catch (_: CredentialAccessException) {
                deleted
            } catch (failure: IllegalStateException) {
                if (!failure.isMissingAssetApi()) throw failure
                deleted
            }
        }
        return deleted
    }

    override suspend fun exists(alias: CredentialAlias): Boolean = read(alias) != null

    override suspend fun listAliases(prefix: String?): List<CredentialAlias> {
        val aliases = primary.listAliases(prefix).toMutableSet()
        legacy.forEach { store ->
            try {
                aliases += store.listAliases(prefix)
            } catch (_: CredentialAccessException) {
            } catch (failure: IllegalStateException) {
                if (!failure.isMissingAssetApi()) throw failure
            }
        }
        return aliases.filterNot { settings.contains(tombstone(it)) }.sortedBy { it.value }
    }

    override suspend fun deleteAll(prefix: String?): Int {
        val aliases = listAliases(prefix)
        var deleted = 0
        aliases.forEach { if (delete(it)) deleted++ }
        return deleted
    }

    private fun tombstone(alias: CredentialAlias): String = TOMBSTONE_PREFIX + alias.value

    private companion object {
        const val TOMBSTONE_PREFIX = "mafei.credential-compat.deleted.v1/"
    }
}

private fun IllegalStateException.isMissingAssetApi(): Boolean =
    message?.startsWith("Missing API symbol: OH_Asset_") == true
