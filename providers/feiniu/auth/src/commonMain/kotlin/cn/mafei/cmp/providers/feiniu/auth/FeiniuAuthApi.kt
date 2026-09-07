package cn.mafei.cmp.providers.feiniu.auth

import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall
import cn.mafei.cmp.providers.feiniu.core.FeiniuAuthenticationError
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuCookieJar
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuEnvelope
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuLoginRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuUserInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * Feiniu authentication: username/password login, session restore from
 * stored credentials, user info and logout. There is deliberately NO OAuth —
 * the product never supported it (frozen product boundary).
 */
class FeiniuAuthApi(private val client: FeiniuClient) {

    /**
     * Logs in with username/password on the resolved host. The token from
     * the envelope (top level or data.token) is stored into the session
     * state together with any rotated Set-Cookie values.
     */
    suspend fun login(
        resolution: FeiniuHostResolution,
        username: String,
        password: String,
    ): FeiniuCredentials {
        val body = MafeiJson.Default.encodeToString(
            FeiniuLoginRequest.serializer(),
            FeiniuLoginRequest(
                username = username.trim(),
                password = password,
                appName = "trimemedia-web",
            ),
        )
        return client.execute(
            resolution,
            FeiniuApiCall(
                path = "/v/api/v1/login",
                method = FeiniuApiCall.FeiniuMethod.Post,
                bodyText = body,
                authRequirement = FeiniuApiCall.AuthRequirement.None,
            ),
        ) { envelope, credentials ->
            val token = envelope.token ?: credentials.token
            if (token.isBlank()) {
                throw FeiniuAuthenticationError("Feiniu login response did not contain a token")
            }
            FeiniuCookieJar.mergeToken(credentials.cookie, token).let { FeiniuCredentials(token, it) }.normalized()
        }
    }

    /** Validates and installs stored credentials via the authenticated user/info probe. */
    suspend fun restore(resolution: FeiniuHostResolution, credentials: FeiniuCredentials): FeiniuUserInfo {
        client.sessionState.set(credentials)
        return try {
            userInfo(resolution)
        } catch (e: FeiniuAuthenticationError) {
            client.sessionState.clear()
            throw cn.mafei.cmp.providers.feiniu.core.FeiniuAuthenticationExpiredError("Feiniu session expired", e)
        }
    }

    suspend fun userInfo(resolution: FeiniuHostResolution): FeiniuUserInfo =
        client.execute(
            resolution,
            FeiniuApiCall(
                path = "/v/api/v1/user/info",
                method = FeiniuApiCall.FeiniuMethod.Get,
                authRequirement = FeiniuApiCall.AuthRequirement.Token,
            ),
        ) { envelope, _ ->
            decodeData(envelope, FeiniuUserInfo.serializer())
                ?: throw FeiniuAuthenticationError("Feiniu user info is empty")
        }

    /** Logs out server-side (best effort) and always clears the local session. */
    suspend fun logout(resolution: FeiniuHostResolution) {
        try {
            client.execute(
                resolution,
                FeiniuApiCall(
                    path = "/v/api/v1/user/logout",
                    method = FeiniuApiCall.FeiniuMethod.Post,
                    authRequirement = FeiniuApiCall.AuthRequirement.Token,
                ),
            ) { _, _ -> true }
        } finally {
            client.sessionState.clear()
        }
    }

    private fun decodeData(envelope: FeiniuEnvelope, serializer: KSerializer<FeiniuUserInfo>): FeiniuUserInfo? {
        val data = envelope.data ?: return null
        return try {
            MafeiJson.Default.decodeFromJsonElement(serializer, data)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}


