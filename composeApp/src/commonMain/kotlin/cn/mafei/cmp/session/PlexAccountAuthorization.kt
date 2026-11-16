package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.providers.plex.core.*
import kotlinx.coroutines.CancellationException

/** Only non-secret projections are rendered by account screens. */
data class PlexServerChoice(val id: String, val name: String)

class PlexLoginChallenge internal constructor(internal val challenge: PlexAuthorization) {
    val browserUrl: String get() = challenge.browserUrl
    override fun toString() = "PlexLoginChallenge(redacted)"
}

class PlexLoginServers internal constructor(internal val account: PlexAuthorizedAccount) {
    val choices: List<PlexServerChoice> = account.servers.map { PlexServerChoice(it.id, it.name) }
    override fun toString() = "PlexLoginServers(credentials=redacted)"
}

internal suspend fun <T> plexLoginCall(block: suspend () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: PlexFailure) {
    throw when (failure.kind) {
        PlexFailureKind.Authentication, PlexFailureKind.Authorization ->
            AuthenticationError(AuthFailureKind.InvalidCredentials, "Plex 授权未通过，请重新授权")
        PlexFailureKind.Expired -> AuthenticationError(AuthFailureKind.TokenExpired, "Plex 授权已过期，请重试")
        PlexFailureKind.Network -> NetworkUnreachableError("无法连接 Plex，请检查网络")
        PlexFailureKind.Timeout -> TimeoutError(message = "Plex 连接超时，请重试")
        PlexFailureKind.RateLimit -> RateLimitedError(message = "Plex 请求过于频繁，请稍后重试")
        PlexFailureKind.Server -> ServerError(message = "Plex 服务暂时不可用")
        else -> ProtocolDecodingError("Plex 响应不完整或服务器身份不匹配")
    }
}
