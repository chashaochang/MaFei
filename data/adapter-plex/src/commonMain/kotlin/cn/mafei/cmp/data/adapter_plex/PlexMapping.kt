package cn.mafei.cmp.data.adapter_plex

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.providers.plex.core.*
import kotlinx.serialization.json.*

internal class PlexMapping(
    private val client: PlexClient,
    private val serverId: ServerId,
) {
    fun item(row: JsonObject): MediaItem? {
        val id = row.text("ratingKey")?.takeIf(::plexId) ?: return null
        val type = when (row.text("type")) {
            "movie" -> MediaType.Movie
            "show" -> MediaType.Series
            "season" -> MediaType.Season
            "episode" -> MediaType.Episode
            else -> return null
        }
        val title = row.text("title")?.takeIf { it.isNotBlank() } ?: return null
        val played = (row.number("viewCount") ?: 0) > 0
        val duration = row.number("duration")?.takeIf { it >= 0 }
        val position = row.number("viewOffset")?.coerceAtLeast(0) ?: 0L
        fun ref(key: String) = row.text(key)?.takeIf(::plexId)?.let(::MediaId)
        return MediaItem(
            id = MediaId(id), serverId = serverId, provider = ProviderKind.Plex,
            title = title, mediaType = type, originalTitle = row.text("originalTitle"),
            overview = row.text("summary"), year = row.number("year")?.toInt(),
            collectionId = row.text("librarySectionID")?.takeIf(::plexId)?.let(::CollectionId),
            parentId = ref("parentRatingKey"),
            seriesId = when (type) {
                MediaType.Episode -> ref("grandparentRatingKey")
                MediaType.Season -> ref("parentRatingKey")
                else -> null
            },
            seriesTitle = when (type) {
                MediaType.Episode -> row.text("grandparentTitle")
                MediaType.Season -> row.text("parentTitle")
                else -> null
            },
            seasonId = if (type == MediaType.Episode) ref("parentRatingKey") else null,
            seasonTitle = if (type == MediaType.Episode) row.text("parentTitle") else null,
            seasonNumber = when (type) {
                MediaType.Episode -> row.number("parentIndex")?.toInt()
                MediaType.Season -> row.number("index")?.toInt()
                else -> null
            },
            episodeNumber = if (type == MediaType.Episode) row.number("index")?.toInt() else null,
            childCount = row.number("childCount")?.toInt(),
            runtimeMs = duration,
            dateAddedEpochMs = row.number("addedAt")?.takeIf { it in 0..Long.MAX_VALUE / 1000 }?.times(1000),
            communityRating = row.text("rating")?.toDoubleOrNull()?.takeIf { it.isFinite() },
            officialRating = row.text("contentRating"),
            genres = row.rows("Genre").mapNotNull { it.text("tag") },
            people = row.rows("Role").mapNotNull {
                it.text("tag")?.let { name -> PersonRef(name = name, role = it.text("role")) }
            },
            images = listOfNotNull(
                image(row.text("thumb") ?: row.text("parentThumb"), ImageKind.Primary),
                image(row.text("art") ?: row.text("grandparentArt"), ImageKind.Backdrop),
            ),
            userData = MediaUserData(
                isPlayed = played, playCount = row.number("viewCount")?.toInt(),
                playbackPositionMs = position,
                playedPercentage = duration?.takeIf { it > 0 }?.let { (position * 100.0 / it).coerceIn(0.0, 100.0) },
                lastPlayedEpochMs = row.number("lastViewedAt")?.takeIf { it in 0..Long.MAX_VALUE / 1000 }?.times(1000),
            ),
            canPlay = type == MediaType.Movie || type == MediaType.Episode,
            mediaSources = row.rows("Media").map(::source),
        )
    }

    fun image(key: String?, kind: ImageKind = ImageKind.Primary): MediaImage? =
        client.resourceUrl(key)?.let { MediaImage(kind, it.toEncodedString()) }

    fun source(row: JsonObject): MediaSourceInfo {
        val parts = row.rows("Part")
        val part = parts.singleOrNull()
        return MediaSourceInfo(
            id = row.text("id"), container = row.text("container"),
            sizeBytes = part?.number("size"), path = part?.text("file"),
            runTimeMs = row.number("duration"),
            playable = part != null && client.resourceUrl(part.text("key")) != null,
            streams = part?.rows("Stream").orEmpty().map { stream ->
                MediaStreamInfo(
                    index = stream.number("index")?.toInt() ?: stream.number("id")?.toInt(),
                    kind = when (stream.number("streamType")) {
                        1L -> MediaStreamKind.Video
                        2L -> MediaStreamKind.Audio
                        3L -> MediaStreamKind.Subtitle
                        else -> MediaStreamKind.Other
                    },
                    codec = stream.text("codec"), language = stream.text("languageCode") ?: stream.text("language"),
                    title = stream.text("title"), displayTitle = stream.text("displayTitle"),
                    isDefault = stream.text("selected") in listOf("1", "true"),
                    isForced = stream.text("forced") in listOf("1", "true"),
                    isExternal = stream.text("key") != null,
                    channels = stream.number("channels")?.toInt(),
                    widthPx = stream.number("width")?.toInt(),
                    heightPx = stream.number("height")?.toInt(),
                    videoRangeType = stream.text("DOVIPresent")?.takeIf { it == "1" }?.let { "DOVI" }
                        ?: stream.text("colorTrc")?.takeIf { it == "smpte2084" }?.let { "HDR10" },
                )
            },
        )
    }
}

internal fun plexId(value: String): Boolean =
    value.isNotEmpty() && value.length <= 32 && value.all { it in '0'..'9' }
