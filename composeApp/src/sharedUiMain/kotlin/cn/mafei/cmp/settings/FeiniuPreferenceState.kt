package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.feature.settings.FeiniuConnectionPreference
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoutePreference
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class FeiniuPreferenceState(
    private val load: suspend () -> FeiniuRoutePreference,
    private val save: suspend (FeiniuRoutePreference) -> Unit,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    var feiniuConnectionPreference by mutableStateOf(FeiniuConnectionPreference.Speed)
    var feiniuConnectionPreferenceError by mutableStateOf<String?>(null)
    var feiniuConnectionPreferenceSaving by mutableStateOf(false)

    suspend fun load() {
        feiniuConnectionPreference = load.invoke().toPresentationPreference()
    }

    fun select(selected: FeiniuConnectionPreference) {
        if (selected != feiniuConnectionPreference && !feiniuConnectionPreferenceSaving) {
            feiniuConnectionPreferenceError = null
            feiniuConnectionPreferenceSaving = true
            scope.launch {
                try {
                    save(selected.toProviderPreference())
                    feiniuConnectionPreference = selected
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    feiniuConnectionPreferenceError = "连接策略保存失败，请重试"
                } finally {
                    feiniuConnectionPreferenceSaving = false
                }
            }
        }
    }
}

private fun FeiniuRoutePreference.toPresentationPreference(): FeiniuConnectionPreference =
    when (this) {
        FeiniuRoutePreference.Public -> FeiniuConnectionPreference.Public
        FeiniuRoutePreference.Relay -> FeiniuConnectionPreference.Relay
        FeiniuRoutePreference.Auto,
        FeiniuRoutePreference.Speed -> FeiniuConnectionPreference.Speed
    }

private fun FeiniuConnectionPreference.toProviderPreference(): FeiniuRoutePreference =
    when (this) {
        FeiniuConnectionPreference.Speed -> FeiniuRoutePreference.Speed
        FeiniuConnectionPreference.Public -> FeiniuRoutePreference.Public
        FeiniuConnectionPreference.Relay -> FeiniuRoutePreference.Relay
    }
