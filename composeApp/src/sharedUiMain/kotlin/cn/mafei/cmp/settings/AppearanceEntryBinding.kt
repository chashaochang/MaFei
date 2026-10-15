package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.session.*

@Composable
internal fun AppearanceEntryBinding(popMineRoute: () -> Unit, appearanceStore: AppearanceStore) {
    AppearanceRoute(appearanceStore, onBack = popMineRoute, modifier = Modifier.fillMaxSize())
}
