import { BaseViewModel } from "../../common/base/BaseViewModel";
import { playMediaRefs } from "../../common/CommonFunc";
import { currentMediaCatalogProvider } from "../../media/MediaCatalogAccess";
import {
  MediaDetail,
  MediaEpisode,
  MediaPage,
  MediaPlaybackTarget
} from "../../media/catalog/MediaCatalogModels";
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
  private selectedEpisodeDurationMs: number = 0
  episodeList: MediaRef[] = []
  episodeStartIndex: number = 0

  private episodePlayedPercentage(episode: MediaEpisode): number {
    let percentage = Math.min(100, Math.max(0, episode.userState.watched.playedPercentage))
    if (percentage <= 0 && episode.userState.watched.playbackPositionMs > 0 && episode.durationMs > 0) {
      percentage = Math.min(100, episode.userState.watched.playbackPositionMs * 100 / episode.durationMs)
    }
    return percentage
  }

  private applyPlaybackProgress(playbackPositionMs: number, playedPercentage: number,
    durationMs: number): void {
    const position = Math.max(0, playbackPositionMs)
    const duration = Math.max(0, durationMs)
    let percentage = Math.min(100, Math.max(0, playedPercentage))
    if (percentage <= 0 && position > 0 && duration > 0) {
      percentage = Math.min(100, position * 100 / duration)
    }
    const displayPosition = position > 0 ? position :
      (percentage > 0 && percentage < 100 && duration > 0 ? duration * percentage / 100 : 0)

    this.selectedEpisodeDurationMs = duration
    this.ui.playbackPositionMs = position
    this.ui.percentageWatched = percentage
    this.ui.remainingTime = ''
    if (displayPosition <= 0 || duration <= displayPosition || percentage >= 100) return

    const remainingMinutes = Math.max(0, Math.floor((duration - displayPosition) / 60000))
    const hours = Math.floor(remainingMinutes / 60)
    const minutes = remainingMinutes % 60
    this.ui.remainingTime = hours > 0 ? `剩余 ${hours} 小时 ${minutes} 分钟` : `剩余 ${minutes} 分钟`
  }

  private clearPlaybackQueue(): void {
    this.episodeList = []
    this.episodeStartIndex = 0
    this.ui.episodeName = ''
    this.applyPlaybackProgress(0, 0, 0)
  }

  // 将剧集按30个一组分组，并生成组名与视频列表
  private groupEpisodes(items: MediaEpisode[]): void {
    // 先映射为 VideoItem 列表
    const mapped: VideoItem[] = items.map<VideoItem>((episode: MediaEpisode) => {
      const image = episode.primaryImage ?? episode.backdropImage
      return {
        id: episode.ref.id,
        mediaRef: episode.ref,
        kind: episode.ref.kind,
        name: `${episode.episodeNumber ?? ''}.${episode.title}`.trim(),
        image: image?.url || '',
        imageRef: image,
        subtitle: episode.overview,
        score: episode.communityRating || 0,
        tags: episode.tags.slice(),
        percent: this.episodePlayedPercentage(episode),
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
      const displayStart = items[start].episodeNumber ?? start + 1
      const displayEnd = items[endExclusive - 1].episodeNumber ?? endExclusive
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

  async getSeasonDetail(ref: MediaRef, seriesRef: MediaRef, partialRefresh: boolean = false): Promise<void> {
    const generation = ++this.requestGeneration
    if (partialRefresh) {
      this.ui.episodeLoading = true
      this.clearPlaybackQueue()
    } else {
      this.ui.firstLoading = true
      this.ui.episodeLoading = false
    }
    let catalog: MediaCatalogProvider
    try {
      catalog = currentMediaCatalogProvider()
    } catch (_error) {
      if (partialRefresh) {
        this.ui.episodeLoading = false
      } else {
        this.ui.firstLoading = false
      }
      return
    }
    if (!ref.belongsTo(catalog.session) || !seriesRef.belongsTo(catalog.session)) {
      if (partialRefresh) {
        this.ui.episodeLoading = false
      } else {
        this.ui.firstLoading = false
      }
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
      this.applyEpisodeQueue(pages[1].items, detail.playbackTarget)
    } catch (_error) {
      if (this.isCurrent(catalog, ref, generation)) {
        if (!partialRefresh) {
          this.ui.videoGroups = []
          this.ui.videos = []
        }
        this.clearPlaybackQueue()
      }
    } finally {
      if (this.isCurrent(catalog, ref, generation)) {
        this.ui.firstLoading = false
        this.ui.episodeLoading = false
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

  private applyEpisodeQueue(items: MediaEpisode[], playbackTarget?: MediaPlaybackTarget): void {
    if (items.length === 0) {
      this.clearPlaybackQueue()
      return
    }
    const targetRef = playbackTarget?.ref
    let targetIndex = targetRef
      ? items.findIndex((episode: MediaEpisode): boolean => episode.ref.equals(targetRef)) : -1
    if (targetIndex < 0 && playbackTarget?.episodeNumber !== undefined) {
      targetIndex = items.findIndex((episode: MediaEpisode): boolean =>
        episode.episodeNumber === playbackTarget.episodeNumber)
    }
    const matchedPlaybackTarget = targetIndex >= 0
    if (targetIndex < 0) {
      targetIndex = items.findIndex((episode: MediaEpisode): boolean => {
        const percentage = this.episodePlayedPercentage(episode)
        return percentage > 0 && percentage < 100
      })
    }
    if (targetIndex < 0) {
      targetIndex = items.findIndex((episode: MediaEpisode): boolean => !episode.userState.watched.watched)
    }
    this.episodeStartIndex = targetIndex >= 0 ? targetIndex : 0
    const targetEpisode = items[this.episodeStartIndex]
    this.ui.episodeName = '第 ' + (targetEpisode.episodeNumber ?? this.episodeStartIndex + 1) + ' 集'
    this.episodeList = items.map((episode: MediaEpisode): MediaRef => episode.ref)
    const targetPosition = matchedPlaybackTarget ? Math.max(0, playbackTarget?.playbackPositionMs || 0) : 0
    const targetPercentage = matchedPlaybackTarget ? Math.max(0, playbackTarget?.playedPercentage || 0) : 0
    const durationMs = Math.max(0,
      (matchedPlaybackTarget ? playbackTarget?.durationMs : 0) || targetEpisode.durationMs)
    if (targetPosition > 0 || targetPercentage > 0) {
      this.applyPlaybackProgress(targetPosition, targetPercentage, durationMs)
    } else {
      this.applyPlaybackProgress(
        targetEpisode.userState.watched.playbackPositionMs,
        targetEpisode.userState.watched.playedPercentage,
        targetEpisode.durationMs
      )
    }
  }

  playFirst(): void {
    if (this.ui.episodeName == '' || this.episodeList.length === 0) {
      return
    }
    playMediaRefs(this.episodeList, getContext(), undefined, undefined, this.episodeStartIndex,
      this.ui.playbackPositionMs > 0 ? this.ui.playbackPositionMs * 10000 : undefined)
  }

  initializeHeroHeight(height: number): void {
    this.ui.imageBaseHeight = height;
    this.ui.imageHeight = height;
    this.originImageHeight = height;
    this.ui.currentScrollState = ScrollState.Idle;
    this.ui.hasEdgeEffect = false;
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
        this.applyPlaybackProgress(state.playbackPositionMs, state.playedPercentage,
          this.selectedEpisodeDurationMs)
        ToastUtil.showToast(this.ui.context, state.watched ? '标记为已观看' : '标记为未观看')
      }
    }).catch((): void => ToastUtil.showToast(this.ui.context, '操作失败'))
  }

  private isCurrent(catalog: MediaCatalogProvider, ref: MediaRef, generation: number): boolean {
    return this.catalog === catalog && generation === this.requestGeneration &&
      this.ui.mediaRef?.equals(ref) === true && ref.belongsTo(catalog.session)
  }
}
