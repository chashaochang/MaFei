package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.PlaybackSessionId

/**
 * User-directed stream selection handed to [MediaProviderAdapter.preparePlayback].
 *
 * All fields are optional: the adapter negotiates on its own when a field is
 * null. Where the provider exposes multiple playable files/versions,
 * [mediaSourceId] pins one (the id must be one the provider published, e.g.
 * inside `MediaItem.mediaSources`). Track indexes follow the provider's own
 * stream numbering; `subtitleStreamIndex = -1` (subtitles off) is also legal.
 * A provider without multi-version/track selection ignores what it cannot
 * express instead of failing.
 */
data class PlaybackOptions(
    val mediaSourceId: String? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
)

/**
 * Outcome of provider-neutral playback preparation.
 *
 * The sealed hierarchy makes every non-success case an explicit value: a
 * missing link, a provider refusal and a rejected link are distinguishable
 * outcomes — never one fuzzy `null`. [Ready.streamUrl]/[Ready.subtitleUrl]
 * are absolute URLs already validated by the adapter (scheme, origin and
 * trust rules of the serving provider); unverified raw wire URLs must never
 * reach the player through this contract.
 */
sealed interface PlaybackPreparation {

    /** Streams are negotiated and playable now. Stream links may expire — adapters re-prepare, callers never persist them. */
    data class Ready(
        val streamUrl: String,
        val subtitleUrl: String? = null,
        /**
         * Headers the player must attach to [streamUrl]/[subtitleUrl]
         * (Feiniu: Authorization + cookie, `mode=relay` on relay routes;
         * Jellyfin: empty — its stream URLs carry access in the URL itself).
         */
        val headers: Map<String, String> = emptyMap(),
        /** Provider play session id for progress reporting, when the provider issues one. Never sufficient alone: see [reportingContext]. */
        val playSessionId: PlaybackSessionId? = null,
        /**
         * Opaque provider context required by [MediaProviderAdapter.reportPlayback].
         * Either provider may need it (Jellyfin: negotiated item/media source;
         * Feiniu: guid set/resolution/bitrate/play link) — a possibly-null
         * playSessionId is never sufficient on its own. Adapters validate
         * instance ownership at report time. Never persisted, and never
         * derived by re-parsing stream URLs.
         */
        val reportingContext: String? = null,
    ) : PlaybackPreparation {
        init {
            require(streamUrl.isNotBlank()) { "PlaybackPreparation.Ready.streamUrl must not be blank" }
        }
    }

    /** The provider cannot offer a playable stream for this item right now. Not an exception: it is an expected outcome. */
    data class Refused(
        val reason: RefusalReason,
        val detail: String? = null,
    ) : PlaybackPreparation
}

/** Why preparation did not yield a playable stream. */
enum class RefusalReason {
    /** Provider explicitly refused playback (e.g. Jellyfin PlaybackErrorCode, Feiniu fatal errno). */
    ServerRefused,

    /** Negotiation succeeded but no playable media source/link was offered. */
    NoPlayableSource,

    /** Success payload carried no stream link at all. */
    LinkMissing,

    /** A link was present but failed adapter validation (unparseable/cross-origin/untrusted) and was discarded. */
    LinkRejected,
}

/** What point of a prepared playback stream is being reported. */
enum class PlaybackEventKind { Start, Progress, Stop }

/**
 * One playback progress report in contract units (milliseconds; adapters
 * convert to their wire units — Jellyfin ticks, Feiniu seconds).
 */
data class PlaybackEvent(
    val kind: PlaybackEventKind,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val isPaused: Boolean? = null,
) {
    init {
        require(positionMs >= 0L) { "PlaybackEvent.positionMs must be non-negative but was $positionMs" }
        require(durationMs == null || durationMs >= 0L) {
            "PlaybackEvent.durationMs must be non-negative but was $durationMs"
        }
    }
}
