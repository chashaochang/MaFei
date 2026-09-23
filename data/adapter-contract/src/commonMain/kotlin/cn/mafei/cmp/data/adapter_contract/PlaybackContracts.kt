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
    /**
     * Quality ceiling in bits/second for a server-side transcode. `null` keeps
     * the provider's own default (the legacy player's "auto": direct play when
     * the server allows it). A concrete value asks the provider to serve a
     * transcoded rendition at or below that bitrate.
     */
    val maxStreamingBitrate: Int? = null,
    /**
     * Position at which a provider-side transcode should start. The player
     * still seeks to the same position after loading; this hint exists for
     * providers such as fnOS whose `/play/play` endpoint creates a stream from
     * the requested timestamp instead of exposing a full-file rendition.
     */
    val startPositionMs: Long? = null,
)

/** Provider track metadata kept separate from platform-player track ids. */
enum class PlaybackTrackKind { Audio, Subtitle }

/**
 * One provider-declared selectable track. [index] is the provider stream
 * index used for a fresh PlaybackInfo request; it is not assumed to equal a
 * native player's runtime track id.
 *
 * [renegotiate] tells the caller HOW to switch to this track:
 * - `true`  — the provider must be asked again (external subtitle files that
 *   live behind their own delivery URL, or any track on a transcoded stream
 *   where the server re-muxes per selection). The caller re-runs
 *   `preparePlayback` and reloads.
 * - `false` — the track is muxed inside a direct-play/direct-stream file the
 *   player receives byte-for-byte; re-requesting the same URL cannot change
 *   it, so the caller must switch in the native runtime ([PlayerController.selectTrack]).
 *
 * Adapters decide this from the stream route they negotiated — it is a fact
 * about the prepared stream, not a UI preference.
 */
data class PlaybackTrack(
    val index: Int,
    val kind: PlaybackTrackKind,
    val displayTitle: String? = null,
    val title: String? = null,
    val language: String? = null,
    val codec: String? = null,
    val channels: Int? = null,
    val selected: Boolean = false,
    /** Track ships as its own file/resource (Jellyfin `IsExternal`, Feiniu `is_external`). */
    val isExternal: Boolean = false,
    /** Selection requires a fresh provider negotiation; see the class contract. */
    val renegotiate: Boolean = true,
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
        /** Provider media-source id selected by this negotiation. */
        val mediaSourceId: String? = null,
        /**
         * How this stream is actually served. The player chrome reports THIS,
         * never a guess derived from the UI's own selection — a quality ceiling
         * the server ignored still has to read as 直连.
         */
        val transport: PlaybackTransport? = null,
        /**
         * Ceiling (bits/second) this stream actually serves at or below, when
         * the provider applied one — Jellyfin's transcoded rendition, Feiniu's
         * chosen upstream rendition. `null` means no ceiling is in effect, so
         * the chrome must show 自动 even if it just asked for 1080p.
         */
        val appliedBitrate: Int? = null,
        /**
         * Selectable renditions, three states:
         *
         * - `null`  — the provider re-encodes on demand to any ceiling, so the
         *   chrome may synthesise its own ladder (Jellyfin).
         * - empty   — this stream has no alternates at all; the chrome must NOT
         *   offer a quality menu (Feiniu original-file direct play).
         * - non-empty — exactly these renditions, in provider order.
         *
         * Ceilings are the [PlaybackQuality.bitrate] values and are passed back
         * as `PlaybackOptions.maxStreamingBitrate`.
         */
        val qualities: List<PlaybackQuality>? = null,
        /** Provider-authoritative audio/subtitle choices for player chrome. */
        val tracks: List<PlaybackTrack> = emptyList(),
        /**
         * Headers the player must attach to [streamUrl]/[subtitleUrl]
         * (Feiniu: Authorization + cookie, `mode=relay` on relay routes;
         * Jellyfin: empty — its stream URLs carry access in the URL itself).
         */
        val headers: Map<String, String> = emptyMap(),
        /** Allows a platform player to accept the provider-issued certificate for this one stream. */
        val allowInsecureTls: Boolean = false,
        /** Some cloud STRM providers reject or rate-limit parallel Range requests. */
        val multiConnectionAllowed: Boolean = true,
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

/** How a prepared stream is served. */
enum class PlaybackTransport {
    /** The original file, byte for byte. */
    DirectPlay,

    /** The original streams, remuxed by the server (same bytes, new container). */
    DirectStream,

    /** The server re-encoded the media (quality ceiling, unsupported codec). */
    Transcode,
}

/** One provider-declared rendition of a prepared stream. */
data class PlaybackQuality(
    /** Ceiling in bits/second; fed back as `PlaybackOptions.maxStreamingBitrate`. */
    val bitrate: Int,
    /** Provider's own hint ("1080p", "4K"); the chrome adds the formatted bitrate. */
    val label: String? = null,
)

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
