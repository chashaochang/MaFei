package cn.mafei.cmp.providers.feiniu.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    @SerialName("sources") val sources: List<FeiniuUserSource> = emptyList(),
)

@Serializable
data class FeiniuUserSource(
    @SerialName("source") val source: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
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
    @SerialName("local_number_of_seasons") val localNumberOfSeasons: Int? = null,
    @SerialName("local_number_of_episodes") val localNumberOfEpisodes: Int? = null,
    @SerialName("single_child_guid") val singleChildGuid: String? = null,
    @SerialName("play_item_guid") val playItemGuid: String? = null,
    @SerialName("media_guid") val mediaGuid: String? = null,
    @SerialName("video_guid") val videoGuid: String? = null,
    @SerialName("audio_guid") val audioGuid: String? = null,
    @SerialName("subtitle_guid") val subtitleGuid: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    @SerialName("genres") val genres: List<Int> = emptyList(),
    @SerialName("logos") val logos: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("media_stream") val mediaStream: FeiniuItemStream? = null,
)

@Serializable
data class FeiniuItemStream(
    @SerialName("resolutions") val resolutions: List<String> = emptyList(),
    @SerialName("audio_type")
    @Serializable(with = FeiniuStringValuesSerializer::class)
    val audioTypes: List<String> = emptyList(),
    @SerialName("color_range_type")
    @Serializable(with = FeiniuStringValuesSerializer::class)
    val colorRangeTypes: List<String> = emptyList(),
    @SerialName("progress") val progress: Double? = null,
    @SerialName("current_time") val currentTimeSeconds: Double? = null,
)

/** fnOS emits these catalog tags as either one string or an array of strings. */
data object FeiniuStringValuesSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())

    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<String>) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonArray(value.map(::JsonPrimitive)))
        } else {
            delegate.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): List<String> {
        if (decoder !is JsonDecoder) return delegate.deserialize(decoder)
        val element = decoder.decodeJsonElement()
        val values = when (element) {
            JsonNull -> emptyList()
            is JsonPrimitive -> listOf(element.content)
            is JsonArray -> element.map { value ->
                (value as? JsonPrimitive)?.content
                    ?: throw SerializationException("Expected string in fnOS catalog tag array")
            }
            else -> throw SerializationException("Expected string or string array for fnOS catalog tags")
        }
        return values.map(String::trim).filter(String::isNotEmpty).distinct()
    }
}

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

/** Item detail payload used by the legacy Feiniu catalog aggregation. */
@Serializable
data class FeiniuItemDetail(
    @SerialName("guid") val guid: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("poster") val poster: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("parent_guid") val parentGuid: String? = null,
    @SerialName("parent_title") val parentTitle: String? = null,
    @SerialName("ancestor_guid") val ancestorGuid: String? = null,
    @SerialName("ancestor_name") val ancestorName: String? = null,
    @SerialName("ancestor_category") val ancestorCategory: String? = null,
    @SerialName("tv_title") val tvTitle: String? = null,
    @SerialName("posters") val posters: String? = null,
    @SerialName("backdrops") val backdrops: String? = null,
    @SerialName("logos") val logos: String? = null,
    @SerialName("vote_average") val voteAverage: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    @SerialName("content_ratings") val contentRatings: String? = null,
    @SerialName("production_countries") val productionCountries: List<String> = emptyList(),
    @SerialName("genres") val genres: List<Int> = emptyList(),
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("local_number_of_seasons") val localNumberOfSeasons: Int? = null,
    @SerialName("local_number_of_episodes") val localNumberOfEpisodes: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("trim_id") val trimId: String? = null,
    @SerialName("poster_width") val posterWidth: Int? = null,
    @SerialName("poster_height") val posterHeight: Int? = null,
    @SerialName("watched") val watched: Int? = null,
    @SerialName("is_watched") val isWatched: Int? = null,
    @SerialName("watched_ts") val watchedEpochSeconds: Long? = null,
    @SerialName("is_favorite") val isFavorite: Int? = null,
    @SerialName("ts") val playbackPositionSeconds: Long? = null,
    @SerialName("duration") val durationSeconds: Long? = null,
    @SerialName("runtime") val runtimeMinutes: Int? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("media_guid") val mediaGuid: String? = null,
    @SerialName("video_guid") val videoGuid: String? = null,
    @SerialName("audio_guid") val audioGuid: String? = null,
    @SerialName("subtitle_guid") val subtitleGuid: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("single_child_guid") val singleChildGuid: String? = null,
    @SerialName("play_item_guid") val playItemGuid: String? = null,
    @SerialName("can_play") val canPlay: Int? = null,
    @SerialName("play_error") val playError: String? = null,
    @SerialName("logic_type") val logicType: Int? = null,
    @SerialName("media_stream") val mediaStream: FeiniuItemStream? = null,
)

