package cn.mafei.cmp.feature.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassMaterial
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.ui.TextPoster
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.episodeSubtitle
import cn.mafei.cmp.feature.detail.generated.resources.Res
import cn.mafei.cmp.feature.detail.generated.resources.ma_shan_zheng_regular
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_1080
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_4k
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_5dot1
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_7dot1
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_720
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_dolby_atmos
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_dolby_digital
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_dolby_truehd
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_dolby_vision
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_dts
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_dts_hd_ma
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_dts_x
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_hdr
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_hdr10
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_hdr10_plus
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_hevc
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_imax_enhanced
import cn.mafei.cmp.feature.detail.generated.resources.video_tag_sdr
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun DetailHero(
    item: MediaItem,
    height: Dp,
    collapseProgress: State<Float>,
    libraryName: String?,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)?,
    logoArtwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    playbackSource: DetailPlaybackSource?,
    backdrop: GlassBackdrop,
) {
    val horizontalPadding = detailPageHorizontalPadding()
    val heroTags = detailHeroTags(item, playbackSource)
    val largeTypography = usesLargeDetailTypography()
    val hasLogo = item.images.any { it.kind == ImageKind.Logo }
    val usesCalligraphy = item.title.any { it in '\u3400'..'\u9fff' }
    val calligraphyFont = if (usesCalligraphy) {
        FontFamily(Font(Res.font.ma_shan_zheng_regular))
    } else {
        null
    }
    val collapsedScale = when {
        hasLogo -> .28f
        usesCalligraphy -> .23f
        else -> .46f
    }
    // 与手机同一套规格：hero 是 edge-to-edge 的 band，底部羽化融入氛围背景。
    // 之前宽屏把 hero 做成圆角卡片（"桌面化第二套布局"），既失去沉浸感，也让
    // 宽屏/窄屏是两个页面。现在只有一种详情页。
    val roundedHero = false

    val heroBounds = Modifier.fillMaxWidth().height(height)
    Box(
        heroBounds
            .padding(top = if (roundedHero) 12.dp else 0.dp)
            .clip(if (roundedHero) RoundedCornerShape(26.dp) else RoundedCornerShape(0.dp)),
    ) {
        // A rounded card keeps a crisp bottom edge so the card reads as a single
        // surface over the atmosphere; the phone band still feathers into the
        // canvas the way the legacy page did.
        val artworkModifier = if (roundedHero) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxSize().featherBottom(.64f)
        }
        val sharedImageModifier = sharedArtworkModifier?.invoke(item, artworkModifier) ?: artworkModifier
        if (artwork != null) {
            artwork(item, sharedImageModifier)
        } else {
            TextPoster(item.title, sharedImageModifier)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .24f to Color.Black.copy(alpha = .08f),
                    .64f to Color.Black.copy(alpha = .26f),
                    1f to Color.Transparent,
                ),
            ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = horizontalPadding, end = horizontalPadding, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            if (hasLogo && logoArtwork != null) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(196.dp)
                        .height(76.dp)
                        .graphicsLayer {
                            val progress = collapseProgress.value
                            val titlePhase = smoothStep(((progress - .42f) / .58f).coerceIn(0f, 1f))
                            val titleScale = 1f - (1f - collapsedScale) * titlePhase
                            scaleX = titleScale
                            scaleY = titleScale
                            alpha = (1f - (progress - .72f) / .22f).coerceIn(0f, 1f)
                            translationY = -8.dp.toPx() - 8.dp.toPx() * titlePhase
                        },
                ) {
                    logoArtwork(item, Modifier.fillMaxSize())
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val progress = collapseProgress.value
                            val titlePhase = smoothStep(((progress - .42f) / .58f).coerceIn(0f, 1f))
                            val titleScale = 1f - (1f - collapsedScale) * titlePhase
                            scaleX = titleScale
                            scaleY = titleScale
                            alpha = (1f - (progress - .72f) / .22f).coerceIn(0f, 1f)
                            translationY = -16.dp.toPx() * titlePhase
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    detailSubtitle(item).takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(
                            subtitle,
                            color = Color.White,
                            fontSize = if (largeTypography) 16.sp else 14.sp,
                            lineHeight = if (largeTypography) 22.sp else 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        item.title,
                        Modifier.fillMaxWidth().semantics { heading() },
                        color = Color.White,
                        fontSize = when {
                            usesCalligraphy && largeTypography -> 72.sp
                            usesCalligraphy -> 68.sp
                            largeTypography -> 44.sp
                            else -> 38.sp
                        },
                        lineHeight = when {
                            usesCalligraphy && largeTypography -> 76.sp
                            usesCalligraphy -> 72.sp
                            largeTypography -> 50.sp
                            else -> 44.sp
                        },
                        fontFamily = calligraphyFont,
                        fontWeight = if (usesCalligraphy) FontWeight.Normal else FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.displaySmall.copy(
                            shadow = Shadow(Color.Black.copy(alpha = .82f), Offset(0f, 3f), 10f),
                        ),
                    )
                }
            }

            DetailMetadata(item, libraryName, Color.White)
            if (item.mediaType != MediaType.Series && heroTags.isNotEmpty()) {
                DetailHeroMediaTags(
                    heroTags,
                    backdrop,
                    modifier = Modifier.padding(top = DetailHeroTagSpacing),
                )
            }
        }
    }
}

