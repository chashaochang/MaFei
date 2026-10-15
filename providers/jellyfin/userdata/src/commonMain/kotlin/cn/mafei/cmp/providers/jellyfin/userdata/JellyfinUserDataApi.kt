package cn.mafei.cmp.providers.jellyfin.userdata

import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinRequests
import cn.mafei.cmp.providers.jellyfin.wire.UserDto

/**
 * User-library write surface: favorite/played toggles, the self user policy
 * probe and administrator-scoped media deletion. All calls require an
 * authenticated session (401 maps to
 * [cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationExpiredError]);
 * authorization failures on [deleteItems] surface as the transport's 401/403
 * taxonomy — the server, not this client, is the authority.
 */
interface JellyfinUserDataApi {

    suspend fun markFavorite(userId: String, itemId: String)

    suspend fun unmarkFavorite(userId: String, itemId: String)

    suspend fun markPlayed(userId: String, itemId: String)

    suspend fun unmarkPlayed(userId: String, itemId: String)

    /** Self user record (GET /Users/{userId}) — used to read the admin policy flag. */
    suspend fun user(userId: String): UserDto

    /** DELETE /Items?ids=... — fails with the mapped authorization error for non-admins. */
    suspend fun deleteItems(itemIds: Collection<String>)
}

class DefaultJellyfinUserDataApi(
    private val client: JellyfinClient,
) : JellyfinUserDataApi {

    override suspend fun markFavorite(userId: String, itemId: String) =
        client.execute(JellyfinRequests.markFavorite(client.config.baseUrl, userId, itemId)) { }

    override suspend fun unmarkFavorite(userId: String, itemId: String) =
        client.execute(JellyfinRequests.unmarkFavorite(client.config.baseUrl, userId, itemId)) { }

    override suspend fun markPlayed(userId: String, itemId: String) =
        client.execute(JellyfinRequests.markPlayed(client.config.baseUrl, userId, itemId)) { }

    override suspend fun unmarkPlayed(userId: String, itemId: String) =
        client.execute(JellyfinRequests.unmarkPlayed(client.config.baseUrl, userId, itemId)) { }

    override suspend fun user(userId: String): UserDto =
        client.executeDecoding(
            JellyfinRequests.user(client.config.baseUrl, userId),
            UserDto.serializer(),
        )

    override suspend fun deleteItems(itemIds: Collection<String>) =
        client.execute(JellyfinRequests.deleteItems(client.config.baseUrl, itemIds)) { }
}
