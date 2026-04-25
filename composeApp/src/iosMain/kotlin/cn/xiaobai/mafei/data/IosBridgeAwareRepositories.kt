package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.activeIosRuntimeBridge
import cn.xiaobai.mafei.data.jellyfin.JellyfinErrorCode
import cn.xiaobai.mafei.data.jellyfin.JellyfinLoadIssue
import cn.xiaobai.mafei.logging.AppLogger
import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.storage.SessionRecord
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.Foundation.NSUserDefaults
import platform.posix.time

private const val HOME_META_KEY = "cn.xiaobai.mafei.kmp.bridge.home.meta"
private const val HOME_CONTINUE_KEY = "cn.xiaobai.mafei.kmp.bridge.home.continue"
private const val HOME_NEXT_UP_KEY = "cn.xiaobai.mafei.kmp.bridge.home.nextup"
private const val HOME_UPDATES_KEY = "cn.xiaobai.mafei.kmp.bridge.home.updates"
private const val HOME_LATEST_KEY = "cn.xiaobai.mafei.kmp.bridge.home.latest"
private const val DETAIL_META_KEY_PREFIX = "cn.xiaobai.mafei.kmp.bridge.detail.meta."
private const val DETAIL_EPISODES_KEY_PREFIX = "cn.xiaobai.mafei.kmp.bridge.detail.episodes."
private const val DETAIL_REQUEST_QUEUE_KEY = "cn.xiaobai.mafei.kmp.bridge.detail.requests"
private const val SEARCH_REQUEST_KEY = "cn.xiaobai.mafei.kmp.bridge.search.request"
private const val SEARCH_META_KEY = "cn.xiaobai.mafei.kmp.bridge.search.meta"
private const val SEARCH_ITEMS_KEY = "cn.xiaobai.mafei.kmp.bridge.search.items"
private const val MEDIA_VIEW_META_KEY = "cn.xiaobai.mafei.kmp.bridge.mediaView.meta"
private const val MEDIA_VIEW_VIEWS_KEY = "cn.xiaobai.mafei.kmp.bridge.mediaView.views"
private const val MEDIA_VIEW_ITEMS_KEY_PREFIX = "cn.xiaobai.mafei.kmp.bridge.mediaView.items."
private const val MEDIA_VIEW_REQUEST_KEY = "cn.xiaobai.mafei.kmp.bridge.mediaView.request"
private const val FAVORITES_REQUEST_KEY = "cn.xiaobai.mafei.kmp.bridge.favorites.request"
private const val FAVORITES_META_KEY = "cn.xiaobai.mafei.kmp.bridge.favorites.meta"
private const val FAVORITES_ITEMS_KEY = "cn.xiaobai.mafei.kmp.bridge.favorites.items"
private const val FAVORITES_MUTATION_REQUEST_KEY = "cn.xiaobai.mafei.kmp.bridge.favorites.mutation.request"
private const val FAVORITES_MUTATION_RESULT_KEY = "cn.xiaobai.mafei.kmp.bridge.favorites.mutation.result"
private const val PLAYBACK_REQUEST_KEY = "cn.xiaobai.mafei.kmp.bridge.playback.request"
private const val PLAYBACK_META_KEY = "cn.xiaobai.mafei.kmp.bridge.playback.meta"
private const val FIELD_SEP = '\u001F'
private const val RECORD_SEP = '\u001E'

class IosBridgeAwareHomeRepository(
    private val fallbackRepository: HomeRepository = FakeHomeRepository(FakeVideoRepository()),
) : HomeRepository {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun loadHomeState(
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): HomeState {
        val runtime = activeIosRuntimeBridge()
        val baseUrl = (runtime?.baseUrl ?: defaultServer?.baseUrl).orEmpty().redactedBaseUrl()
        val userId = (runtime?.userId ?: session?.userId).redactedIdentifier()
        AppLogger.debug(
            tag = "IosHomeBridge",
            message = "loadHomeState baseUrl=$baseUrl userId=$userId",
        )
        val fallbackState = fallbackRepository.loadHomeState(defaultServer, session)
        val snapshot = loadHomeSnapshot(
            defaults = defaults,
            defaultServer = defaultServer,
            session = session,
        )
        if (snapshot == null) {
            AppLogger.warn(
                tag = "IosHomeBridge",
                message = "home snapshot miss, fallback repository used baseUrl=$baseUrl userId=$userId",
            )
            return fallbackState
        }
        AppLogger.info(
            tag = "IosHomeBridge",
            message = "home snapshot hit continue=${snapshot.continueWatching.size} nextUp=${snapshot.nextUp.size} views=${snapshot.mediaViews.size} latest=${snapshot.latestAdded.size} baseUrl=$baseUrl userId=$userId",
        )

        return fallbackState.copy(
            continueWatching = snapshot.continueWatching.ifEmpty { fallbackState.continueWatching },
            nextUp = snapshot.nextUp.ifEmpty { fallbackState.nextUp },
            mediaViews = snapshot.mediaViews.ifEmpty { fallbackState.mediaViews },
            updates = snapshot.updates.ifEmpty { fallbackState.updates },
            latestAdded = snapshot.latestAdded.ifEmpty { fallbackState.latestAdded },
            loadIssue = null,
        )
    }
}

class IosBridgeAwareDetailRepository(
    private val fallbackRepository: DetailRepository = FakeDetailRepository(FakeVideoRepository()),
) : DetailRepository {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun loadDetail(
        itemId: String,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): VideoDetail? {
        val runtime = activeIosRuntimeBridge()
        val baseUrl = (runtime?.baseUrl ?: defaultServer?.baseUrl).orEmpty().redactedBaseUrl()
        val userId = (runtime?.userId ?: session?.userId).redactedIdentifier()
        AppLogger.debug(
            tag = "IosDetailBridge",
            message = "loadDetail itemId=${itemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
        )
        val fallback = fallbackRepository.loadDetail(itemId, defaultServer, session)
            ?: buildPlaceholderDetail(itemId)
        val snapshot = loadDetailSnapshot(
            defaults = defaults,
            itemId = itemId,
            defaultServer = defaultServer,
            session = session,
        )
        if (snapshot != null) {
            if (snapshot.loadIssue != null) {
                AppLogger.warn(
                    tag = "IosDetailBridge",
                    message = "detail failure snapshot hit itemId=${itemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId retryable=${snapshot.loadIssue.retryable}",
                )
            } else {
                AppLogger.info(
                    tag = "IosDetailBridge",
                    message = "detail snapshot hit itemId=${itemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
                )
            }
            return snapshot
        }

        if (enqueueDetailSnapshotRequest(defaults, itemId, defaultServer, session)) {
            AppLogger.debug(
                tag = "IosDetailBridge",
                message = "detail snapshot request enqueued itemId=${itemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
            )
            repeat(10) {
                delay(180)
                loadDetailSnapshot(
                    defaults = defaults,
                    itemId = itemId,
                    defaultServer = defaultServer,
                    session = session,
                )?.let { requestedSnapshot ->
                    if (requestedSnapshot.loadIssue != null) {
                        AppLogger.warn(
                            tag = "IosDetailBridge",
                            message = "detail native failure snapshot ready itemId=${itemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId retryable=${requestedSnapshot.loadIssue.retryable}",
                        )
                    } else {
                        AppLogger.info(
                            tag = "IosDetailBridge",
                            message = "detail native snapshot ready itemId=${itemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
                        )
                    }
                    return requestedSnapshot
                }
            }
            AppLogger.warn(
                tag = "IosDetailBridge",
                message = "detail native snapshot timed out itemId=${itemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
            )
        } else {
            AppLogger.warn(
                tag = "IosDetailBridge",
                message = "detail request enqueue skipped itemId=${itemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
            )
        }

        val context = buildBridgeContextLabel(defaultServer, session) ?: run {
            AppLogger.warn(
                tag = "IosDetailBridge",
                message = "detail fallback without bridge context itemId=${itemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
            )
            return fallback
        }
        AppLogger.warn(
            tag = "IosDetailBridge",
            message = "detail fallback with bridge context itemId=${itemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
        )

        return fallback.copy(
            metaLine = "${fallback.metaLine} · $context",
            synopsis = "iOS native detail snapshot is not available for this item yet.\n\n${fallback.synopsis}",
        )
    }
}

