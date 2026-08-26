import { common, Want } from '@kit.AbilityKit'
import { curves } from '@kit.ArkUI'
import { BaseViewModel } from '../../common/base/BaseViewModel'
import { getTagBase64, playMediaRefs } from '../../common/CommonFunc'
import { AppPreference } from '../../data/AppPreference'
import { MediaLibraryRefreshEvent } from '../../events/Events'
import { currentMediaCatalogProvider } from '../../media/MediaCatalogAccess'
import {
  MediaDetail,
  MediaEpisode,
  MediaFileInfo,
  MediaSeason,
  MediaStreamInfo,
  MediaStreamKind,
  MediaSummary
} from '../../media/catalog/MediaCatalogModels'
import {
  MediaCatalogProvider,
  MediaEpisodeQueryProfile
} from '../../media/catalog/MediaCatalogProvider'
import { MediaRef, MediaRefKind } from '../../media/MediaRef'
import { ToastUtil } from '../../utils/ToastUtil'
import { VideoItem } from '../home/hometab/HomeUIState'
import {
  ManagementLibraryOperationCode,
  ManagementLibraryOperationResult,
  ManagementMediaDeleteSummary,
  ManagementMediaDeleteTarget
} from '../management/library/ManagementLibraryModels'
import { ManagementMediaDeletePolicy } from '../management/library/ManagementMediaDeletePolicy'
import { ManagementMediaDeleteRepository } from '../management/library/ManagementMediaDeleteRepository'
import { VideoDetailUIState, VideoTag } from './VideoDetailUIState'

interface EpisodeQueueSelection {
  readonly refs: MediaRef[]
  readonly startIndex: number
  readonly label: string
}

export class VideoDetailViewModel extends BaseViewModel<VideoDetailUIState> {
  private deleteRepository: ManagementMediaDeleteRepository = new ManagementMediaDeleteRepository()
  private originImageHeight: number = 0
  private springBackAnimation: curves.ICurve = curves.interpolatingSpring(0, 1, 288, 30)
  private catalog?: MediaCatalogProvider
  private requestGeneration: number = 0
  episodeList: MediaRef[] = []
  episodeStartIndex: number = 0

  private formatDuration(durationMs: number): string {
    if (durationMs <= 0) {
      return ''
    }
    const totalMinutes = Math.max(1, Math.floor(durationMs / 60000))
    const hours = Math.floor(totalMinutes / 60)
    const minutes = totalMinutes % 60
    if (hours > 0 && minutes > 0) {
      return `${hours}小时${minutes}分钟`
    }
    if (hours > 0) {
      return `${hours}小时`
    }
    return `${minutes}分钟`
  }

  private formatEpisodePlayText(episode: MediaEpisode | MediaSummary): string {
    const parts: string[] = []
    if (episode.seasonNumber !== undefined) {
      parts.push(`第 ${episode.seasonNumber} 季`)
    }
    parts.push(`第 ${episode.episodeNumber || 1} 集`)
    return parts.join('  ')
  }

  private isCurrent(catalog: MediaCatalogProvider, ref: MediaRef, generation: number): boolean {
    return this.catalog === catalog && generation === this.requestGeneration &&
      this.ui.mediaRef?.equals(ref) === true && ref.belongsTo(catalog.session)
  }

  private applyDeleteCapability(detail: MediaDetail, catalog: MediaCatalogProvider): void {
    const firstPath = detail.mediaFiles.length > 0 ? detail.mediaFiles[0].path : ''
    this.ui.deletePath = detail.path || firstPath
    this.ui.canDelete = catalog.session.capabilities.management && detail.canDelete === true &&
      this.ui.deletePath.trim().length > 0
  }

  private mediaInfo(detail: MediaDetail): string {
    const parts: string[] = []
    if (detail.officialRating.length > 0) parts.push(detail.officialRating)
    if (detail.productionYear !== undefined) parts.push(detail.productionYear.toString())
    detail.genres.forEach((genre: string): void => {
      if (genre.length > 0) parts.push(genre)
    })
    const duration = this.formatDuration(detail.durationMs)
    if (duration.length > 0) parts.push(duration)
    if (this.ui.mediaName.length > 0) parts.push(this.ui.mediaName)
    return parts.join(' · ')
  }

