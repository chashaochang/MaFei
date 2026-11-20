package cn.mafei.cmp.player.runtime

import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.runtime_macos.MacSurfaceRegistry

val usesSdkMacVideoSurface: Boolean get() = false

fun createPlayerRuntime(surfaces: MacSurfaceRegistry): PlayerRuntime = createMacSystemPlayerRuntime(surfaces)
