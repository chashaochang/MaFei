package cn.mafei.cmp.session

import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.secure_credential_api.CredentialFailureKind
import cn.mafei.cmp.core.storage_api.SettingsStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** One owner for legacy receipts and recovery; account activation uses the existing session pipeline. */
internal class ApplicationLegacyMigration(
    private val settings: SettingsStorage,
    private val secureStore: SecureCredentialStore,
    private val transport: HttpTransport,
    private val legacySnapshotReader: cn.mafei.cmp.data.migration.LegacySnapshotReader?,
    private val sessions: ApplicationSessions,
    private val connectionCatalog: ConnectionCatalog,
    private val persistedChasingSeries: SettingsChasingSeriesStore,
    private val failureReporter: (String, Throwable) -> Unit,
    private val persistenceStageReporter: (String) -> Unit,
    private val isClosed: () -> Boolean,
) {
    suspend fun importRememberedPasswordsSafely(
        saved: List<SavedConnection>,
        failureStage: String,
    ) {
        try {
            importLegacyRememberedPasswords(saved)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Password recovery is optional. A locked or temporarily unavailable
            // credential backend must not hide accounts or block normal startup.
            // Leave the import marker unset so a later attempt can retry it.
            failureReporter(failureStage, failure)
        }
    }

    /**
     * Older ArkUI builds stored remembered Jellyfin passwords under an AssetStore alias that is
     * unrelated to the CMP session namespace. The Harmony host includes those values in its
     * in-memory legacy snapshot; import each missing password once without touching the old asset.
     */
    private suspend fun importLegacyRememberedPasswords(saved: List<SavedConnection>) {
        val reader = legacySnapshotReader ?: return
        if (settings.getString(LEGACY_PASSWORD_IMPORT_KEY) == LEGACY_PASSWORD_IMPORT_DONE) return
        val captured = reader.read() as? cn.mafei.cmp.data.migration.LegacyReadResult.Captured ?: return
        val snapshot = captured.snapshot
        val servers = snapshot.servers.associateBy { it.key }
        for (account in snapshot.accounts) {
            val remembered = account.password ?: continue
            val oldServer = servers[account.serverKey] ?: continue
            val connection = saved.firstOrNull { candidate ->
                candidate.server.provider == oldServer.provider &&
                    candidate.remoteUserId == account.remoteUserId &&
                    candidate.server.baseUrl.trimEnd('/') == oldServer.baseUrl.trimEnd('/')
            } ?: continue
            // A later explicit "do not remember" login is authoritative. Do not let a
            // still-present ArkUI AssetStore record resurrect the password.
            if (!connection.rememberLogin) continue
            if (secureStore.read(connection.passwordAlias) == null) {
                try {
                    secureStore.save(connection.passwordAlias, remembered, overwrite = false)
                } catch (failure: CredentialAccessException) {
                    if (failure.kind != CredentialFailureKind.AlreadyExists) throw failure
                }
            }
        }
        settings.putString(LEGACY_PASSWORD_IMPORT_KEY, LEGACY_PASSWORD_IMPORT_DONE)
    }

    /** Imports once through the ordinary session admission pipeline. */
    suspend fun migrateLegacyData(): cn.mafei.cmp.data.migration.MigrationResult? =
        migrateLegacyData(allowConsumedRecovery = false)

    suspend fun migrateLegacyData(
        allowConsumedRecovery: Boolean,
    ): cn.mafei.cmp.data.migration.MigrationResult? {
        val reader = legacySnapshotReader ?: return null
        check(!isClosed()) { "Application is closed" }
        if (isLegacyMigrationConsumed() && !allowConsumedRecovery) return null
        val journal = cn.mafei.cmp.core.storage_api.VersionedSettingsStorage(settings)
        val sink = cn.mafei.cmp.data.migration.SettingsBackedMigrationSink(settings)
        val engine = cn.mafei.cmp.data.migration.LegacyMigrationEngine(
            reader = reader,
            secure = secureStore,
            journal = journal,
            sink = sink,
            nowEpochMs = { SystemEpochClock.nowEpochMs() },
            reportStage = { persistenceStageReporter("migration-$it") },
        )
        val result = try {
            engine.migrate()
        } catch (failure: Throwable) {
            failureReporter("migration", failure)
            throw failure
        }
        if (result == cn.mafei.cmp.data.migration.MigrationResult.NotNeeded) {
            settings.putString(LEGACY_MIGRATION_EMPTY_KEY, LEGACY_MIGRATION_EMPTY)
            markLegacyMigrationConsumed()
            return result
        }
        if (result is cn.mafei.cmp.data.migration.MigrationResult.Completed) {
            persistenceStageReporter("migration-records-read")
            val records = sink.storedRecords(result.migrationId)
                ?: return result
            persistenceStageReporter("migration-chasing-save")
            persistedChasingSeries.importMigrated(records.chasing)
            // Persist every mappable connection, then admit the migrated
            // current account through the SAME activate pipeline as a fresh
            // login: credentials are read from the secure store by the
            // SavedConnection aliases (which the planner shaped to match).
            val mapping = records.toSavedConnections()
            for (connection in mapping.connections) {
                persistenceStageReporter("migration-catalog-save")
                connectionCatalog.save(connection)
            }
            // A first activation may have failed after the migration receipt
            // was committed. Re-read the receipt on later starts and retry the
            // same activation until the normal session index is durable.
            if (sessions.state.value.session == null) {
                val connection = records.selectedAccountId?.let { selected ->
                    mapping.connections.firstOrNull { it.account.id == selected }
                } ?: mapping.connections.maxWithOrNull(
                    compareBy<SavedConnection> { it.account.lastActiveEpochMs ?: Long.MIN_VALUE }
                        .thenBy { it.account.id.value },
                )
                connection?.let {
                    persistenceStageReporter("migration-credential-restore")
                    val secrets = it.aliases.mapNotNull { alias ->
                        secureStore.read(alias)?.let { alias to it }
                    }.toMap()
                    if (secrets.isEmpty()) return result
                    persistenceStageReporter("migration-session-activate")
                    sessions.activate(it, cn.mafei.cmp.session.DefaultProviderSessionFactory(transport).restore(it, secrets))
                }
            }
            markLegacyMigrationConsumed()
            settings.putString(LEGACY_PASSWORD_IMPORT_KEY, LEGACY_PASSWORD_IMPORT_DONE)
        }
        return result
    }

    suspend fun isLegacyMigrationConsumed(): Boolean =
        settings.getString(LEGACY_MIGRATION_CONSUMED_KEY) == LEGACY_MIGRATION_CONSUMED

    suspend fun markLegacyMigrationConsumed() {
        if (!isLegacyMigrationConsumed()) {
            settings.putString(LEGACY_MIGRATION_CONSUMED_KEY, LEGACY_MIGRATION_CONSUMED)
        }
    }

    suspend fun isLegacyMigrationConfirmedEmpty(): Boolean =
        settings.getString(LEGACY_MIGRATION_EMPTY_KEY) == LEGACY_MIGRATION_EMPTY

    suspend fun isLegacyAccountRecoverySuppressed(): Boolean =
        settings.getString(LEGACY_RECOVERY_SUPPRESSED_KEY) == LEGACY_RECOVERY_SUPPRESSED

    suspend fun suppressLegacyAccountRecovery() {
        settings.putString(LEGACY_RECOVERY_SUPPRESSED_KEY, LEGACY_RECOVERY_SUPPRESSED)
    }

    private companion object {
        const val LEGACY_MIGRATION_CONSUMED_KEY = "mafei.legacy-migration.v1.consumed"
        const val LEGACY_MIGRATION_CONSUMED = "done"
        const val LEGACY_MIGRATION_EMPTY_KEY = "mafei.legacy-migration.v1.empty"
        const val LEGACY_MIGRATION_EMPTY = "confirmed"
        const val LEGACY_RECOVERY_SUPPRESSED_KEY = "mafei.legacy-migration.v1.recovery-suppressed"
        const val LEGACY_RECOVERY_SUPPRESSED = "logout"
        // v1 was written before the ArkUI password bridge was reliable. v2 could then be
        // committed while the HAP used a different signing certificate, making AssetStore
        // appear empty. Use a fresh receipt after restoring the historical signing identity.
        const val LEGACY_PASSWORD_IMPORT_KEY = "mafei.legacy-password.v3.imported"
        const val LEGACY_PASSWORD_IMPORT_DONE = "done"
    }
}
