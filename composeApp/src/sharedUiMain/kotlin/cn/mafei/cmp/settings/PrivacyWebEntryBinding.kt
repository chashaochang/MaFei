package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.session.*

@Composable
internal fun PrivacyWebEntryBinding(
    popMineRoute: () -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    val page = remember { cn.mafei.cmp.session.WebPageRequest(PRIVACY_AGREEMENT_URL, "隐私协议") }
    EmbeddedWebPage(LocalEmbeddedWebHost.current, page, page.title, null, popMineRoute, {},
        { action, value -> if (action == "openUrl") uriHandler.openUri(value) }, Modifier.fillMaxSize())
}
