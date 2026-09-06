package cn.mafei.cmp.providers.jellyfin.auth

import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSession
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinRequests
import kotlinx.serialization.builtins.ListSerializer
import cn.mafei.cmp.providers.jellyfin.wire.AuthenticationResultDto
import cn.mafei.cmp.providers.jellyfin.wire.PublicUserDto
import cn.mafei.cmp.providers.jellyfin.wire.SystemInfoDto

/** Result of a successful username/password login. */
data class JellyfinLoginResult(
    val session: JellyfinSession,
    val userName: String?,
    val userImageTag: String?,
    val raw: AuthenticationResultDto,
)

/**
 * Stable authentication surface: public users, login, logout, and restoring
 * a previously stored session. The access token lives only inside
 * [JellyfinSessionState]; persisting it is the data layer's job (secure
 * credential store, GLM-04/06).
 */
interface JellyfinAuthApi {

    suspend fun publicUsers(): List<PublicUserDto>

    /**
     * Authenticates with username/password, stores the resulting session in
     * the client's session state, and returns it. Fails with
     * [cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationError] on
     * wrong credentials.
     */
    suspend fun authenticateByName(username: String, password: String): JellyfinLoginResult

    /**
     * Restores a stored session and validates it against GET /System/Info.
     * An expired token results in
     * [cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationExpiredError]
     * and the session state is cleared before the error propagates.
     */
    suspend fun restoreSession(userId: String, accessToken: String, serverId: String? = null): SystemInfoDto

    /** Fetches the authenticated system info (session validation probe). */
    suspend fun systemInfo(): SystemInfoDto

    /** Logs out (best effort server-side) and clears the local session. */
    suspend fun logout()
}

class DefaultJellyfinAuthApi(
    private val client: JellyfinClient,
    private val sessionState: JellyfinSessionState = client.sessionState,
) : JellyfinAuthApi {

    override suspend fun publicUsers(): List<PublicUserDto> =
        client.executeDecoding(JellyfinRequests.publicUsers(client.config.baseUrl), ListSerializer(PublicUserDto.serializer()))

    override suspend fun authenticateByName(username: String, password: String): JellyfinLoginResult {
        val result = client.executeDecoding(
            JellyfinRequests.authenticateByName(client.config.baseUrl, username, password),
            AuthenticationResultDto.serializer(),
        )
        val user = requireNotNull(result.user) { "AuthenticationResult without user" }
        val token = requireNotNull(result.accessToken) { "AuthenticationResult without accessToken" }
        val session = JellyfinSession(userId = user.id, accessToken = token, serverId = result.serverId)
        sessionState.set(session)
        return JellyfinLoginResult(
            session = session,
            userName = user.name,
            userImageTag = user.primaryImageTag,
            raw = result,
        )
    }

    override suspend fun restoreSession(userId: String, accessToken: String, serverId: String?): SystemInfoDto {
        sessionState.set(JellyfinSession(userId = userId, accessToken = accessToken, serverId = serverId))
        return try {
            systemInfo()
        } catch (e: cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationExpiredError) {
            sessionState.clear()
            throw e
        }
    }

    override suspend fun systemInfo(): SystemInfoDto =
        client.executeDecoding(JellyfinRequests.systemInfo(client.config.baseUrl), SystemInfoDto.serializer())

    override suspend fun logout() {
        try {
            client.execute(JellyfinRequests.logout(client.config.baseUrl)) { /* 204 No Content */ }
        } finally {
            sessionState.clear()
        }
    }
}
