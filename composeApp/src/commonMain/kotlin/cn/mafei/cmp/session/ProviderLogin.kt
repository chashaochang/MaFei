package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.providers.feiniu.connection.FeiniuAddressParser
import cn.mafei.cmp.providers.feiniu.core.*
import cn.mafei.cmp.providers.feiniu.facade.DefaultFeiniuFacade
import cn.mafei.cmp.providers.jellyfin.core.*
import cn.mafei.cmp.providers.jellyfin.discovery.JellyfinAddressCandidates
import cn.mafei.cmp.providers.jellyfin.discovery.VerifiedJellyfinServer
import cn.mafei.cmp.providers.jellyfin.facade.DefaultJellyfinFacade
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/** The caller must activate this fresh binding, or clear it if navigation abandons admission. */
class AuthenticatedConnection(val connection: SavedConnection, val binding: ProviderSessionBinding) {
    override fun toString() = "AuthenticatedConnection(credentials=redacted)"
}

/**
 * Server address + username/password login. Jellyfin LAN addresses may omit the
 * scheme and port; the original HarmonyOS HTTP/HTTPS and 8096/8920 candidates
 * are probed before authentication. Feiniu accepts either the original FNID or
 * a direct address and keeps the complete resolved route list. No OAuth.
 *
 * Logging in to an account that is already saved **reuses that account's local
 * identity and credential namespace** ([savedConnectionFor]) instead of minting
 * another one. A namespace per login made every login append a duplicate row for
 * the same server/account to the account history, orphaned the previous
 * credentials, registered a new Jellyfin device every time, and could never trip
 * [ConnectionCatalog]'s "one stable namespace per selection" guard. Only a
 * genuinely new account receives new ids ([SessionIdentity]).
 *
 * Passwords are only arguments/request bodies, never object state or settings.
 * Authentication uses an isolated temporary SDK; the admitted binding is rebuilt
 * from its successful authoritative session, not an old restored SDK instance.
 * This object and both SDKs borrow the application transport and never close it.
 */
