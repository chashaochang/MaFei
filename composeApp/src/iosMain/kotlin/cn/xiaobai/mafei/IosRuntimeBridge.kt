package cn.xiaobai.mafei

import cn.xiaobai.mafei.logging.AppLogger
import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.screens.ServerHealthStatus
import cn.xiaobai.mafei.storage.SessionRecord

internal data class IosRuntimeBridgeContext(
    val baseUrl: String,
    val serverId: String,
    val username: String,
    val userId: String?,
    val rememberSession: Boolean,
    val accessToken: String?,
    val savedAtEpochMillis: Long,
) {
    fun toServer(): JellyfinServer {
        return JellyfinServer(
            id = serverId,
            serverName = buildServerName(baseUrl),
            baseUrl = baseUrl,
            isDefault = true,
            health = ServerHealthStatus.HEALTHY,
            errorMessage = null,
        )
    }

    fun toActiveSessionRecord(): SessionRecord? {
        val token = accessToken?.trim().takeUnless { it.isNullOrEmpty() } ?: return null

        // KMP common startup currently keys off `rememberSession` to decide whether it can
        // enter Home directly. We promote the active native container session into a
        // runtime-only KMP session here, while persistence still remains controlled by Swift.
        return SessionRecord(
            serverId = serverId,
            userId = userId,
            username = username,
            rememberSession = true,
            accessToken = token,
            savedAtEpochMillis = savedAtEpochMillis,
        )
    }
}

internal object IosRuntimeBridgeRegistry {
    private var activeContext: IosRuntimeBridgeContext? = null

    fun install(
        baseUrl: String?,
        serverId: String?,
        username: String?,
        userId: String?,
        rememberSession: Boolean,
        accessToken: String?,
        savedAtEpochMillis: Long,
    ) {
        val resolvedBaseUrl = baseUrl?.trim().orEmpty()
        val resolvedServerId = serverId?.trim().orEmpty()
        val resolvedUsername = username?.trim().orEmpty()

        if (resolvedBaseUrl.isBlank() || resolvedServerId.isBlank() || resolvedUsername.isBlank()) {
            AppLogger.warn(
                tag = "IosRuntimeBridge",
                message = "clearing bridge because required fields are missing baseUrl=${resolvedBaseUrl.redactedBaseUrl()} serverId=${resolvedServerId.redactedIdentifier()} usernamePresent=${resolvedUsername.isNotBlank()}",
            )
            clear()
            return
        }

        activeContext = IosRuntimeBridgeContext(
            baseUrl = resolvedBaseUrl,
            serverId = resolvedServerId,
            username = resolvedUsername,
            userId = userId?.trim().takeUnless { it.isNullOrEmpty() },
            rememberSession = rememberSession,
            accessToken = accessToken?.trim().takeUnless { it.isNullOrEmpty() },
            savedAtEpochMillis = savedAtEpochMillis,
        )
        AppLogger.info(
            tag = "IosRuntimeBridge",
            message = "runtime bridge installed baseUrl=${resolvedBaseUrl.redactedBaseUrl()} serverId=${resolvedServerId.redactedIdentifier()} userId=${userId.redactedIdentifier()} rememberSession=$rememberSession tokenAvailable=${!accessToken.isNullOrBlank()}",
        )
    }

    fun clear() {
        if (activeContext != null) {
            AppLogger.info(tag = "IosRuntimeBridge", message = "runtime bridge cleared")
        }
        activeContext = null
    }

    fun snapshot(): IosRuntimeBridgeContext? = activeContext
}

internal fun installIosRuntimeBridge(
    baseUrl: String?,
    serverId: String?,
    username: String?,
    userId: String?,
    rememberSession: Boolean,
    accessToken: String?,
    savedAtEpochMillis: Long,
) {
    IosRuntimeBridgeRegistry.install(
        baseUrl = baseUrl,
        serverId = serverId,
        username = username,
        userId = userId,
        rememberSession = rememberSession,
        accessToken = accessToken,
        savedAtEpochMillis = savedAtEpochMillis,
    )
}

internal fun clearIosRuntimeBridge() {
    IosRuntimeBridgeRegistry.clear()
}

internal fun activeIosRuntimeBridge(): IosRuntimeBridgeContext? = IosRuntimeBridgeRegistry.snapshot()

private fun buildServerName(baseUrl: String): String {
    val host = baseUrl
        .substringAfter("://", baseUrl)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .ifBlank { "Jellyfin" }

    return "Jellyfin @ $host"
}

private fun String?.redactedIdentifier(): String {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return "-"
    if (value.length <= 8) return value
    return value.take(4) + "..." + value.takeLast(2)
}

private fun String.redactedBaseUrl(): String {
    val withoutScheme = substringAfter("://", this)
    return withoutScheme.substringBefore('?').substringBefore('#').ifBlank { this }
}
