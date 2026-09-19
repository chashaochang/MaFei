package cn.mafei.cmp.providers.feiniu.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * P0 Feiniu wire models. JSON keys are snake_case per the fnOS media API —
 * every property carries the exact `@SerialName`. Unknown fields are ignored
 * (frozen MafeiJson policy).
 */
@Serializable
data class FeiniuEnvelope(
    @SerialName("code") val code: Int? = null,
    @SerialName("msg") val msg: String? = null,
    @SerialName("data") val data: JsonElement? = null,
    /** fnOS login/auth responses carry the access token at the top level. */
    @SerialName("token") val token: String? = null,
)

@Serializable
data class FeiniuResolvePortInfo(
    @SerialName("httpPort") val httpPort: Int? = null,
    @SerialName("httpsPort") val httpsPort: Int? = null,
)

@Serializable
data class FeiniuResolveData(
    @SerialName("ipv4") val lanIpv4: List<String> = emptyList(),
    @SerialName("ipv6") val lanIpv6: List<String> = emptyList(),
    @SerialName("publicIpv4") val publicIpv4: List<String> = emptyList(),
    @SerialName("publicIpv6") val publicIpv6: List<String> = emptyList(),
    @SerialName("fn") val relayHosts: List<String> = emptyList(),
    @SerialName("port") val port: FeiniuResolvePortInfo? = null,
)

@Serializable
data class FeiniuTokenData(
    @SerialName("token") val token: String? = null,
)

@Serializable
data class FeiniuLoginRequest(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("app_name") val appName: String,
)

@Serializable
data class FeiniuUserInfo(
    @SerialName("avatar") val avatar: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("is_admin") val isAdmin: Int? = null,
)

@Serializable
data class FeiniuSysConfig(
    @SerialName("server_name") val serverName: String? = null,
    @SerialName("nas_oauth") val nasOauth: JsonElement? = null,
)

/** One media item as returned by item/favorite/search/season/episode lists. */
@Serializable
data class FeiniuMediaItem(
    @SerialName("guid") val guid: String,
    @SerialName("title") val title: String,
    @SerialName("type") val type: String? = null,
    @SerialName("poster") val poster: String? = null,
    @SerialName("can_play") val canPlay: Int? = null,
    @SerialName("parent_guid") val parentGuid: String? = null,
    @SerialName("parent_title") val parentTitle: String? = null,
    @SerialName("ancestor_guid") val ancestorGuid: String? = null,
    @SerialName("ancestor_name") val ancestorName: String? = null,
    @SerialName("ancestor_category") val ancestorCategory: String? = null,
    @SerialName("tv_title") val tvTitle: String? = null,
    @SerialName("watched") val watched: Int? = null,
    @SerialName("watched_ts") val watchedEpochSeconds: Long? = null,
    @SerialName("is_favorite") val isFavorite: Int? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("vote_average") val voteAverage: String? = null,
    @SerialName("ts") val epochSeconds: Long? = null,
    @SerialName("duration") val durationSeconds: Long? = null,
    @SerialName("runtime") val runtimeMinutes: Int? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("play_item_guid") val playItemGuid: String? = null,
    @SerialName("media_guid") val mediaGuid: String? = null,
    @SerialName("video_guid") val videoGuid: String? = null,
    @SerialName("audio_guid") val audioGuid: String? = null,
    @SerialName("subtitle_guid") val subtitleGuid: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("media_stream") val mediaStream: FeiniuItemStream? = null,
)

@Serializable
data class FeiniuItemStream(
    @SerialName("resolutions") val resolutions: List<String> = emptyList(),
    @SerialName("audio_type") val audioType: String? = null,
    @SerialName("color_range_type") val colorRangeType: String? = null,
    @SerialName("progress") val progress: Double? = null,
    @SerialName("current_time") val currentTimeSeconds: Double? = null,
)

/** GET /v/api/v1/play/list entry used by the home resume shelf. */
@Serializable
data class FeiniuPlayItem(
    @SerialName("guid") val guid: String,
    @SerialName("title") val title: String,
    @SerialName("type") val type: String? = null,
    @SerialName("poster") val poster: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("can_play") val canPlay: Int? = null,
    @SerialName("parent_guid") val parentGuid: String? = null,
    @SerialName("parent_title") val parentTitle: String? = null,
    @SerialName("ancestor_guid") val ancestorGuid: String? = null,
    @SerialName("ancestor_name") val ancestorName: String? = null,
    @SerialName("ancestor_category") val ancestorCategory: String? = null,
    @SerialName("tv_title") val tvTitle: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("is_favorite") val isFavorite: Int? = null,
    @SerialName("watched") val watched: Int? = null,
    @SerialName("watched_ts") val watchedEpochSeconds: Long? = null,
    @SerialName("ts") val epochSeconds: Long? = null,
    @SerialName("duration") val durationSeconds: Long? = null,
    @SerialName("media_stream") val mediaStream: FeiniuItemStream? = null,
)

