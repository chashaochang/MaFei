package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.feature.settings.AboutPrivacyScreen
import cn.mafei.cmp.session.*

@Composable
internal fun AboutPrivacyEntryBinding(
    account: AccountFlowState,
    popMineRoute: () -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    embeddedWebHost: EmbeddedWebHost,
) {
    AboutPrivacyScreen(
        onBack = popMineRoute,
        onPrivacy = {
            if (embeddedWebHost === UnsupportedEmbeddedWebHost) uriHandler.openUri(PRIVACY_AGREEMENT_URL) else account.mineBackStack += BrowsePrivacyWebRoute
        },
        onUserAgreement = {
            if (account.mineBackStack.lastOrNull() != BrowseUserAgreementRoute) {
                account.mineBackStack += BrowseUserAgreementRoute
            }
        },
        onOpenUrl = uriHandler::openUri,
        modifier = Modifier.fillMaxSize(),
    )
}
