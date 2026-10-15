package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.*

internal const val HomeLatestPageSize = 30
internal const val CatalogPageSize = 100
internal const val LegacySearchLimit = 800
internal const val SearchFirstSegmentSize = PageRequest.MAX_PAGE_SIZE
internal val SearchMediaTypes = setOf(MediaType.Episode, MediaType.Movie, MediaType.Video, MediaType.Series)
internal const val PersonWorkPageSize = 10
internal const val HistoryPageSize = 10
internal const val MineHistoryPreviewSourceLimit = 24
internal const val HistoryEnrichmentConcurrency = 4
internal const val HistorySeriesCacheTtlMs = 5L * 60L * 1_000L
internal const val HistoryLoadError = "播放历史加载失败，点击重试"
internal val HistoryMediaTypes = setOf(MediaType.Movie, MediaType.Episode)
internal val FavoriteMediaTypes = setOf(MediaType.Movie, MediaType.Series, MediaType.Episode, MediaType.Video)
