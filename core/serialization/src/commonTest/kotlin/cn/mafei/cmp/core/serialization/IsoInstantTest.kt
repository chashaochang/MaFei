package cn.mafei.cmp.core.serialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IsoInstantTest {

    @Test
    fun parsesUtcInstantsWithAndWithoutFraction() {
        assertEquals(0L, parseIsoInstantToEpochMs("1970-01-01T00:00:00Z"))
        assertEquals(1_704_067_200_000L, parseIsoInstantToEpochMs("2024-01-01T00:00:00Z"))
        assertEquals(1_704_067_200_500L, parseIsoInstantToEpochMs("2024-01-01T00:00:00.5Z"))
        assertEquals(1_704_067_200_123L, parseIsoInstantToEpochMs("2024-01-01T00:00:00.123Z"))
        assertEquals(1_704_067_200_123L, parseIsoInstantToEpochMs("2024-01-01T00:00:00.1234567Z"))
    }

    @Test
    fun parsesOffsetInstant() {
        // 2024-01-01T08:00:00+08:00 == 2024-01-01T00:00:00Z
        assertEquals(1_704_067_200_000L, parseIsoInstantToEpochMs("2024-01-01T08:00:00+08:00"))
        // 2024-01-01T00:00:00-05:00 == 2024-01-01T05:00:00Z
        assertEquals(1_704_085_200_000L, parseIsoInstantToEpochMs("2024-01-01T00:00:00-05:00"))
        // compact offset without colon
        assertEquals(1_704_067_200_000L, parseIsoInstantToEpochMs("2024-01-01T08:00:00+0800"))
        // offset with hour only
        assertEquals(1_704_067_200_000L, parseIsoInstantToEpochMs("2024-01-01T08:00:00+08"))
    }

    @Test
    fun parsesSpaceSeparatedAndLowercaseT() {
        assertEquals(1_704_067_200_123L, parseIsoInstantToEpochMs("2024-01-01 00:00:00.123Z"))
        assertEquals(1_704_067_200_000L, parseIsoInstantToEpochMs("2024-01-01t00:00:00z"))
    }

    @Test
    fun parsesDateOnlyAsUtcMidnight() {
        assertEquals(1_704_067_200_000L, parseIsoInstantToEpochMs("2024-01-01"))
    }

    @Test
    fun parsesPreEpochDates() {
        assertEquals(0L, parseIsoInstantToEpochMs("1970-01-01T00:00:00-00:00"))
        assertEquals(-86_400_000L, parseIsoInstantToEpochMs("1969-12-31T00:00:00Z"))
        assertEquals(-1L, parseIsoInstantToEpochMs("1969-12-31T23:59:59.999Z"))
    }

    @Test
    fun parsesOmittedSeconds() {
        assertEquals(1_704_067_200_000L, parseIsoInstantToEpochMs("2024-01-01T00:00Z"))
    }

    @Test
    fun rejectsMalformedInput() {
        assertNull(parseIsoInstantToEpochMs(""))
        assertNull(parseIsoInstantToEpochMs("nonsense"))
        assertNull(parseIsoInstantToEpochMs("2024-13-01T00:00:00Z"))
        assertNull(parseIsoInstantToEpochMs("2024-01-32T00:00:00Z"))
        assertNull(parseIsoInstantToEpochMs("2024-02-30T00:00:00Z"))
        assertNull(parseIsoInstantToEpochMs("2024-01-01T25:00:00Z"))
        assertNull(parseIsoInstantToEpochMs("2024-01-01T00:61:00Z"))
        assertNull(parseIsoInstantToEpochMs("2024-01-01T00:00:00"))
        assertNull(parseIsoInstantToEpochMs("2024-01-01T00:00:00+99:00"))
    }

    @Test
    fun handlesLeapYears() {
        assertEquals(1_709_164_800_000L, parseIsoInstantToEpochMs("2024-02-29T00:00:00Z"))
        assertNull(parseIsoInstantToEpochMs("2023-02-29T00:00:00Z"))
        assertNull(parseIsoInstantToEpochMs("1900-02-29T00:00:00Z"))
        assertEquals(-2_203_891_200_000L, parseIsoInstantToEpochMs("1900-03-01T00:00:00Z"))
    }

    @Test
    fun formatsCanonicalUtcInstant() {
        assertEquals("1970-01-01T00:00:00Z", formatIsoInstant(0L))
        assertEquals("2024-01-01T00:00:00Z", formatIsoInstant(1_704_067_200_000L))
        assertEquals("2024-01-01T00:00:00.123Z", formatIsoInstant(1_704_067_200_123L))
        assertEquals("1969-12-31T23:59:59.999Z", formatIsoInstant(-1L))
        assertEquals("2024-01-01T12:34:56.789Z", formatIsoInstant(1_704_112_496_789L))
    }

    @Test
    fun formatAndParseRoundTrip() {
        for (epochMs in longArrayOf(0L, 1L, 999L, 1_000L, -1L, -1_000L, 1_704_067_200_123L, 4_102_444_799_999L)) {
            assertEquals(epochMs, parseIsoInstantToEpochMs(formatIsoInstant(epochMs)), "round trip failed for $epochMs")
        }
    }
}
