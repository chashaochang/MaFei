package cn.mafei.cmp.core.storage_api

/**
 * Contract fake: synchronous in-memory [SettingsStorage] honoring the
 * documented [edit] atomicity, used by GLM-01 tests and reusable as a
 * reference for real platform backends.
 */
open class InMemorySettingsStorage : SettingsStorage {

    private val strings = linkedMapOf<String, String>()
    private val bytes = linkedMapOf<String, ByteArray>()

    override suspend fun getString(key: String): String? = strings[key]

    override suspend fun putString(key: String, value: String) {
        strings[key] = value
        bytes.remove(key)
    }

    override suspend fun getBytes(key: String): ByteArray? = bytes[key]?.copyOf()

    override suspend fun putBytes(key: String, value: ByteArray) {
        bytes[key] = value.copyOf()
        strings.remove(key)
    }

    override suspend fun remove(key: String): Boolean =
        (strings.remove(key) != null) || (bytes.remove(key) != null)

    override suspend fun contains(key: String): Boolean =
        strings.containsKey(key) || bytes.containsKey(key)

    override suspend fun listKeys(prefix: String?): List<String> =
        (strings.keys + bytes.keys)
            .filter { prefix == null || it.startsWith(prefix) }
            .sorted()

    override suspend fun edit(mutate: StorageEditor.() -> Unit) {
        val editor = BatchEditor().apply(mutate)
        for (mutation in editor.mutations) {
            when (mutation) {
                is Mutation.PutString -> putString(mutation.key, mutation.value)
                is Mutation.PutBytes -> putBytes(mutation.key, mutation.value)
                is Mutation.Remove -> remove(mutation.key)
            }
        }
    }
}

private class BatchEditor : StorageEditor {
    val mutations = mutableListOf<Mutation>()

    override fun putString(key: String, value: String) {
        mutations += Mutation.PutString(key, value)
    }

    override fun putBytes(key: String, value: ByteArray) {
        mutations += Mutation.PutBytes(key, value.copyOf())
    }

    override fun remove(key: String) {
        mutations += Mutation.Remove(key)
    }
}

private sealed class Mutation {
    data class PutString(val key: String, val value: String) : Mutation()
    data class PutBytes(val key: String, val value: ByteArray) : Mutation()
    data class Remove(val key: String) : Mutation()
}
