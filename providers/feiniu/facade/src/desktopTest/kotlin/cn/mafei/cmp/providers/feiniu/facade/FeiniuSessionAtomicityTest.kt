package cn.mafei.cmp.providers.feiniu.facade

import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F1-1: real-thread proof that the conditional session commit is atomic —
 * a logout between the mutation check and its commit must win. This case
 * needs genuine thread interleaving (latch-driven), hence desktopTest;
 * the commonTest regressions cover the deterministic staged interleavings.
 */
class FeiniuSessionAtomicityTest {

    @Test
    fun conditionalUpdateIsAtomicAgainstLogout() {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account"))
        val expected = state.generation()
        val enteredMutation = CountDownLatch(1)
        val resumeMutation = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending = executor.submit<FeiniuCredentials?> {
                state.updateIfUnchanged(expected) { previous ->
                    enteredMutation.countDown()
                    check(resumeMutation.await(5, TimeUnit.SECONDS))
                    previous.copy(cookie = "sid=late-response")
                }
            }
            assertTrue(enteredMutation.await(5, TimeUnit.SECONDS))
            state.clear() // another thread logs out between check and commit
            resumeMutation.countDown()
            pending.get(5, TimeUnit.SECONDS)
            assertFalse(state.current.value.hasToken, "Atomic conditional write must not resurrect logout")
        } finally {
            resumeMutation.countDown()
            executor.shutdownNow()
        }
    }
}
