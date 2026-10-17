package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.providers.feiniu.core.FeiniuDigest
import cn.mafei.cmp.providers.feiniu.core.FeiniuIdentity
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemDetail
import cn.mafei.cmp.providers.feiniu.core.FeiniuMediaItem
import cn.mafei.cmp.providers.feiniu.core.FeiniuOfficialStreamRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuOfficialStreamRequestHeader
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayInfoData
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayItem
import cn.mafei.cmp.providers.feiniu.core.FeiniuStreamListData
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

/** Shared hierarchy, stream metadata and resume enrichment; no second media state store. */
internal class FeiniuMediaDetails(private val adapterSession: FeiniuAdapterSession) {
    private val resolution get() = adapterSession.resolution
    private val serverId get() = adapterSession.serverId

    suspend fun item(id: MediaId): MediaItem {
        val detail = adapterSession.translate { adapterSession.facade.catalog.itemDetail(resolution, id.value) }
        // Legacy guard: a detail payload for another guid is not this item.
        if (detail.guid?.trim()?.equals(id.value, ignoreCase = false) == false) {
            throw ResourceNotFoundError(resource = "feiniu item $id (server returned ${detail.guid})")
        }
        val mediaType = detail.type.toFeiniuMediaType()
        return coroutineScope {
            val peopleDeferred = async(start = CoroutineStart.UNDISPATCHED) { optionalPeople(id.value) }
            val playInfoDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                if (mediaType.supportsFeiniuPlayInfo()) optionalPlayInfo(id.value) else null
            }
            val streamDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                if (mediaType.supportsFeiniuDetailStreams()) {
                    detailStreams(detail, playInfoDeferred.await())
                } else {
                    cn.mafei.cmp.providers.feiniu.core.FeiniuStreamListData()
                }
            }
            val genresDeferred = async(start = CoroutineStart.UNDISPATCHED) { adapterSession.genreNames() }
            val contextDeferred = async(start = CoroutineStart.UNDISPATCHED) { detailContext(detail, mediaType) }
            detail.toMediaItem(
                serverId = serverId,
                imageUrl = adapterSession::imageUrl,
                people = peopleDeferred.await(),
                streams = streamDeferred.await(),
                itemFiles = emptyList(),
                playInfo = playInfoDeferred.await(),
                genreNames = genresDeferred.await(),
                context = contextDeferred.await(),
            )
        }
            ?: throw ResourceNotFoundError(resource = "feiniu item $id")
    }

    suspend fun enrichedResumeItems(
        source: List<FeiniuPlayItem>,
        mediaTypes: Set<MediaType>,
    ): List<MediaItem> {
        val enriched = coroutineScope {
            source.filter { item ->
                item.guid.isNotBlank() &&
                    (mediaTypes.isEmpty() || item.toFeiniuPlayMediaType() in mediaTypes)
            }.map { item ->
                async(start = CoroutineStart.UNDISPATCHED) { resumeMediaItem(item) }
            }.awaitAll()
        }.filterNotNull()
        val seen = mutableSetOf<String>()
        return enriched.filter { item ->
            val seriesId = item.seriesId
            val identity = if (item.mediaType == MediaType.Episode && seriesId != null) {
                "series:${seriesId.value}"
            } else {
                "item:${item.id.value}"
            }
            seen.add(identity)
        }
    }

    private suspend fun optionalPeople(itemGuid: String) = try {
        adapterSession.translate { adapterSession.facade.catalog.people(resolution, itemGuid) }.list
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (authentication: AuthenticationError) {
        throw authentication
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun optionalPlayInfo(itemGuid: String) = try {
        adapterSession.translate { adapterSession.facade.playback.playInfo(resolution, itemGuid, pinnedOperation = adapterSession.currentOperation()) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (authentication: AuthenticationError) {
        throw authentication
    } catch (_: Exception) {
        null
    }

    private suspend fun optionalStreamList(itemGuid: String) = try {
        adapterSession.translate { adapterSession.facade.playback.streamList(resolution, itemGuid, beforePlay = 1) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (authentication: AuthenticationError) {
        throw authentication
    } catch (_: Exception) {
        cn.mafei.cmp.providers.feiniu.core.FeiniuStreamListData()
    }

    private suspend fun optionalItemFiles(itemGuid: String) = try {
        adapterSession.translate { adapterSession.facade.catalog.itemFiles(resolution, itemGuid) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (authentication: AuthenticationError) {
        throw authentication
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun detailStreams(
        detail: FeiniuItemDetail,
        playInfo: FeiniuPlayInfoData?,
    ): FeiniuStreamListData = coroutineScope {
        val listedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            optionalStreamList(detail.guid.orEmpty())
        }
        val itemFilesDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            optionalItemFiles(detail.guid.orEmpty())
        }
        val listed = mergeFeiniuItemFiles(listedDeferred.await(), itemFilesDeferred.await())
        val preferredMediaGuid = playInfo?.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
            ?: detail.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
        val mediaGuids = feiniuDetailMediaGuids(detail, listed, preferredMediaGuid)
        if (mediaGuids.isEmpty()) return@coroutineScope preferFeiniuMediaFile(listed, preferredMediaGuid)

        val operation = adapterSession.currentOperation()
        val ip = try {
            val userInfo = adapterSession.translate { adapterSession.facade.auth.userInfo(resolution, pinnedOperation = operation) }
            FeiniuDigest.md5(userInfo.sources.firstOrNull()?.sourceName?.trim().orEmpty())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@coroutineScope preferFeiniuMediaFile(listed, preferredMediaGuid)
        }
        val official = mediaGuids.map { mediaGuid ->
            async(start = CoroutineStart.UNDISPATCHED) {
                try {
                    mediaGuid to adapterSession.translate {
                        adapterSession.facade.playback.officialStream(
                            resolution = resolution,
                            request = FeiniuOfficialStreamRequest(
                                mediaGuid = mediaGuid,
                                ip = ip,
                                header = FeiniuOfficialStreamRequestHeader(listOf(FeiniuIdentity.USER_AGENT)),
                                level = 1,
                            ),
                            pinnedOperation = operation,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
        preferFeiniuMediaFile(mergeFeiniuOfficialStreams(detail, listed, official), preferredMediaGuid)
    }

    suspend fun latestMediaItem(
        entry: FeiniuLatestLibraryItem,
        genreNames: Map<Int, String>,
    ): MediaItem? {
        val item = entry.item
        val kind = item.type.toFeiniuMediaType()
        val hierarchyNeedsDetail = kind in setOf(MediaType.Episode, MediaType.Season) && item.parentGuid.isNullOrBlank()
        val imageNeedsDetail = item.stillPath.isNullOrBlank() && item.poster.isNullOrBlank()
        val detail = if (hierarchyNeedsDetail || imageNeedsDetail) adapterSession.optionalItemDetail(item.guid) else null
        val effective = detail?.toRecommendationMediaItem(item) ?: item
        val base = effective.toMediaItem(serverId, adapterSession::imageUrl, entry.collectionId, genreNames) ?: return null
        val context = detailContext(effective.toContextDetail(detail), kind)
        return base.withFeiniuContext(context, useSeriesTitleForEpisode = true)
    }

    /** Mechanical equivalent of legacy `enrichCatalogItem` + mapper.resume. */
    private suspend fun resumeMediaItem(item: FeiniuPlayItem): MediaItem? {
        val stateBase = item.toMediaItem(serverId, adapterSession::imageUrl) ?: return null
        val fallback = item.toRecommendationMediaItem()
        val kind = stateBase.mediaType
        val hierarchyNeedsDetail = kind in setOf(MediaType.Episode, MediaType.Season) && fallback.parentGuid.isNullOrBlank()
        val imageNeedsDetail = fallback.stillPath.isNullOrBlank() && fallback.poster.isNullOrBlank()
        val detail = if (hierarchyNeedsDetail || imageNeedsDetail) adapterSession.optionalItemDetail(item.guid) else null
        val effective = detail?.toRecommendationMediaItem(fallback) ?: fallback
        val mapped = effective.toMediaItem(serverId, adapterSession::imageUrl)
        val base = (mapped ?: stateBase).copy(
            userData = stateBase.userData,
            runtimeMs = stateBase.runtimeMs ?: mapped?.runtimeMs,
            canPlay = if (detail != null) mapped?.canPlay ?: stateBase.canPlay else stateBase.canPlay,
        )
        val context = detailContext(effective.toContextDetail(detail), kind)
        return base.withFeiniuContext(context, useSeriesTitleForEpisode = true)
    }

    /** Same parent/series fallback walk as the legacy `itemContext()`. */
    private suspend fun detailContext(detail: FeiniuItemDetail, kind: cn.mafei.cmp.core.domain.MediaType): FeiniuDetailContext {
        var seriesId: String? = null
        var seasonId: String? = null
        var seriesTitle = detail.tvTitle?.trim()?.takeIf { it.isNotEmpty() }
        var seasonTitle: String? = null
        var primary = detail.feiniuPrimaryImagePath()
        var backdrop = detail.backdrops?.trim()?.takeIf { it.isNotEmpty() }
        var logo = detail.logos?.trim()?.takeIf { it.isNotEmpty() }
        var seriesDetail: FeiniuItemDetail? = null

        when (kind) {
            cn.mafei.cmp.core.domain.MediaType.Series -> {
                seriesId = detail.guid?.trim()?.takeIf { it.isNotEmpty() }
                seriesTitle = seriesTitle ?: detail.title?.trim()?.takeIf(String::isNotEmpty)
            }
            cn.mafei.cmp.core.domain.MediaType.Season -> {
                val parent = detail.parentGuid?.trim().orEmpty()
                val library = detail.ancestorGuid?.trim().orEmpty()
                seriesId = parent.takeIf { it.isNotEmpty() && it != library }
                seasonTitle = detail.title?.trim()?.takeIf(String::isNotEmpty)
            }
            cn.mafei.cmp.core.domain.MediaType.Episode -> {
                val parentId = detail.parentGuid?.trim().orEmpty()
                val parent = adapterSession.optionalItemDetail(parentId)
                if (parent?.type.toFeiniuMediaType() == cn.mafei.cmp.core.domain.MediaType.Series) {
                    seriesId = parent?.guid?.trim()?.takeIf { it.isNotEmpty() } ?: parentId.takeIf { it.isNotEmpty() }
                    seriesTitle = seriesTitle
                        ?: parent?.title?.trim()?.takeIf(String::isNotEmpty)
                        ?: detail.parentTitle?.trim()?.takeIf(String::isNotEmpty)
                    seriesDetail = parent
                } else {
                    seasonId = parentId.takeIf { it.isNotEmpty() }
                    seasonTitle = parent?.title?.trim()?.takeIf(String::isNotEmpty)
                    val seriesParent = parent?.parentGuid?.trim().orEmpty()
                    val library = parent?.ancestorGuid?.trim().orEmpty()
                    seriesId = seriesParent.takeIf { it.isNotEmpty() && it != library }
                    seriesTitle = seriesTitle ?: parent?.parentTitle?.trim()?.takeIf(String::isNotEmpty)
                    primary = primary ?: parent?.feiniuPrimaryImagePath()
                    backdrop = backdrop ?: parent?.backdrops?.trim()?.takeIf { it.isNotEmpty() }
                    logo = logo ?: parent?.logos?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
            else -> Unit
        }
        if (seriesDetail == null && !seriesId.isNullOrBlank() && seriesId != detail.guid) {
            seriesDetail = adapterSession.optionalItemDetail(seriesId)
        }
        seriesDetail?.let { series ->
            seriesTitle = seriesTitle ?: series.title?.trim()?.takeIf(String::isNotEmpty)
            primary = primary ?: series.feiniuPrimaryImagePath()
            backdrop = backdrop ?: series.backdrops?.trim()?.takeIf { it.isNotEmpty() }
            logo = logo ?: series.logos?.trim()?.takeIf { it.isNotEmpty() }
        }
        primary = primary ?: backdrop
        backdrop = backdrop ?: primary
        return FeiniuDetailContext(
            seriesId = seriesId,
            seasonId = seasonId,
            seriesTitle = seriesTitle,
            seasonTitle = seasonTitle,
            primaryImagePath = primary,
            backdropImagePath = backdrop,
            logoImagePath = logo,
        )
    }

    /** Builds the same fallback-filled shape that legacy `itemContext()` read. */
    private fun FeiniuMediaItem.toContextDetail(source: FeiniuItemDetail?): FeiniuItemDetail = FeiniuItemDetail(
        guid = guid,
        title = title,
        type = type,
        originalTitle = source?.originalTitle,
        overview = overview,
        poster = poster,
        stillPath = stillPath,
        status = status,
        parentGuid = parentGuid,
        parentTitle = parentTitle,
        ancestorGuid = ancestorGuid,
        ancestorName = ancestorName,
        ancestorCategory = ancestorCategory,
        tvTitle = tvTitle,
        posters = stillPath?.takeIf(String::isNotBlank)
            ?: poster?.takeIf(String::isNotBlank)
            ?: source?.posters,
        backdrops = source?.backdrops,
        logos = logos?.takeIf(String::isNotBlank) ?: source?.logos,
        voteAverage = voteAverage,
        releaseDate = releaseDate,
        airDate = airDate,
        firstAirDate = firstAirDate,
        lastAirDate = lastAirDate,
        contentRatings = source?.contentRatings,
        productionCountries = source?.productionCountries.orEmpty(),
        genres = genres,
        numberOfSeasons = numberOfSeasons,
        numberOfEpisodes = numberOfEpisodes,
        localNumberOfSeasons = localNumberOfSeasons,
        localNumberOfEpisodes = localNumberOfEpisodes,
        watched = watched,
        watchedEpochSeconds = watchedEpochSeconds,
        isFavorite = isFavorite,
        durationSeconds = durationSeconds,
        runtimeMinutes = runtimeMinutes,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        mediaGuid = mediaGuid,
        videoGuid = videoGuid,
        audioGuid = audioGuid,
        subtitleGuid = subtitleGuid,
        fileName = fileName,
        singleChildGuid = singleChildGuid,
        playItemGuid = playItemGuid,
        canPlay = canPlay,
        mediaStream = mediaStream,
    )

    private fun MediaItem.withFeiniuContext(
        context: FeiniuDetailContext,
        useSeriesTitleForEpisode: Boolean,
    ): MediaItem {
        val contextBackdrop = context.backdropImagePath?.trim()?.takeIf(String::isNotEmpty)
        val contextLogo = context.logoImagePath?.trim()?.takeIf(String::isNotEmpty)
        val retainedImages = images.filterNot { image ->
            (image.kind == ImageKind.Backdrop && contextBackdrop != null) ||
                (image.kind == ImageKind.Logo && contextLogo != null)
        }
        val additions = buildList {
            fun add(kind: ImageKind, path: String?) {
                val normalized = path?.trim()?.takeIf(String::isNotEmpty) ?: return
                if (retainedImages.none { it.kind == kind }) this.add(MediaImage(kind, adapterSession.imageUrl(normalized)))
            }
            add(ImageKind.Primary, context.primaryImagePath)
            add(ImageKind.Backdrop, contextBackdrop)
            add(ImageKind.Logo, contextLogo)
        }
        return copy(
            title = if (useSeriesTitleForEpisode && mediaType == MediaType.Episode) {
                context.seriesTitle?.takeIf(String::isNotBlank) ?: title
            } else {
                title
            },
            seriesId = context.seriesId?.takeIf(String::isNotBlank)?.let(::MediaId),
            seriesTitle = context.seriesTitle?.takeIf(String::isNotBlank),
            seasonId = context.seasonId?.takeIf(String::isNotBlank)?.let(::MediaId),
            seasonTitle = context.seasonTitle?.takeIf(String::isNotBlank),
            images = retainedImages + additions,
        )
    }

    private fun cn.mafei.cmp.core.domain.MediaType.supportsFeiniuPlayInfo(): Boolean = when (this) {
        cn.mafei.cmp.core.domain.MediaType.Movie,
        cn.mafei.cmp.core.domain.MediaType.Series,
        cn.mafei.cmp.core.domain.MediaType.Season,
        cn.mafei.cmp.core.domain.MediaType.Episode,
        cn.mafei.cmp.core.domain.MediaType.Video -> true
        else -> false
    }

    private fun cn.mafei.cmp.core.domain.MediaType.supportsFeiniuDetailStreams(): Boolean = when (this) {
        cn.mafei.cmp.core.domain.MediaType.Movie,
        cn.mafei.cmp.core.domain.MediaType.Episode,
        cn.mafei.cmp.core.domain.MediaType.Video -> true
        else -> false
    }
}

private fun FeiniuItemDetail.feiniuPrimaryImagePath(): String? =
    listOf(stillPath, poster, posters)
        .firstNotNullOfOrNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }
