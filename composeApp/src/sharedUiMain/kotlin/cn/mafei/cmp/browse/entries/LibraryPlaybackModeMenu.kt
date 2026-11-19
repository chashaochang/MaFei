package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyModalSheet
import cn.mafei.cmp.common.ui.MaFeiErrorDialog
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal sealed interface PlaybackModeTarget {
    data class Library(val collection: MediaCollection) : PlaybackModeTarget
    data class Work(val item: MediaItem) : PlaybackModeTarget
}

internal class LibraryPlaybackModeMenuState {
    var target by mutableStateOf<PlaybackModeTarget?>(null)
    fun openLibrary(collection: MediaCollection) { target = PlaybackModeTarget.Library(collection) }
    fun openWork(item: MediaItem) { target = PlaybackModeTarget.Work(item) }
}

private enum class PlaybackModeChoice(val label: String, val enabled: Boolean?) {
    Inherit("跟随媒体库", null), ShortDrama("短剧", true), Regular("普通", false),
}

@Composable
internal fun LibraryPlaybackModeMenu(state: LibraryPlaybackModeMenuState, player: BrowsePlayerState) {
    val scope = rememberCoroutineScope()
    val backdrop = rememberGlassBackdrop()
    var failure by remember { mutableStateOf<String?>(null) }
    val target = state.target
    var selectedMode by remember(target) { mutableStateOf<PlaybackModeChoice?>(null) }
    var saving by remember(target) { mutableStateOf(false) }
    LaunchedEffect(target) {
        try {
            val enabled = when (target) {
                is PlaybackModeTarget.Library -> player.shortDramaLibraryMode(target.collection)
                is PlaybackModeTarget.Work -> player.shortDramaWorkMode(target.item)
                null -> return@LaunchedEffect
            }
            selectedMode = PlaybackModeChoice.entries.first { it.enabled == enabled }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.target = null
            failure = "无法读取播放模式，请重试"
        }
    }
    if (target != null && selectedMode != null) {
        val isLibrary = target is PlaybackModeTarget.Library
        LegacyModalSheet(
            title = if (isLibrary) "媒体库播放模式" else "作品播放模式",
            onDismiss = { if (!saving) state.target = null },
            heightFraction = 0.52f,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                PlaybackModeChoice.entries.filter { !isLibrary || it.enabled != null }.forEach { choice ->
                    val active = selectedMode == choice
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = active }
                            .mafeiInteractive(radiusDp = 8f, label = choice.label, onClick = {
                                if (!saving) {
                                    saving = true
                                    scope.launch {
                                        try {
                                            when (target) {
                                                is PlaybackModeTarget.Library -> player.setShortDramaLibrary(target.collection, choice.enabled == true)
                                                is PlaybackModeTarget.Work -> player.setShortDramaWork(target.item, choice.enabled)
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            failure = "无法保存播放模式，请重试"
                                        } finally {
                                            if (state.target == target) state.target = null
                                            saving = false
                                        }
                                    }
                                }
                            }).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(choice.label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        if (active) Text("\u2713", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    MaFeiErrorDialog(failure, backdrop, onDismiss = { failure = null })
}
