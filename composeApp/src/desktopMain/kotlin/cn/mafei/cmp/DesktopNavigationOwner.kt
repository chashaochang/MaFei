@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.ui.LocalNavigationEventDispatcherOwner as NavDisplayNavigationOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner as RootNavigationOwner
import androidx.navigationevent.compose.NavigationEventDispatcherOwner

/**
 * Bridges the public Navigation Event owner into the compatibility local read by
 * the CPF-aligned Navigation 3 Desktop artifact.
 *
 * Desktop can delete this shim when it moves to the unified Compose 1.10+
 * Navigation 3 artifacts. Android, iOS and OHOS use their published platform owner.
 */
@Composable
internal fun DesktopNavigationOwner(content: @Composable () -> Unit) {
    NavigationEventDispatcherOwner(parent = null) {
        val navigationOwner = checkNotNull(RootNavigationOwner.current)
        CompositionLocalProvider(
            NavDisplayNavigationOwner provides navigationOwner,
            content = content,
        )
    }
}
