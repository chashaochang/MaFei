package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.session.*

@Composable
internal fun PlayerSettingsEntryBinding(playback: SessionPlaybackRuntime?, popMineRoute: () -> Unit, playerPreferencesStore: PlayerPreferencesStore) {
    PlayerSettingsRoute(playerPreferencesStore, engineSelection = playback?.controller?.capabilities?.engineSelection == true, onBack = popMineRoute)
}
