package cn.mafei.cmp.player.ui

import kotlin.math.roundToInt

/** Legacy `PLAYER_MIN_BRIGHTNESS`: the gesture never drives the screen fully dark. */
const val PlayerMinBrightness = 0.05f

/** Which half of the video was grabbed; the legacy split is exactly one half. */
enum class PlayerGestureLevel { Brightness, Volume }

/**
 * The legacy gesture HUD (`PlayerGestureFeedback`). One at a time, exactly like
 * the ArkUI enum it mirrors — the three shapes never share the screen.
 */
sealed interface PlayerGestureFeedback {
    /** Centred card: 时间 / 总时长 + a progress bar in the accent colour. */
    data class Seek(val targetMs: Long, val durationMs: Long) : PlayerGestureFeedback

    /** Top pill: icon + level bar, for 亮度 / 音量. */
    data class Level(val level: PlayerGestureLevel, val progress: Float) : PlayerGestureFeedback

    /** Top pill: 3X 快进中 while a long press holds the speed up. */
    data object Speed : PlayerGestureFeedback
}

/** Legacy `gestureStartX < widthVp / 2`. */
fun playerGestureLevelFor(startX: Float, widthPx: Float): PlayerGestureLevel =
    if (startX < widthPx / 2f) PlayerGestureLevel.Brightness else PlayerGestureLevel.Volume

/**
 * Legacy brightness ramp: `clamp(start + percent, 0.05, 1)` where `percent` is
 * the fraction of the video HEIGHT the finger travelled (up = brighter).
 */
fun playerGestureBrightness(startBrightness: Float, deltaFraction: Float): Float =
    (startBrightness + deltaFraction).coerceIn(PlayerMinBrightness, 1f)

/**
 * Legacy volume ramp: `clamp(start + max * percent, 0, max)` steps.
 * A platform reporting [PlayerDeviceControls.maxVolume] 0 cannot express a
 * volume at all, so the result stays 0 and callers must not engage the gesture.
 */
fun playerGestureVolumeSteps(startSteps: Int, maxSteps: Int, deltaFraction: Float): Int =
    if (maxSteps <= 0) 0 else (startSteps + maxSteps * deltaFraction).roundToInt().coerceIn(0, maxSteps)

/** Bar fraction the legacy HUD renders for a level gesture. */
fun playerGestureLevelProgress(level: PlayerGestureLevel, brightness: Float, steps: Int, maxSteps: Int): Float =
    when (level) {
        PlayerGestureLevel.Brightness -> brightness.coerceIn(0f, 1f)
        PlayerGestureLevel.Volume -> if (maxSteps <= 0) 0f else (steps.toFloat() / maxSteps).coerceIn(0f, 1f)
    }

/**
 * Legacy long-press HUD text (`PLAYER_LONG_PRESS_SPEED` is the integer 3 there,
 * so the label reads "3X 快进中" and never "3.0X").
 */
fun playerSpeedFeedbackText(speed: Float): String = "${trimPlayerFraction(speed.toDouble())}X 快进中"
