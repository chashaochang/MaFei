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

    override fun onCreate(savedInstanceState: Bundle?) {
        playerScreenVisible = savedInstanceState?.getBoolean(PlayerScreenVisibleKey) == true
        applyOrientationPolicy()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val playerHost = AndroidPlayerHost.getOrCreate(applicationContext)
        val runtime = ProcessApplication.getOrCreate {
            ApplicationRuntime(
                createAndroidSettings(applicationContext),
                AndroidSecureCredentialStore(applicationContext),
                createAndroidHttpTransport(),
                playerRuntimeFactory = { playerHost.createRuntime() },
            )
        }
        setContent {
            App(runtime, onThemeChanged = { dark ->
                // Probe theme can differ from the system theme. Let Activity's
                // edge-to-edge API select readable system icons for that theme.
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }, playerSurface = { controller, modifier ->
                playerHost.Surface(controller, modifier)
            }, onPlayerScreenChanged = { visible ->
                if (playerScreenVisible != visible) {
                    playerScreenVisible = visible
                    applyOrientationPolicy()
                }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PlayerScreenVisibleKey, playerScreenVisible)
        super.onSaveInstanceState(outState)
    }

    private fun applyOrientationPolicy() {
        requestedOrientation = mafeiRequestedOrientation(isCompactScreen(), playerScreenVisible)
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
    }
}

internal fun mafeiRequestedOrientation(compactScreen: Boolean, playerScreenVisible: Boolean): Int = when {
    !compactScreen -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    playerScreenVisible -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}