class IosBridgeAwareSearchRepository(
    private val fakeVideoRepository: FakeVideoRepository = FakeVideoRepository(),
) : SearchRepository {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun search(
        keyword: String,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): SearchResult {
        val trimmedKeyword = keyword.trim()
        val runtime = activeIosRuntimeBridge()
        val baseUrl = (runtime?.baseUrl ?: defaultServer?.baseUrl).orEmpty().redactedBaseUrl()
        val userId = (runtime?.userId ?: session?.userId).redactedIdentifier()
        AppLogger.debug(
            tag = "IosSearchBridge",
            message = "search start keywordLength=${trimmedKeyword.length} baseUrl=$baseUrl userId=$userId",
        )
        val fallback = SearchResult(items = fakeVideoRepository.search(trimmedKeyword))

        loadSearchSnapshot(
            defaults = defaults,
            keyword = trimmedKeyword,
            defaultServer = defaultServer,
            session = session,
        )?.let { snapshot ->
            if (snapshot.loadIssue != null) {
                AppLogger.warn(
                    tag = "IosSearchBridge",
                    message = "search failure snapshot hit keywordLength=${trimmedKeyword.length} itemCount=${snapshot.items.size} baseUrl=$baseUrl userId=$userId retryable=${snapshot.loadIssue.retryable}",
                )
            } else {
                AppLogger.info(
                    tag = "IosSearchBridge",
                    message = "search snapshot hit keywordLength=${trimmedKeyword.length} itemCount=${snapshot.items.size} baseUrl=$baseUrl userId=$userId",
                )
            }
            return SearchResult(items = snapshot.items, loadIssue = snapshot.loadIssue)
        }

        if (trimmedKeyword.isNotEmpty() && enqueueSearchSnapshotRequest(defaults, trimmedKeyword, defaultServer, session)) {
            AppLogger.debug(
                tag = "IosSearchBridge",
                message = "search snapshot request enqueued keywordLength=${trimmedKeyword.length} baseUrl=$baseUrl userId=$userId",
            )
            repeat(10) {
                delay(150)
                loadSearchSnapshot(
                    defaults = defaults,
                    keyword = trimmedKeyword,
                    defaultServer = defaultServer,
                    session = session,
                )?.let { snapshot ->
                    if (snapshot.loadIssue != null) {
                        AppLogger.warn(
                            tag = "IosSearchBridge",
                            message = "search native failure snapshot ready keywordLength=${trimmedKeyword.length} itemCount=${snapshot.items.size} baseUrl=$baseUrl userId=$userId retryable=${snapshot.loadIssue.retryable}",
                        )
                    } else {
                        AppLogger.info(
                            tag = "IosSearchBridge",
                            message = "search native snapshot ready keywordLength=${trimmedKeyword.length} itemCount=${snapshot.items.size} baseUrl=$baseUrl userId=$userId",
                        )
                    }
                    return SearchResult(items = snapshot.items, loadIssue = snapshot.loadIssue)
                }
            }
            AppLogger.warn(
                tag = "IosSearchBridge",
                message = "search native snapshot timed out keywordLength=${trimmedKeyword.length} baseUrl=$baseUrl userId=$userId",
            )
        } else if (trimmedKeyword.isNotEmpty()) {
            AppLogger.warn(
                tag = "IosSearchBridge",
                message = "search snapshot enqueue skipped keywordLength=${trimmedKeyword.length} baseUrl=$baseUrl userId=$userId",
            )
        }

        val context = buildBridgeContextLabel(defaultServer, session) ?: run {
            AppLogger.warn(
                tag = "IosSearchBridge",
                message = "search fallback without bridge context keywordLength=${trimmedKeyword.length} baseUrl=$baseUrl userId=$userId",
            )
            return fallback
        }
        AppLogger.warn(
            tag = "IosSearchBridge",
            message = "search fallback with bridge context keywordLength=${trimmedKeyword.length} baseUrl=$baseUrl userId=$userId",
        )

        return SearchResult(
            items = fallback.items.mapIndexed { index, item ->
                if (index == 0) {
                    item.copy(subtitle = "${item.subtitle} · $context")
                } else {
                    item
                }
            }
        )
    }
}

class IosBridgeAwareMediaViewRepository(
    private val fallbackRepository: MediaViewRepository = FakeMediaViewRepository(FakeVideoRepository()),
) : MediaViewRepository {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun loadMediaView(
        viewId: String,
        startIndex: Int,
        limit: Int,
        sortOption: MediaViewSortOption,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): MediaViewBrowseResult {
        AppLogger.debug(
            tag = "IosMediaViewBridge",
            message = "loadMediaView viewId=${viewId.redactedIdentifier()} startIndex=$startIndex limit=$limit sort=${sortOption.name}",
        )
        val fallback = fallbackRepository.loadMediaView(
            viewId = viewId,
            startIndex = startIndex,
            limit = limit,
            sortOption = sortOption,
            defaultServer = defaultServer,
            session = session,
        )

        loadMediaViewSnapshot(
            defaults = defaults,
            viewId = viewId,
            sortOption = sortOption,
            startIndex = startIndex,
            limit = limit,
            defaultServer = defaultServer,
            session = session,
        )?.let { snapshot ->
            if (snapshot.loadIssue != null) {
                AppLogger.warn(
                    tag = "IosMediaViewBridge",
                    message = "mediaView failure snapshot hit viewId=${viewId.redactedIdentifier()} startIndex=$startIndex limit=$limit sort=${sortOption.name} totalCount=${snapshot.totalCount} retryable=${snapshot.loadIssue.retryable}",
                )
            } else {
                AppLogger.info(
                    tag = "IosMediaViewBridge",
                    message = "cache hit viewId=${viewId.redactedIdentifier()} startIndex=$startIndex limit=$limit sort=${sortOption.name} totalCount=${snapshot.totalCount}",
                )
            }
            return snapshot
        }

        if (
            enqueueMediaViewSnapshotRequest(
                defaults = defaults,
                viewId = viewId,
                startIndex = startIndex,
                limit = limit,
                sortOption = sortOption,
                defaultServer = defaultServer,
                session = session,
            )
        ) {
            AppLogger.debug(
                tag = "IosMediaViewBridge",
                message = "waiting for native snapshot viewId=${viewId.redactedIdentifier()} startIndex=$startIndex limit=$limit sort=${sortOption.name}",
            )
            repeat(12) {
                delay(150)
                loadMediaViewSnapshot(
                    defaults = defaults,
                    viewId = viewId,
                    sortOption = sortOption,
                    startIndex = startIndex,
                    limit = limit,
                    defaultServer = defaultServer,
                    session = session,
                )?.let { snapshot ->
                    if (snapshot.loadIssue != null) {
                        AppLogger.warn(
                            tag = "IosMediaViewBridge",
                            message = "mediaView native failure snapshot ready viewId=${viewId.redactedIdentifier()} startIndex=$startIndex limit=$limit sort=${sortOption.name} totalCount=${snapshot.totalCount} retryable=${snapshot.loadIssue.retryable}",
                        )
                    } else {
                        AppLogger.info(
                            tag = "IosMediaViewBridge",
                            message = "native snapshot ready viewId=${viewId.redactedIdentifier()} startIndex=$startIndex limit=$limit sort=${sortOption.name} totalCount=${snapshot.totalCount}",
                        )
                    }
                    return snapshot
                }
            }
        }

        AppLogger.warn(
            tag = "IosMediaViewBridge",
            message = "falling back to placeholder repository viewId=${viewId.redactedIdentifier()} startIndex=$startIndex limit=$limit sort=${sortOption.name}",
        )
        return fallback
    }
}

