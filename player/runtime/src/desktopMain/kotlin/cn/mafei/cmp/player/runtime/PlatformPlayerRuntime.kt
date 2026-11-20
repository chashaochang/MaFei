package cn.mafei.cmp.player.runtime

import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.runtime_macos.JniAvPlayerBridge
import cn.mafei.cmp.player.runtime_macos.MacOsAvPlayerRuntime
import cn.mafei.cmp.player.runtime_macos.MacSurfaceRegistry

internal fun createMacSystemPlayerRuntime(surfaces: MacSurfaceRegistry): PlayerRuntime = MacOsAvPlayerRuntime(
    capabilities = PlayerCapabilities(
        seek = true,
        speed = true,
        trackSelection = true,
        contentMode = true,
        backgroundPlayback = false,
    ),
    bridge = JniAvPlayerBridge(),
    surfaces = surfaces,
)
