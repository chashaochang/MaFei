package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The season document that was visible before an episode detail route took over the provider detail slot. Returning to that route should reveal this document
 * immediately; a background refresh can update it later without replacing the whole season page with a loading state.
 */
private data class SeasonRestoreSnapshot(
    val series: MediaItem,
    val seasons: List<MediaItem>,
    val seasonId: MediaId,
    val season: MediaItem?,
    val episodes: List<MediaItem>,
    val seriesPlayTarget: MediaItem?,
    val seasonPlayTarget: MediaItem?,
)

internal class DetailBrowser(context: BrowserContext, private val playbackPreferences: MediaPlaybackPreferences?) : BrowserController(context) {
    val generation: Long
        get() = detailTicket

    private var detailTicket = 0L
    private var detailRefreshTicket = 0L
    private var seasonRestoreSnapshot: SeasonRestoreSnapshot? = null

    suspend fun openDetail(id: MediaId, initialItem: MediaItem? = null) {
        val previous = state.value
        val previousDetail = previous.detail
        val previousSeasonId = previous.selectedSeasonId
        if (previousDetail?.mediaType == MediaType.Series && previous.detailEpisodes.any { it.id == id } && previousSeasonId != null) {
            seasonRestoreSnapshot =
                SeasonRestoreSnapshot(
                    series = previousDetail,
                    seasons = previous.detailSeasons,
                    seasonId = previousSeasonId,
                    season = previous.selectedSeasonDetail,
                    episodes = previous.detailEpisodes,
                    seriesPlayTarget = previous.detailPlayTarget,
                    seasonPlayTarget = previous.selectedSeasonPlayTarget,
                )
        }
        val operation = ++detailTicket
        ++detailRefreshTicket
        mutableState.value =
            state.value.copy(
                detailId = id,
                // Keep the tapped card as the loading document. Besides avoiding a
                // blank hero while the request is in flight, this gives the shared
                // artwork transition a destination element during the route change.
                detail = initialItem?.takeIf { it.id == id },
                detailLoading = true,
                detailError = null,
                detailSeasons = emptyList(),
                selectedSeasonId = null,
                selectedSeasonDetail = null,
                detailEpisodes = emptyList(),
                detailPlayTarget = null,
                selectedSeasonPlayTarget = null,
                detailSectionsLoading = false,
                detailSectionsError = null,
                detailActionBusy = false,
                detailActionError = null,
                selectedMediaSourceId = null,
                selectedAudioStreamIndex = null,
                selectedSubtitleStreamIndex = null,
            )
        try {
            val loaded = repository.item(session, id, refresh = true)
            if (operation != detailTicket) return
            val playableSources = loaded.value.mediaSources.filter { it.playable != false }
            // 旧版按「账号 + 媒体条目」恢复版本；找不到时明确选择第一条可播源，
            // 起播时也会把这个 source id 交给 Provider，而不是交回服务器猜测。
            var preferredSourceId: String? = null
            if (playableSources.size > 1) {
                val remembered =
                    try {
                        playbackPreferences?.preferredMediaSourceId(session.accountId, id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                preferredSourceId = remembered?.takeIf { candidate -> playableSources.any { it.id == candidate } }
            }
            val selectedSource = playableSources.firstOrNull { it.id == preferredSourceId } ?: playableSources.firstOrNull()
            mutableState.value =
                state.value.copy(
                    detail = loaded.value,
                    detailLoading = false,
                    detailError = if (loaded.origin == MediaLoadOrigin.StaleCache) "连接暂不可用，显示缓存详情" else null,
                    selectedMediaSourceId = selectedSource?.id,
                    selectedAudioStreamIndex = selectedSource?.legacyDefaultAudioStreamIndex(),
                    selectedSubtitleStreamIndex = selectedSource?.legacyDefaultSubtitleStreamIndex(),
                    detailPlayTarget = loaded.value.takeIf { it.mediaType != MediaType.Series && it.canPlay },
                )
            if (loaded.value.mediaType == MediaType.Series) {
                loadSeriesSections(id, loaded.value, operation)
            }
        } catch (cancelled: CancellationException) {
            if (operation == detailTicket) mutableState.value = state.value.copy(detailLoading = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailTicket) mutableState.value = state.value.copy(detailLoading = false, detailError = browseError(failure))
        }
    }

    /**
     * Refreshes the detail currently on screen without replacing its document state. This is used when a child route returns to a movie/series/episode: the old
     * detail, scroll position, sections and actions remain available while the provider catches up in the background.
     */
    suspend fun refreshDetail(id: MediaId) {
        val beforeRequest = state.value
        if (beforeRequest.detail?.id != id || beforeRequest.detailLoading) return
        val operation = ++detailRefreshTicket
        val detailGeneration = detailTicket
        try {
            val loaded = repository.item(session, id, refresh = true)
            if (operation != detailRefreshTicket || detailGeneration != detailTicket || state.value.detail?.id != id) return
            // A transient network failure may be represented by the repository
            // as a stale-cache success. The same detail is already visible, so
            // publishing an error row would only move the page for no new data.
            if (loaded.origin == MediaLoadOrigin.StaleCache) return

            val current = state.value
            val detail = loaded.value
            val playableSources = detail.mediaSources.filter { it.playable != false }
            val selectedSource = playableSources.firstOrNull { it.id == current.selectedMediaSourceId } ?: playableSources.firstOrNull()
            val sourceUnchanged = selectedSource?.id == current.selectedMediaSourceId
            mutableState.value =
                current.copy(
                    detail = detail,
                    detailError = null,
                    selectedMediaSourceId = selectedSource?.id,
                    // A background data refresh must not silently undo a track the
                    // user selected on this same source.
                    selectedAudioStreamIndex =
                        if (sourceUnchanged) {
                            current.selectedAudioStreamIndex
                        } else {
                            selectedSource?.legacyDefaultAudioStreamIndex()
                        },
                    selectedSubtitleStreamIndex =
                        if (sourceUnchanged) {
                            current.selectedSubtitleStreamIndex
                        } else {
                            selectedSource?.legacyDefaultSubtitleStreamIndex()
                        },
                    detailPlayTarget =
                        if (detail.mediaType == MediaType.Series) {
                            current.detailPlayTarget
                        } else {
                            detail.takeIf { it.canPlay }
                        },
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Background refresh failures leave the presented detail exactly as
            // it was. Explicit retry still uses [openDetail] and exposes errors.
        }
    }

    /**
     * Restores the season that was visible immediately before an episode was opened. This is intentionally synchronous and local: the season route must not
     * flash a full-page loading state just because its child route used the shared detail slot.
     *
     * @return true when a matching in-memory season snapshot was restored.
     */
    fun restoreSeasonContext(seriesId: MediaId, seasonId: MediaId): Boolean {
        val snapshot = seasonRestoreSnapshot ?: return false
        if (snapshot.series.id != seriesId || snapshot.seasonId != seasonId) return false

        val currentEpisode = state.value.detail?.takeIf { item -> snapshot.episodes.any { it.id == item.id } }
        val episodes =
            if (currentEpisode == null) {
                snapshot.episodes
            } else {
                snapshot.episodes.map { episode -> if (episode.id == currentEpisode.id) currentEpisode else episode }
            }
        ++detailTicket
        ++detailRefreshTicket
        mutableState.value =
            state.value.copy(
                detailId = snapshot.series.id,
                detail = snapshot.series,
                detailLoading = false,
                detailError = null,
                detailSeasons = snapshot.seasons,
                selectedSeasonId = snapshot.seasonId,
                selectedSeasonDetail = snapshot.season ?: snapshot.seasons.firstOrNull { it.id == snapshot.seasonId },
                detailEpisodes = episodes,
                detailPlayTarget = snapshot.seriesPlayTarget,
                selectedSeasonPlayTarget = snapshot.seasonPlayTarget,
                detailSectionsLoading = false,
                detailSectionsError = null,
                detailActionBusy = false,
                detailActionError = null,
                selectedMediaSourceId = null,
                selectedAudioStreamIndex = null,
                selectedSubtitleStreamIndex = null,
            )
        seasonRestoreSnapshot = null
        return true
    }

    suspend fun selectSeason(id: MediaId) {
        val current = state.value
        val seriesId = current.detail?.takeIf { it.mediaType == MediaType.Series }?.id ?: return
        if (current.detailSeasons.none { it.id == id }) return
        val operation = detailTicket
        mutableState.value =
            current.copy(
                selectedSeasonId = id,
                selectedSeasonDetail = null,
                detailEpisodes = emptyList(),
                selectedSeasonPlayTarget = null,
                detailSectionsLoading = true,
                detailSectionsError = null,
            )
        val seasonDetail =
            try {
                repository.item(session, id, refresh = true).value
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        if (operation != detailTicket || state.value.selectedSeasonId != id) return
        mutableState.value = state.value.copy(selectedSeasonDetail = seasonDetail)
        loadEpisodes(
            seriesId = seriesId,
            seasonId = id,
            operation = operation,
            playbackTarget = seasonDetail?.playbackTarget,
            preferProgressFallback = true,
            updateSeriesTarget = false,
        )
    }

    private suspend fun loadSeriesSections(seriesId: MediaId, detail: MediaItem, operation: Long) {
        mutableState.value = state.value.copy(detailSectionsLoading = true, detailSectionsError = null)
        try {
            val (loaded, providerNextUp) =
                coroutineScope {
                    val seasons =
                        async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                            repository.browse(session, MediaQuery.Seasons(seriesId), refresh = true)
                        }
                    val nextUp =
                        async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                            if (detail.playbackTarget != null) return@async null
                            try {
                                repository.browse(session, MediaQuery.NextUp(seriesId), refresh = true).value.items.firstOrNull()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // Legacy preferredSeriesTarget deliberately treats a
                                // failed Next Up lookup as "no target".
                                null
                            }
                        }
                    seasons.await() to nextUp.await()
                }
            if (operation != detailTicket) return
            val seasons = loaded.value.items.sortedWith(compareBy<MediaItem> { it.seasonNumber ?: Int.MAX_VALUE }.thenBy { it.title })
            val playbackTarget = detail.playbackTarget ?: providerNextUp?.toPlaybackTarget()
            val selected =
                playbackTarget?.seasonNumber?.let { targetSeason -> seasons.firstOrNull { it.seasonNumber == targetSeason } } ?: seasons.firstOrNull()
            mutableState.value =
                state.value.copy(
                    detailSeasons = seasons,
                    selectedSeasonId = selected?.id,
                    selectedSeasonDetail = null,
                    detailSectionsLoading = selected != null,
                    detailSectionsError = null,
                )
            if (selected != null) {
                loadEpisodes(
                    seriesId = seriesId,
                    seasonId = selected.id,
                    operation = operation,
                    playbackTarget = playbackTarget,
                    preferProgressFallback = false,
                    updateSeriesTarget = true,
                )
            } else {
                val directTarget =
                    providerNextUp
                        ?: playbackTarget?.id?.let { id ->
                            try {
                                repository.item(session, id, refresh = true).value
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            }
                        }
                if (operation == detailTicket) {
                    mutableState.value = state.value.copy(detailPlayTarget = directTarget, detailSectionsLoading = false)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailTicket) {
                mutableState.value = state.value.copy(detailSectionsLoading = false, detailSectionsError = browseError(failure))
            }
        }
    }

    private suspend fun loadEpisodes(
        seriesId: MediaId,
        seasonId: MediaId,
        operation: Long,
        playbackTarget: MediaPlaybackTarget?,
        preferProgressFallback: Boolean,
        updateSeriesTarget: Boolean,
    ) {
        try {
            // Legacy requests one 1000-item window. PageRequest is capped at
            // 500, so consume at most two provider pages and intentionally
            // ignore any remaining cursor rather than inventing UI pagination.
            val loaded = repository.browse(session, MediaQuery.Episodes(seriesId, seasonId), PageRequest(pageSize = 500), refresh = true)
            if (operation != detailTicket || state.value.selectedSeasonId != seasonId) return
            val freshPages = MediaPageAccumulator()
            var page = freshPages.append(null, loaded.value)
            page.nextCursor?.let { secondCursor ->
                val second = repository.browse(session, MediaQuery.Episodes(seriesId, seasonId), PageRequest(cursor = secondCursor, pageSize = 500))
                if (operation != detailTicket || state.value.selectedSeasonId != seasonId) return
                page = freshPages.append(secondCursor, second.value)
            }
            val episodes = page.items.sortedWith(compareBy<MediaItem> { it.episodeNumber ?: Int.MAX_VALUE }.thenBy { it.title })
            val playTarget = resolveEpisodePlaybackTarget(episodes, playbackTarget, preferProgressFallback)
            mutableState.value =
                state.value.copy(
                    detailEpisodes = episodes,
                    detailPlayTarget = if (updateSeriesTarget) playTarget else state.value.detailPlayTarget,
                    selectedSeasonPlayTarget = playTarget,
                    detailSectionsLoading = false,
                    detailSectionsError = null,
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailTicket && state.value.selectedSeasonId == seasonId) {
                mutableState.value = state.value.copy(detailSectionsLoading = false, detailSectionsError = browseError(failure))
            }
        }
    }

    fun back() {
        ++detailTicket
        ++detailRefreshTicket
        mutableState.value =
            state.value.copy(
                detailId = null,
                detail = null,
                detailLoading = false,
                detailError = null,
                detailSeasons = emptyList(),
                selectedSeasonId = null,
                selectedSeasonDetail = null,
                detailEpisodes = emptyList(),
                detailPlayTarget = null,
                selectedSeasonPlayTarget = null,
                detailSectionsLoading = false,
                detailSectionsError = null,
                detailActionBusy = false,
                detailActionError = null,
                selectedMediaSourceId = null,
                selectedAudioStreamIndex = null,
                selectedSubtitleStreamIndex = null,
            )
    }
}
