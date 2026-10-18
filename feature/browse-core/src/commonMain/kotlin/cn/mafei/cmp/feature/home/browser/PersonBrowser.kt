package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class PersonBrowser(context: BrowserContext) : BrowserController(context), BrowserTabParticipant {
    private var personTicket = 0L
    private val favoriteLocks = mutableSetOf<MediaId>()

    override fun switchTabContext(previous: String, target: String, retained: Set<String>) { ++personTicket }

    override suspend fun resumeTabRequests(requests: Set<BrowserTabRequest>) {
        val current = state.value
        if (BrowserTabRequest.Person in requests && !current.personLoading) {
            current.personId?.let { openPerson(it, current.personRequestedName, keepContent = true) }
        }
    }

    /** Legacy CastDetailViewModel: detail first, then three independent ten-item work lists in parallel. */
    suspend fun openPerson(id: MediaId, name: String, keepContent: Boolean = false) {
        val operation = ++personTicket
        val before = state.value
        mutableState.value =
            state.value.copy(
                personId = id,
                personRequestedName = name,
                tabPendingRequests = state.value.tabPendingRequests - BrowserTabRequest.Person,
                person = before.person.takeIf { keepContent },
                personLoading = true,
                personError = null,
                personMovies = if (keepContent) before.personMovies else emptyList(),
                personSeries = if (keepContent) before.personSeries else emptyList(),
                personEpisodes = if (keepContent) before.personEpisodes else emptyList(),
                personSectionsLoading = false,
                personFavoriteBusy = false,
            )
        try {
            val loaded = repository.person(session, id, name, refresh = true)
            if (operation != personTicket) return
            mutableState.value =
                state.value.copy(
                    person = loaded.value,
                    personLoading = false,
                    personError =
                        if (loaded.origin == MediaLoadOrigin.StaleCache) {
                            "连接暂不可用，显示缓存人物详情"
                        } else null,
                    personSectionsLoading = true,
                )
            val works = coroutineScope {
                val movies = async { loadPersonWorks(id, MediaType.Movie) }
                val episodes = async { loadPersonWorks(id, MediaType.Episode) }
                val series = async { loadPersonWorks(id, MediaType.Series) }
                Triple(movies.await(), series.await(), episodes.await())
            }
            if (operation != personTicket) return
            mutableState.value =
                state.value.copy(personMovies = works.first, personSeries = works.second, personEpisodes = works.third, personSectionsLoading = false)
        } catch (cancelled: CancellationException) {
            if (operation == personTicket) {
                mutableState.value = state.value.copy(personLoading = false, personSectionsLoading = false,
                    tabPendingRequests = state.value.tabPendingRequests + BrowserTabRequest.Person)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == personTicket) {
                mutableState.value = state.value.copy(personLoading = false, personSectionsLoading = false, personError = browseError(failure))
            }
        }
    }

    private suspend fun loadPersonWorks(personId: MediaId, type: MediaType): List<MediaItem> {
        val request =
            PageRequest(
                pageSize = PersonWorkPageSize,
                sort = MediaSort(MediaSortField.Title, SortDirection.Ascending),
                filter = MediaFilter(mediaTypes = setOf(type)),
            )
        return try {
            val loaded = repository.browse(session = session, query = MediaQuery.PersonItems(personId), request = request, refresh = true)
            loaded.value.items
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The legacy page keeps the other two work groups when one provider
            // query fails, so a section failure is represented by an empty group.
            emptyList()
        }
    }

    /** 旧版 CastDetailPage 可收藏人物本体（favorite 端点对任意 itemId 有效）。 */
    suspend fun togglePersonFavorite() {
        val person = state.value.person ?: return
        if (!capabilities.favoritesEdit || state.value.personFavoriteBusy) return
        if (!favoriteLocks.add(person.id)) return
        val operation = personTicket
        mutableState.value = state.value.copy(personFavoriteBusy = true)
        try {
            repository.setFavorite(session, person.id, !person.isFavorite)
            val updated = state.value.replacePersonFavorite(person.id, !person.isFavorite)
            mutableState.value = if (operation == personTicket) updated.copy(personFavoriteBusy = false) else updated
        } catch (cancelled: CancellationException) {
            if (operation == personTicket) mutableState.value = state.value.copy(personFavoriteBusy = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == personTicket) mutableState.value = state.value.copy(personFavoriteBusy = false, personError = browseError(failure))
        } finally {
            favoriteLocks.remove(person.id)
        }
    }

    fun clearPerson() {
        ++personTicket
        mutableState.value =
            state.value.copy(
                personId = null,
                tabPendingRequests = state.value.tabPendingRequests - BrowserTabRequest.Person,
                personRequestedName = "",
                person = null,
                personLoading = false,
                personError = null,
                personMovies = emptyList(),
                personSeries = emptyList(),
                personEpisodes = emptyList(),
                personSectionsLoading = false,
                personFavoriteBusy = false,
            )
    }
}
