package cn.mafei.cmp.data.repositories.playback

import cn.mafei.cmp.data.adapter_contract.PlaybackTrack
import cn.mafei.cmp.player.api.PlayerTrack

/**
 * Legacy `MFPlayerView.resolveNativeTrack` precedence: exact id, then display
 * title, then language, then a single candidate.
 *
 * Provider stream indexes and native track ids are different namespaces — never
 * assume they are equal beyond the id coincidence check below.
 *
 * Shared by the two places that hand a provider track to a native runtime: the
 * user-driven switch (`SessionPlaybackRuntime.selectTrack`) and the initial
 * application of a preselection right after a load
 * (`ProviderPlaybackCoordinator`), so both resolve identically. Returns null
 * when nothing matches; callers then leave the player on its own default
 * instead of switching to an arbitrary track.
 */
fun resolveNativeTrackId(providerTrack: PlaybackTrack?, nativeTracks: List<PlayerTrack>): String? {
    if (providerTrack == null || nativeTracks.isEmpty()) return null
    nativeTracks.firstOrNull { it.id == providerTrack.index.toString() }?.let { return it.id }
    val title = providerTrack.displayTitle?.trim()?.lowercase().orEmpty()
    val language = providerTrack.language?.trim()?.lowercase().orEmpty()
    if (title.isNotEmpty()) {
        nativeTracks.firstOrNull { it.label.trim().lowercase() == title }?.let { return it.id }
    }
    if (language.isNotEmpty()) {
        nativeTracks.firstOrNull { it.language?.trim()?.lowercase() == language }?.let { return it.id }
    }
    return nativeTracks.singleOrNull()?.id
}