class IosBridgeAwareFavoritesRepository(
    private val fallbackRepository: FavoritesRepository = FakeFavoritesRepository(FakeVideoRepository()),
) : FavoritesRepository {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun loadFavorites(
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): FavoritesState {
        val runtime = activeIosRuntimeBridge()
        val baseUrl = (runtime?.baseUrl ?: defaultServer?.baseUrl).orEmpty().redactedBaseUrl()
        val userId = (runtime?.userId ?: session?.userId).redactedIdentifier()
        AppLogger.debug(
            tag = "IosFavoritesBridge",
            message = "loadFavorites baseUrl=$baseUrl userId=$userId",
        )
        val fallback = fallbackRepository.loadFavorites(defaultServer, session)

        loadFavoritesSnapshot(
            defaults = defaults,
            defaultServer = defaultServer,
            session = session,
        )?.let { snapshot ->
            if (snapshot.loadIssue != null) {
                AppLogger.warn(
                    tag = "IosFavoritesBridge",
                    message = "favorites failure snapshot hit itemCount=${snapshot.items.size} baseUrl=$baseUrl userId=$userId retryable=${snapshot.loadIssue.retryable}",
                )
            } else {
                AppLogger.info(
                    tag = "IosFavoritesBridge",
                    message = "favorites snapshot hit itemCount=${snapshot.items.size} baseUrl=$baseUrl userId=$userId",
                )
            }
            return FavoritesState(items = snapshot.items, loadIssue = snapshot.loadIssue)
        }

        if (enqueueFavoritesSnapshotRequest(defaults, defaultServer, session)) {
            AppLogger.debug(
                tag = "IosFavoritesBridge",
                message = "favorites snapshot request enqueued baseUrl=$baseUrl userId=$userId",
            )
            repeat(10) {
                delay(150)
                loadFavoritesSnapshot(
                    defaults = defaults,
                    defaultServer = defaultServer,
                    session = session,
                )?.let { snapshot ->
                    if (snapshot.loadIssue != null) {
                        AppLogger.warn(
                            tag = "IosFavoritesBridge",
                            message = "favorites native failure snapshot ready itemCount=${snapshot.items.size} baseUrl=$baseUrl userId=$userId retryable=${snapshot.loadIssue.retryable}",
                        )
                    } else {
                        AppLogger.info(
                            tag = "IosFavoritesBridge",
                            message = "favorites native snapshot ready itemCount=${snapshot.items.size} baseUrl=$baseUrl userId=$userId",
                        )
                    }
                    return FavoritesState(items = snapshot.items, loadIssue = snapshot.loadIssue)
                }
            }
        }

        AppLogger.warn(
            tag = "IosFavoritesBridge",
            message = "favorites fallback used baseUrl=$baseUrl userId=$userId",
        )
        return fallback
    }

    override suspend fun updateFavorite(
        itemId: String,
        favorite: Boolean,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): FavoriteMutationResult {
        AppLogger.debug(
            tag = "IosFavoritesBridge",
            message = "updateFavorite request itemId=${itemId.redactedIdentifier()} favorite=$favorite",
        )
        if (
            !enqueueFavoritesMutationRequest(
                defaults = defaults,
                itemId = itemId,
                favorite = favorite,
                defaultServer = defaultServer,
                session = session,
            )
        ) {
            AppLogger.warn(
                tag = "IosFavoritesBridge",
                message = "updateFavorite enqueue failed itemId=${itemId.redactedIdentifier()} favorite=$favorite",
            )
            return FavoriteMutationResult.Failure(
                targetFavorite = favorite,
                loadIssue = JellyfinLoadIssue(
                    errorCode = JellyfinErrorCode.AUTH_FAILED,
                    message = "未登录或无默认服务器，无法同步收藏状态。",
                    retryable = false,
                ),
            )
        }

        repeat(12) {
            delay(150)
            loadFavoritesMutationResult(
                defaults = defaults,
                itemId = itemId,
                favorite = favorite,
                defaultServer = defaultServer,
                session = session,
            )?.let { result ->
                val resultType = when (result) {
                    is FavoriteMutationResult.Success -> "success"
                    is FavoriteMutationResult.Failure -> "failure"
                }
                when (result) {
                    is FavoriteMutationResult.Success -> {
                        AppLogger.info(
                            tag = "IosFavoritesBridge",
                            message = "updateFavorite result received itemId=${itemId.redactedIdentifier()} favorite=$favorite type=$resultType",
                        )
                    }

                    is FavoriteMutationResult.Failure -> {
                        AppLogger.warn(
                            tag = "IosFavoritesBridge",
                            message = "updateFavorite result received itemId=${itemId.redactedIdentifier()} favorite=$favorite type=$resultType code=${result.loadIssue.errorCode.name} retryable=${result.loadIssue.retryable} message=${result.loadIssue.message}",
                        )
                    }
                }
                return result
            }
        }

        AppLogger.warn(
            tag = "IosFavoritesBridge",
            message = "updateFavorite timed out waiting native result itemId=${itemId.redactedIdentifier()} favorite=$favorite",
        )
        return FavoriteMutationResult.Failure(
            targetFavorite = favorite,
            loadIssue = JellyfinLoadIssue(
                errorCode = JellyfinErrorCode.UNKNOWN,
                message = if (favorite) {
                    "收藏请求超时，请稍后重试。"
                } else {
                    "取消收藏请求超时，请稍后重试。"
                },
                retryable = true,
            ),
        )
    }
}

