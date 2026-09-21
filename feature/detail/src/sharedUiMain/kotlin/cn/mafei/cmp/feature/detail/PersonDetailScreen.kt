package cn.mafei.cmp.feature.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.InlineProblem
import cn.mafei.cmp.common.ui.LegacyBaseItemCard
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaPerson
import org.jetbrains.compose.resources.painterResource

/** Mechanical Compose translation of the legacy HarmonyOS CastDetailPage. */
@Composable
fun PersonDetailScreen(
    person: MediaPerson?,
    movies: List<MediaItem>,
    series: List<MediaItem>,
    episodes: List<MediaItem>,
    loading: Boolean,
    // Retained for source compatibility with the previous paged shell. The
    // legacy CastDetailPage renders the three resolved groups as-is.
    sectionsLoading: Boolean = false,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onItem: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    canFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    favoriteBusy: Boolean = false,
    onLoadMoreMovies: (() -> Unit)? = null,
    onLoadMoreSeries: (() -> Unit)? = null,
    onLoadMoreEpisodes: (() -> Unit)? = null,
    loadingMoreSections: Set<String> = emptySet(),
    personArtwork: (@Composable (MediaPerson, Modifier) -> Unit)? = null,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val backdrop = rememberGlassBackdrop()
    val actionBarBackdrop = rememberGlassBackdrop()
    val scrollState = rememberScrollState()
    val actionBarProgress by remember(scrollState) {
        derivedStateOf { if (scrollState.value > 0) 1f else 0f }
    }
    var overviewSheetVisible by remember(person?.id?.value) { mutableStateOf(false) }
    DetailSheetBackHandler(
        enabled = overviewSheetVisible,
        onDismiss = { overviewSheetVisible = false },
    )
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().captureGlassBackdrop(actionBarBackdrop),
        ) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .captureGlassBackdrop(backdrop),
            )
            if (person == null) {
                Box(
                    Modifier.fillMaxSize().padding(
                        top = safeArea.top + 56.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = safeArea.bottom,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        error != null -> InlineProblem(error, onRetry)
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(scrollState)
                        .padding(top = safeArea.top + 56.dp, bottom = safeArea.bottom + 30.dp),
                ) {
                    PersonHeader(
                        person = person,
                        artwork = personArtwork,
                        canFavorite = canFavorite,
                        favoriteBusy = favoriteBusy,
                        onToggleFavorite = onToggleFavorite,
                        onOpenOverview = { overviewSheetVisible = true },
                        backdrop = backdrop,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                            .widthIn(max = 720.dp).fillMaxWidth(),
                    )
                    PersonWorkSection("电影", movies, onItem, artwork)
                    PersonWorkSection("电视剧", series, onItem, artwork)
                    PersonWorkSection("剧集", episodes, onItem, artwork)
                    if (error != null && !loading) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
                            InlineProblem(error, onRetry)
                        }
                    }
                }
            }
        }

        MaFeiActionBar(
            title = person?.name.orEmpty(),
            onBack = { if (overviewSheetVisible) overviewSheetVisible = false else onBack() },
            backdrop = actionBarBackdrop,
            materialAlpha = if (person == null) 1f else actionBarProgress,
            titleAlpha = if (person == null) 0f else actionBarProgress,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (overviewSheetVisible) {
            OverviewSheetOverlay(
                overview = person?.overview,
                backdrop = actionBarBackdrop,
                onDismiss = { overviewSheetVisible = false },
            )
        }
    }
}

@Composable
private fun PersonHeader(
    person: MediaPerson,
    artwork: (@Composable (MediaPerson, Modifier) -> Unit)?,
    canFavorite: Boolean,
    favoriteBusy: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onOpenOverview: () -> Unit,
    backdrop: cn.mafei.cmp.common.glass.GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    val imageShape = RoundedCornerShape(12.dp)
    Row(
        modifier.height(200.dp)
            // The CMP product follows the legacy Native-theme branch (System
            // Material is replaced by our liquid material): 20dp top / 16dp
            // bottom around the 200dp person header.
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.fillMaxHeight().aspectRatio(291f / 436f)
                .clip(imageShape)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, imageShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null && person.image != null) {
                artwork(person, Modifier.fillMaxSize())
            } else {
                Image(
                    painter = painterResource(MaFeiLegacyDrawables.castAvatar),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.Start,
        ) {
            // ArkUI starts this column with a weighted Blank: name, biography
            // and favorite action sit together at the bottom of the portrait.
            Spacer(Modifier.weight(1f))
            Text(
                person.name,
                Modifier.fillMaxWidth().semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Medium,
            )
            person.sortName?.takeIf { it.isNotBlank() && it != person.name }?.let { sortName ->
                Text(
                    sortName,
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 1,
                )
            }
            person.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                OverviewSection(
                    overview = overview,
                    modifier = Modifier.padding(top = 10.dp),
                    onOpen = onOpenOverview,
                )
            }
            Spacer(Modifier.height(10.dp))
            // 旧版 CastDetailPage 始终显示人物收藏按钮；无能力时点击不执行写操作。
            LiquidButton(
                onClick = {
                    if (canFavorite && !favoriteBusy) onToggleFavorite?.invoke()
                },
                backdrop = backdrop,
                isInteractive = canFavorite && !favoriteBusy && onToggleFavorite != null,
                height = 50.dp,
                horizontalPadding = 14.dp,
                modifier = Modifier.size(50.dp),
            ) {
                Image(
                    painter = painterResource(
                        if (person.isFavorite) {
                            MaFeiLegacyDrawables.favoriteSelected
                        } else {
                            MaFeiLegacyDrawables.favoriteUnselected
                        },
                    ),
                    contentDescription = if (person.isFavorite) "取消收藏" else "收藏",
                    modifier = Modifier.size(22.dp),
                    colorFilter = if (person.isFavorite) {
                        null // 选中时保留原色（红心）
                    } else {
                        ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
            }
        }
    }
}

@Composable
private fun PersonWorkSection(
    title: String,
    items: List<MediaItem>,
    onItem: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
) {
    if (items.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            title,
            Modifier.padding(start = 16.dp).semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            // ArkUI's 194vp list did not clip BaseItemCard's trailing text,
            // while Compose measures the explicit 19sp/17sp line heights and
            // the card's 8dp bottom breathing room into the row. Allocate the
            // card's real 206dp height so the subtitle remains fully visible.
            Modifier.fillMaxWidth().height(206.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.width(8.dp)) }
            items(items, key = { it.id.value }) { item ->
                LegacyBaseItemCard(
                    item = item,
                    onClick = { onItem(item) },
                    poster = artwork?.let { draw -> { imageModifier -> draw(item, imageModifier) } },
                )
            }
            item { Spacer(Modifier.width(8.dp)) }
        }
    }
}
