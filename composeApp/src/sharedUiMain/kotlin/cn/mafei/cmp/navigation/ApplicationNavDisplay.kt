package cn.mafei.cmp

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import cn.mafei.cmp.session.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ApplicationNavDisplay(
    backStack: List<BrowseRoute>,
    onBack: () -> Unit,
    entryProvider: (BrowseRoute) -> NavEntry<BrowseRoute>,
    modifier: Modifier = Modifier,
) {
    // A tab replaces the root instead of pushing a document. Give each root
    // its own display lifetime so the outgoing page is removed immediately,
    // while detail push/pop within that root keeps the normal transition.
    // Preserve saveable page state (e.g. Home's selected feed) independently
    // of that lifetime; retaining a scroll anchor without its feed is unsafe.
    val rootStateHolder = rememberSaveableStateHolder()
    val rootKey = backStack.first().toString()
    key(rootKey) {
        rootStateHolder.SaveableStateProvider(rootKey) {
            BrowseStackDisplay(backStack, onBack, entryProvider, modifier)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun BrowseStackDisplay(
    backStack: List<BrowseRoute>,
    onBack: () -> Unit,
    entryProvider: (BrowseRoute) -> NavEntry<BrowseRoute>,
    modifier: Modifier,
) {
    NavDisplay(
        backStack = backStack,
        onBack = { onBack() },
        transitionSpec = {
            (fadeIn(tween(180)) + slideInHorizontally(tween(260)) { it / 8 }) togetherWith (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 8 })
        },
        popTransitionSpec = {
            (fadeIn(tween(180)) + slideInHorizontally(tween(260)) { -it / 8 }) togetherWith (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 8 })
        },
        predictivePopTransitionSpec = {
            (fadeIn(tween(180)) + slideInHorizontally(tween(260)) { -it / 8 }) togetherWith (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 8 })
        },
        entryProvider = entryProvider,
        modifier = modifier,
    )
}