class ProviderLogin(
    private val transport: HttpTransport,
    /**
     * Resolves an already saved account, or null when this is a first login.
     *
     * [remoteUserId] null marks the **pre-authentication** lookup, which can only
     * match on the credentials the user typed ([username]). Jellyfin needs that
     * one: its credential namespace doubles as the DeviceId sent with the login
     * request, and Jellyfin binds the access token to the requesting DeviceId.
     * [remoteServerId] is the provider-reported server identity when discovery or
     * authentication has supplied it, so another address of the same Jellyfin
     * server can still reuse the saved account. A non-null [remoteUserId] is the
     * authoritative lookup by the server's own user id.
     */
    private val savedConnectionFor: suspend (
        provider: ProviderKind,
        address: String,
        originalAddress: String?,
        remoteServerId: String?,
        remoteUserId: String?,
        username: String,
    ) -> SavedConnection? = { _, _, _, _, _, _ -> null },
) {
    suspend fun authenticate(
        provider: ProviderKind,
        baseUrl: String,
        username: String,
        password: String,
        feiniuRoutePreference: FeiniuRoutePreference = FeiniuRoutePreference.Speed,
    ): AuthenticatedConnection {
        currentCoroutineContext().ensureActive()
        if (baseUrl.any { it.code < 32 || it.code == 127 }) {
            throw ClientConfigError("请输入完整的 HTTP 或 HTTPS 服务器地址")
        }
        val rawAddress = baseUrl.trim()
        val name = username.trim()
        if (name.isBlank() || name.any { it.code < 32 || it.code == 127 }) {
            throw ClientConfigError("请输入有效的用户名")
        }
        // Keep user-input validation outside the failure sanitizer. A
        // ClientConfigError raised later by session admission means that a
        // successful server response was structurally unusable, not that the
        // address field itself was invalid.
        val exactUrl = when {
            provider == ProviderKind.Feiniu -> null
            rawAddress.hasHttpScheme() -> validateSavedBaseUrl(rawAddress)
            else -> null
        }
        val jellyfinCandidates = if (provider == ProviderKind.Jellyfin && exactUrl == null) {
            JellyfinAddressCandidates.fromInput(rawAddress).also {
                if (it.isEmpty()) throw ClientConfigError("请输入有效的服务器地址")
            }
        } else {
            emptyList()
        }
        // Empty passwords are supported by Jellyfin. Do not trim/normalize secrets.
        return try {
            when (provider) {
                ProviderKind.Jellyfin -> {
                    // Only the anonymous probe uses this placeholder; the real
                    // namespace (and therefore the DeviceId) is fixed once the
                    // address is resolved and a saved account can be looked up.
                    val verified = if (exactUrl == null) {
                        resolveJellyfinAddress(jellyfinCandidates, newCredentialKey())
                    } else {
                        null
                    }
                    jellyfin(exactUrl ?: requireNotNull(verified).baseUrl, verified, name, password)
                }
                ProviderKind.Feiniu -> feiniu(rawAddress, name, password, feiniuRoutePreference)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // SDK/server/transport exceptions may retain bodies, URLs or passwords
            // in message/cause. Only safe local categories cross the UI boundary.
            throw loginError(failure)
        }
    }

    private suspend fun resolveJellyfinAddress(
        candidates: List<HttpUrl>,
        deviceId: String,
    ): VerifiedJellyfinServer {
        for (candidate in candidates) {
            currentCoroutineContext().ensureActive()
            val facade = DefaultJellyfinFacade(
                JellyfinServerConfig(candidate, "MaFei", "MaFei", deviceId, "2.0.0"),
                LoginBorrowedTransport(transport),
            )
            try {
                val verified = withTimeoutOrNull(JELLYFIN_PROBE_TIMEOUT_MS) {
                    facade.discovery.verifiedServerInfo(candidate)
                }
                if (verified != null) return verified
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A candidate is only a hypothesis. Continue until one returns
                // a valid, supported Jellyfin public-system response.
            }
        }
        throw JellyfinConnectionError(JellyfinConnectionKind.Unknown, "No compatible Jellyfin server candidate responded")
    }

    private suspend fun jellyfin(
        url: HttpUrl,
        verified: VerifiedJellyfinServer?,
        username: String,
        password: String,
    ): AuthenticatedConnection {
        // The DeviceId must be fixed BEFORE AuthenticateByName: Jellyfin binds the
        // issued token to it, so the admitted session has to keep presenting it. A
        // saved account contributes its own stable device, which renews that
        // registration instead of adding one per login.
        val known = savedConnectionFor(
            ProviderKind.Jellyfin,
            SessionIdentity.serverAddress(url),
            null,
            verified?.info?.id,
            null,
            username,
        )
        val presented = PresentedDevice(known?.deviceId ?: newCredentialKey(), borrowed = known != null)
        val state = InMemoryJellyfinSessionState()
        val facade = DefaultJellyfinFacade(
            JellyfinServerConfig(url, "MaFei", "MaFei", presented.id, "2.0.0"),
            LoginBorrowedTransport(transport), state,
        )
        try {
            val result = facade.auth.authenticateByName(username, password)
            currentCoroutineContext().ensureActive()
            val installed = state.current.value ?: throw ProtocolDecodingError()
            val authenticatedSystemInfo = if (verified == null) {
                try {
                    facade.auth.systemInfo()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            // installed.serverId is the server's own identity (exactly what the
            // account is keyed on remotely); it used to be dropped on the floor.
            val connection = connection(
                provider = ProviderKind.Jellyfin,
                url = url,
                presented = presented,
                remoteServerId = installed.serverId ?: verified?.info?.id ?: authenticatedSystemInfo?.id,
                remoteId = installed.userId,
                username = result.userName?.takeIf { it.isNotBlank() } ?: username,
                remoteServerName = verified?.info?.serverName ?: authenticatedSystemInfo?.serverName,
                remoteServerVersion = verified?.info?.version ?: authenticatedSystemInfo?.version,
            )
            val binding = DefaultProviderSessionFactory(transport).restore(connection,
                mapOf(connection.tokenAlias to SecureSecret.AuthToken("MediaBrowser", installed.accessToken)))
            return AuthenticatedConnection(connection, binding)
        } finally {
            state.clear() // Local cleanup only: server logout would revoke the new binding's token.
        }
    }

    private suspend fun feiniu(
        rawAddress: String,
        username: String,
        password: String,
        preference: FeiniuRoutePreference,
    ): AuthenticatedConnection {
        val state = InMemoryFeiniuSessionState()
        val facade = DefaultFeiniuFacade(LoginBorrowedTransport(transport), state)
        val parsed = FeiniuAddressParser.parse(rawAddress)
        val resolution = when (parsed.kind) {
            FeiniuAddressParser.Kind.Direct -> {
                val candidates = FeiniuAddressParser.directBaseUrls(rawAddress).map { candidate ->
                    FeiniuHostCandidate(
                        validateSavedBaseUrl(candidate).toEncodedString(),
                        FeiniuRoute.Direct,
                    )
                }
                FeiniuHostResolution(
                    preferred = candidates.first(),
                    fallbacks = candidates.drop(1),
                )
            }
            FeiniuAddressParser.Kind.FnId -> facade.connection.resolve(rawAddress, preference)
        }
        val url = validateSavedBaseUrl(resolution.preferred.baseUrl)
        try {
            facade.auth.login(resolution, username, password)
            // Login only returns credentials. Identity must come from authenticated
            // user/info, which also may rotate cookies before we snapshot credentials.
            val user = facade.auth.userInfo(resolution)
            currentCoroutineContext().ensureActive()
            val remoteId = user.userId?.takeIf { it.isNotBlank() }
                ?: user.username?.takeIf { it.isNotBlank() }
                ?: throw ProtocolDecodingError()
            val connection = connection(ProviderKind.Feiniu, url, null, null, remoteId,
                user.username?.takeIf { it.isNotBlank() } ?: username,
                user.nickname?.takeIf { it.isNotBlank() },
                feiniuOriginalAddress = rawAddress.trim(),
                feiniuResolution = resolution,
            )
            // This is the authoritative post-userInfo state, never login()'s receipt.
            val credentials = facade.credentials.value.credentials
            val secrets = buildMap {
                put(connection.tokenAlias, SecureSecret.AuthToken("Bearer", credentials.token))
                if (credentials.cookie.isNotEmpty()) put(connection.cookieAlias, SecureSecret.CookieText(credentials.cookie))
            }
            return AuthenticatedConnection(connection, DefaultProviderSessionFactory(transport).restore(connection, secrets))
        } finally {
            state.clear()
        }
    }

    /**
     * Builds the connection for one login attempt.
     *
     * A saved account for the same (provider, server address, remote user) keeps
     * its [ServerId]/[AccountId], its credential namespace and its device
     * registration, so re-login overwrites the existing record and its
     * credentials instead of appending a duplicate. [presented] is the device
     * identity the request already carried; when that turns out to be a different
     * remote account, the new record inherits the device (its token belongs to it)
     * but must take a fresh namespace, because the other account still owns that
     * one. Missing remote identity falls back to [SessionIdentity].
     */
    private suspend fun connection(
        provider: ProviderKind,
        url: HttpUrl,
        presented: PresentedDevice?,
        remoteServerId: String?,
        remoteId: String,
        username: String,
        displayName: String? = null,
        remoteServerName: String? = null,
        remoteServerVersion: String? = null,
        feiniuOriginalAddress: String? = null,
        feiniuResolution: FeiniuHostResolution? = null,
    ): SavedConnection {
        // Prefer the provider's stable server identity when it is available;
        // address remains the compatibility fallback for older saved records.
        val saved = savedConnectionFor(
            provider,
            SessionIdentity.serverAddress(url),
            feiniuOriginalAddress,
            remoteServerId,
            remoteId,
            username,
        )
        val credentialKey = when {
            saved != null -> saved.credentialKey
            presented != null && !presented.borrowed -> presented.id
            else -> newCredentialKey()
        }
        val serverId = saved?.server?.id ?: SessionIdentity.serverId(provider, url, remoteServerId)
        val accountId = saved?.account?.id ?: SessionIdentity.accountId(serverId, remoteId)
        val deviceId = presented?.id ?: saved?.deviceId ?: credentialKey
        val server = Server(
            id = serverId,
            provider = provider,
            name = remoteServerName?.trim()?.takeIf { it.isNotEmpty() }
                ?: saved?.server?.name?.takeIf(String::isNotBlank)
                ?: url.host,
            baseUrl = url.toEncodedString(),
            serverVersion = remoteServerVersion?.trim()?.takeIf { it.isNotEmpty() }
                ?: saved?.server?.serverVersion,
            addedEpochMs = saved?.server?.addedEpochMs,
        )
        val account = Account(accountId, server.id, provider, username, displayName ?: username)
        // Saved routes describe this server's reachability, and the lookup above
        // already matched this exact address, so keep them for a re-saved account.
        val routes = when {
            provider != ProviderKind.Feiniu -> emptyList()
            feiniuResolution != null -> feiniuResolution.orderedCandidates
                .distinctBy { SessionIdentity.serverAddress(it.baseUrl) }
                .take(16)
                .map { SavedFeiniuRoute(validateSavedBaseUrl(it.baseUrl).toEncodedString(), it.route.name) }
            saved != null -> saved.feiniuRoutes
            else -> listOf(SavedFeiniuRoute(server.baseUrl, "Direct"))
        }
        // Avatar URLs may contain signed credentials; fetch them at runtime instead.
        return SavedConnection(
            server = server,
            account = account,
            remoteUserId = remoteId,
            credentialKey = credentialKey,
            deviceId = deviceId,
            feiniuRoutes = routes,
            feiniuOriginalAddress = if (provider == ProviderKind.Feiniu) {
                feiniuOriginalAddress ?: saved?.feiniuOriginalAddress ?: server.baseUrl
            } else {
                null
            },
        )
    }

    /**
     * Device identity a login already presented to the server. [borrowed] marks
     * that a saved account supplied it, whose namespace is therefore still taken:
     * the namespace may only follow once that same account confirms the login.
     */
    private data class PresentedDevice(val id: String, val borrowed: Boolean)
}

private const val JELLYFIN_PROBE_TIMEOUT_MS = 5_000L

private fun String.hasHttpScheme(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private class LoginBorrowedTransport(private val root: HttpTransport) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = root.execute(request)
    override fun close() = Unit
}

private fun loginError(failure: Exception): DomainError = when (failure) {
    is ClientConfigError -> ProtocolDecodingError("服务器登录响应不完整或不受支持")
    is JellyfinAuthenticationError, is JellyfinAuthenticationExpiredError,
    is FeiniuAuthenticationError -> AuthenticationError(AuthFailureKind.InvalidCredentials, "登录未通过，请检查账号和密码")
    is JellyfinAuthorizationError -> AuthorizationError("此账号没有访问权限")
    is JellyfinConnectionError -> if (failure.kind == JellyfinConnectionKind.Timeout) TimeoutError(message = "连接超时，请重试")
        else NetworkUnreachableError("无法连接服务器，请检查地址和网络")
    is FeiniuConnectionError -> if (failure.kind == FeiniuConnectionKind.Timeout) TimeoutError(message = "连接超时，请重试")
        else NetworkUnreachableError("无法连接服务器，请检查地址和网络")
    is JellyfinRateLimitedError -> RateLimitedError(message = "请求过于频繁，请稍后重试")
    is JellyfinServerError -> ServerError(message = "服务器暂时不可用，请稍后重试")
    is JellyfinInvalidAddressError, is FeiniuInvalidAddressError -> ClientConfigError("请输入有效的服务器地址")
    is JellyfinDecodeError, is FeiniuDecodeError, is ProtocolDecodingError,
    is IllegalArgumentException, is IllegalStateException -> ProtocolDecodingError("服务器登录响应不完整或不受支持")
    else -> UnknownDomainError("登录失败，请检查服务器地址或稍后重试")
}
