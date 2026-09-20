package cn.mafei.cmp.core.domain

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * 展示风格标记。
 *
 * ## 设计约定（数据驱动 / MVVM）
 *
 * **展示文本的格式由 model 层定义，provider 只提供风格标记。**
 * 适配器取回原始字段后，按本文件给出的字段语义与期望格式填值/派生；UI 只渲染 model
 * 暴露的展示字段，**不得再按 `ProviderKind` 分支自行格式化**。
 *
 * 每个枚举项的 KDoc 写明它对应的期望输出格式（含分隔符与空值行为），该描述即验收标准。
 *
 * ## 新 provider 如何接入
 *
 * [MediaDisplayStyle.forProvider] 的 `when` 是穷尽的：新增 [ProviderKind] 取值会**编译失败**，
 * 强制开发者在该处显式选一套风格。若某个 provider 的服务器/库命名与默认风格不符，
 * 适配器可对单条数据写入 [MediaItem.displayStyleOverride] 做局部覆盖。
 */
@Serializable
data class MediaDisplayStyle(
    /** 卡片文案风格，见 [CardTextStyle]。 */
    val cardStyle: CardTextStyle = CardTextStyle.EpisodeAware,
    /** Home hero 副标题的构成，见 [HeroSubtitleStyle]。 */
    val heroSubtitleStyle: HeroSubtitleStyle = HeroSubtitleStyle.TypeThenYear,
    /** 详情页主按钮上「第几季第几集」的写法，见 [EpisodeLabelStyle]。 */
    val episodeLabelStyle: EpisodeLabelStyle = EpisodeLabelStyle.LongForm,
    /** 卡片副标题是否使用剧集编号前缀，见 [EpisodeCodeStyle]。 */
    val episodeCodeStyle: EpisodeCodeStyle = EpisodeCodeStyle.SeriesCode,
    /** 列表页可选的排序字段，UI 按此渲染排序菜单；空表示只提供默认排序。 */
    val sortableFields: List<MediaSortField> = DefaultSortFields,
) {
    companion object {
        /** 缺省风格：[CardTextStyle.EpisodeAware] + [HeroSubtitleStyle.TypeThenYear] + [EpisodeLabelStyle.LongForm] + [EpisodeCodeStyle.SeriesCode]。 */
        val Default = MediaDisplayStyle()

        /**
         * provider → 风格表。**穷尽 `when`，新增 [ProviderKind] 会编译失败**——这是刻意设计，
         * 目的是让「接入新 provider」必须在类型系统里做出选择，而不是静默落到某一侧。
         */
        fun forProvider(provider: ProviderKind): MediaDisplayStyle = when (provider) {
            ProviderKind.Jellyfin -> Default
            ProviderKind.Feiniu -> MediaDisplayStyle(
                cardStyle = CardTextStyle.Plain,
                heroSubtitleStyle = HeroSubtitleStyle.YearOnly,
                episodeLabelStyle = EpisodeLabelStyle.Compact,
                episodeCodeStyle = EpisodeCodeStyle.None,
            )
        }
    }
}

/**
 * 卡片文案风格。
 *
 * - [EpisodeAware]：剧集卡片**期望格式**为两行——主标题取剧名，副标题取季/集信息或
 *   「剧名 · 单集名」；「继续观看」里的电影副标题固定为类型词「电影」。
 * - [Plain]：卡片一行主标题 + 一行通用元信息（见 [MediaItem.metaLine]），不区分剧集。
 */
@Serializable
enum class CardTextStyle { EpisodeAware, Plain }

/**
 * Home hero 副标题的构成。
 *
 * - [TypeThenYear]：**期望格式**「电影 · 2024」。仅当 [MediaType.Movie] 时构造
 *   （类型词固定为「电影」，其后跟年份，无年份则只有「电影」）；**非电影类型返回 `null`**。
 * - [YearOnly]：**期望格式**「2024」。任意媒体类型，只有年份；无年份返回 `null`。
 */
@Serializable
enum class HeroSubtitleStyle { TypeThenYear, YearOnly }

/**
 * 详情页主按钮上剧集信息的写法（不含「播放 / 继续播放」本身）。
 *
 * - [LongForm]：**期望格式**「第 1 季  第 2 集」——季与集之间为**两个空格**；缺季或集时只输出存在的部分；都缺返回 `null`。
 * - [Compact]：**期望格式**「季 1 集 2」；只有集时「集 2」；只有季时「季 1」；都缺返回 `null`。
 */
@Serializable
enum class EpisodeLabelStyle { LongForm, Compact }

/**
 * 卡片副标题是否使用剧集编号前缀。
 *
 * - [SeriesCode]：**期望格式**「S1:E2 - 标题」——季/集缺失按 0 处理，标题为空时只有「S1:E2」。
 * - [None]：不使用编号前缀，卡片副标题回退到通用元信息行（见 [MediaItem.metaLine]）。
 */
@Serializable
enum class EpisodeCodeStyle { SeriesCode, None }

