package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.PageCursor

/**
 * Offset page-cursor codec shared by adapters that page by numeric offset
 * (Jellyfin `startIndex`). Encoded shape: `v1:off:<offset>` with a canonical
 * non-negative decimal offset that fits an [Int].
 *
 * Semantics (frozen):
 * - `cursor == null` means "first page"; the codec is never involved for it.
 * - [decode] rejects anything that is not exactly a cursor this codec
 *   produced — wrong version/prefix, signs, whitespace, leading zeros,
 *   non-numeric or out-of-range offsets — with [ClientConfigError]. It never
 *   silently falls back to offset 0, so a corrupt/garbage cursor can never
 *   restart the first page.
 * - Adapters whose provider pages differently (e.g. Feiniu 1-based pages)
 *   use their own strategy; this codec must not be forced onto them.
 */
data object OffsetPageCursorCodec {

    const val VERSION_PREFIX: String = "v1:off:"
    private val OFFSET = Regex("(0|[1-9][0-9]*)")

    fun encode(offset: Int): PageCursor {
        require(offset >= 0) { "Cursor offset must be non-negative but was $offset" }
        return PageCursor("$VERSION_PREFIX$offset")
    }

    /** Decodes [cursor] to its 0-based offset or throws [ClientConfigError] with the offending value. */
    fun decode(cursor: PageCursor): Int {
        val raw = cursor.value
        if (!raw.startsWith(VERSION_PREFIX)) {
            throw ClientConfigError("Unsupported page cursor '$raw': expected '$VERSION_PREFIX<offset>'")
        }
        val digits = raw.substring(VERSION_PREFIX.length)
        if (!OFFSET.matches(digits)) {
            throw ClientConfigError("Malformed page cursor '$raw': offset must be a canonical non-negative decimal")
        }
        val offset = digits.toLongOrNull()
            ?: throw ClientConfigError("Page cursor '$raw' offset is out of range")
        if (offset > Int.MAX_VALUE) {
            throw ClientConfigError("Page cursor '$raw' offset is out of range")
        }
        return offset.toInt()
    }
}
