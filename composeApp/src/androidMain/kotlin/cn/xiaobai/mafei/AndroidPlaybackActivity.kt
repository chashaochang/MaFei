package cn.xiaobai.mafei

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import cn.xiaobai.mafei.data.jellyfin.createJellyfinProvider
import cn.xiaobai.mafei.data.jellyfin.requireAndroidProvider
import cn.xiaobai.mafei.storage.AndroidAppContextHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import java.util.UUID

class AndroidPlaybackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContextHolder.initialize(applicationContext)
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty()
        if (streamUrl.isBlank()) {
            finish()
            return
        }
        val reportConfig = PlaybackReportConfig(
            serverBaseUrl = intent.getStringExtra(EXTRA_SERVER_BASE_URL),
            accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN),
            playbackItemId = intent.getStringExtra(EXTRA_PLAYBACK_ITEM_ID),
            playSessionId = intent.getStringExtra(EXTRA_PLAY_SESSION_ID),
            mediaSourceId = intent.getStringExtra(EXTRA_MEDIA_SOURCE_ID),
            streamTypeLabel = intent.getStringExtra(EXTRA_STREAM_TYPE_LABEL),
        )

        setContent {
            AndroidPlaybackScreen(
                streamUrl = streamUrl,
                title = title.ifBlank { "MaFei Player" },
                subtitle = subtitle,
                reportConfig = reportConfig,
                onBack = { finish() },
            )
        }
    }

    companion object {
        private const val EXTRA_STREAM_URL = "extra_stream_url"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_SUBTITLE = "extra_subtitle"
        private const val EXTRA_PLAYBACK_ITEM_ID = "extra_playback_item_id"
        private const val EXTRA_PLAY_SESSION_ID = "extra_play_session_id"
        private const val EXTRA_MEDIA_SOURCE_ID = "extra_media_source_id"
        private const val EXTRA_STREAM_TYPE_LABEL = "extra_stream_type_label"
        private const val EXTRA_SERVER_BASE_URL = "extra_server_base_url"
        private const val EXTRA_ACCESS_TOKEN = "extra_access_token"

        fun createIntent(
            context: Context,
            streamUrl: String,
            title: String,
            subtitle: String,
            playbackItemId: String?,
            playSessionId: String?,
            mediaSourceId: String?,
            streamTypeLabel: String?,
            serverBaseUrl: String?,
            accessToken: String?,
        ): Intent {
            return Intent(context, AndroidPlaybackActivity::class.java)
                .putExtra(EXTRA_STREAM_URL, streamUrl)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SUBTITLE, subtitle)
                .putExtra(EXTRA_PLAYBACK_ITEM_ID, playbackItemId)
                .putExtra(EXTRA_PLAY_SESSION_ID, playSessionId)
                .putExtra(EXTRA_MEDIA_SOURCE_ID, mediaSourceId)
                .putExtra(EXTRA_STREAM_TYPE_LABEL, streamTypeLabel)
                .putExtra(EXTRA_SERVER_BASE_URL, serverBaseUrl)
                .putExtra(EXTRA_ACCESS_TOKEN, accessToken)
        }
    }
}

private data class PlaybackReportConfig(
    val serverBaseUrl: String?,
    val accessToken: String?,
    val playbackItemId: String?,
    val playSessionId: String?,
    val mediaSourceId: String?,
    val streamTypeLabel: String?,
) {
    val itemId: UUID? get() = runCatching { UUID.fromString(playbackItemId.orEmpty()) }.getOrNull()
    val enabled: Boolean get() = !serverBaseUrl.isNullOrBlank() && !accessToken.isNullOrBlank() && itemId != null
}

private class JellyfinPlaybackReporter(
    private val config: PlaybackReportConfig,
) {
    fun reportStart(positionTicks: Long, paused: Boolean) {
        if (!config.enabled) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val api = createJellyfinProvider()
                    .requireAndroidProvider()
                    .jellyfin
                    .createApi(
                        baseUrl = config.serverBaseUrl!!,
                        accessToken = config.accessToken!!,
                    )
                api.playStateApi.reportPlaybackStart(
                    PlaybackStartInfo(
                        canSeek = true,
                        itemId = config.itemId!!,
                        mediaSourceId = config.mediaSourceId,
                        isPaused = paused,
                        isMuted = false,
                        positionTicks = positionTicks,
                        playMethod = config.streamTypeLabel.toPlayMethod(),
                        playSessionId = config.playSessionId,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                    )
                )
            }
        }
    }

    fun reportProgress(positionTicks: Long, paused: Boolean) {
        if (!config.enabled) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val api = createJellyfinProvider()
                    .requireAndroidProvider()
                    .jellyfin
                    .createApi(
                        baseUrl = config.serverBaseUrl!!,
                        accessToken = config.accessToken!!,
                    )
                api.playStateApi.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        canSeek = true,
                        itemId = config.itemId!!,
                        mediaSourceId = config.mediaSourceId,
                        isPaused = paused,
                        isMuted = false,
                        positionTicks = positionTicks,
                        playMethod = config.streamTypeLabel.toPlayMethod(),
                        playSessionId = config.playSessionId,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                    )
                )
            }
        }
    }

    fun reportStop(positionTicks: Long, failed: Boolean) {
        if (!config.enabled) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val api = createJellyfinProvider()
                    .requireAndroidProvider()
                    .jellyfin
                    .createApi(
                        baseUrl = config.serverBaseUrl!!,
                        accessToken = config.accessToken!!,
                    )
                api.playStateApi.reportPlaybackStopped(
                    PlaybackStopInfo(
                        itemId = config.itemId!!,
                        mediaSourceId = config.mediaSourceId,
                        positionTicks = positionTicks,
                        playSessionId = config.playSessionId,
                        failed = failed,
                    )
                )
            }
        }
    }
}

