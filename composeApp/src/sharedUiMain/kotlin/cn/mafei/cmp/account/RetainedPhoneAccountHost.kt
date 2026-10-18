package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cn.mafei.cmp.common.ui.LocalMaFeiPageActive
import cn.mafei.cmp.common.ui.MaFeiRetainedContent

/** Parks the visited account tree between phone tabs and while the wide modal is closed. */
@Composable
internal fun RetainedPhoneAccountHost(
    phone: Boolean,
    presented: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    var visited by remember { mutableStateOf(false) }
    SideEffect { if (presented) visited = true }
    // The caller moves the same movable content into the wide modal while open.
    // Every other visited state owns exactly one parking slot, including a hidden
    // phone tab resized to wide before the account surface is opened again.
    if ((visited || presented) && (phone || !presented)) {
        MaFeiRetainedContent(active = phone && presented) {
            val active = LocalMaFeiPageActive.current
            RetainedBrowseNavigationOwner(active) {
                RetainedBrowseLifecycleOwner(active) {
                    MineFullScreenHost(content)
                }
            }
        }
    }
}
