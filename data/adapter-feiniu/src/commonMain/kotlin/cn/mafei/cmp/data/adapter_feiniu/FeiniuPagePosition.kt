package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.PageCursor
import cn.mafei.cmp.core.domain.PageRequest

/** A page number is not a returned-item offset: a server may cap page size.
 * Keep wire page, requested size and observed count separate. No SDK/core change.
 * Private versioned format; repositories pass it through without parsing.
 */
internal data class FeiniuPagePosition(val page: Int = 1, val seen: Long = 0) {
    fun next(size: Int, returned: Int): PageCursor {
        if (page == Int.MAX_VALUE || seen > Long.MAX_VALUE - returned) {
            throw ClientConfigError("Feiniu page cursor overflow")
        }
        return PageCursor("v1:fnpage:${page + 1}:$size:${seen + returned}")
    }

    companion object {
        fun decode(request: PageRequest): FeiniuPagePosition {
            val raw = request.cursor?.value ?: return FeiniuPagePosition()
            val match = Regex("^v1:fnpage:([1-9][0-9]*):([1-9][0-9]*):([1-9][0-9]*)$").matchEntire(raw)
                ?: throw ClientConfigError("Invalid Feiniu page cursor")
            val page = match.groupValues[1].toIntOrNull()
            val size = match.groupValues[2].toIntOrNull()
            val seen = match.groupValues[3].toLongOrNull()
            if (page == null || page < 2 || size != request.pageSize || seen == null || seen < page - 1L) {
                throw ClientConfigError("Feiniu cursor overflow or page-size mismatch")
            }
            return FeiniuPagePosition(page, seen)
        }
    }
}
