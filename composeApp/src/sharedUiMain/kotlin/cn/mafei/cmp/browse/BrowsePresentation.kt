package cn.mafei.cmp

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.media.landscapeArtworkCandidates
import cn.mafei.cmp.common.media.posterArtworkCandidates
import cn.mafei.cmp.common.ui.LegacySortChoice
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaDisplayStyle
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.MediaSortField
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.SortDirection
import cn.mafei.cmp.session.*
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun LegacyPersonArtwork(
    image: MediaImage?,
    name: String,
    cache: ArtworkMemoryCache,
    fetch: suspend (MediaImage, Int) -> ByteArray?,
    modifier: Modifier = Modifier,
    targetWidthPx: Int = 240,
) {
    MediaArtwork(
        image = image,
        title = name,
        cache = cache,
        fetch = fetch,
        modifier = modifier,
        fallback = { fallbackModifier ->
            Image(painter = painterResource(MaFeiLegacyDrawables.castAvatar), contentDescription = null, modifier = fallbackModifier)
        },
        targetWidthPx = targetWidthPx,
    )
}

internal fun MediaItem.preferredPoster(): List<MediaImage> = posterArtworkCandidates()

internal fun MediaItem.preferredLandscape(): List<MediaImage> = landscapeArtworkCandidates()

internal fun MediaItem.preferredLogo(): MediaImage? = images.firstOrNull { it.kind == ImageKind.Logo }

internal const val ChasingFeatureReleased = false

internal fun legacySortChoices(provider: ProviderKind, direction: SortDirection): List<LegacySortChoice<MediaSort>> {
    // 可排序字段集由 model 的展示风格表给出（core:domain/MediaPresentation.kt），
    // 此处不再判断 provider；UI 只负责把字段翻译成菜单文案。
    val labels =
        mapOf(
            MediaSortField.Default to "默认",
            MediaSortField.Title to "按名称",
            MediaSortField.DateAdded to "按添加日期",
            MediaSortField.PremiereDate to "按首播日期",
            MediaSortField.DatePlayed to "按播放日期",
            MediaSortField.CommunityRating to "按社区评分",
            MediaSortField.CriticRating to "按影评人评分",
            MediaSortField.ProductionYear to "按出品年份",
            MediaSortField.PlayCount to "按播放次数",
            MediaSortField.Runtime to "按时长",
            MediaSortField.OfficialRating to "按官方评级",
            MediaSortField.Random to "随机",
            MediaSortField.Album to "按专辑",
            MediaSortField.Artist to "按艺术家",
            MediaSortField.AlbumArtist to "按专辑艺术家",
            MediaSortField.AiredEpisodeOrder to "按播出集数顺序",
            MediaSortField.SeriesSortName to "按剧集名称",
            MediaSortField.Studio to "按制作公司",
            MediaSortField.IsFavoriteOrLiked to "按收藏/喜欢",
            MediaSortField.DateLastContentAdded to "按最后添加内容日期",
            MediaSortField.IsUnplayed to "未播放优先",
            MediaSortField.IsPlayed to "已播放优先",
        )
    return MediaDisplayStyle.forProvider(provider).sortableFields.map { field -> LegacySortChoice(labels.getValue(field), MediaSort(field, direction)) }
}
