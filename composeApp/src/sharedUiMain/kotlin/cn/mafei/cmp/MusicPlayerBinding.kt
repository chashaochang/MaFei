package cn.mafei.cmp

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.feature.music.MusicPlayerScreen
import cn.mafei.cmp.feature.music.MusicRepeatMode
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.common.ui.LegacyTransientToast
import cn.mafei.cmp.common.ui.MaFeiBackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Audio's platform host outlives opening/closing the full player, so the mini player stays live. */
@Composable
internal fun BoundMusicNativeSurface(controller: PlayerController, item: MediaItem,
    playerSurface: @Composable (PlayerController, () -> Unit, () -> Unit, Boolean, Modifier) -> Unit,
    onBack: () -> Unit, onStop: () -> Unit, onSeek: (Long) -> Unit,
    onNext: (() -> Unit)?, onPrevious: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    val generation by remember(controller) { controller.state.map { it.generation }.distinctUntilChanged() }
        .collectAsState(controller.state.value.generation)
    val loadSystemArtwork = rememberPlatformArtworkLoader(item)
    CompositionLocalProvider(LocalPlatformPlaybackActions provides PlatformPlaybackActions(
        generation, item.id.value, item.title,
        onPlay = { scope.launch { controller.play() } }, onPause = { scope.launch { controller.pause() } },
        onStop = onStop, onSeek = onSeek, onSpeed = { value -> scope.launch { controller.speed(value) } },
        onNext = onNext, onPrevious = onPrevious, loadArtwork = loadSystemArtwork)) {
        // CPF delivers OHOS commands through its native view parameters. This session-owned
        // output node is not a second player; audio has no visible video rectangle.
        playerSurface(controller, onBack, {}, false, Modifier.size(1.dp).alpha(0f))
    }
}

@Composable
internal fun BoundMusicPlayer(item: MediaItem, controller: PlayerController, preparing: Boolean, error: String?,
    queue: List<MediaItem>, queueIndex: Int, repeatMode: MusicRepeatMode,
    onBack: () -> Unit, onTogglePlay: () -> Unit, onRetry: () -> Unit, onSeek: (Long) -> Unit,
    onPrevious: (() -> Unit)?, onNext: (() -> Unit)?, onSelect: (Int) -> Unit, onRepeat: () -> Unit,
    onFavorite: (suspend () -> Unit)?, onRemove: (Int) -> Unit, onClear: () -> Unit,
    artwork: @Composable (MediaItem, Modifier) -> Unit, ambientArtwork: @Composable (MediaItem, Modifier) -> Unit) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var saving by remember(item.id) { mutableStateOf(false) }
    var favoriteError by remember(item.id) { mutableStateOf<String?>(null) }
    MaFeiBackHandler(true, onBack)
    MusicPlayerScreen(item, state, preparing, error, queue, queueIndex, repeatMode, onBack, onTogglePlay,
        onRetry, onSeek, onPrevious, onNext, onSelect, onRepeat,
        onFavorite = onFavorite?.let { action -> {
            if (!saving) {
                saving = true
                scope.launch {
                    try { action(); favoriteError = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { favoriteError = "收藏失败，请重试" }
                    finally { saving = false }
                }
            }
        } }, favoriteBusy = saving, onRemove = onRemove, onClear = onClear,
        artwork = artwork, ambientArtwork = ambientArtwork)
    LegacyTransientToast(favoriteError)
}
