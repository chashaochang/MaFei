package cn.mafei.cmp

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            }, playerSurface = { controller, _, _, _, modifier ->
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
     * Android phone/tablet uses the player button as an orientation toggle.
     * Window-level fullscreen belongs to the desktop hosts; keeping Android's
     * system bars visible also prevents the player from jumping under the
     * gesture/navigation area while the device rotates.
     */
    private fun applySystemBarPolicy() {
        // Edge-to-edge remains enabled for the shared CMP layout. Do not hide
        // bars here: on Android the button changes orientation only.
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
 * Player orientation policy, mirroring the ArkUI build's
 * `PlayerPage.toggleFullscreen` + `PlayerFullscreenHelper`:
 *
 * - outside the player: portrait;
 * - windowed player: **portrait** — on a phone the player page is portrait and
 *   全屏 is the only landscape mode (the legacy page only calls
 *   `enableFullscreen()` from the fullscreen toggle);
 * - fullscreen: sensor landscape, so the two landscapes can still flip;
 * - large screens keep their natural orientation outside the player and use
 *   sensor landscape while the player is fullscreen.
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
    playerScreenVisible && playerFullscreenRequested -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    !compactScreen -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}
