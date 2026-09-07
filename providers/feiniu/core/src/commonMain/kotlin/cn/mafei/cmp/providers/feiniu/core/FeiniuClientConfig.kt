package cn.mafei.cmp.providers.feiniu.core

import cn.mafei.cmp.common.core.EpochClock
import cn.mafei.cmp.common.core.SystemEpochClock

/** Injected clock/random for deterministic tests of the signing pipeline. */
interface FeiniuRuntimeSource {
    fun nowEpochMs(): Long
    fun random(): Double
}

class DefaultFeiniuRuntimeSource(
    private val clock: EpochClock = SystemEpochClock,
) : FeiniuRuntimeSource {
    override fun nowEpochMs(): Long = clock.nowEpochMs()
    override fun random(): Double = kotlin.random.Random.nextDouble()
}
