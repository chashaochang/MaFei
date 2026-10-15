package cn.mafei.cmp.player.api

/** Binary-search the current time window; a seek is independent of previous emissions. */
fun activeDanmaku(entries: List<DanmakuEntry>, positionMs: Long, durationMs: Long = 6000): List<Pair<Int, DanmakuEntry>> {
    if (positionMs < 0 || durationMs <= 0) return emptyList()
    var low = 0; var high = entries.size
    val earliest = (positionMs - durationMs).coerceAtLeast(0)
    while (low < high) { val mid = (low + high) ushr 1; if (entries[mid].timeMs < earliest) low = mid + 1 else high = mid }
    val visible = mutableListOf<Pair<Int, DanmakuEntry>>()
    var index = low
    while (index < entries.size && entries[index].timeMs <= positionMs && visible.size < 48) {
        if (entries[index].mode == 1 || entries[index].mode == 4 || entries[index].mode == 5 || entries[index].mode == 6) visible += index to entries[index]
        index++
    }
    return visible
}
