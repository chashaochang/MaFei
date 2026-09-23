package cn.mafei.cmp.player.ui

import cn.mafei.cmp.player.api.PlayerContentMode

/**
 * 「画面比例」 choices in the legacy `PLAYER_CONTENT_MODE_OPTIONS` order.
 *
 * The order is user-visible: the panel is a plain top-to-bottom list whose
 * first entry is the default, so it must not be re-sorted by enum name.
 */
val playerContentModeChoices: List<PlayerContentMode> = listOf(
    PlayerContentMode.Fit,
    PlayerContentMode.Fill,
    PlayerContentMode.Original,
    PlayerContentMode.Ratio16x9,
    PlayerContentMode.Ratio4x3,
    PlayerContentMode.Ratio21x9,
)

/** Legacy labels, including the two ratio entries rendered as bare numbers. */
fun playerContentModeLabel(mode: PlayerContentMode): String = when (mode) {
    PlayerContentMode.Fit -> "适应"
    PlayerContentMode.Fill -> "填满"
    PlayerContentMode.Original -> "原始比例"
    PlayerContentMode.Ratio16x9 -> "16:9"
    PlayerContentMode.Ratio4x3 -> "4:3"
    PlayerContentMode.Ratio21x9 -> "21:9"
}

/**
 * Shape of the video AREA, resolved without any Compose dependency so the
 * rules stay unit-testable.
 *
 * The native surface only ever receives [PlayerContentMode.fills]; everything
 * else here is host geometry, which is why 16:9 / 4:3 / 21:9 behave the same on
 * every backend instead of needing per-provider negotiation.
 */
data class PlayerVideoArea(
    /** Fixed area shape (width/height), fitted inside the page. */
    val aspectRatio: Float? = null,
    /** 1:1 area in source pixels — 原始比例 — null when the size is unknown. */
    val oneToOnePixels: PixelSize? = null,
) {
    val fillsPage: Boolean get() = aspectRatio == null && oneToOnePixels == null
}

data class PixelSize(val width: Int, val height: Int)

fun playerVideoArea(
    mode: PlayerContentMode,
    sourceWidthPx: Int?,
    sourceHeightPx: Int?,
): PlayerVideoArea {
    mode.forcedAspectRatio?.let { ratio -> return PlayerVideoArea(aspectRatio = ratio) }
    if (mode != PlayerContentMode.Original) return PlayerVideoArea()
    val width = sourceWidthPx?.takeIf { it > 0 }
    val height = sourceHeightPx?.takeIf { it > 0 }
    return if (width != null && height != null) {
        PlayerVideoArea(oneToOnePixels = PixelSize(width, height))
    } else {
        // Unmeasurable source: showing it 1:1 is impossible, so degrade to the
        // page-filling area instead of guessing a size.
        PlayerVideoArea()
    }
}
