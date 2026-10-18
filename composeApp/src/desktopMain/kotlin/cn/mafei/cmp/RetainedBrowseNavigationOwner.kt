@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.ui.LocalNavigationEventDispatcherOwner as NavDisplayNavigationOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner as RootNavigationOwner
import cn.mafei.cmp.common.ui.MaFeiRetainedNavigationOwner

/** Uses the same child dispatcher for shared back handlers and Desktop's NavDisplay shim. */
@Composable
internal fun RetainedBrowseNavigationOwner(active: Boolean, content: @Composable () -> Unit) {
    MaFeiRetainedNavigationOwner(active) {
        CompositionLocalProvider(
            NavDisplayNavigationOwner provides checkNotNull(RootNavigationOwner.current),
            content = content,
        )
    }
}
