package cn.mafei.cmp.data.migration

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.secure_credential_api.*

/** Pure validation and mapping. No writes occur until the entire snapshot is accepted. */
class LegacyMigrationPlanner {
    fun plan(snapshot: LegacySnapshot, nowEpochMs: Long): MigrationPlan {
        rejectUnless(snapshot.version == 1, MigrationProblem.UnsupportedVersion)
        rejectUnless(LegacySource.entries.all { snapshot.sources[it] in setOf(
            LegacySourceState.Read, LegacySourceState.ConfirmedAbsent) }, MigrationProblem.IncompleteSources)
        identity(snapshot.installationId)
        rejectUnless(nowEpochMs >= 0, MigrationProblem.InvalidField)
        fun requireRead(needed: Boolean, vararg sources: LegacySource) {
            if (needed) rejectUnless(sources.any { snapshot.sources[it] == LegacySourceState.Read },
                MigrationProblem.IncompleteSources)
        }
        requireRead(snapshot.servers.any { it.provider == ProviderKind.Jellyfin },
            LegacySource.JellyfinDatabase, LegacySource.JellyfinPreferences)
        requireRead(snapshot.servers.any { it.provider == ProviderKind.Feiniu },
            LegacySource.FeiniuAccounts, LegacySource.FeiniuLegacySession)
        requireRead(snapshot.chasing.isNotEmpty(), LegacySource.ChasingSeries)
        requireRead(snapshot.home != null, LegacySource.HomeSnapshot)
        requireRead(snapshot.selectedAccountKey != null, LegacySource.AccountSelection, LegacySource.JellyfinPreferences)
        requireRead(snapshot.playback != LegacyPlaybackPreferences(), LegacySource.PlaybackPreferences)
        unique(snapshot.servers.map { it.key }); unique(snapshot.accounts.map { it.key })
        val prefix = "legacy-v1/" + part(snapshot.installationId)
        val sourceServers = snapshot.servers.associateBy { it.key }
        val servers = snapshot.servers.sortedBy { it.key }.map { old ->
            identity(old.key); safeBaseUrl(old.baseUrl)
            old.originalAddress?.let { address ->
                identity(address)
                rejectUnless(address.none { it in "?#@\\" }, MigrationProblem.InvalidField)
            }
            old.fallbackBaseUrls.forEach(::safeBaseUrl)
            old.connectionRoute?.let { field(it) }
            Server(ServerId("$prefix/server/${part(old.key)}"), old.provider, old.name, old.baseUrl, old.version)
        }
        val serverIds = snapshot.servers.associate { old ->
            old.key to ServerId("$prefix/server/${part(old.key)}")
        }
        val accountIds = snapshot.accounts.associate { old ->
            identity(old.key)
            old.key to AccountId("$prefix/account/${part(old.key)}")
        }
        fun accountId(key: String): AccountId = accountIds[key] ?: fail(MigrationProblem.UnknownAccount)
        val selected = snapshot.selectedAccountKey?.let { accountId(it) }
        val secrets = mutableListOf<Pair<CredentialAlias, SecureSecret>>()
        val accounts = snapshot.accounts.sortedBy { it.key }.map { old ->
            val server = sourceServers[old.serverKey] ?: fail(MigrationProblem.UnknownServer)
            identity(old.remoteUserId)
            requireRead(old.token != null || old.cookie != null,
                if (server.provider == ProviderKind.Jellyfin) LegacySource.JellyfinDatabase else LegacySource.FeiniuCredentials,
                if (server.provider == ProviderKind.Jellyfin) LegacySource.JellyfinPreferences else LegacySource.FeiniuLegacySession)
            rejectUnless(old.lastActiveEpochMs == null || old.lastActiveEpochMs >= 0, MigrationProblem.InvalidField)
            val id = accountId(old.key)
            fun alias(kind: String, secret: SecureSecret?): String? = secret?.let {
                // Session-shaped namespace: SavedConnection derives its aliases
                // as "mafei/session/<credentialKey>/<kind>", so the migrated
                // secrets must land exactly there to be readable after restore.
                val alias = CredentialAlias("mafei/session/${legacyCredentialKey(old.key)}/$kind")
                secrets += alias to secret
                alias.value
            }
            old.token?.let { field(it.scheme); field(it.token) }
            old.cookie?.let { field(it.value) }
            MigratedAccount(Account(id, serverIds.getValue(old.serverKey), server.provider,
                old.userName, isCurrent = id == selected, lastActiveEpochMs = old.lastActiveEpochMs),
                old.remoteUserId, alias("token", old.token), alias("cookie", old.cookie),
                legacyCredentialKey(old.key))
        }
        val playback = snapshot.playback
        rejectUnless(playback.engine == null || playback.engine in setOf("auto", "av", "mpv"), MigrationProblem.InvalidField)
        rejectUnless(playback.cacheSizeMb == null || playback.cacheSizeMb > 0, MigrationProblem.InvalidField)
        val preferred = playback.preferredSources.map {
            identity(it.mediaId); identity(it.sourceId)
            MigratedPreferredSource(accountId(it.accountKey), it.mediaId, it.sourceId)
        }.sortedWith(compareBy({ it.accountId.value }, { it.mediaId }))
        unique(preferred.map { part(it.accountId.value) + "/" + part(it.mediaId) })
        unique(snapshot.chasing.map { it.accountKey })
        val chasing = snapshot.chasing.map {
            val ids = it.seriesIds.map { value -> value.trim() }.filter { value -> value.isNotEmpty() }
                .distinct().sorted()
            ids.forEach(::identity)
            MigratedChasing(accountId(it.accountKey), ids)
        }.sortedBy { it.accountId.value }
        val warnings = mutableSetOf<MigrationWarning>()
        val home = snapshot.home?.let { old ->
            val id = accountId(old.accountKey)
            rejectUnless(old.version == 2, MigrationProblem.UnsupportedVersion)
            rejectUnless(old.savedAtEpochMs >= 0, MigrationProblem.InvalidField)
            val all = old.media + old.resume + old.recommendations + old.nextUp + old.latestMedia + old.latest.values.flatten()
            all.forEach {
                identity(it.id)
                rejectUnless(it.childCount == null || it.childCount >= 0, MigrationProblem.InvalidField)
                rejectUnless(it.percent == null || it.percent.isFinite(), MigrationProblem.InvalidField)
                rejectUnless(it.score == null || it.score.isFinite(), MigrationProblem.InvalidField)
            }
            old.latest.keys.forEach(::identity)
            if (old.savedAtEpochMs > nowEpochMs || nowEpochMs - old.savedAtEpochMs > HOME_MAX_AGE_MS) {
                warnings += MigrationWarning.HomeExpired
                null
            } else {
                warnings += MigrationWarning.HomeImagesRequireRefresh
                MigratedHomeSnapshot(id, old.copy(latest = old.latest.entries.sortedBy { it.key }.associate { it.toPair() }))
            }
        }
        return MigrationPlan(prefix, MigrationRecords(servers, accounts,
            MigratedPlaybackPreferences(playback.engine, playback.hardwareDecode, playback.cacheSizeMb, preferred),
            chasing, home, selected, snapshot.servers.sortedBy { it.key }.map {
                MigratedServerConnection(serverIds.getValue(it.key), it.originalAddress, it.connectionRoute,
                    it.fallbackBaseUrls.toList())
            }), warnings, secrets)
    }