/** 旧版 `SortOption.ets` 的完整顺序；媒体库与收藏页共用这一菜单。 */
private val DefaultSortFields = listOf(
    MediaSortField.Default,
    MediaSortField.Title,
    MediaSortField.DateAdded,
    MediaSortField.PremiereDate,
    MediaSortField.DatePlayed,
    MediaSortField.CommunityRating,
    MediaSortField.CriticRating,
    MediaSortField.ProductionYear,
    MediaSortField.PlayCount,
    MediaSortField.Runtime,
    MediaSortField.OfficialRating,
    MediaSortField.Random,
    MediaSortField.Album,
    MediaSortField.Artist,
    MediaSortField.AlbumArtist,
    MediaSortField.AiredEpisodeOrder,
    MediaSortField.SeriesSortName,
    MediaSortField.Studio,
    MediaSortField.IsFavoriteOrLiked,
    MediaSortField.DateLastContentAdded,
    MediaSortField.IsUnplayed,
    MediaSortField.IsPlayed,
)

// ---------------------------------------------------------------------------
// 展示字段：UI 只读以下属性，不得自行格式化，也不得按 provider 分叉
// ---------------------------------------------------------------------------

/**
 * 本条数据采用的展示风格：优先用适配器显式写入的 [MediaItem.displayStyleOverride]，
 * 否则按 [MediaItem.provider] 查 [MediaDisplayStyle.forProvider]。
 */
val MediaItem.displayStyle: MediaDisplayStyle
    get() = displayStyleOverride ?: MediaDisplayStyle.forProvider(provider)

/**
 * 媒体类型词。**期望格式**：电影 / 剧集 / 季 / 单集 / 音乐人 / 专辑 / 音频 / 相册 / 照片 / 合集 / 文件夹 / 视频；
 * [MediaType.Unknown] 返回 `null`（不得编造类型词）。
 */
val MediaItem.typeLabel: String?
    get() = when (mediaType) {
        MediaType.Movie -> "电影"
        MediaType.Series -> "剧集"
        MediaType.Season -> "季"
        MediaType.Episode -> "单集"
        MediaType.MusicArtist -> "音乐人"
        MediaType.MusicAlbum -> "专辑"
        MediaType.Audio -> "音频"
        MediaType.PhotoAlbum -> "相册"
        MediaType.Photo -> "照片"
        MediaType.BoxSet -> "合集"
        MediaType.Folder -> "文件夹"
        MediaType.Video -> "视频"
        MediaType.Unknown -> null
    }

/**
 * 时长。**期望格式**「99 分钟」——分钟向下取整，不输出秒；`runtimeMs` 为空或不大于 0 时返回 `null`
 * （不得用 0 分钟占位）。
 */
val MediaItem.durationLabel: String?
    get() = runtimeMs?.takeIf { it > 0L }?.let { "${it / 60_000} 分钟" }

/**
 * 卡片 / 列表行通用元信息。**期望格式**「2024 · 电影 · 99 分钟」——按「年份、类型词、时长」顺序、
 * 以 ` · ` 连接，逐项省略缺失值；三项皆空时返回「媒体」（不返回空串）。
 */
val MediaItem.metaLine: String
    get() = listOfNotNull(year?.toString(), typeLabel, durationLabel)
        .joinToString(" · ")
        .ifEmpty { "媒体" }

/**
 * Home hero 副标题。**期望格式**由 [MediaDisplayStyle.heroSubtitleStyle] 决定：
 * - [HeroSubtitleStyle.TypeThenYear]：「电影 · 2024」；无年份「电影」；非电影 `null`
 * - [HeroSubtitleStyle.YearOnly]：「2024」；无年份 `null`
 */
val MediaItem.heroSubtitle: String?
    get() = when (displayStyle.heroSubtitleStyle) {
        HeroSubtitleStyle.TypeThenYear -> if (mediaType == MediaType.Movie) {
            buildList {
                add("电影")
                year?.takeIf { it != 0 }?.let { add(it.toString()) }
            }.joinToString(" · ")
        } else {
            null
        }

        HeroSubtitleStyle.YearOnly -> year?.takeIf { it != 0 }?.toString()
    }

/**
 * 详情页剧集副标题（hero 底部、标题上方）。**期望格式**「剧名 · 第 1 季 · 第 2 集」：
 * 依次为剧名（缺失则省略）、季（优先 `seasonTitle`，否则「第 N 季」，皆缺则省略）、
 * 「第 M 集」（`episodeNumber` 缺失按 1 处理）；以 ` · ` 连接。
 * **非 [MediaType.Episode] 返回 `null`**（不输出空串）。
 */
val MediaItem.episodeSubtitle: String?
    get() {
        if (mediaType != MediaType.Episode) return null
        return buildList {
            seriesTitle?.takeIf { it.isNotBlank() }?.let(::add)
            seasonTitle?.takeIf { it.isNotBlank() }?.let(::add)
                ?: seasonNumber?.let { add("第 $it 季") }
            add("第 ${episodeNumber ?: 1} 集")
        }.joinToString(" · ")
    }