/** GET/POST `/v/api/v1/person/list/{itemGuid}` credit. */
@Serializable
data class FeiniuPersonCredit(
    @SerialName("item_guid") val itemGuid: String? = null,
    @SerialName("person_guid") val personGuid: String? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("job") val job: String? = null,
    @SerialName("order") val order: Int? = null,
    @SerialName("department") val department: String? = null,
    @SerialName("trim_id") val trimId: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tmdb_id") val tmdbId: Long? = null,
    @SerialName("lan") val language: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("also_know_as") val alsoKnownAs: String? = null,
    @SerialName("biography") val biography: String? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("gender") val gender: Int? = null,
)

@Serializable
data class FeiniuPersonListData(
    @SerialName("list") val list: List<FeiniuPersonCredit> = emptyList(),
    @SerialName("total") val total: Int? = null,
)

/** GET `/v/api/v1/person/{guid}`. */
@Serializable
data class FeiniuPersonDetail(
    @SerialName("guid") val guid: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("profile") val profile: String? = null,
    @SerialName("biography") val biography: String? = null,
    @SerialName("is_favorite") val isFavorite: Int? = null,
)

@Serializable
data class FeiniuPersonItemListRequest(
    @SerialName("person_guid") val personGuid: String,
    @SerialName("page") val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("job") val job: String,
    @SerialName("sort_column") val sortColumn: String,
    @SerialName("sort_type") val sortType: String,
)

@Serializable
data class FeiniuPersonItem(
    @SerialName("item_guid") val itemGuid: String? = null,
    @SerialName("person_guid") val personGuid: String? = null,
    @SerialName("guid") val guid: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("poster") val poster: String? = null,
    @SerialName("vote_average") val voteAverage: String? = null,
    @SerialName("watched") val watched: Int? = null,
    @SerialName("is_favorite") val isFavorite: Int? = null,
    @SerialName("media_stream") val mediaStream: FeiniuItemStream? = null,
    @SerialName("job") val job: String? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
)

@Serializable
data class FeiniuPersonItemListData(
    @SerialName("total") val total: Int? = null,
    @SerialName("list") val list: List<FeiniuPersonItem> = emptyList(),
)

@Serializable
data class FeiniuGenreItem(
    @SerialName("id") val id: Int? = null,
    @SerialName("value") val value: String? = null,
)