    companion object { const val HOME_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 }
}

/** Collision-free fixed-width encoding, not a hash; inputs are explicitly non-secret IDs. */
private fun part(value: String): String = value.map { it.code.toString(16).padStart(4, '0') }.joinToString("")

/**
 * Stable 32-hex credential namespace for one migrated account, derived from
 * the legacy account key. Deterministic across processes so the stored
 * secrets stay readable by SavedConnection on every subsequent start.
 * FNV-1a in two independent 64-bit lanes; this is an ADDRESS, not a secret.
 */
fun legacyCredentialKey(oldKey: String): String {
    // FNV-1a 64 offset basis (two's-complement literal) and a second
    // multiply-xor lane seeded with the golden-ratio constant.
    var laneA = -3750763034362895579L
    var laneB = -7046029288634856825L
    for (element in oldKey.encodeToByteArray()) {
        laneA = (laneA xor element.toLong()) * 0x100000001b3L
        laneB = (laneB + element.toLong()) * -7046029288634856825L
        laneB = laneB xor (laneB ushr 29)
    }
    return toHex16(laneA, laneB) + toHex16(laneA + laneB, laneA * 31 + laneB)
}

private fun toHex16(high: Long, low: Long): String {
    val chars = "0123456789abcdef"
    val out = CharArray(16)
    for (index in 0 until 8) {
        out[index] = chars[((high ushr ((7 - index) * 4)) and 0xfL).toInt()]
        out[8 + index] = chars[((low ushr ((7 - index) * 4)) and 0xfL).toInt()]
    }
    return out.concatToString()
}
private fun identity(value: String) {
    rejectUnless(value.isNotBlank() && value == value.trim() && value.length <= 2048 &&
        value.none { it.code < 32 || it.code == 127 }, MigrationProblem.InvalidIdentity)
}
private fun field(value: String) = rejectUnless(value.isNotBlank(), MigrationProblem.InvalidField)
private fun unique(keys: List<String>) = rejectUnless(keys.distinct().size == keys.size, MigrationProblem.DuplicateIdentity)
private fun safeBaseUrl(value: String) {
    // Conservative plain endpoint gate, not a networking URL parser. The platform Reader must
    // reject or explicitly resolve legacy authenticated URLs before creating this normalized view.
    rejectUnless(value == value.trim() && (value.startsWith("https://") || value.startsWith("http://")) &&
        value.none { it.isWhitespace() || it.code < 32 || it in "?#@\\" }, MigrationProblem.InvalidField)
    val authority = value.substringAfter("://").substringBefore('/')
    rejectUnless(authority.isNotBlank(), MigrationProblem.InvalidField)
}
internal fun rejectUnless(ok: Boolean, problem: MigrationProblem) { if (!ok) fail(problem) }
internal fun fail(problem: MigrationProblem): Nothing = throw MigrationValidationException(problem)