class IosBridgeAwarePlaybackRepository(
    private val fakeVideoRepository: FakeVideoRepository = FakeVideoRepository(),
) : PlaybackRepository {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun loadPlaybackContext(
        itemId: String,
        episodeId: Int,
        playbackItemId: String?,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): PlaybackContext {
        val runtime = activeIosRuntimeBridge()
        val baseUrl = (runtime?.baseUrl ?: defaultServer?.baseUrl).orEmpty().redactedBaseUrl()
        val userId = (runtime?.userId ?: session?.userId).redactedIdentifier()
        AppLogger.debug(
            tag = "IosPlaybackBridge",
            message = "loadPlaybackContext itemId=${itemId.redactedIdentifier()} episodeId=$episodeId playbackItemId=${playbackItemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
        )
        val fallback = FakePlaybackRepository(fakeVideoRepository).loadPlaybackContext(
            itemId = itemId,
            episodeId = episodeId,
            playbackItemId = playbackItemId,
            defaultServer = defaultServer,
            session = session,
        )
        loadPlaybackSnapshot(
            defaults = defaults,
            itemId = itemId,
            episodeId = episodeId,
            playbackItemId = playbackItemId,
            defaultServer = defaultServer,
            session = session,
        )?.let { snapshot ->
            if (snapshot.streamTypeLabel.startsWith("bridge-failure:")) {
                AppLogger.warn(
                    tag = "IosPlaybackBridge",
                    message = "playback failure snapshot hit itemId=${itemId.redactedIdentifier()} episodeId=$episodeId playbackItemId=${playbackItemId.redactedIdentifier()} streamType=${snapshot.streamTypeLabel} baseUrl=$baseUrl userId=$userId",
                )
            } else {
                AppLogger.info(
                    tag = "IosPlaybackBridge",
                    message = "playback snapshot hit itemId=${itemId.redactedIdentifier()} episodeId=$episodeId playbackItemId=${playbackItemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
                )
            }
            return snapshot
        }

        if (
            enqueuePlaybackSnapshotRequest(
                defaults = defaults,
                itemId = itemId,
                episodeId = episodeId,
                playbackItemId = playbackItemId,
                defaultServer = defaultServer,
                session = session,
            )
        ) {
            AppLogger.debug(
                tag = "IosPlaybackBridge",
                message = "playback snapshot request enqueued itemId=${itemId.redactedIdentifier()} episodeId=$episodeId playbackItemId=${playbackItemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
            )
            repeat(12) {
                delay(150)
                loadPlaybackSnapshot(
                    defaults = defaults,
                    itemId = itemId,
                    episodeId = episodeId,
                    playbackItemId = playbackItemId,
                    defaultServer = defaultServer,
                    session = session,
                )?.let { requestedSnapshot ->
                    AppLogger.info(
                        tag = "IosPlaybackBridge",
                        message = "playback native snapshot ready itemId=${itemId.redactedIdentifier()} episodeId=$episodeId playbackItemId=${playbackItemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
                    )
                    return requestedSnapshot
                }
            }
        }

        val context = buildBridgeContextLabel(defaultServer, session) ?: run {
            AppLogger.warn(
                tag = "IosPlaybackBridge",
                message = "playback fallback without bridge context itemId=${itemId.redactedIdentifier()} episodeId=$episodeId playbackItemId=${playbackItemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
            )
            return fallback
        }

        AppLogger.warn(
            tag = "IosPlaybackBridge",
            message = "playback fallback with bridge context itemId=${itemId.redactedIdentifier()} episodeId=$episodeId playbackItemId=${playbackItemId.redactedIdentifier()} baseUrl=$baseUrl userId=$userId",
        )

        return fallback.copy(
            statusMessage = "Using iOS native playback preflight bridge. $context. ${fallback.statusMessage}",
        )
    }
}

private data class HomeSnapshot(
    val continueWatching: List<ContinueWatchingItem>,
    val nextUp: List<NextUpItem>,
    val mediaViews: List<HomeMediaView>,
    val updates: List<UpdateItem>,
    val latestAdded: List<LibraryItem>,
)

private data class DetailSnapshot(
    val title: String,
    val metaLine: String,
    val synopsis: String,
    val isFavorite: Boolean,
    val continueEpisodeId: Int,
    val continueProgressLabel: String,
    val continueProgressPercent: Int,
    val updateCount: Int,
    val episodes: List<EpisodeItem>,
)

private data class SearchSnapshot(
    val items: List<LibraryItem>,
    val loadIssue: JellyfinLoadIssue?,
)

private data class MediaViewSnapshot(
    val viewId: String,
    val title: String,
    val subtitle: String,
    val totalCount: Int,
    val items: List<LibraryItem>,
    val loadIssue: JellyfinLoadIssue?,
)

private data class FavoritesSnapshot(
    val items: List<LibraryItem>,
    val loadIssue: JellyfinLoadIssue?,
)

private data class FavoritesMutationSnapshot(
    val itemId: String,
    val favorite: Boolean,
    val success: Boolean,
    val errorCode: JellyfinErrorCode?,
    val retryable: Boolean,
    val message: String,
)

private data class PlaybackSnapshot(
    val itemId: String,
    val episodeId: Int,
    val requestedPlaybackItemId: String?,
    val playbackItemId: String,
    val showTitle: String,
    val episodeTitle: String,
    val streamUrl: String,
    val streamTypeLabel: String,
    val playSessionId: String?,
    val mediaSourceId: String?,
    val mediaContainer: String?,
    val runtimeLabel: String,
    val startPositionLabel: String,
    val statusMessage: String,
)

private fun loadHomeSnapshot(
    defaults: NSUserDefaults,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): HomeSnapshot? {
    val metaRecord = defaults.stringForKey(HOME_META_KEY).orEmpty()
    if (metaRecord.isBlank()) return null

    val meta = decodeRecord(metaRecord)
    if (meta.size < 5) return null
    val schemaVersion = meta[0].toIntOrNull() ?: return null
    if (schemaVersion != 1) return null

    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId

    if (!expectedBaseUrl.isNullOrBlank() && meta[1] != expectedBaseUrl) return null
    if (!expectedUserId.isNullOrBlank() && meta[2] != expectedUserId) return null

    return HomeSnapshot(
        continueWatching = decodeRecords(defaults.stringForKey(HOME_CONTINUE_KEY))
            .mapNotNull { fields ->
                if (fields.size < 5) return@mapNotNull null
                ContinueWatchingItem(
                    itemId = fields[0],
                    title = fields[1],
                    episodeTitle = fields[2],
                    progressLabel = fields[3],
                    progressPercent = fields[4].toIntOrNull() ?: 0,
                    backdropUrl = fields.getOrNull(5)?.ifBlank { null },
                )
            },
        nextUp = decodeRecords(defaults.stringForKey(HOME_NEXT_UP_KEY))
            .mapNotNull { fields ->
                if (fields.size < 4) return@mapNotNull null
                NextUpItem(
                    itemId = fields[0],
                    title = fields[1],
                    episodeTitle = fields[2],
                    subtitle = fields[3],
                    thumbnailUrl = fields.getOrNull(4)?.ifBlank { null },
                )
            },
        mediaViews = loadMediaViewSummaries(
            defaults = defaults,
            defaultServer = defaultServer,
            session = session,
        ),
        updates = decodeRecords(defaults.stringForKey(HOME_UPDATES_KEY))
            .mapNotNull { fields ->
                if (fields.size < 4) return@mapNotNull null
                UpdateItem(
                    itemId = fields[0],
                    title = fields[1],
                    latestEpisodeTitle = fields[2],
                    newEpisodeCount = fields[3].toIntOrNull() ?: 0,
                    thumbnailUrl = fields.getOrNull(4)?.ifBlank { null },
                )
            },
        latestAdded = decodeRecords(defaults.stringForKey(HOME_LATEST_KEY))
            .mapNotNull { fields ->
                if (fields.size < 3) return@mapNotNull null
                LibraryItem(
                    itemId = fields[0],
                    title = fields[1],
                    subtitle = fields[2],
                    posterUrl = fields.getOrNull(3)?.ifBlank { null },
                )
            },
    )
}

