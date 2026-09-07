package cn.mafei.cmp.providers.feiniu.core

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Native (ios/ohos) snapshot cell. Snapshots are immutable; mutual exclusion
 * uses the sanctioned stdlib atomic spin lock (compare-and-set with a
 * single-assignment critical section).
 */
@OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
internal actual class FeiniuSnapshotCell actual constructor(initial: FeiniuSessionSnapshot) {

    private val lock = AtomicInt(0)
    private var current: FeiniuSessionSnapshot = initial

    private inline fun <T> locked(block: () -> T): T {
        while (!lock.compareAndSet(0, 1)) {
            // spin; the critical section is a single assignment
        }
        try {
            return block()
        } finally {
            lock.store(0)
        }
    }

    actual fun read(): FeiniuSessionSnapshot = locked { current }

    actual fun compareAndCommit(expected: FeiniuSessionSnapshot, next: FeiniuSessionSnapshot): Boolean = locked {
        if (current != expected) {
            false
        } else {
            current = next
            true
        }
    }

    actual fun write(next: FeiniuSessionSnapshot): Unit = locked { current = next }
}
