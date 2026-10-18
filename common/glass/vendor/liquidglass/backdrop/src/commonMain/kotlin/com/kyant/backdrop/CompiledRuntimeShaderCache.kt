package com.kyant.backdrop

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Bounded process cache for immutable compiled programs, never mutable shader builders.
 * Full source text is the key, so unrelated callers cannot alias a named shader key.
 * Eviction drops only our reference: an active shader keeps its program alive.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class CompiledRuntimeShaderCache<T : Any>(
    private val capacity: Int = 32,
    private val compile: (String) -> T,
) {
    init { require(capacity > 0) }

    private class Entry<T>(val source: String, val program: Lazy<T>)
    private val entries = AtomicReference<List<Entry<T>>>(emptyList())

    val size: Int get() = entries.load().size

    fun obtain(source: String): T {
        while (true) {
            val current = entries.load()
            val index = current.indexOfFirst { it.source == source }
            if (index == 0) return current[0].program.value
            val entry = if (index >= 0) current[index] else Entry(
                source,
                // Only the published entry compiles. Racing UI/warmup callers share
                // one initialization, without holding a process-wide compile lock.
                lazy(LazyThreadSafetyMode.SYNCHRONIZED) { compile(source) },
            )
            val updated = buildList {
                add(entry)
                for (old in current) {
                    if (size == capacity) break
                    if (old !== entry) add(old)
                }
            }
            if (entries.compareAndSet(current, updated)) return entry.program.value
        }
    }
}
