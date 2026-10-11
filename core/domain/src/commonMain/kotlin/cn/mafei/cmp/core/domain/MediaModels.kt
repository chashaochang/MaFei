package cn.mafei.cmp.core.domain

import kotlinx.serialization.Serializable

/**
 * Reference to an image resource. The url is opaque to the domain: providers
 * decide whether it is absolute or needs a token appended at render time.
 */
@Serializable
data class MediaImage(
    val kind: ImageKind = ImageKind.Primary,
    val url: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
)

/**
 * Per-user state attached to a media item (favorites, watch progress).
 * Providers fold their user-data concepts into this stable shape.
 */
@Serializable
data class MediaUserData(
    val isFavorite: Boolean = false,
    val isPlayed: Boolean = false,
    val playCount: Int? = null,
    /** Jellyfin series user data used by the legacy chasing-page completed rule. */
    val unplayedItemCount: Int? = null,
    val playbackPositionMs: Long = 0L,
    val playedPercentage: Double? = null,
    val lastPlayedEpochMs: Long? = null,
) {
    val hasProgress: Boolean get() = playbackPositionMs > 0L
}

/** A person credited on a media item. */
@Serializable
data class PersonRef(
    val name: String,
    val role: String? = null,
    val personType: PersonKind = PersonKind.Actor,
    val image: MediaImage? = null,
    /** Stable provider person id. Null means the credit cannot open a person page. */
    val id: MediaId? = null,
)

/** Provider-neutral person detail used by the legacy actor page. */
@Serializable
data class MediaPerson(
    val id: MediaId,
    val serverId: ServerId,
    val provider: ProviderKind,
    val name: String,
    val sortName: String? = null,
    val overview: String? = null,
    val image: MediaImage? = null,
    val isFavorite: Boolean = false,
    val movieCount: Int? = null,
    val seriesCount: Int? = null,
    val episodeCount: Int? = null,
)

/**
 * An external reference attached to an item (Jellyfin `ExternalUrls`: e.g.
 * IMDb/TMDB/TheTVDB links). The url is opened by the platform browser; the
 * label is the provider-provided site name.
 */
@Serializable
data class MediaExternalUrl(
    val name: String,
    val url: String,
)

/**
 * Provider-authoritative playback target carried by a series/season detail.
 *
 * This mirrors the legacy ArkUI `MediaPlaybackTarget`: Feiniu may return the
 * target directly from `/play/info`, while Jellyfin resolves it through the
 * per-series Next Up endpoint. It is deliberately only a reference + progress
 * hint; the browser still resolves it against the real episode list before it
 * renders or plays anything.
 */
@Serializable
data class MediaPlaybackTarget(
    val id: MediaId? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val playbackPositionMs: Long = 0L,
    val playedPercentage: Double? = null,
    val durationMs: Long? = null,
)

/**
 * The stable, provider-neutral description of a browsable media object.
 *
 * This model must never embed provider wire DTOs (e.g. Jellyfin
 * `BaseItemDto` or FNOS catalog payloads); adapters translate those into
 * these fields. Optional numeric positional data uses [seasonNumber] /
 * [episodeNumber] and the generic [indexNumber]/[parentIndexNumber] pair for
 * other hierarchies (tracks, photos, chapters).
 */
@Serializable
data class MediaItem(
    val id: MediaId,
    val serverId: ServerId,
    val provider: ProviderKind,
    val title: String,
    val mediaType: MediaType = MediaType.Unknown,
    val originalTitle: String? = null,
    val overview: String? = null,
    val collectionId: CollectionId? = null,
    val parentId: MediaId? = null,
    val seriesId: MediaId? = null,
    val seriesTitle: String? = null,
    val seasonId: MediaId? = null,
    val seasonTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    /** Direct child count reported by the provider (season episodes, series seasons, folder items). */
    val childCount: Int? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val year: Int? = null,
    val premiereEpochMs: Long? = null,
    val dateAddedEpochMs: Long? = null,
    val runtimeMs: Long? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val genres: List<String> = emptyList(),
    /** Provider-supplied media badges shown on legacy poster cards (for example 4K/HDR/DTS). */
    val tags: List<String> = emptyList(),
    val people: List<PersonRef> = emptyList(),
    val images: List<MediaImage> = emptyList(),
    val userData: MediaUserData = MediaUserData(),
    val canPlay: Boolean = false,
    /**
     * Playable files for this item, each with its elementary streams. The legacy
     * HarmonyOS detail page rendered this as 「文件媒体信息」; the wire DTO already
     * carried it, but nothing mapped it into the domain before.
     */
    val mediaSources: List<MediaSourceInfo> = emptyList(),
    /** Provider-supplied external reference links (IMDb/TMDB/...), detail pages only. */
    val externalUrls: List<MediaExternalUrl> = emptyList(),
    /** Legacy detail playback target; resolved against the episode list by the browser. */
    val playbackTarget: MediaPlaybackTarget? = null,
    /**
     * 本条数据的展示风格覆盖。适配器**只在**自身服务器的命名/排版与
     * [MediaDisplayStyle.forProvider] 给出的默认风格不一致时才写入；为 `null` 时按
     * [provider] 查默认表。展示字段（[heroSubtitle]、[episodeLabel]、[metaLine] 等）
     * 的格式规则统一在 `MediaPresentation.kt` 定义，UI 不得自行格式化。
     */
    val displayStyleOverride: MediaDisplayStyle? = null,
    /** Jellyfin music metadata; null/empty for non-audio items. */
    val albumId: MediaId? = null,
    val albumTitle: String? = null,
    val albumArtist: String? = null,
    val artistNames: List<String> = emptyList(),
    val artistIds: List<MediaId> = emptyList(),
    val recursiveItemCount: Int? = null,
    val channelNumber: String? = null,
    val currentProgram: LiveProgram? = null,
    val photo: PhotoInfo? = null,
) {
    val primaryImage: MediaImage? get() = images.firstOrNull { it.kind == ImageKind.Primary } ?: images.firstOrNull()
}

