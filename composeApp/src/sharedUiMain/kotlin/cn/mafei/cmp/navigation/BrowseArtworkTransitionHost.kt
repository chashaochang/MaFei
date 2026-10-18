package cn.mafei.cmp

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import cn.mafei.cmp.common.ui.LocalMaFeiPageActive
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.session.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun BrowseArtworkTransitionHost(content: @Composable (@Composable (MediaItem, Modifier) -> Modifier) -> Unit) {
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedTransitionScope = this
        val sharedArtworkModifier: @Composable (MediaItem, Modifier) -> Modifier =
            remember(sharedTransitionScope) {
                { item, modifier ->
                    if (!LocalMaFeiPageActive.current) modifier else
                    with(sharedTransitionScope) {
                        modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "media-artwork:${item.id.value}"),
                            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                            enter = fadeIn(tween(320)),
                            exit = fadeOut(tween(320)),
                            boundsTransform = { _, _ -> tween(420) },
                            zIndexInOverlay = 1f,
                        )
                    }
                }
            }
        content(sharedArtworkModifier)
    }
}
