package cn.mafei.cmp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color
import cn.mafei.cmp.session.ApplicationRuntime
import cn.mafei.cmp.session.ProcessApplication
import cn.mafei.cmp.storage.createAndroidSettings
import cn.mafei.cmp.platform.secure_storage_android.AndroidSecureCredentialStore
import cn.mafei.cmp.platform.http_android.createAndroidHttpTransport
import cn.mafei.cmp.player.runtime_android.createAndroidPlayerRuntime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val runtime = ProcessApplication.getOrCreate {
            ApplicationRuntime(
                createAndroidSettings(applicationContext),
                AndroidSecureCredentialStore(applicationContext),
                createAndroidHttpTransport(),
                playerRuntimeFactory = { createAndroidPlayerRuntime(applicationContext) },
            )
        }
        setContent {
            App(runtime, onThemeChanged = { dark ->
                // Probe theme can differ from the system theme. Let Activity's
                // edge-to-edge API select readable system icons for that theme.
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            })
        }
    }
}
