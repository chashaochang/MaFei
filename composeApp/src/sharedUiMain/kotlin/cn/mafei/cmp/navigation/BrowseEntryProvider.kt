package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavEntry

/**
 * Keep the renderer itself stable, not only the provider that points to it.
 * The factory creates its composable lambda inside remember: creating it at
 * the call site would update its tracked block on every shell recomposition.
 * Every captured value that can change must be included in [inputs].
 */
@Composable
internal fun rememberBrowseEntryProvider(
    vararg inputs: Any?,
    contentFactory: () -> (@Composable (BrowseRoute) -> Unit),
): (BrowseRoute) -> NavEntry<BrowseRoute> {
    val content = remember(*inputs, calculation = contentFactory)
    val latestContent = rememberUpdatedState(content)
    return remember {
        { route -> NavEntry(route) { latestContent.value(route) } }
    }
}
