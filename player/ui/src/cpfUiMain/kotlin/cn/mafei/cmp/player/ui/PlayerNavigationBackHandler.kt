package cn.mafei.cmp.player.ui

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
internal fun PlayerNavigationBackHandler(onBack: () -> Unit) {
    if (LocalNavigationEventDispatcherOwner.current == null) return
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onBack,
    )
}
