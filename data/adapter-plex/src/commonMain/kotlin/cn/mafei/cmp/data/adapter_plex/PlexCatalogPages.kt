package cn.mafei.cmp.data.adapter_plex

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.plex.core.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal fun plexType(type: MediaType): String = when (type) {
    MediaType.Movie -> "1"
    MediaType.Series -> "2"
    MediaType.Season -> "3"
    MediaType.Episode -> "4"
    else -> throw ClientConfigError("Unsupported Plex video type")
}

/** PMS accepts one type per listing. Merge the sorted heads while retaining each raw offset. */
internal class PlexCatalogPages(private val client: PlexClient, private val mapper: PlexMapping) {
    suspend fun combined(path: String, request: PageRequest, query: Map<String, String>): Page<MediaItem> {
        val types = request.filter.mediaTypes.map(::plexType).sorted()
        val orderedQuery = if ("sort" in query) query else query + ("sort" to "titleSort:asc")
        val offsets = decode(request.cursor, path, types, orderedQuery)
        val sources = coroutineScope {
            types.mapIndexed { index, type -> async {
                val container = client.container(path, orderedQuery + mapOf(
                    "type" to type,
                    "X-Plex-Container-Start" to offsets[index].toString(),
                    "X-Plex-Container-Size" to request.pageSize.toString(),
                ))
                Source((container["Metadata"] as? JsonArray).orEmpty(), offsets[index],
                    container.number("totalSize")?.takeIf { it in 0..Int.MAX_VALUE }?.toInt(), request.pageSize)
            } }.map { it.await() }
        }
        val result = mutableListOf<MediaItem>()
        while (result.size < request.pageSize) {
            sources.forEach { it.skipInvalidRows() }
            // An exhausted window may hide a head newer than the other sources.
            if (sources.any { it.head == null && it.hasMore }) break
            val next = sources.filter { it.head != null }.minWithOrNull { a, b ->
                compare(requireNotNull(a.head), requireNotNull(b.head), request.sort)
            } ?: break
            val row = requireNotNull(next.head)
            next.consumed++
            mapper.item(row)?.takeIf { it.mediaType in request.filter.mediaTypes }?.let(result::add)
        }
        sources.forEach { it.skipInvalidRows() }
        val more = sources.any { it.head != null || it.hasMore }
        val cursor = if (more) PageCursor(Prefix + MafeiJson.Default.encodeToString(
            Cursor(path, types, sources.map { it.offset + it.consumed }, orderedQuery),
        )) else null
        val total = if (sources.all { it.total != null }) {
            sources.sumOf { requireNotNull(it.total).toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else null
        return Page(result, cursor, total)
    }

    private class Source(val rows: List<JsonElement>, val offset: Int, val total: Int?, val pageSize: Int) {
        var consumed = 0
        val head get() = rows.getOrNull(consumed) as? JsonObject
        val hasMore get() = rows.isNotEmpty() &&
            if (total != null) offset.toLong() + rows.size < total else rows.size >= pageSize

        fun skipInvalidRows() {
            while (consumed < rows.size && rows[consumed] !is JsonObject) consumed++
        }
    }

    private fun decode(cursor: PageCursor?, path: String, types: List<String>, query: Map<String, String>): List<Int> {
        if (cursor == null) return List(types.size) { 0 }
        val saved = try {
            if (!cursor.value.startsWith(Prefix)) throw IllegalArgumentException()
            MafeiJson.Default.decodeFromString<Cursor>(cursor.value.removePrefix(Prefix))
        } catch (_: Exception) {
            throw ClientConfigError("Invalid Plex combined catalog cursor")
        }
        if (saved.path != path || saved.types != types || saved.query != query ||
            saved.offsets.size != types.size || saved.offsets.any { it < 0 || it > Int.MAX_VALUE - PageRequest.MAX_PAGE_SIZE }
        ) throw ClientConfigError("Plex combined cursor belongs to a different query")
        return saved.offsets
    }

    private fun compare(a: JsonObject, b: JsonObject, sort: MediaSort?): Int {
        val field = sort?.field ?: MediaSortField.Default
        val key = when (field) {
            MediaSortField.Default, MediaSortField.Title -> "titleSort"
            MediaSortField.DateAdded -> "addedAt"
            MediaSortField.DatePlayed -> "lastViewedAt"
            MediaSortField.PremiereDate -> "originallyAvailableAt"
            MediaSortField.CommunityRating -> "rating"
            MediaSortField.ProductionYear -> "year"
            MediaSortField.PlayCount -> "viewCount"
            MediaSortField.Runtime -> "duration"
            MediaSortField.EpisodeNumber, MediaSortField.SeasonNumber -> "index"
            else -> throw ClientConfigError("Unsupported Plex combined sort")
        }
        val compared = if (field in setOf(MediaSortField.Default, MediaSortField.Title, MediaSortField.PremiereDate)) {
            fun value(row: JsonObject) = (row.text(key) ?: if (key == "titleSort") row.text("title") else null)
                .orEmpty().lowercase()
            value(a).compareTo(value(b))
        } else {
            (a.text(key)?.toDoubleOrNull() ?: 0.0).compareTo(b.text(key)?.toDoubleOrNull() ?: 0.0)
        }
        return if (sort?.direction == SortDirection.Descending) -compared else compared
    }

    @Serializable
    private data class Cursor(val path: String, val types: List<String>, val offsets: List<Int>, val query: Map<String, String>)

    private companion object { const val Prefix = "plex-combined-v1:" }
}
