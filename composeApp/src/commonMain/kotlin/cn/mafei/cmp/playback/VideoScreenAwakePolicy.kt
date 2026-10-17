package cn.mafei.cmp

import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState

/** Only the visible local video surface requests this; music never supplies the request. */
internal fun PlayerState.keepVideoScreenAwake(): Boolean =
    !casting && !pictureInPicture && when (phase) {
        PlayerPhase.Loading, PlayerPhase.Ready, PlayerPhase.Playing, PlayerPhase.Buffering -> true
        else -> false
    }
