package cn.mafei.cmp.data.repositories

import cn.mafei.cmp.core.domain.*

/** Query meaning is part of the cache key; Provider cursors stay opaque. */
sealed interface MediaQuery {
    data class Items(val collectionId: CollectionId? = null) : MediaQuery
    data class Search(val term: String) : MediaQuery
    data class Seasons(val seriesId: MediaId) : MediaQuery
    data class Episodes(val seriesId: MediaId, val seasonId: MediaId? = null) : MediaQuery
    /** Provider-authoritative one-item Next Up result for a series detail. */
    data class NextUp(val seriesId: MediaId) : MediaQuery
    /** Legacy chasing-page Next Up semantics, including its one-year cutoff. */
    data class ChasingNextUp(val seriesId: MediaId, val nextUpDateCutoffEpochMs: Long) : MediaQuery
    data class PersonItems(val personId: MediaId) : MediaQuery
    /** One account-scoped page of resumable (in-progress) items — watch history source. */
    data object Resume : MediaQuery
    /** Player-side "More like this" data. The legacy detail page does not render it. */
    data class Similar(val itemId: MediaId) : MediaQuery
}

/** Runtime handle, not a persisted login record. Even reopening the same account creates a new generation. */
class MediaSession internal constructor(
    val provider: ProviderKind,
    val serverId: ServerId,
    val accountId: AccountId,
    val generation: Long,
)

enum class MediaLoadOrigin { Network, FreshCache, StaleCache }

/** Stale fallback is explicit so UI can show an offline/stale state, never a fake refresh success. */
data class MediaLoad<T>(
    val value: T,
    val session: MediaSession,
    val origin: MediaLoadOrigin,
    val refreshError: DomainError? = null,
)

data class MediaCachePolicy(
    val freshTtlMs: Long = 60_000,
    val staleMaxAgeMs: Long = 300_000,
    /** Per typed cache (pages, collections, detail and bounded list-state hints). */
    val maxEntries: Int = 256,
) {
    init {
        require(freshTtlMs >= 0 && staleMaxAgeMs >= freshTtlMs)
        require(maxEntries > 0)
    }
}
