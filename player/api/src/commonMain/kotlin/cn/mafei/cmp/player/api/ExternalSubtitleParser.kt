package cn.mafei.cmp.player.api

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/** Text delivery formats. ASS positioning/styles and bitmap subtitles remain native-engine capabilities. */
object ExternalSubtitleParser {
    fun parse(document: String): List<SubtitleCue> {
        val text = document.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        return if (text.contains("[Events]", ignoreCase = true)) parseAss(text) else parseTimedText(text)
    }

    private fun parseTimedText(text: String): List<SubtitleCue> = text.split(Regex("\n[ \t]*\n"))
        .asSequence().mapNotNull { block ->
            val lines = block.lines()
            val index = lines.indexOfFirst { "-->" in it }
            if (index < 0) return@mapNotNull null
            val range = lines[index].split("-->", limit = 2)
            val start = timestamp(range[0].trim()) ?: return@mapNotNull null
            val end = timestamp(range[1].trim().substringBefore(' ')) ?: return@mapNotNull null
            cue(start, end, lines.drop(index + 1).joinToString("\n"))
        }.take(40_000).sortedBy { it.startMs }.toList()

    private fun parseAss(text: String): List<SubtitleCue> {
        var fields = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        var events = false
        val result = mutableListOf<SubtitleCue>()
        for (line in text.lineSequence()) {
            if (line.startsWith('[')) events = line.trim().equals("[Events]", ignoreCase = true)
            if (!events) continue
            if (line.startsWith("Format:", ignoreCase = true)) fields = line.substringAfter(':').split(',').map { it.trim().lowercase() }
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue
            // Text is the final ASS field and may itself contain commas.
            if (fields.lastOrNull() != "text") continue
            val values = line.substringAfter(':').trim().split(',', limit = fields.size)
            fun field(name: String) = fields.indexOf(name).takeIf { it >= 0 }?.let { values.getOrNull(it) }
            val start = field("start")?.let(::timestamp) ?: continue
            val end = field("end")?.let(::timestamp) ?: continue
            val body = field("text")?.replace(Regex("\\{[^}]*}"), "")?.replace("\\N", "\n")?.replace("\\n", "\n") ?: continue
            cue(start, end, body)?.let(result::add)
            if (result.size >= 40_000) break
        }
        return result.sortedBy { it.startMs }
    }

    private fun cue(start: Long, end: Long, raw: String): SubtitleCue? {
        if (start < 0 || end <= start) return null
        val text = raw.replace(Regex("<[^>]*>"), "").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&nbsp;", " ").replace("&amp;", "&").trim().take(4096)
        return text.takeIf { it.isNotBlank() }?.let { SubtitleCue(start, end, it) }
    }

    private fun timestamp(raw: String): Long? {
        val parts = raw.trim().replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val seconds = parts.last().toDoubleOrNull() ?: return null
        if (hours !in 0..9999 || minutes !in 0..59 || !seconds.isFinite() || seconds < 0 || seconds >= 60) return null
        return hours * 3_600_000 + minutes * 60_000 + (seconds * 1000).toLong()
    }
}

/** Binary indexed overlapping cues, also correct after seeking backwards. */
class SubtitleTimeline(private val cues: List<SubtitleCue>) {
    private val maximumEnd = LongArray(cues.size)
    init {
        var end = 0L
        cues.forEachIndexed { index, cue -> end = maxOf(end, cue.endMs); maximumEnd[index] = end }
    }
    fun textAt(positionMs: Long): String {
        var low = 0; var high = cues.size
        while (low < high) { val middle = (low + high) / 2; if (cues[middle].startMs <= positionMs) low = middle + 1 else high = middle }
        val to = low
        low = 0; high = to
        while (low < high) { val middle = (low + high) / 2; if (maximumEnd[middle] <= positionMs) low = middle + 1 else high = middle }
        return cues.subList(low, to).asSequence().filter { positionMs < it.endMs }.map { it.text }.distinct().take(4).joinToString("\n")
    }
}
