package cn.xiaobai.mafei

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cn.xiaobai.mafei.data.AndroidJellyfinDetailRepository
import cn.xiaobai.mafei.data.AndroidJellyfinFavoritesRepository
import cn.xiaobai.mafei.data.AndroidJellyfinHomeRepository
import cn.xiaobai.mafei.data.AndroidJellyfinMediaViewRepository
import cn.xiaobai.mafei.data.AndroidJellyfinPlaybackRepository
import cn.xiaobai.mafei.data.AndroidJellyfinSearchRepository
import cn.xiaobai.mafei.logging.AppLogger
import cn.xiaobai.mafei.storage.AndroidAppContextHolder
import cn.xiaobai.mafei.storage.createAppPersistence

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.installDefault()
        AppLogger.info("Bootstrap", "MainActivity onCreate.")
        AndroidAppContextHolder.initialize(applicationContext)
        AppLogger.debug("Bootstrap", "MainActivity ensured app context is initialized.")
        window.setBackgroundDrawableResource(android.R.color.white)
        setContent {
            var showAppContent by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                AppLogger.debug("Bootstrap", "MainActivity waiting one frame before showing app content.")
                withFrameNanos { }
                showAppContent = true
                AppLogger.debug("Bootstrap", "MainActivity switched to app content.")
            }

            if (showAppContent) {
                val homeRepository = remember { AndroidJellyfinHomeRepository() }
                val detailRepository = remember { AndroidJellyfinDetailRepository() }
                val searchRepository = remember { AndroidJellyfinSearchRepository() }
                val playbackRepository = remember { AndroidJellyfinPlaybackRepository() }
                val favoritesRepository = remember { AndroidJellyfinFavoritesRepository() }
                val mediaViewRepository = remember { AndroidJellyfinMediaViewRepository() }
                val appPersistence = remember { createAppPersistence() }
                App(
                    homeRepository = homeRepository,
                    detailRepository = detailRepository,
                    searchRepository = searchRepository,
                    playbackRepository = playbackRepository,
                    favoritesRepository = favoritesRepository,
                    mediaViewRepository = mediaViewRepository,
                    onOpenPlayback = { context ->
                        val servers = appPersistence.loadServers()
                        val defaultServer = servers.firstOrNull { it.isDefault }
                        val session = appPersistence.loadSession()
                        startActivity(
                            AndroidPlaybackActivity.createIntent(
                                context = this@MainActivity,
                                streamUrl = context.streamUrl,
                                title = context.showTitle,
                                subtitle = context.episodeTitle,
                                playbackItemId = context.playbackItemId,
                                playSessionId = context.playSessionId,
                                mediaSourceId = context.mediaSourceId,
                                streamTypeLabel = context.streamTypeLabel,
                                serverBaseUrl = defaultServer?.baseUrl,
                                accessToken = session?.accessToken,
                            )
                        )
                    },
                )
            } else {
                AndroidLaunchPlaceholder()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AndroidLaunchPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F3EE)),
    )
}
