package cn.mafei.cmp.data.repositories

import cn.mafei.cmp.core.domain.*

/** One query/session's ordered page walk, owned by one caller coroutine.
 * Never parses cursor contents. Refresh/account/query changes require a new
 * instance or reset. append validates before changing state, so failures are
 * retryable without losing the last good page.
 */
class MediaPageAccumulator {
    private var started = false
    private var page = Page.empty<MediaItem>()
    private val seenCursors = mutableSetOf<PageCursor>()

    fun reset() { started = false; page = Page.empty(); seenCursors.clear() }

    fun append(requestCursor: PageCursor?, incoming: Page<MediaItem>): Page<MediaItem> {
        if ((!started && requestCursor != null) || (started && (page.nextCursor == null || requestCursor != page.nextCursor))) {
            throw ClientConfigError("Out-of-order media page")
        }
        if (incoming.nextCursor != null && (incoming.nextCursor == requestCursor || incoming.nextCursor in seenCursors)) {
            throw ProtocolDecodingError("Provider cursor cycle detected")
        }
        val owner = page.items.firstOrNull() ?: incoming.items.firstOrNull()
        if (owner != null && incoming.items.any { it.serverId != owner.serverId || it.provider != owner.provider }) {
            throw ClientConfigError("Cannot merge pages from different providers/servers")
        }
        val merged = linkedMapOf<MediaId, MediaItem>()
        page.items.forEach { merged[it.id] = it }
        incoming.items.forEach { merged[it.id] = it }
        val next = Page(merged.values.toList(), incoming.nextCursor, incoming.totalEstimate ?: page.totalEstimate)
        incoming.nextCursor?.let { seenCursors.add(it) }
        started = true
        page = next
        return next
    }
}
