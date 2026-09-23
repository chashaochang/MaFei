package cn.mafei.cmp.common.ui

import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId

/**
 * Shared, non-secret presentation model for every saved-account surface.
 *
 * Account history and account switching are two routes over the same entity. Keeping this model
 * in common UI prevents each feature from inventing its own grouping identity and row contract.
 */
data class SavedAccountListItem(
    val key: String,
    val provider: ProviderKind,
    val serverId: ServerId,
    val serverName: String,
    val displayName: String,
    val address: String,
    val lastUsedEpochMs: Long? = null,
    val hasRememberedPassword: Boolean = false,
    val isCurrent: Boolean,
)

data class SavedAccountListGroup(
    val key: String,
    val title: String,
    val items: List<SavedAccountListItem>,
)

/** One server identity owns one group even when its reachable address changes. */
fun savedAccountListGroups(items: List<SavedAccountListItem>): List<SavedAccountListGroup> {
    return items.groupBy { item ->
        "${item.provider}\nid:${item.serverId.value}"
    }.map { (key, groupedItems) ->
        SavedAccountListGroup(
            key = key,
            title = groupedItems.first().serverName.trim().ifEmpty {
                groupedItems.first().address.trim()
            },
            items = groupedItems.sortedWith(
                compareByDescending<SavedAccountListItem> { it.isCurrent }
                    .thenByDescending { it.lastUsedEpochMs ?: Long.MIN_VALUE },
            ),
        )
    }.sortedWith(
        compareByDescending<SavedAccountListGroup> { group ->
            group.items.any { it.isCurrent }
        }.thenByDescending { group ->
            group.items.firstOrNull()?.lastUsedEpochMs ?: Long.MIN_VALUE
        },
    )
}