/**
 * The catalog can render before PlaybackInfo has a complete stream list. Keep
 * the badges visible in that window by falling back to the provider's explicit
 * summary labels; never invent a badge from the title or artwork URL.
 */
internal fun detailHeroTags(
    item: MediaItem,
    playbackSource: DetailPlaybackSource?,
): List<DetailVideoTag> {
    val summaryTags = detailVideoTagsFromProviderLabels(
        buildList {
            addAll(item.tags)
            playbackSource?.let { source ->
                addAll(
                    listOf(
                        source.compactLabel,
                        source.summary,
                        source.videoSummary,
                        source.audioSummary,
                    ),
                )
                source.source.name?.let(::add)
            }
        },
    )
    val detailedTags = playbackSource?.tagKeys.orEmpty()
    return DetailVideoTag.values().filter { it in detailedTags || it in summaryTags }
}

@Composable
internal fun DetailHeroMediaTags(
    tags: List<DetailVideoTag>,
    backdrop: GlassBackdrop,
    centered: Boolean = true,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (centered) {
            Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        } else {
            Arrangement.spacedBy(6.dp)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = tags,
            key = { tag -> tag.name },
        ) { tag ->
            DetailMediaBadge(tag.badgeSpec(), backdrop)
        }
    }
}

private data class DetailMediaBadgeSpec(
    val label: String,
    val websiteAsset: DrawableResource? = null,
    val tint: Color? = null,
)

private fun DetailVideoTag.badgeSpec(): DetailMediaBadgeSpec = when (this) {
    DetailVideoTag.Tag8K -> DetailMediaBadgeSpec("8K")
    DetailVideoTag.Tag4K -> DetailMediaBadgeSpec("4K", Res.drawable.video_tag_4k)
    DetailVideoTag.Tag1440 -> DetailMediaBadgeSpec("1440P")
    DetailVideoTag.Tag1080 -> DetailMediaBadgeSpec("1080P", Res.drawable.video_tag_1080)
    DetailVideoTag.Tag720 -> DetailMediaBadgeSpec("720P", Res.drawable.video_tag_720)
    DetailVideoTag.Hdr -> DetailMediaBadgeSpec("HDR", Res.drawable.video_tag_hdr)
    DetailVideoTag.Hdr10 -> DetailMediaBadgeSpec("HDR10", Res.drawable.video_tag_hdr10)
    DetailVideoTag.Hdr10Plus -> DetailMediaBadgeSpec("HDR10+", Res.drawable.video_tag_hdr10_plus)
    DetailVideoTag.Sdr -> DetailMediaBadgeSpec("SDR", Res.drawable.video_tag_sdr)
    DetailVideoTag.Hevc -> DetailMediaBadgeSpec("HEVC", Res.drawable.video_tag_hevc)
    DetailVideoTag.Stereo -> DetailMediaBadgeSpec("立体声")
    DetailVideoTag.Surround51 -> DetailMediaBadgeSpec("5.1", Res.drawable.video_tag_5dot1)
    DetailVideoTag.Surround71 -> DetailMediaBadgeSpec("7.1", Res.drawable.video_tag_7dot1)
    DetailVideoTag.DolbyVision -> DetailMediaBadgeSpec("Dolby Vision", Res.drawable.video_tag_dolby_vision)
    DetailVideoTag.DolbyAtmos -> DetailMediaBadgeSpec("Dolby Atmos", Res.drawable.video_tag_dolby_atmos)
    DetailVideoTag.DolbyTrueHd -> DetailMediaBadgeSpec("Dolby TrueHD", Res.drawable.video_tag_dolby_truehd)
    DetailVideoTag.DolbyDigital -> DetailMediaBadgeSpec("Dolby Digital", Res.drawable.video_tag_dolby_digital)
    DetailVideoTag.Dts -> DetailMediaBadgeSpec("DTS", Res.drawable.video_tag_dts)
    DetailVideoTag.DtsX -> DetailMediaBadgeSpec("DTS:X", Res.drawable.video_tag_dts_x)
    DetailVideoTag.DtsHdMa -> DetailMediaBadgeSpec("DTS-HD MA", Res.drawable.video_tag_dts_hd_ma)
    DetailVideoTag.ImaxEnhanced -> DetailMediaBadgeSpec("IMAX Enhanced", Res.drawable.video_tag_imax_enhanced)
}

