package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.session.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun BrowseArtworkTransitionHost(
    content: @Composable (@Composable (MediaItem, Modifier) -> Modifier) -> Unit,
) {
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedTransitionScope = this
        content(sharedArtworkModifier)
    }
    }
}
