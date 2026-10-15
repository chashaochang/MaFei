package cn.mafei.cmp

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun BrowseArtworkTransitionHost(content: @Composable (SharedTransitionScope) -> Unit) {
    SharedTransitionLayout(Modifier.fillMaxSize()) { content(this) }
}
