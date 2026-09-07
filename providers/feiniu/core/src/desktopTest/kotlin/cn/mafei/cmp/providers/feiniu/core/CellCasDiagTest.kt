package cn.mafei.cmp.providers.feiniu.core

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test

data class S(val v: Long)

class CellCasDiagTest {
    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun rawAtomicReferenceCas() {
        val state = AtomicReference(S(0))
        var lost = 0
        val threads = (0 until 4).map { _ ->
            Thread {
                repeat(1000) {
                    while (true) {
                        val observed = state.load()
                        val next = S(observed.v + 1)
                        if (state.compareAndSet(observed, next)) break
                        lost++
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        println("DIAG final=${state.load().v} lost=$lost")
        check(state.load().v == 4000L) { "raw CAS lost updates: ${state.load().v}" }
    }
}
