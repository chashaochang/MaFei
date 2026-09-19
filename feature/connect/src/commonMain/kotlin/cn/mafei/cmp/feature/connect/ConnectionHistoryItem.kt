package cn.mafei.cmp.feature.connect

import cn.mafei.cmp.core.domain.ProviderKind

/** Non-secret presentation data for the login page's saved-account history. */
data class ConnectionHistoryItem(
    val key: String,
    val provider: ProviderKind,
    val displayName: String,
    val serverName: String,
    val address: String,
    val hasRememberedPassword: Boolean,
    /** The account in use right now. Its row offers no deletion. */
    val isCurrent: Boolean,
)
