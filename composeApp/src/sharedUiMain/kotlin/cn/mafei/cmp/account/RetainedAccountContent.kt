package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

/**
 * Keeps one movable account tree and a renderer that only changes with its inputs.
 * Construct all nested composable renderers inside [contentFactory]; raw lambdas
 * created before this call would still invalidate their hidden invocation scopes.
 */
@Composable
internal fun rememberRetainedAccountContent(
    accountKey: Any,
    vararg inputs: Any?,
    contentFactory: () -> (@Composable (Modifier) -> Unit),
): @Composable (Modifier) -> Unit {
    val content = remember(accountKey, *inputs, calculation = contentFactory)
    val latestContent = rememberUpdatedState(content)
    return remember(accountKey) {
        movableContentOf<Modifier> { modifier -> latestContent.value(modifier) }
    }
}