private fun loadMediaViewSummaries(
    defaults: NSUserDefaults,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): List<HomeMediaView> {
    val summaries = loadMediaViewSummaryRecords(defaults, defaultServer, session)
    if (summaries.isEmpty()) return emptyList()

    return summaries.map { summary ->
        val resolvedSubtitle = if (summary.loadIssue != null) {
            "${summary.subtitle} · iOS 分区桥接异常，可重试"
        } else {
            summary.subtitle
        }
        HomeMediaView(
            viewId = summary.viewId,
            title = summary.title,
            subtitle = resolvedSubtitle,
            items = loadStoredMediaViewItems(
                defaults = defaults,
                viewId = summary.viewId,
                sortOption = MediaViewSortOption.RECENT,
            ),
        )
    }
}

private fun loadMediaViewSummaryRecords(
    defaults: NSUserDefaults,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): List<MediaViewSnapshot> {
    val metaRecord = defaults.stringForKey(MEDIA_VIEW_META_KEY).orEmpty()
    if (metaRecord.isBlank()) return emptyList()

    val meta = decodeRecord(metaRecord)
    if (meta.size < 5) return emptyList()
    val schemaVersion = meta[0].toIntOrNull() ?: return emptyList()
    if (schemaVersion != 1) return emptyList()

    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId

    if (!expectedBaseUrl.isNullOrBlank() && meta[1] != expectedBaseUrl) return emptyList()
    if (!expectedUserId.isNullOrBlank() && meta[2] != expectedUserId) return emptyList()

    return decodeRecords(defaults.stringForKey(MEDIA_VIEW_VIEWS_KEY))
        .mapNotNull { fields ->
            if (fields.size < 3) return@mapNotNull null
            val viewId = fields[0]
            val mediaViewFailureReason = fields.getOrNull(4).orEmpty()
                .takeIf { it.startsWith("bridge-failure:") }
                ?.removePrefix("bridge-failure:")
                ?.takeIf { it.isNotBlank() }
            val mediaViewLoadIssue = mediaViewFailureReason?.let { reason ->
                JellyfinLoadIssue(
                    errorCode = JellyfinErrorCode.UNKNOWN,
                    message = mediaViewBridgeFailureMessage(reason),
                    retryable = true,
                )
            }
            MediaViewSnapshot(
                viewId = viewId,
                title = fields[1],
                subtitle = fields[2],
                totalCount = fields.getOrNull(3)?.toIntOrNull() ?: 0,
                items = emptyList(),
                loadIssue = mediaViewLoadIssue,
            )
        }
}

private fun loadDetailSnapshot(
    defaults: NSUserDefaults,
    itemId: String,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): VideoDetail? {
    val metaRecord = defaults.stringForKey(detailMetaKey(itemId)).orEmpty()
    if (metaRecord.isBlank()) return null

    val meta = decodeRecord(metaRecord)
    if (meta.size < 13) return null
    val schemaVersion = meta[0].toIntOrNull() ?: return null
    if (schemaVersion != 1) return null

    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId

    if (!expectedBaseUrl.isNullOrBlank() && meta[1] != expectedBaseUrl) return null
    if (!expectedUserId.isNullOrBlank() && meta[2] != expectedUserId) return null
    if (meta[3] != itemId) return null

    val episodes = decodeRecords(defaults.stringForKey(detailEpisodesKey(itemId)))
        .mapNotNull { fields ->
            if (fields.size < 5) return@mapNotNull null
            EpisodeItem(
                id = fields[0].toIntOrNull() ?: return@mapNotNull null,
                title = fields[1],
                durationLabel = fields[2],
                isNew = fields[3] == "1",
                playbackItemId = fields[4].ifBlank { null },
                thumbnailUrl = fields.getOrNull(5)?.ifBlank { null },
            )
        }

    val snapshot = DetailSnapshot(
        title = meta[4],
        metaLine = meta[5],
        synopsis = meta[6],
        isFavorite = meta[7] == "1",
        continueEpisodeId = meta[8].toIntOrNull() ?: 1,
        continueProgressLabel = meta[9],
        continueProgressPercent = meta[10].toIntOrNull() ?: 0,
        updateCount = meta[11].toIntOrNull() ?: 0,
        episodes = episodes,
    )
    val detailBridgeState = meta.getOrNull(13).orEmpty()
    val detailFailureReason = detailBridgeState
        .takeIf { it.startsWith("bridge-failure:") }
        ?.removePrefix("bridge-failure:")
        ?.takeIf { it.isNotBlank() }
    val failureMessage = detailFailureReason?.let(::detailBridgeFailureMessage)
    val resolvedMetaLine = if (detailFailureReason != null) {
        "${snapshot.metaLine} · iOS 详情桥接异常，可重试"
    } else {
        snapshot.metaLine
    }
    val resolvedSynopsis = when {
        failureMessage.isNullOrBlank() -> snapshot.synopsis
        snapshot.synopsis.contains(failureMessage) -> snapshot.synopsis
        else -> "$failureMessage\n\n${snapshot.synopsis}"
    }
    val detailLoadIssue = failureMessage?.let { message ->
        JellyfinLoadIssue(
            errorCode = JellyfinErrorCode.UNKNOWN,
            message = message,
            retryable = true,
        )
    }

    return VideoDetail(
        itemId = itemId,
        title = snapshot.title,
        metaLine = resolvedMetaLine,
        synopsis = resolvedSynopsis,
        isFavorite = snapshot.isFavorite,
        continueEpisodeId = snapshot.continueEpisodeId,
        continueProgressLabel = snapshot.continueProgressLabel,
        continueProgressPercent = snapshot.continueProgressPercent,
        updateCount = snapshot.updateCount,
        episodes = snapshot.episodes.ifEmpty {
            listOf(
                EpisodeItem(
                    id = 1,
                    title = "正片",
                    durationLabel = "--",
                    isNew = false,
                    playbackItemId = itemId,
                )
            )
        },
        posterUrl = meta.getOrNull(14)?.ifBlank { null },
        backdropUrl = meta.getOrNull(15)?.ifBlank { null },
        loadIssue = detailLoadIssue,
    )
}

private fun loadSearchSnapshot(
    defaults: NSUserDefaults,
    keyword: String,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): SearchSnapshot? {
    val metaRecord = defaults.stringForKey(SEARCH_META_KEY).orEmpty()
    if (metaRecord.isBlank()) return null

    val meta = decodeRecord(metaRecord)
    if (meta.size < 6) return null
    val schemaVersion = meta[0].toIntOrNull() ?: return null
    if (schemaVersion != 1) return null

    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId

    if (!expectedBaseUrl.isNullOrBlank() && meta[1] != expectedBaseUrl) return null
    if (!expectedUserId.isNullOrBlank() && meta[2] != expectedUserId) return null
    if (meta[3] != keyword) return null

    val searchBridgeState = meta.getOrNull(6).orEmpty()
    val searchFailureReason = searchBridgeState
        .takeIf { it.startsWith("bridge-failure:") }
        ?.removePrefix("bridge-failure:")
        ?.takeIf { it.isNotBlank() }
    val searchLoadIssue = searchFailureReason?.let { reason ->
        JellyfinLoadIssue(
            errorCode = JellyfinErrorCode.UNKNOWN,
            message = searchBridgeFailureMessage(reason),
            retryable = true,
        )
    }

    return SearchSnapshot(
        items = decodeRecords(defaults.stringForKey(SEARCH_ITEMS_KEY))
            .mapNotNull { fields ->
                if (fields.size < 3) return@mapNotNull null
                LibraryItem(
                    itemId = fields[0],
                    title = fields[1],
                    subtitle = fields[2],
                    posterUrl = fields.getOrNull(3)?.ifBlank { null },
                )
            },
        loadIssue = searchLoadIssue,
    )
}

