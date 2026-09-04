package cn.mafei.cmp.common.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException

class CommonCoreTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    // ------------------------------------------------------------- EpochClock

    @Test
    fun fixedClockIsDeterministic() {
        val clock = FixedEpochClock(initialEpochMs = 1_000L)
        assertEquals(1_000L, clock.nowEpochMs())

        clock.advanceBy(250L)
        assertEquals(1_250L, clock.nowEpochMs())

        clock.setTo(9_999L)
        assertEquals(9_999L, clock.nowEpochMs())
    }

    @Test
    fun systemClockProducesMonotonicIncreasingEpochTime() {
        val first = SystemEpochClock.nowEpochMs()
        val second = SystemEpochClock.nowEpochMs()
        assertTrue(second >= first)
        assertTrue(first > 1_600_000_000_000L) // sane epoch (>= 2020-09)
    }

    // ------------------------------------------------- runCatchingCancellable

    @Test
    fun runCatchingCancellableCapturesFailures() {
        val failure = runCatchingCancellable { error("boom") }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull() is IllegalStateException)

        val success = runCatchingCancellable { 42 }
        assertEquals(42, success.getOrThrow())
    }

    @Test
    fun runCatchingCancellableRethrowsCancellation() = runTest {
        val thrown = try {
            runCatchingCancellable { throw CancellationException("cancelled") }
            null
        } catch (t: Throwable) {
            t
        }
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun runCatchingSuspendCancellableRethrowsCancellation() = runTest {
        val thrown = try {
            runCatchingSuspendCancellable { throw CancellationException("cancelled") }
            null
        } catch (t: Throwable) {
            t
        }
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun runCatchingSuspendCancellableCapturesFailures() = runTest {
        val failure = runCatchingSuspendCancellable { error("late boom") }
        assertTrue(failure.isFailure)
        assertFalse(failure.isSuccess)

        val success = runCatchingSuspendCancellable { "value" }
        assertEquals("value", success.getOrThrow())
    }

    // ------------------------------------------------------------- TextChecks

    @Test
    fun blankToNullTrimsAndDropsBlanks() {
        assertNull(null.blankToNull())
        assertNull("".blankToNull())
        assertNull("   ".blankToNull())
        assertEquals("x", " x ".blankToNull())
        assertEquals("a b", "a b".blankToNull())
    }

    @Test
    fun requireNonBlankValidatesAndTrims() {
        assertEquals("v", requireNonBlank(" v ", "field"))
        assertFailsWith<IllegalArgumentException> { requireNonBlank("", "field") }
        assertFailsWith<IllegalArgumentException> { requireNonBlank(null, "field") }
        assertFailsWith<IllegalArgumentException> { requireNonBlank("   ", "field") }
    }
}