/**
 * 卡片上的剧集编号行。**期望格式**：
 * - [EpisodeCodeStyle.SeriesCode]：「S1:E2 - 标题」——季/集缺失按 0 处理，标题为空则只有「S1:E2」；
 *   非 [MediaType.Episode] 返回 `null`
 * - [EpisodeCodeStyle.None]：恒为 `null`（UI 回退到 [metaLine]）
 */
val MediaItem.episodeCardLine: String?
    get() {
        if (displayStyle.episodeCodeStyle != EpisodeCodeStyle.SeriesCode) return null
        if (mediaType != MediaType.Episode) return null
        val code = "S${seasonNumber ?: 0}:E${episodeNumber ?: 0}"
        return title.takeIf { it.isNotBlank() }?.let { "$code - $it" } ?: code
    }

/**
 * 卡片主标题。**期望格式**：剧集取剧名（`seriesTitle`，缺失回退 `title`），其余类型取 `title`。
 */
val MediaItem.cardTitle: String
    get() = if (mediaType == MediaType.Episode) {
        seriesTitle?.takeIf { it.isNotBlank() } ?: title
    } else {
        title
    }

/**
 * 详情页主按钮的剧集信息（不含「播放 / 继续播放」）。**期望格式**由
 * [MediaDisplayStyle.episodeLabelStyle] 决定，见 [EpisodeLabelStyle] 的说明；
 * 剧集信息完全缺失时返回 `null`（调用方回退到「播放」）。
 */
val MediaItem.episodeLabel: String?
    get() {
        val season = seasonNumber
        val episode = episodeNumber
        return when (displayStyle.episodeLabelStyle) {
            EpisodeLabelStyle.LongForm -> buildList {
                season?.let { add("第 $it 季") }
                episode?.let { add("第 $it 集") }
            }.joinToString("  ").takeIf { it.isNotBlank() }

            EpisodeLabelStyle.Compact -> when {
                season != null && episode != null -> "季 $season 集 $episode"
                episode != null -> "集 $episode"
                season != null -> "季 $season"
                else -> null
            }
        }
    }

// ---------------------------------------------------------------------------
// Legacy playback-history presentation
// ---------------------------------------------------------------------------

/**
 * Playback-history title copied from `MineHistoryPoster.titleText()`:
 *
 * - movie/video: item title;
 * - episode in a single-season series: `剧名 · 第N集`;
 * - episode in a multi-season series: `剧名 · 第N季第M集`;
 * - blank title: `未命名内容`.
 */
val PlaybackHistoryItem.titleText: String
    get() {
        val item = media
        val name = if (item.mediaType == MediaType.Episode) {
            item.seriesTitle?.trim()?.takeIf(String::isNotEmpty) ?: item.title.trim()
        } else {
            item.title.trim()
        }.ifEmpty { "未命名内容" }
        val episode = item.episodeNumber?.takeIf { it > 0 }
        if (item.mediaType != MediaType.Episode || episode == null) return name
        val season = item.seasonNumber?.takeIf { it > 0 }
        return if (seriesHasMultipleSeasons && season != null) {
            "$name · 第${season}季第${episode}集"
        } else {
            "$name · 第${episode}集"
        }
    }

/** Legacy movie badge: only finite ratings in `(0, 10]`, rounded to one decimal. */
val PlaybackHistoryItem.movieRating: Double?
    get() = media.communityRating
        ?.takeIf { media.mediaType == MediaType.Movie && it.isFinite() && it > 0.0 && it <= 10.0 }
        ?.let { (it * 10.0).roundToInt() / 10.0 }

/** The exact one-decimal text rendered by the old poster badge. */
val PlaybackHistoryItem.movieRatingText: String?
    get() = movieRating?.let { rating ->
        val tenths = (rating * 10.0).roundToInt()
        "${tenths / 10}.${tenths % 10}"
    }

/**
 * Legacy episode badge. The old resource strings contain no spaces:
 * `更新至N集` / `更新至N季M集`.
 */
val PlaybackHistoryItem.updateBadgeText: String?
    get() {
        if (media.mediaType != MediaType.Episode) return null
        val episode = latestEpisodeNumber?.takeIf { it > 0 } ?: return null
        val season = latestSeasonNumber?.takeIf { it > 0 }
        return if (seriesHasMultipleSeasons && season != null) {
            "更新至${season}季${episode}集"
        } else {
            "更新至${episode}集"
        }
    }

/**
 * Legacy progress normalization: use provider percentage when present,
 * otherwise derive it from position/runtime, round to an integer and clamp to
 * 0..100. Completed rows and zero progress have no progress bar.
 */
val PlaybackHistoryItem.progressPercent: Int?
    get() {
        if (media.userData.isPlayed) return null
        val raw = media.userData.playedPercentage
            ?: media.runtimeMs?.takeIf { it > 0L }?.let { runtime ->
                media.userData.playbackPositionMs.toDouble() * 100.0 / runtime.toDouble()
            }
            ?: 0.0
        if (!raw.isFinite()) return null
        return raw.roundToInt().coerceIn(0, 100).takeIf { it > 0 }
    }
