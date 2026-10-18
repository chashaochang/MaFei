package cn.mafei.cmp

import androidx.compose.runtime.Composable
import cn.mafei.cmp.common.ui.MaFeiRetainedNavigationOwner

/** CPF NavDisplay and shared back handlers already read the same public owner local. */
@Composable
internal fun RetainedBrowseNavigationOwner(active: Boolean, content: @Composable () -> Unit) {
    MaFeiRetainedNavigationOwner(active, content)
}
