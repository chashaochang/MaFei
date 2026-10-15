package cn.mafei.cmp.common.ui

import androidx.compose.runtime.Composable
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationEventHandler

@Composable
fun MaFeiBackHandler(enabled: Boolean, onBack: () -> Unit) {
    if (LocalNavigationEventDispatcherOwner.current == null) return
    NavigationEventHandler(enabled = enabled) { progress ->
        progress.collect { }
        onBack()
    }
}
