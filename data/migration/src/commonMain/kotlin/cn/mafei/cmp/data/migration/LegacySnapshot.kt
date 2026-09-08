package cn.mafei.cmp.data.migration

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import kotlinx.serialization.Serializable

/** Every source must be inspected. Not yet bridged is not equivalent to absent. */
enum class LegacySource {
    JellyfinDatabase, JellyfinPreferences, FeiniuAccounts, FeiniuCredentials,
    FeiniuLegacySession, AccountSelection, PlaybackPreferences, ChasingSeries, HomeSnapshot,
}
enum class LegacySourceState { Read, ConfirmedAbsent, Pending }

/** A captured, immutable normalized view; NOT serializable because it contains credentials.
 * [installationId] is a persistent, non-secret source-installation identifier, never a token.
 * Readers must capture a consistent snapshot while legacy/session writers are stopped.
 */
class LegacySnapshot(
    val version: Int,
    val installationId: String,
    val sources: Map<LegacySource, LegacySourceState>,
    val servers: List<LegacyServer> = emptyList(),
    val accounts: List<LegacyAccount> = emptyList(),
    val playback: LegacyPlaybackPreferences = LegacyPlaybackPreferences(),
    val chasing: List<LegacyChasing> = emptyList(),
    val home: LegacyHomeSnapshot? = null,
    val selectedAccountKey: String? = null,
) {
    override fun toString(): String = "LegacySnapshot(version=$version, contents=redacted)"
}

data class LegacyServer(
    val key: String, val provider: ProviderKind, val name: String, val baseUrl: String,
    val version: String? = null,
    /** FNOS original address/FNID and fallback routing survive separately from the active URL. */
    val originalAddress: String? = null, val connectionRoute: String? = null,
    val fallbackBaseUrls: List<String> = emptyList(),
)

/** [remoteUserId] must preserve the actual provider ID, not the RDB User row ID. */
class LegacyAccount(
    val key: String, val serverKey: String, val remoteUserId: String, val userName: String,
    val token: SecureSecret.AuthToken? = null, val cookie: SecureSecret.CookieText? = null,
    val lastActiveEpochMs: Long? = null,
) {
    override fun toString(): String = "LegacyAccount(redacted)"
}

@Serializable
data class LegacyPlaybackPreferences(
    /** Legacy enum spelling retained for the player adapter to translate; missing stays missing. */
    val engine: String? = null, val hardwareDecode: Boolean? = null, val cacheSizeMb: Int? = null,
    val preferredSources: List<LegacyPreferredSource> = emptyList(),
)
@Serializable
data class LegacyPreferredSource(val accountKey: String, val mediaId: String, val sourceId: String)
data class LegacyChasing(val accountKey: String, val seriesIds: List<String>)

/** Only non-secret display metadata. Old image/play URLs, headers and filesystem paths must not
 * be imported: they can contain tokens. Consumers re-fetch images using provider media IDs. */
@Serializable
data class MigratedHomeItem(
    val id: String, val name: String, val kind: String? = null,
    val childCount: Int? = null, val libraryKind: String? = null,
    val seriesId: String? = null, val mediaName: String? = null, val percent: Double? = null,
    val subtitle: String? = null, val description: String? = null, val score: Double? = null,
    val tags: List<String> = emptyList(), val time: String? = null,
)
@Serializable
data class LegacyHomeSnapshot(
    val version: Int, val accountKey: String, val savedAtEpochMs: Long,
    val media: List<MigratedHomeItem> = emptyList(),
    val resume: List<MigratedHomeItem> = emptyList(),
    val recommendations: List<MigratedHomeItem> = emptyList(),
    val nextUp: List<MigratedHomeItem> = emptyList(),
    val latestMedia: List<MigratedHomeItem> = emptyList(),
    val latest: Map<String, List<MigratedHomeItem>> = emptyMap(),
    val selectedLatestMediaId: String? = null, val hasLiveTv: Boolean = false,
)

sealed interface LegacyReadResult {
    class Captured(val snapshot: LegacySnapshot) : LegacyReadResult
    /** Safe fixed reason only; do not include raw database fields or backend exceptions. */
    data class Unavailable(val reason: ReadFailure) : LegacyReadResult
}
enum class ReadFailure { PendingIntegration, Locked, Corrupted, UnsupportedVersion, InconsistentCapture }
fun interface LegacySnapshotReader { suspend fun read(): LegacyReadResult }

@Serializable
data class MigratedAccount(
    val account: Account, val remoteUserId: String,
    val tokenAlias: String? = null, val cookieAlias: String? = null,
)
@Serializable
data class MigratedChasing(val accountId: AccountId, val seriesIds: List<String>)
@Serializable
data class MigratedPreferredSource(val accountId: AccountId, val mediaId: String, val sourceId: String)
@Serializable
data class MigratedPlaybackPreferences(
    val engine: String?, val hardwareDecode: Boolean?, val cacheSizeMb: Int?,
    val preferredSources: List<MigratedPreferredSource>,
)
@Serializable
data class MigratedHomeSnapshot(val accountId: AccountId, val snapshot: LegacyHomeSnapshot)
@Serializable
data class MigratedServerConnection(
    val serverId: ServerId, val originalAddress: String?, val connectionRoute: String?,
    val fallbackBaseUrls: List<String>,
)

/** Public, non-sensitive business payload. Never contains [SecureSecret] or raw legacy blobs. */
@Serializable
data class MigrationRecords(
    val servers: List<Server>, val accounts: List<MigratedAccount>,
    val playback: MigratedPlaybackPreferences, val chasing: List<MigratedChasing>,
    val home: MigratedHomeSnapshot?, val selectedAccountId: AccountId?,
    val connections: List<MigratedServerConnection>,
)

class MigrationPlan internal constructor(
    val id: String, val records: MigrationRecords, val warnings: Set<MigrationWarning>,
    internal val secrets: List<Pair<CredentialAlias, SecureSecret>>,
) { override fun toString(): String = "MigrationPlan(secrets=redacted)" }

enum class MigrationWarning { HomeExpired, HomeImagesRequireRefresh }
enum class MigrationProblem {
    UnsupportedVersion, IncompleteSources, InvalidIdentity, DuplicateIdentity, InvalidField,
    UnknownAccount, UnknownServer, CredentialConflict, SourceChanged, CorruptedJournal,
}
class MigrationValidationException(val problem: MigrationProblem) :
    Exception("Legacy migration rejected: ${problem.name}")

sealed interface MigrationResult {
    data class Pending(val reason: ReadFailure) : MigrationResult
    data class Completed(val migrationId: String, val alreadyCompleted: Boolean,
        val warnings: Set<MigrationWarning>) : MigrationResult
}

/** The real implementation MUST hold an exclusive startup/migration lease (also against login,
 * logout and ordinary business writes) for the entire [exclusive] block, shared by all engine
 * instances/processes. [commit] atomically writes the records AND durable receipt in one business
 * transaction, is idempotent for the same ID/payload, and rejects conflicts. It must not overwrite
 * unrelated/newer user data. This is NOT a transaction with the credential store.
 * No platform implementation is supplied in 04F; a sink unable to honor this must fail closed. */
interface MigrationSink {
    suspend fun <T> exclusive(block: suspend () -> T): T
    suspend fun receipt(migrationId: String): String?
    suspend fun commit(migrationId: String, manifest: String, records: MigrationRecords)
}
