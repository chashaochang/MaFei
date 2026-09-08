package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.Account
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.Server
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.data.repositories.session.SessionSelection
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Non-secret, stable alias namespace, not a password or cryptographic key. */
fun newCredentialKey(): String = buildString(32) {
    repeat(32) { append("0123456789abcdef"[Random.nextInt(16)]) }
}

@Serializable
data class SavedFeiniuRoute(val baseUrl: String, val route: String) {
    init {
        validateSavedBaseUrl(baseUrl)
        if (route !in setOf("Local", "Public", "Relay", "Direct")) {
            throw ClientConfigError("Unsupported saved Feiniu route")
        }
    }
    override fun toString() = "SavedFeiniuRoute(route=$route, baseUrl=redacted)"
}

/** All fields are non-secret metadata; credentials only exist behind [tokenAlias]/[cookieAlias]. */
@Serializable
data class SavedConnection(
    val server: Server,
    val account: Account,
    val remoteUserId: String,
    val credentialKey: String,
    val feiniuRoutes: List<SavedFeiniuRoute> = emptyList(),
) {
    init {
        if (account.serverId != server.id || account.provider != server.provider) {
            throw ClientConfigError("Saved account does not belong to its server/provider")
        }
        if (remoteUserId.isBlank() || remoteUserId.any { it.code < 32 || it.code == 127 }) {
            throw ClientConfigError("Saved remote user identity is invalid")
        }
        if (credentialKey.length != 32 || credentialKey.any { it !in '0'..'9' && it !in 'a'..'f' }) {
            throw ClientConfigError("Saved credential namespace is invalid")
        }
        validateSavedBaseUrl(server.baseUrl)
        account.avatarImage?.let { validateSavedBaseUrl(it.url) }
        if (server.provider == ProviderKind.Feiniu && feiniuRoutes.isEmpty()) {
            throw ClientConfigError("Saved Feiniu connection requires explicit routes")
        }
        if (server.provider == ProviderKind.Jellyfin && feiniuRoutes.isNotEmpty()) {
            throw ClientConfigError("Jellyfin connection cannot contain Feiniu routes")
        }
        if (feiniuRoutes.size > 16 || feiniuRoutes.distinct().size != feiniuRoutes.size) {
            throw ClientConfigError("Saved Feiniu routes are duplicated or exceed the supported limit")
        }
    }

    val selection: SessionSelection get() = SessionSelection(server.provider, server.id, account.id)
    // ASCII, fixed length and independent of potentially long/user-controlled remote identifiers.
    val tokenAlias: CredentialAlias get() = CredentialAlias("mafei/session/$credentialKey/token")
    val cookieAlias: CredentialAlias get() = CredentialAlias("mafei/session/$credentialKey/cookie")
    val aliases: Set<CredentialAlias> get() = when (server.provider) {
        ProviderKind.Jellyfin -> setOf(tokenAlias)
        ProviderKind.Feiniu -> setOf(tokenAlias, cookieAlias)
    }
    override fun toString() = "SavedConnection(provider=${server.provider}, metadata=redacted)"
}

internal fun validateSavedBaseUrl(raw: String): HttpUrl {
    // parseOrNull avoids an exception containing a rejected credential-bearing URL.
    if (raw != raw.trim() || raw.any { it.code < 32 || it.code == 127 } || '?' in raw || '#' in raw) {
        throw ClientConfigError("Saved base URL must not contain query, fragment or control characters")
    }
    return HttpUrl.parseOrNull(raw) ?: throw ClientConfigError("Invalid saved HTTP base URL")
}
