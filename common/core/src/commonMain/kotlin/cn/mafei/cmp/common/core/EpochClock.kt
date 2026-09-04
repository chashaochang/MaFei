package cn.mafei.cmp.common.core

/**
 * Epoch-milliseconds clock abstraction. Consumers so far: data layer (cache
 * expiry, migration timestamps), providers (playstate reporting) — injected
 * everywhere so time-dependent logic stays deterministic in tests.
 */
interface EpochClock {
    fun nowEpochMs(): Long
}

/** Wall-clock implementation backed by the platform epoch clock. */
data object SystemEpochClock : EpochClock {
    override fun nowEpochMs(): Long = currentTimeMilliseconds()
}

/** Test/fixture clock: frozen time advanced manually. */
class FixedEpochClock(initialEpochMs: Long = 0L) : EpochClock {
    var currentEpochMs: Long = initialEpochMs
        private set

    override fun nowEpochMs(): Long = currentEpochMs

    fun advanceBy(milliseconds: Long) {
        currentEpochMs += milliseconds
    }

    fun setTo(epochMs: Long) {
        currentEpochMs = epochMs
    }
}

/**
 * Platform epoch milliseconds. Kept as an internal expect/actual pair so this
 * module stays dependency-free; prefer injecting [EpochClock] instead.
 */
internal expect fun currentTimeMilliseconds(): Long
