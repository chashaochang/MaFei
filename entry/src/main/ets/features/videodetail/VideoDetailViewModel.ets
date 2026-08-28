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
  MediaPlaybackTarget,
  MediaSeason
} from '../../media/catalog/MediaCatalogModels'
import {
  MediaCatalogProvider,
  MediaEpisodeQueryProfile
} from '../../media/catalog/MediaCatalogProvider'
import { MediaRef, MediaRefKind } from '../../media/MediaRef'
import {
  PlaybackSourceViewData,
  selectedMediaOptionLabel,
  selectedMediaOptionStreamIndex,
  VideoDetailPresentationAdapter,
  VideoTag
} from '../../media/presentation/VideoDetailPresentationModels'
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
import { VideoDetailUIState } from './VideoDetailUIState'
import { VideoDetailPresentationAdapterRegistry } from
  './presentation/VideoDetailPresentationAdapterRegistry'

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
  private mediaSources: MediaFileInfo[] = []
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

  private presentationAdapter(catalog: MediaCatalogProvider): VideoDetailPresentationAdapter {
    return VideoDetailPresentationAdapterRegistry.instance().resolve(catalog.session.provider)
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

  private primaryMediaInfo(detail: MediaDetail): string {
    const parts: string[] = []
    if (detail.officialRating.length > 0) parts.push(detail.officialRating)
    detail.genres.forEach((genre: string): void => {
      if (genre.length > 0) parts.push(genre)
    })
    return parts.join(' · ')
  }

  private secondaryMediaInfo(detail: MediaDetail): string {
    const parts: string[] = []
    if (detail.productionYear !== undefined) parts.push(detail.productionYear.toString())
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
    this.mediaSources = detail.mediaFiles.slice()
    const presentationAdapter = this.presentationAdapter(catalog)
    this.ui.playbackSources = presentationAdapter.present(this.mediaSources)
    this.ui.externalUrls = detail.externalLinks.slice()
    this.ui.playbackPositionMs = detail.userState.watched.playbackPositionMs
    this.ui.percentageWatched = detail.userState.watched.playedPercentage
    this.ui.remainingTime = ''
    this.ui.playButtonText = '播放'
    this.ui.subtitle = ''
    this.ui.episodeName = ''
    this.ui.selectedMediaSourceIndex = this.preferredMediaSourceIndex(detail.ref, this.mediaSources)
    this.applySelectedSourcePresentation()
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
      this.ui.episodeName = presentationAdapter.episodeLabel(episode.seasonNumber, episode.episodeNumber)
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
    this.ui.primaryInfo = this.primaryMediaInfo(detail)
    this.ui.secondaryInfo = this.secondaryMediaInfo(detail)
    this.ui.info = [this.ui.primaryInfo, this.ui.secondaryInfo]
      .filter((part: string): boolean => part.length > 0)
      .join(' · ')
    if (this.ui.playbackPositionMs > 0) {
      const remainingMs = Math.max(0, detail.durationMs - this.ui.playbackPositionMs)
      const remainingMinutes = Math.max(0, Math.floor(remainingMs / 60000))
      const hours = Math.floor(remainingMinutes / 60)
      const minutes = remainingMinutes % 60
      this.ui.remainingTime = hours > 0 ? `剩余 ${hours} 小时 ${minutes} 分钟` : `剩余 ${minutes} 分钟`
      this.ui.playButtonText = '继续播放'
    }
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
        name: season.seasonNumber !== undefined ? `第 ${season.seasonNumber} 季` : season.title,
        image: image?.url || '',
        imageRef: image,
        subtitle: subtitleParts.join(' · '),
        score: season.communityRating || 0,
        tags: season.tags.slice(),
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

  private async preferredSeriesTarget(catalog: MediaCatalogProvider,
    detail: MediaDetail): Promise<MediaPlaybackTarget | undefined> {
    if (detail.playbackTarget) return detail.playbackTarget
    try {
      const nextPage = await catalog.nextUp({
        seriesRef: detail.ref,
        limit: 1,
        disableFirstEpisode: false,
        enableResumable: true,
        enableRewatching: true
      })
      if (nextPage.items.length > 0) {
        const next = nextPage.items[0]
        return {
          ref: next.ref,
          seasonNumber: next.seasonNumber,
          episodeNumber: next.episodeNumber
        }
      }
    } catch (_error) {
    }
    return undefined
  }

  private async seriesQueue(catalog: MediaCatalogProvider, detail: MediaDetail,
    seasonsPromise: Promise<MediaSeason[]>): Promise<EpisodeQueueSelection> {
    const adapter = this.presentationAdapter(catalog)
    const targetPromise = this.preferredSeriesTarget(catalog, detail)
    const seasons = await seasonsPromise
    const target = await targetPromise
    const targetLabel = adapter.episodeLabel(target?.seasonNumber, target?.episodeNumber)
    const hasTargetNumber = target?.seasonNumber !== undefined || target?.episodeNumber !== undefined
    if (seasons.length === 0) {
      return target?.ref ? { refs: [target.ref], startIndex: 0, label: targetLabel }
        : { refs: [], startIndex: 0, label: '' }
    }
    const targetSeason = target?.seasonNumber !== undefined
      ? seasons.find((season: MediaSeason): boolean => season.seasonNumber === target.seasonNumber)
      : undefined
    const season = targetSeason || seasons[0]
    const episodePage = await catalog.getEpisodes({
      seriesRef: detail.ref,
      seasonRef: season.ref,
      startIndex: 0,
      limit: 1000,
      profile: MediaEpisodeQueryProfile.PlaybackQueue
    })
    const episodes = episodePage.items
    if (episodes.length === 0) {
      return target?.ref ? { refs: [target.ref], startIndex: 0, label: targetLabel }
        : { refs: [], startIndex: 0, label: '' }
    }
    let startIndex = target?.ref
      ? episodes.findIndex((episode: MediaEpisode): boolean => episode.ref.equals(target.ref)) : -1
    if (startIndex < 0 && target?.episodeNumber !== undefined) {
      startIndex = episodes.findIndex((episode: MediaEpisode): boolean => {
        return episode.seasonNumber === target.seasonNumber && episode.episodeNumber === target.episodeNumber
      })
    }
    if (startIndex < 0) {
      startIndex = episodes.findIndex((episode: MediaEpisode): boolean => !episode.userState.watched.watched)
    }
    if (startIndex < 0) startIndex = 0
    const selected = episodes[startIndex]
    return {
      refs: episodes.map((episode: MediaEpisode): MediaRef => episode.ref),
      startIndex: startIndex,
      label: hasTargetNumber ? targetLabel :
        adapter.episodeLabel(selected.seasonNumber, selected.episodeNumber)
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
      label: this.presentationAdapter(catalog).episodeLabel(
        episodePage.items[startIndex].seasonNumber, episodePage.items[startIndex].episodeNumber)
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
        const queuePromise: Promise<EpisodeQueueSelection> = this.seriesQueue(catalog, detail, seasonsPromise)
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
    playMediaRefs(refs, getContext(), this.selectedAudioStreamIndex(), this.selectedSubtitleStreamIndex(),
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
    if (index < 0 || index >= this.mediaSources.length || index === this.ui.selectedMediaSourceIndex) {
      return
    }
    this.ui.selectedMediaSourceIndex = index
    this.applySelectedSourcePresentation()
    const ref = this.ui.mediaRef
    const sourceId = this.currentMediaFile()?.id.trim() || ''
    if (ref && sourceId.length > 0) {
      try {
        AppPreference.getInstance().setPreferredMediaSourceId(ref, sourceId)
      } catch (_error) {
        // A preference write must not prevent the user from changing versions.
      }
    }
  }

  selectAudioOption(index: number): void {
    const source = this.currentPlaybackSource()
    if (!source || index < 0 || index >= source.audioOptions.length) return
    this.ui.selectedAudioOptionIndex = index
    this.ui.selectedAudioOptionLabel = selectedMediaOptionLabel(source.audioOptions, index, '无音频')
  }

  selectSubtitleOption(index: number): void {
    const source = this.currentPlaybackSource()
    if (!source || index < 0 || index >= source.subtitleOptions.length) return
    this.ui.selectedSubtitleOptionIndex = index
    this.ui.selectedSubtitleOptionLabel = selectedMediaOptionLabel(source.subtitleOptions, index, '无字幕')
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
    if (this.mediaSources.length === 0) return undefined
    const index = Math.min(Math.max(this.ui.selectedMediaSourceIndex, 0), this.mediaSources.length - 1)
    return this.mediaSources[index]
  }

  private currentPlaybackSource(): PlaybackSourceViewData | undefined {
    if (this.ui.playbackSources.length === 0) return undefined
    const index = Math.min(Math.max(this.ui.selectedMediaSourceIndex, 0), this.ui.playbackSources.length - 1)
    return this.ui.playbackSources[index]
  }

  private selectedAudioStreamIndex(): number | undefined {
    const source = this.currentPlaybackSource()
    if (!source) return undefined
    return selectedMediaOptionStreamIndex(source.audioOptions, this.ui.selectedAudioOptionIndex)
  }

  private selectedSubtitleStreamIndex(): number | undefined {
    const source = this.currentPlaybackSource()
    if (!source) return undefined
    return selectedMediaOptionStreamIndex(source.subtitleOptions, this.ui.selectedSubtitleOptionIndex)
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

  getTags(): string[] {
    return this.ui.tags
  }

  private applySelectedSourcePresentation(): void {
    const source = this.currentPlaybackSource()
    if (!source) {
      this.ui.path = ''
      this.ui.size = ''
      this.ui.createDate = ''
      this.ui.addedDate = ''
      this.ui.videoInfo = ''
      this.ui.audioInfo = ''
      this.ui.subtitleInfo = ''
      this.ui.tags = []
      this.ui.mediaInfoSections = []
      this.ui.selectedAudioOptionIndex = 0
      this.ui.selectedSubtitleOptionIndex = 0
      this.ui.selectedAudioOptionLabel = '无音频'
      this.ui.selectedSubtitleOptionLabel = '无字幕'
      return
    }
    this.ui.path = source.filePath
    this.ui.size = source.fileSize
    this.ui.createDate = source.fileCreatedDate
    this.ui.addedDate = source.addedDate
    this.ui.videoInfo = source.videoSummary
    this.ui.audioInfo = source.audioSummary
    this.ui.subtitleInfo = source.subtitleSummary
    this.ui.tags = source.tagKeys.map((tag: VideoTag): string => getTagBase64(tag))
      .filter((value: string): boolean => value.length > 0)
    this.ui.mediaInfoSections = source.infoSections.slice()
    this.ui.selectedAudioOptionIndex = source.defaultAudioOptionIndex
    this.ui.selectedSubtitleOptionIndex = source.defaultSubtitleOptionIndex
    this.ui.selectedAudioOptionLabel = selectedMediaOptionLabel(
      source.audioOptions, source.defaultAudioOptionIndex, '无音频')
    this.ui.selectedSubtitleOptionLabel = selectedMediaOptionLabel(
      source.subtitleOptions, source.defaultSubtitleOptionIndex, '无字幕')
  }
}

export const BANNER_SCALE_FACTOR: number = 3.00

export interface CalculateHeightParam {
  yOffset: number
  offset: number
  state: ScrollState
}
