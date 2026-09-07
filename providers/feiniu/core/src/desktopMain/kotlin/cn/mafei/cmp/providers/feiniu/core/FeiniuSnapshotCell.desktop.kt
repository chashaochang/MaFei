package cn.mafei.cmp.providers.feiniu.core

internal actual class FeiniuSnapshotCell actual constructor(initial: FeiniuSessionSnapshot) {

    private val lock = Any()

    @Volatile
    private var current: FeiniuSessionSnapshot = initial

    actual fun read(): FeiniuSessionSnapshot = current

    actual fun compareAndCommit(expected: FeiniuSessionSnapshot, next: FeiniuSessionSnapshot): Boolean =
        synchronized(lock) {
            if (current != expected) return@compareAndCommit false
            current = next
            true
        }

    actual fun write(next: FeiniuSessionSnapshot): Unit = synchronized(lock) {
        current = next
    }
}
