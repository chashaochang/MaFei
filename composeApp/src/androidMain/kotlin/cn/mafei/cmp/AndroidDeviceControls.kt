package cn.mafei.cmp

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import cn.mafei.cmp.player.api.PlayerDeviceControls

/**
 * Android 亮度/音量（播放页左侧调亮度、右侧调音量）。
 *
 * 亮度走的是 **Activity 窗口属性**（`screenBrightness`），不是系统设置：旧版鸿蒙用的
 * `setWindowBrightness` 同样只影响播放页窗口，退出后系统亮度不变。窗口亮度为
 * `BRIGHTNESS_OVERRIDE_NONE`(-1，跟随系统) 时读不到具体值，按旧版回落到 0.5 —— 这样
 * 手势是从中间开始加减，而不是从黑屏起跳。
 *
 * 音量走 `STREAM_MUSIC`，与系统音量键同一条流；读不到时返回 null，手势那一侧就不介入。
 */
internal class AndroidDeviceControls(private val activity: Activity) : PlayerDeviceControls {

    private val audioManager: AudioManager? =
        activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun brightness(): Float? {
        val override = activity.window?.attributes?.screenBrightness ?: return null
        return if (override >= 0f) override else PlayerDeviceControls.DEFAULT_BRIGHTNESS
    }

    override fun setBrightness(value: Float) {
        val window = activity.window ?: return
        val attributes: WindowManager.LayoutParams = window.attributes
        attributes.screenBrightness = value.coerceIn(0f, 1f)
        window.attributes = attributes
    }

    override fun volume(): Int? = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun maxVolume(): Int = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    override fun setVolume(steps: Int) {
        val manager = audioManager ?: return
        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maximum <= 0) return
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, steps.coerceIn(0, maximum), 0)
    }
}
