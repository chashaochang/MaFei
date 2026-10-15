package cn.mafei.cmp.player.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap

/** Native video is not in Compose's display list. Hosts publish small live frames for material sampling only. */
@Stable
class PlayerVideoBackdropSource {
    var frame: ImageBitmap? by mutableStateOf(null)
    var sampling: Boolean by mutableStateOf(false)
}

val LocalPlayerVideoBackdropSource = staticCompositionLocalOf<PlayerVideoBackdropSource?> { null }
