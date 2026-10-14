package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.media.legacyPrimaryArtworkCandidates
import cn.mafei.cmp.common.ui.LegacyPosterPlaceholder
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.feature.detail.PersonDetailScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.*
import kotlinx.coroutines.launch

@Composable
internal fun PersonEntryBinding(
    route: BrowsePersonRoute,
    scope: kotlinx.coroutines.CoroutineScope,
    browser: MediaBrowser,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    navigator: BrowseNavigator,
) {
    PersonDetailScreen(
        person = state.person?.takeIf { it.id.value == route.personId },
        movies = state.personMovies,
        series = state.personSeries,
        episodes = state.personEpisodes,
        loading = state.personLoading,
        sectionsLoading = state.personSectionsLoading,
        error = state.personError,
        onBack = navigator::pop,
        onRetry = { scope.launch { browser.openPerson(MediaId(route.personId), route.personName) } },
        onItem = { item -> navigator.openDetail(item, if (item.mediaType == MediaType.Series) "电视节目" else "电影") },
        canFavorite = browser.capabilities.favoritesEdit,
        onToggleFavorite = { scope.launch { browser.togglePersonFavorite() } },
        favoriteBusy = state.personFavoriteBusy,
        modifier = Modifier.fillMaxSize(),
        personArtwork = { person, modifier ->
            LegacyPersonArtwork(image = person.image, name = person.name, cache = artworkCache, fetch = fetchArtwork, modifier = modifier, targetWidthPx = 360)
        },
        artwork = { item, modifier ->
            MediaArtwork(
                item.legacyPrimaryArtworkCandidates(),
                item.title,
                artworkCache,
                fetchArtwork,
                modifier,
                fallback = { fallbackModifier -> LegacyPosterPlaceholder(item.mediaType, fallbackModifier) },
                targetWidthPx = 360,
            )
        },
    )
}
