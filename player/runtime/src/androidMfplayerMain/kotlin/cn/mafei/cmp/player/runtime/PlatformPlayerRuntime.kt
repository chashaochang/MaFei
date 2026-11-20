package cn.mafei.cmp.player.runtime

import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.runtime_android.AndroidSurfaceRegistry
import org.mfplayer.cmp.android.AndroidPlayerAdapter
import org.mfplayer.cmp.android.RealAndroidPlayerDriver

val usesSdkAndroidVideoSurface: Boolean get() = true

fun createPlayerRuntime(context: Context, surfaces: AndroidSurfaceRegistry): PlayerRuntime {
    lateinit var driver: RealAndroidPlayerDriver
    var mounted: FrameLayout? = null
    return MfPlayerRuntime(
        capabilities = PlayerCapabilities(contentMode = true),
        androidRc1LoadCompatibility = true,
        createEngine = {
            driver = RealAndroidPlayerDriver(context.applicationContext)
            AndroidPlayerAdapter(driver)
        },
        surface = { engine, handle, attach ->
            val adapter = engine as AndroidPlayerAdapter
            if (attach) {
                val container = surfaces.surfaceOf(handle) as? FrameLayout
                if (container != null) {
                    val video = driver.createVideoSurfaceView(container.context)
                    container.addView(video, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                    mounted = container
                    adapter.onVideoSurfaceAttached()
                }
            } else {
                mounted?.let { container ->
                    container.removeAllViews()
                    mounted = null
                    adapter.onVideoSurfaceDetached()
                }
            }
        },
    )
}