private fun loadMediaViewSnapshot(
    defaults: NSUserDefaults,
    viewId: String,
    sortOption: MediaViewSortOption,
    startIndex: Int,
    limit: Int,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): MediaViewBrowseResult? {
    val baseSnapshot = loadMediaViewSummaryRecords(defaults, defaultServer, session)
        .firstOrNull { it.viewId == viewId } ?: return null
    val exactItems = loadStoredMediaViewItems(defaults, viewId, sortOption)
    val recentItems = if (sortOption == MediaViewSortOption.TITLE) {
        loadStoredMediaViewItems(defaults, viewId, MediaViewSortOption.RECENT)
    } else {
        emptyList()
    }
    val cachedItems = when {
        exactItems.isNotEmpty() -> exactItems
        sortOption == MediaViewSortOption.TITLE &&
            baseSnapshot.totalCount > 0 &&
            recentItems.size >= baseSnapshot.totalCount -> recentItems.sortedBy { it.title.lowercase() }
        else -> exactItems
    }
    val totalCount = baseSnapshot.totalCount.coerceAtLeast(cachedItems.size)
    val requestedEndExclusive = (startIndex + limit).coerceAtMost(totalCount)
    val hasEnoughCachedItems = requestedEndExclusive <= cachedItems.size || totalCount <= cachedItems.size

    if (!hasEnoughCachedItems && totalCount > cachedItems.size) {
        if (baseSnapshot.loadIssue != null) {
            AppLogger.warn(
                tag = "IosMediaViewBridge",
                message = "cached page incomplete but failure snapshot present viewId=${viewId.redactedIdentifier()} startIndex=$startIndex limit=$limit cachedCount=${cachedItems.size} totalCount=$totalCount sort=${sortOption.name}",
            )
        } else {
            AppLogger.debug(
                tag = "IosMediaViewBridge",
                message = "cached page incomplete viewId=${viewId.redactedIdentifier()} startIndex=$startIndex limit=$limit cachedCount=${cachedItems.size} totalCount=$totalCount sort=${sortOption.name}",
            )
            return null
        }
    }

    val resolvedSubtitle = if (baseSnapshot.loadIssue != null) {
        "${baseSnapshot.subtitle} · iOS 分区桥接异常，可重试"
    } else {
        baseSnapshot.subtitle
    }
    val slice = cachedItems.drop(startIndex).take(limit)

    return MediaViewBrowseResult(
        view = HomeMediaView(
            viewId = baseSnapshot.viewId,
            title = baseSnapshot.title,
            subtitle = resolvedSubtitle,
            items = slice,
        ),
        loadIssue = baseSnapshot.loadIssue,
        hasMore = startIndex + slice.size < totalCount,
        nextStartIndex = startIndex + slice.size,
        totalCount = totalCount,
    )
}

private fun loadFavoritesSnapshot(
    defaults: NSUserDefaults,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): FavoritesSnapshot? {
    val metaRecord = defaults.stringForKey(FAVORITES_META_KEY).orEmpty()
    if (metaRecord.isBlank()) return null

    val meta = decodeRecord(metaRecord)
    if (meta.size < 5) return null
    val schemaVersion = meta[0].toIntOrNull() ?: return null
    if (schemaVersion != 1) return null

    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId

    if (!expectedBaseUrl.isNullOrBlank() && meta[1] != expectedBaseUrl) return null
    if (!expectedUserId.isNullOrBlank() && meta[2] != expectedUserId) return null

    val favoritesBridgeState = meta.getOrNull(5).orEmpty()
    val favoritesFailureReason = favoritesBridgeState
        .takeIf { it.startsWith("bridge-failure:") }
        ?.removePrefix("bridge-failure:")
        ?.takeIf { it.isNotBlank() }
    val favoritesLoadIssue = favoritesFailureReason?.let { reason ->
        JellyfinLoadIssue(
            errorCode = JellyfinErrorCode.UNKNOWN,
            message = favoritesBridgeFailureMessage(reason),
            retryable = true,
        )
    }

    return FavoritesSnapshot(
        items = decodeRecords(defaults.stringForKey(FAVORITES_ITEMS_KEY))
            .mapNotNull { fields ->
                if (fields.size < 3) return@mapNotNull null
                LibraryItem(
                    itemId = fields[0],
                    title = fields[1],
                    subtitle = fields[2],
                    posterUrl = fields.getOrNull(3)?.ifBlank { null },
                )
            },
        loadIssue = favoritesLoadIssue,
    )
}

private fun loadPlaybackSnapshot(
    defaults: NSUserDefaults,
    itemId: String,
    episodeId: Int,
    playbackItemId: String?,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): PlaybackContext? {
    val metaRecord = defaults.stringForKey(PLAYBACK_META_KEY).orEmpty()
    if (metaRecord.isBlank()) return null

    val meta = decodeRecord(metaRecord)
    if (meta.size < 18) return null
    val schemaVersion = meta[0].toIntOrNull() ?: return null
    if (schemaVersion != 1) return null

    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId
    val normalizedRequestedPlaybackItemId = playbackItemId.orEmpty()

    if (!expectedBaseUrl.isNullOrBlank() && meta[1] != expectedBaseUrl) return null
    if (!expectedUserId.isNullOrBlank() && meta[2] != expectedUserId) return null
    if (meta[3] != itemId) return null
    if ((meta[4].toIntOrNull() ?: return null) != episodeId) return null
    if (meta[5] != normalizedRequestedPlaybackItemId) return null

    val snapshot = PlaybackSnapshot(
        itemId = meta[3],
        episodeId = meta[4].toIntOrNull() ?: episodeId,
        requestedPlaybackItemId = meta[5].ifBlank { null },
        playbackItemId = meta[6],
        showTitle = meta[7],
        episodeTitle = meta[8],
        streamUrl = meta[9],
        streamTypeLabel = meta[10],
        playSessionId = meta[11].ifBlank { null },
        mediaSourceId = meta[12].ifBlank { null },
        mediaContainer = meta[13].ifBlank { null },
        runtimeLabel = meta[14],
        startPositionLabel = meta[15],
        statusMessage = meta[16],
    )

    return PlaybackContext(
        itemId = snapshot.itemId,
        playbackItemId = snapshot.playbackItemId,
        showTitle = snapshot.showTitle,
        episodeTitle = snapshot.episodeTitle,
        streamUrl = snapshot.streamUrl,
        streamTypeLabel = snapshot.streamTypeLabel,
        playSessionId = snapshot.playSessionId,
        mediaSourceId = snapshot.mediaSourceId,
        mediaContainer = snapshot.mediaContainer,
        runtimeLabel = snapshot.runtimeLabel,
        startPositionLabel = snapshot.startPositionLabel,
        statusMessage = snapshot.statusMessage,
        loadIssue = null,
    )
}

