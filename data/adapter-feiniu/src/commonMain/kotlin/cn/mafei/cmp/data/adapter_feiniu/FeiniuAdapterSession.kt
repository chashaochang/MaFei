package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemDetail
import cn.mafei.cmp.providers.feiniu.core.FeiniuMediaItem
import cn.mafei.cmp.providers.feiniu.facade.FeiniuFacade
import kotlin.coroutines.cancellation.CancellationException

/** Shared identity guard, provider access and genre cache for one adapter instance. */
internal class FeiniuAdapterSession(
    val facade: FeiniuFacade,
    val serverId: ServerId,
    val accountId: AccountId,
    val resolution: FeiniuHostResolution,
) {
    val provider: ProviderKind = ProviderKind.Feiniu

    val capabilities = AdapterCapabilities(
        search = true,
        favoritesRead = true,
        favoritesFilter = false,
        favoritesEdit = true,
        // Legacy FeiniuVideoCatalogProvider.itemListPayload writes
        // tags.watched as the proven "1" / "0" literals.
        watchedFilter = true,
        watchedEdit = true,
        playbackReporting = true,
    )

    private var genreNames: Map<Int, String>? = null
    // Construct after login/restore. A different identity needs a new adapter;
    // cookie revisions within the same identity remain usable.
    private val boundIdentity = facade.credentials.value.identityGeneration

    fun currentOperation(): cn.mafei.cmp.providers.feiniu.core.FeiniuSessionSnapshot {
        val snapshot = facade.credentials.value
        if (snapshot.identityGeneration != boundIdentity) {
            throw CancellationException("Feiniu adapter identity was replaced")
        }
        return snapshot
    }

    fun imageUrl(imagePath: String): String {
        val normalized = imagePath.trim()
        return if (normalized.startsWith("http://", ignoreCase = true) ||
            normalized.startsWith("https://", ignoreCase = true)
        ) {
            normalized
        } else {
            val requestPath = if (normalized.startsWith('/')) normalized else "/$normalized"
            facade.assets.imageUrl(resolution.preferred.baseUrl, requestPath)
        }
    }

    suspend fun <T> translate(block: suspend () -> T): T =
        try {
            currentOperation()
            block().also { currentOperation() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: cn.mafei.cmp.providers.feiniu.core.FeiniuError) {
            throw e.toDomainError()
        }

    suspend fun optionalItemDetail(itemGuid: String?): FeiniuItemDetail? {
        val normalized = itemGuid?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        return try {
            translate { facade.catalog.itemDetail(resolution, normalized) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (_: Exception) {
            null
        }
    }

    suspend fun genreNamesFor(items: Iterable<FeiniuMediaItem>): Map<Int, String> =
        if (items.any { it.genres.any { id -> id > 0 } }) genreNames() else emptyMap()

    suspend fun genreNames(): Map<Int, String> {
        genreNames?.let { return it }
        val loaded = try {
            translate { facade.catalog.genres(resolution) }
                .mapNotNull { entry ->
                    val id = entry.id?.takeIf { it > 0 } ?: return@mapNotNull null
                    val name = entry.value?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    id to name
                }
                .toMap()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyMap()
        }
        genreNames = loaded
        return loaded
    }
}
