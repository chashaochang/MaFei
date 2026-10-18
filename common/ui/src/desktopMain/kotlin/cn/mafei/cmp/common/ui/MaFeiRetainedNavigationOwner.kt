package cn.mafei.cmp.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/** Keeps a page's back handlers registered while only its active branch receives events. */
@Composable
fun MaFeiRetainedNavigationOwner(active: Boolean, content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalNavigationEventDispatcherOwner.current) {
        "A retained navigation page requires a parent NavigationEventDispatcherOwner"
    }.navigationEventDispatcher
    // alpha06's NavigationEventDispatcherOwner composable reuses the parent dispatcher.
    // Construct a real child so disabling or disposing this page cannot affect its siblings.
    val dispatcher = remember(parent) {
        NavigationEventDispatcher(parent).apply { isEnabled = active }
    }
    SideEffect { dispatcher.isEnabled = active }
    DisposableEffect(dispatcher) { onDispose { dispatcher.dispose() } }
    val owner = remember(dispatcher) {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner, content = content)
}