private fun String?.toPlayMethod(): PlayMethod {
    return when (this) {
        "转码流" -> PlayMethod.TRANSCODE
        "直出流" -> PlayMethod.DIRECT_STREAM
        else -> PlayMethod.DIRECT_PLAY
    }
}

@Composable
private fun AndroidPlaybackScreen(
    streamUrl: String,
    title: String,
    subtitle: String,
    reportConfig: PlaybackReportConfig,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }
    val reporter = remember(reportConfig) { JellyfinPlaybackReporter(reportConfig) }
    var stateLabel by remember { mutableStateOf("loading") }
    var errorLabel by remember { mutableStateOf<String?>(null) }
    var startedReported by remember { mutableStateOf(false) }
    var stopReported by remember { mutableStateOf(false) }
    var lastPositionTicks by remember { mutableLongStateOf(0L) }

    fun currentTicks(): Long = (exoPlayer.currentPosition.coerceAtLeast(0L)) * 10_000L

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                stateLabel = when (state) {
                    Player.STATE_IDLE -> "idle"
                    Player.STATE_BUFFERING -> "loading"
                    Player.STATE_READY -> if (exoPlayer.isPlaying) "playing" else "ready"
                    Player.STATE_ENDED -> "ended"
                    else -> "unknown"
                }
                lastPositionTicks = currentTicks()

                if (state == Player.STATE_READY && !startedReported) {
                    startedReported = true
                    reporter.reportStart(positionTicks = lastPositionTicks, paused = !exoPlayer.isPlaying)
                }
                if (state == Player.STATE_ENDED && !stopReported) {
                    stopReported = true
                    reporter.reportStop(positionTicks = lastPositionTicks, failed = false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                stateLabel = "error"
                errorLabel = error.message ?: "播放失败"
                lastPositionTicks = currentTicks()
                if (!stopReported) {
                    stopReported = true
                    reporter.reportStop(positionTicks = lastPositionTicks, failed = true)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (stateLabel != "error") {
                    stateLabel = if (isPlaying) "playing" else "paused"
                }
                lastPositionTicks = currentTicks()
                if (startedReported) {
                    reporter.reportProgress(positionTicks = lastPositionTicks, paused = !isPlaying)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            lastPositionTicks = currentTicks()
            if (!stopReported) {
                reporter.reportStop(positionTicks = lastPositionTicks, failed = stateLabel == "error")
                stopReported = true
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(startedReported) {
        if (!startedReported) return@LaunchedEffect
        while (isActive) {
            delay(15_000)
            lastPositionTicks = currentTicks()
            reporter.reportProgress(positionTicks = lastPositionTicks, paused = !exoPlayer.isPlaying)
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = {
                        lastPositionTicks = currentTicks()
                        if (!stopReported) {
                            reporter.reportStop(positionTicks = lastPositionTicks, failed = stateLabel == "error")
                            stopReported = true
                        }
                        onBack()
                    }) {
                        Text("返回")
                    }
                    Text("Android 播放", style = MaterialTheme.typography.titleLarge)
                }

                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "状态：$stateLabel${errorLabel?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (stateLabel) {
                        "error" -> Color(0xFFEF5350)
                        "playing" -> Color(0xFF66BB6A)
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    "回写：${if (reportConfig.enabled) "已启用" else "未启用（缺少会话上下文）"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "URL: $streamUrl",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }) {
                        Text(if (exoPlayer.isPlaying) "暂停" else "播放")
                    }
                    OutlinedButton(onClick = {
                        errorLabel = null
                        stopReported = false
                        startedReported = false
                        exoPlayer.seekTo(0L)
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }) {
                        Text("重试")
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            lastPositionTicks = currentTicks()
                            reporter.reportProgress(positionTicks = lastPositionTicks, paused = !exoPlayer.isPlaying)
                        }
                    }) {
                        Text("同步进度")
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    update = { view ->
                        view.player = exoPlayer
                    },
                )
            }
        }
    }
}
