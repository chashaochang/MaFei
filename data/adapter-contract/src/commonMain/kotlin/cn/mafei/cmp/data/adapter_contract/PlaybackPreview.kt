package cn.mafei.cmp.data.adapter_contract

/** A real preview tile sheet returned by a provider, never a playable URL. */
data class PlaybackPreviewFrame(
    val encodedTile: ByteArray,
    val frameWidthPx: Int,
    val frameHeightPx: Int,
    val tileColumn: Int,
    val tileRow: Int,
) {
    init {
        require(encodedTile.isNotEmpty()) { "Playback preview tile must not be empty" }
        require(frameWidthPx > 0 && frameHeightPx > 0)
        require(tileColumn >= 0 && tileRow >= 0)
    }
}

/** Metadata needed to locate one frame inside a Jellyfin-style tile sheet. */
data class PlaybackPreviewInfo(
    val widthPx: Int,
    val heightPx: Int,
    val intervalMs: Long,
    val thumbnailCount: Int,
    val tileWidth: Int,
    val tileHeight: Int,
)

data class PlaybackPreviewLocation(
    val sheetIndex: Int,
    val tileColumn: Int,
    val tileRow: Int,
)

object PlaybackPreviewMath {
    /** Returns null for incomplete server metadata instead of guessing a frame. */
    fun locate(info: PlaybackPreviewInfo, positionMs: Long): PlaybackPreviewLocation? {
        if (info.widthPx <= 0 || info.heightPx <= 0 || info.intervalMs <= 0L ||
            info.thumbnailCount <= 0 || info.tileWidth <= 0 || info.tileHeight <= 0
        ) return null
        val thumbnail = (positionMs.coerceAtLeast(0L) / info.intervalMs)
            .coerceAtMost((info.thumbnailCount - 1).toLong())
            .toInt()
        val perSheet = info.tileWidth * info.tileHeight
        if (perSheet <= 0) return null
        val offset = thumbnail % perSheet
        return PlaybackPreviewLocation(
            sheetIndex = thumbnail / perSheet,
            tileColumn = offset % info.tileWidth,
            tileRow = offset / info.tileWidth,
        )
    }
}
