package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Root lifecycle and session actions. No page or navigation state belongs here. */
internal class ApplicationStartup(
    private val runtime: ApplicationRuntime,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var starting by mutableStateOf(true)
    var showStartupProgress by mutableStateOf(false)
    var startupExiting by mutableStateOf(false)

    suspend fun start() = kotlinx.coroutines.coroutineScope {
        val progress = launch {
            delay(450)
            showStartupProgress = true
        }
        try {
            runtime.start()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "旧版本数据迁移未完成，请稍后重试"
        } finally {
            progress.cancel()
            startupExiting = true
            delay(220)
            starting = false
        }
    }

    fun logout(session: ApplicationSession) = perform("安全凭证清理未完成，请重试") {
        error = null
        runtime.sessions.logout(session)
    }

    fun retry() { scope.launch { runtime.sessions.start() } }

    fun cleanup() = perform("安全凭证清理仍未完成，请稍后重试") {
        runtime.sessions.retryCleanup()
        error = null
    }

    private fun perform(failureMessage: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = failureMessage
            } finally {
                busy = false
            }
        }
    }
}

@Composable
internal fun rememberApplicationStartup(runtime: ApplicationRuntime): ApplicationStartup {
    val scope = rememberCoroutineScope()
    val state = remember(runtime) { ApplicationStartup(runtime, scope) }
    LaunchedEffect(state) { state.start() }
    return state
}
