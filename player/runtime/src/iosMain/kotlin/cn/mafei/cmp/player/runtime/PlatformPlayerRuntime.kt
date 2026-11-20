package cn.mafei.cmp.player.runtime

import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.runtime_ios.IosAvPlayerBridge
import cn.mafei.cmp.player.runtime_ios.IosPlayerRuntime
import cn.mafei.cmp.player.runtime_ios.IosPollScheduler
import cn.mafei.cmp.player.runtime_ios.IosSurfaceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun createPlayerRuntime(
    bridge: IosAvPlayerBridge,
    surfaces: IosSurfaceRegistry,
    scope: CoroutineScope,
): PlayerRuntime = IosPlayerRuntime(
    PlayerCapabilities(seek = true, speed = true, trackSelection = true, contentMode = true),
    bridge,
    surfaces,
    object : IosPollScheduler {
        override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable {
            val job = scope.launch {
                while (isActive) {
                    delay(periodMs)
                    tick()
                }
            }
            return AutoCloseable { job.cancel() }
        }
    },
)
