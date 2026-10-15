package cn.mafei.cmp.common.ui

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/** Shell overlays join the same back chain as page-local sheets and NavDisplay. */
@Composable
fun MaFeiBackHandler(enabled: Boolean, onBack: () -> Unit) {
    if (LocalNavigationEventDispatcherOwner.current == null) return
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}
