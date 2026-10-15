package cn.mafei.cmp.player.api

/** Raw DandanPlay comment fields mirrored from the legacy CommentData shape. */
data class DanmakuComment(
    val id: Long,
    val p: String,
    val message: String,
)

/** Platform-neutral result consumed by an OHOS/Compose danmaku renderer. */
data class DanmakuEntry(
    val id: Long,
    val timeMs: Long,
    val mode: Int,
    val color: Int,
    val text: String,
)

/** Parses the DandanPlay `p` field used by the legacy OHOS renderer. */
object DanmakuParser {
    /** Bilibili XML uses color at p[3]; DandanPlay JSON uses p[2]. */
    fun parseXml(xml: String): List<DanmakuEntry> {
        if (xml.length > 2 * 1024 * 1024 || xml.contains("<!DOCTYPE", ignoreCase = true)) return emptyList()
        return parse(Regex("<d\\s+[^>]*p\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</d>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).take(50_000).mapIndexed { index, match ->
                val fields = match.groupValues[1].split(',')
                val p = if (fields.size >= 8) "${fields[0]},${fields[1]},${fields[3]},${fields[7]}" else match.groupValues[1]
                DanmakuComment(index.toLong(), p, decodeXmlText(match.groupValues[2]))
            }.asIterable())
    }

    private fun decodeXmlText(value: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);").replace(value) { match ->
        when (val entity = match.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
            else -> {
                val code = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16) else entity.drop(1).toIntOrNull()
                when {
                    code == null || code !in 1..0x10FFFF || code in 0xD800..0xDFFF -> match.value
                    code <= 0xFFFF -> code.toChar().toString()
                    else -> { val point = code - 0x10000; "${(0xD800 + (point shr 10)).toChar()}${(0xDC00 + (point and 1023)).toChar()}" }
                }
            }
        }
    }
    fun parse(comments: Iterable<DanmakuComment>): List<DanmakuEntry> = comments.mapNotNull { comment ->
        val fields = comment.p.split(',')
        if (fields.size < 3) return@mapNotNull null
        val seconds = fields[0].toDoubleOrNull() ?: return@mapNotNull null
        val mode = fields[1].toIntOrNull() ?: return@mapNotNull null
        val color = parseColor(fields[2]) ?: return@mapNotNull null
        if (!seconds.isFinite() || seconds < 0.0 || seconds > Long.MAX_VALUE / 1000.0 || comment.message.isEmpty()) return@mapNotNull null
        DanmakuEntry(
            id = comment.id,
            timeMs = (seconds * 1000.0).toLong().coerceAtLeast(0L),
            mode = mode,
            color = color,
            text = comment.message,
        )
    }.sortedWith(compareBy<DanmakuEntry> { it.timeMs }.thenBy { it.id })

    private fun parseColor(raw: String): Int? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        return value.toIntOrNull() ?: value.removePrefix("0x").removePrefix("0X").toLongOrNull(16)?.toInt()
    }
}
