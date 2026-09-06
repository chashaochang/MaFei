package cn.mafei.cmp.providers.jellyfin.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinVersionCompatibilityTest {

    @Test
    fun parsesCommonJellyfinVersionShapes() {
        assertEquals(Triple(10, 9, 11), JellyfinVersionCompatibility.parse("10.9.11"))
        assertEquals(Triple(10, 8, 0), JellyfinVersionCompatibility.parse("10.8"))
        assertEquals(Triple(10, 10, 0), JellyfinVersionCompatibility.parse("10.10.0"))
        assertEquals(Triple(10, 9, 11), JellyfinVersionCompatibility.parse("10.9.11.5")) // build suffix ignored
        assertEquals(Triple(10, 9, 0), JellyfinVersionCompatibility.parse("10.9.0-beta1"))
        assertNull(JellyfinVersionCompatibility.parse(""))
        assertNull(JellyfinVersionCompatibility.parse(null))
        assertNull(JellyfinVersionCompatibility.parse("unknown"))
    }

    @Test
    fun defaultMinimumIsTenPointEight() {
        assertTrue(JellyfinVersionCompatibility.isSupported("10.8.0"))
        assertTrue(JellyfinVersionCompatibility.isSupported("10.9.11"))
        assertTrue(JellyfinVersionCompatibility.isSupported("10.10.3"))
        assertFalse(JellyfinVersionCompatibility.isSupported("10.7.7"))
        assertFalse(JellyfinVersionCompatibility.isSupported("9.9.9"))
        assertFalse(JellyfinVersionCompatibility.isSupported(null))
    }

    @Test
    fun customMinimumIsRespected() {
        assertTrue(JellyfinVersionCompatibility.isSupported("10.9.0", minimum = "10.9.0"))
        assertFalse(JellyfinVersionCompatibility.isSupported("10.8.9", minimum = "10.9.0"))
    }
}

class JellyfinTicksTest {

    @Test
    fun secondAndMillisecondConversions() {
        assertEquals(10_000_000L, JellyfinTicks.fromMilliseconds(1_000L))
        assertEquals(1_000L, JellyfinTicks.toMilliseconds(10_000_000L))
        assertEquals(0L, JellyfinTicks.fromMilliseconds(0L))
        // truncation for sub-millisecond precision is the documented direction
        assertEquals(1L, JellyfinTicks.fromMilliseconds(0).let { JellyfinTicks.toMilliseconds(15_000L) })
    }
}
