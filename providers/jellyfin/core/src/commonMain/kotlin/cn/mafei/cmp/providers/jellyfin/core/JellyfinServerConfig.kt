package cn.mafei.cmp.providers.jellyfin.core

import cn.mafei.cmp.core.http_contract.HttpUrl

/**
 * Static client identity and server address. Everything in here is
 * non-secret; the access token lives in [JellyfinSessionState] and its
 * persistent storage is the data layer's business (secure credential store).
 */
data class JellyfinServerConfig(
    val baseUrl: HttpUrl,
    val clientName: String,
    val deviceName: String,
    val deviceId: String,
    val appVersion: String,
)
