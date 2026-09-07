package cn.mafei.cmp.providers.feiniu.core

/**
 * Single-variable atomic snapshot cell (G1): credentials, identity
 * generation and revision live in ONE immutably-published value, so
 * "read state → derive next → commit" is atomic. Snapshots are immutable,
 * so publication is safe.
 *
 * Platform actuals: JVM uses a monitor; Native uses a stdlib-atomic spin
 * lock (compare-and-set with an O(1) assignment critical section).
 * [compareAndCommit] blocks on contention and returns `false` only when the
 * current snapshot differs from [expected] — contention and staleness are
 * therefore distinguishable at the state layer (G3).
 */
internal expect class FeiniuSnapshotCell(initial: FeiniuSessionSnapshot) {
    fun read(): FeiniuSessionSnapshot

    /** Atomic compare-and-commit; blocks on contention. `false` = stale. */
    fun compareAndCommit(expected: FeiniuSessionSnapshot, next: FeiniuSessionSnapshot): Boolean

    /** Unconditional atomic write. */
    fun write(next: FeiniuSessionSnapshot)
}
