package cn.mafei.cmp.feature.detail

import androidx.compose.runtime.Composable
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationEventHandler

@Composable
internal fun DetailSheetBackHandler(enabled: Boolean, onDismiss: () -> Unit) {
    if (LocalNavigationEventDispatcherOwner.current == null) return
    NavigationEventHandler(enabled = enabled) { progress ->
        progress.collect { }
        onDismiss()
    }
}
