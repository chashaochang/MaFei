package cn.mafei.cmp.session

import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.core.storage_api.getBoolean
import cn.mafei.cmp.core.storage_api.putBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Process-scoped ordinary settings and legacy-compatible media-source selections. */
internal class RuntimePreferences(
    private val settings: SettingsStorage,
    private val appearance: AppearanceStore,
    private val isClosed: () -> Boolean,
    private val failureReporter: (String, Throwable) -> Unit,
) {
    /** Legacy-compatible account + item scoped media-source selections. */
    val playbackPreferences: cn.mafei.cmp.feature.home.MediaPlaybackPreferences by lazy {
        val storage = cn.mafei.cmp.core.storage_api.JsonSnapshotStorage(
            settings,
            Json { ignoreUnknownKeys = true },
            { SystemEpochClock.nowEpochMs() },
        )
        val preferenceMutex = Mutex()
        object : cn.mafei.cmp.feature.home.MediaPlaybackPreferences {
            override suspend fun preferredMediaSourceId(accountId: AccountId, mediaId: MediaId): String? =
                preferenceMutex.withLock {
                    try {
                        val current = storage.load(
                            PreferredMediaSourcesKey,
                            PreferredMediaSources.serializer(),
                        )
                        current?.entries?.firstOrNull {
                            it.accountId == accountId.value && it.mediaId == mediaId.value
                        }?.sourceId ?: storage.load(PreferredMediaSourceKey, String.serializer())
                    } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }

            override suspend fun setPreferredMediaSourceId(
                accountId: AccountId,
                mediaId: MediaId,
                sourceId: String,
            ) {
                preferenceMutex.withLock {
                    try {
                        val retained = storage.load(
                            PreferredMediaSourcesKey,
                            PreferredMediaSources.serializer(),
                        )?.entries.orEmpty().filterNot {
                            it.accountId == accountId.value && it.mediaId == mediaId.value
                        }
                        storage.save(
                            PreferredMediaSourcesKey,
                            PreferredMediaSources(
                                (listOf(PreferredMediaSource(accountId.value, mediaId.value, sourceId)) + retained)
                                    .take(PreferredMediaSourceLimit),
                            ),
                            PreferredMediaSources.serializer(),
                        )
                        // The previous CMP draft stored one global string. Once a scoped
                        // selection is written it must not leak into unrelated items.
                        storage.delete(PreferredMediaSourceKey)
                    } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A preference write must not block playback or source switching.
                    }
                }
            }
        }
    }
    /** Same non-secret search history that ArkUI stored in AppPreference. */
    suspend fun searchHistory(): List<String> {
        check(!isClosed()) { "Application is closed" }
        val raw = settings.getString(SEARCH_HISTORY_KEY) ?: return emptyList()
        return runCatching {
            RuntimeSettingsJson.decodeFromString(ListSerializer(String.serializer()), raw)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(10)
        }.getOrDefault(emptyList())
    }

    suspend fun saveSearchHistory(items: List<String>) {
        check(!isClosed()) { "Application is closed" }
        val normalized = items.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
        settings.putString(
            SEARCH_HISTORY_KEY,
            RuntimeSettingsJson.encodeToString(ListSerializer(String.serializer()), normalized),
        )
    }

    /** Uses the existing ordinary-settings key, independent of account storage. */
    suspend fun loadAppearanceMode(): String? {
        check(!isClosed()) { "Application is closed" }
        return appearance.storedMode()
    }

    suspend fun saveAppearanceMode(value: String) {
        check(!isClosed()) { "Application is closed" }
        val mode = cn.mafei.cmp.common.design_system.AppearanceMode.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported appearance mode")
        appearance.save(mode)
    }

    /** Product-wide Liquid Glass strength, persisted as a 0..100 percentage. */
    suspend fun loadGlassIntensity(): Float {
        check(!isClosed()) { "Application is closed" }
        return try {
            settings.getString(GLASS_INTENSITY_KEY)
                ?.toIntOrNull()
                ?.coerceIn(0, 100)
                ?.div(100f)
                ?: DEFAULT_GLASS_INTENSITY
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter("glass-intensity-read", failure)
            DEFAULT_GLASS_INTENSITY
        }
    }

    suspend fun saveGlassIntensity(value: Float) {
        check(!isClosed()) { "Application is closed" }
        settings.putString(
            GLASS_INTENSITY_KEY,
            (value.coerceIn(0f, 1f) * 100f).toInt().toString(),
        )
    }

    /** Whether the home page should render its recommendation carousel. */
    suspend fun loadHomeCarouselVisible(): Boolean {
        check(!isClosed()) { "Application is closed" }
        return try {
            settings.getBoolean(HOME_CAROUSEL_VISIBLE_KEY, default = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter("home-carousel-visibility-read", failure)
            true
        }
    }

    suspend fun saveHomeCarouselVisible(value: Boolean) {
        check(!isClosed()) { "Application is closed" }
        settings.putBoolean(HOME_CAROUSEL_VISIBLE_KEY, value)
    }

    /** Native player output path. Buffer is the safe default on OHOS phones/tablets. */
    suspend fun loadPlayerRenderMode(): String {
        check(!isClosed()) { "Application is closed" }
        return when (settings.getString(PLAYER_RENDER_MODE_KEY)) {
            PLAYER_RENDER_MODE_DIRECT -> PLAYER_RENDER_MODE_DIRECT
            else -> PLAYER_RENDER_MODE_BUFFER
        }
    }

    suspend fun savePlayerRenderMode(value: String) {
        check(!isClosed()) { "Application is closed" }
        settings.putString(
            PLAYER_RENDER_MODE_KEY,
            if (value == PLAYER_RENDER_MODE_DIRECT) PLAYER_RENDER_MODE_DIRECT else PLAYER_RENDER_MODE_BUFFER,
        )
    }

    /** ArkUI-compatible Feiniu route strategy. Missing/corrupt values are Speed. */
    private companion object {
        const val GLASS_INTENSITY_KEY = "mafei.ui.liquid-glass-intensity.v1"
        const val DEFAULT_GLASS_INTENSITY = .5f
        const val HOME_CAROUSEL_VISIBLE_KEY = "mafei.ui.home-carousel-visible.v1"
        const val PLAYER_RENDER_MODE_KEY = "mafei.player.render-mode.v1"
        const val PLAYER_RENDER_MODE_BUFFER = "buffer"
        const val PLAYER_RENDER_MODE_DIRECT = "direct"
    }
}

private val RuntimeSettingsJson = Json { ignoreUnknownKeys = true }
private const val SEARCH_HISTORY_KEY = "mafei/ui/search-history-v1"
private const val PreferredMediaSourceKey = "mafei/media/playback/preferred-media-source-v1"
private const val PreferredMediaSourcesKey = "mafei/media/playback/preferred-media-sources-v2"
private const val PreferredMediaSourceLimit = 128

@Serializable
private data class PreferredMediaSource(
    val accountId: String,
    val mediaId: String,
    val sourceId: String,
)

@Serializable
private data class PreferredMediaSources(
    val entries: List<PreferredMediaSource> = emptyList(),
)
