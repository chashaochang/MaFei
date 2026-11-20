package cn.mafei.cmp.player.runtime_macos

import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.TrackKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class NativeTrack(val id: String, val kind: String, val label: String, val language: String, val selected: Boolean)

internal fun decodeNativeTracks(value: String): List<PlayerTrack> = Json.decodeFromString<List<NativeTrack>>(value).map {
    PlayerTrack(it.id, TrackKind.valueOf(it.kind), it.label, it.language, it.selected)
}
