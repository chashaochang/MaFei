import { BaseViewModel } from "../../common/base/BaseViewModel";
import { playMediaRefs } from "../../common/CommonFunc";
import { currentMediaCatalogProvider } from "../../media/MediaCatalogAccess";
import { MediaDetail, MediaEpisode, MediaPage } from "../../media/catalog/MediaCatalogModels";
import {
  MediaCatalogProvider,
  MediaEpisodeQueryProfile
} from "../../media/catalog/MediaCatalogProvider";
import { MediaRef } from "../../media/MediaRef";
import { VideoItem } from "../home/hometab/HomeUIState";
import { SeasonDetailUIState } from "./SeasonDetailUIState";
import { curves } from '@kit.ArkUI';
import { BANNER_SCALE_FACTOR, CalculateHeightParam } from "../videodetail/VideoDetailViewModel";
import { ToastUtil } from "../../utils/ToastUtil";

export class SeasonDetailViewModel extends BaseViewModel<SeasonDetailUIState> {
  private originImageHeight: number = 0;
  private springBackAnimation: curves.ICurve = curves.interpolatingSpring(0, 1, 288, 30);
  private catalog?: MediaCatalogProvider
  private requestGeneration: number = 0
  episodeList: MediaRef[] = []
  episodeStartIndex: number = 0

  // 将剧集按30个一组分组，并生成组名与视频列表
  private groupEpisodes(items: MediaEpisode[]): void {
    // 先映射为 VideoItem 列表
    const mapped: VideoItem[] = items.map<VideoItem>((episode: MediaEpisode) => {
      return {
        id: episode.ref.id,
        mediaRef: episode.ref,
        kind: episode.ref.kind,
        name: `${episode.episodeNumber || ''}.${episode.title}`.trim(),
        image: episode.primaryImage?.url || '',
        imageRef: episode.primaryImage,
        subtitle: episode.overview,
        score: episode.communityRating || 0,
        tags: episode.tags.slice(),
        mediaName: this.ui.title,
        type: 'Episode',
        time: `${episode.durationMs > 0 ? (() => {
          const totalSeconds = Math.floor(episode.durationMs / 1000)
          const hours = Math.floor(totalSeconds / 3600)
          const minutes = Math.floor((totalSeconds % 3600) / 60)
          const seconds = totalSeconds % 60
          const parts: string[] = []
          if (hours > 0) {
            parts.push(`${hours}小时`)
          }
          if (minutes > 0) {
            parts.push(`${minutes}分钟`)
          }
          if (seconds > 0) {
            parts.push(`${seconds}秒`)
          }
          // 如果小时和分钟都为0且秒也为0，返回空字符串；如果只有秒存在也显示秒
          return parts.length > 0 ? parts.join('') : '0秒'
        })() : ''}`,

      }
    })

    const groupSize = 30
    const total = mapped.length
    const groups: string[] = []
    const videos: VideoItem[][] = []

    for (let start = 0; start < total; start += groupSize) {
      const endExclusive = Math.min(start + groupSize, total)
      const slice = mapped.slice(start, endExclusive)
      const displayStart = start + 1
      const displayEnd = endExclusive
      let groupName: string
      if (slice.length === 1) {
        // 单独一集只显示单号，如 61
        groupName = `${displayStart}`
      } else {
        groupName = `${displayStart} - ${displayEnd}`
      }
      groups.push(groupName)
      videos.push(slice)
    }

    this.ui.videoGroups = groups
    this.ui.videos = videos
    this.ui.selectGroupIndex = 0
  }

  async getSeasonDetail(ref: MediaRef, seriesRef: MediaRef): Promise<void> {
    const generation = ++this.requestGeneration
    this.ui.firstLoading = true
    let catalog: MediaCatalogProvider
    try {
      catalog = currentMediaCatalogProvider()
    } catch (_error) {
      this.ui.firstLoading = false
      return
    }
    if (!ref.belongsTo(catalog.session) || !seriesRef.belongsTo(catalog.session)) {
      this.ui.firstLoading = false
      return
    }
    this.catalog = catalog
    this.ui.mediaRef = ref
    this.ui.seriesRef = seriesRef
    this.ui.id = ref.id
    this.ui.parentId = seriesRef.id
    try {
      const detail = await catalog.getDetail(ref)
      if (!this.isCurrent(catalog, ref, generation)) {
        return
      }
      this.applyDetail(detail)
      const requests: Promise<MediaPage<MediaEpisode>>[] = [
        catalog.getEpisodes({
          seriesRef: seriesRef,
          seasonRef: ref,
          startIndex: 0,
          limit: 1000,
          profile: MediaEpisodeQueryProfile.Display
        }),
        catalog.getEpisodes({
          seriesRef: seriesRef,
          seasonRef: ref,
          startIndex: 0,
          limit: 1000,
          profile: MediaEpisodeQueryProfile.PlaybackQueue
        })
      ]
      const pages = await Promise.all(requests)
      if (!this.isCurrent(catalog, ref, generation)) {
        return
      }
      this.groupEpisodes(pages[0].items)
      this.applyEpisodeQueue(pages[1].items)
    } catch (_error) {
      if (this.isCurrent(catalog, ref, generation)) {
        this.ui.videoGroups = []
        this.ui.videos = []
        this.episodeList = []
      }
    } finally {
      if (this.isCurrent(catalog, ref, generation)) {
        this.ui.firstLoading = false
      }
    }
  }