@Serializable
data class FeiniuItemListData(
    @SerialName("list") val list: List<FeiniuMediaItem> = emptyList(),
    @SerialName("total") val total: Int? = null,
    @SerialName("dir") val dir: String? = null,
    @SerialName("jump_list") val jumpList: List<String> = emptyList(),
    @SerialName("mdb_category") val mediaDatabaseCategory: String? = null,
    @SerialName("mdb_name") val mediaDatabaseName: String? = null,
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

/** One file/version from `/stream/list` (same fields consumed by the ArkUI catalog mapper). */
@Serializable
data class FeiniuStreamFileInfo(
    @SerialName("guid") val guid: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("size") val sizeBytes: Long? = null,
    @SerialName("timestamp") val timestamp: Long? = null,
    @SerialName("type") val type: Int? = null,
    @SerialName("sort_num") val sortNumber: Int? = null,
    @SerialName("can_play") val canPlay: Int? = null,
    @SerialName("play_error") val playError: String? = null,
    @SerialName("create_time") val createTime: Long? = null,
    @SerialName("update_time") val updateTime: Long? = null,
    @SerialName("file_birth_time") val fileBirthTime: Long? = null,
    @SerialName("progress_thumb_hash_dir") val progressThumbHashDir: String? = null,
    @SerialName("duration") val durationSeconds: Double? = null,
    @SerialName("direct_link_qualities") val directLinkQualities: List<FeiniuDirectLinkQuality> = emptyList(),
)

@Serializable
data class FeiniuVideoStream(
    @SerialName("media_guid") val mediaGuid: String? = null,
    @SerialName("guid") val guid: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("index") val index: Int? = null,
    @SerialName("resolution_type") val resolutionType: String? = null,
    @SerialName("color_range_type") val colorRangeType: String? = null,
    @SerialName("color_range") val colorRange: String? = null,
    @SerialName("codec_name") val codecName: String? = null,
    @SerialName("codec_type") val codecType: String? = null,
    @SerialName("profile") val profile: String? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("coded_width") val codedWidth: Int? = null,
    @SerialName("coded_height") val codedHeight: Int? = null,
    @SerialName("display_aspect_ratio") val displayAspectRatio: String? = null,
    @SerialName("pix_fmt") val pixelFormat: String? = null,
    @SerialName("level") val level: JsonElement? = null,
    @SerialName("color_space") val colorSpace: String? = null,
    @SerialName("color_transfer") val colorTransfer: String? = null,
    @SerialName("color_primaries") val colorPrimaries: String? = null,
    @SerialName("dv_profile") val dolbyVisionProfile: Int? = null,
    @SerialName("bits_per_raw_sample") val bitsPerRawSample: JsonElement? = null,
    @SerialName("wrapper") val wrapper: String? = null,
    @SerialName("duration") val durationSeconds: Double? = null,
    @SerialName("bps") val bitRate: Int? = null,
    @SerialName("fps") val fps: JsonElement? = null,
    @SerialName("frame_rate") val frameRate: JsonElement? = null,
    @SerialName("avg_frame_rate") val averageFrameRate: JsonElement? = null,
    @SerialName("r_frame_rate") val rawFrameRate: JsonElement? = null,
    @SerialName("progressive") val progressive: Int? = null,
    @SerialName("bit_depth") val bitDepth: Int? = null,
    @SerialName("refs") val referenceFrames: Int? = null,
    @SerialName("reference_frames") val referenceFramesFallback: Int? = null,
    @SerialName("rotation") val rotation: Int? = null,
    @SerialName("is_bluray") val isBluray: Boolean? = null,
)

@Serializable
data class FeiniuAudioStream(
    @SerialName("media_guid") val mediaGuid: String? = null,
    @SerialName("guid") val guid: String? = null,
    @SerialName("index") val index: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("audio_type") val audioType: String? = null,
    @SerialName("codec_name") val codecName: String? = null,
    @SerialName("codec_type") val codecType: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("channels") val channels: Int? = null,
    @SerialName("profile") val profile: String? = null,
    @SerialName("sample_rate") val sampleRate: JsonElement? = null,
    @SerialName("is_default") val isDefault: Int? = null,
    @SerialName("channel_layout") val channelLayout: String? = null,
    @SerialName("duration") val durationSeconds: Double? = null,
    @SerialName("bps") val bitRate: Int? = null,
    @SerialName("is_fake") val isFake: Boolean? = null,
)

@Serializable
data class FeiniuSubtitleStream(
    @SerialName("media_guid") val mediaGuid: String? = null,
    @SerialName("guid") val guid: String? = null,
    @SerialName("index") val index: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("codec_name") val codecName: String? = null,
    @SerialName("codec_type") val codecType: String? = null,
    @SerialName("format") val format: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("forced") val forced: Int? = null,
    @SerialName("is_default") val isDefault: Int? = null,
    @SerialName("is_external") val isExternal: Int? = null,
    @SerialName("trim_id") val trimId: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("Source") val source: String? = null,
    @SerialName("create_time") val createTime: Long? = null,
    @SerialName("update_time") val updateTime: Long? = null,
    @SerialName("extra_file") val extraFile: Int? = null,
    @SerialName("is_bitmap") val isBitmap: Int? = null,
    @SerialName("file_size") val fileSizeBytes: Long? = null,
)

@Serializable
data class FeiniuItemFile(
    @SerialName("media_guid") val mediaGuid: String? = null,
    @SerialName("item_guid") val itemGuid: String? = null,
    @SerialName("filename") val fileName: String? = null,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("size") val sizeBytes: Long? = null,
    @SerialName("file_size") val fileSizeBytes: Long? = null,
    @SerialName("duration") val durationSeconds: Double? = null,
    @SerialName("create_time") val createTime: Long? = null,
    @SerialName("update_time") val updateTime: Long? = null,
    @SerialName("file_birth_time") val fileBirthTime: Long? = null,
    @SerialName("timestamp") val timestamp: Long? = null,
    @SerialName("media_stream") val mediaStream: FeiniuItemStream? = null,
)

/** One quality candidate returned by fnOS's official `/stream` endpoint. */
@Serializable
data class FeiniuDirectLinkQuality(
    @SerialName("bitrate") val bitrate: Long? = null,
    @SerialName("resolution") val resolution: String? = null,
    @SerialName("progressive") val progressive: Boolean? = null,
    @SerialName("is_m3u8") val isM3u8: Boolean? = null,
    @SerialName("url") val url: String? = null,
)

@Serializable
data class FeiniuOfficialStreamRequestHeader(
    @SerialName("User-Agent") val userAgent: List<String>,
)

@Serializable
data class FeiniuOfficialStreamRequest(
    @SerialName("media_guid") val mediaGuid: String,
    @SerialName("ip") val ip: String,
    @SerialName("header") val header: FeiniuOfficialStreamRequestHeader,
    @SerialName("level") val level: Int,
)

@Serializable
data class FeiniuOfficialStreamData(
    @SerialName("file_stream") val fileStream: FeiniuStreamFileInfo? = null,
    @SerialName("video_stream") val videoStream: FeiniuVideoStream? = null,
    @SerialName("audio_streams") val audioStreams: List<FeiniuAudioStream> = emptyList(),
    @SerialName("subtitle_streams") val subtitleStreams: List<FeiniuSubtitleStream> = emptyList(),
    /** Preserve every upstream header; fnOS may add provider-specific names. */
    @SerialName("header") val header: JsonObject? = null,
    @SerialName("direct_link_qualities") val directLinkQualities: List<FeiniuDirectLinkQuality> = emptyList(),
    @SerialName("qualities") val qualities: List<FeiniuDirectLinkQuality> = emptyList(),
)

/** Typed stream catalog; fields and fallback behavior mirror the legacy provider. */
@Serializable
data class FeiniuStreamListData(
    @SerialName("files") val files: List<FeiniuStreamFileInfo> = emptyList(),
    @SerialName("video_streams") val videoStreams: List<FeiniuVideoStream> = emptyList(),
    @SerialName("audio_streams") val audioStreams: List<FeiniuAudioStream> = emptyList(),
    @SerialName("subtitle_streams") val subtitleStreams: List<FeiniuSubtitleStream> = emptyList(),
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
    @SerialName("audio_guid") val audioGuid: String? = null,
    @SerialName("media_guid") val mediaGuid: String? = null,
    @SerialName("play_link") val playLink: String? = null,
    @SerialName("subtitle_link") val subtitleLink: String? = null,
    @SerialName("is_subtitle_external") val isSubtitleExternal: Int? = null,
    @SerialName("audio_index") val audioIndex: Int? = null,
    @SerialName("video_index") val videoIndex: Int? = null,
    @SerialName("subtitle_index") val subtitleIndex: Int? = null,
    @SerialName("subtitle_guid") val subtitleGuid: String? = null,
    @SerialName("hls_time") val hlsTime: Int? = null,
    @SerialName("non_fatal_errno") val nonFatalErrno: Int? = null,
    @SerialName("video_encoder") val videoEncoder: String? = null,
    @SerialName("video_guid") val videoGuid: String? = null,
    @SerialName("not_supported_resolution") val notSupportedResolution: String? = null,
    @SerialName("supported_highest_resolution") val supportedHighestResolution: String? = null,
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
