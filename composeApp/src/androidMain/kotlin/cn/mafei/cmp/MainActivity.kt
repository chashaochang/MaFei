package cn.mafei.cmp

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cn.mafei.cmp.session.ApplicationRuntime
import cn.mafei.cmp.session.ProcessApplication
import cn.mafei.cmp.storage.createAndroidSettings
import cn.mafei.cmp.platform.secure_storage_android.AndroidSecureCredentialStore
import cn.mafei.cmp.platform.http_android.createAndroidHttpTransport

class MainActivity : ComponentActivity() {
    private var playerScreenVisible = false
    private var playerFullscreenRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        playerScreenVisible = savedInstanceState?.getBoolean(PlayerScreenVisibleKey) == true
        playerFullscreenRequested = savedInstanceState?.getBoolean(PlayerFullscreenKey) == true
        applyOrientationPolicy()
        applySystemBarPolicy()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val playerHost = AndroidPlayerHost.getOrCreate(applicationContext)
        val runtime = ProcessApplication.getOrCreate {
            ApplicationRuntime(
                createAndroidSettings(applicationContext),
                AndroidSecureCredentialStore(applicationContext),
                createAndroidHttpTransport(),
                playerRuntimeFactory = { playerHost.createRuntime() },
                // 播放页左侧调亮度、右侧调音量；窗口亮度与 STREAM_MUSIC 都归属本 Activity。
                deviceControls = AndroidDeviceControls(this),
            )
        }
        setContent {
            App(runtime, onThemeChanged = { dark ->
                // Probe theme can differ from the system theme. Let Activity's
                // edge-to-edge API select readable system icons for that theme.
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }, playerSurface = { controller, _, _, modifier ->
                playerHost.Surface(controller, modifier)
            }, onPlayerScreenChanged = { visible ->
                if (playerScreenVisible != visible) {
                    playerScreenVisible = visible
                    if (!visible) playerFullscreenRequested = false
                    applyOrientationPolicy()
                    applySystemBarPolicy()
                }
            }, onPlayerFullscreenChanged = { fullscreen ->
                if (playerFullscreenRequested != fullscreen) {
                    playerFullscreenRequested = fullscreen
                    applyOrientationPolicy()
                    applySystemBarPolicy()
                }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PlayerScreenVisibleKey, playerScreenVisible)
        outState.putBoolean(PlayerFullscreenKey, playerFullscreenRequested)
        super.onSaveInstanceState(outState)
    }

    /**
     * 全屏要真的收掉系统栏。旧版 `enterFullscreen()` 就是
     * `setWindowSystemBarEnable([])`（`restoreSystemBars()` 恢复）—— 平板上全屏不旋转，
     * 系统栏的收放才是「全屏」唯一可见的形态变化，不收就成了一个点了没反应的按钮。
     */
    private fun applySystemBarPolicy() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (playerScreenVisible && playerFullscreenRequested) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyOrientationPolicy() {
        requestedOrientation = mafeiRequestedOrientation(
            compactScreen = isCompactScreen(),
            playerScreenVisible = playerScreenVisible,
            playerFullscreenRequested = playerFullscreenRequested,
        )
    }

    private fun isCompactScreen(): Boolean {
        val configuration = resources.configuration
        val smallestWidthDp = configuration.smallestScreenWidthDp
            .takeUnless { it == Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED || it <= 0 }
            ?: configuration.screenWidthDp
        return smallestWidthDp < CompactScreenWidthDp
    }

    private companion object {
        const val CompactScreenWidthDp = 600
        const val PlayerScreenVisibleKey = "mafei.player-screen-visible"
        const val PlayerFullscreenKey = "mafei.player-fullscreen"
    }
}

/**
 * Compact-screen orientation policy, mirroring the ArkUI build's
 * `PlayerPage.toggleFullscreen` + `PlayerFullscreenHelper`:
 *
 * - outside the player: portrait;
 * - windowed player: **portrait** — on a phone the player page is portrait and
 *   全屏 is the only landscape mode (the legacy page only calls
 *   `enableFullscreen()` from the fullscreen toggle);
 * - fullscreen: sensor landscape, so the two landscapes can still flip.
 *
 * The windowed case used to be FULL_SENSOR, which made "exit fullscreen" a
 * no-op on a phone held sideways: nothing can snap the window back to portrait
 * while the policy keeps following the sensor.
 */
internal fun mafeiRequestedOrientation(
    compactScreen: Boolean,
    playerScreenVisible: Boolean,
    playerFullscreenRequested: Boolean = false,
): Int = when {
    !compactScreen -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    playerScreenVisible && playerFullscreenRequested -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}
