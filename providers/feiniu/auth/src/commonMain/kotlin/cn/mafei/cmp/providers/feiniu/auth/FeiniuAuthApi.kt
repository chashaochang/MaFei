package cn.mafei.cmp.providers.feiniu.auth

import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall
import cn.mafei.cmp.providers.feiniu.core.FeiniuAuthenticationError
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuCookieJar
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuTokenData
import cn.mafei.cmp.providers.feiniu.core.FeiniuEnvelope
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuLoginRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuUserInfo

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
        // R1: the login token must come from THIS response only —
        // `envelope.token` (top level) or `data.token` (legacy nested shape).
        // Falling back to pre-login credentials would resurrect another
        // account's session.
        return client.execute(
            resolution,
            FeiniuApiCall(
                path = "/v/api/v1/login",
                method = FeiniuApiCall.FeiniuMethod.Post,
                bodyText = body,
                authRequirement = FeiniuApiCall.AuthRequirement.None,
            ),
        ) { envelope, credentials ->
            val nestedToken = envelope.data
                ?.let { element ->
                    runCatching {
                        MafeiJson.Default.decodeFromJsonElement(FeiniuTokenData.serializer(), element)
                    }.getOrNull()?.token
                }
            val token = envelope.token ?: nestedToken
            if (token.isNullOrBlank()) {
                throw FeiniuAuthenticationError("Feiniu login response did not contain a token")
            }
            // `credentials` here are already transient-only (anonymous calls
            // drop stored cookies), so only THIS response's cookies merge in.
            FeiniuCookieJar.mergeToken(credentials.cookie, token).let { FeiniuCredentials(token, it) }.normalized()
        }
    }

    /**
     * Validates and installs stored credentials via the authenticated
     * user/info probe. Only an explicit authentication rejection clears the
     * session; a decode failure (malformed payload) must NOT log the user
     * out (R3).
     */
    suspend fun restore(resolution: FeiniuHostResolution, credentials: FeiniuCredentials): FeiniuUserInfo {
        client.sessionState.set(credentials)
        // F1: the expired-session cleanup only applies to the identity this
        // restore started with — a stale 401 arriving after an account switch
        // must not log the new account out.
        val identity = client.sessionState.identityGeneration()
        return try {
            userInfo(resolution)
        } catch (e: cn.mafei.cmp.providers.feiniu.core.FeiniuAuthenticationExpiredError) {
            client.sessionState.clearIfIdentityUnchanged(identity)
            throw e
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
            // R3: decode failures propagate as FeiniuDecodeError from the
            // client wrapper — they must never become authentication errors.
            val data = envelope.data
                ?: throw cn.mafei.cmp.providers.feiniu.core.FeiniuDecodeError("Feiniu user info is empty")
            MafeiJson.Default.decodeFromJsonElement(FeiniuUserInfo.serializer(), data)
        }

    /** Logs out server-side (best effort); the local clear is identity-guarded. */
    suspend fun logout(resolution: FeiniuHostResolution) {
        val identity = client.sessionState.identityGeneration()
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
            // A login/account switch during the logout request must survive.
            client.sessionState.clearIfIdentityUnchanged(identity)
        }
    }

}


