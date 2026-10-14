package cn.mafei.cmp.player.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*

/** Attach to the whole scene, not the video hit area: controls and menus are its siblings. */
internal fun Modifier.playerKeyboardControls(
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: ((Long) -> Unit)?,
    onFullscreen: (() -> Unit)?,
    onWindowFullscreen: (() -> Unit)?,
    onVolumeBy: ((Int) -> Unit)?,
    onMute: (() -> Unit)?,
): Modifier = onKeyEvent { event ->
    if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return@onKeyEvent false
    val action: (() -> Unit)? = when (event.key) {
        Key.Escape -> onBack
        Key.Spacebar -> onTogglePlay
        Key.F -> onFullscreen
        Key.W -> onWindowFullscreen
        Key.DirectionLeft -> onSeekBy?.let { { it(-10_000L) } }
        Key.DirectionRight -> onSeekBy?.let { { it(10_000L) } }
        Key.DirectionUp -> onVolumeBy?.let { { it(1) } }
        Key.DirectionDown -> onVolumeBy?.let { { it(-1) } }
        Key.M -> onMute
        else -> null
    }
    if (action == null) return@onKeyEvent false
    val activation = if (event.key in listOf(Key.Spacebar, Key.F, Key.W, Key.M)) KeyEventType.KeyUp else KeyEventType.KeyDown
    if (event.type == activation) action()
    true
}
