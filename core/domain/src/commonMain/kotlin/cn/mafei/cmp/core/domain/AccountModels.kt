package cn.mafei.cmp.core.domain

import kotlinx.serialization.Serializable

/**
 * A media server the app knows about. [baseUrl] is stored as entered by the
 * user; connection routing (local/relay/fallback) stays inside providers.
 */
@Serializable
data class Server(
    val id: ServerId,
    val provider: ProviderKind,
    val name: String,
    val baseUrl: String,
    val serverVersion: String? = null,
    val addedEpochMs: Long? = null,
)

/**
 * A user account bound to one [Server]. Secrets (tokens, cookies, passwords)
 * are never stored here; accounts only hold non-sensitive references handled
 * by `core:secure-credential-api`.
 */
@Serializable
data class Account(
    val id: AccountId,
    val serverId: ServerId,
    val provider: ProviderKind,
    val userName: String,
    val displayName: String = userName,
    val isCurrent: Boolean = false,
    val lastActiveEpochMs: Long? = null,
    val avatarImage: MediaImage? = null,
)
