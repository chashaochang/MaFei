package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.feature.settings.AddGroupScreen
import cn.mafei.cmp.session.*

@Composable
internal fun AddGroupEntryBinding(popMineRoute: () -> Unit) {
    AddGroupScreen(onBack = popMineRoute, modifier = Modifier.fillMaxSize())
}
