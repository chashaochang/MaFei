package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.session.*

/** Account-scoped document stack. Window layout must never recreate this state. */
internal class BrowseNavigationState {
    val browseBackStack = mutableStateListOf<BrowseRoute>(BrowseHomeRoute)
    val browseInitialItems = mutableStateMapOf<String, MediaItem>()
    val currentRoute get() = browseBackStack.lastOrNull() ?: BrowseHomeRoute
}
