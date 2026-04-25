package cn.xiaobai.mafei

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import cn.xiaobai.mafei.data.IosBridgeAwareDetailRepository
import cn.xiaobai.mafei.data.IosBridgeAwareFavoritesRepository
import cn.xiaobai.mafei.data.IosBridgeAwareHomeRepository
import cn.xiaobai.mafei.data.IosBridgeAwareMediaViewRepository
import cn.xiaobai.mafei.data.IosBridgeAwarePlaybackRepository
import cn.xiaobai.mafei.data.IosBridgeAwareSearchRepository
import cn.xiaobai.mafei.logging.AppLogger

fun MainViewController() = MainViewControllerWithBridge(
    baseUrl = null,
    serverId = null,
    username = null,
    userId = null,
    accessToken = null,
    rememberSession = false,
    savedAtEpochMillis = 0L,
)

fun MainViewControllerWithBridge(
    baseUrl: String?,
    serverId: String?,
    username: String?,
    userId: String?,
    accessToken: String?,
    rememberSession: Boolean,
    savedAtEpochMillis: Long,
) = ComposeUIViewController {
    AppLogger.installDefault()
    AppLogger.info(
        tag = "MainViewController",
        message = "installing iOS runtime bridge baseUrl=${baseUrl.redactedBaseUrl()} serverId=${serverId.redactedIdentifier()} userId=${userId.redactedIdentifier()} rememberSession=$rememberSession tokenAvailable=${!accessToken.isNullOrBlank()}",
    )
    installIosRuntimeBridge(
        baseUrl = baseUrl,
        serverId = serverId,
        username = username,
        userId = userId,
        rememberSession = rememberSession,
        accessToken = accessToken,
        savedAtEpochMillis = savedAtEpochMillis,
    )

    val homeRepository = remember { IosBridgeAwareHomeRepository() }
    val detailRepository = remember { IosBridgeAwareDetailRepository() }
    val searchRepository = remember { IosBridgeAwareSearchRepository() }
    val mediaViewRepository = remember { IosBridgeAwareMediaViewRepository() }
    val favoritesRepository = remember { IosBridgeAwareFavoritesRepository() }
    val playbackRepository = remember { IosBridgeAwarePlaybackRepository() }

    App(
        homeRepository = homeRepository,
        detailRepository = detailRepository,
        searchRepository = searchRepository,
        mediaViewRepository = mediaViewRepository,
        favoritesRepository = favoritesRepository,
        playbackRepository = playbackRepository,
    )
}

private fun String?.redactedIdentifier(): String {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return "-"
    if (value.length <= 8) return value
    return value.take(4) + "..." + value.takeLast(2)
}

private fun String?.redactedBaseUrl(): String {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return "-"
    val withoutScheme = value.substringAfter("://", value)
    return withoutScheme.substringBefore('?').substringBefore('#').ifBlank { value }
}
