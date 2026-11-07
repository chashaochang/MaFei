package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.player.api.PlayerRenderMode
import cn.mafei.cmp.session.*

@Composable
internal fun PlayerSettingsEntryBinding(
    playback: SessionPlaybackRuntime?,
    popMineRoute: () -> Unit,
    playerPreferencesStore: PlayerPreferencesStore,
    currentPlayerRenderMode: State<PlayerRenderMode>,
    currentPlayerRenderModeChanged: State<(PlayerRenderMode) -> Unit>,
    playerRenderModeSupported: Boolean,
) {
    PlayerSettingsRoute(
        store = playerPreferencesStore,
        engineSelection = playback?.controller?.capabilities?.engineSelection == true,
        showPlayerRenderMode = playerRenderModeSupported,
        playerRenderMode = currentPlayerRenderMode.value.storageValue,
        onPlayerRenderModeChanged = { value -> currentPlayerRenderModeChanged.value(value.toPlayerRenderMode()) },
        onBack = popMineRoute,
    )
}
