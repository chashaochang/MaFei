package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.http_contract.HttpUrl

/**
 * Stable local identity for one remote account.
 *
 * The same remote account must always resolve to the same [ServerId] / [AccountId]:
 * `ConnectionCatalog.save` requires one credential namespace per selection
 * ("Saved account credential namespace must remain stable"), and the account
 * history de-duplicates by selection. Deriving the identity from the per-login
 * random credential key — the previous behaviour — made every login look like a
 * brand-new server plus a brand-new account, so the history appended another
 * identical row and the old credentials were never reused.
 */
internal object SessionIdentity {

    /**
     * Provider-reported server identity when available (Jellyfin returns the
     * server id with its login result), otherwise the normalized server address.
     */
    fun serverId(provider: ProviderKind, url: HttpUrl, remoteServerId: String?): ServerId = ServerId(
        provider.name.lowercase() + "-server-" +
            sanitize(remoteServerId?.takeIf { it.isNotBlank() } ?: serverAddress(url)),
    )

    /** Server identity + remote user id, so two accounts on one server stay distinct. */
    fun accountId(serverId: ServerId, remoteUserId: String): AccountId =
        AccountId(sanitize(serverId.value) + "-account-" + sanitize(remoteUserId))

    /**
     * `scheme://host[:port][/path]`, lowercase, without a trailing slash: stable
     * across logins to the same server, and still able to tell two Jellyfin
     * instances apart when they share a host/port behind different paths.
     */
    fun serverAddress(url: HttpUrl): String = buildString {
        append(url.scheme.lowercase()).append("://").append(url.host.lowercase())
        if (url.port > 0) append(':').append(url.port)
        append(normalizedPath(url.encodedPath))
    }

    /** Normalized address of an already saved connection; null when unparseable. */
    fun serverAddress(baseUrl: String): String? = HttpUrl.parseOrNull(baseUrl)?.let(::serverAddress)

    /** "" for the root and for any spelling that only differs by slashes. */
    private fun normalizedPath(path: String): String =
        path.trim('/').let { if (it.isEmpty()) "" else "/$it" }

    /**
     * Ids are compared verbatim and never re-parsed, so this only keeps them
     * readable and free of separators.
     */
    private fun sanitize(value: String): String =
        value.trim().map { if (it.isLetterOrDigit() || it in "-._~") it else '-' }.joinToString("")
}
