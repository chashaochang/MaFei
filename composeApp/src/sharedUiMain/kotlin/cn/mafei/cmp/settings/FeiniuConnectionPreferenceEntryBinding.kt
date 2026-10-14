package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.feature.settings.FeiniuConnectionPreferenceScreen
import cn.mafei.cmp.session.*

@Composable
internal fun FeiniuConnectionPreferenceEntryBinding(
    feiniu: FeiniuPreferenceState,
    popMineRoute: () -> Unit,
) {
    FeiniuConnectionPreferenceScreen(
        selectedPreference = feiniu.feiniuConnectionPreference,
        onSelect = feiniu::select,
        onBack = popMineRoute,
        errorMessage = feiniu.feiniuConnectionPreferenceError,
        modifier = Modifier.fillMaxSize(),
    )
}
