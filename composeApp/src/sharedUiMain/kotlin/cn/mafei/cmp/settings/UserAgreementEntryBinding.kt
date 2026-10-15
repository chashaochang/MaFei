package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.feature.settings.UserAgreementScreen
import cn.mafei.cmp.session.*

@Composable
internal fun UserAgreementEntryBinding(popMineRoute: () -> Unit) {
    UserAgreementScreen(onBack = popMineRoute, modifier = Modifier.fillMaxSize())
}
