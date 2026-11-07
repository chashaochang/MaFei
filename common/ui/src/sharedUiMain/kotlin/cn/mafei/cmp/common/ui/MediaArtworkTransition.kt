package cn.mafei.cmp.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import cn.mafei.cmp.core.domain.MediaItem

/** The navigation host supplies the transition; reusable cards only mark their artwork. */
val LocalMediaArtworkTransition = staticCompositionLocalOf<@Composable (MediaItem, Modifier) -> Modifier> {
    { _, modifier -> modifier }
}

@Composable
fun Modifier.mediaArtworkTransition(item: MediaItem): Modifier =
    LocalMediaArtworkTransition.current(item, this)
