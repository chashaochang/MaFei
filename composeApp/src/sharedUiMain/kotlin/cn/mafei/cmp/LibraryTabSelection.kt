package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** The library shortcut survives root-page switches; only the active tab follows the route. */
@Composable
internal fun rememberLibraryTabId(
    currentLibraryId: String?,
    serverId: String,
    accountId: String,
): String? {
    var lastLibraryId by rememberSaveable(serverId, accountId) { mutableStateOf<String?>(null) }
    LaunchedEffect(serverId, accountId, currentLibraryId) {
        if (currentLibraryId != null) lastLibraryId = currentLibraryId
    }
    return currentLibraryId ?: lastLibraryId
}