private fun loadFavoritesMutationResult(
    defaults: NSUserDefaults,
    itemId: String,
    favorite: Boolean,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): FavoriteMutationResult? {
    val metaRecord = defaults.stringForKey(FAVORITES_MUTATION_RESULT_KEY).orEmpty()
    if (metaRecord.isBlank()) return null

    val meta = decodeRecord(metaRecord)
    if (meta.size < 10) return null
    val schemaVersion = meta[0].toIntOrNull() ?: return null
    if (schemaVersion != 1) return null

    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId

    if (!expectedBaseUrl.isNullOrBlank() && meta[1] != expectedBaseUrl) return null
    if (!expectedUserId.isNullOrBlank() && meta[2] != expectedUserId) return null
    if (meta[3] != itemId) return null
    if ((meta[4] == "1") != favorite) return null

    val rawErrorCode = meta[6]
    val parsedErrorCode = parseFavoritesMutationErrorCode(rawErrorCode)
    if (rawErrorCode.isNotBlank() && parsedErrorCode == null) {
        AppLogger.warn(
            tag = "IosFavoritesBridge",
            message = "updateFavorite result contains unknown error code itemId=${itemId.redactedIdentifier()} favorite=$favorite code=$rawErrorCode",
        )
    }

    val snapshot = FavoritesMutationSnapshot(
        itemId = meta[3],
        favorite = meta[4] == "1",
        success = meta[5] == "success",
        errorCode = parsedErrorCode,
        retryable = meta[7] == "1",
        message = meta[8],
    )

    return if (snapshot.success) {
        FavoriteMutationResult.Success(
            isFavorite = snapshot.favorite,
            message = snapshot.message,
        )
    } else {
        FavoriteMutationResult.Failure(
            targetFavorite = snapshot.favorite,
            loadIssue = JellyfinLoadIssue(
                errorCode = snapshot.errorCode ?: JellyfinErrorCode.UNKNOWN,
                message = snapshot.message,
                retryable = snapshot.retryable,
            ),
        )
    }
}

private fun parseFavoritesMutationErrorCode(rawCode: String): JellyfinErrorCode? {
    val normalized = rawCode.trim()
    if (normalized.isBlank()) return null
    return enumValues<JellyfinErrorCode>()
        .firstOrNull { it.name.equals(normalized, ignoreCase = true) }
}

private fun enqueueDetailSnapshotRequest(
    defaults: NSUserDefaults,
    itemId: String,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): Boolean {
    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl ?: run {
        AppLogger.warn(
            tag = "IosDetailBridge",
            message = "detail request enqueue failed: missing baseUrl itemId=${itemId.redactedIdentifier()}",
        )
        return false
    }
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId ?: run {
        AppLogger.warn(
            tag = "IosDetailBridge",
            message = "detail request enqueue failed: missing userId itemId=${itemId.redactedIdentifier()} baseUrl=${expectedBaseUrl.redactedBaseUrl()}",
        )
        return false
    }

    val existingRequests = decodeRecords(defaults.stringForKey(DETAIL_REQUEST_QUEUE_KEY))
        .filter { it.size >= 4 }
        .filterNot { fields ->
            val requestedAt = fields[3].toLongOrNull() ?: return@filterNot false
            currentEpochMillis() - requestedAt > 120_000
        }

    if (existingRequests.any { fields ->
            fields[0] == expectedBaseUrl && fields[1] == expectedUserId && fields[2] == itemId
        }) {
        AppLogger.debug(
            tag = "IosDetailBridge",
            message = "detail request already pending itemId=${itemId.redactedIdentifier()} baseUrl=${expectedBaseUrl.redactedBaseUrl()} userId=${expectedUserId.redactedIdentifier()}",
        )
        return true
    }

    val updatedRequests = existingRequests + listOf(
        listOf(
            expectedBaseUrl,
            expectedUserId,
            itemId,
            currentEpochMillis().toString(),
        )
    )
    defaults.setObject(encodeRecords(updatedRequests), DETAIL_REQUEST_QUEUE_KEY)
    AppLogger.info(
        tag = "IosDetailBridge",
        message = "enqueued native detail request itemId=${itemId.redactedIdentifier()} baseUrl=${expectedBaseUrl.redactedBaseUrl()} userId=${expectedUserId.redactedIdentifier()} queueSize=${updatedRequests.size}",
    )
    return true
}

private fun enqueueSearchSnapshotRequest(
    defaults: NSUserDefaults,
    keyword: String,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): Boolean {
    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId
    if (expectedBaseUrl.isNullOrBlank() || expectedUserId.isNullOrBlank()) {
        AppLogger.warn(
            tag = "IosSearchBridge",
            message = "search request enqueue skipped: missing baseUrl/userId keywordLength=${keyword.length}",
        )
        return false
    }

    decodeRecords(defaults.stringForKey(SEARCH_REQUEST_KEY))
        .firstOrNull()
        ?.takeIf { fields ->
            fields.size >= 4 &&
                fields[0] == expectedBaseUrl &&
                fields[1] == expectedUserId &&
                fields[2] == keyword &&
                (currentEpochMillis() - (fields[3].toLongOrNull() ?: 0L)) <= 30_000
        }
        ?.let {
            AppLogger.debug(
                tag = "IosSearchBridge",
                message = "search request already pending keywordLength=${keyword.length} baseUrl=${expectedBaseUrl.redactedBaseUrl()} userId=${expectedUserId.redactedIdentifier()}",
            )
            return true
        }

    val request = listOf(
        expectedBaseUrl,
        expectedUserId,
        keyword,
        currentEpochMillis().toString(),
    )
    defaults.setObject(encodeRecords(listOf(request)), SEARCH_REQUEST_KEY)
    AppLogger.info(
        tag = "IosSearchBridge",
        message = "enqueued native search request keywordLength=${keyword.length} baseUrl=${expectedBaseUrl.redactedBaseUrl()} userId=${expectedUserId.redactedIdentifier()}",
    )
    return true
}

private fun enqueueFavoritesSnapshotRequest(
    defaults: NSUserDefaults,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): Boolean {
    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl ?: return false
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId ?: return false

    val request = listOf(
        expectedBaseUrl,
        expectedUserId,
        currentEpochMillis().toString(),
    )
    defaults.setObject(encodeRecords(listOf(request)), FAVORITES_REQUEST_KEY)
    return true
}

private fun enqueueMediaViewSnapshotRequest(
    defaults: NSUserDefaults,
    viewId: String,
    startIndex: Int,
    limit: Int,
    sortOption: MediaViewSortOption,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): Boolean {
    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl ?: return false
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId ?: return false

    val request = listOf(
        expectedBaseUrl,
        expectedUserId,
        viewId,
        startIndex.toString(),
        limit.toString(),
        sortOption.name,
        currentEpochMillis().toString(),
    )
    defaults.setObject(encodeRecords(listOf(request)), MEDIA_VIEW_REQUEST_KEY)
    AppLogger.info(
        tag = "IosMediaViewBridge",
        message = "enqueued native request baseUrl=${expectedBaseUrl.redactedBaseUrl()} userId=${expectedUserId.redactedIdentifier()} viewId=${viewId.redactedIdentifier()} startIndex=$startIndex limit=$limit sort=${sortOption.name}",
    )
    return true
}

