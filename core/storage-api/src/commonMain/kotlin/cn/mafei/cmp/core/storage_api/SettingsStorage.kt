package cn.mafei.cmp.core.storage_api

/**
 * Non-sensitive key/value persistence for settings and small snapshots.
 *
 * Contract rules:
 * - all operations are `suspend`; implementations must not block callers;
 * - [edit] applies the collected mutations atomically (all-or-nothing);
 * - no platform storage types (ArkData/SharedPreferences/UserDefaults/...)
 *   may leak through this interface — values are plain strings/bytes;
 * - secrets (tokens, cookies, passwords) must NOT be written here; use
 *   `core:secure-credential-api` and keep only non-sensitive references in
 *   this store.
 */
interface SettingsStorage {

    suspend fun getString(key: String): String?

    suspend fun putString(key: String, value: String)

    suspend fun getBytes(key: String): ByteArray?

    suspend fun putBytes(key: String, value: ByteArray)

    /** Removes the entry; returns `true` when something was deleted. */
    suspend fun remove(key: String): Boolean

    suspend fun contains(key: String): Boolean

    /** Keys starting with [prefix] (all keys when `null`), in stable order. */
    suspend fun listKeys(prefix: String? = null): List<String>

    /**
     * Batch mutation. Mutations recorded by the editor are applied in one
     * atomic step after the block returns; if the block throws, nothing is
     * applied. Later mutations to the same key win.
     */
    suspend fun edit(mutate: StorageEditor.() -> Unit)
}

/** Mutation set collected by [SettingsStorage.edit]. */
interface StorageEditor {
    fun putString(key: String, value: String)
    fun putBytes(key: String, value: ByteArray)
    fun remove(key: String)
}

// ---------------------------------------------------------------------------
// Typed convenience accessors, implemented over the string contract so every
// backend stays exchangeable. Unparseable or missing values fall back to the
// caller-supplied default.
// ---------------------------------------------------------------------------

suspend fun SettingsStorage.putLong(key: String, value: Long) {
    putString(key, value.toString())
}

suspend fun SettingsStorage.getLong(key: String, default: Long = 0L): Long =
    getString(key)?.toLongOrNull() ?: default

suspend fun SettingsStorage.putInt(key: String, value: Int) {
    putString(key, value.toString())
}

suspend fun SettingsStorage.getInt(key: String, default: Int = 0): Int =
    getString(key)?.toIntOrNull() ?: default

suspend fun SettingsStorage.putBoolean(key: String, value: Boolean) {
    putString(key, if (value) "true" else "false")
}

suspend fun SettingsStorage.getBoolean(key: String, default: Boolean = false): Boolean =
    when (getString(key)) {
        "true" -> true
        "false" -> false
        else -> default
    }