@Serializable
data class LiveProgram(val title: String, val overview: String? = null,
    val startEpochMs: Long? = null, val endEpochMs: Long? = null)

@Serializable
data class PhotoInfo(
    val fileName: String, val path: String = "", val format: String = "", val dateKey: String = "unknown",
    val width: Int = 0, val height: Int = 0, val fullImage: MediaImage,
    val canDownload: Boolean = false, val cameraMake: String? = null, val cameraModel: String? = null,
    val software: String? = null, val exposureTime: Double? = null, val focalLength: Double? = null,
    val orientation: String? = null, val aperture: Double? = null, val shutterSpeed: Double? = null,
    val latitude: Double? = null, val longitude: Double? = null, val altitude: Double? = null,
    val isoSpeedRating: Double? = null,
    val dateTaken: String? = null,
    val dateTakenEpochMs: Long? = null,
)

/** Short-lived authenticated original download; never logged or persisted. */
class MediaDownload(val url: String, val fileName: String, val headers: Map<String, String> = emptyMap()) {
    override fun toString(): String = "MediaDownload(redacted)"
}

/**
 * One row in the legacy playback-history surfaces (the Mine preview and the
 * standalone history page).
 *
 * The latest available season/episode does not describe [media] itself, so it
 * deliberately lives in this aggregate instead of being written into the
 * ordinary [MediaItem]. Providers still own the playable row; the browser
 * enriches only episode rows by walking the owning series exactly like the old
 * `MineHistoryRepository`.
 */
@Serializable
data class PlaybackHistoryItem(
    val media: MediaItem,
    val latestSeasonNumber: Int? = null,
    val latestEpisodeNumber: Int? = null,
    val seriesHasMultipleSeasons: Boolean = (media.seasonNumber ?: 0) > 1,
)

/** One playable file for an item — a Jellyfin "media source" / version. */
@Serializable
data class MediaSourceInfo(
    val id: String? = null,
    val name: String? = null,
    val path: String? = null,
    val container: String? = null,
    val sizeBytes: Long? = null,
    val runTimeMs: Long? = null,
    /** File birth/create time reported by the provider. */
    val fileCreatedEpochMs: Long? = null,
    /** Time the file was added to the provider catalog. */
    val dateAddedEpochMs: Long? = null,
    val isRemote: Boolean = false,
    val sortOrder: Int? = null,
    val playable: Boolean? = null,
    val playError: String? = null,
    val defaultVideoIndex: Int? = null,
    val defaultAudioIndex: Int? = null,
    val defaultSubtitleIndex: Int? = null,
    val streams: List<MediaStreamInfo> = emptyList(),
) {
    val videoStream: MediaStreamInfo? get() = streams.firstOrNull { it.kind == MediaStreamKind.Video }
    val audioStreams: List<MediaStreamInfo> get() = streams.filter { it.kind == MediaStreamKind.Audio }
    val subtitleStreams: List<MediaStreamInfo> get() = streams.filter { it.kind == MediaStreamKind.Subtitle }
}

/** One elementary stream inside a [MediaSourceInfo]. */
@Serializable
data class MediaStreamInfo(
    val index: Int? = null,
    val kind: MediaStreamKind = MediaStreamKind.Other,
    val codec: String? = null,
    val profile: String? = null,
    val level: Double? = null,
    val language: String? = null,
    val title: String? = null,
    /** Provider display label kept separate from the raw stream title. */
    val displayTitle: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val channels: Int? = null,
    val channelLayout: String? = null,
    val sampleRateHz: Int? = null,
    val bitRate: Int? = null,
    val bitDepth: Int? = null,
    val referenceFrames: Int? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    /** Encoded frame dimensions; may differ from the display dimensions. */
    val codedWidthPx: Int? = null,
    val codedHeightPx: Int? = null,
    val aspectRatio: String? = null,
    val frameRate: Double? = null,
    val rotation: Int? = null,
    val interlaced: Boolean? = null,
    /** Provider resolution label such as `2160p`; kept separate from display title. */
    val resolutionType: String? = null,
    val videoRangeType: String? = null,
    val dolbyVisionProfile: Int? = null,
    val colorPrimaries: String? = null,
    val colorSpace: String? = null,
    val colorTransfer: String? = null,
    val pixelFormat: String? = null,
    val isBluray: Boolean? = null,
    val audioType: String? = null,
    val isFake: Boolean? = null,
    val source: String? = null,
    val fileSizeBytes: Long? = null,
) {
    val resolutionLabel: String? get() = when {
        widthPx != null && heightPx != null -> "${widthPx}×${heightPx}"
        else -> null
    }
}

@Serializable
enum class MediaStreamKind { Video, Audio, Subtitle, Other }

/**
 * A server-side library ("view") grouping media items, used for the home
 * screen shelves.
 */
@Serializable
data class MediaCollection(
    val id: CollectionId,
    val serverId: ServerId,
    val provider: ProviderKind,
    val name: String,
    val mediaTypes: Set<MediaType> = emptySet(),
    val primaryImage: MediaImage? = null,
    /** Provider-reported direct child count when the library endpoint exposes it. */
    val itemCount: Int? = null,
)
