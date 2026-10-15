package cn.mafei.cmp.core.storage_api

/**
 * Schema version and migration bookkeeping layered on top of
 * [SettingsStorage].
 *
 * Implementations extend [SettingsStorage] and inherit the default methods,
 * which persist metadata under reserved keys (`$mafei.schema.*`). Migration
 * markers let the data layer run idempotent migrations: a marker is written
 * only after the corresponding migration committed.
 */
interface VersionedStorage : SettingsStorage {

    /** Current schema version; `null` for a fresh (never-migrated) store. */
    suspend fun schemaVersion(): Long? =
        getString(KEY_SCHEMA_VERSION)?.toLongOrNull()

    suspend fun setSchemaVersion(version: Long) {
        putString(KEY_SCHEMA_VERSION, version.toString())
    }

    suspend fun isMigrationCompleted(id: String): Boolean =
        getString(migrationKey(id)) != null

    suspend fun completedMigrations(): Set<String> =
        listKeys(KEY_MIGRATIONS_PREFIX).mapNotNull { it.removePrefix(KEY_MIGRATIONS_PREFIX) }.toSet()

    /** Records a migration as completed; idempotent. */
    suspend fun markMigrationCompleted(id: String) {
        edit {
            putString(migrationKey(id), MIGRATION_DONE)
        }
    }

    /** Clears a migration marker (used by tests and re-migration tooling). */
    suspend fun clearMigration(id: String) {
        remove(migrationKey(id))
    }

    private fun migrationKey(id: String): String = KEY_MIGRATIONS_PREFIX + id

    private companion object {
        const val KEY_SCHEMA_VERSION = "\$mafei.schema.version"
        const val KEY_MIGRATIONS_PREFIX = "\$mafei.schema.migration."
        const val MIGRATION_DONE = "done"
    }
}
