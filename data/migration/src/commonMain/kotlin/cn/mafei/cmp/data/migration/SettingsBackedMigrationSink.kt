package cn.mafei.cmp.data.migration

import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.core.storage_api.StorageEditor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Production [MigrationSink] over [SettingsStorage]: the journal keys the
 * engine already uses share this store, so `commit` lands records AND the
 * receipt in ONE atomic `edit` (receipt written last — a crash before it
 * leaves the engine's idempotent re-run path intact). Same id + different
 * payload is rejected as [MigrationProblem.SourceChanged]; an identical
 * replay is a no-op.
 *
 * [exclusive] holds an in-process lease. Cross-process exclusivity (two app
 * instances racing at first launch) is the host's single-instance guarantee —
 * documented Pending for platform verification.
 */
class SettingsBackedMigrationSink(
    private val settings: SettingsStorage,
    keysPrefix: String = "mafei.migration",
) : MigrationSink {

    private val recordsKey = "$keysPrefix.records"
    private val receiptKey = "$keysPrefix.receipt"
    private val leaseKey = "$keysPrefix.lease"
    private val mutex = Mutex()
    private var held = false

    override suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock {
        // Re-entrancy guard for the common double-start mistake; the process
        // level single-instance guarantee stays with the host.
        check(!held) { "Migration is already running" }
        held = true
        try {
            settings.edit { putString(leaseKey, LEASE_HELD) }
            block()
        } finally {
            held = false
            settings.edit { putString(leaseKey, LEASE_FREE) }
        }
    }

    override suspend fun receipt(migrationId: String): String? =
        settings.getString(receiptKey(migrationId))

    override suspend fun commit(migrationId: String, manifest: String, records: MigrationRecords) {
        val existing = settings.getString(receiptKey(migrationId))
        if (existing != null) {
            if (existing != manifest) throw MigrationValidationException(MigrationProblem.SourceChanged)
            return // identical replay: nothing to write
        }
        val encoded = Json.Default.encodeToString(MigrationRecords.serializer(), records)
        settings.edit {
            putString(recordsKey(migrationId), encoded)
            putString(receiptKey(migrationId), manifest) // receipt LAST on purpose
        }
    }

    /** Stored business records for a completed migration, or null. */
    suspend fun storedRecords(migrationId: String): MigrationRecords? =
        settings.getString(recordsKey(migrationId))?.let {
            Json.Default.decodeFromString(MigrationRecords.serializer(), it)
        }

    private fun receiptKey(migrationId: String) = "$receiptKey.$migrationId"
    private fun recordsKey(migrationId: String) = "$recordsKey.$migrationId"

    private companion object {
        const val LEASE_HELD = "held"
        const val LEASE_FREE = "free"
    }
}

/** Minimal in-memory [SettingsStorage] for tests and host bring-up before the real backend lands. */
class InMemorySettingsStorage : SettingsStorage {
    private val values = linkedMapOf<String, String>()

    override suspend fun getString(key: String): String? = values[key]
    override suspend fun putString(key: String, value: String) { values[key] = value }
    override suspend fun getBytes(key: String): ByteArray? = getString(key)?.encodeToByteArray()
    override suspend fun putBytes(key: String, value: ByteArray) { putString(key, value.decodeToString()) }
    override suspend fun remove(key: String): Boolean = values.remove(key) != null
    override suspend fun contains(key: String): Boolean = values.containsKey(key)
    override suspend fun listKeys(prefix: String?): List<String> =
        values.keys.filter { prefix == null || it.startsWith(prefix) }.sorted()

    override suspend fun edit(mutate: StorageEditor.() -> Unit) {
        val staged = linkedMapOf<String, String?>()
        val editor = object : StorageEditor {
            override fun putString(key: String, value: String) { staged[key] = value }
            override fun putBytes(key: String, value: ByteArray) { staged[key] = value.decodeToString() }
            override fun remove(key: String) { staged[key] = null }
        }
        editor.mutate()
        for ((key, value) in staged) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }
}
