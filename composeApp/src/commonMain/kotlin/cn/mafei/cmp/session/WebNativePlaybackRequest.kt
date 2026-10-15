package cn.mafei.cmp.session

import kotlinx.serialization.Serializable
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions

/** Same fields/units as the old PlayOptions.fromJson / ExoPlayerPlugin. */
@Serializable
data class WebNativePlaybackRequest(
    val ids: List<String>, val startIndex: Int = 0, val mediaSourceId: String? = null,
    val startPositionTicks: Long? = null, val audioStreamIndex: Int? = null, val subtitleStreamIndex: Int? = null,
) {
    init {
        require(ids.isNotEmpty() && ids.size <= 100 && startIndex in ids.indices)
        require(ids.all { it.isNotBlank() && it.length <= 512 && it.none(Char::isISOControl) })
        require(startPositionTicks == null || startPositionTicks >= 0)
        require(audioStreamIndex == null || audioStreamIndex >= 0)
        require(subtitleStreamIndex == null || subtitleStreamIndex >= -1)
    }
    val startMs: Long get() = (startPositionTicks ?: 0) / 10_000L
    fun options() = PlaybackOptions(mediaSourceId = mediaSourceId, audioStreamIndex = audioStreamIndex,
        subtitleStreamIndex = subtitleStreamIndex, startPositionMs = startMs)
}
