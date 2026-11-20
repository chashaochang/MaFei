package cn.mafei.cmp.player.runtime

import android.content.Context
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.runtime_android.AndroidMediaPlayerRuntime
import cn.mafei.cmp.player.runtime_android.AndroidSurfaceRegistry
import cn.mafei.cmp.player.runtime_android.MainThreadPollScheduler
import cn.mafei.cmp.player.runtime_android.SystemMediaPlayerEngine

val usesSdkAndroidVideoSurface: Boolean get() = false

fun createPlayerRuntime(context: Context, surfaces: AndroidSurfaceRegistry): PlayerRuntime =
    AndroidMediaPlayerRuntime(
        capabilities = PlayerCapabilities(
            seek = true,
            speed = true,
            trackSelection = false, // Device-dependent; not exposed by this adapter.
            backgroundPlayback = false,
            contentMode = true,
        ),
        engine = SystemMediaPlayerEngine(context),
        pollScheduler = MainThreadPollScheduler(),
        surfaces = surfaces,
    )
