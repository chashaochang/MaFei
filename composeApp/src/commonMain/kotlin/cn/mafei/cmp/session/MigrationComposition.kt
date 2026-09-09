package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.Account
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.Server
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.migration.MigratedAccount
import cn.mafei.cmp.data.migration.MigratedServerConnection
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
 * The credentialKey comes from the migrated account itself
 * (`MigratedAccount.credentialKey`): the engine stored the secrets under the
 * SESSION-shaped aliases derived from exactly that key, so a restored
 * SavedConnection reads its token/cookie back from the same aliases.
 * Hosts must NOT substitute their own key for migrated accounts.
 *
 * Feiniu routes are rebuilt from the persisted connection record: the legacy
 * route is normalized onto the provider's route set (blank defaults to
 * Local, the provider default) and applied to the primary base URL and every
 * fallback. An unrecognized route name skips the connection with an
 * observable reason instead of shipping a half-usable login.
 */
fun MigrationRecords.toSavedConnections(): MigratedConnectionMapping {
    val serversById = servers.associateBy { it.id }
    val connectionsByServer = connections.associateBy { it.serverId }
    val connections = mutableListOf<SavedConnection>()
    val skipped = mutableListOf<SkippedMigrationConnection>()
    for (migrated in accounts) {
        val server = serversById[migrated.account.serverId]
        if (server == null) {
            skipped += SkippedMigrationConnection(migrated, "unknown server")
            continue
        }
        val key = migrated.credentialKey
        if (key == null) {
            skipped += SkippedMigrationConnection(migrated, "migrated account has no credential namespace")
            continue
        }
        val connection = runCatching {
            buildSavedConnection(server, migrated, connectionsByServer[server.id], key)
        }.getOrElse { failure ->
            skipped += SkippedMigrationConnection(migrated, "connection rejected: ${failure.message}")
            null
        }
        if (connection != null) connections += connection
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
    connectionRecord: MigratedServerConnection?,
    credentialKey: String,
): SavedConnection {
    // Feiniu requires an explicit route set: the legacy record's route is
    // normalized and applied to the primary base URL and every fallback.
    val feiniuRoutes = if (server.provider == ProviderKind.Feiniu) {
        val route = normalizeLegacyRoute(connectionRecord?.connectionRoute)
            ?: throw IllegalArgumentException("unrecognized legacy Feiniu route '${connectionRecord?.connectionRoute}'")
        buildList {
            add(SavedFeiniuRoute(server.baseUrl, route))
            connectionRecord?.fallbackBaseUrls?.forEach { url ->
                add(SavedFeiniuRoute(url, route))
            }
        }
    } else {
        emptyList()
    }
    return SavedConnection(
        server = server,
        account = migrated.account,
        remoteUserId = migrated.remoteUserId,
        credentialKey = credentialKey,
        feiniuRoutes = feiniuRoutes,
    )
}

/** Legacy route strings normalize case-insensitively onto the provider's route set. */
private fun normalizeLegacyRoute(raw: String?): String? {
    val trimmed = raw?.trim()
    if (trimmed.isNullOrEmpty()) return "Local" // provider default when legacy stored no route
    return when (trimmed.lowercase()) {
        "local" -> "Local"
        "public" -> "Public"
        "relay" -> "Relay"
        "direct" -> "Direct"
        else -> null
    }
}
