package cn.mafei.cmp

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable

/** UI scroll anchors are scoped to a route in a tab, not to the provider's current collection. */
@Stable
internal class BrowsePageScrollStates {
    private val grids = mutableMapOf<Pair<BrowseRoute, BrowseRoute>, LazyGridState>()

    fun grid(root: BrowseRoute, route: BrowseRoute): LazyGridState =
        grids.getOrPut(root to route) { LazyGridState() }

    fun retain(entries: Set<Pair<BrowseRoute, BrowseRoute>>) {
        grids.keys.retainAll(entries)
    }
}

@Composable
internal fun PruneBrowsePageScrollStates(navigation: BrowseNavigationState, states: BrowsePageScrollStates) {
    val entries = navigation.retainedRoots.flatMap { root ->
        navigation.backStackFor(root).map { route -> root to route }
    }.toSet()
    SideEffect { states.retain(entries) }
}
