package cn.mafei.cmp

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import cn.mafei.cmp.common.media.posterArtworkCandidates
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.player.api.PlaybackGeneration
import kotlinx.coroutines.CancellationException

/** System controls reuse the visible player's actions and queue, never a platform-owned queue. */
internal data class PlatformPlaybackActions(
    val generation: PlaybackGeneration?,
    val mediaId: String,
    val title: String,
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onStop: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onSpeed: (Float) -> Unit,
    val onNext: (() -> Unit)?,
    val onPrevious: (() -> Unit)?,
    val loadArtwork: (suspend () -> ByteArray?)? = null,
)

internal val LocalPlatformPlaybackActions = staticCompositionLocalOf<PlatformPlaybackActions?> { null }

@Composable
internal fun rememberPlatformArtworkLoader(item: MediaItem?): (suspend () -> ByteArray?)? {
    val runtime = LocalApplicationRuntime.current
    val session = runtime.sessions.state.collectAsState().value.session
    val candidates = item?.posterArtworkCandidates().orEmpty()
    return remember(runtime, session, candidates) {
        if (session == null || candidates.isEmpty()) null
        else suspend { loadPlatformArtwork(candidates) { image, width -> runtime.loadArtwork(session, image, width) } }
    }
}

internal suspend fun loadPlatformArtwork(
    candidates: List<MediaImage>,
    fetch: suspend (MediaImage, Int) -> ByteArray?,
): ByteArray? {
    for (candidate in candidates) {
        val bytes = try { fetch(candidate, 480) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        if (bytes != null && bytes.isNotEmpty() && bytes.size <= 4 * 1024 * 1024) return bytes
    }
    return null
}
