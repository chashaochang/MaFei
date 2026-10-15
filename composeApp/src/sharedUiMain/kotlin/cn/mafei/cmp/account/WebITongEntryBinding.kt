package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.feature.home.PlaybackQueue
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
internal fun WebITongEntryBinding(
    account: AccountFlowState,
    scope: kotlinx.coroutines.CoroutineScope,
    session: ApplicationSession,
    player: BrowsePlayerState,
    playback: SessionPlaybackRuntime?,
    popMineRoute: () -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    embeddedWebMemory: EmbeddedWebPageState,
    onPlayerFullscreenChanged: (Boolean) -> Unit,
    isSessionActive: () -> Boolean,
    repository: cn.mafei.cmp.data.repositories.MediaRepository,
    deviceControls: cn.mafei.cmp.player.api.PlayerDeviceControls,
) {
    JellyfinEmbeddedPage(session, embeddedWebMemory, popMineRoute) { action, raw ->
        if (account.mineBackStack.lastOrNull() == BrowseWebITongRoute && isSessionActive()) {
            scope.launch {
                try {
                    when (action) {
                        "loadPlayer" -> {
                            val options =
                                cn.mafei.cmp.core.serialization.MafeiJson.Default.decodeFromString(
                                    cn.mafei.cmp.session.WebNativePlaybackRequest.serializer(),
                                    raw,
                                )
                            val items = options.ids.map { id -> repository.item(session.media, MediaId(id)).value }
                            if (isSessionActive()) {
                                account.returnToEmbeddedWeb = true
                                account.minePresented = false
                                player.startPlaybackItem(
                                    items[options.startIndex],
                                    PlaybackQueue(items, options.startIndex),
                                    options.startMs,
                                    options.options(),
                                )
                            }
                        }
                        "pausePlayer" -> playback?.controller?.pause()
                        "resumePlayer" -> playback?.controller?.play()
                        "stopPlayer",
                        "destroyPlayer" -> playback?.stop()
                        "seek" -> raw.toLongOrNull()?.takeIf { it >= 0 }?.let { playback?.controller?.seek(it / 10_000L) }
                        "seekMs" -> raw.toLongOrNull()?.takeIf { it >= 0 }?.let { playback?.controller?.seek(it) }
                        "setVolume" ->
                            raw.toFloatOrNull()
                                ?.takeIf { it.isFinite() && it in 0f..100f }
                                ?.let {
                                    val controls = deviceControls
                                    if (controls.maxVolume() > 0) controls.setVolume((it / 100f * controls.maxVolume()).toInt())
                                }
                        "openUrl" -> cn.mafei.cmp.core.http_contract.HttpUrl.parseOrNull(raw)?.let { uriHandler.openUri(it.toString()) }
                        "openClientSettings" -> account.mineBackStack += BrowseSettingsRoute
                        "openServerSelection" -> {
                            account.loadAccounts()
                            account.mineBackStack += BrowseAccountSwitchRoute
                        }
                        "enableFullscreen",
                        "disableFullscreen" -> {
                            player.playerFullscreen = action == "enableFullscreen"
                            onPlayerFullscreenChanged(player.playerFullscreen)
                        }
                        "exitApp" -> popMineRoute()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    player.playbackError = "网页操作失败，请重试"
                }
            }
        }
    }
}
