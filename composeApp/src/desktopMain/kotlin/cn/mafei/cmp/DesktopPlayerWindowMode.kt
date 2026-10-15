package cn.mafei.cmp

import androidx.compose.ui.window.WindowPlacement

/** Only a player-initiated fullscreen transition owns the previous window placement. */
internal class DesktopPlayerWindowMode {
    private var restorePlacement: WindowPlacement? = null

    fun request(current: WindowPlacement, fullscreen: Boolean): WindowPlacement {
        if (fullscreen) {
            if (current != WindowPlacement.Fullscreen && restorePlacement == null) restorePlacement = current
            return WindowPlacement.Fullscreen
        }
        val previous = restorePlacement
        restorePlacement = null
        return if (current == WindowPlacement.Fullscreen) previous ?: WindowPlacement.Floating else current
    }

    fun playerClosed(current: WindowPlacement): WindowPlacement =
        if (restorePlacement != null) request(current, false) else current
}