  private applyDetail(detail: MediaDetail, catalog: MediaCatalogProvider): void {
    this.ui.mediaRef = detail.ref
    this.ui.seriesRef = detail.seriesRef
    this.ui.seasonRef = detail.seasonRef
    this.ui.id = detail.ref.id
    this.ui.title = detail.title
    this.ui.isTvShows = detail.ref.kind === MediaRefKind.Series
    this.ui.isMovie = detail.ref.kind === MediaRefKind.Movie
    this.ui.imageRef = detail.backdropImage || detail.primaryImage
    this.ui.image = this.ui.imageRef?.url || ''
    this.ui.logoRef = detail.logoImage
    this.ui.logo = detail.logoImage?.url || ''
    this.ui.score = detail.communityRating || 0
    this.ui.overview = detail.overview
    this.ui.people = detail.personCredits.slice()
    this.ui.isFavorite = detail.userState.favorite.favorite
    this.ui.played = detail.userState.watched.watched
    this.ui.mediaSources = detail.mediaFiles.slice()
    this.ui.externalUrls = detail.externalLinks.slice()
    this.ui.playbackPositionMs = detail.userState.watched.playbackPositionMs
    this.ui.percentageWatched = detail.userState.watched.playedPercentage
    this.ui.remainingTime = ''
    this.ui.playButtonText = '播放'
    this.ui.subtitle = ''
    this.ui.episodeName = ''
    this.ui.selectedMediaSourceIndex = this.preferredMediaSourceIndex(detail.ref, this.ui.mediaSources)
    this.ui.selectedAudio = undefined
    this.ui.selectedSubtitle = undefined
    this.applyDeleteCapability(detail, catalog)

    if (detail.ref.kind === MediaRefKind.Episode) {
      const episode: MediaEpisode = {
        ref: detail.ref,
        seriesRef: detail.seriesRef || detail.ref,
        seasonRef: detail.seasonRef,
        title: detail.title,
        seriesTitle: detail.seriesTitle || '',
        overview: detail.overview,
        seasonNumber: detail.seasonNumber,
        episodeNumber: detail.episodeNumber,
        premiereDate: detail.premiereDate,
        durationMs: detail.durationMs,
        communityRating: detail.communityRating,
        tags: detail.tags.slice(),
        primaryImage: detail.primaryImage,
        backdropImage: detail.backdropImage,
        userState: detail.userState
      }
      this.ui.episodeName = this.formatEpisodePlayText(episode)
      const episodeParts: string[] = []
      if ((detail.seriesTitle || '').length > 0) episodeParts.push(detail.seriesTitle || '')
      if ((detail.seasonTitle || '').length > 0) {
        episodeParts.push(detail.seasonTitle || '')
      } else if (detail.seasonNumber !== undefined) {
        episodeParts.push(`第 ${detail.seasonNumber} 季`)
      }
      episodeParts.push(`第 ${detail.episodeNumber || 1} 集`)
      this.ui.subtitle = episodeParts.join(' · ')
    }
    this.ui.info = this.mediaInfo(detail)
    if (this.ui.playbackPositionMs > 0) {
      const remainingMs = Math.max(0, detail.durationMs - this.ui.playbackPositionMs)
      const remainingMinutes = Math.max(0, Math.floor(remainingMs / 60000))
      const hours = Math.floor(remainingMinutes / 60)
      const minutes = remainingMinutes % 60
      this.ui.remainingTime = hours > 0 ? `剩余 ${hours} 小时 ${minutes} 分钟` : `剩余 ${minutes} 分钟`
      this.ui.playButtonText = '继续播放'
    }
    this.getFileInfo()
    this.getVideoInfo()
  }

  private mapSeasons(seasons: MediaSeason[]): VideoItem[] {
    return seasons.map<VideoItem>((season: MediaSeason): VideoItem => {
      const image = season.primaryImage || season.backdropImage
      const subtitleParts: string[] = [`共${season.episodeCount}集`]
      if (season.productionYear !== undefined) subtitleParts.push(season.productionYear.toString())
      return {
        id: season.ref.id,
        mediaRef: season.ref,
        seriesRef: season.seriesRef,
        kind: season.ref.kind,
        name: season.title,
        image: image?.url || '',
        imageRef: image,
        subtitle: subtitleParts.join(' · '),
        score: season.communityRating || 0,
        tags: [],
        mediaName: this.ui.title,
        type: 'Season'
      }
    })
  }

