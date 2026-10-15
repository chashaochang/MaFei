package cn.mafei.cmp.providers.feiniu.facade

import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * G1-G3 real-thread regressions from the e203f5e final review. These need
 * genuine thread interleaving/pressure (latches, 4-thread pressure) and
 * therefore live in desktopTest; commonTest keeps the deterministic staged
 * variants with the synchronous trampoline.
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
            assertFalse(state.snapshot().credentials.hasToken, "Atomic conditional write must not resurrect logout")
        } finally {
            resumeMutation.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun concurrentIdentityInstallsMustNotReuseGeneration() {
        val state = InMemoryFeiniuSessionState()
        val workers = 4
        val installsPerWorker = 1000
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workers)
        try {
            val pending = (0 until workers).map { worker ->
                executor.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(installsPerWorker) { state.set(FeiniuCredentials("account-$worker-$it")) }
                }
            }
            start.countDown()
            pending.forEach { it.get(30, TimeUnit.SECONDS) }
            assertEquals((workers * installsPerWorker).toLong(), state.identityGeneration(),
                "Every successful set must consume a distinct monotonically increasing identity generation")
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    // G3: hold the production commit critical section via its public mutation
    // callback; a matching-identity install must not be dropped on contention.
    @Test
    fun validIdentityInstallMustNotBeDroppedOnContention() {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old"))
        val snapshot = state.snapshot()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val pending = executor.submit<FeiniuCredentials?> {
            state.commitIfUnchanged(snapshot) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                it.copy(cookie = "sid=poll-response")
            }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val committed = state.installIdentityIfUnchanged(snapshot.identityGeneration, FeiniuCredentials("new"))
            assertNotNull(committed, "Matching-identity login was silently dropped on lock contention")
            assertEquals("new", state.snapshot().credentials.token)
        } finally {
            release.countDown()
            pending.get(5, TimeUnit.SECONDS)
            executor.shutdownNow()
        }
    }

    // G3: a matching-identity logout must not be silently dropped on contention.
    @Test
    fun validIdentityClearMustNotBeDroppedOnContention() {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old"))
        val snapshot = state.snapshot()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val pending = executor.submit<FeiniuCredentials?> {
            state.commitIfUnchanged(snapshot) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                it.copy(cookie = "sid=poll-response")
            }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val committed = state.clearIfIdentityUnchanged(snapshot.identityGeneration)
            assertNotNull(committed, "Matching-identity logout was silently dropped on lock contention")
            assertFalse(state.snapshot().credentials.hasToken)
        } finally {
            release.countDown()
            pending.get(5, TimeUnit.SECONDS)
            executor.shutdownNow()
        }
    }
}
