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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import cn.mafei.cmp.common.ui.LocalMaFeiPageActive
import cn.mafei.cmp.common.ui.MaFeiRetainedContent
import cn.mafei.cmp.core.domain.MediaItem

internal val LocalBrowseRoot = compositionLocalOf<BrowseRoute> { BrowseHomeRoute }
internal val LocalBrowseArtworkTransition = staticCompositionLocalOf<@Composable (MediaItem, Modifier) -> Modifier> {
    { _, modifier -> modifier }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ApplicationNavDisplay(
    navigation: BrowseNavigationState,
    onBack: () -> Unit,
    entryProvider: (BrowseRoute) -> NavEntry<BrowseRoute>,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    visible: Boolean = true,
) {
    val rootStateHolder = rememberSaveableStateHolder()
    Box(modifier) {
        navigation.retainedRoots.forEach { root ->
            key(root) {
                val rootKey = root.toString()
                DisposableEffect(rootKey) {
                    onDispose { rootStateHolder.removeState(rootKey) }
                }
                rootStateHolder.SaveableStateProvider(rootKey) {
                    MaFeiRetainedContent(active = visible && root == navigation.activeRoot) {
                        val pageActive = LocalMaFeiPageActive.current && active
                        CompositionLocalProvider(LocalBrowseRoot provides root, LocalMaFeiPageActive provides pageActive) {
                            RetainedBrowseNavigationOwner(pageActive) {
                                RetainedBrowseLifecycleOwner(pageActive) {
                                    // Each tab owns its shared-element registry. A hidden copy of
                                    // the same poster can never become another tab's transition target.
                                    BrowseArtworkTransitionHost { artworkTransition ->
                                      CompositionLocalProvider(LocalBrowseArtworkTransition provides artworkTransition) {
                                        BrowseStackDisplay(
                                            backStack = navigation.backStackFor(root).toList(),
                                            onBack = onBack,
                                            entryProvider = entryProvider,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                      }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RetainedBrowseLifecycleOwner(active: Boolean, content: @Composable () -> Unit) {
    val parent = LocalLifecycleOwner.current
    val owner = remember(parent) { BrowsePageLifecycleOwner(active) }
    DisposableEffect(owner, parent) {
        val observer = LifecycleEventObserver { _, event -> owner.updateParent(event.targetState) }
        parent.lifecycle.addObserver(observer)
        onDispose {
            parent.lifecycle.removeObserver(observer)
            owner.destroy()
        }
    }
    SideEffect { owner.updateActive(active) }
    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}

private class BrowsePageLifecycleOwner(private var active: Boolean) : LifecycleOwner {
    override val lifecycle = LifecycleRegistry(this)
    private var parentState = Lifecycle.State.INITIALIZED

    fun updateParent(state: Lifecycle.State) {
        parentState = state
        update()
    }

    fun updateActive(value: Boolean) {
        active = value
        update()
    }

    fun destroy() {
        if (lifecycle.currentState != Lifecycle.State.INITIALIZED) {
            lifecycle.currentState = Lifecycle.State.DESTROYED
        }
    }

    private fun update() {
        val maximum = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        val state = minOf(parentState, maximum)
        if (lifecycle.currentState != Lifecycle.State.INITIALIZED || state != Lifecycle.State.DESTROYED) {
            lifecycle.currentState = state
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
