package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import cn.mafei.cmp.core.domain.MediaItem

/** Account-scoped tab stacks. Window layout must never recreate this state. */
internal class BrowseNavigationState {
    private val stacks = mutableStateMapOf<BrowseRoute, SnapshotStateList<BrowseRoute>>(
        BrowseHomeRoute to mutableStateListOf(BrowseHomeRoute),
    )
    private val rootRecency = mutableStateListOf<BrowseRoute>(BrowseHomeRoute)
    var activeRoot: BrowseRoute by mutableStateOf(BrowseHomeRoute)
        private set
    var tabGeneration: Long = 0L
        private set

    /** Stable list identity per root; existing page commands always operate on the active stack. */
    val browseBackStack: SnapshotStateList<BrowseRoute>
        get() = checkNotNull(stacks[activeRoot])
    val retainedRoots: List<BrowseRoute>
        get() = rootRecency.toList()
    val browseInitialItems = mutableStateMapOf<String, MediaItem>()
    val currentRoute
        get() = browseBackStack.lastOrNull() ?: BrowseHomeRoute

    fun backStackFor(root: BrowseRoute): List<BrowseRoute> = checkNotNull(stacks[root])

    /** Reselecting a tab preserves its documents and scroll state instead of resetting it. */
    fun selectRoot(root: BrowseRoute) {
        if (root == activeRoot) return
        tabGeneration++
        val outgoing = activeRoot
        if (!outgoing.isRetainableBrowseRoot()) {
            stacks.remove(outgoing)
            rootRecency.remove(outgoing)
        }
        stacks.getOrPut(root) { mutableStateListOf(root) }
        rootRecency.remove(root)
        rootRecency += root
        activeRoot = root
        // A temporary library root also counts toward the memory bound. Evicted tabs start
        // fresh on their next visit; roots still in the cache keep their complete stack.
        while (rootRecency.size > MaxRetainedBrowseRoots) {
            stacks.remove(rootRecency.removeAt(0))
        }
    }
}

internal const val MaxRetainedBrowseRoots = 4

private fun BrowseRoute.isRetainableBrowseRoot(): Boolean =
    this == BrowseHomeRoute || this == BrowseLibraryIndexRoute ||
        this == BrowseFavoriteRoute || this == BrowseChasingRoute
