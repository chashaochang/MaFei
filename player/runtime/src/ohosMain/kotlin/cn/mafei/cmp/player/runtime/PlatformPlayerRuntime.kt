package cn.mafei.cmp.player.runtime

import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.runtime_ohos.OhosNativePlayerBridge
import cn.mafei.cmp.player.runtime_ohos.OhosPlayerRuntime

fun createPlayerRuntime(bridge: OhosNativePlayerBridge): PlayerRuntime = OhosPlayerRuntime(
    capabilities = PlayerCapabilities(
        seek = true,
        speed = true,
        trackSelection = true,
        backgroundPlayback = false,
        contentMode = true,
        isoDirectPlay = true,
        playbackPreferences = true,
        engineSelection = true,
    ),
    bridge = bridge,
)
