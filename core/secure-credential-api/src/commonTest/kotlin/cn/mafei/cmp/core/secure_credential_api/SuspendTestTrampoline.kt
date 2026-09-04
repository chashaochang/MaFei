package cn.mafei.cmp.core.secure_credential_api

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

/**
 * Runs a suspending test body on the calling thread. Contract-test fakes never
 * dispatch to real threads, so any actual suspension is a test failure.
 * (This Kotlin toolchain rejects `@Test suspend` functions in commonTest.)
 */
internal fun runSuspendTest(block: suspend () -> Unit) {
    val result = block.startCoroutineUninterceptedOrReturn(NoopContinuation)
    check(result !== COROUTINE_SUSPENDED) {
        "test body suspended unexpectedly; it must complete synchronously"
    }
}

private object NoopContinuation : Continuation<Unit> {
    override val context = EmptyCoroutineContext
    override fun resumeWith(result: Result<Unit>) {
        result.exceptionOrNull()?.let { throw it }
    }
}
