package cn.mafei.cmp.data.adapter_feiniu

import kotlinx.coroutines.runBlocking

/**
 * Kotlin-test rejects `@Test suspend` in this common source set, while the
 * adapter deliberately uses real coroutine fan-out. Run the body to actual
 * completion instead of requiring every fake to finish synchronously.
 */
internal fun runSuspendTest(block: suspend () -> Unit) = runBlocking { block() }
