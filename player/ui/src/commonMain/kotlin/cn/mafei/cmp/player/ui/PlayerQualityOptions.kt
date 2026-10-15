package cn.mafei.cmp.player.ui

/**
 * One selectable quality ceiling in the player's 「清晰度」 panel — the CMP
 * equivalent of the legacy `QualityOption` (`{maxHeight, bitrate}` pairs the
 * server is asked to transcode to; `bitrate == 0` is the "auto" entry that
 * restores direct play).
 *
 * The ladder itself is client policy, not provider data, so it lives beside
 * the chrome exactly like the ArkUI `QualityOptionsProvider` did.
 */
data class PlayerQualityOption(
    val label: String,
    /** Bits per second requested from the provider; 0 = auto (no ceiling). */
    val bitrate: Int,
    val selected: Boolean = false,
)

object PlayerQualityOptions {

    /** 旧版 QualityOptionsProvider 的默认档位表（从高到低，末项是自动）。 */
    private val ladder = listOf(
        2160 to 120_000_000,
        2160 to 80_000_000,
        1080 to 60_000_000,
        1080 to 40_000_000,
        1080 to 20_000_000,
        1080 to 15_000_000,
        1080 to 10_000_000,
        720 to 8_000_000,
        720 to 6_000_000,
        720 to 4_000_000,
        480 to 3_000_000,
        480 to 1_500_000,
        480 to 720_000,
        360 to 420_000,
        0 to 0, // 自动
    )

    /**
     * 按视频尺寸裁剪档位（旧版 `getApplicableQualityOptions`）：先把非常规宽高比
     * 当作上下留边折算成等效宽度，再据此确定画面上限，最后从表尾向上收集所有不超过
     * 该上限的档位（自动档总在末尾）。
     */
    fun applicable(
        videoWidthPx: Int?,
        videoHeightPx: Int?,
        /**
         * Ceiling the PROVIDER reports as in effect (`PreparedStreamFacts.appliedBitrate`),
         * not the ceiling the user asked for: if the server direct-played anyway,
         * nothing is selected and the panel says 「自动」, which is the truth.
         */
        appliedBitrate: Int?,
    ): List<PlayerQualityOption> {
        val width = videoWidthPx?.takeIf { it > 0 }
        val height = videoHeightPx?.takeIf { it > 0 }
        val maxAllowedHeight = if (width == null || height == null) {
            ladder.maxOf { it.first }
        } else {
            val aspectRatio = width.toDouble() / height
            val maxAllowedWidth = if (aspectRatio < 16.0 / 9.0) height * 16 / 9 else width
            when {
                maxAllowedWidth >= 3800 -> 2160
                maxAllowedWidth >= 1900 -> 1080
                maxAllowedWidth >= 1260 -> 720
                maxAllowedWidth >= 620 -> 480
                else -> 360
            }
        }
        val effective = appliedBitrate ?: 0
        return ladder
            .filter { (maxHeight, _) -> maxHeight == 0 || maxHeight <= maxAllowedHeight }
            .map { (maxHeight, bitrate) ->
                PlayerQualityOption(
                    label = qualityLabel(maxHeight, bitrate),
                    bitrate = bitrate,
                    selected = bitrate == effective,
                )
            }
    }

    /**
     * Provider-declared renditions (fnOS hands out the upstream's own signed
     * ladder) rendered as panel entries, with the legacy 「自动」 entry in front
     * because "no ceiling" stays a legitimately different request.
     */
    fun fromProvider(
        renditions: List<Pair<String?, Int>>,
        appliedBitrate: Int?,
    ): List<PlayerQualityOption> {
        val applied = appliedBitrate ?: 0
        return buildList {
            add(PlayerQualityOption(label = "自动", bitrate = 0, selected = applied == 0))
            renditions.forEach { (name, bitrate) ->
                val detail = formatBitrate(bitrate)
                add(
                    PlayerQualityOption(
                        label = name?.takeIf { it.isNotBlank() }?.let { "$it  $detail" } ?: detail,
                        bitrate = bitrate,
                        selected = applied == bitrate,
                    ),
                )
            }
        }
    }

    /** Shared formatter so provider ladders and the synthesised ladder read alike. */
    fun formatBitrate(bitrate: Int): String = formatBitrateValue(bitrate)

    private fun qualityLabel(maxHeight: Int, bitrate: Int): String =
        if (maxHeight == 0 || bitrate == 0) "自动" else "${maxHeight}p  ${formatBitrateValue(bitrate)}"

    private fun formatBitrateValue(bitrate: Int): String = when {
        bitrate >= 1_000_000 -> {
            val mbps = bitrate / 1_000_000.0
            val text = if (mbps >= 10 || mbps % 1.0 == 0.0) {
                mbps.toInt().toString()
            } else {
                ((mbps * 10).toInt() / 10.0).toString()
            }
            "$text Mbps"
        }
        else -> "${bitrate / 1_000} Kbps"
    }
}
