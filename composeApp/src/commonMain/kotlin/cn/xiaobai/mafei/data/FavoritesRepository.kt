package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.data.jellyfin.JellyfinLoadIssue
import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.storage.SessionRecord

data class FavoritesState(
    val items: List<LibraryItem>,
    val loadIssue: JellyfinLoadIssue? = null,
)

sealed interface FavoriteMutationResult {
    data class Success(
        val isFavorite: Boolean,
        val message: String,
    ) : FavoriteMutationResult

    data class Failure(
        val targetFavorite: Boolean,
        val loadIssue: JellyfinLoadIssue,
    ) : FavoriteMutationResult
}

interface FavoritesRepository {
    suspend fun loadFavorites(
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): FavoritesState

    suspend fun updateFavorite(
        itemId: String,
        favorite: Boolean,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): FavoriteMutationResult
}

class FakeFavoritesRepository(
    private val fakeVideoRepository: FakeVideoRepository,
) : FavoritesRepository {
    private val favoriteItemIds = linkedSetOf("foundation", "dune-part-two")

    override suspend fun loadFavorites(
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): FavoritesState {
        val allItems = fakeVideoRepository.search("")
        return FavoritesState(
            items = allItems.filter { it.itemId in favoriteItemIds },
        )
    }

    override suspend fun updateFavorite(
        itemId: String,
        favorite: Boolean,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): FavoriteMutationResult {
        if (favorite) {
            favoriteItemIds.add(itemId)
        } else {
            favoriteItemIds.remove(itemId)
        }

        return FavoriteMutationResult.Success(
            isFavorite = favorite,
            message = if (favorite) "已收藏" else "已取消收藏",
        )
    }
}
