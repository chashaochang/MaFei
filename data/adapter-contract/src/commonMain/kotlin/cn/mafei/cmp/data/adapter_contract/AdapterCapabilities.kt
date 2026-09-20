package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.ClientConfigError

/**
 * Fine-grained capability axes of a provider adapter.
 *
 * The set is deliberately split per operation family instead of one
 * `supportsUserDataEdits` boolean: real providers differ per capability (the
 * Feiniu SDK can edit favorites but the Jellyfin P0 SDK has no user-data
 * write endpoints at all; Feiniu reads favorites through a dedicated endpoint
 * that cannot combine arbitrary filters).
 */
enum class AdapterCapability {
    /** Provider-neutral [MediaProviderAdapter.search]. */
    Search,

    /** Can answer a favorites-only listing (dedicated endpoint or filter). */
    FavoritesRead,

    /** Can combine the favorites flag with other filters on a normal listing. */
    FavoritesFilter,

    /** Can toggle favorite state ([MediaProviderAdapter.setFavorite]). */
    FavoritesEdit,

    /** Can answer played/unplayed-filtered listings. */
    WatchedFilter,

    /** Can toggle watched state ([MediaProviderAdapter.setWatched]). */
    WatchedEdit,

    /** Can report playback start/progress/stop ([MediaProviderAdapter.reportPlayback]). */
    PlaybackReporting,

    /** Can delete media items ([MediaProviderAdapter.deleteItems]), server policy permitting. */
    MediaDelete,

    /** Legacy followed-series chasing page (resume + per-series next-up + completed state). */
    Chasing,
}

/**
 * What one adapter build actually supports. Adapters must declare flags
 * honestly: an SDK write endpoint that is not implemented yet is declared
 * `false` and its method throws [ClientConfigError] when called — it never
 * pretends success.
 */
data class AdapterCapabilities(
    val search: Boolean = false,
    val favoritesRead: Boolean = false,
    val favoritesFilter: Boolean = false,
    val favoritesEdit: Boolean = false,
    val watchedFilter: Boolean = false,
    val watchedEdit: Boolean = false,
    val playbackReporting: Boolean = false,
    val mediaDelete: Boolean = false,
    val chasing: Boolean = false,
) {
    fun supports(capability: AdapterCapability): Boolean = when (capability) {
        AdapterCapability.Search -> search
        AdapterCapability.FavoritesRead -> favoritesRead
        AdapterCapability.FavoritesFilter -> favoritesFilter
        AdapterCapability.FavoritesEdit -> favoritesEdit
        AdapterCapability.WatchedFilter -> watchedFilter
        AdapterCapability.WatchedEdit -> watchedEdit
        AdapterCapability.PlaybackReporting -> playbackReporting
        AdapterCapability.MediaDelete -> mediaDelete
        AdapterCapability.Chasing -> chasing
    }
}

/**
 * Single gate every capability-checked adapter method calls first. Callers
 * are expected to consult [AdapterCapabilities] up front; reaching here with
 * an unsupported capability is a wiring bug, surfaced as [ClientConfigError].
 */
fun requireCapability(capabilities: AdapterCapabilities, capability: AdapterCapability) {
    if (!capabilities.supports(capability)) {
        throw ClientConfigError(
            "Adapter does not support capability '$capability'; consult AdapterCapabilities before calling",
        )
    }
}