  private applyEpisodeQueue(selection: EpisodeQueueSelection): void {
    this.episodeList = selection.refs.slice()
    this.episodeStartIndex = selection.startIndex
    this.ui.episodeName = selection.label
    if (selection.label.length > 0) this.ui.playButtonText = selection.label
  }

  private async seriesQueue(catalog: MediaCatalogProvider, seriesRef: MediaRef): Promise<EpisodeQueueSelection> {
    let preferredId = ''
    let preferredLabel = ''
    try {
      const nextPage = await catalog.nextUp({
        seriesRef: seriesRef,
        limit: 1,
        disableFirstEpisode: false,
        enableResumable: true,
        enableRewatching: true
      })
      if (nextPage.items.length > 0) {
        preferredId = nextPage.items[0].ref.id
        preferredLabel = this.formatEpisodePlayText(nextPage.items[0])
      }
    } catch (_error) {
    }
    const episodePage = await catalog.getEpisodes({
      seriesRef: seriesRef,
      startIndex: 0,
      limit: 1000,
      profile: MediaEpisodeQueryProfile.PlaybackQueue
    })
    if (episodePage.items.length === 0) return { refs: [], startIndex: 0, label: '' }
    let startIndex = preferredId.length > 0 ?
      episodePage.items.findIndex((episode: MediaEpisode): boolean => episode.ref.id === preferredId) : -1
    if (startIndex < 0) startIndex = 0
    const target = episodePage.items[startIndex]
    return {
      refs: episodePage.items.map((episode: MediaEpisode): MediaRef => episode.ref),
      startIndex: startIndex,
      label: preferredLabel.length > 0 ? preferredLabel : this.formatEpisodePlayText(target)
    }
  }

  private async episodeQueue(catalog: MediaCatalogProvider, detail: MediaDetail): Promise<EpisodeQueueSelection> {
    if (!detail.seriesRef) return { refs: [detail.ref], startIndex: 0, label: this.ui.episodeName }
    const episodePage = await catalog.getEpisodes({
      seriesRef: detail.seriesRef,
      seasonRef: detail.seasonRef,
      startIndex: 0,
      limit: 1000,
      profile: MediaEpisodeQueryProfile.PlaybackQueue
    })
    if (episodePage.items.length === 0) {
      return { refs: [detail.ref], startIndex: 0, label: this.ui.episodeName }
    }
    let startIndex = episodePage.items.findIndex((episode: MediaEpisode): boolean => episode.ref.equals(detail.ref))
    if (startIndex < 0) startIndex = 0
    return {
      refs: episodePage.items.map((episode: MediaEpisode): MediaRef => episode.ref),
      startIndex: startIndex,
      label: this.formatEpisodePlayText(episodePage.items[startIndex])
    }
  }

  async getDetail(ref: MediaRef): Promise<void> {
    const generation = ++this.requestGeneration
    this.ui.firstLoading = true
    let catalog: MediaCatalogProvider
    try {
      catalog = currentMediaCatalogProvider()
    } catch (_error) {
      this.ui.firstLoading = false
      return
    }
    if (!ref.belongsTo(catalog.session)) {
      this.ui.firstLoading = false
      return
    }
    this.catalog = catalog
    this.ui.mediaRef = ref
    this.ui.id = ref.id
    try {
      const detail = await catalog.getDetail(ref)
      if (!this.isCurrent(catalog, ref, generation)) return
      this.applyDetail(detail, catalog)
      if (detail.ref.kind === MediaRefKind.Series) {
        const seasonsPromise: Promise<MediaSeason[]> = detail.seasons.length > 0 ?
          Promise.resolve(detail.seasons.slice()) : catalog.getSeasons(detail.ref)
        const queuePromise: Promise<EpisodeQueueSelection> = this.seriesQueue(catalog, detail.ref)
        try {
          const seasons = await seasonsPromise
          if (this.isCurrent(catalog, ref, generation)) this.ui.seasons = this.mapSeasons(seasons)
        } catch (_error) {
          if (this.isCurrent(catalog, ref, generation)) this.ui.seasons = []
        }
        try {
          const selection = await queuePromise
          if (this.isCurrent(catalog, ref, generation)) this.applyEpisodeQueue(selection)
        } catch (_error) {
          if (this.isCurrent(catalog, ref, generation)) {
            this.episodeList = []
            this.episodeStartIndex = 0
          }
        }
      } else if (detail.ref.kind === MediaRefKind.Episode) {
        try {
          const selection = await this.episodeQueue(catalog, detail)
          if (this.isCurrent(catalog, ref, generation)) {
            this.episodeList = selection.refs.slice()
            this.episodeStartIndex = selection.startIndex
          }
        } catch (_error) {
          if (this.isCurrent(catalog, ref, generation)) {
            this.episodeList = [detail.ref]
            this.episodeStartIndex = 0
          }
        }
      } else {
        this.ui.seasons = []
        this.episodeList = []
        this.episodeStartIndex = 0
      }
    } catch (_error) {
    } finally {
      if (this.isCurrent(catalog, ref, generation)) this.ui.firstLoading = false
    }
  }