  private applyDetail(detail: MediaDetail): void {
    this.ui.title = `${detail.seriesTitle || this.ui.mediaName} ${detail.title}`.trim()
    this.ui.image = detail.primaryImage?.url || ''
    this.ui.imageRef = detail.primaryImage
    this.ui.backgroundImage = (detail.backdropImage || detail.primaryImage)?.url || ''
    this.ui.backgroundImageRef = detail.backdropImage || detail.primaryImage
    this.ui.seriesName = detail.seriesTitle || this.ui.mediaName
    this.ui.seasonName = detail.title
    this.ui.info = this.ui.preInfo || (detail.productionYear !== undefined ? detail.productionYear.toString() : '')
    this.ui.overview = detail.overview
    this.ui.people = detail.personCredits.slice()
    this.ui.isFavorite = detail.userState.favorite.favorite
    this.ui.played = detail.userState.watched.watched
  }

  private applyEpisodeQueue(items: MediaEpisode[]): void {
    if (items.length === 0) {
      this.episodeList = []
      this.episodeStartIndex = 0
      this.ui.episodeName = ''
      return
    }
    const firstNotPlayedIndex = items.findIndex((episode: MediaEpisode): boolean =>
      !episode.userState.watched.watched)
    this.episodeStartIndex = firstNotPlayedIndex >= 0 ? firstNotPlayedIndex : 0
    const targetEpisode = items[this.episodeStartIndex]
    this.ui.episodeName = '第 ' + (targetEpisode.episodeNumber || this.episodeStartIndex + 1) + ' 集'
    this.episodeList = items.map((episode: MediaEpisode): MediaRef => episode.ref)
  }

  playFirst(): void {
    if (this.ui.episodeName == '' || this.episodeList.length === 0) {
      return
    }
    playMediaRefs(this.episodeList, getContext(), undefined, undefined, this.episodeStartIndex)
  }

  calculateImageHeight(param: CalculateHeightParam): boolean {
    if (param.yOffset === 0) {
      this.ui.hasEdgeEffect = false;
    } else {
      this.ui.hasEdgeEffect = param.offset > 0;
    }
    if (param.yOffset > 0) {
      return false;
    }
    if (param.state === ScrollState.Scroll && param.offset !== 0) {
      this.ui.currentScrollState = ScrollState.Scroll;
      const currentBannerHeight: number = this.ui.imageHeight - param.offset;
      if (currentBannerHeight < this.originImageHeight) {
        this.ui.currentScrollState = ScrollState.Fling;
        this.ui.imageHeight = this.originImageHeight;
        return false;
      }
      const bannerOffset: number = -(param.offset / BANNER_SCALE_FACTOR);
      this.ui.imageHeight += bannerOffset
      return true;
    } else if (this.ui.currentScrollState === ScrollState.Scroll && param.state === ScrollState.Fling) {
      this.bannerSpringBack();
      return true;
    }
    return false;
  }

  bannerSpringBack() {
    this.ui.currentScrollState = 2;
    this.ui.context.animateTo({ curve: this.springBackAnimation, duration: 250 }, () => {
      this.changeImageHeight();
    });
  }

  protected changeImageHeight(): void {
    this.ui.imageHeight = this.ui.imageBaseHeight;
    this.originImageHeight = this.ui.imageHeight;
  }

  changeFavorite(): void {
    const catalog = this.catalog
    const ref = this.ui.mediaRef
    if (!catalog || !ref || !catalog.session.capabilities.favorites || !ref.belongsTo(catalog.session)) {
      return
    }
    const favorite = !this.ui.isFavorite
    catalog.setFavorite(ref, favorite).then((state) => {
      if (this.catalog === catalog && this.ui.mediaRef?.equals(ref)) {
        this.ui.isFavorite = state.favorite
        ToastUtil.showToast(this.ui.context, state.favorite ? '已收藏' : '已取消收藏')
      }
    }).catch((): void => ToastUtil.showToast(this.ui.context, favorite ? '收藏失败' : '取消收藏失败'))
  }

  changePlayedState(): void {
    const catalog = this.catalog
    const ref = this.ui.mediaRef
    if (!catalog || !ref || !catalog.session.capabilities.watched || !ref.belongsTo(catalog.session)) {
      return
    }
    const watched = !this.ui.played
    catalog.setWatched(ref, watched).then((state) => {
      if (this.catalog === catalog && this.ui.mediaRef?.equals(ref)) {
        this.ui.played = state.watched
        ToastUtil.showToast(this.ui.context, state.watched ? '标记为已观看' : '标记为未观看')
      }
    }).catch((): void => ToastUtil.showToast(this.ui.context, '操作失败'))
  }

  private isCurrent(catalog: MediaCatalogProvider, ref: MediaRef, generation: number): boolean {
    return this.catalog === catalog && generation === this.requestGeneration &&
      this.ui.mediaRef?.equals(ref) === true && ref.belongsTo(catalog.session)
  }
}