/** P0 subset of the item detail payload. */
@Serializable
data class FeiniuItemDetail(
    @SerialName("guid") val guid: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("posters") val posters: String? = null,
    @SerialName("backdrops") val backdrops: String? = null,
    @SerialName("vote_average") val voteAverage: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("content_ratings") val contentRatings: String? = null,
    @SerialName("production_countries") val productionCountries: List<String> = emptyList(),
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("trim_id") val trimId: String? = null,
    @SerialName("play_error") val playError: String? = null,
)

@Serializable
data class FeiniuItemListData(
    @SerialName("list") val list: List<FeiniuMediaItem> = emptyList(),
    @SerialName("total") val total: Int? = null,
    @SerialName("dir") val dir: String? = null,
    @SerialName("top_dir") val topDir: String? = null,
)

/** Tags block of the item list request (P0 filter subset). */
@Serializable
data class FeiniuItemListTags(
    @SerialName("type") val type: List<String>,
    @SerialName("genres") val genres: Int? = null,
    @SerialName("resolution") val resolution: String? = null,
    @SerialName("watched") val watched: String? = null,
    @SerialName("decade") val decade: String? = null,
)

@Serializable
data class FeiniuItemListRequest(
    @SerialName("ancestor_guid") val ancestorGuid: String? = null,
    @SerialName("exclude_grouped_video") val excludeGroupedVideo: Int? = 1,
    @SerialName("sort_type") val sortType: String? = null,
    @SerialName("sort_column") val sortColumn: String? = null,
    @SerialName("page_size") val pageSize: Int? = null,
    @SerialName("page") val page: Int? = null,
    @SerialName("tags") val tags: FeiniuItemListTags,
)

@Serializable
data class FeiniuPlayInfoItem(
    @SerialName("title") val title: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("parent_title") val parentTitle: String? = null,
    @SerialName("tv_title") val tvTitle: String? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("duration") val durationSeconds: Long? = null,
)

@Serializable
data class FeiniuPlayInfoData(
    @SerialName("guid") val guid: String? = null,
    @SerialName("media_guid") val mediaGuid: String? = null,
    @SerialName("audio_guid") val audioGuid: String? = null,
    @SerialName("subtitle_guid") val subtitleGuid: String? = null,
    @SerialName("video_guid") val videoGuid: String? = null,
    @SerialName("ts") val epochSeconds: Long? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("item") val item: FeiniuPlayInfoItem? = null,
)

/** P0 subset of the stream list payload (track description). */
@Serializable
data class FeiniuStreamListData(
    @SerialName("files") val files: JsonElement? = null,
    @SerialName("video_streams") val videoStreams: JsonElement? = null,
    @SerialName("audio_streams") val audioStreams: JsonElement? = null,
    @SerialName("subtitle_streams") val subtitleStreams: JsonElement? = null,
)

/** POST /v/api/v1/play/play request (P0 fields). */
@Serializable
data class FeiniuPlayPlayRequest(
    @SerialName("media_guid") val mediaGuid: String,
    @SerialName("video_guid") val videoGuid: String,
    @SerialName("video_encoder") val videoEncoder: String,
    @SerialName("resolution") val resolution: String,
    @SerialName("bitrate") val bitrate: Long,
    @SerialName("startTimestamp") val startTimestamp: Long,
    @SerialName("audio_encoder") val audioEncoder: String,
    @SerialName("audio_guid") val audioGuid: String,
    @SerialName("subtitle_guid") val subtitleGuid: String,
    @SerialName("channels") val channels: Int,
    @SerialName("forced_sdr") val forcedSdr: Int,
)

/** POST /v/api/v1/play/play response — play_link is the (expiring) stream URL. */
@Serializable
data class FeiniuPlayPlayData(
    @SerialName("media_guid") val mediaGuid: String? = null,
    @SerialName("play_link") val playLink: String? = null,
    @SerialName("subtitle_link") val subtitleLink: String? = null,
    @SerialName("is_subtitle_external") val isSubtitleExternal: Int? = null,
    @SerialName("audio_index") val audioIndex: Int? = null,
    @SerialName("video_index") val videoIndex: Int? = null,
    @SerialName("subtitle_index") val subtitleIndex: Int? = null,
    @SerialName("hls_time") val hlsTime: Int? = null,
    @SerialName("non_fatal_errno") val nonFatalErrno: Int? = null,
    @SerialName("video_encoder") val videoEncoder: String? = null,
)

/** POST /v/api/v1/play/record request (progress reporting). */
@Serializable
data class FeiniuPlayRecordRequest(
    @SerialName("item_guid") val itemGuid: String,
    @SerialName("media_guid") val mediaGuid: String,
    @SerialName("video_guid") val videoGuid: String,
    @SerialName("audio_guid") val audioGuid: String,
    @SerialName("subtitle_guid") val subtitleGuid: String? = null,
    @SerialName("resolution") val resolution: String,
    @SerialName("bitrate") val bitrate: Long,
    @SerialName("ts") val timestampSeconds: Long,
    @SerialName("duration") val durationSeconds: Long,
    @SerialName("play_link") val playLink: String? = null,
)
