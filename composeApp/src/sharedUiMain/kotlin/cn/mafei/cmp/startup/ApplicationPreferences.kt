package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.common.glass.DefaultGlassIntensity
import cn.mafei.cmp.player.api.PlayerRenderMode
import cn.mafei.cmp.session.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Application-owned preferences; route transitions must not cancel pending writes. */
internal class ApplicationPreferences(private val runtime: ApplicationRuntime, private val scope: kotlinx.coroutines.CoroutineScope) {
    var liquidGlassIntensity by mutableFloatStateOf(DefaultGlassIntensity)
    var glassIntensitySaveJob by mutableStateOf<Job?>(null)
    val homeCarouselVisibleState = mutableStateOf(true)
    var homeCarouselSaveJob by mutableStateOf<Job?>(null)
    var playerRenderMode by mutableStateOf(PlayerRenderMode.Buffer)
    var playerRenderModeSaveJob by mutableStateOf<Job?>(null)

    fun setGlassIntensity(value: Float) {
        liquidGlassIntensity = value
        glassIntensitySaveJob?.cancel()
        glassIntensitySaveJob =
            scope.launch {
                delay(180)
                runtime.saveGlassIntensity(value)
            }
    }

    fun setHomeCarouselVisible(visible: Boolean) {
        homeCarouselVisibleState.value = visible
        homeCarouselSaveJob?.cancel()
        homeCarouselSaveJob = scope.launch { runtime.saveHomeCarouselVisible(visible) }
    }

    fun setRenderMode(mode: PlayerRenderMode) {
        playerRenderMode = mode
        playerRenderModeSaveJob?.cancel()
        playerRenderModeSaveJob = scope.launch { runtime.savePlayerRenderMode(mode.storageValue) }
    }
}

@Composable
internal fun rememberApplicationPreferences(runtime: ApplicationRuntime): ApplicationPreferences {
    val scope = rememberCoroutineScope()
    val state = remember(runtime) { ApplicationPreferences(runtime, scope) }
    LaunchedEffect(runtime) { state.liquidGlassIntensity = runtime.loadGlassIntensity() }

    LaunchedEffect(runtime) { state.homeCarouselVisibleState.value = runtime.loadHomeCarouselVisible() }

    LaunchedEffect(runtime) { state.playerRenderMode = runtime.loadPlayerRenderMode().toPlayerRenderMode() }

    return state
}

internal fun String.toPlayerRenderMode(): PlayerRenderMode = if (equals("direct", ignoreCase = true)) PlayerRenderMode.Direct else PlayerRenderMode.Buffer

internal val PlayerRenderMode.storageValue: String
    get() = if (this == PlayerRenderMode.Direct) "direct" else "buffer"
