package cn.mafei.cmp.data.migration

import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.secure_credential_api.CredentialFailureKind
import cn.mafei.cmp.core.storage_api.VersionedStorage
import kotlinx.serialization.json.Json

class LegacyMigrationEngine(
    private val reader: LegacySnapshotReader,
    private val secure: SecureCredentialStore,
    private val journal: VersionedStorage,
    private val sink: MigrationSink,
    private val nowEpochMs: () -> Long,
    private val reportStage: (String) -> Unit = {},
) {
    /** Ordering: validate -> journal plan -> immutable secrets -> business+receipt -> marker.
     * A crash before receipt repeats create-if-absent credential writes. A crash after receipt
     * never writes credentials again. Old source files are never deleted or modified.
     * Journal/receipt contain the exact non-secret manifest, not a collision-prone hash.
     * Backend exceptions/cancellation propagate, so failures are never reported as empty success.
     */
    suspend fun migrate(): MigrationResult = sink.exclusive {
        reportStage("reader")
        val captured = reader.read()
        if (captured is LegacyReadResult.Unavailable) return@exclusive MigrationResult.Pending(captured.reason)
        val snapshot = (captured as LegacyReadResult.Captured).snapshot
        // Freeze clock-derived home expiry at first attempt so a retry crossing seven days does
        // not change the migration manifest. This metadata itself contains no source secrets.
        val planner = LegacyMigrationPlanner()
        reportStage("plan")
        val initialPlan = planner.plan(snapshot, nowEpochMs())
        if (initialPlan.records.servers.isEmpty() && initialPlan.records.accounts.isEmpty() &&
            initialPlan.records.playback == MigratedPlaybackPreferences(null, null, null, emptyList()) &&
            initialPlan.records.chasing.isEmpty() && initialPlan.records.home == null
        ) {
            reportStage("not-needed")
            return@exclusive MigrationResult.NotNeeded
        }
        val clockKey = "\$mafei.migration.capture-time.${initialPlan.id}"
        reportStage("journal-read")
        val storedClock = journal.getString(clockKey)
        val captureTime = storedClock?.toLongOrNull() ?: if (storedClock == null) nowEpochMs()
            else fail(MigrationProblem.CorruptedJournal)
        val plan = planner.plan(snapshot, captureTime)
        val manifest = Json.encodeToString(MigrationRecords.serializer(), plan.records)
        // Detach nested lists/maps from Reader-owned collections before the first suspension that
        // performs a write. A caller retaining mutable source lists cannot alter committed data.
        val records = Json.decodeFromString(MigrationRecords.serializer(), manifest)
        val planKey = "\$mafei.migration.plan.${plan.id}"
        val recordedPlan = journal.getString(planKey)
        val receipt = sink.receipt(plan.id)
        val done = journal.isMigrationCompleted(plan.id)
        rejectUnless((storedClock == null) == (recordedPlan == null), MigrationProblem.CorruptedJournal)
        rejectUnless(receipt == null || recordedPlan != null, MigrationProblem.CorruptedJournal)
        if (done) {
            rejectUnless(receipt != null && recordedPlan != null, MigrationProblem.CorruptedJournal)
        }
        rejectUnless(recordedPlan == null || recordedPlan == manifest, MigrationProblem.SourceChanged)
        rejectUnless(receipt == null || receipt == manifest, MigrationProblem.SourceChanged)
        if (recordedPlan == null) {
            reportStage("journal-plan")
            journal.edit {
                putString(clockKey, captureTime.toString())
                putString(planKey, manifest)
            }
        }
        if (receipt == null) {
            for ((alias, secret) in plan.secrets) {
                reportStage("credential-read")
                val existing = secure.read(alias)
                if (existing != null) {
                    rejectUnless(existing == secret, MigrationProblem.CredentialConflict)
                } else {
                    try {
                        reportStage("credential-save")
                        secure.save(alias, secret, overwrite = false)
                    } catch (e: CredentialAccessException) {
                        if (e.kind != CredentialFailureKind.AlreadyExists) throw e
                        rejectUnless(secure.read(alias) == secret, MigrationProblem.CredentialConflict)
                    }
                }
            }
            reportStage("records-commit")
            sink.commit(plan.id, manifest, records)
        }
        if (!done) {
            reportStage("journal-complete")
            journal.markMigrationCompleted(plan.id)
        }
        reportStage("completed")
        MigrationResult.Completed(plan.id, done, plan.warnings)
    }
}
