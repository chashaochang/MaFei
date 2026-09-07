package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.PageCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OffsetPageCursorCodecTest {

    // ------------------------------------------------------------ valid shapes

    @Test
    fun encodesCanonicalOffsets() {
        assertEquals("v1:off:0", OffsetPageCursorCodec.encode(0).value)
        assertEquals("v1:off:50", OffsetPageCursorCodec.encode(50).value)
        assertEquals("v1:off:${Int.MAX_VALUE}", OffsetPageCursorCodec.encode(Int.MAX_VALUE).value)
    }

    @Test
    fun roundTripsBoundaryOffsets() {
        for (offset in listOf(0, 1, 49, 50, 499, Int.MAX_VALUE / 2, Int.MAX_VALUE)) {
            assertEquals(offset, OffsetPageCursorCodec.decode(OffsetPageCursorCodec.encode(offset)))
        }
    }

    @Test
    fun encodesOnlyNonNegativeOffsets() {
        assertFailsWith<IllegalArgumentException> { OffsetPageCursorCodec.encode(-1) }
        assertFailsWith<IllegalArgumentException> { OffsetPageCursorCodec.encode(Int.MIN_VALUE) }
    }

    // ------------------------------------------------------- rejected cursors

    @Test
    fun rejectsForeignPrefixesAndVersions() {
        for (raw in listOf(
            "v2:off:0", // future version
            "v1:page:0", // different scheme under same version
            "V1:off:0", // case-sensitive version prefix
            "v1:OFF:0",
            "v0:off:0",
            "off:0",
            "0",
            "opaque-provider-token",
            "v1:off", // missing offset segment
            "v1:off:0:", // trailing separator
            " v1:off:0", // leading whitespace
        )) {
            // Rejection carries the offending cursor; never a silent restart hint.
            val error = assertFailsWith<ClientConfigError>("cursor '$raw' must be rejected") {
                OffsetPageCursorCodec.decode(PageCursor(raw))
            }
            assertEquals(true, error.message?.contains(raw) == true)
        }
    }

    @Test
    fun rejectsMalformedOffsets() {
        for (raw in listOf(
            "v1:off:", // empty offset
            "v1:off:abc", // non-numeric
            "v1:off:-1", // negative
            "v1:off:+1", // explicit sign
            "v1:off: 1", // inner space
            "v1:off:1 ", // trailing space
            "v1:off:007", // non-canonical leading zeros
            "v1:off:0x10",
            "v1:off:1.5",
            "v1:off:1:2",
            "v1:off:１", // fullwidth digit
        )) {
            assertFailsWith<ClientConfigError>("cursor '$raw' must be rejected") {
                OffsetPageCursorCodec.decode(PageCursor(raw))
            }
        }
    }

    @Test
    fun rejectsOutOfRangeOffsets() {
        // One past Int.MAX_VALUE and far past Long range — both must be
        // rejected, never truncated or clamped to a small first-page offset.
        assertFailsWith<ClientConfigError> { OffsetPageCursorCodec.decode(PageCursor("v1:off:2147483648")) }
        assertFailsWith<ClientConfigError> { OffsetPageCursorCodec.decode(PageCursor("v1:off:99999999999999999999")) }
    }

    @Test
    fun rejectionIsClientConfigErrorWithCursorContext() {
        val error = assertFailsWith<ClientConfigError> {
            OffsetPageCursorCodec.decode(PageCursor("v2:off:7"))
        }
        assertEquals(true, error.message?.contains("v2:off:7"))
    }

    // -------------------------------------------------------------- PageCursor

    @Test
    fun pageCursorRejectsBlankValues() {
        assertFailsWith<IllegalArgumentException> { PageCursor("") }
        assertFailsWith<IllegalArgumentException> { PageCursor("   ") }
    }

    @Test
    fun decodeIsDeterministic() {
        val cursor = OffsetPageCursorCodec.encode(7)
        assertEquals(7, OffsetPageCursorCodec.decode(cursor))
        assertEquals(7, OffsetPageCursorCodec.decode(cursor))
    }
}
