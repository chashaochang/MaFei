package cn.mafei.cmp.feature.detail

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.ui.LocalMaFeiNavigationChrome
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassMaterial
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyVideoCard
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.LocalShellBackdrop
import cn.mafei.cmp.common.ui.legacyMediaColumns
import cn.mafei.cmp.common.ui.legacyMediaGridGap
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.common.ui.mafeiArtworkFeedback
import cn.mafei.cmp.common.ui.mafeiMediaCardInteraction
import cn.mafei.cmp.common.ui.rememberMediaCardInteraction
import cn.mafei.cmp.common.ui.mediaArtworkGridHoverInset
import cn.mafei.cmp.common.ui.mediaArtworkHoverTopInset
import cn.mafei.cmp.common.ui.mafeiDismissOnOutsidePointer
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.PersonKind
import cn.mafei.cmp.core.domain.episodeLabel
import cn.mafei.cmp.core.domain.PersonRef
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun OverviewSection(
    overview: String?,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val text = overview?.takeIf { it.isNotBlank() } ?: return
    val largeTypography = usesLargeDetailTypography()
    val textColor = LocalContentColor.current.copy(alpha = .86f)
    val moreColor = MaterialTheme.colorScheme.primary
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = textColor,
        fontSize = if (largeTypography) 15.sp else 13.sp,
        lineHeight = if (largeTypography) 22.sp else 18.sp,
    )
    val textMeasurer = rememberTextMeasurer()
    val latestOnOpen by rememberUpdatedState(onOpen)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val preview = remember(text, maxWidthPx, textStyle, moreColor) {
            legacyOverviewPreview(
                text = text,
                maxWidthPx = maxWidthPx,
                textMeasurer = textMeasurer,
                style = textStyle,
                moreColor = moreColor,
                onMore = { latestOnOpen() },
            )
        }
        Text(
            text = preview,
            modifier = Modifier.fillMaxWidth(),
            style = textStyle,
            maxLines = LegacyOverviewMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Same-layer bottom sheet so the page backdrop remains the live glass source. */
@Composable
internal fun BoxScope.OverviewSheetOverlay(
    overview: String?,
    backdrop: GlassBackdrop,
    onDismiss: () -> Unit,
    surfaceMotion: Modifier = Modifier,
) {
    val text = overview?.takeIf { it.isNotBlank() } ?: return
    val safeArea = LocalMaFeiSafeArea.current
    val sheetMaterialBackdrop = rememberGlassBackdrop()
    // The close button samples the dialog's own frosted material. Sampling the
    // page backdrop here makes the button visually leak through the dialog.
    val closeButtonBackdrop = sheetMaterialBackdrop
    val contentScrollState = rememberScrollState()
    val sheetDrag = rememberDetailSheetDrag(contentScrollState, onDismiss)
    val largeWindow = LocalMaFeiNavigationChrome.current.effectiveLayout
        ?.let { it.navigation != NavigationPlacement.Bottom } == true
    val sheetAlignment = if (largeWindow) Alignment.Center else Alignment.BottomCenter
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = .28f))
            .mafeiDismissOnOutsidePointer(onDismiss)
            .testTag("detailSheetScrim"),
    )
    GlassSurface(
        backdrop = LocalShellBackdrop.current ?: backdrop,
        spec = OverviewSheetSurfaceSpec,
        tint = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.align(sheetAlignment)
            .padding(horizontal = if (largeWindow) 24.dp else 0.dp)
            .widthIn(max = 720.dp).fillMaxWidth().height(420.dp)
            .then(surfaceMotion)
            .then(if (largeWindow) Modifier else sheetDrag)
            .pointerInput(Unit) { detectTapGestures { } },
        exportedBackdrop = sheetMaterialBackdrop,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                Modifier.fillMaxSize().padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = safeArea.bottom + 10.dp,
                ),
            ) {
                DetailSheetHeader("简介", closeButtonBackdrop, onDismiss)
                Text(
                    text,
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(contentScrollState),
                    color = Color.White.copy(alpha = .92f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
internal fun SeriesSections(
    seasons: List<MediaItem>,
    loading: Boolean,
    error: String?,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)?,
    onSeasonSelected: (MediaItem) -> Unit,
) {
    val largeTypography = usesLargeDetailTypography()
    val showLoading = rememberDelayedDetailLoading(loading)
    Column(Modifier.fillMaxWidth()) {
        DetailAnimatedVisibility(
            visible = seasons.isNotEmpty(),
            enter = fadeIn(tween(DetailMotionEnterMillis)) +
                slideInVertically(tween(DetailMotionEnterMillis)) { it / 16 },
            exit = fadeOut(tween(DetailMotionExitMillis)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (seasons.size > 1) {
                    Text(
                        "共${seasons.size}季",
                        Modifier.padding(horizontal = 16.dp).semantics { heading() },
                        fontSize = if (largeTypography) 18.sp else 15.sp,
                        lineHeight = if (largeTypography) 24.sp else 21.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val horizontalPadding = 16.dp
                    val availableWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(1.dp)
                    // Reuse the same density progression as media/favorite/search
                    // grids. The actual constrained width is used here, so the
                    // count follows sidebar state and window resizing instead of
                    // guessing from a device category.
                    val columns = legacyMediaColumns(availableWidth.value)
                    val gap = legacyMediaGridGap(availableWidth.value)
                    val hoverInset = mediaArtworkGridHoverInset(availableWidth, columns, gap)
                    Column(
                        Modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = hoverInset),
                        verticalArrangement = Arrangement.spacedBy(maxOf(gap, hoverInset)),
                    ) {
                        seasons.chunked(columns).forEach { rowSeasons ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                rowSeasons.forEach { season ->
                                    SeasonCard(
                                        season = season,
                                        artwork = artwork,
                                        sharedArtworkModifier = sharedArtworkModifier,
                                        onClick = { onSeasonSelected(season) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(columns - rowSeasons.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        DetailAnimatedVisibility(
            visible = showLoading,
            enter = fadeIn(tween(DetailMotionFastMillis)),
            exit = fadeOut(tween(DetailMotionExitMillis)),
        ) {
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Inline)
            }
        }
        DetailAnimatedVisibility(
            visible = seasons.isEmpty() && !loading && error != null,
            enter = fadeIn(tween(DetailMotionEnterMillis)),
            exit = fadeOut(tween(DetailMotionExitMillis)),
        ) {
            Text(
                error.orEmpty(),
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun RowScope.SeasonCard(
    season: MediaItem,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LegacyVideoCard(
        item = season,
        title = season.seasonNumber?.let { "第 $it 季" } ?: season.title,
        subtitle = buildList {
            add("共${season.childCount ?: 0}集")
            season.year?.let { add(it.toString()) }
        }.joinToString(" · "),
        textAlign = TextAlign.Center,
        onClick = onClick,
        modifier = sharedArtworkModifier?.invoke(season, modifier) ?: modifier,
        poster = artwork?.let { draw ->
            { cardModifier -> draw(season, cardModifier) }
        },
    )
}

/** 外部链接（IMDb/TMDB/...），与旧版详情页同位；点击交给平台浏览器。 */
@Composable
internal fun ExternalLinksSection(
    links: List<cn.mafei.cmp.core.domain.MediaExternalUrl>,
    onOpenUrl: (String) -> Unit,
) {
    val largeTypography = usesLargeDetailTypography()
    Column(Modifier.fillMaxWidth()) {
        Text(
            "链接",
            Modifier.padding(horizontal = 16.dp).semantics { heading() },
            color = LocalContentColor.current,
            fontSize = if (largeTypography) 18.sp else 15.sp,
            lineHeight = if (largeTypography) 24.sp else 21.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            links.forEach { link ->
                Text(
                    link.name,
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(DetailInformationBackground)
                        .mafeiInteractive(
                            onClick = { onOpenUrl(link.url) },
                            radiusDp = 4f,
                            label = "打开${link.name}",
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White.copy(alpha = .88f),
                    fontSize = if (largeTypography) 14.sp else 12.sp,
                )
            }
        }
    }
}

@Composable
internal fun LegacyPeopleSection(
    people: List<PersonRef>,
    artwork: (@Composable (PersonRef, Modifier) -> Unit)?,
    onPersonSelected: (PersonRef) -> Unit = {},
) {
    val largeLayout = LocalMaFeiNavigationChrome.current.effectiveLayout
        ?.let { it.navigation != NavigationPlacement.Bottom } == true
    val avatarSize = if (largeLayout) 99.dp else 66.dp
    val itemSpacing = if (largeLayout) 18.dp else 12.dp
    val textSpacing = if (largeLayout) 12.dp else 8.dp
    val rowHeight = if (largeLayout) 195.dp else 130.dp
    val titleSize = if (largeLayout) 18.sp else 15.sp
    val nameSize = if (largeLayout) 14.sp else 12.sp
    val roleSize = if (largeLayout) 12.sp else 10.sp
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            "演职人员",
            Modifier.padding(start = 16.dp).semantics { heading() },
            color = LocalContentColor.current,
            fontSize = titleSize,
            lineHeight = if (largeLayout) 24.sp else 21.sp,
            fontWeight = FontWeight.Medium,
        )
        LazyRow(
            Modifier.fillMaxWidth()
                .height(rowHeight + mediaArtworkHoverTopInset(avatarSize)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = mediaArtworkHoverTopInset(avatarSize)),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            items(
                people,
                key = { it.id?.value ?: "${it.name}:${it.role.orEmpty()}:${it.personType}" },
            ) { person ->
                val interaction = rememberMediaCardInteraction()
                val personInteractive = person.id != null
                Column(
                    Modifier.width(avatarSize).mafeiMediaCardInteraction(
                        interaction,
                        onClick = { onPersonSelected(person) },
                        enabled = personInteractive,
                        label = "查看${person.name}",
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(textSpacing),
                ) {
                    Box(
                        Modifier.size(avatarSize).mafeiArtworkFeedback(interaction, CircleShape, enabled = personInteractive)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)),
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
                    Text(
                        person.name,
                        Modifier.width(avatarSize),
                        color = LocalContentColor.current,
                        fontSize = nameSize,
                        lineHeight = if (largeLayout) 18.sp else 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        person.legacyRoleLabel(),
                        Modifier.width(avatarSize),
                        color = LocalContentColor.current.copy(alpha = .68f),
                        fontSize = roleSize,
                        lineHeight = if (largeLayout) 16.sp else 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal fun episodeLabel(item: MediaItem): String = buildString {
    val season = item.seasonNumber
    val episode = item.episodeNumber
    if (season != null && episode != null) append("S${season}:E${episode} · ")
    append(item.title)
}

internal fun episodeProgress(item: MediaItem): Float? =
    item.playbackProgress()?.takeIf { it > 0f && it < 1f }

// All three legacy detail pages use TextSectionAttribute's default maxLines=3.
// LastSpanAttribute(..., 2, ...) reserves suffix characters; it is not a line count.
private const val LegacyOverviewMaxLines = 3

private const val LegacyOverviewMoreTag = "legacy-overview-more"

internal val OverviewSheetSurfaceSpec = GlassSurfaceSpec(
    role = GlassRole.Dialog,
    level = GlassLevel.Opaque,
    tintAlpha = 1f,
    intensity = 0f,
    cornerRadiusDp = 24f,
    material = GlassMaterial.Frosted,
)

private fun legacyOverviewPreview(
    text: String,
    maxWidthPx: Int,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    moreColor: Color,
    onMore: () -> Unit,
): AnnotatedString {
    if (maxWidthPx <= 0) return AnnotatedString(text)

    fun fits(prefixLength: Int): Boolean {
        val candidate = if (prefixLength >= text.length) text else {
            text.substring(0, prefixLength) + "... 更多"
        }
        return !textMeasurer.measure(
            text = AnnotatedString(candidate),
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = true,
            maxLines = LegacyOverviewMaxLines,
            constraints = Constraints(maxWidth = maxWidthPx),
        ).hasVisualOverflow
    }

    if (fits(text.length)) return AnnotatedString(text)

    var left = 0
    var right = text.length
    while (left < right) {
        val middle = (left + right + 1) / 2
        if (fits(middle)) left = middle else right = middle - 1
    }
    val prefixEnd = left.coerceIn(0, text.length)
    return buildAnnotatedString {
        append(text.substring(0, prefixEnd))
        append("... ")
        withLink(LinkAnnotation.Clickable(LegacyOverviewMoreTag, linkInteractionListener = { onMore() })) {
            withStyle(SpanStyle(color = moreColor)) { append("更多") }
        }
    }
}

private fun PersonRef.legacyRoleLabel(): String = when (personType) {
    PersonKind.Actor -> "饰${role.orEmpty()}"
    PersonKind.Director -> "导演"
    else -> role.orEmpty()
}