  hasEpisodeQueue(): boolean {
    return this.episodeList.length > 0
  }

  async loadDeleteAccess(): Promise<void> {
    this.ui.isAdministrator = false
    const catalog = this.catalog
    if (!catalog || !catalog.session.capabilities.management) return
    const result = await this.deleteRepository.requireAdministrator()
    if (this.catalog === catalog) this.ui.isAdministrator = result.ok
  }

  private currentDeleteTarget(): ManagementMediaDeleteTarget {
    const target = new ManagementMediaDeleteTarget()
    target.id = this.ui.id
    target.name = this.ui.title
    target.path = this.ui.deletePath
    target.canDelete = this.ui.canDelete
    return target
  }

  deleteSummary(): ManagementMediaDeleteSummary {
    return ManagementMediaDeletePolicy.summarize([this.currentDeleteTarget()], this.ui.isAdministrator)
  }

  async deleteCurrentMedia(): Promise<ManagementLibraryOperationResult> {
    if (this.ui.deleting) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.Conflict, 'management_media_delete_conflict')
    }
    const target = this.currentDeleteTarget()
    if (!ManagementMediaDeletePolicy.canDeleteTarget(true, target)) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.PermissionDenied, 'management_media_delete_no_eligible')
    }
    this.ui.deleting = true
    try {
      const result = await this.deleteRepository.deleteTargets([target])
      if (result.ok) {
        this.ui.canDelete = false
        this.ui.context.getHostContext()?.eventHub.emit(MediaLibraryRefreshEvent)
      }
      return result
    } finally {
      this.ui.deleting = false
    }
  }

  playCurrent(): void {
    const ref = this.ui.mediaRef
    if (!ref) return
    if (this.ui.isTvShows) {
      this.playFirst()
      return
    }
    let refs: MediaRef[] = [ref]
    let startIndex = 0
    if (ref.kind === MediaRefKind.Episode && this.episodeList.length > 0) {
      refs = this.episodeList.slice()
      startIndex = this.episodeStartIndex
    }
    const source = this.currentMediaFile()
    playMediaRefs(refs, getContext(), this.ui.selectedAudio?.index, this.ui.selectedSubtitle?.index,
      startIndex, this.ui.playbackPositionMs > 0 ? this.ui.playbackPositionMs * 10000 : undefined,
      source?.id)
  }

  playFirst(): void {
    if (this.episodeList.length === 0) return
    playMediaRefs(this.episodeList, getContext(), undefined, undefined, this.episodeStartIndex)
  }

  /**
   * Applies a playback-version choice at the detail boundary.  Keeping this in the view model
   * makes the selected source available to all later playback entries, including Home's
   * "continue watching" card.
   */
  selectMediaSource(index: number): void {
    if (index < 0 || index >= this.ui.mediaSources.length || index === this.ui.selectedMediaSourceIndex) {
      return
    }
    this.ui.selectedMediaSourceIndex = index
    this.ui.selectedAudio = undefined
    this.ui.selectedSubtitle = undefined
    const ref = this.ui.mediaRef
    const sourceId = this.currentMediaFile()?.id.trim() || ''
    if (ref && sourceId.length > 0) {
      try {
        AppPreference.getInstance().setPreferredMediaSourceId(ref, sourceId)
      } catch (_error) {
        // A preference write must not prevent the user from changing versions.
      }
    }
    this.getFileInfo()
    this.getVideoInfo()
  }

  openExternalUrl(url: string): void {
    if (url.length === 0) return
    try {
      const context = this.ui.context.getHostContext() as common.UIAbilityContext
      const want: Want = {
        action: 'ohos.want.action.viewData',
        entities: ['entity.system.browsable'],
        uri: url
      }
      context.startAbility(want).catch((): void => {
      })
    } catch (_error) {
    }
  }

  initializeHeroHeight(height: number): void {
    this.ui.imageBaseHeight = height
    this.ui.imageHeight = height
    this.originImageHeight = height
    this.ui.currentScrollState = ScrollState.Idle
    this.ui.hasEdgeEffect = false
  }

  calculateImageHeight(param: CalculateHeightParam): boolean {
    if (param.yOffset === 0) this.ui.hasEdgeEffect = false
    else this.ui.hasEdgeEffect = param.offset > 0
    if (param.yOffset > 0) return false
    if (param.state === ScrollState.Scroll && param.offset !== 0) {
      this.ui.currentScrollState = ScrollState.Scroll
      const currentBannerHeight: number = this.ui.imageHeight - param.offset
      if (currentBannerHeight < this.originImageHeight) {
        this.ui.currentScrollState = ScrollState.Fling
        this.ui.imageHeight = this.originImageHeight
        return false
      }
      this.ui.imageHeight += -(param.offset / BANNER_SCALE_FACTOR)
      return true
    } else if (this.ui.currentScrollState === ScrollState.Scroll && param.state === ScrollState.Fling) {
      this.bannerSpringBack()
      return true
    }
    return false
  }

  bannerSpringBack(): void {
    this.ui.currentScrollState = ScrollState.Fling
    this.ui.context.animateTo({ curve: this.springBackAnimation, duration: 250 }, (): void => {
      this.changeImageHeight()
    })
  }

  protected changeImageHeight(): void {
    this.ui.imageHeight = this.ui.imageBaseHeight
    this.originImageHeight = this.ui.imageHeight
  }

  changeFavorite(): void {
    const catalog = this.catalog
    const ref = this.ui.mediaRef
    if (!catalog || !ref || !catalog.session.capabilities.favorites || !ref.belongsTo(catalog.session)) return
    const favorite = !this.ui.isFavorite
    catalog.setFavorite(ref, favorite).then((state): void => {
      if (this.catalog === catalog && this.ui.mediaRef?.equals(ref)) {
        this.ui.isFavorite = state.favorite
        ToastUtil.showToast(this.ui.context, state.favorite ? '已收藏' : '已取消收藏')
      }
    }).catch((): void => ToastUtil.showToast(this.ui.context, favorite ? '收藏失败' : '取消收藏失败'))
  }

  changePlayedState(): void {
    const catalog = this.catalog
    const ref = this.ui.mediaRef
    if (!catalog || !ref || !catalog.session.capabilities.watched || !ref.belongsTo(catalog.session)) return
    const watched = !this.ui.played
    catalog.setWatched(ref, watched).then((state): void => {
      if (this.catalog === catalog && this.ui.mediaRef?.equals(ref)) {
        this.ui.played = state.watched
        ToastUtil.showToast(this.ui.context, state.watched ? '标记为已观看' : '标记为未观看')
      }
    }).catch((): void => ToastUtil.showToast(this.ui.context, '操作失败'))
  }

  private currentMediaFile(): MediaFileInfo | undefined {
    if (this.ui.mediaSources.length === 0) return undefined
    const index = Math.min(Math.max(this.ui.selectedMediaSourceIndex, 0), this.ui.mediaSources.length - 1)
    return this.ui.mediaSources[index]
  }

  private preferredMediaSourceIndex(ref: MediaRef, sources: MediaFileInfo[]): number {
    if (sources.length < 2) {
      return 0
    }
    try {
      const preferredId = AppPreference.getInstance().getPreferredMediaSourceId(ref)
      const index = sources.findIndex((source: MediaFileInfo): boolean => source.id === preferredId)
      return index >= 0 ? index : 0
    } catch (_error) {
      // Detail loading must not depend on preference initialization or storage availability.
      return 0
    }
  }

  private videoTagsByStreams(streams: MediaStreamInfo[]): VideoTag[] {
    const tags: VideoTag[] = []
    streams.forEach((stream: MediaStreamInfo): void => {
      if (stream.kind === MediaStreamKind.Video) {
        if ((stream.width || 0) >= 3800 || (stream.height || 0) >= 2000) {
          if (!tags.includes('4K')) tags.push('4K')
        } else if ((stream.width || 0) >= 1900 || (stream.height || 0) >= 1000) {
          if (!tags.includes('1080')) tags.push('1080')
        }
        const range = (stream.videoRangeType || '').toUpperCase()
        if ((range.includes('DOVI') || range.includes('DOLBY')) && !tags.includes('DOLBY_VISION')) {
          tags.push('DOLBY_VISION')
        } else if (range.includes('HDR') && !tags.includes('HDR')) {
          tags.push('HDR')
        } else if (range.includes('SDR') && !tags.includes('SDR')) {
          tags.push('SDR')
        }
      } else if (stream.kind === MediaStreamKind.Audio) {
        const layout = (stream.channelLayout || '').toLowerCase()
        const codec = stream.codec.toLowerCase()
        if (layout.includes('stereo') && !tags.includes('STEREO')) tags.push('STEREO')
        if (codec.includes('dts') && !tags.includes('DTS')) {
          tags.push('DTS')
        } else if (codec.includes('dolby') || codec.includes('ac3') || codec.includes('eac3')) {
          if (layout.includes('7.1') && !tags.includes('DOLBY_SURROUND')) tags.push('DOLBY_SURROUND')
          else if (layout.includes('5.1') && !tags.includes('DOLBY_ATOMS')) tags.push('DOLBY_ATOMS')
        }
      }
    })
    return tags
  }

  getTags(): string[] {
    const source = this.currentMediaFile()
    this.ui.tags = source ? this.videoTagsByStreams(source.streams).map((tag: VideoTag): string => {
      return getTagBase64(tag)
    }).filter((value: string): boolean => value.length > 0) : []
    return this.ui.tags
  }

  getFileInfo(): void {
    const source = this.currentMediaFile()
    if (!source) {
      this.ui.path = ''
      this.ui.size = ''
      return
    }
    this.ui.path = source.path
    this.ui.size = source.sizeBytes > 0 ? (source.sizeBytes / 1024 / 1024 / 1024).toFixed(2) + ' GB' : ''
  }

  getVideoInfo(): void {
    const source = this.currentMediaFile()
    if (!source) {
      this.ui.videoInfo = ''
      this.ui.audioInfo = ''
      this.ui.subtitleInfo = ''
      return
    }
    this.ui.videoInfo = source.streams.filter((stream: MediaStreamInfo): boolean => {
      return stream.kind === MediaStreamKind.Video
    }).map((stream: MediaStreamInfo): string => {
      const bitrate = stream.bitrate ? `${Math.floor(stream.bitrate / 1000000)} mbps` : ''
      const bitDepth = stream.bitDepth ? `${stream.bitDepth} bit` : ''
      return [stream.displayTitle, bitrate, bitDepth].filter((value: string): boolean => value.length > 0).join(' · ')
    }).join('\n')
    this.ui.audioInfo = source.streams.filter((stream: MediaStreamInfo): boolean => {
      return stream.kind === MediaStreamKind.Audio
    }).map((stream: MediaStreamInfo): string => {
      return `${stream.displayTitle}${stream.sampleRate ? ` · ${stream.sampleRate} Hz` : ''}`
    }).join('\n')
    this.ui.subtitleInfo = source.streams.filter((stream: MediaStreamInfo): boolean => {
      return stream.kind === MediaStreamKind.Subtitle
    }).map((stream: MediaStreamInfo): string => `${stream.language} ${stream.codec}`.trim()).join('\n')
  }
}

export const BANNER_SCALE_FACTOR: number = 3.00

export interface CalculateHeightParam {
  yOffset: number
  offset: number
  state: ScrollState
}
