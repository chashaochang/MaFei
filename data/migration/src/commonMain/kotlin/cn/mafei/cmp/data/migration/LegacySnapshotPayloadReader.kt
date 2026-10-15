package cn.mafei.cmp.data.migration

import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Decodes the normalized, one-shot snapshot produced by a thin platform host.
 *
 * The payload may contain credentials and must never be logged or persisted as
 * ordinary settings. It is decoded in memory and immediately handed to the
 * migration engine, which moves secrets into [SecureCredentialStore].
 */
object LegacySnapshotPayloadReader {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(payload: String): LegacySnapshotReader = LegacySnapshotReader {
        if (payload.isBlank()) {
            LegacyReadResult.Unavailable(ReadFailure.PendingIntegration)
        } else {
            decode(payload)
        }
    }

    internal fun decode(payload: String): LegacyReadResult = try {
        val wire = json.decodeFromString<SnapshotPayload>(payload)
        if (wire.status != CAPTURED_STATUS) {
            LegacyReadResult.Unavailable(wire.reason.toReadFailure())
        } else {
            val sourceStates = LegacySource.entries.associateWith { source ->
                wire.sources[source.name]?.toSourceState()
                    ?: return LegacyReadResult.Unavailable(ReadFailure.InconsistentCapture)
            }
            LegacyReadResult.Captured(
                LegacySnapshot(
                    version = wire.version,
                    installationId = wire.installationId,
                    sources = sourceStates,
                    servers = wire.servers.map { server ->
                        LegacyServer(
                            key = server.key,
                            provider = server.provider.toProvider(),
                            name = server.name,
                            baseUrl = server.baseUrl,
                            version = server.version,
                            originalAddress = server.originalAddress,
                            connectionRoute = server.connectionRoute,
                            fallbackBaseUrls = server.fallbackBaseUrls,
                        )
                    },
                    accounts = wire.accounts.map { account ->
                        LegacyAccount(
                            key = account.key,
                            serverKey = account.serverKey,
                            remoteUserId = account.remoteUserId,
                            userName = account.userName,
                            token = account.token?.takeIf { it.value.isNotBlank() }?.let {
                                SecureSecret.AuthToken(it.scheme, it.value)
                            },
                            cookie = account.cookie?.takeIf(String::isNotBlank)?.let(SecureSecret::CookieText),
                            password = account.password?.let(SecureSecret::Password),
                            lastActiveEpochMs = account.lastActiveEpochMs,
                        )
                    },
                    selectedAccountKey = wire.selectedAccountKey,
                ),
            )
        }
    } catch (_: Exception) {
        LegacyReadResult.Unavailable(ReadFailure.Corrupted)
    }

    private fun String?.toReadFailure(): ReadFailure = when (this) {
        ReadFailure.Locked.name -> ReadFailure.Locked
        ReadFailure.Corrupted.name -> ReadFailure.Corrupted
        ReadFailure.UnsupportedVersion.name -> ReadFailure.UnsupportedVersion
        ReadFailure.InconsistentCapture.name -> ReadFailure.InconsistentCapture
        else -> ReadFailure.PendingIntegration
    }

    private fun String.toSourceState(): LegacySourceState? = when (this) {
        LegacySourceState.Read.name -> LegacySourceState.Read
        LegacySourceState.ConfirmedAbsent.name -> LegacySourceState.ConfirmedAbsent
        LegacySourceState.Pending.name -> LegacySourceState.Pending
        else -> null
    }

    private fun String.toProvider(): ProviderKind = when (this) {
        JELLYFIN_PROVIDER -> ProviderKind.Jellyfin
        FEINIU_PROVIDER -> ProviderKind.Feiniu
        else -> throw IllegalArgumentException("Unsupported legacy provider")
    }

    private const val CAPTURED_STATUS = "captured"
    private const val JELLYFIN_PROVIDER = "jellyfin"
    private const val FEINIU_PROVIDER = "feiniu"
}

@Serializable
private data class SnapshotPayload(
    val status: String,
    val reason: String? = null,
    val version: Int = 1,
    val installationId: String = "",
    val sources: Map<String, String> = emptyMap(),
    val servers: List<ServerPayload> = emptyList(),
    val accounts: List<AccountPayload> = emptyList(),
    val selectedAccountKey: String? = null,
)

@Serializable
private data class ServerPayload(
    val key: String,
    val provider: String,
    val name: String,
    val baseUrl: String,
    val version: String? = null,
    val originalAddress: String? = null,
    val connectionRoute: String? = null,
    val fallbackBaseUrls: List<String> = emptyList(),
)

@Serializable
private data class AccountPayload(
    val key: String,
    val serverKey: String,
    val remoteUserId: String,
    val userName: String,
    val token: TokenPayload? = null,
    val cookie: String? = null,
    val password: String? = null,
    val lastActiveEpochMs: Long? = null,
)

@Serializable
private data class TokenPayload(val scheme: String, val value: String)
