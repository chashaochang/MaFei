package cn.mafei.cmp.player.api

/** Engine requested by the legacy player settings. */
enum class PlayerEngineType {
    Auto,
    System,
    Mpv,
}

/** Provider/media facts needed by the engine resolver; no wire DTOs cross this boundary. */
data class PlayerEngineSourceFacts(
    val container: String? = null,
    val videoCodec: String? = null,
    val pixelFormat: String? = null,
    val videoRangeType: String? = null,
    val dolbyVisionProfile: Int? = null,
    val audioCodecs: List<String> = emptyList(),
    val externalSubtitleCodecs: List<String> = emptyList(),
)

data class PlayerEngineDecision(
    val engine: PlayerEngineType,
    val reason: String,
    val forceMpvHardwareDecode: Boolean = false,
    val forceMpvSoftwareDecode: Boolean = false,
)

/**
 * The old Harmony resolver's actual compatibility rules, expressed without ArkTS.
 * This is deliberately a pure decision function; a platform runtime must still
 * advertise that it can provide the selected engine before exposing the setting.
 */
object PlayerEngineResolver {
    private val systemContainers = setOf("mp4", "m4v", "mov", "mkv", "ts")
    private val systemVideoCodecs = setOf("h264", "avc", "hevc", "h265")
    private val systemAudioCodecs = setOf("aac", "mp3")
    private val systemSubtitleCodecs = setOf("srt", "vtt", "webvtt")

    fun resolve(
        requested: PlayerEngineType,
        source: PlayerEngineSourceFacts?,
        systemAvailable: Boolean,
    ): PlayerEngineDecision {
        if (requested == PlayerEngineType.Mpv) {
            return mpvDecision(source, "已按设置固定使用 MPV。")
        }
        if (source == null) {
            return mpvDecision(null, "播放器正在等待片源信息。")
        }
        val unsupported = systemUnsupportedReasons(source)
        if (requested == PlayerEngineType.System) {
            if (systemAvailable && unsupported.isEmpty()) {
                return PlayerEngineDecision(PlayerEngineType.System, "当前片源兼容系统播放器。")
            }
            val reason = if (!systemAvailable) "当前平台没有系统播放器实现" else unsupported.joinToString("；")
            return mpvDecision(source, "系统播放器不可用，已回退到 MPV：$reason")
        }
        if (systemAvailable && unsupported.isEmpty()) {
            return PlayerEngineDecision(PlayerEngineType.System, "自动选择系统播放器。")
        }
        return mpvDecision(
            source,
            if (unsupported.isEmpty()) "当前平台暂不提供系统播放器，自动选择 MPV。"
            else "自动回退到 MPV：${unsupported.joinToString("；")}",
        )
    }

    fun systemUnsupportedReasons(source: PlayerEngineSourceFacts): List<String> = buildList {
        val container = source.container.normalized()
        if (container !in systemContainers) add("视频封装 ${source.container ?: "unknown"} 不支持")
        val codec = source.videoCodec.normalized()
        if (codec !in systemVideoCodecs) add("视频编码 ${source.videoCodec ?: "unknown"} 不支持")
        val pixel = source.pixelFormat.normalized()
        if (pixel.contains("444") || pixel.contains("422") || pixel.contains("rgb") || pixel.contains("gbr")) {
            add("像素格式 ${source.pixelFormat ?: "unknown"} 不支持")
        }
        if (source.videoRangeType.normalized() in setOf("hdr vivid", "hdrvivid", "vivid")) {
            add("HDR Vivid 可能导致系统播放器黑屏")
        }
        if (source.audioCodecs.any { it.normalized() !in systemAudioCodecs }) {
            add("包含系统播放器不支持的音轨")
        }
        if (source.externalSubtitleCodecs.any { it.normalized() !in systemSubtitleCodecs }) {
            add("包含系统播放器不支持的字幕")
        }
    }

    private fun mpvDecision(source: PlayerEngineSourceFacts?, reason: String): PlayerEngineDecision {
        val pixel = source?.pixelFormat.normalized()
        val software = pixel.contains("444") || pixel.contains("422") || pixel.contains("rgb") || pixel.contains("gbr")
        return PlayerEngineDecision(
            engine = PlayerEngineType.Mpv,
            reason = reason,
            forceMpvHardwareDecode = !software,
            forceMpvSoftwareDecode = software,
        )
    }

    private fun String?.normalized(): String = this?.trim()?.lowercase().orEmpty()
}
