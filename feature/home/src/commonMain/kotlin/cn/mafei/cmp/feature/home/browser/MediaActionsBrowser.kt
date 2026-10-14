package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.CancellationException

internal class MediaActionsBrowser(context: BrowserContext, private val detailGeneration: () -> Long) : BrowserController(context) {
    private enum class UserDataAction { Watched, Favorite }
    private data class UserDataActionKey(val id: MediaId, val action: UserDataAction)
    private val userDataActionLocks = mutableSetOf<UserDataActionKey>()

    suspend fun toggleFavorite() {
        val detail = state.value.detail ?: return
        if (!capabilities.favoritesEdit) return
        mutateDetail(detail, favorite = !detail.userData.isFavorite, watched = null)
    }

    suspend fun toggleWatched() {
        val detail = state.value.detail ?: return
        if (!capabilities.watchedEdit) return
        mutateDetail(detail, favorite = null, watched = !detail.userData.isPlayed)
    }

    suspend fun toggleWatched(item: MediaItem) {
        if (!capabilities.watchedEdit) return
        mutateListItem(item, UserDataAction.Watched)
    }

    suspend fun toggleFavorite(item: MediaItem) {
        if (!capabilities.favoritesEdit) return
        mutateListItem(item, UserDataAction.Favorite)
    }

    // ---------------------------------------------------------------- deletion

    /** 探测当前账号是否可删除媒体（管理员策略），供 UI 决定是否出现删除入口。 */
    suspend fun refreshDeleteAvailability() {
        if (state.value.deleteAvailable != null) return
        if (!capabilities.mediaDelete) {
            mutableState.value = state.value.copy(deleteAvailable = false)
            return
        }
        val available = try {
            repository.canDeleteMedia(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        mutableState.value = state.value.copy(deleteAvailable = available)
    }

    /** 旧版“我的”页角色标签：Jellyfin Policy / 飞牛 user/info 是唯一来源。 */
    suspend fun refreshAdministratorStatus() {
        if (state.value.administrator != null) return
        val administrator = try {
            repository.isAdministrator(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        mutableState.value = state.value.copy(administrator = administrator)
    }

    /** 管理员批量删除媒体（列表页多选 / 详情页单条）。成功后同步修剪各列表。 */
    suspend fun deleteMedia(ids: Collection<MediaId>) {
        val targets = ids.filter { it.value.isNotBlank() }.distinct()
        if (targets.isEmpty() || !capabilities.mediaDelete || state.value.deleteBusy) return
        mutableState.value = state.value.copy(deleteBusy = true, deleteError = null)
        try {
            repository.deleteItems(session, targets)
            if (state.value.deleteBusy) {
                mutableState.value = state.value.removeMedia(targets.toSet()).copy(deleteBusy = false)
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = state.value.copy(deleteBusy = false)
            throw cancelled
        } catch (failure: Exception) {
            mutableState.value = state.value.copy(deleteBusy = false, deleteError = browseError(failure))
        }
    }

    private suspend fun mutateDetail(
        detail: MediaItem,
        favorite: Boolean?,
        watched: Boolean?,
    ) {
        val operation = detailGeneration()
        if (state.value.detailActionBusy) return
        mutableState.value = state.value.copy(detailActionBusy = true, detailActionError = null)
        try {
            when {
                favorite != null -> repository.setFavorite(session, detail.id, favorite)
                else -> repository.setWatched(session, detail.id, checkNotNull(watched))
            }
            if (operation != detailGeneration() || !state.value.containsMedia(detail.id)) return
            val nextUserData = detail.userData.copy(
                isFavorite = favorite ?: detail.userData.isFavorite,
                isPlayed = watched ?: detail.userData.isPlayed,
            )
            mutableState.value = state.value.replaceUserData(detail.id, nextUserData).copy(
                detailActionBusy = false,
                detailActionError = null,
            )
        } catch (cancelled: CancellationException) {
            if (operation == detailGeneration()) {
                mutableState.value = state.value.copy(detailActionBusy = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailGeneration()) {
                mutableState.value = state.value.copy(
                    detailActionBusy = false,
                    detailActionError = browseError(failure),
                )
            }
        }
    }

    /**
     * Mutates a card action without requiring the item to be the currently
     * opened detail. The old implementation reused [mutateDetail], whose
     * stale-detail guard silently discarded list actions because ordinary
     * library/home items are not part of the detail state. Keep one lock per
     * media/action pair so a favorite request cannot block a watched request
     * for the same card, and publish the optimistic state through every feed
     * before the provider round trip completes.
     */
    private suspend fun mutateListItem(item: MediaItem, action: UserDataAction) {
        val key = UserDataActionKey(item.id, action)
        if (!userDataActionLocks.add(key)) return
        val current = state.value.userDataFor(item.id) ?: item.userData
        val previous = when (action) {
            UserDataAction.Watched -> current.isPlayed
            UserDataAction.Favorite -> current.isFavorite
        }
        val target = !previous
        mutableState.value = state.value.replaceUserData(item.id) { data ->
            when (action) {
                UserDataAction.Watched -> data.copy(isPlayed = target)
                UserDataAction.Favorite -> data.copy(isFavorite = target)
            }
        }
        try {
            when (action) {
                UserDataAction.Watched -> repository.setWatched(session, item.id, target)
                UserDataAction.Favorite -> repository.setFavorite(session, item.id, target)
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = state.value.replaceUserData(item.id) { data ->
                when (action) {
                    UserDataAction.Watched -> data.copy(isPlayed = previous)
                    UserDataAction.Favorite -> data.copy(isFavorite = previous)
                }
            }
            throw cancelled
        } catch (failure: Exception) {
            mutableState.value = state.value.replaceUserData(item.id) { data ->
                when (action) {
                    UserDataAction.Watched -> data.copy(isPlayed = previous)
                    UserDataAction.Favorite -> data.copy(isFavorite = previous)
                }
            }.copy(
                detailActionError = browseError(failure).takeIf { state.value.detail?.id == item.id },
            )
        } finally {
            userDataActionLocks.remove(key)
        }
    }
}
