package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.providers.feiniu.core.*
import cn.mafei.cmp.providers.feiniu.facade.DefaultFeiniuFacade
import cn.mafei.cmp.providers.jellyfin.core.*
import cn.mafei.cmp.providers.jellyfin.facade.DefaultJellyfinFacade
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/** The caller must activate this fresh binding, or clear it if navigation abandons admission. */
class AuthenticatedConnection(val connection: SavedConnection, val binding: ProviderSessionBinding) {
    override fun toString() = "AuthenticatedConnection(credentials=redacted)"
}

/**
 * Explicit HTTP(S) address + username/password login. No discovery, FNID routing,
 * OAuth or automatic retries. Each call creates a new saved connection identity;
 * reauthentication of an existing saved account is a separate future flow.
 *
 * Passwords are only arguments/request bodies, never object state or settings.
 * Authentication uses an isolated temporary SDK; the admitted binding is rebuilt
 * from its successful authoritative session, not an old restored SDK instance.
 * This object and both SDKs borrow the application transport and never close it.
 */
class ProviderLogin(private val transport: HttpTransport) {
    suspend fun authenticate(
        provider: ProviderKind,
        baseUrl: String,
        username: String,
        password: String,
    ): AuthenticatedConnection {
        currentCoroutineContext().ensureActive()
        if (baseUrl.any { it.code < 32 || it.code == 127 }) {
            throw ClientConfigError("请输入完整的 HTTP 或 HTTPS 服务器地址")
        }
        val url = validateSavedBaseUrl(baseUrl.trim())
        val name = username.trim()
        if (name.isBlank() || name.any { it.code < 32 || it.code == 127 }) {
            throw ClientConfigError("请输入有效的用户名")
        }
        // Empty passwords are supported by Jellyfin. Do not trim/normalize secrets.
        return try {
            when (provider) {
                ProviderKind.Jellyfin -> jellyfin(url, name, password)
                ProviderKind.Feiniu -> feiniu(url, name, password)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // SDK/server/transport exceptions may retain bodies, URLs or passwords
            // in message/cause. Only safe local categories cross the UI boundary.
            throw loginError(failure)
        }
    }

    private suspend fun jellyfin(url: HttpUrl, username: String, password: String): AuthenticatedConnection {
        val key = newCredentialKey()
        val state = InMemoryJellyfinSessionState()
        val facade = DefaultJellyfinFacade(
            JellyfinServerConfig(url, "MaFei", "MaFei CMP", key, "2.0.0"),
            LoginBorrowedTransport(transport), state,
        )
        try {
            val result = facade.auth.authenticateByName(username, password)
            currentCoroutineContext().ensureActive()
            val installed = state.current.value ?: throw ProtocolDecodingError()
            val connection = connection(ProviderKind.Jellyfin, url, key, installed.userId,
                result.userName?.takeIf { it.isNotBlank() } ?: username)
            val binding = DefaultProviderSessionFactory(transport).restore(connection,
                mapOf(connection.tokenAlias to SecureSecret.AuthToken("MediaBrowser", installed.accessToken)))
            return AuthenticatedConnection(connection, binding)
        } finally {
            state.clear() // Local cleanup only: server logout would revoke the new binding's token.
        }
    }

    private suspend fun feiniu(url: HttpUrl, username: String, password: String): AuthenticatedConnection {
        val state = InMemoryFeiniuSessionState()
        val facade = DefaultFeiniuFacade(LoginBorrowedTransport(transport), state)
        val resolution = FeiniuHostResolution(FeiniuHostCandidate(url.toEncodedString(), FeiniuRoute.Direct))
        try {
            facade.auth.login(resolution, username, password)
            // Login only returns credentials. Identity must come from authenticated
            // user/info, which also may rotate cookies before we snapshot credentials.
            val user = facade.auth.userInfo(resolution)
            currentCoroutineContext().ensureActive()
            val remoteId = user.userId?.takeIf { it.isNotBlank() }
                ?: user.username?.takeIf { it.isNotBlank() }
                ?: throw ProtocolDecodingError()
            val connection = connection(ProviderKind.Feiniu, url, newCredentialKey(), remoteId,
                user.username?.takeIf { it.isNotBlank() } ?: username,
                user.nickname?.takeIf { it.isNotBlank() })
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

    private fun connection(provider: ProviderKind, url: HttpUrl, key: String, remoteId: String,
                           username: String, displayName: String? = null): SavedConnection {
        val server = Server(ServerId("server-$key"), provider, url.host, url.toEncodedString())
        val account = Account(AccountId("account-$key"), server.id, provider, username, displayName ?: username)
        // Avatar URLs may contain signed credentials; fetch them at runtime instead.
        return SavedConnection(server, account, remoteId, key,
            if (provider == ProviderKind.Feiniu) listOf(SavedFeiniuRoute(server.baseUrl, "Direct")) else emptyList())
    }
}

private class LoginBorrowedTransport(private val root: HttpTransport) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = root.execute(request)
    override fun close() = Unit
}

private fun loginError(failure: Exception): DomainError = when (failure) {
    is JellyfinAuthenticationError, is JellyfinAuthenticationExpiredError,
    is FeiniuAuthenticationError -> AuthenticationError(AuthFailureKind.InvalidCredentials, "登录未通过，请检查账号和密码")
    is JellyfinAuthorizationError -> AuthorizationError("此账号没有访问权限")
    is JellyfinConnectionError -> if (failure.kind == JellyfinConnectionKind.Timeout) TimeoutError(message = "连接超时，请重试")
        else NetworkUnreachableError("无法连接服务器，请检查地址和网络")
    is FeiniuConnectionError -> if (failure.kind == FeiniuConnectionKind.Timeout) TimeoutError(message = "连接超时，请重试")
        else NetworkUnreachableError("无法连接服务器，请检查地址和网络")
    is JellyfinRateLimitedError -> RateLimitedError(message = "请求过于频繁，请稍后重试")
    is JellyfinServerError -> ServerError(message = "服务器暂时不可用，请稍后重试")
    is JellyfinInvalidAddressError, is FeiniuInvalidAddressError -> ClientConfigError("请输入完整的 HTTP 或 HTTPS 服务器地址")
    is JellyfinDecodeError, is FeiniuDecodeError, is ProtocolDecodingError,
    is ClientConfigError, is IllegalArgumentException, is IllegalStateException -> ProtocolDecodingError("服务器登录响应不完整或不受支持")
    else -> UnknownDomainError("登录失败，请检查服务器地址或稍后重试")
}
