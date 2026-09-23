package cn.mafei.cmp.data.repositories.playback

import cn.mafei.cmp.data.adapter_contract.PlaybackQuality
import cn.mafei.cmp.data.adapter_contract.PlaybackTransport

/**
 * Provider facts about the stream that is currently bound to the player.
 *
 * This is what the player chrome is allowed to report: the media source it got,
 * how the server is serving it, which ceilings it can serve, and which ceiling
 * is in effect. The chrome must never derive any of these from its own last
 * click — a quality the server ignored has to read as 「自动」/「直连」, not as
 * the label the user tapped.
 *
 * Deliberately a subset of `PlaybackPreparation.Ready`: stream URLs, headers
 * and reporting contexts stay in the data layer.
 */
data class PreparedStreamFacts(
    /** Provider media-source id (Jellyfin multi-version); null for single-file providers. */
    val mediaSourceId: String? = null,
    val transport: PlaybackTransport? = null,
    /** null = the chrome may synthesise its own ladder; empty = no alternates; see the contract. */
    val qualities: List<PlaybackQuality>? = null,
    /** Ceiling actually in effect, or null when none is. */
    val appliedBitrate: Int? = null,
)
