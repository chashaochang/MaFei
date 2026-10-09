package cn.mafei.cmp

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import android.net.Uri
import cn.mafei.cmp.player.api.PlayerPhase

/** User-started music only. START_NOT_STICKY never restores credentials or resumes after process death. */
class AndroidMusicService : Service() {
    private var token = 0L
    private lateinit var session: MediaSession
    private lateinit var audio: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var noisyRegistered = false
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) AndroidMusicPlayback.command("pause", token = token)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        session = MediaSession(this, "MaFeiMusic")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { AndroidMusicPlayback.command("play", token = token) }
            override fun onPause() { AndroidMusicPlayback.command("pause", token = token) }
            override fun onStop() { AndroidMusicPlayback.command("stop", token = token) }
            override fun onSeekTo(pos: Long) { AndroidMusicPlayback.command("seek", pos, token) }
            override fun onSkipToNext() { AndroidMusicPlayback.command("next", token = token) }
            override fun onSkipToPrevious() { AndroidMusicPlayback.command("previous", token = token) }
        }, Handler(Looper.getMainLooper()))
        session.setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Channel, "音乐播放", NotificationManager.IMPORTANCE_LOW))
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, RECEIVER_EXPORTED) else registerReceiver(noisy, filter)
        noisyRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.getLongExtra(TokenExtra, 0) ?: 0
        if (requested == 0L || requested != AndroidMusicPlayback.token) {
            if (AndroidMusicPlayback.token == 0L) stopSelf(startId)
            return START_NOT_STICKY
        }
        intent?.getStringExtra(ActionExtra)?.let { action ->
            AndroidMusicPlayback.command(action, token = requested)
            return START_NOT_STICKY
        }
        abandonFocus()
        token = requested
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NotificationId, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NotificationId, notification())
            session.isActive = true
            val focusedToken = token
            val listener = AudioManager.OnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> AndroidMusicPlayback.focus(focusedToken, true)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AndroidMusicPlayback.focus(focusedToken, false, true)
                    AudioManager.AUDIOFOCUS_LOSS -> AndroidMusicPlayback.focus(focusedToken, false)
                }
            }
            focusListener = listener
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            if (Build.VERSION.SDK_INT >= 26) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper())).build()
                focusRequest = request
            }
            AndroidMusicPlayback.serviceAttached(token, ::update, ::requestPlaybackFocus)
            requestPlaybackFocus()
            update()
        } catch (_: SecurityException) { AndroidMusicPlayback.focus(token, false); stopSelf(startId) }
        catch (_: IllegalStateException) { AndroidMusicPlayback.focus(token, false); stopSelf(startId) }
        return START_NOT_STICKY
    }

    private fun requestPlaybackFocus() {
        val result = if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audio.requestAudioFocus(it) }
            ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        else audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        AndroidMusicPlayback.focus(token, result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }

    private fun update() {
        if (token != AndroidMusicPlayback.token) return
        val state = AndroidMusicPlayback.state
        if (state.phase in setOf(PlayerPhase.Idle, PlayerPhase.Ended, PlayerPhase.Error, PlayerPhase.Released)) { stopSelf(); return }
        val playing = state.phase == PlayerPhase.Playing
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO
        if (AndroidMusicPlayback.canNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        if (AndroidMusicPlayback.canPrevious) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        session.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(when (state.phase) {
            PlayerPhase.Playing -> PlaybackState.STATE_PLAYING
            PlayerPhase.Loading, PlayerPhase.Buffering -> PlaybackState.STATE_BUFFERING
            else -> PlaybackState.STATE_PAUSED
        }, state.positionMs, if (playing) state.speed else 0f, SystemClock.elapsedRealtime()).build())
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, AndroidMusicPlayback.title)
            .apply { state.durationMs?.let { putLong(MediaMetadata.METADATA_KEY_DURATION, it) } }.build())
        getSystemService(NotificationManager::class.java).notify(NotificationId, notification())
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, Channel) else Notification.Builder(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val compact = mutableListOf<Int>()
        var actionIndex = 0
        if (AndroidMusicPlayback.canPrevious) {
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "上一首", actionIntent("previous")).build())
            compact += actionIndex++
        }
        val playing = AndroidMusicPlayback.state.phase == PlayerPhase.Playing
        builder.addAction(Notification.Action.Builder(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (playing) "暂停" else "播放", actionIntent(if (playing) "pause" else "play")).build())
        compact += actionIndex++
        if (AndroidMusicPlayback.canNext) {
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "下一首", actionIntent("next")).build())
            compact += actionIndex
        }
        return builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(AndroidMusicPlayback.title)
            .setContentIntent(open).setCategory(Notification.CATEGORY_TRANSPORT).setOnlyAlertOnce(true)
            .setDeleteIntent(actionIntent("stop"))
            .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(AndroidMusicPlayback.state.phase == PlayerPhase.Playing)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(*compact.toIntArray())).build()
    }

    private fun actionIntent(action: String) = PendingIntent.getService(this, 0,
        Intent(this, AndroidMusicService::class.java).setData(Uri.parse("mafei-music://$token/$action"))
            .putExtra(TokenExtra, token).putExtra(ActionExtra, action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        else focusListener?.let { audio.abandonAudioFocus(it) }
        focusRequest = null; focusListener = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) { AndroidMusicPlayback.command("stop", token = token); stopSelf() }
    override fun onDestroy() {
        AndroidMusicPlayback.serviceDetached(token)
        abandonFocus()
        if (noisyRegistered) unregisterReceiver(noisy)
        session.isActive = false; session.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        internal const val TokenExtra = "mafei.music.generation"
        private const val ActionExtra = "mafei.music.action"
        private const val Channel = "mafei-music"
        private const val NotificationId = 2001
    }
}
