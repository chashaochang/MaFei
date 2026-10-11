package cn.mafei.cmp.common.media

import androidx.compose.ui.graphics.ImageBitmap

/** UI-thread-only transformed artwork cache, owned by the account's [ArtworkMemoryCache]. */
internal class ArtworkBlurCache(
    private val maxBytes: Long = 16L * 1024 * 1024,
    private val maxEntries: Int = 8,
) {
    private class Entry(
        val source: ImageBitmap,
        val geometry: ArtworkBlurGeometry,
        val result: ImageBitmap,
        val bytes: Long,
    )

    private val entries = mutableListOf<Entry>()
    private var bytes = 0L

    init {
        require(maxBytes > 0 && maxEntries > 0)
    }

    fun get(source: ImageBitmap, geometry: ArtworkBlurGeometry): ImageBitmap? {
        val index = indexOf(source, geometry)
        if (index < 0) return null
        val entry = entries.removeAt(index)
        entries.add(entry)
        return entry.result
    }

    fun put(source: ImageBitmap, geometry: ArtworkBlurGeometry, result: ImageBitmap) {
        // Count the source too: this cache can outlive its entry in the decode cache.
        val cost = (source.width.toLong() * source.height + result.width.toLong() * result.height) * 4
        if (cost > maxBytes) return
        val existing = indexOf(source, geometry)
        if (existing >= 0) bytes -= entries.removeAt(existing).bytes
        while (entries.isNotEmpty() && (bytes + cost > maxBytes || entries.size >= maxEntries)) {
            bytes -= entries.removeAt(0).bytes
        }
        entries.add(Entry(source, geometry, result, cost))
        bytes += cost
    }

    private fun indexOf(source: ImageBitmap, geometry: ArtworkBlurGeometry): Int =
        entries.indexOfFirst { it.source === source && it.geometry == geometry }
}