private fun enqueueFavoritesMutationRequest(
    defaults: NSUserDefaults,
    itemId: String,
    favorite: Boolean,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): Boolean {
    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl ?: return false
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId ?: return false

    val request = listOf(
        expectedBaseUrl,
        expectedUserId,
        itemId,
        if (favorite) "1" else "0",
        currentEpochMillis().toString(),
    )
    defaults.removeObjectForKey(FAVORITES_MUTATION_RESULT_KEY)
    defaults.setObject(encodeRecords(listOf(request)), FAVORITES_MUTATION_REQUEST_KEY)
    return true
}

private fun enqueuePlaybackSnapshotRequest(
    defaults: NSUserDefaults,
    itemId: String,
    episodeId: Int,
    playbackItemId: String?,
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): Boolean {
    val expectedBaseUrl = activeIosRuntimeBridge()?.baseUrl ?: defaultServer?.baseUrl ?: return false
    val expectedUserId = activeIosRuntimeBridge()?.userId ?: session?.userId ?: return false

    val request = listOf(
        expectedBaseUrl,
        expectedUserId,
        itemId,
        episodeId.toString(),
        playbackItemId.orEmpty(),
        currentEpochMillis().toString(),
    )
    defaults.setObject(encodeRecords(listOf(request)), PLAYBACK_REQUEST_KEY)
    return true
}

private fun decodeRecords(raw: String?): List<List<String>> {
    val payload = raw.orEmpty()
    if (payload.isBlank()) return emptyList()
    return payload
        .split(RECORD_SEP)
        .filter { it.isNotBlank() }
        .map(::decodeRecord)
}

private fun decodeRecord(raw: String): List<String> {
    return raw.split(FIELD_SEP).map(::unescape)
}

private fun encodeRecords(records: List<List<String>>): String {
    if (records.isEmpty()) return ""
    return records.joinToString(RECORD_SEP.toString()) { encodeRecord(it) }
}

private fun encodeRecord(fields: List<String>): String {
    return fields.joinToString(FIELD_SEP.toString()) { escape(it) }
}

private fun unescape(input: String): String {
    return input
        .replace("\\u001F", FIELD_SEP.toString())
        .replace("\\u001E", RECORD_SEP.toString())
        .replace("\\\\", "\\")
}

private fun escape(input: String): String {
    return input
        .replace("\\", "\\\\")
        .replace(FIELD_SEP.toString(), "\\u001F")
        .replace(RECORD_SEP.toString(), "\\u001E")
}

private fun buildBridgeContextLabel(
    defaultServer: JellyfinServer?,
    session: SessionRecord?,
): String? {
    val runtime = activeIosRuntimeBridge()
    val server = runtime?.toServer() ?: defaultServer
    val username = runtime?.username ?: session?.username
    val userId = runtime?.userId ?: session?.userId

    if (server == null && username.isNullOrBlank()) {
        return null
    }

    val parts = buildList {
        if (!username.isNullOrBlank()) add("user=$username")
        if (!userId.isNullOrBlank()) add("userId=$userId")
        if (server != null) add("server=${server.serverName}")
    }

    return parts.joinToString(" · ")
}

private fun detailMetaKey(itemId: String): String = DETAIL_META_KEY_PREFIX + itemId

private fun detailEpisodesKey(itemId: String): String = DETAIL_EPISODES_KEY_PREFIX + itemId

private fun mediaViewItemsKey(
    viewId: String,
    sortOption: MediaViewSortOption,
): String = MEDIA_VIEW_ITEMS_KEY_PREFIX + sortOption.name.lowercase() + "." + viewId

private fun loadStoredMediaViewItems(
    defaults: NSUserDefaults,
    viewId: String,
    sortOption: MediaViewSortOption,
): List<LibraryItem> {
    return decodeRecords(defaults.stringForKey(mediaViewItemsKey(viewId, sortOption)))
        .mapNotNull { itemFields ->
            if (itemFields.size < 3) return@mapNotNull null
            LibraryItem(
                itemId = itemFields[0],
                title = itemFields[1],
                subtitle = itemFields[2],
                posterUrl = itemFields.getOrNull(3)?.ifBlank { null },
            )
        }
}

@OptIn(ExperimentalForeignApi::class)
private fun currentEpochMillis(): Long = time(null).toLong() * 1000L

private fun String?.redactedIdentifier(): String {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return "-"
    if (value.length <= 8) return value
    return value.take(4) + "..." + value.takeLast(2)
}

private fun String.redactedBaseUrl(): String {
    val withoutScheme = substringAfter("://", this)
    return withoutScheme.substringBefore('?').substringBefore('#').ifBlank { this }
}

private fun detailBridgeFailureMessage(reason: String): String {
    return when (reason) {
        "network" -> "iOS 详情桥接请求失败（网络未连通）。请检查网络后重试。"
        "cancelled" -> "iOS 详情桥接请求已取消。请重新进入详情页重试。"
        "empty-snapshot" -> "iOS 详情桥接未返回可用快照。请稍后重试。"
        "native-error" -> "iOS 详情桥接出现原生错误。请稍后重试。"
        else -> "iOS 详情桥接暂时不可用。请稍后重试。"
    }
}

private fun searchBridgeFailureMessage(reason: String): String {
    return when (reason) {
        "network" -> "iOS 搜索桥接请求失败（网络未连通）。请检查网络后重试。"
        "cancelled" -> "iOS 搜索桥接请求已取消。请重新搜索。"
        "empty-snapshot" -> "iOS 搜索桥接未返回可用快照。请稍后重试。"
        "native-error" -> "iOS 搜索桥接出现原生错误。请稍后重试。"
        else -> "iOS 搜索桥接暂时不可用。请稍后重试。"
    }
}

private fun favoritesBridgeFailureMessage(reason: String): String {
    return when (reason) {
        "network" -> "iOS 收藏桥接请求失败（网络未连通）。请检查网络后重试。"
        "cancelled" -> "iOS 收藏桥接请求已取消。请重试。"
        "empty-snapshot" -> "iOS 收藏桥接未返回可用快照。请稍后重试。"
        "native-error" -> "iOS 收藏桥接出现原生错误。请稍后重试。"
        else -> "iOS 收藏桥接暂时不可用。请稍后重试。"
    }
}

private fun mediaViewBridgeFailureMessage(reason: String): String {
    return when (reason) {
        "network" -> "iOS 分区桥接请求失败（网络未连通）。请检查网络后重试。"
        "cancelled" -> "iOS 分区桥接请求已取消。请重新进入分区。"
        "empty-snapshot" -> "iOS 分区桥接未返回可用快照。请稍后重试。"
        "native-error" -> "iOS 分区桥接出现原生错误。请稍后重试。"
        else -> "iOS 分区桥接暂时不可用。请稍后重试。"
    }
}

private fun buildPlaceholderDetail(itemId: String): VideoDetail {
    val shortId = itemId.take(8)
    return VideoDetail(
        itemId = itemId,
        title = "媒体详情 ($shortId)",
        metaLine = "详情占位 · Home 已真实接通",
        synopsis = "当前详情仍可能显示占位内容。iOS 会优先读取原生预取的 Detail snapshot，已覆盖部分从 Home 进入的媒体项。",
        isFavorite = false,
        continueEpisodeId = 1,
        continueProgressLabel = "未开始播放",
        continueProgressPercent = 0,
        updateCount = 0,
        episodes = listOf(
            EpisodeItem(
                id = 1,
                title = "正片",
                durationLabel = "--",
                isNew = false,
                playbackItemId = itemId,
            )
        ),
    )
}