@Composable
private fun DetailMediaBadge(
    spec: DetailMediaBadgeSpec,
    backdrop: GlassBackdrop,
) {
    val largeTypography = usesLargeDetailTypography()
    GlassSurface(
        backdrop = backdrop,
        spec = DetailBadgeGlassSpec,
        tint = Color.White,
        modifier = Modifier
            .height(22.dp)
            .semantics { contentDescription = spec.label },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (spec.websiteAsset != null) {
                Image(
                    painter = painterResource(spec.websiteAsset),
                    contentDescription = null,
                    modifier = Modifier.height(18.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = spec.tint?.let(ColorFilter::tint),
                )
            } else {
                Text(
                    text = spec.label,
                    color = Color(0xFF111111),
                    fontSize = if (largeTypography) 14.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = .1.sp,
                    lineHeight = if (largeTypography) 16.sp else 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private val DetailBadgeGlassSpec = GlassSurfaceSpec(
    role = GlassRole.Card,
    level = GlassLevel.Reduced,
    tintAlpha = .56f,
    intensity = .82f,
    cornerRadiusDp = 6f,
    material = GlassMaterial.Frosted,
)

private fun Modifier.featherBottom(startFraction: Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White,
                    startFraction.coerceIn(0f, .98f) to Color.White,
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

@Composable
internal fun DetailMetadata(item: MediaItem, libraryName: String?, color: Color) {
    val largeTypography = usesLargeDetailTypography()
    val metadataFontSize = if (largeTypography) 15.sp else 13.sp
    val metadataLineHeight = if (largeTypography) 21.sp else 18.sp
    val primaryInfo = buildList {
        item.officialRating?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(item.genres.filter { it.isNotBlank() })
    }.joinToString(" · ")
    val secondaryInfo = buildList {
        item.year?.let { add(it.toString()) }
        item.runtimeMs?.takeIf { it > 0L }?.let { add(legacyDetailDurationLabel(it)) }
        libraryName?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")

    if ((item.communityRating ?: 0.0) > 0.0 || primaryInfo.isNotBlank()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                buildAnnotatedString {
                    item.communityRating?.takeIf { it > 0.0 }?.let { rating ->
                        withStyle(SpanStyle(color = ScoreColor)) { append("${formatRating(rating)}分") }
                    }
                    if ((item.communityRating ?: 0.0) > 0.0 && primaryInfo.isNotBlank()) append(" · ")
                    append(primaryInfo)
                },
                color = color,
                fontSize = metadataFontSize,
                lineHeight = metadataLineHeight,
                maxLines = 1,
            )
        }
    }
    if (secondaryInfo.isNotBlank()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                secondaryInfo,
                color = color,
                fontSize = metadataFontSize,
                lineHeight = metadataLineHeight,
                maxLines = 1,
            )
        }
    }
}

internal fun detailSubtitle(item: MediaItem): String = item.episodeSubtitle ?: ""

private fun formatRating(value: Double): String {
    val tenths = (value * 10.0).roundToInt()
    return "${tenths / 10}.${abs(tenths % 10)}"
}

internal val DetailHeroTagSpacing = 6.dp

private val ScoreColor = Color(0xFFF1B020)
