package cn.mafei.cmp.common.glass

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GlassEffectGuardTest {
    @Test
    fun healthyEffectsKeepRunning() {
        val guard = GlassEffectGuard()
        var draws = 0
        repeat(3) { assertTrue(guard.run { draws++ }) }
        assertEquals(3, draws)
        assertFalse(guard.failed)
    }

    @Test
    fun effectFailureLatchesAndNotifiesOnceWithoutRetrying() {
        var notifications = 0
        var attempts = 0
        val guard = GlassEffectGuard { notifications++ }
        assertFalse(guard.run { attempts++; throw IllegalStateException("test effect failure") })
        assertTrue(guard.failed)
        repeat(3) { assertFalse(guard.run { attempts++ }) }
        guard.fail()
        assertEquals(1, attempts)
        assertEquals(1, notifications)
    }

    @Test
    fun cancellationAndFatalErrorsAreNotMaterialFailures() {
        val guard = GlassEffectGuard()
        val cancellation = CancellationException("test cancellation")
        assertSame(cancellation, assertFailsWith<CancellationException> { guard.run { throw cancellation } })
        val fatal = AssertionError("test fatal error")
        assertSame(fatal, assertFailsWith<AssertionError> { guard.run { throw fatal } })
        assertFalse(guard.failed)
        assertTrue(guard.run {})
    }

    @Test
    fun explicitFailureIsScopedToItsRenderingLifetime() {
        val failedWindow = GlassEffectGuard()
        val otherWindow = GlassEffectGuard()
        failedWindow.fail()
        assertFalse(failedWindow.run { error("must not retry") })
        assertTrue(otherWindow.run {})
        assertTrue(GlassEffectGuard().run {})
    }
}
