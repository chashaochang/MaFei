package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.Account
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.Server
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.migration.MigratedAccount
import cn.mafei.cmp.data.migration.MigrationRecords
import cn.mafei.cmp.player.api.PlayerRuntime
import kotlinx.coroutines.CoroutineScope

/**
 * Playback composition for one restored/active session: the session's
 * provider adapter becomes the single reporting source of a new
 * [SessionPlaybackRuntime]. Logout/account-switch must call
 * [SessionPlaybackRuntime.release] on the instance created here.
 */
fun ApplicationSession.createPlayback(
    runtime: PlayerRuntime,
    scope: CoroutineScope,
    nowMs: () -> Long,
    reportIntervalMs: Long = 10_000,
): SessionPlaybackRuntime = SessionPlaybackRuntime.create(
    provider = selection.provider,
    adapter = binding.adapter,
    runtime = runtime,
    scope = scope,
    nowMs = nowMs,
    reportIntervalMs = reportIntervalMs,
)

/**
 * Turns migrated business records into host-ready [SavedConnection]s.
 *
 * [credentialKeyFor] is supplied by the host (stable 32-hex namespace per
 * migrated account) because the credential-key derivation policy belongs to
 * the session layer. Feiniu connections are skipped with an explicit reason:
 * their saved route set has no wire-format evidence in the migration records,
 * and SavedConnection requires non-empty routes for Feiniu. Skipping is
 * observable and never silently degrades into a half-usable connection.
 */
fun MigrationRecords.toSavedConnections(
    credentialKeyFor: (MigratedAccount) -> String,
): MigratedConnectionMapping {
    val serversById = servers.associateBy { it.id }
    val connections = mutableListOf<SavedConnection>()
    val skipped = mutableListOf<SkippedMigrationConnection>()
    for (migrated in accounts) {
        val server = serversById[migrated.account.serverId]
        if (server == null) {
            skipped += SkippedMigrationConnection(migrated, "unknown server")
            continue
        }
        val connection = runCatching {
            buildSavedConnection(server, migrated, credentialKeyFor(migrated))
        }.getOrNull()
        if (connection == null) {
            skipped += SkippedMigrationConnection(migrated, "connection metadata rejected by validation")
        } else {
            connections += connection
        }
    }
    return MigratedConnectionMapping(connections, skipped)
}

data class MigratedConnectionMapping(
    val connections: List<SavedConnection>,
    val skipped: List<SkippedMigrationConnection>,
)

data class SkippedMigrationConnection(val account: MigratedAccount, val reason: String)

private fun buildSavedConnection(
    server: Server,
    migrated: MigratedAccount,
    credentialKey: String,
): SavedConnection = SavedConnection(
    server = server,
    account = migrated.account,
    remoteUserId = migrated.remoteUserId,
    credentialKey = credentialKey,
    feiniuRoutes = emptyList(), // Feiniu route reconstruction needs fixture evidence (Pending)
)
