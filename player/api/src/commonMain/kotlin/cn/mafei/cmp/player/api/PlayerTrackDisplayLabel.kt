package cn.mafei.cmp.player.api

import cn.mafei.cmp.core.domain.mediaLanguageDisplayName

/** A native track without a title still has useful language/codec metadata. */
fun playerTrackDisplayLabel(
    id: String,
    kind: TrackKind,
    title: String?,
    language: String?,
    codec: String?,
    isExternal: Boolean = false,
): String {
    title?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val parts = buildList {
        language?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() !in setOf("und", "unknown") }
            ?.let { code -> add(mediaLanguageDisplayName(code).takeUnless { it == "未知" } ?: code) }
        codec?.trim()?.takeIf(String::isNotEmpty)?.let { add(it.uppercase()) }
        if (kind == TrackKind.Subtitle && isExternal) add("外挂")
    }
    if (parts.isNotEmpty()) return parts.joinToString(" · ")
    return when (kind) {
        TrackKind.Audio -> "音轨 $id"
        TrackKind.Subtitle -> "字幕 $id"
        TrackKind.Video -> "视频 $id"
    }
}
